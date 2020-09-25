package me.dags.discordsync.service;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.config.DiscordChannel;
import me.dags.discordsync.event.MessageEvent;
import me.dags.discordsync.event.RoleEvent;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.DisconnectEvent;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ReconnectedEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.AnnotatedEventManager;
import net.dv8tion.jda.api.hooks.SubscribeEvent;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Optional;

public class JDAService implements DiscoService {

    private static final Logger LOGGER = LoggerFactory.getLogger("JDADiscordService");
    private static final JsonFactory JSON_FACTORY = JsonFactory.builder().build();

    private final JDA api;
    private final OkHttpClient client = new OkHttpClient.Builder().build();

    private JDAService(JDA api) {
        this.api = api;
        api.setAutoReconnect(true);
        api.setEventManager(new AnnotatedEventManager());
        api.addEventListener(this);
    }

    @SubscribeEvent
    public void onMessage(MessageReceivedEvent event) {
        if (event.isWebhookMessage() || event.getAuthor().isBot()) {
            return;
        }
        String guild = event.getGuild().getId();
        String channel = event.getChannel().getId();
        String author = event.getAuthor().getName();
        String avatar = event.getAuthor().getAvatarUrl();
        String content = event.getMessage().getContentRaw();
        PluginHelper.postEvent(new MessageEvent(guild, channel, author, avatar, content));
        LOGGER.debug("Message: {}: {}", author, content);
    }

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        for (Role role : event.getRoles()) {
            RoleEvent add = RoleEvent.add(role.getName().toLowerCase(), event.getUser().getId());
            PluginHelper.postEvent(add);
        }
        LOGGER.info("Role Add: {}: {}", event.getMember().getNickname(), event.getRoles());
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        for (Role role : event.getRoles()) {
            RoleEvent remove = RoleEvent.remove(role.getName().toLowerCase(), event.getUser().getId());
            PluginHelper.postEvent(remove);
        }
        LOGGER.info("Role Remove: {}: {}", event.getMember().getNickname(), event.getRoles());
    }

    @SubscribeEvent
    public void onServiceDisconnect(DisconnectEvent event) {
        LOGGER.info("service disconnected");
    }

    @SubscribeEvent
    public void onServiceReady(ReadyEvent event) {
        LOGGER.info("service ready");
    }

    @SubscribeEvent
    public void onServiceReconnect(ReconnectedEvent event) {
        LOGGER.info("service reconnected");
    }

    @Override
    public void shutdown() {
        LOGGER.info("shutting down");
        api.shutdown();
        client.dispatcher().executorService().shutdown();
    }

    @Override
    public void sendMessage(DiscordChannel channel, MessageEvent message) {
        post(channel.getWebhook(), message).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                LOGGER.error("Error performing async request.", e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                handleResponse(response);
            }
        });
    }

    @Override
    public void sendMessageSync(DiscordChannel channel, MessageEvent message) {
        try {
            handleResponse(post(channel.getWebhook(), message).execute());
        } catch (IOException e) {
            LOGGER.error("Error performing sync request.", e);
        }
    }

    private Call post(String url, MessageEvent message) {
        String json = JDAService.encode(message);
        RequestBody body = RequestBody.create(MediaType.get("application/json"), json);
        Request request = new Request.Builder()
                .post(body)
                .url(url)
                .build();
        return client.newCall(request);
    }

    public static Optional<DiscoService> create(String token) {
        try {
            JDA api = new JDABuilder(token).build();
            JDAService service = new JDAService(api);
            return Optional.of(service);
        } catch (LoginException e) {
            LOGGER.error("Error connecting to discord api.", e);
            return Optional.empty();
        }
    }

    private static void handleResponse(Response response) {
        try (Response r = response) {
            if (!r.isSuccessful()) {
                LOGGER.error("Failed to post message. Response: {}", r.message());
            }
        }
    }

    private static String encode(MessageEvent message) {
        try {
            StringWriter writer = new StringWriter(128);
            JsonGenerator generator = JSON_FACTORY.createGenerator(writer);

            generator.writeStartObject();
            generator.writeStringField("username", message.getAuthor());
            generator.writeStringField("avatar_url", message.getAvatar());
            generator.writeStringField("content", message.getContent());
            generator.writeEndObject();

            return writer.toString();
        } catch (IOException e) {
            LOGGER.error("Error encoding message.", e);
            return "{}";
        }
    }
}
