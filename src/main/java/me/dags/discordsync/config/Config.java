package me.dags.discordsync.config;

import java.util.Collections;
import java.util.List;

/**
 * @author dags <dags@dags.me>
 */
public class Config {

    public Server server = new Server();
    public Discord discord = new Discord();
    public Messages prompts = new Messages();

    public static class Server {
        public String name = "Minecraft Server";
        public String avatar = "";
        public List<String> roles = Collections.singletonList("patron");
    }

    public static class Discord {
        public String guildId = "";
        public String botUserToken = "";
        public String botClientId = "";
    }

    public static class Messages {
        public String prompt = "[blue](Use [gold,underline,/discord auth](/discord auth) to link your Discord account)";
        public String auth = "[blue]([gold,underline,{url}](Click me) to authenticate your account)";
        public String add = "[blue]([gold,underline,{url}](Click me) to add the chat bot to your guild)";
    }
}
