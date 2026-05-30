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
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * AutoLog command for configuring automatic logging
 */
public class AutoLogCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.autolog")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `admin.autolog` permission to configure auto-logging."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        OptionMapping actionTypeOption = event.getOption("actiontype");
        String actionType = actionTypeOption != null ? actionTypeOption.getAsString() : null;

        try {
            String settingKey = getAutoLogSettingKey(action, actionType);
            Map<String, Object> guildSettings = ServerBot.getStorageManager()
                    .getGuildSettings(event.getGuild().getId());
            boolean currentValue = Boolean.TRUE.equals(guildSettings.get(settingKey));
            boolean newValue = !currentValue; // Toggle the setting

            // Update the setting
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), settingKey, newValue);

            String actionDescription = getActionDescription(action, actionType);
            String statusText = newValue ? "**Enabled**" : "Disabled";

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "AutoLog Updated",
                    "**" + actionDescription + "** auto-logging has been " + statusText.toLowerCase() + ".\n\n" +
                            "**Status:** " + statusText))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "AutoLog Error",
                    "Failed to update auto-logging setting: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private String getAutoLogSettingKey(String action, String actionType) {
        String base = "autoLog_" + action.toLowerCase();
        if (actionType != null) {
            base += "_" + actionType.toLowerCase();
        }
        return base;
    }

    private String getActionDescription(String action, String actionType) {
        String base = switch (action.toLowerCase()) {
            case "moderation" -> "Moderation Actions";
            case "member" -> "Member Events";
            case "message" -> "Message Events";
            case "voice" -> "Voice Events";
            case "server" -> "Server Events";
            case "all" -> "All Events";
            default -> action.substring(0, 1).toUpperCase() + action.substring(1);
        };

        if (actionType != null) {
            base += " (" + actionType + ")";
        }

        return base;
    }

    public static CommandData getCommandData() {
        return Commands.slash("autolog", "Configure automatic logging for different events")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "The type of events to auto-log", true)
                                .addChoice("All Events", "all")
                                .addChoice("Moderation", "moderation")
                                .addChoice("Member Events", "member")
                                .addChoice("Message Events", "message")
                                .addChoice("Voice Events", "voice")
                                .addChoice("Server Events", "server"),
                        new OptionData(OptionType.STRING, "actiontype", "Specific event type (optional)", false)
                                .addChoice("Joins", "joins")
                                .addChoice("Leaves", "leaves")
                                .addChoice("Bans", "bans")
                                .addChoice("Kicks", "kicks")
                                .addChoice("Mutes", "mutes")
                                .addChoice("Warns", "warns")
                                .addChoice("Deletes", "deletes")
                                .addChoice("Edits", "edits"));
    }

    @Override
    public String getName() {
        return "autolog";
    }

    @Override
    public String getDescription() {
        return "Configure automatic logging for different events";
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
