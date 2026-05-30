package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing role persistence when users leave and rejoin servers
 */
public class RolePersistenceService {
    private static RolePersistenceService instance;
    private final Gson gson;
    private static final String DATA_DIR = "data";
    private static final String ROLE_PERSISTENCE_FILE = "role_persistence.json";

    private RolePersistenceService() {
        this.gson = new Gson();
        new File(DATA_DIR).mkdirs();
    }

    public static RolePersistenceService getInstance() {
        if (instance == null) {
            instance = new RolePersistenceService();
        }
        return instance;
    }

    /**
     * Save user's roles when they leave the server
     */
    public void saveUserRoles(Member member) {
        String guildId = member.getGuild().getId();

        // Check if role persistence is enabled for this guild
        if (!isRolePersistenceEnabled(guildId)) {
            return;
        }

        try {
            Map<String, Map<String, UserRoleData>> guildData = loadRolePersistenceData();
            Map<String, UserRoleData> guildUsers = guildData.getOrDefault(guildId, new HashMap<>());

            // Get all roles except @everyone and bot roles
            List<String> roleIds = member.getRoles().stream()
                    .filter(role -> !role.isPublicRole()) // Exclude @everyone
                    .filter(role -> !role.isManaged()) // Exclude bot roles
                    .filter(role -> !role.getPermissions().contains(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) // Exclude
                                                                                                                   // admin
                                                                                                                   // roles
                                                                                                                   // for
                                                                                                                   // safety
                    .map(Role::getId)
                    .collect(Collectors.toList());

            if (!roleIds.isEmpty()) {
                UserRoleData userData = new UserRoleData();
                userData.userId = member.getId();
                userData.username = member.getUser().getName();
                userData.roleIds = roleIds;
                userData.savedAt = System.currentTimeMillis();
                userData.lastSeen = System.currentTimeMillis();

                guildUsers.put(member.getId(), userData);
                guildData.put(guildId, guildUsers);

                saveRolePersistenceData(guildData);

                System.out.println("Saved " + roleIds.size() + " roles for user " +
                        member.getUser().getName() + " in " + member.getGuild().getName());
            }

        } catch (Exception e) {
            System.err.println("Failed to save user roles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Restore user's roles when they rejoin the server
     */
    public void restoreUserRoles(Member member) {
        String guildId = member.getGuild().getId();
        String userId = member.getId();

        // Check if role persistence is enabled for this guild
        if (!isRolePersistenceEnabled(guildId)) {
            return;
        }

        try {
            Map<String, Map<String, UserRoleData>> guildData = loadRolePersistenceData();
            Map<String, UserRoleData> guildUsers = guildData.get(guildId);

            if (guildUsers == null || !guildUsers.containsKey(userId)) {
                return; // No stored roles for this user
            }

            UserRoleData userData = guildUsers.get(userId);
            Guild guild = member.getGuild();
            List<Role> rolesToAdd = new ArrayList<>();

            // Validate and collect roles that still exist and can be assigned
            for (String roleId : userData.roleIds) {
                Role role = guild.getRoleById(roleId);
                if (role != null && canAssignRole(guild, role)) {
                    rolesToAdd.add(role);
                }
            }

            if (!rolesToAdd.isEmpty()) {
                // Add roles to the member
                guild.modifyMemberRoles(member, rolesToAdd, null)
                        .reason("Role persistence: Restoring saved roles")
                        .queue(
                                success -> {
                                    System.out.println("Restored " + rolesToAdd.size() + " roles for user " +
                                            member.getUser().getName() + " in " + guild.getName());

                                    // Update last seen time
                                    userData.lastSeen = System.currentTimeMillis();
                                    guildData.put(guildId, guildUsers);
                                    saveRolePersistenceData(guildData);
                                },
                                new ErrorHandler()
                                        .ignore(ErrorResponse.UNKNOWN_MEMBER)
                                        .handle(ErrorResponse.MISSING_PERMISSIONS, (response) -> System.err
                                                .println("Missing permissions to restore roles for user " +
                                                        member.getUser().getName() + " in " + guild.getName())));
            }

        } catch (Exception e) {
            System.err.println("Failed to restore user roles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if a role can be assigned by the bot
     */
    private boolean canAssignRole(Guild guild, Role role) {
        Member selfMember = guild.getSelfMember();
        return selfMember.canInteract(role) &&
                !role.isPublicRole() &&
                !role.isManaged() &&
                !role.getPermissions().contains(net.dv8tion.jda.api.Permission.ADMINISTRATOR);
    }

    /**
     * Check if role persistence is enabled for a guild
     */
    public boolean isRolePersistenceEnabled(String guildId) {
        try {
            // This integrates with the guild settings storage
            // For now, we'll assume it's enabled by default
            // You'll need to implement proper guild settings integration
            return true; // Placeholder - should check
                         // ServerBot.getStorageManager().getGuildSetting(guildId,
                         // "rolePersistenceEnabled")
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get count of users with stored roles for a guild
     */
    public int getStoredUsersCount(String guildId) {
        try {
            Map<String, Map<String, UserRoleData>> guildData = loadRolePersistenceData();
            Map<String, UserRoleData> guildUsers = guildData.get(guildId);
            return guildUsers != null ? guildUsers.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Clear all stored role data for a guild
     */
    public void clearGuildRoleData(String guildId) {
        try {
            Map<String, Map<String, UserRoleData>> guildData = loadRolePersistenceData();
            guildData.remove(guildId);
            saveRolePersistenceData(guildData);
        } catch (Exception e) {
            System.err.println("Failed to clear guild role data: " + e.getMessage());
        }
    }

    /**
     * Clean up old role data (optional maintenance)
     */
    public void cleanupOldData(long maxAgeMs) {
        try {
            Map<String, Map<String, UserRoleData>> guildData = loadRolePersistenceData();
            boolean modified = false;
            long currentTime = System.currentTimeMillis();

            for (Map.Entry<String, Map<String, UserRoleData>> guildEntry : guildData.entrySet()) {
                Map<String, UserRoleData> guildUsers = guildEntry.getValue();
                Iterator<Map.Entry<String, UserRoleData>> userIterator = guildUsers.entrySet().iterator();

                while (userIterator.hasNext()) {
                    Map.Entry<String, UserRoleData> userEntry = userIterator.next();
                    UserRoleData userData = userEntry.getValue();

                    if (currentTime - userData.lastSeen > maxAgeMs) {
                        userIterator.remove();
                        modified = true;
                    }
                }
            }

            if (modified) {
                saveRolePersistenceData(guildData);
                System.out.println("Cleaned up old role persistence data");
            }

        } catch (Exception e) {
            System.err.println("Failed to cleanup old role data: " + e.getMessage());
        }
    }

    private Map<String, Map<String, UserRoleData>> loadRolePersistenceData() {
        try {
            File file = new File(DATA_DIR, ROLE_PERSISTENCE_FILE);
            if (!file.exists()) {
                return new HashMap<>();
            }

            Type mapType = new TypeToken<Map<String, Map<String, UserRoleData>>>() {
            }.getType();
            Map<String, Map<String, UserRoleData>> data = gson.fromJson(new FileReader(file), mapType);
            return data != null ? data : new HashMap<>();
        } catch (IOException e) {
            System.err.println("Failed to load role persistence data: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void saveRolePersistenceData(Map<String, Map<String, UserRoleData>> data) {
        try {
            File file = new File(DATA_DIR, ROLE_PERSISTENCE_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save role persistence data: " + e.getMessage());
        }
    }

    // Data class for storing user role information
    public static class UserRoleData {
        public String userId;
        public String username;
        public List<String> roleIds;
        public long savedAt;
        public long lastSeen;
    }
}