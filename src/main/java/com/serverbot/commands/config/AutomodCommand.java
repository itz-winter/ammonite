package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * Auto-moderation configuration command
 */
public class AutomodCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member executor = event.getMember();
        if (!PermissionManager.hasPermission(executor, "admin.automod")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need administrator permissions to configure automod."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        String feature = event.getOption("feature").getAsString();

        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            switch (action.toLowerCase()) {
                case "enable" -> {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "automod_" + feature + "_enabled", true);
                    if (event.getOption("threshold") != null) {
                        ServerBot.getStorageManager().updateGuildSettings(guildId, "automod_" + feature + "_threshold",
                                event.getOption("threshold").getAsInt());
                    }

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "Automod Feature Enabled",
                            "**Feature:** " + feature.toUpperCase().replace("_", " ") + "\n" +
                                    "**Status:** Enabled\n" +
                                    "**Configured by:** " + executor.getAsMention()))
                            .queue();
                }
                case "disable" -> {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "automod_" + feature + "_enabled",
                            false);

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "Automod Feature Disabled",
                            "**Feature:** " + feature.toUpperCase().replace("_", " ") + "\n" +
                                    "**Status:** Disabled\n" +
                                    "**Configured by:** " + executor.getAsMention()))
                            .queue();
                }
                case "view" -> {
                    boolean enabled = guildSettings.getOrDefault("automod_" + feature + "_enabled", false).equals(true);
                    Object threshold = guildSettings.get("automod_" + feature + "_threshold");

                    String description = "**Feature:** " + feature.toUpperCase().replace("_", " ") + "\n" +
                            "**Status:** " + (enabled ? "Enabled" : "Disabled");

                    if (threshold != null) {
                        description += "\n**Threshold:** " + threshold;
                    }

                    event.replyEmbeds(EmbedUtils.createInfoEmbed(
                            "Automod Configuration",
                            description)).setEphemeral(true).queue();
                }
                default -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Invalid Action", "Valid actions are: enable, disable, view")).setEphemeral(true).queue();
                }
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Automod Config Failed",
                    "Failed to configure automod: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("automod", "Configure auto-moderation features")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Action to perform", true)
                                .addChoice("Enable", "enable")
                                .addChoice("Disable", "disable")
                                .addChoice("View", "view"),
                        new OptionData(OptionType.STRING, "feature", "Automod feature to configure", true)
                                .addChoice("Anti-Spam", "anti_spam")
                                .addChoice("Bad Words", "bad_words")
                                .addChoice("Caps Lock", "caps_lock")
                                .addChoice("Repeated Text", "repeated_text")
                                .addChoice("Mass Mentions", "mass_mentions")
                                .addChoice("Link Filter", "link_filter"),
                        new OptionData(OptionType.INTEGER, "threshold", "Threshold value for the feature (optional)",
                                false)
                                .setMinValue(1)
                                .setMaxValue(100));
    }

    @Override
    public String getName() {
        return "automod";
    }

    @Override
    public String getDescription() {
        return "Configure auto-moderation features and settings";
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
