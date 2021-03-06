package me.dags.discordsync;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.EventContext;
import org.spongepowered.api.event.cause.EventContextKeys;
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
        PluginContainer container = Sponge.getPluginManager().getPlugin(DiscordSync.ID).orElseThrow(IllegalStateException::new);
        plugin = container.getInstance().orElseThrow(IllegalStateException::new);
        cause = Cause.of(EventContext.builder().add(EventContextKeys.PLUGIN, container).build(), this);
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

    public static SpongeExecutorService getAsync() {
        return getInstance().getAsyncService();
    }

    private synchronized SpongeExecutorService getAsyncService() {
        if (async == null || async.isShutdown()) {
            async = Sponge.getScheduler().createAsyncExecutor(plugin);
        }
        return async;
    }

    public static Cause getDefaultCause() {
        return getInstance().cause;
    }

    private static PluginHelper getInstance() {
        return instance;
    }

    public static SpongeExecutorService getSync() {
        return getInstance().getSyncService();
    }

    private synchronized SpongeExecutorService getSyncService() {
        if (sync == null || sync.isShutdown()) {
            sync = Sponge.getScheduler().createSyncExecutor(plugin);
        }
        return sync;
    }

    public static void postEvent(Event event) {
        sync(() -> Sponge.getEventManager().post(event));
    }

    public static void shutdown() {
        getInstance().shutdownServices();
    }

    private synchronized void shutdownServices() {
        if (async != null) {
            async.shutdown();
        }
        if (sync != null) {
            sync.shutdown();
        }
    }

    public static void sync(Runnable runnable) {
        getSync().submit(runnable);
    }
}
