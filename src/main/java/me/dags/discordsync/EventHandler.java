package me.dags.discordsync;

import me.dags.discordsync.config.Channels;
import me.dags.discordsync.config.Config;
import me.dags.discordsync.service.DiscoService;
import me.dags.discordsync.config.DiscordChannel;
import me.dags.discordsync.event.MessageEvent;
import me.dags.discordsync.event.RoleEvent;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;

import java.util.Collections;
import java.util.List;

public class EventHandler {

    private final String guildId;
    private final String serverName;
    private final String serverAvatar;
    private final DiscordChannel.Format format;
    private final List<DiscordChannel> channels;

    public EventHandler(Config config, Channels channels) {
        this.guildId = config.discord.guildId;
        this.serverName = config.server.name;
        this.serverAvatar = config.server.avatar;
        this.format = new DiscordChannel.Format(channels.main.discord);
        this.channels = Collections.singletonList(new DiscordChannel(
                channels.main.channelId,
                channels.main.minecraft.message,
                channels.main.webhook
        ));
    }

    @Listener
    public void onDiscordMessage(MessageEvent event) {
        if (!event.getGuild().equals(guildId)) {
            return;
        }

        for (DiscordChannel channel : channels) {
            if (channel.getId().equals(event.getChannel())) {
                Text text = channel.getTemplate()
                        .with("name", event.getAuthor())
                        .with("message", event.getContent())
                        .render();

                PluginHelper.sync(() -> Sponge.getServer().getBroadcastChannel().send(text));
                return;
            }
        }
    }

    @Listener
    public void onDiscordRoleAdd(RoleEvent.Add event) {

    }

    @Listener
    public void onDiscordRoleRemove(RoleEvent.Remove event) {

    }

    @Listener
    public void onServerChat(MessageChannelEvent.Chat event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(service -> {
            if (hasPublicChannel(event)) {
                String name = player.getName();
                String title = format.getTitle(name, serverName);
                String avatar = format.getAvatar(name);
                String content = format.getMessage(name, event.getRawMessage().toPlain());
                MessageEvent message = new MessageEvent(guildId, title, avatar, content);
                for (DiscordChannel channel : channels) {
                    service.sendMessage(channel, message);
                }
            }
        });
    }

    @Listener
    public void onServerJoin(ClientConnectionEvent.Join event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(service -> {
            String title = format.getTitle(player.getName(), serverName);
            String content = format.getConnect(player.getName());
            MessageEvent message = new MessageEvent(guildId, title, serverAvatar, content);
            for (DiscordChannel channel : channels) {
                service.sendMessage(channel, message);
            }
        });
    }

    @Listener
    public void onServerQuit(ClientConnectionEvent.Disconnect event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(service -> {
            String title = format.getTitle(player.getName(), serverName);
            String content = format.getDisconnect(player.getName());
            MessageEvent message = new MessageEvent(guildId, title, serverAvatar, content);
            for (DiscordChannel channel : channels) {
                service.sendMessage(channel, message);
            }
        });
    }

    @Listener
    public void onServerStared(GameStartedServerEvent event) {
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(service -> {
            MessageEvent message = new MessageEvent(guildId, serverName, serverAvatar, format.getStart());
            for (DiscordChannel channel : channels) {
                service.sendMessage(channel, message);
            }
        });
    }

    @Listener
    public void onServerStopping(GameStoppingServerEvent event) {
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(service -> {
            MessageEvent message = new MessageEvent(guildId, serverName, serverAvatar, format.getStop());
            for (DiscordChannel channel : channels) {
                service.sendMessage(channel, message);
            }
        });
    }

    public void sendTestMessage(CommandSource source, String message) {
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(service -> {
            String title = format.getTitle(source.getName(), serverName);
            String content = format.getMessage(source.getName(), message);
            MessageEvent event = new MessageEvent(guildId, title, serverAvatar, content);
            service.sendMessage(channels.get(0), event);
        });
    }

    private static boolean hasPublicChannel(MessageChannelEvent event) {
        MessageChannel c = event.getChannel().orElse(MessageChannel.TO_NONE);
        return c == MessageChannel.TO_ALL
                || c == MessageChannel.TO_PLAYERS
                || c == Sponge.getServer().getBroadcastChannel()
                || c.getClass().getSimpleName().equals("BoopableChannel");
    }
}
