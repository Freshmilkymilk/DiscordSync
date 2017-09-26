package me.dags.discordsync.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.ImmutableList;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import me.dags.discordsync.DiscordSync;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.event.AuthUserEvent;
import me.dags.discordsync.storage.UserStorage;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.service.user.UserStorageService;
import spark.Request;
import spark.Response;
import spark.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordAuthService {

    private static final String AUTHORIZE = "https://discordapp.com/api/oauth2/authorize";
    private static final String TOKEN = "https://discordapp.com/api/oauth2/token";
    private static final String GET_USER = "https://discordapp.com/api/users/@me";
    private static final String LOGIN_ROUTE = "/discord";
    private static final String AUTH_ROUTE = "/discord/auth";

    private final Cache<String, UUID> sessionCache = Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();
    private final UserStorage storage;
    private final String clientSecret;
    private final String clientId;
    private final String url;
    private final int port;

    private final Service service = Service.ignite();
    private final boolean running;

    private DiscordAuthService(UserStorage storage, String clientId, String clientSecret, String url, int port) {
        this.url = url;
        this.port = port;
        this.storage = storage;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.running = start();
    }

    public boolean isRunning() {
        return running;
    }

    public UserStorage getStorage() {
        return storage;
    }

    public Optional<User> getUser(String snowflake) {
        return storage.getUserId(snowflake).flatMap(Sponge.getServiceManager().provideUnchecked(UserStorageService.class)::get);
    }

    public Optional<String> getSnowflake(UUID uuid) {
        return storage.getUserSnowflake(uuid);
    }

    public String getSignUpURL(UUID uuid) {
        String state = Long.toString(System.currentTimeMillis());
        sessionCache.put(state, uuid);
        return Unirest.get(loginRoute()).queryString("id", state).getUrl();
    }

    public void startSyncRolesTask(ImmutableList<String> roles) {
        PluginHelper.getAsync().scheduleAtFixedRate(() -> syncRoles(roles), 5, 30, TimeUnit.SECONDS);
    }

    private void syncRoles(List<String> roles) {
        Optional<DiscordClientService> clientService = Sponge.getServiceManager().provide(DiscordClientService.class);
        if (clientService.isPresent()) {
            DiscordClientService client = clientService.get();
            PermissionService service = Sponge.getServiceManager().provideUnchecked(PermissionService.class);
            getStorage().iterate((uuid, snowflake) -> {
                Subject subject = service.getUserSubjects().get(uuid.toString());
                Map<String, Boolean> values = new HashMap<>(roles.size());
                Map<String, Boolean> permissions = DiscordSync.getSubjectData(subject).getPermissions(SubjectData.GLOBAL_CONTEXT);
                for (String role : roles) {
                    String node = String.format(DiscordSync.ROLE_PERMISSION, role);
                    values.put(role, permissions.getOrDefault(node, false));
                }
                client.syncRoles(snowflake, values);
            });
        }
    }

    public void stop() {
        service.stop();
    }

    private boolean start() {
        if (clientId.isEmpty() || clientSecret.isEmpty() || url.isEmpty()) {
            return false;
        }
        try {
            service.port(port);
            service.get(LOGIN_ROUTE, this::handleLogin);
            service.get(AUTH_ROUTE, this::handleAuth);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private String loginRoute() {
        return url + LOGIN_ROUTE;
    }

    private String authRoute() {
        return url + AUTH_ROUTE;
    }

    private Object handleLogin(Request request, Response response) {
        String state = request.queryParams("id");

        String url = Unirest.get(AUTHORIZE)
                .queryString("response_type", "code")
                .queryString("client_id", clientId)
                .queryString("scope", "identify")
                .queryString("state", state)
                .queryString("redirect_uri", authRoute())
                .getUrl();

        response.redirect(url);
        
        return "Redirecting...";
    }

    private Object handleAuth(Request request, Response response)  {
        String state = request.queryParams("state");
        String code = request.queryParams("code");

        if (state == null) {
            return "Invalid session state!";
        }

        if (code == null) {
            return "Could not obtain session code!";
        }

        UUID uuid = sessionCache.getIfPresent(state);
        sessionCache.invalidate(state);

        if (uuid == null) {
            return "Session timed out!";
        }

        try {
            HttpResponse<JsonNode> tokenRequest = Unirest.post(TOKEN)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .queryString("grant_type", "authorization_code")
                    .queryString("client_secret", clientSecret)
                    .queryString("client_id", clientId)
                    .queryString("redirect_uri", authRoute())
                    .queryString("code", code)
                    .asJson();

            String token = tokenRequest.getBody().getObject().getString("access_token");

            HttpResponse<JsonNode> user = Unirest.get(GET_USER)
                    .header("Authorization", "Bearer " + token)
                    .header("User-Agent", "login (n/a, 1.0)")
                    .asJson();

            String id = user.getBody().getObject().getString("id");
            AuthUserEvent event = AuthUserEvent.pass(uuid, id);
            PluginHelper.postEvent(event);
            return "<h1>Great success!</h1>";
        } catch (Throwable t) {
            AuthUserEvent event = AuthUserEvent.fail(uuid, t.getLocalizedMessage());
            PluginHelper.postEvent(event);
            return t.getLocalizedMessage();
        }
    }

    public static void create(UserStorage storage, String clientId, String clientSecret, String url, int port, Consumer<DiscordAuthService> callback) {
        DiscordAuthService service = new DiscordAuthService(storage, clientId, clientSecret, url, port);
        if (service.isRunning()) {
            callback.accept(service);
        }
    }
}
