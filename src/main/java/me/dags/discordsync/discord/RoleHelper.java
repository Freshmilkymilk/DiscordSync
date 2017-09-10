package me.dags.discordsync.discord;

import de.btobastian.javacord.entities.Server;
import de.btobastian.javacord.entities.User;
import de.btobastian.javacord.entities.permissions.Role;

import java.util.LinkedList;
import java.util.List;

public class RoleHelper {

    public void addRole(Server server, User user, String name) {
        List<Role> roles = new LinkedList<>();
        for (Role role : user.getRoles(server)) {
            if (role.getName().equals(name)) {
                return;
            }
            roles.add(role);
        }

        for (Role role : server.getRoles()) {
            if (role.getName().equals(name)) {
                roles.add(role);
            }
        }

        server.updateRoles(user, roles.toArray(new Role[roles.size()]));
    }

    public void removeRole(Server server, User user, String name) {
        List<Role> roles = new LinkedList<>();
        for (Role role : user.getRoles(server)) {
            if (role.getName().equals(name)) {
                continue;
            }
            roles.add(role);
        }
        server.updateRoles(user, roles.toArray(new Role[roles.size()]));
    }
}
