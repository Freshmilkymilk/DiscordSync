package me.dags.discordsync;

import me.dags.commandbus.CommandBus;
import me.dags.commandbus.annotation.Command;
import me.dags.commandbus.annotation.Permission;
import me.dags.commandbus.annotation.Src;
import me.dags.commandbus.fmt.Fmt;
import me.dags.discordsync.discord.DiscordAuthService;
import me.dags.discordsync.discord.DiscordChannel;
import me.dags.discordsync.discord.DiscordClientService;
import me.dags.discordsync.discord.DiscordMessageService;
import me.dags.discordsync.event.AuthUserEvent;
import me.dags.discordsync.event.ChangeRoleEvent;
import me.dags.discordsync.storage.Config;
import me.dags.discordsync.storage.FileUserStorage;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

@Plugin(id = "discordsync")
public class DiscordSync {

    public static final String ID = "discordsync";

    private final Path configDir;

    @Inject
    public DiscordSync(@ConfigDir(sharedRoot = false) Path path) {
        configDir = path;
    }

    @Permission
    @Command("discord auth")
    public void auth(@Src Player player) {
        player.sendMessage(PluginHelper.getAuthText(player.getUniqueId()));
    }

    @Permission
    @Command("discord reload")
    public void reload(@Src CommandSource source) {
        Fmt.info("Reloading...").stress(source);
        refresh(null);
    }

    @Listener
    public void init(GameInitializationEvent event) {
        CommandBus.create(this).register(this).submit();
        DiscordClientService.markStarted();
        refresh(null);
    }

    @Listener
    public void refresh(GameReloadEvent event) {
        Sponge.getServiceManager().provide(DiscordAuthService.class).ifPresent(DiscordAuthService::stop);
        Sponge.getServiceManager().provide(DiscordClientService.class).ifPresent(DiscordClientService::disconnect);

        Config config = new Config(configDir.resolve("config.json"));
        String name = config.get("Server", "server", "name");
        String avatar = config.get("", "server", "avatar");
        String guildId = config.get("", "discord", "guild");
        String token = config.get("", "discord", "token");
        String clientId = config.get("", "discord", "clientId");
        String clientSecret = config.get("", "discord", "clientSecret");
        String url = config.get("", "auth", "url");
        int port = config.get(8080, "auth", "port");

        Config channels = new Config(configDir.resolve("channels.json"));
        String message = channels.get("{0}: {1}", "format", "message");
        String connect = channels.get("```{0} joined the server```", "format", "connect");
        String disconnect = channels.get("```{0} left the server```", "format", "disconnect");
        String start = channels.get("```Server starting...```", "format", "start");
        String stop = channels.get("```Server stopping...```", "format", "stop");
        String userAvatar = channels.get("", "format", "avatar");
        DiscordChannel.Format format = new DiscordChannel.Format(message, connect, disconnect, start, stop, userAvatar);

        String channelId = channels.get("", "public", "channelId");
        String channelTemplate = channels.get("[blue](`[Discord]` {name}): {message}", "public", "template");
        String channelWebhook = channels.get("https://minotar.net/helm/{0}", "public", "webhook");
        DiscordChannel channel = new DiscordChannel(channelId, channelTemplate, channelWebhook);

        FileUserStorage users = new FileUserStorage(configDir.resolve("users.json"));
        DiscordAuthService.create(users, clientId, clientSecret, url, port, authService -> {
            Sponge.getServiceManager().setProvider(this, DiscordAuthService.class, authService);
        });

        DiscordMessageService messageService = DiscordMessageService.create(name, avatar, format, channel);
        Sponge.getServiceManager().setProvider(this, DiscordMessageService.class, messageService);

        DiscordClientService.create(guildId, token, messageService, clientService -> {
            Sponge.getServiceManager().setProvider(this, DiscordClientService.class, clientService);
        });

        channels.save();
        config.save();
        users.save();
    }

    @Listener
    public void stop(GameStoppingServerEvent event) {
        Sponge.getServiceManager().provide(DiscordMessageService.class).ifPresent(DiscordMessageService::sendStopping);
    }

    @Listener
    public void join(ClientConnectionEvent.Join event, @Root Player player) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent()) {
            Optional<String> snowflake = authService.get().getSnowflake(player.getUniqueId());
            if (snowflake.isPresent()) {
                PluginHelper.syncRoles(player, snowflake.get());
            } else {
                player.sendMessage(PluginHelper.getPromptText());
            }
        }

        Sponge.getServiceManager().provide(DiscordMessageService.class)
                .ifPresent(discordMessageService -> discordMessageService.sendConnect(player.getName()));
    }

    @Listener
    public void quit(ClientConnectionEvent.Disconnect event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscordMessageService.class)
                .ifPresent(messageService -> messageService.sendDisconnect(player.getName()));
    }

    @Listener
    public void chat(MessageChannelEvent.Chat event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscordMessageService.class).ifPresent(messageService -> {
            if (PluginHelper.hasPublicChannel(event)) {
                String name = player.getName();
                String message = event.getRawMessage().toPlain();
                messageService.sendMessage(name, message);
            }
        });
    }

    @Listener
    public void authPass(AuthUserEvent.Pass event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            authService.get().getStorage().setUser(event.getSnowflake(), event.getId());
            event.getUser().ifPresent(user -> {
                PluginHelper.syncRoles(user, event.getSnowflake());
                user.getPlayer().ifPresent(player -> {
                    Text text = Text.of("Authentication succeeded!", TextColors.GREEN);
                    player.sendMessage(text);
                });
            });
        }
    }

    @Listener
    public void authFail(AuthUserEvent.Fail event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            event.getPlayer().ifPresent(player -> {
                Text text = Text.of("Authentication failed: ", event.getReason(), TextColors.RED);
                player.sendMessage(text);
            });
        }
    }

    @Listener
    public void roleAdd(ChangeRoleEvent.Add event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            Optional<User> user = authService.get().getUser(event.getSubjectSnowflake());
            user.ifPresent(u -> PluginHelper.addRoles(u, Collections.singleton(event.getRole()), true));
        }
    }

    @Listener
    public void roleRemove(ChangeRoleEvent.Remove event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            Optional<User> user = authService.get().getUser(event.getSubjectSnowflake());
            user.ifPresent(u -> PluginHelper.removeRoles(u, Collections.singleton(event.getRole()), true));
        }
    }
}
