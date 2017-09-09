package me.dags.discordsync.discord;

import com.google.common.util.concurrent.FutureCallback;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.Javacord;
import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.permissions.Role;
import de.btobastian.javacord.listener.Listener;
import de.btobastian.javacord.listener.server.ServerMemberBanListener;
import de.btobastian.javacord.listener.server.ServerMemberUnbanListener;
import de.btobastian.javacord.listener.user.UserRoleAddListener;
import de.btobastian.javacord.listener.user.UserRoleRemoveListener;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.event.ChangeRoleEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordClientService implements UserRoleAddListener, UserRoleRemoveListener, ServerMemberBanListener, ServerMemberUnbanListener {

    private static volatile boolean started = false;

    private final Logger logger = LoggerFactory.getLogger("DiscordClient");
    private final Consumer<DiscordClientService> callback;
    private final DiscordAPI api;
    private final String guild;

    private volatile boolean connected = false;

    private DiscordClientService(String guild, String token, Consumer<DiscordClientService> callback) {
        this.api = Javacord.getApi(token, true);
        this.callback = callback;
        this.guild = guild;
        registerListener(this);
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
                if (started) {
                    started = false;
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
            PluginHelper.getInstance().postEvent(event);
        }
    }

    @Override
    public void onUserRoleRemove(DiscordAPI discordAPI, User user, Role role) {
        if (role.getServer().getId().equals(guild)) {
            ChangeRoleEvent event = ChangeRoleEvent.remove(role.getName().toLowerCase(), user.getId());
            PluginHelper.getInstance().postEvent(event);
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
        PluginHelper.getInstance().async(async, callback, Collections.emptySet());
    }

    private void registerListener(Listener listener) {
        api.registerListener(listener);
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

    public static void markStarted() {
        started = true;
    }

    public static void create(String guild, String token, DiscordMessageService messageService, Consumer<DiscordClientService> callback) {
        DiscordClientService service = new DiscordClientService(guild, token, callback);
        service.registerListener(messageService);
        service.connect();
    }
}
