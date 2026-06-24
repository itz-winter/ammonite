package com.serverbot.utils;

import com.serverbot.ServerBot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Advanced permission management system based on permission nodes
 */
public class PermissionManager {

    private static final Logger logger = LoggerFactory.getLogger(PermissionManager.class);

    // Default permissions (true = allowed by default for @everyone)
    private static final Map<String, Boolean> DEFAULT_PERMISSIONS = new HashMap<>();

    // Mapping from Discord permissions to bot permission nodes
    // If a member's Discord roles grant a Discord permission, they automatically
    // get the mapped bot nodes
    private static final Map<Permission, Set<String>> DISCORD_PERMISSION_MAPPING = new EnumMap<>(Permission.class);

    static {
        // Initialize default permissions
        initializeDefaultPermissions();
        // Initialize Discord permission -> bot node mapping
        initializeDiscordPermissionMapping();
    }

    private static void initializeDefaultPermissions() {
        // Global wildcard permission
        DEFAULT_PERMISSIONS.put("*", false);

        // General Permissions
        DEFAULT_PERMISSIONS.put("permissions.admin", false);
        DEFAULT_PERMISSIONS.put("permissions.view", false);
        DEFAULT_PERMISSIONS.put("admin.permissions", false); // Used by /permissions command
        DEFAULT_PERMISSIONS.put("admin.config", false);
        DEFAULT_PERMISSIONS.put("admin.server", false);

        // Automod Commands (all false by default)
        DEFAULT_PERMISSIONS.put("automod.*", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.*", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.autodelete", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.capslimit", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.duplicatelimit", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.mentionlimit", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.messagelimit", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.punishment", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.timeoutduration", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.muteduration", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.banduration", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.view", false);
        DEFAULT_PERMISSIONS.put("automod.antispam.bypass", false); // bypass antispam check (admins get this
                                                                   // automatically)
        DEFAULT_PERMISSIONS.put("automod.viewsettings", false);
        DEFAULT_PERMISSIONS.put("automod.edit", false);

        // Moderation Commands (all false by default)
        DEFAULT_PERMISSIONS.put("mod.*", false);
        DEFAULT_PERMISSIONS.put("mod.basic", false);
        DEFAULT_PERMISSIONS.put("mod.admin", false);
        DEFAULT_PERMISSIONS.put("mod.warn", false);
        DEFAULT_PERMISSIONS.put("mod.kick", false);
        DEFAULT_PERMISSIONS.put("mod.ban", false);
        DEFAULT_PERMISSIONS.put("mod.timeout", false);
        DEFAULT_PERMISSIONS.put("mod.mute", false);
        DEFAULT_PERMISSIONS.put("mod.hist", false);
        DEFAULT_PERMISSIONS.put("mod.lockdown", false);
        DEFAULT_PERMISSIONS.put("mod.softban", false);
        DEFAULT_PERMISSIONS.put("mod.purge.messages", false);
        DEFAULT_PERMISSIONS.put("mod.purge.members", false);

        // Economy Commands
        DEFAULT_PERMISSIONS.put("economy.*", false);
        DEFAULT_PERMISSIONS.put("economy.setbalance", false);
        DEFAULT_PERMISSIONS.put("economy.addbalance", false);
        DEFAULT_PERMISSIONS.put("economy.subtractbalance", false);
        DEFAULT_PERMISSIONS.put("economy.bank.setbalance", false);
        DEFAULT_PERMISSIONS.put("economy.bank.addbalance", false);
        DEFAULT_PERMISSIONS.put("economy.bank.subtractbalance", false);
        DEFAULT_PERMISSIONS.put("economy.bank.loan", false);
        DEFAULT_PERMISSIONS.put("economy.bank.settings", false);
        DEFAULT_PERMISSIONS.put("economy.work.*", false);
        DEFAULT_PERMISSIONS.put("economy.work.use", true);
        DEFAULT_PERMISSIONS.put("economy.work.settings", false);
        DEFAULT_PERMISSIONS.put("economy.transaction.*", false);
        DEFAULT_PERMISSIONS.put("economy.transaction.chargeback", false);
        DEFAULT_PERMISSIONS.put("economy.transaction.pay", true);
        DEFAULT_PERMISSIONS.put("economy.gambling.*", false);
        DEFAULT_PERMISSIONS.put("economy.gambling.dice", true);
        DEFAULT_PERMISSIONS.put("economy.gambling.coin", true);
        DEFAULT_PERMISSIONS.put("economy.gambling.blackjack", true);
        DEFAULT_PERMISSIONS.put("economy.gambling.slots", true);
        DEFAULT_PERMISSIONS.put("economy.leaderboard", true);

        // Leveling & Rank
        DEFAULT_PERMISSIONS.put("levels.*", false);
        DEFAULT_PERMISSIONS.put("levels.config", false);
        DEFAULT_PERMISSIONS.put("levels.edit", false);
        DEFAULT_PERMISSIONS.put("levels.use", true);
        DEFAULT_PERMISSIONS.put("rank.use", true);

        // Logging Commands
        DEFAULT_PERMISSIONS.put("log.*", false);
        DEFAULT_PERMISSIONS.put("log.config", false);
        DEFAULT_PERMISSIONS.put("log.setchannel", false);
        DEFAULT_PERMISSIONS.put("log.manual", false);

        // Pride / Fun Commands
        DEFAULT_PERMISSIONS.put("pride.use", true);
        DEFAULT_PERMISSIONS.put("pride.pronouns.self", true);
        DEFAULT_PERMISSIONS.put("pride.pronouns.others", false);
        DEFAULT_PERMISSIONS.put("points.use", true);

        // Utility / Games
        DEFAULT_PERMISSIONS.put("echo.use", false);
        DEFAULT_PERMISSIONS.put("chess.use", true);
        DEFAULT_PERMISSIONS.put("poker.use", true);
        DEFAULT_PERMISSIONS.put("embed.simple", false);
        DEFAULT_PERMISSIONS.put("embed.advanced", false);
        DEFAULT_PERMISSIONS.put("serverstats.use", false);

        // Rules Commands
        DEFAULT_PERMISSIONS.put("rules.use", true);
        DEFAULT_PERMISSIONS.put("rules.edit", false);
        DEFAULT_PERMISSIONS.put("settings.rules", false);

        // Proxy System Commands (PluralKit-style)
        // Note: Only proxy.use is permission-gated. All other proxy operations are
        // available to everyone.
        DEFAULT_PERMISSIONS.put("proxy.*", false);
        DEFAULT_PERMISSIONS.put("proxy.use", true); // Permission to use proxies in a server (messages get proxied)

        // Ticket System Commands
        DEFAULT_PERMISSIONS.put("tickets.*", false);
        DEFAULT_PERMISSIONS.put("tickets.use", true);
        DEFAULT_PERMISSIONS.put("tickets.create", true);
        DEFAULT_PERMISSIONS.put("tickets.close", true);
        DEFAULT_PERMISSIONS.put("tickets.view", true);
        DEFAULT_PERMISSIONS.put("tickets.manage", false); // Add/remove users from tickets
        DEFAULT_PERMISSIONS.put("tickets.admin", false); // Full ticket system management
        DEFAULT_PERMISSIONS.put("tickets.category.*", false);
        DEFAULT_PERMISSIONS.put("tickets.category.create", false);
        DEFAULT_PERMISSIONS.put("tickets.category.edit", false);
        DEFAULT_PERMISSIONS.put("tickets.category.delete", false);
        DEFAULT_PERMISSIONS.put("tickets.settings", false);

        // Global Chat Commands
        DEFAULT_PERMISSIONS.put("globalchat.*", false);
        DEFAULT_PERMISSIONS.put("globalchat.use", true); // Allow messages to be relayed
        DEFAULT_PERMISSIONS.put("globalchat.link", false); // Link channels — guild owner only by default
        DEFAULT_PERMISSIONS.put("globalchat.unlink", false); // Unlink channels — guild owner only by default
    }

    /**
     * Initialize mapping from Discord role permissions to bot permission nodes.
     * When a member's Discord roles grant a listed Discord permission, the member
     * automatically receives the corresponding bot permission nodes — no manual
     * /permissions set required. Explicit user/role deny entries still override.
     */
    private static void initializeDiscordPermissionMapping() {
        // BAN_MEMBERS → moderation ban-related nodes + warn/unwarn/warns
        DISCORD_PERMISSION_MAPPING.put(Permission.BAN_MEMBERS, Set.of(
                "mod.ban", "mod.softban", "mod.warn", "mod.unwarn", "mod.warns", "mod.hist",
                "permissions.view", "logging.view", "log.manual"));

        // KICK_MEMBERS → moderation kick-related nodes
        DISCORD_PERMISSION_MAPPING.put(Permission.KICK_MEMBERS, Set.of(
                "mod.kick", "mod.warn", "mod.hist",
                "permissions.view", "logging.view", "log.manual"));

        // MODERATE_MEMBERS (timeout) → timeout / mute nodes
        DISCORD_PERMISSION_MAPPING.put(Permission.MODERATE_MEMBERS, Set.of(
                "mod.timeout", "mod.mute", "mod.warn", "mod.hist",
                "permissions.view", "logging.view", "log.manual"));

        // MANAGE_MESSAGES → purge & lockdown
        DISCORD_PERMISSION_MAPPING.put(Permission.MESSAGE_MANAGE, Set.of(
                "mod.purge", "mod.purge.messages", "mod.purge.members", "mod.lockdown"));

        // MANAGE_ROLES → reaction-role admin & role-persistence
        DISCORD_PERMISSION_MAPPING.put(Permission.MANAGE_ROLES, Set.of(
                "admin.reactionroles", "admin.rolepersistence"));

        // MANAGE_SERVER → server-wide admin nodes
        DISCORD_PERMISSION_MAPPING.put(Permission.MANAGE_SERVER, Set.of(
                "admin.settings", "admin.config", "admin.welcome", "admin.prefix",
                "admin.logging", "admin.autolog", "admin.antispam", "admin.punishment_dm",
                "log.config", "log.setchannel", "log.manual",
                "automod.viewsettings", "automod.edit",
                "globalchat.link", "globalchat.unlink",
                "tickets.admin", "tickets.settings", "tickets.manage",
                "tickets.category.create", "tickets.category.edit", "tickets.category.delete",
                "rules.edit", "settings.rules",
                "serverstats.use"));

        // MANAGE_CHANNEL → lockdown & ticket management
        DISCORD_PERMISSION_MAPPING.put(Permission.MANAGE_CHANNEL, Set.of(
                "mod.lockdown", "tickets.manage"));
    }

    /**
     * Check if a member has a specific permission node
     */
    public static boolean hasPermission(Member member, String permissionNode) {
        if (member == null || permissionNode == null) {
            return false;
        }

        // Guild owner has all permissions
        if (member.isOwner()) {
            return true;
        }

        // Check if it's a bot owner command (these cannot be assigned via permissions)
        if (isBotOwnerOnlyCommand(permissionNode)) {
            return PermissionUtils.isBotOwner(member.getUser(), getBotOwnerId());
        }

        // Discord Administrators should have all permissions by default (unless the
        // command
        // is restricted to the bot owner above). This ensures roles that grant the
        // built-in Administrator permission behave as expected.
        if (PermissionUtils.hasAdminPermissions(member)) {
            return true;
        }

        try {
            String guildId = member.getGuild().getId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check user-specific permissions first
            Boolean userPerm = getUserPermission(guildSettings, member.getId(), permissionNode);
            if (userPerm != null) {
                return userPerm;
            }

            // Check role permissions
            List<Role> memberRoles = member.getRoles();
            Boolean rolePerm = getRolePermission(guildSettings, memberRoles, permissionNode);
            if (rolePerm != null) {
                return rolePerm;
            }

            // Check @everyone permissions
            Boolean everyonePerm = getEveryonePermission(guildSettings, permissionNode);
            if (everyonePerm != null) {
                return everyonePerm;
            }

            // Check if the member's Discord role permissions implicitly grant this node.
            // This runs after explicit user/role/everyone checks so that manual overrides
            // (especially denies) always take precedence.
            if (hasDiscordPermissionFor(member, permissionNode)) {
                return true;
            }

            // Fall back to default permission
            if (getDefaultPermission(permissionNode)) {
                return true;
            }

            // Last resort: check if the user is the bot owner. This ensures bot owners
            // can use any command in any server they have access to without needing
            // explicit permission assignments.
            return PermissionUtils.isBotOwner(member.getUser(), getBotOwnerId());

        } catch (Exception e) {
            logger.warn("Error checking permission {} for user {}: {}", permissionNode, member.getId(), e.getMessage());
            // If an error occurs during guild permission check, allow bot owners as fallback
            if (PermissionUtils.isBotOwner(member.getUser(), getBotOwnerId())) {
                return true;
            }
            return getDefaultPermission(permissionNode);
        }
    }

    /**
     * Set permission for a user
     */
    public static void setUserPermission(String guildId, String userId, String permissionNode, boolean allowed) {
        try {
            String key = "userPermissions_" + userId + "_" + permissionNode;
            ServerBot.getStorageManager().updateGuildSettings(guildId, key, allowed);
        } catch (Exception e) {
            logger.error("Error setting user permission: {}", e.getMessage());
        }
    }

    /**
     * Set permission for a role
     */
    public static void setRolePermission(String guildId, String roleId, String permissionNode, boolean allowed) {
        try {
            String key = "rolePermissions_" + roleId + "_" + permissionNode;
            ServerBot.getStorageManager().updateGuildSettings(guildId, key, allowed);
        } catch (Exception e) {
            logger.error("Error setting role permission: {}", e.getMessage());
        }
    }

    /**
     * Set permission for @everyone
     */
    public static void setEveryonePermission(String guildId, String permissionNode, boolean allowed) {
        try {
            String key = "everyonePermissions_" + permissionNode;
            ServerBot.getStorageManager().updateGuildSettings(guildId, key, allowed);
        } catch (Exception e) {
            logger.error("Error setting everyone permission: {}", e.getMessage());
        }
    }

    /**
     * Remove permission for a user
     */
    public static void removeUserPermission(String guildId, String userId, String permissionNode) {
        try {
            String key = "userPermissions_" + userId + "_" + permissionNode;
            ServerBot.getStorageManager().removeGuildSetting(guildId, key);
        } catch (Exception e) {
            logger.error("Error removing user permission: {}", e.getMessage());
        }
    }

    /**
     * Remove permission for a role
     */
    public static void removeRolePermission(String guildId, String roleId, String permissionNode) {
        try {
            String key = "rolePermissions_" + roleId + "_" + permissionNode;
            ServerBot.getStorageManager().removeGuildSetting(guildId, key);
        } catch (Exception e) {
            logger.error("Error removing role permission: {}", e.getMessage());
        }
    }

    /**
     * Remove permission for @everyone
     */
    public static void removeEveryonePermission(String guildId, String permissionNode) {
        try {
            String key = "everyonePermissions_" + permissionNode;
            ServerBot.getStorageManager().removeGuildSetting(guildId, key);
        } catch (Exception e) {
            logger.error("Error removing everyone permission: {}", e.getMessage());
        }
    }

    /**
     * Get all permissions for a user
     */
    public static Map<String, Boolean> getUserPermissions(String guildId, String userId) {
        Map<String, Boolean> permissions = new HashMap<>();
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String prefix = "userPermissions_" + userId + "_";

            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    String permissionNode = entry.getKey().substring(prefix.length());
                    permissions.put(permissionNode, (Boolean) entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.error("Error getting user permissions: {}", e.getMessage());
        }
        return permissions;
    }

    /**
     * Get all permissions for a role
     */
    public static Map<String, Boolean> getRolePermissions(String guildId, String roleId) {
        Map<String, Boolean> permissions = new HashMap<>();
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String prefix = "rolePermissions_" + roleId + "_";

            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    String permissionNode = entry.getKey().substring(prefix.length());
                    permissions.put(permissionNode, (Boolean) entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.error("Error getting role permissions: {}", e.getMessage());
        }
        return permissions;
    }

    /**
     * Get all permissions for @everyone
     */
    public static Map<String, Boolean> getEveryonePermissions(String guildId) {
        Map<String, Boolean> permissions = new HashMap<>();
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String prefix = "everyonePermissions_";

            for (Map.Entry<String, Object> entry : settings.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    String permissionNode = entry.getKey().substring(prefix.length());
                    permissions.put(permissionNode, (Boolean) entry.getValue());
                }
            }
        } catch (Exception e) {
            logger.error("Error getting everyone permissions: {}", e.getMessage());
        }
        return permissions;
    }

    // Private helper methods

    /**
     * Check whether any of the member's Discord role permissions implicitly grant
     * the requested bot permission node via the DISCORD_PERMISSION_MAPPING table.
     * This also supports wildcard node queries (e.g. "mod.*") by checking whether
     * any mapped node starts with the wildcard prefix.
     */
    private static boolean hasDiscordPermissionFor(Member member, String permissionNode) {
        for (Map.Entry<Permission, Set<String>> entry : DISCORD_PERMISSION_MAPPING.entrySet()) {
            Permission discordPerm = entry.getKey();
            Set<String> grantedNodes = entry.getValue();

            if (!member.hasPermission(discordPerm)) {
                continue;
            }

            // Direct match
            if (grantedNodes.contains(permissionNode)) {
                return true;
            }

            // Wildcard query: "mod.*" should match if any granted node starts with "mod."
            if (permissionNode.endsWith(".*")) {
                String prefix = permissionNode.substring(0, permissionNode.length() - 1); // "mod."
                for (String node : grantedNodes) {
                    if (node.startsWith(prefix)) {
                        return true;
                    }
                }
            }

            // Granted wildcard: if a granted node is "mod.*" and the query is "mod.ban"
            for (String node : grantedNodes) {
                if (node.endsWith(".*")) {
                    String prefix = node.substring(0, node.length() - 1);
                    if (permissionNode.startsWith(prefix)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Boolean getUserPermission(Map<String, Object> settings, String userId, String permissionNode) {
        // Check exact permission
        String key = "userPermissions_" + userId + "_" + permissionNode;
        Boolean result = (Boolean) settings.get(key);
        if (result != null)
            return result;

        // Check wildcard permissions
        return checkWildcardPermissions(settings, "userPermissions_" + userId + "_", permissionNode);
    }

    private static Boolean getRolePermission(Map<String, Object> settings, List<Role> roles, String permissionNode) {
        boolean hasDeny = false;
        boolean hasAllow = false;

        for (Role role : roles) {
            // Check exact permission
            String key = "rolePermissions_" + role.getId() + "_" + permissionNode;
            Boolean result = (Boolean) settings.get(key);
            if (result != null) {
                if (!result)
                    hasDeny = true; // Explicit deny found
                else
                    hasAllow = true; // Explicit allow found
            }

            // Check wildcard permissions
            Boolean wildcardResult = checkWildcardPermissions(settings, "rolePermissions_" + role.getId() + "_",
                    permissionNode);
            if (wildcardResult != null) {
                if (!wildcardResult)
                    hasDeny = true; // Wildcard deny found
                else
                    hasAllow = true; // Wildcard allow found
            }
        }

        // Deny takes precedence over allow
        if (hasDeny)
            return false;
        if (hasAllow)
            return true;
        return null; // No role has an opinion on this permission
    }

    private static Boolean getEveryonePermission(Map<String, Object> settings, String permissionNode) {
        // Check exact permission
        String key = "everyonePermissions_" + permissionNode;
        Boolean result = (Boolean) settings.get(key);
        if (result != null)
            return result;

        // Check wildcard permissions
        return checkWildcardPermissions(settings, "everyonePermissions_", permissionNode);
    }

    private static Boolean checkWildcardPermissions(Map<String, Object> settings, String prefix,
            String permissionNode) {
        String[] parts = permissionNode.split("\\.");

        // Check wildcards from most specific to least specific.
        // For "mod.ban", this checks: "mod.*" first, then "*"
        // More specific wildcards take precedence.
        Boolean result = null;

        StringBuilder wildcardBuilder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                wildcardBuilder.append(".");
            wildcardBuilder.append(parts[i]);

            String wildcardKey = prefix + wildcardBuilder.toString() + ".*";
            Boolean wildcardResult = (Boolean) settings.get(wildcardKey);
            if (wildcardResult != null) {
                result = wildcardResult; // Keep the most specific match
            }
        }

        // If we found a specific wildcard match, use it
        if (result != null) {
            return result;
        }

        // Fall back to global wildcard "*" only if no specific wildcard matched
        String globalWildcardKey = prefix + "*";
        Boolean globalWildcard = (Boolean) settings.get(globalWildcardKey);
        if (globalWildcard != null) {
            return globalWildcard;
        }

        return null;
    }

    private static boolean getDefaultPermission(String permissionNode) {
        // Check exact match first
        Boolean defaultPerm = DEFAULT_PERMISSIONS.get(permissionNode);
        if (defaultPerm != null)
            return defaultPerm;

        // Check wildcard matches from most specific to least specific
        // The most specific wildcard that matches takes precedence
        String[] parts = permissionNode.split("\\.");
        Boolean result = null;
        StringBuilder wildcardBuilder = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                wildcardBuilder.append(".");
            wildcardBuilder.append(parts[i]);

            String wildcardNode = wildcardBuilder.toString() + ".*";
            Boolean wildcardPerm = DEFAULT_PERMISSIONS.get(wildcardNode);
            if (wildcardPerm != null) {
                result = wildcardPerm; // Keep the most specific match
            }
        }

        if (result != null)
            return result;

        // Check global wildcard as last resort
        Boolean globalWildcard = DEFAULT_PERMISSIONS.get("*");
        if (globalWildcard != null) {
            return globalWildcard;
        }

        // Default to false if no matching permission found
        return false;
    }

    private static boolean isBotOwnerOnlyCommand(String permissionNode) {
        // Commands that cannot be assigned via permissions
        return permissionNode.equals("presence.use") ||
                permissionNode.equals("appearance.use") ||
                permissionNode.equals("config.use") ||
                permissionNode.equals("restart.use") ||
                permissionNode.equals("backup.use");
    }

    private static String getBotOwnerId() {
        try {
            return ServerBot.getStorageManager().getGuildSettings("global").get("botOwnerId").toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get all available permission nodes
     */
    public static Set<String> getAllPermissionNodes() {
        return new HashSet<>(DEFAULT_PERMISSIONS.keySet());
    }

    /**
     * Check if a permission node exists
     */
    public static boolean isValidPermissionNode(String permissionNode) {
        return DEFAULT_PERMISSIONS.containsKey(permissionNode) ||
                permissionNode.endsWith(".*");
    }
}
