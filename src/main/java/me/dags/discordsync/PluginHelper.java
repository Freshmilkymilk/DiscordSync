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

    private final Cause cause;
    private final Object plugin;
    private SpongeExecutorService sync;
    private SpongeExecutorService async;

    private PluginHelper() {
        plugin = Sponge.getPluginManager().getPlugin(DiscordSync.ID).flatMap(PluginContainer::getInstance).orElseThrow(IllegalStateException::new);
        cause = Cause.source(plugin).build();
    }

    private synchronized SpongeExecutorService getSyncService() {
        if (sync == null || sync.isShutdown()) {
            sync = Sponge.getScheduler().createSyncExecutor(plugin);
        }
        return sync;
    }

    private synchronized SpongeExecutorService getAsyncService() {
        if (async == null || async.isShutdown()) {
            async = Sponge.getScheduler().createAsyncExecutor(plugin);
        }
        return async;
    }

    private synchronized void shutdownServices() {
        async.shutdown();
        sync.shutdown();
    }

    public static Cause getDefaultCause() {
        return getInstance().cause;
    }

    public static void shutdown() {
        getInstance().shutdownServices();
    }

    public static SpongeExecutorService getSync() {
        return getInstance().getSyncService();
    }

    public static SpongeExecutorService getAsync() {
        return getInstance().getAsyncService();
    }

    public static void sync(Runnable runnable) {
        getSync().submit(runnable);
    }

    public static void async(Runnable runnable) {
        getAsync().submit(runnable);
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
