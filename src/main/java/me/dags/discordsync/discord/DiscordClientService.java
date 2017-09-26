package me.dags.discordsync.discord;

import com.google.common.util.concurrent.FutureCallback;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.permissions.Role;
import de.btobastian.javacord.listener.server.ServerMemberBanListener;
import de.btobastian.javacord.listener.server.ServerMemberUnbanListener;
import de.btobastian.javacord.listener.user.UserRoleAddListener;
import de.btobastian.javacord.listener.user.UserRoleRemoveListener;
import me.dags.discordsync.DiscordSync;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.event.ChangeRoleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordClientService implements UserRoleAddListener, UserRoleRemoveListener, ServerMemberBanListener, ServerMemberUnbanListener {

    private static volatile boolean firstStart = true;

    private final Logger logger = LoggerFactory.getLogger("DiscordClient");
    private final Consumer<DiscordClientService> callback;
    private final DiscordAPI api;
    private final String guild;

    private volatile boolean connected = false;

    private DiscordClientService(String guild, String token, Consumer<DiscordClientService> callback) {
        this.api = Javacord.getApi(token, true);
        this.callback = callback;
        this.guild = guild;
        this.api.registerListener(this);
    }

    public boolean isConnected() {
        return connected;
    }

    public void disconnect() {
        connected = false;
        api.disconnect();
    }

    private void connect() {
        final DiscordClientService instance = this;

        api.connect(new FutureCallback<DiscordAPI>() {
            @Override
            public void onSuccess(@Nullable DiscordAPI result) {
                logger.info("Successfully connected to Discord");
                callback.accept(instance);
                connected = true;
                if (firstStart) {
                    firstStart = false;
                    Sponge.getServiceManager().provide(DiscordMessageService.class)
                            .ifPresent(DiscordMessageService::sendStarting);
                }
            }

            @Override
            public void onFailure(Throwable t) {
                logger.warn("Unable to connect ot Discord!");
                connected = false;
            }
        });
    }

    @Override
    public void onUserRoleAdd(DiscordAPI discordAPI, User user, Role role) {
        if (role.getServer().getId().equals(guild)) {
            ChangeRoleEvent event = ChangeRoleEvent.add(role.getName().toLowerCase(), user.getId());
            PluginHelper.postEvent(event);
        }
    }

    @Override
    public void onUserRoleRemove(DiscordAPI discordAPI, User user, Role role) {
        if (role.getServer().getId().equals(guild)) {
            ChangeRoleEvent event = ChangeRoleEvent.remove(role.getName().toLowerCase(), user.getId());
            PluginHelper.postEvent(event);
        }
    }

    @Override
    public void onServerMemberBan(DiscordAPI discordAPI, User user, Server server) {
        if (server.getId().equals(guild)) {
            logger.info("Banned Discord user {}", user.getName());
        }
    }

    @Override
    public void onServerMemberUnban(DiscordAPI discordAPI, String s, Server server) {
        if (server.getId().equals(guild)) {
            logger.info("UnBanned Discord user id {}", s);
        }
    }

    public void getRolesAsync(String snowflake, Consumer<Set<String>> callback) {
        Supplier<Set<String>> async = () -> getRolesBlocking(snowflake);
        PluginHelper.async(async, callback, Collections.emptySet());
    }

    public void syncRoles(String snowflake, Map<String, Boolean> values) {
        Server server = api.getServerById(guild);
        if (server == null) {
            return;
        }

        User user = server.getMemberById(snowflake);
        if (user == null) {
            return;
        }

        List<Role> roles = new LinkedList<>();
        for (Role role : user.getRoles(server)) {
            if (values.containsKey(role.getName().toLowerCase())) {
                continue;
            }
            roles.add(role);
        }

        for (Role role : server.getRoles()) {
            // only add owned roles
            String node = String.format(DiscordSync.ROLE_PERMISSION, role.getName().toLowerCase());
            if (values.getOrDefault(node, false)) {
                roles.add(role);
            }
        }

        server.updateRoles(user, roles.toArray(new Role[roles.size()]));
    }

    private Set<String> getRolesBlocking(String snowflake) {
        try {
            Future<User> future = api.getUserById(snowflake);
            User user = future.get();

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
        service.api.registerListener(messageService);
        service.connect();
    }
}
