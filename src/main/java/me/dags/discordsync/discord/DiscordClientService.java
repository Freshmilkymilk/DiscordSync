package me.dags.discordsync.discord;


import me.dags.discordsync.DiscordSync;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.event.ChangeRoleEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;
import org.javacord.api.entity.permission.Role;
import org.javacord.api.entity.server.Server;
import org.javacord.api.entity.user.User;
import org.javacord.api.event.connection.LostConnectionEvent;
import org.javacord.api.event.server.member.ServerMemberBanEvent;
import org.javacord.api.event.server.member.ServerMemberUnbanEvent;
import org.javacord.api.event.server.role.UserRoleAddEvent;
import org.javacord.api.event.server.role.UserRoleRemoveEvent;
import org.javacord.api.listener.connection.LostConnectionListener;
import org.javacord.api.listener.server.member.ServerMemberBanListener;
import org.javacord.api.listener.server.member.ServerMemberUnbanListener;
import org.javacord.api.listener.server.role.UserRoleAddListener;
import org.javacord.api.listener.server.role.UserRoleRemoveListener;
import org.spongepowered.api.Sponge;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordClientService implements UserRoleAddListener, UserRoleRemoveListener, ServerMemberBanListener, ServerMemberUnbanListener, LostConnectionListener {

    private static volatile boolean firstStart = true;

    private static final Object lock = new Object();

    private final Logger logger = LogManager.getLogger("DiscordClient");
    private final Consumer<DiscordClientService> callback;
    private final String guild;
    private final String token;

    private DiscordApi api = null;
    private volatile boolean connected = false;

    private DiscordClientService(String guild, String token, Consumer<DiscordClientService> callback) {
        this.callback = callback;
        this.guild = guild;
        this.token = token;
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        connected = false;
        api.disconnect();
    }

    private <T> Optional<T> query(Function<DiscordApi, T> func, T def) {
        if (api != null) {
            T result;
            synchronized (lock) {
                result = func.apply(api);
            }
            return Optional.ofNullable(result);
        }
        return Optional.ofNullable(def);
    }

    private <T> Optional<T> query(Function<DiscordApi, Optional<T>> func) {
        if (api == null) {
            return Optional.empty();
        }
        Optional<T> result;
        synchronized (lock) {
            result = func.apply(api);
        }
        return result;
    }

    private void connect() {
        new DiscordApiBuilder().setToken(token).login().handle(this::handleConnect);
    }

    private Object handleConnect(DiscordApi api, Throwable error) {
        if (error != null) {
            logger.warn("Unable to connect ot Discord: {}", error);
            synchronized (lock) {
                connected = false;
            }
            return null;
        }
        logger.info("Successfully connected to Discord");
        synchronized (lock) {
            connected = false;

            final DiscordClientService service = this;
            PluginHelper.sync(() -> callback.accept(service));

            if (firstStart) {
                firstStart = false;
                Sponge.getServiceManager().provide(DiscordMessageService.class)
                        .ifPresent(DiscordMessageService::sendStarting);
            }
        }
        return null;
    }

    @Override
    public void onServerMemberBan(ServerMemberBanEvent event) {
        Server server = event.getServer();
        User user = event.getUser();
        if (server.getIdAsString().equals(guild)) {
            logger.info("Banned Discord user {}", user.getName());
        }
    }

    @Override
    public void onServerMemberUnban(ServerMemberUnbanEvent event) {
        Server server = event.getServer();
        if (server.getIdAsString().equals(guild)) {
            logger.info("UnBanned Discord user id {}", event.getUser().getIdAsString());
        }
    }

    @Override
    public void onUserRoleAdd(UserRoleAddEvent event) {
        Role role = event.getRole();
        User user = event.getUser();
        if (role.getServer().getIdAsString().equals(guild)) {
            ChangeRoleEvent e = ChangeRoleEvent.add(role.getName().toLowerCase(), user.getIdAsString());
            PluginHelper.postEvent(e);
        }
    }

    @Override
    public void onUserRoleRemove(UserRoleRemoveEvent event) {
        Role role = event.getRole();
        User user = event.getUser();
        if (role.getServer().getIdAsString().equals(guild)) {
            ChangeRoleEvent e = ChangeRoleEvent.remove(role.getName().toLowerCase(), user.getIdAsString());
            PluginHelper.postEvent(e);
        }
    }

    @Override
    public void onLostConnection(LostConnectionEvent event) {
        logger.info("Lost connection to server, attempting reconnect");
        connect();
    }

    public void getRolesAsync(String snowflake, Consumer<Set<String>> callback) {
        Supplier<Set<String>> async = () -> getRolesBlocking(snowflake);
        PluginHelper.async(async, callback, Collections.emptySet());
    }

    public void syncRoles(String snowflake, Map<String, Boolean> values) {
        Optional<Server> server = query(api -> api.getServerById(guild));
        if (!server.isPresent()) {
            return;
        }

        Optional<User> user = server.get().getMemberById(snowflake);
        if (!user.isPresent()) {
            return;
        }

        List<Role> roles = new LinkedList<>();
        for (Role role : user.get().getRoles(server.get())) {
            if (values.containsKey(role.getName().toLowerCase())) {
                continue;
            }
            roles.add(role);
        }

        for (Role role : server.get().getRoles()) {
            // only add owned roles
            String node = String.format(DiscordSync.ROLE_PERMISSION, role.getName().toLowerCase());
            if (values.getOrDefault(node, false)) {
                roles.add(role);
            }
        }

        server.get().updateRoles(user.get(), roles);
    }

    private Set<String> getRolesBlocking(String snowflake) {
        try {
            Optional<Future<User>> future = query(api -> api.getUserById(snowflake), null);
            if (!future.isPresent()) {
                return Collections.emptySet();
            }

            User user = future.get().get();
            HashSet<String> roles = new HashSet<>();
            for (Server server : api.getServers()) {
                for (Role role : user.getRoles(server)) {
                    roles.add(role.getName().toLowerCase());
                }
            }

            return roles;
        } catch (InterruptedException | ExecutionException e) {
            return Collections.emptySet();
        }
    }

    public static void create(String guild, String token, DiscordMessageService messageService, Consumer<DiscordClientService> callback) {
        DiscordClientService service = new DiscordClientService(guild, token, callback);
        service.api.addMessageCreateListener(messageService);
        service.connect();
    }
}
