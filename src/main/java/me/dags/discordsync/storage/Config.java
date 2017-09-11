package me.dags.discordsync.storage;

import com.google.gson.*;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * @author dags <dags@dags.me>
 */
public class Config {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path path;
    private final JsonObject root;

    public Config(Path path) {
        this.path = path;
        this.root = load(path);
    }

    public JsonObject getRoot() {
        return root;
    }

    public Config set(String value, String... path) {
        return set(new JsonPrimitive(value), path);
    }

    public Config set(Number value, String... path) {
        return set(new JsonPrimitive(value), path);
    }

    public Config set(Boolean value, String... path) {
        return set(new JsonPrimitive(value), path);
    }

    public String get(String defaultVal, String... path) {
        return mustValue(new JsonPrimitive(defaultVal), path).getAsString();
    }

    public int get(int defaultVal, String... path) {
        return mustValue(new JsonPrimitive(defaultVal), path).getAsInt();
    }

    public double get(double defaultVal, String... path) {
        return mustValue(new JsonPrimitive(defaultVal), path).getAsDouble();
    }

    public boolean get(boolean defaultVal, String... path) {
        return mustValue(new JsonPrimitive(defaultVal), path).getAsBoolean();
    }

    public <T> Iterable<T> getList(Function<JsonElement, T> mapper, String... path) {
        JsonObject parent = mustParent(path);
        String key = getLastKey(path);
        JsonArray last = parent.getAsJsonArray(key);
        if (last == null || !last.isJsonArray()) {
            last = new JsonArray();
            parent.add(key, last);
        }
        List<T> list = new LinkedList<>();
        for (JsonElement element : last) {
            T t = mapper.apply(element);
            if (t != null) {
                list.add(t);
            }
        }
        return list;
    }

    public Config save() {
        save(root, path);
        return this;
    }

    private Config set(JsonElement element, String... path) {
        JsonObject parent = mustParent(path);
        String key = getLastKey(path);
        parent.add(key, element);
        return this;
    }

    private JsonObject mustParent(String... path) {
        JsonObject parent = root;
        for (int i = 0; i < path.length - 1; i++) {
            String key = path[i];
            JsonElement child = parent.get(key);
            if (child == null || !child.isJsonObject()) {
                child = new JsonObject();
                parent.add(key, child);
            }
            parent = child.getAsJsonObject();
        }
        return parent;
    }

    private JsonElement mustValue(JsonElement defaultVal, String... path) {
        JsonObject parent = mustParent(path);

        String key = getLastKey(path);
        JsonElement value = parent.get(key);
        if (value == null) {
            value = defaultVal;
            parent.add(key, value);
        }

        return value;
    }

    private String getLastKey(String... path) {
        return path[path.length - 1];
    }

    @Nonnull
    private static JsonObject load(Path path) {
        JsonObject root;

        path = path.toAbsolutePath();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement element = new JsonParser().parse(reader);
                if (element.isJsonObject()) {
                    root = element.getAsJsonObject();
                } else{
                    root = new JsonObject();
                }
            } catch (IOException e) {
                e.printStackTrace();
                root = new JsonObject();
            }
        } else {
            root = new JsonObject();
        }

        return root;
    }

    private static void save(JsonObject root, Path path) {
        path = path.toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
