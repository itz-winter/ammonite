package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * Log command for testing and manual logging
 */
public class LogCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "logging.view")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `logging.view` permission to view logs."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        OptionMapping actionTypeOption = event.getOption("actiontype");
        String actionType = actionTypeOption != null ? actionTypeOption.getAsString() : null;

        try {
            // Get the log channel — check multiple keys for compatibility
            Map<String, Object> guildSettings = ServerBot.getStorageManager()
                    .getGuildSettings(event.getGuild().getId());
            String logChannelId = null;

            // 1. Check per-type key set by /logging command (e.g., moderation_log_channel)
            if (logChannelId == null) {
                Object v = guildSettings.get("moderation_log_channel");
                if (v instanceof String)
                    logChannelId = (String) v;
            }

            // 2. Check legacy key (logChannelId)
            if (logChannelId == null) {
                Object v = guildSettings.get("logChannelId");
                if (v instanceof String)
                    logChannelId = (String) v;
            }

            if (logChannelId == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "No Log Channel",
                        "No log channel is configured. Use `/logging set` to set one.")).setEphemeral(true).queue();
                return;
            }

            TextChannel logChannel = event.getGuild().getTextChannelById(logChannelId);
            if (logChannel == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Log Channel Not Found",
                        "The configured log channel no longer exists.")).setEphemeral(true).queue();
                return;
            }

            // Create log entry based on action
            String logTitle = getActionTitle(action);
            String logDescription = getActionDescription(action, actionType, event.getUser());

            // Send log to channel
            logChannel.sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    logTitle, logDescription)).queue(
                            success -> {
                                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                        "Log Entry Created",
                                        "Manual log entry created in " + logChannel.getAsMention())).setEphemeral(true)
                                        .queue();
                            },
                            error -> {
                                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                        "Log Failed",
                                        "Failed to send log message: " + error.getMessage())).setEphemeral(true)
                                        .queue();
                            });

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Log Error",
                    "Failed to create log entry: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private String getActionTitle(String action) {
        return switch (action.toLowerCase()) {
            case "test" -> "Test Log Entry";
            case "moderation" -> "Moderation Action";
            case "member" -> "Member Event";
            case "message" -> "Message Event";
            case "voice" -> "Voice Event";
            case "server" -> "Server Event";
            default -> "Custom Log Entry";
        };
    }

    private String getActionDescription(String action, String actionType, net.dv8tion.jda.api.entities.User user) {
        String typeText = actionType != null ? " (" + actionType + ")" : "";
        return "**Action:** " + action.substring(0, 1).toUpperCase() + action.substring(1) + typeText + "\n" +
                "**Triggered by:** " + user.getAsMention() + "\n" +
                "**Time:** <t:" + (System.currentTimeMillis() / 1000) + ":F>";
    }

    public static CommandData getCommandData() {
        return Commands.slash("log", "Create a manual log entry")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "The action to log", true)
                                .addChoice("Test", "test")
                                .addChoice("Moderation", "moderation")
                                .addChoice("Member", "member")
                                .addChoice("Message", "message")
                                .addChoice("Voice", "voice")
                                .addChoice("Server", "server"),
                        new OptionData(OptionType.STRING, "actiontype", "Additional action type details", false));
    }

    @Override
    public String getName() {
        return "log";
    }

    @Override
    public String getDescription() {
        return "Create a manual log entry";
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
