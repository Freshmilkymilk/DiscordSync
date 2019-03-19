package me.dags.discordsync.discord;

import com.google.common.collect.ImmutableList;
import me.dags.discordsync.PluginHelper;
import okhttp3.FormBody;
import org.javacord.api.entity.message.Message;
import org.javacord.api.event.message.MessageCreateEvent;
import org.javacord.api.listener.message.MessageCreateListener;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.text.Text;

import java.io.IOException;
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
    public void onMessageCreate(MessageCreateEvent event) {
        Message message = event.getMessage();
        if (message.getAuthor().isBotOwner()) {
            return;
        }

        if (message.getContent().startsWith("!")) {
            for (DiscordChannel channel : channels) {
                if (channel.getId().equals(message.getChannel().getIdAsString())) {
                    DiscordCommands.getInstance().process(message);
                    return;
                }
            }
        }

        for (DiscordChannel channel : channels) {
            if (channel.getId().equals(message.getChannel().getIdAsString())) {
                String name = message.getAuthor().getDisplayName();
                String source = name != null ? name : message.getAuthor().getName();
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
        PluginHelper.post(channel.getWebhook(), new FormBody.Builder()
                .add("username", name)
                .add("avatar_url", avatar)
                .add("content", content)
                .build());
    }

    private void sendSync(DiscordChannel channel, String name, String avatar, String content) {
        try {
            PluginHelper.postSync(channel.getWebhook(), new FormBody.Builder()
                    .add("username", name)
                    .add("avatar_url", avatar)
                    .add("content", content)
                    .build());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static DiscordMessageService create(String name, String avatar, DiscordChannel.Format format, DiscordChannel... channels) {
        return new DiscordMessageService(name, avatar, format, ImmutableList.copyOf(channels));
    }
}
