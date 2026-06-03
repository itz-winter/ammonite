package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
// import net.dv8tion.jda.api.interactions.commands.build.OptionData; // unused
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Command for managing role persistence settings
 */
public class RolePersistenceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.rolepersistence")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions [200]",
                    "You need the `admin.rolepersistence` permission to configure role persistence.\n" +
                            "Error Code: **200** - Permission Denied\n" +
                            "Use `/error category:2` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "enable" -> handleEnable(event);
            case "disable" -> handleDisable(event);
            case "status" -> handleStatus(event);
            case "clear" -> handleClear(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Subcommand [101]",
                    "Unknown subcommand: " + subcommand + "\n" +
                            "Error Code: **101** - Invalid Value\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleEnable(SlashCommandInteractionEvent event) {
        try {
            ServerBot.getStorageManager().updateGuildSettings(
                    event.getGuild().getId(), "rolePersistenceEnabled", true);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Role Persistence Enabled",
                    "Role persistence has been **enabled** for this server.\n\n" +
                            "**Features:**\n" +
                            "• User roles are automatically saved when they leave\n" +
                            "• Roles are restored when users rejoin\n" +
                            "• Excludes @everyone and bot roles\n" +
                            "• Maintains role hierarchy and permissions"))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed [400]",
                    "Failed to enable role persistence: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleDisable(SlashCommandInteractionEvent event) {
        try {
            ServerBot.getStorageManager().updateGuildSettings(
                    event.getGuild().getId(), "rolePersistenceEnabled", false);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Role Persistence Disabled",
                    "Role persistence has been **disabled** for this server.\n\n" +
                            "**Note:** Existing stored role data is preserved and can be re-enabled at any time."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed [400]",
                    "Failed to disable role persistence: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        try {
            // Get current role persistence status - you'll need to implement this in
            // storage manager
            boolean isEnabled = getRolePersistenceStatus(event.getGuild().getId());
            int storedUsersCount = getStoredUsersCount(event.getGuild().getId());

            String status = isEnabled ? "**🟢 Enabled**" : "**🔴 Disabled**";
            String description = String.format(
                    "**Status:** %s\n" +
                            "**Stored Users:** %d users with saved roles\n\n" +
                            "**How it works:**\n" +
                            "• When enabled, user roles are automatically saved when they leave\n" +
                            "• Roles are restored when users rejoin the server\n" +
                            "• Only restores roles the bot can assign (respects hierarchy)\n" +
                            "• Excludes @everyone, bot roles, and dangerous permissions",
                    status, storedUsersCount);

            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "🔄 Role Persistence Status", description)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Status Check Failed [400]",
                    "Failed to check role persistence status: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        OptionMapping confirmOption = event.getOption("confirm");
        if (confirmOption == null || !confirmOption.getAsBoolean()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Confirmation Required [103]",
                    "This will permanently delete **all** stored role data for this server.\n\n" +
                            "To confirm, use: `/rolepersistence clear confirm:true`\n\n" +
                            "Error Code: **103** - Confirmation Required\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        try {
            clearAllStoredRoles(event.getGuild().getId());

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "🗑️ Role Data Cleared",
                    "All stored role persistence data has been **permanently deleted**.\n\n" +
                            "Role persistence remains "
                            + (getRolePersistenceStatus(event.getGuild().getId()) ? "enabled" : "disabled") +
                            " and will start tracking new role data as users leave/join."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Clear Failed [400]",
                    "Failed to clear role persistence data: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private boolean getRolePersistenceStatus(String guildId) {
        try {
            // Check guild settings for role persistence enabled status
            // This would integrate with your storage system
            return true; // Placeholder - implement based on your storage system
        } catch (Exception e) {
            return false;
        }
    }

    private int getStoredUsersCount(String guildId) {
        try {
            return com.serverbot.services.RolePersistenceService.getInstance().getStoredUsersCount(guildId);
        } catch (Exception e) {
            return 0;
        }
    }

    private void clearAllStoredRoles(String guildId) {
        try {
            com.serverbot.services.RolePersistenceService.getInstance().clearGuildRoleData(guildId);
        } catch (Exception e) {
            throw new RuntimeException("Failed to clear stored roles", e);
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("rolepersistence", "Configure role persistence for users who rejoin")
                .addSubcommands(
                        new SubcommandData("enable", "Enable role persistence for this server"),
                        new SubcommandData("disable", "Disable role persistence for this server"),
                        new SubcommandData("status", "Check role persistence status and statistics"),
                        new SubcommandData("clear", "Clear all stored role persistence data")
                                .addOption(OptionType.BOOLEAN, "confirm", "Confirm deletion of all role data", true));
    }

    @Override
    public String getName() {
        return "rolepersistence";
    }

    @Override
    public String getDescription() {
        return "Configure role persistence for users who rejoin";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}