package com.serverbot.utils;

import com.serverbot.ServerBot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for permission checks
 */
public class PermissionUtils {

    /**
     * Check if a member has administrator permissions
     */
    public static boolean hasAdminPermissions(Member member) {
        return member.hasPermission(Permission.ADMINISTRATOR);
    }

    /**
     * Check if a member has moderation permissions
     */
    public static boolean hasModerationPermissions(Member member) {
        return member.hasPermission(Permission.KICK_MEMBERS) ||
                member.hasPermission(Permission.BAN_MEMBERS) ||
                member.hasPermission(Permission.MANAGE_ROLES) ||
                member.hasPermission(Permission.ADMINISTRATOR);
    }

    /**
     * Check if a member has moderator permissions (alias for compatibility)
     */
    public static boolean hasModeratorPermissions(Member member) {
        return hasModerationPermissions(member);
    }

    /**
     * Check if a member has manage server permissions
     */
    public static boolean hasManageServerPermissions(Member member) {
        return member.hasPermission(Permission.MANAGE_SERVER) ||
                member.hasPermission(Permission.ADMINISTRATOR);
    }

    /**
     * Check if a member can interact with another member (hierarchy check)
     */
    public static boolean canInteractWith(Member moderator, Member target) {
        if (moderator.equals(target)) {
            return false; // Can't target yourself
        }

        if (target.isOwner()) {
            return false; // Can't target server owner
        }

        if (moderator.isOwner()) {
            return true; // Server owner can target anyone
        }

        // Check role hierarchy
        return moderator.canInteract(target);
    }

    /**
     * Check if the bot can interact with a member
     */
    public static boolean botCanInteractWith(Guild guild, Member target) {
        Member botMember = guild.getSelfMember();

        if (target.isOwner()) {
            return false; // Bot can't target server owner
        }

        // Check if bot has higher role hierarchy
        return botMember.canInteract(target);
    }

    /**
     * Check if a user is the bot owner
     */
    public static boolean isBotOwner(User user, String ownerId) {
        // Legacy support for single owner ID parameter
        if (ownerId != null && user.getId().equals(ownerId)) {
            return true;
        }

        // Check against all configured owners
        BotConfig config = ServerBot.getConfigManager().getConfig();
        return config != null && config.isOwner(user.getId());
    }

    /**
     * Check if a user is the bot owner (overload without ownerId parameter)
     */
    public static boolean isBotOwner(User user) {
        BotConfig config = ServerBot.getConfigManager().getConfig();
        return config != null && config.isOwner(user.getId());
    }

    /**
     * Get permission level of a member
     * 0 = Normal user
     * 1 = Moderator
     * 2 = Administrator
     * 3 = Server owner
     * 4 = Bot owner
     */
    public static int getPermissionLevel(Member member, String botOwnerId) {
        if (isBotOwner(member.getUser(), botOwnerId)) {
            return 4;
        }

        if (member.isOwner()) {
            return 3;
        }

        if (hasAdminPermissions(member)) {
            return 2;
        }

        if (hasModerationPermissions(member)) {
            return 1;
        }

        return 0;
    }

    /**
     * Check if a member has permission to use a specific command based on custom
     * role permissions
     */
    public static boolean hasCommandPermission(Member member, String commandName) {
        try {
            String guildId = member.getGuild().getId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            List<Role> memberRoles = member.getRoles();

            // Check if any role explicitly denies this command
            for (Role role : memberRoles) {
                @SuppressWarnings("unchecked")
                Set<String> deniedCommands = (Set<String>) guildSettings
                        .getOrDefault("rolePermissions_" + role.getId() + "_denied", new HashSet<>());
                if (deniedCommands.contains(commandName.toLowerCase())) {
                    return false; // Explicit deny takes precedence
                }
            }

            // Check if any role explicitly allows this command
            for (Role role : memberRoles) {
                @SuppressWarnings("unchecked")
                Set<String> allowedCommands = (Set<String>) guildSettings
                        .getOrDefault("rolePermissions_" + role.getId() + "_allowed", new HashSet<>());
                if (allowedCommands.contains(commandName.toLowerCase())) {
                    return true; // Explicit allow overrides default permissions
                }
            }

            // If no custom permissions are set, fall back to default Discord permissions
            return true; // Default: allow (individual commands can still check their own permission
                         // requirements)

        } catch (Exception e) {
            // If there's an error, fall back to allowing the command (safer default)
            return true;
        }
    }

    /**
     * Check if a member has permission to use a command requiring admin permissions
     */
    public static boolean hasCommandPermissionWithAdmin(Member member, String commandName) {
        // First check custom role permissions
        if (!hasCommandPermission(member, commandName)) {
            return false;
        }

        // Then check if they have admin permissions for admin-required commands
        return hasAdminPermissions(member);
    }

    /**
     * Check if a member has permission to use a command requiring moderation
     * permissions
     */
    public static boolean hasCommandPermissionWithModeration(Member member, String commandName) {
        // First check custom role permissions
        if (!hasCommandPermission(member, commandName)) {
            return false;
        }

        // Then check if they have moderation permissions
        return hasModerationPermissions(member);
    }
}
