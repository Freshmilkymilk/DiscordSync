package me.dags.discordsync;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author dags <dags@dags.me>
 */
public class PluginHelper {

    private static final PluginHelper instance = new PluginHelper();

    private final SpongeExecutorService sync;
    private final SpongeExecutorService async;
    private final Cause cause;

    private PluginHelper() {
        Object plugin = Sponge.getPluginManager().getPlugin(DiscordSync.ID).flatMap(PluginContainer::getInstance).orElseThrow(IllegalStateException::new);
        sync = Sponge.getScheduler().createSyncExecutor(plugin);
        async = Sponge.getScheduler().createAsyncExecutor(plugin);
        cause = Cause.source(plugin).build();
    }

    public static Cause getDefaultCause() {
        return getInstance().cause;
    }

    public static SpongeExecutorService getSync() {
        return getInstance().sync;
    }

    public static SpongeExecutorService getAsync() {
        return getInstance().async;
    }

    public static void sync(Runnable runnable) {
        getInstance().sync.submit(runnable);
    }

    public static void async(Runnable runnable) {
        getInstance().async.submit(runnable);
    }

    public static void async(Runnable async, Runnable sync) {
        async(() -> {
            async.run();
            sync(sync);
        });
    }

    public static <T> void async(Supplier<T> async, Consumer<T> sync, T defaultValue) {
        async(() -> {
            T t = async.get();
            if (t == null) {
                t = defaultValue;
            }
            T val = t;
            sync(() -> sync.accept(val));
        });
    }

    public static void postEvent(Event event) {
        sync(() -> Sponge.getEventManager().post(event));
    }

    private static PluginHelper getInstance() {
        return instance;
    }
}
