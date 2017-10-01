package me.dags.discordsync;

import com.google.common.collect.ImmutableList;
import me.dags.commandbus.CommandBus;
import me.dags.commandbus.annotation.Command;
import me.dags.commandbus.annotation.Permission;
import me.dags.commandbus.annotation.Src;
import me.dags.commandbus.fmt.Fmt;
import me.dags.discordsync.discord.DiscordAuthService;
import me.dags.discordsync.discord.DiscordChannel;
import me.dags.discordsync.discord.DiscordClientService;
import me.dags.discordsync.discord.DiscordMessageService;
import me.dags.discordsync.event.AuthUserEvent;
import me.dags.discordsync.event.ChangeRoleEvent;
import me.dags.discordsync.storage.FileUserStorage;
import me.dags.discordsync.storage.StorageHelper;
import me.dags.textmu.MarkupSpec;
import me.dags.textmu.MarkupTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.config.ConfigDir;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.filter.cause.Root;
import org.spongepowered.api.event.game.GameReloadEvent;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.event.game.state.GameStoppingServerEvent;
import org.spongepowered.api.event.message.MessageChannelEvent;
import org.spongepowered.api.event.network.ClientConnectionEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;
import org.spongepowered.api.service.permission.SubjectData;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.action.TextActions;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColors;

import javax.inject.Inject;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Plugin(id = "discordsync")
public class DiscordSync {

    public static final String ID = "discordsync";
    public static final String ROLE_PERMISSION = "discordsync.role.%s";
    private static final Logger logger = LoggerFactory.getLogger("DiscordSync");

    private final Path configDir;
    private final MarkupSpec spec;
    private MarkupTemplate prompt;
    private MarkupTemplate auth;
    private MarkupTemplate add;

    @Inject
    public DiscordSync(@ConfigDir(sharedRoot = false) Path path) {
        configDir = path;
        spec = MarkupSpec.create();
        prompt = spec.template("[blue](Use [gold,underline,/discord auth](/discord auth) to link your Discord account)");
        auth = spec.template("[blue]([gold,underline,{url}](Click me) to authenticate your account)");
    }

    @Permission
    @Command("discord bot")
    public void add(@Src Player player) {
        Config config = StorageHelper.load(configDir.resolve("config.json"), Config.class, Config::new);
        String address = String.format(DiscordAuthService.ADD_BOT, config.discord.botClientId);
        Text message = add.with("url", address).render();
        player.sendMessage(message);
    }

