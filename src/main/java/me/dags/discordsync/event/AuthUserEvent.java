package me.dags.discordsync.event;

import java.util.UUID;
import me.dags.discordsync.PluginHelper;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

/**
 * @author dags <dags@dags.me>
 */
public class AuthUserEvent extends AbstractEvent implements UserEvent {

    private final UUID uuid;
    private final String getSnowflake;

    private AuthUserEvent(UUID uuid, String id) {
        this.uuid = uuid;
        this.getSnowflake = id;
    }

    @Override
    public UUID getId() {
        return uuid;
    }

    @Override
    public Cause getCause() {
        return PluginHelper.getDefaultCause();
    }

    public String getSnowflake() {
        return getSnowflake;
    }

    public static AuthUserEvent pass(UUID uuid, String id) {
        return new Pass(uuid, id);
    }

    public static AuthUserEvent fail(UUID uuid, String reason) {
        return new Fail(uuid, reason);
    }

    public static class Pass extends AuthUserEvent {

        private Pass(UUID uuid, String id) {
            super(uuid, id);
        }
    }

    public static class Fail extends AuthUserEvent {

        private final String reason;

        private Fail(UUID uuid, String reason) {
            super(uuid, "");
            this.reason = reason;
        }

        public String getReason() {
            return reason;
        }
    }
}
