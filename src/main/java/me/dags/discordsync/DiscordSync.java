package me.dags.discordsync;

import com.google.inject.Inject;
import me.dags.commandbus.CommandBus;
import me.dags.commandbus.annotation.Command;
import me.dags.commandbus.annotation.Join;
import me.dags.commandbus.annotation.Permission;
import me.dags.commandbus.annotation.Src;
import me.dags.commandbus.fmt.Fmt;
import me.dags.discordsync.config.Channels;
import me.dags.discordsync.config.Config;
import me.dags.discordsync.service.DiscoService;
import me.dags.discordsync.service.JDAService;
import me.dags.discordsync.storage.StorageHelper;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;

import java.nio.file.Path;

@Plugin(id = DiscordSync.ID, name = "DiscordSync", version = "3.0", description = "Discord chat integration")
public class DiscordSync {

    public static final String ID = "discordsync";

    private final Path dir;

    private EventHandler eventHandler;

    @Inject
    public DiscordSync(@ConfigDir(sharedRoot = false) Path dir) {
        this.dir = dir;
    }

    @Listener
    public void onInit(GameInitializationEvent event) {
        CommandBus.create(this).register(this).submit();
        onReload(null);
    }

    @Listener
    public void onReload(GameReloadEvent event) {
        // load config files
        Config config = StorageHelper.load(dir.resolve("config.json"), Config.class, Config::new);
        Channels channels = StorageHelper.load(dir.resolve("channels.json"), Channels.class, Channels::new);

        // stop previous service
        Sponge.getServiceManager().provide(DiscoService.class).ifPresent(DiscoService::shutdown);

        // create new service and register
        JDAService.create(config.discord.botUserToken).ifPresent(service -> {
            EventHandler handler = new EventHandler(config, channels);
            if (eventHandler != null) {
                Sponge.getEventManager().unregisterListeners(eventHandler);
            }
            eventHandler = handler;
            Sponge.getEventManager().registerListeners(this, eventHandler);
            Sponge.getServiceManager().setProvider(this, DiscoService.class, service);
        });
    }

    @Permission
    @Command("discord reload")
    public void reloadCommand(@Src CommandSource src) {
        Fmt.info("reloading discord service...").tell(src);
        onReload(null);
    }

    @Permission
    @Command("discord test <message>")
    public void testCommand(@Src CommandSource src, @Join String message) {
        if (eventHandler != null) {
            Fmt.info("Sending test message: ").stress(message).tell(src);
            eventHandler.sendTestMessage(src, message);
        }
    }
}
