package me.dags.discordsync;

import java.util.Collections;
import java.util.List;

/**
 * @author dags <dags@dags.me>
 */
public class Config {

    public Server server = new Server();
    public Discord discord = new Discord();
    public Auth auth = new Auth();
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
        public String botClientSecret = "";
    }

    public static class Auth {
        public String url = "http://localhost:8080";
        public int port = 8080;
    }

    public static class Messages {
        public String prompt = "[blue](Use [gold,underline,/discord auth](/discord auth) to link your Discord account)";
        public String auth = "[blue,underline,{url}](Click me to authenticate your account)";
        public String add = "[blue,underline,{url}](Click me to add the chat bot to your guild)";
    }
}
