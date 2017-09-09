package me.dags.discordsync.event;

import me.dags.discordsync.PluginHelper;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

/**
 * @author dags <dags@dags.me>
 */
public class ChangeRoleEvent extends AbstractEvent implements Event {

    private final String role;
    private final String snowflake;

    private ChangeRoleEvent(String role, String snowflake) {
        this.role = role;
        this.snowflake = snowflake;
    }

    public String getRole() {
        return role;
    }

    public String getSubjectSnowflake() {
        return snowflake;
    }

    @Override
    public Cause getCause() {
        return PluginHelper.getInstance().getDefaultCause();
    }

    public static ChangeRoleEvent.Add add(String role, String snowflake) {
        return new Add(role, snowflake);
    }

    public static ChangeRoleEvent.Remove remove(String role, String snowflake) {
        return new Remove(role, snowflake);
    }

    public static class Add extends ChangeRoleEvent {

        private Add(String role, String snowflake) {
            super(role, snowflake);
        }
    }

    public static class Remove extends ChangeRoleEvent {

        private Remove(String role, String snowflake) {
            super(role, snowflake);
        }
    }
}
