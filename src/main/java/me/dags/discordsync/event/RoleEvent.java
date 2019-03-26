package me.dags.discordsync.event;

import me.dags.discordsync.PluginHelper;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.impl.AbstractEvent;

/**
 * @author dags <dags@dags.me>
 */
public class RoleEvent extends AbstractEvent implements Event {

    private final String role;
    private final String snowflake;

    private RoleEvent(String role, String snowflake) {
        this.role = role;
        this.snowflake = snowflake;
    }

    @Override
    public Cause getCause() {
        return PluginHelper.getDefaultCause();
    }

    public String getRole() {
        return role;
    }

    public String getSubjectSnowflake() {
        return snowflake;
    }

    public static Add add(String role, String snowflake) {
        return new Add(role, snowflake);
    }

    public static Remove remove(String role, String snowflake) {
        return new Remove(role, snowflake);
    }

    public static class Add extends RoleEvent {

        private Add(String role, String snowflake) {
            super(role, snowflake);
        }
    }

    public static class Remove extends RoleEvent {

        private Remove(String role, String snowflake) {
            super(role, snowflake);
        }
    }
}
