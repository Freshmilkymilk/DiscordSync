package me.dags.discordsync.discord;

import com.google.common.collect.ImmutableList;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import de.btobastian.javacord.DiscordAPI;
import de.btobastian.javacord.entities.message.Message;
import de.btobastian.javacord.listener.message.MessageCreateListener;
import me.dags.discordsync.PluginHelper;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;

import java.util.List;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordMessageService implements MessageCreateListener {

    private final List<DiscordChannel> channels;
    private final DiscordChannel.Format format;
    private final String serverName;
    private final String serverAvatar;

    private DiscordMessageService(String serverName, String serverAvatar, DiscordChannel.Format format, List<DiscordChannel> channels) {
        this.channels = channels;
        this.format = format;
        this.serverName = serverName;
        this.serverAvatar = serverAvatar;
    }

    @Override
    public void onMessageCreate(DiscordAPI discord, Message message) {
        if (message.getAuthor().isBot()) {
            return;
        }

        for (DiscordChannel channel : channels) {
            if (channel.getId().equals(message.getChannelReceiver().getId())) {
                String nick = message.getAuthor().getNickname(message.getChannelReceiver().getServer());
                String source = nick != null ? nick : message.getAuthor().getName();
                String content = message.getContent();
                Text text = channel.getTemplate().with("name", source).with("message", content).render();
                PluginHelper.sync(() -> Sponge.getServer().getBroadcastChannel().send(text));
            }
        }
    }

    public void sendStarting() {
        sendStatus(format.getStart());
    }

    public void sendStopping() {
        for (DiscordChannel channel : channels) {
            sendSync(channel, serverName, serverAvatar, format.getStop());
        }
    }

    public void sendConnect(String name) {
        sendStatus(format.getConnect(name));
    }

    public void sendDisconnect(String name) {
        sendStatus(format.getDisconnect(name));
    }

    public void sendMessage(String name, String message) {
        String title = format.getTitle(name, serverName);
        String content = format.getMessage(name, message);
        String avatar = format.getAvatar(name);
        sendMessage(title, avatar, content);
    }

    private void sendMessage(String name, String avatar, String content) {
        for (DiscordChannel channel : channels) {
            sendAsync(channel, name, avatar, content);
        }
    }

    private void sendStatus(String message) {
        for (DiscordChannel channel : channels) {
            sendAsync(channel, serverName, serverAvatar, message);
        }
    }

    private void sendAsync(DiscordChannel channel, String name, String avatar, String content) {
        Unirest.post(channel.getWebhook())
                .field("username", name)
                .field("avatar_url", avatar)
                .field("content", content)
                .asStringAsync();
    }

    private void sendSync(DiscordChannel channel, String name, String avatar, String content) {
        try {
            Unirest.post(channel.getWebhook())
                    .field("username", name)
                    .field("avatar_url", avatar)
                    .field("content", content)
                    .asString();
        } catch (UnirestException e) {
            e.printStackTrace();
        }
    }

    public static DiscordMessageService create(String name, String avatar, DiscordChannel.Format format, DiscordChannel... channels) {
        return new DiscordMessageService(name, avatar, format, ImmutableList.copyOf(channels));
    }
}
