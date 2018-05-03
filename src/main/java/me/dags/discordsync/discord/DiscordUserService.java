package me.dags.discordsync.discord;

import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import me.dags.discordsync.DiscordSync;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.storage.UserStorage;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.service.user.UserStorageService;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordUserService {

    public static final String ADD_BOT = "https://discordapp.com/api/oauth2/authorize?client_id=%s&scope=bot";

    private final UserStorage storage;
    private final boolean running;

    private DiscordUserService(UserStorage storage) {
        this.storage = storage;
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

    public void stop() {

    }

    public void startSyncRolesTask(ImmutableList<String> roles) {
        PluginHelper.getAsync().scheduleAtFixedRate(() -> syncRoles(roles), 5, 30, TimeUnit.SECONDS);
    }

    private boolean start() {
        return true;
    }

    private void syncRoles(List<String> roles) {
        Optional<DiscordClientService> clientService = Sponge.getServiceManager().provide(DiscordClientService.class);
        if (clientService.isPresent()) {
            DiscordClientService client = clientService.get();
            PermissionService service = Sponge.getServiceManager().provideUnchecked(PermissionService.class);
            getStorage().iterate((uuid, snowflake) -> {
                Optional<Subject> subject = service.getUserSubjects().getSubject(uuid.toString());
                if (!subject.isPresent()) {
                    return;
                }
                Map<String, Boolean> values = new HashMap<>(roles.size());
                Map<String, Boolean> permissions = DiscordSync.getSubjectData(subject.get()).getPermissions(SubjectData.GLOBAL_CONTEXT);
                for (String role : roles) {
                    String node = String.format(DiscordSync.ROLE_PERMISSION, role);
                    values.put(role, permissions.getOrDefault(node, false));
                }
                client.syncRoles(snowflake, values);
            });
        }
    }

    public static void create(UserStorage storage, Consumer<DiscordUserService> callback) {
        DiscordUserService service = new DiscordUserService(storage);
        if (service.isRunning()) {
            callback.accept(service);
        }
    }
}
