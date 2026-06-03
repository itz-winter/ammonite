package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * Logging configuration command for setting up server logging
 */
public class LoggingCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member executor = event.getMember();
        if (!PermissionManager.hasPermission(executor, "admin.logging")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need administrator permissions to configure logging."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        String logType = event.getOption("type") != null ? event.getOption("type").getAsString() : null;

        try {
            String guildId = event.getGuild().getId();

            // Validate type is provided for actions that need it
            if (!action.equalsIgnoreCase("reset") && logType == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Missing Type", "You must specify a logging type for this action.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            switch (action.toLowerCase()) {
                case "set" -> {
                    TextChannel channel = event.getOption("channel").getAsChannel().asTextChannel();

                    // Store the logging channel configuration
                    ServerBot.getStorageManager().updateGuildSettings(guildId, logType + "_log_channel",
                            channel.getId());

                    // Also enable autolog for this type automatically
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_" + logType, true);

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "Logchannel set",
                            "**Log Type:** " + logType.toUpperCase() + "\n" +
                                    "**Channel:** " + channel.getAsMention() + "\n" +
                                    "**Auto-logging:** Enabled\n" +
                                    "**Set by:** " + executor.getAsMention()))
                            .queue();
                }
                case "disable" -> {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, logType + "_log_channel", null);

                    // Also disable autolog for this type
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_" + logType, false);

                    // If this is the last configured log type, also clear legacy logChannelId
                    Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
                    boolean anyOtherTypeEnabled = false;
                    for (String type : new String[] { "moderation", "message", "member", "voice", "server" }) {
                        if (!type.equals(logType)) {
                            Boolean enabled = (Boolean) settings.get("autoLog_" + type);
                            if (Boolean.TRUE.equals(enabled)) {
                                anyOtherTypeEnabled = true;
                                break;
                            }
                        }
                    }
                    if (!anyOtherTypeEnabled) {
                        // Clear legacy logChannelId when all logging is disabled
                        ServerBot.getStorageManager().updateGuildSettings(guildId, "logChannelId", null);
                    }

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "Logging Disabled",
                            "**Log Type:** " + logType.toUpperCase() + " logging has been disabled.\n" +
                                    "**Disabled by:** " + executor.getAsMention()))
                            .queue();
                }
                case "view" -> {
                    Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
                    String channelId = (String) guildSettings.get(logType + "_log_channel");
                    Boolean autoLogEnabled = (Boolean) guildSettings.get("autoLog_" + logType);

                    if (channelId == null) {
                        event.replyEmbeds(EmbedUtils.createInfoEmbed(
                                "Logging Status",
                                "**Log Type:** " + logType.toUpperCase() + "\n" +
                                        "**Status:** Disabled"))
                                .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    } else {
                        TextChannel logChannel = event.getGuild().getTextChannelById(channelId);
                        String channelMention = logChannel != null ? logChannel.getAsMention()
                                : "Channel not found (ID: " + channelId + ")";
                        String autoLogStatus = Boolean.TRUE.equals(autoLogEnabled) ? "Enabled" : "Disabled";

                        event.replyEmbeds(EmbedUtils.createInfoEmbed(
                                "Logging Status",
                                "**Log Type:** " + logType.toUpperCase() + "\n" +
                                        "**Channel:** " + channelMention + "\n" +
                                        "**Auto-logging:** " + autoLogStatus))
                                .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    }
                }
                case "reset" -> {
                    // Clear all logging settings
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "logChannelId", null);
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_all", null);

                    for (String type : new String[] { "moderation", "message", "member", "voice", "server" }) {
                        ServerBot.getStorageManager().updateGuildSettings(guildId, type + "_log_channel", null);
                        ServerBot.getStorageManager().updateGuildSettings(guildId, "autoLog_" + type, null);
                    }

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "Logging Reset",
                            "All logging settings have been cleared.\n" +
                                    "**Reset by:** " + executor.getAsMention()))
                            .queue();
                }
                default -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Invalid Value!", "Valid actions are: set, disable, view, reset")).setEphemeral(true)
                            .queue();
                }
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Log Config Failed",
                    "Failed to configure logging: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("logging", "Configure server logging channels")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Action to perform", true)
                                .addChoice("Set Channel", "set")
                                .addChoice("Disable", "disable")
                                .addChoice("View Status", "view")
                                .addChoice("Reset All", "reset"),
                        new OptionData(OptionType.STRING, "type", "Type of logging", false)
                                .addChoice("Moderation", "moderation")
                                .addChoice("Message", "message")
                                .addChoice("Member", "member")
                                .addChoice("Voice", "voice")
                                .addChoice("Server", "server"),
                        new OptionData(OptionType.CHANNEL, "channel", "Channel for logging (required for set)", false));
    }

    @Override
    public String getName() {
        return "logging";
    }

    @Override
    public String getDescription() {
        return "Configure server logging channels and settings";
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
