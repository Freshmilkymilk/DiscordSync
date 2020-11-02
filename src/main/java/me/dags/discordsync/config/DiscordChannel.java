package me.dags.discordsync.config;

import me.dags.text.MUSpec;
import me.dags.text.template.MUTemplate;

import java.text.MessageFormat;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordChannel {

    private static final MUSpec spec = MUSpec.create();

    private final String id;
    private final String webhook;
    private final MUTemplate template;

    public DiscordChannel(String id, String template, String webhook) {
        this.id = id;
        this.webhook = webhook;
        this.template = spec.template(template);
    }

    public String getId() {
        return id;
    }

    public MUTemplate getTemplate() {
        return template;
    }

    public String getWebhook() {
        return webhook;
    }

    public static class Format {

        private final String title;
        private final String message;
        private final String connect;
        private final String disconnect;
        private final String start;
        private final String stop;
        private final String avatar;

        public Format(Channels.Discord discord) {
            this(discord.title, discord.message, discord.connected, discord.disconnected, discord.starting, discord.stopping, discord.avatar);
        }

        public Format(String title, String message, String connect, String disconnect, String start, String stop, String avatar) {
            this.title = title;
            this.message = message;
            this.connect = connect;
            this.disconnect = disconnect;
            this.start = start;
            this.stop = stop;
            this.avatar = avatar;
        }

        public String getAvatar(Object... args) {
            return MessageFormat.format(avatar, args);
        }

        public String getConnect(Object... args) {
            return MessageFormat.format(connect, args);
        }

        public String getDisconnect(Object... args) {
            return MessageFormat.format(disconnect, args);
        }

        public String getMessage(Object... args) {
            return MessageFormat.format(message, args);
        }

        public String getStart(Object... args) {
            return MessageFormat.format(start, args);
        }

        public String getStop(Object... args) {
            return MessageFormat.format(stop, args);
        }

        public String getTitle(Object... args) {
            return MessageFormat.format(title, args);
        }
    }
}
