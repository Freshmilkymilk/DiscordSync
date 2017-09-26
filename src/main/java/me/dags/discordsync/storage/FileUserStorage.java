package me.dags.discordsync.storage;

import com.google.gson.JsonElement;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * @author dags <dags@dags.me>
 */
public class FileUserStorage extends JsonConfig implements UserStorage {

    public FileUserStorage(Path path) {
        super(path);
    }

    @Override
    public void iterate(BiConsumer<UUID, String> consumer) {
        JsonElement users = getRoot().get("uuid");
        if (users == null || !users.isJsonObject()) {
            return;
        }

        for (Map.Entry<String, JsonElement> entry : users.getAsJsonObject().entrySet()) {
            UUID uuid = UUID.fromString(entry.getKey());
            String snowflake = entry.getValue().getAsString();
            consumer.accept(uuid, snowflake);
        }
    }

    @Override
    public void setUser(String snowflake, UUID uuid) {
        String id = uuid.toString();
        set(id, "snowflake", snowflake);
        set(snowflake, "uuid", id);
        save();
    }

    @Override
    public String getId(String snowflake) {
        return get("", "snowflake", snowflake);
    }

    @Override
    public String getSnowFlake(String uuid) {
        return get("", "uuid", uuid);
    }
}
