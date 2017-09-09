package me.dags.discordsync;

import me.dags.commandbus.fmt.Fmt;
import me.dags.discordsync.discord.DiscordAuthService;
import me.dags.discordsync.discord.DiscordClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.scheduler.SpongeExecutorService;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColors;
import org.spongepowered.api.text.format.TextStyles;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * @author dags <dags@dags.me>
 */
public class PluginHelper {

    private static final Logger logger = LoggerFactory.getLogger("DiscordSync");
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

    public static Text getAuthText(UUID uuid) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent()) {
            try {
                String url = authService.get().getSignUpURL(uuid);
                return Text.builder("Click me to authenticate your Discord account")
                        .color(TextColors.YELLOW)
                        .style(TextStyles.UNDERLINE)
                        .onClick(TextActions.openUrl(new URL(url)))
                        .build();

            } catch (MalformedURLException e) {
                return Text.of("Service is not available right now", TextColors.GRAY);
            }
        }
        return Text.of("Service is not available right now", TextColors.GRAY);
    }

    public static Text getPromptText() {
        return Text.builder("Use '/discord auth' to link your Discord account")
                .color(TextColors.YELLOW)
                .style(TextStyles.UNDERLINE)
                .onClick(TextActions.suggestCommand("/discord auth"))
                .build();
    }

    public static void syncRoles(User user, String snowflake) {
        Optional<DiscordClientService> service = Sponge.getServiceManager().provide(DiscordClientService.class);
        if (service.isPresent() && service.get().isConnected()) {
            final User subject = user;
            final DiscordClientService clientService = service.get();
            user.getPlayer().ifPresent(Fmt.subdued("Syncing your roles...")::tell);
            clientService.getRolesAsync(snowflake, roles -> {
                getSubjectData(user).clearParents();
                addRoles(subject, roles, false);
                user.getPlayer().ifPresent(Fmt.subdued("Syncing complete!")::tell);
            });
        }
    }

    public static void addRoles(User user, Set<String> roles, boolean notify) {
        logger.info("Adding roles: {} to user: {}", roles, user.getName());
        int matches = 0;
        Optional<Player> player = user.getPlayer();
        SubjectData subjectData = getSubjectData(user);
        PermissionService perms = Sponge.getServiceManager().provideUnchecked(PermissionService.class);
        for (Subject group : perms.getGroupSubjects().getAllSubjects()) {
            if (roles.contains(group.getIdentifier().toLowerCase())) {
                subjectData.addParent(SubjectData.GLOBAL_CONTEXT, group);
                if (notify) {
                    player.ifPresent(Fmt.subdued("You have been added to group ").stress(group.getIdentifier())::tell);
                }
                if (++matches > roles.size()) {
                    break;
                }
            }
        }
    }

    public static void removeRoles(User user, Set<String> roles, boolean notify) {
        logger.info("Removing roles: {} from user: {}", roles, user.getName());
        int matches = 0;
        Optional<Player> player = user.getPlayer();
        SubjectData subjectData = getSubjectData(user);
        PermissionService perms = Sponge.getServiceManager().provideUnchecked(PermissionService.class);
        for (Subject group : perms.getGroupSubjects().getAllSubjects()) {
            if (roles.contains(group.getIdentifier().toLowerCase())) {
                subjectData.removeParent(SubjectData.GLOBAL_CONTEXT, group);
                if (notify) {
                    player.ifPresent(Fmt.subdued("You have been removed from group ").stress(group.getIdentifier())::tell);
                }
                if (++matches > roles.size()) {
                    break;
                }
            }
        }
    }

    public static boolean hasPublicChannel(MessageChannelEvent event) {
        MessageChannel c = event.getChannel().orElse(MessageChannel.TO_NONE);
        return c == MessageChannel.TO_ALL
                || c == MessageChannel.TO_PLAYERS
                || c == Sponge.getServer().getBroadcastChannel()
                || c.getClass().getSimpleName().equals("BoopableChannel");
    }

    private static SubjectData getSubjectData(Subject subject) {
        return subject.getTransientSubjectData();
    }
}
