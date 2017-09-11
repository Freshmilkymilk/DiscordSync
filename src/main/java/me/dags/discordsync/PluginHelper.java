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

    public Cause getDefaultCause() {
        return cause;
    }

    public SpongeExecutorService getSync() {
        return sync;
    }

    public SpongeExecutorService getAsync() {
        return async;
    }

    public void sync(Runnable runnable) {
        sync.submit(runnable);
    }

    public void async(Runnable runnable) {
        async.submit(runnable);
    }

    public void async(Runnable async, Runnable sync) {
        async(() -> {
            async.run();
            sync(sync);
        });
    }

    public <T> void async(Supplier<T> async, Consumer<T> sync, T defaultValue) {
        async(() -> {
            T t = async.get();
            if (t == null) {
                t = defaultValue;
            }
            T val = t;
            sync(() -> sync.accept(val));
        });
    }

    public void postEvent(Event event) {
        sync(() -> Sponge.getEventManager().post(event));
    }

    public static PluginHelper getInstance() {
        return instance;
    }
}
