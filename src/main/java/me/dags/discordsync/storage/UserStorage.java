package me.dags.discordsync.storage;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * @author dags <dags@dags.me>
 */
public interface UserStorage {

    String getId(String snowflake);

    String getSnowFlake(String uuid);

    void setUser(String snowflake, UUID uuid);

    void iterate(BiConsumer<UUID, String> consumer);

    default Optional<UUID> getUserId(String snowflake) {
        String id = getId(snowflake);
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(id));
    }

    default Optional<String> getUserSnowflake(UUID uuid) {
        String snowflake = getSnowFlake(uuid.toString());
        if (snowflake == null || snowflake.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(snowflake);
    }
}