    @Permission
    @Command("discord auth")
    public void auth(@Src Player player) throws MalformedURLException {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent()) {
            String url = authService.get().getSignUpURL(player.getUniqueId());
            Text message = auth.applier().render().toBuilder().onClick(TextActions.openUrl(new URL(url))).build();
            player.sendMessage(message);
        } else {
            Fmt.error("Service not available right now");
        }
    }

    @Permission
    @Command("discord reload")
    public void reload(@Src CommandSource source) {
        Fmt.info("Reloading...").stress(source);
        refresh(null);
    }

    @Listener
    public void init(GameInitializationEvent event) {
        CommandBus.create(this).register(this).submit();
        refresh(null);
    }

    @Listener
    public void refresh(GameReloadEvent event) {
        Sponge.getServiceManager().provide(DiscordAuthService.class).ifPresent(DiscordAuthService::stop);
        Sponge.getServiceManager().provide(DiscordClientService.class).ifPresent(DiscordClientService::disconnect);

        Config config = StorageHelper.load(configDir.resolve("config.json"), Config.class, Config::new);
        prompt = spec.template(config.prompts.prompt);
        auth = spec.template(config.prompts.auth);
        add = spec.template(config.prompts.add);

        Channels channels = StorageHelper.load(configDir.resolve("channels.json"), Channels.class, Channels::new);
        DiscordChannel.Format format = new DiscordChannel.Format(channels.main.discord);
        DiscordChannel channel = new DiscordChannel(channels.main.channelId, channels.main.minecraft.message, channels.main.webhook);

        FileUserStorage users = new FileUserStorage(configDir.resolve("users.json"));
        DiscordAuthService.create(users, config, authService -> {
            Sponge.getServiceManager().setProvider(this, DiscordAuthService.class, authService);
            authService.startSyncRolesTask(ImmutableList.copyOf(config.server.roles));
        });

        DiscordMessageService messageService = DiscordMessageService.create(config.server.name, config.server.avatar, format, channel);
        Sponge.getServiceManager().setProvider(this, DiscordMessageService.class, messageService);

        DiscordClientService.create(config.discord.guildId, config.discord.botUserToken, messageService, clientService -> {
            Sponge.getServiceManager().setProvider(this, DiscordClientService.class, clientService);
        });

        users.save();
    }

    @Listener
    public void stop(GameStoppingServerEvent event) {
        Sponge.getServiceManager().provide(DiscordMessageService.class).ifPresent(DiscordMessageService::sendStopping);
    }

    @Listener
    public void join(ClientConnectionEvent.Join event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscordMessageService.class)
                .ifPresent(discordMessageService -> discordMessageService.sendConnect(player.getName()));

        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent()) {
            Optional<String> snowflake = authService.get().getSnowflake(player.getUniqueId());
            if (snowflake.isPresent()) {
                syncRoles(player, snowflake.get());
            } else {
                Text message = prompt.applier().render();
                player.sendMessage(message);
            }
        }
    }

    @Listener
    public void quit(ClientConnectionEvent.Disconnect event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscordMessageService.class)
                .ifPresent(messageService -> messageService.sendDisconnect(player.getName()));
    }

    @Listener
    public void chat(MessageChannelEvent.Chat event, @Root Player player) {
        Sponge.getServiceManager().provide(DiscordMessageService.class).ifPresent(messageService -> {
            if (hasPublicChannel(event)) {
                String name = player.getName();
                String message = event.getRawMessage().toPlain();
                messageService.sendMessage(name, message);
            }
        });
    }

    @Listener
    public void authPass(AuthUserEvent.Pass event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            authService.get().getStorage().setUser(event.getSnowflake(), event.getId());
            event.getUser().ifPresent(user -> {
                syncRoles(user, event.getSnowflake());
                user.getPlayer().ifPresent(player -> {
                    Text text = Text.of("Authentication succeeded!", TextColors.GREEN);
                    player.sendMessage(text);
                });
            });
        }
    }

    @Listener
    public void authFail(AuthUserEvent.Fail event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            event.getPlayer().ifPresent(player -> {
                Text text = Text.of("Authentication failed: ", event.getReason(), TextColors.RED);
                player.sendMessage(text);
            });
        }
    }

    @Listener
    public void roleAdd(ChangeRoleEvent.Add event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            Optional<User> user = authService.get().getUser(event.getSubjectSnowflake());
            user.ifPresent(u -> addRoles(u, Collections.singleton(event.getRole()), true));
        }
    }

    @Listener
    public void roleRemove(ChangeRoleEvent.Remove event) {
        Optional<DiscordAuthService> authService = Sponge.getServiceManager().provide(DiscordAuthService.class);
        if (authService.isPresent() && authService.get().isRunning()) {
            Optional<User> user = authService.get().getUser(event.getSubjectSnowflake());
            user.ifPresent(u -> removeRoles(u, Collections.singleton(event.getRole()), true));
        }
    }

    private void syncRoles(User user, String snowflake) {
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

    private void addRoles(User user, Set<String> roles, boolean notify) {
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

    private void removeRoles(User user, Set<String> roles, boolean notify) {
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

    private boolean hasPublicChannel(MessageChannelEvent event) {
        MessageChannel c = event.getChannel().orElse(MessageChannel.TO_NONE);
        return c == MessageChannel.TO_ALL
                || c == MessageChannel.TO_PLAYERS
                || c == Sponge.getServer().getBroadcastChannel()
                || c.getClass().getSimpleName().equals("BoopableChannel");
    }

    public static SubjectData getSubjectData(Subject subject) {
        return subject.getTransientSubjectData();
    }
}
