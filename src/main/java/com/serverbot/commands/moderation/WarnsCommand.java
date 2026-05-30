package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Warns command to view user warnings
 */
public class WarnsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();

        // Check permissions - can view own warnings or need mod perms for others
        if (!targetUser.equals(event.getUser()) && !PermissionManager.hasPermission(member, "mod.warns")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `mod.warns` permission to view other users' warnings."))
                    .setEphemeral(true).queue();
            return;
        }

        try {
            List<Map<String, Object>> warnings = ServerBot.getStorageManager().getWarnings(event.getGuild().getId(),
                    targetUser.getId());

            if (warnings.isEmpty()) {
                String target = targetUser.equals(event.getUser()) ? "You have" : targetUser.getName() + " has";
                event.replyEmbeds(EmbedUtils.createInfoEmbed(
                        "No Warnings", target + " no active warnings.")).setEphemeral(true).queue();
                return;
            }

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.WARNING_COLOR)
                    .setTitle("User Warnings")
                    .setDescription("Warnings for " + targetUser.getAsMention())
                    .setThumbnail(targetUser.getAvatarUrl());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

            int displayCount = Math.min(warnings.size(), 10);

            for (int i = 0; i < displayCount; i++) {
                Map<String, Object> warning = warnings.get(i);
                String reason = (String) warning.get("reason");
                String moderatorId = (String) warning.get("moderatorId");
                long timestamp = (Long) warning.get("timestamp");

                String moderatorName = "*unknown*";
                try {
                    User mod = event.getJDA().getUserById(moderatorId);
                    if (mod != null)
                        moderatorName = mod.getName();
                } catch (Exception ignored) {
                }

                String dateStr = formatter.format(Instant.ofEpochMilli(timestamp));

                String fieldTitle = "Warn #" + (i + 1);
                String fieldValue = "**Reason:** " + reason + "\n" +
                        "**Moderator:** " + moderatorName + "\n" +
                        "**Date:** " + dateStr;

                embed.addField(fieldTitle, fieldValue, false);
            }

            embed.addField("Stats",
                    "**Total Active Warnings:** " + warnings.size(), true);

            if (warnings.size() > displayCount) {
                embed.setFooter("Showing " + displayCount + " most recent warnings out of " + warnings.size());
            }

            // Send ephemeral if viewing own warnings, public if mod checking others
            boolean ephemeral = targetUser.equals(event.getUser());
            event.replyEmbeds(embed.build()).setEphemeral(ephemeral).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Storage Error", "Failed to retrieve warnings: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        return Commands.slash("warns", "View user warnings")
                .addOption(OptionType.USER, "user", "User to check warnings for (optional)", false);
    }

    @Override
    public String getName() {
        return "warns";
    }

    @Override
    public String getDescription() {
        return "View user warnings";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return false; // Users can check their own warnings
    }
}
