package me.dags.discordsync.discord;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.hash.Hashing;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import me.dags.discordsync.PluginHelper;
import me.dags.discordsync.event.AuthUserEvent;
import org.javacord.api.entity.message.Message;
import org.javacord.api.entity.user.User;

/**
 * @author dags <dags@dags.me>
 */
public class DiscordCommands {

    private static final DiscordCommands INSTANCE = new DiscordCommands();

    private final Cache<String, UUID> cache = Caffeine.newBuilder().expireAfterAccess(5, TimeUnit.MINUTES).build();

    private DiscordCommands() {

    }

    public void process(Message message) {
        String[] args = message.getContent().split(" ");
        message.getUserAuthor().ifPresent(user -> {
            if (auth(user, args)) {
                message.delete();
            }
        });
    }

    public Optional<UUID> retrieveId(String token) {
        return Optional.ofNullable(cache.getIfPresent(token));
    }

    public String generateToken(UUID uuid) {
        String token = Hashing.goodFastHash(32).newHasher()
                .putString(uuid.toString(), Charset.defaultCharset())
                .putLong(System.currentTimeMillis())
                .hash().toString();
        cache.put(token, uuid);
        return token;
    }

    private boolean auth(User user, String... args) {
        if (args.length != 2 || !args[0].equalsIgnoreCase("auth")) {
            return false;
        }

        Optional<UUID> id = retrieveId(args[1]);
        if (!id.isPresent()) {
            return false;
        }

        UUID uuid = id.get();
        String snowflake = user.getIdAsString();
        PluginHelper.postEvent(AuthUserEvent.pass(uuid, snowflake));
        return true;
    }

    public static DiscordCommands getInstance() {
        return INSTANCE;
    }
}
