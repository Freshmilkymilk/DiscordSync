package me.dags.discordsync.service;

import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.config.DiscordChannel;
import me.dags.discordsync.event.MessageEvent;
import me.dags.discordsync.event.RoleEvent;
import net.dv8tion.jda.core.JDA;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.entities.Role;
import net.dv8tion.jda.core.events.DisconnectEvent;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.ReconnectedEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.core.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.AnnotatedEventManager;
import net.dv8tion.jda.core.hooks.SubscribeEvent;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.util.Optional;

public class JDAService implements DiscoService {

    private static final Logger logger = LoggerFactory.getLogger("JDADiscordService");

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
        logger.debug("Message: {}: {}", author, content);
    }

    @SubscribeEvent
    public void onRoleAdd(GuildMemberRoleAddEvent event) {
        for (Role role : event.getRoles()) {
            RoleEvent add = RoleEvent.add(role.getName().toLowerCase(), event.getUser().getId());
            PluginHelper.postEvent(add);
        }
        logger.info("Role Add: {}: {}", event.getMember().getNickname(), event.getRoles());
    }

    @SubscribeEvent
    public void onRoleRemove(GuildMemberRoleRemoveEvent event) {
        for (Role role : event.getRoles()) {
            RoleEvent remove = RoleEvent.remove(role.getName().toLowerCase(), event.getUser().getId());
            PluginHelper.postEvent(remove);
        }
        logger.info("Role Remove: {}: {}", event.getMember().getNickname(), event.getRoles());
    }

    @SubscribeEvent
    public void onServiceDisconnect(DisconnectEvent event) {
        logger.info("service disconnected");
    }

    @SubscribeEvent
    public void onServiceReady(ReadyEvent event) {
        logger.info("service ready");
    }

    @SubscribeEvent
    public void onServiceReconnect(ReconnectedEvent event) {
        logger.info("service reconnected");
    }

    @Override
    public void shutdown() {
        logger.info("shutting down");
        api.shutdown();
        client.dispatcher().executorService().shutdown();
    }

    @Override
    public void sendMessage(DiscordChannel channel, MessageEvent message) {
        post(channel.getWebhook(), message).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {

            }

            @Override
            public void onResponse(Call call, Response response) {

            }
        });
    }

    @Override
    public void sendMessageSync(DiscordChannel channel, MessageEvent message) {
        try {
            post(channel.getWebhook(), message).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Call post(String url, MessageEvent message) {
        JSONObject json = new JSONObject();
        json.put("username", message.getAuthor());
        json.put("avatar_url", message.getAvatar());
        json.put("content", message.getContent());

        RequestBody body = RequestBody.create(MediaType.get("application/json"), json.toString());

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
            return Optional.empty();
        }
    }
}
