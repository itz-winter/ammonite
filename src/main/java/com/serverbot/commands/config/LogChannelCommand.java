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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.*;

/**
 * Enhanced LogChannel command for managing selective logging channels
 */
public class LogChannelCommand implements SlashCommand {

    private static final Set<String> VALID_LOG_TYPES = Set.of(
            "bans", "kicks", "warnings", "mutes", "joins", "leaves",
            "messages", "voice", "roles", "nicknames", "all");

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.logging")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `admin.logging` permission to configure log channels."))
                    .setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();

        switch (subcommand) {
            case "set" -> handleSet(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            case "clear" -> handleClear(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Subcommand", "Unknown subcommand: " + subcommand)).setEphemeral(true).queue();
        }
    }

    private void handleSet(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getOption("channel") != null
                ? event.getOption("channel").getAsChannel().asTextChannel()
                : null;

        if (channel == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Channel [100]",
                    "Please specify a channel for logging.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping typeOption = event.getOption("type");
        String logType = typeOption != null ? typeOption.getAsString().toLowerCase() : "all";

        if (!VALID_LOG_TYPES.contains(logType)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Log Type [101]",
                    "Valid log types: " + String.join(", ", VALID_LOG_TYPES) + "\n" +
                            "Error Code: **101** - Invalid Value\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        try {
            if ("all".equals(logType)) {
                // Set the channel for all log types
                for (String type : VALID_LOG_TYPES) {
                    if (!"all".equals(type)) {
                        ServerBot.getStorageManager().updateGuildSettings(
                                event.getGuild().getId(), "logChannel_" + type, channel.getId());
                    }
                }
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "Log Channel Set",
                        String.format("Set %s as the log channel for **all** event types.", channel.getAsMention())))
                        .queue();
            } else {
                ServerBot.getStorageManager().updateGuildSettings(
                        event.getGuild().getId(), "logChannel_" + logType, channel.getId());

                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "Log Channel Set",
                        String.format("Set %s as the log channel for **%s** events.", channel.getAsMention(), logType)))
                        .queue();
            }
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed [400]",
                    "Failed to set log channel: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).queue();
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getOption("channel") != null
                ? event.getOption("channel").getAsChannel().asTextChannel()
                : null;

        if (channel == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Channel [100]",
                    "Please specify a channel for logging.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping typeOption = event.getOption("type");
        if (typeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Log Type [100]",
                    "Please specify which log type to add.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        String logType = typeOption.getAsString().toLowerCase();
        if (!VALID_LOG_TYPES.contains(logType) || "all".equals(logType)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Log Type [101]",
                    "Valid log types (except 'all'): "
                            + String.join(", ", VALID_LOG_TYPES.stream().filter(t -> !"all".equals(t)).toList()) + "\n"
                            +
                            "Error Code: **101** - Invalid Value\n" +
                            "Use `/error category:1` for full documentation."

            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(
                    event.getGuild().getId(), "logChannel_" + logType, channel.getId());

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Log Type Added",
                    String.format("Added **%s** logging to %s.", logType, channel.getAsMention()))).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed [400]",
                    "Failed to add log type: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).queue();
        }
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        OptionMapping typeOption = event.getOption("type");
        String logType = typeOption != null ? typeOption.getAsString().toLowerCase() : "all";

        if (!VALID_LOG_TYPES.contains(logType)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Log Type [101]",
                    "Valid log types: " + String.join(", ", VALID_LOG_TYPES) + "\n" +

                            "Error Code: **101** - Invalid Value\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        try {
            if ("all".equals(logType)) {
                // Remove all log channels
                for (String type : VALID_LOG_TYPES) {
                    if (!"all".equals(type)) {
                        ServerBot.getStorageManager().updateGuildSettings(
                                event.getGuild().getId(), "logChannel_" + type, null);
                    }
                }
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "All Logging Removed", "Removed logging for all event types.")).queue();
            } else {
                ServerBot.getStorageManager().updateGuildSettings(
                        event.getGuild().getId(), "logChannel_" + logType, null);

                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "Log Type Removed", String.format("Removed **%s** logging.", logType))).queue();
            }
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed [400]",
                    "Failed to remove log type: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        StringBuilder description = new StringBuilder();
        description.append("**Current Log Channel Configuration:**\n\n");

        boolean hasAnyLogging = false;
        for (String type : VALID_LOG_TYPES) {
            if ("all".equals(type))
                continue;

            // Get log channel for this type from storage
            String channelId = (String) ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId())
                    .get("logChannel_" + type);
            if (channelId != null) {
                TextChannel channel = event.getGuild().getTextChannelById(channelId);
                if (channel != null) {
                    description.append(String.format("**%s:** %s\n",
                            type.substring(0, 1).toUpperCase() + type.substring(1),
                            channel.getAsMention()));
                    hasAnyLogging = true;
                }
            }
        }

        if (!hasAnyLogging) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "No Log Channels", "No log channels have been configured for this server.")).setEphemeral(true)
                    .queue();
        } else {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Log Channels", description.toString())).setEphemeral(true).queue();
        }
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        try {
            for (String type : VALID_LOG_TYPES) {
                if (!"all".equals(type)) {
                    ServerBot.getStorageManager().updateGuildSettings(
                            event.getGuild().getId(), "logChannel_" + type, null);
                }
            }

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Log Channels Cleared", "All log channel configurations have been removed.")).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Clear Failed [400]",
                    "Failed to clear log channels: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("logchannel", "Configure logging channels with selective log types")
                .addSubcommands(
                        new SubcommandData("set", "Set log channel for specific or all event types")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to use for logging", true)
                                .addOptions(new OptionData(OptionType.STRING, "type", "Type of events to log", false)
                                        .addChoice("Bans", "bans")
                                        .addChoice("Kicks", "kicks")
                                        .addChoice("Warnings", "warnings")
                                        .addChoice("Mutes", "mutes")
                                        .addChoice("Member joins", "joins")
                                        .addChoice("Member leaves", "leaves")
                                        .addChoice("Messages", "messages")
                                        .addChoice("Voice events", "voice")
                                        .addChoice("Role changes", "roles")
                                        .addChoice("Nickname changes", "nicknames")
                                        .addChoice("All event types", "all")),
                        new SubcommandData("add", "Add log channel for a specific event type")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to use for logging", true)
                                .addOption(OptionType.STRING, "type", "Type of events to log", true),
                        new SubcommandData("remove", "Remove log channel for specific or all event types")
                                .addOptions(new OptionData(OptionType.STRING, "type", "Type of events to stop logging",
                                        false)
                                        .addChoice("Bans", "bans")
                                        .addChoice("Kicks", "kicks")
                                        .addChoice("Warnings", "warnings")
                                        .addChoice("Mutes", "mutes")
                                        .addChoice("Member joins", "joins")
                                        .addChoice("Member leaves", "leaves")
                                        .addChoice("Messages", "messages")
                                        .addChoice("Voice events", "voice")
                                        .addChoice("Role changes", "roles")
                                        .addChoice("Nickname changes", "nicknames")
                                        .addChoice("All event types", "all")),
                        new SubcommandData("list", "List all configured log channels"),
                        new SubcommandData("clear", "Clear all log channel configurations"));
    }

    @Override
    public String getName() {
        return "logchannel";
    }

    @Override
    public String getDescription() {
        return "Configure logging channels with selective log types";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }
}
