package me.dags.discordsync.event;

import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.service.user.UserStorageService;

import java.util.Optional;
import java.util.UUID;

/**
 * @author dags <dags@dags.me>
 */
public interface UserEvent extends Event {

    UUID getId();

    default Optional<User> getUser() {
        return Sponge.getServiceManager().provideUnchecked(UserStorageService.class).get(getId());
    }

    default Optional<Player> getPlayer() {
        return getUser().flatMap(User::getPlayer);
    }
}
