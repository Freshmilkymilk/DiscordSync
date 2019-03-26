package me.dags.discordsync.config;

/**
 * @author dags <dags@dags.me>
 */
public class Channels {

    public Channel main = new Channel();

    public static class Channel {
        public String channelId = "";
        public String webhook = "";
        public Discord discord = new Discord();
        public Minecraft minecraft = new Minecraft();
    }

    public static class Discord {
        public String title = "{0} ({1})";
        public String message = "**{0}**: {1}";
        public String connected = "```{0} joined the server```";
        public String disconnected = "```{0} left the server```";
        public String starting = "```Server is starting...```";
        public String stopping = "```Server is stopping...```";
        public String avatar = "https://minotar.net/helm/{0}";
    }

    public static class Minecraft {
        public String message = "[blue](`[Discord]` {name}): {message}";
    }
}
