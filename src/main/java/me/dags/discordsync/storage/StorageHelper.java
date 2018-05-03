package me.dags.discordsync.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * @author dags <dags@dags.me>
 */
public class StorageHelper {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static <T> T load(Path path, Class<T> type, Supplier<T> defVal) {
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                T t = GSON.fromJson(reader, type);
                if (t != null) {
                    write(t, path);
                    return t;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        T t = defVal.get();
        write(t, path);
        return t;
    }

    public static void write(Object o, Path path) {
        try {
            if (!Files.exists(path)) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(o, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
