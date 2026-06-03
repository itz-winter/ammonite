package com.serverbot.commands.leveling;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Level management command for setting and managing user levels
 */
public class LevelCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "leveling.admin.level")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `leveling.admin.level` permission to manage levels."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        int levelAmount = event.getOption("level").getAsInt();
        User targetUser = event.getOption("user").getAsUser();

        try {
            long currentExp = ServerBot.getStorageManager().getExperience(event.getGuild().getId(), targetUser.getId());
            int currentLevel = ServerBot.getStorageManager().getLevel(event.getGuild().getId(), targetUser.getId());

            int newLevel = currentLevel;
            long newExp = currentExp;

            switch (action.toLowerCase()) {
                case "add" -> {
                    newLevel = currentLevel + levelAmount;
                    // Calculate XP required for the new level
                    newExp = calculateExpForLevel(newLevel);
                    ServerBot.getStorageManager().addExperience(event.getGuild().getId(), targetUser.getId(),
                            (int) (newExp - currentExp));
                }
                case "subtract" -> {
                    newLevel = Math.max(0, currentLevel - levelAmount);
                    newExp = calculateExpForLevel(newLevel);
                    // Since we can't subtract XP directly, we'll add the difference to reach the
                    // target level
                    long expDiff = newExp - currentExp;
                    if (expDiff != 0) {
                        ServerBot.getStorageManager().addExperience(event.getGuild().getId(), targetUser.getId(),
                                (int) expDiff);
                    }
                }
                case "set" -> {
                    newLevel = Math.max(0, levelAmount);
                    newExp = calculateExpForLevel(newLevel);
                    long expDiff = newExp - currentExp;
                    if (expDiff != 0) {
                        ServerBot.getStorageManager().addExperience(event.getGuild().getId(), targetUser.getId(),
                                (int) expDiff);
                    }
                }
                default -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Invalid Action", "Valid actions are: add, subtract, set")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    return;
                }
            }

            // Get the actual new level after XP changes
            int finalLevel = ServerBot.getStorageManager().getLevel(event.getGuild().getId(), targetUser.getId());
            long finalExp = ServerBot.getStorageManager().getExperience(event.getGuild().getId(), targetUser.getId());

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Level " + action.substring(0, 1).toUpperCase() + action.substring(1) + "ed",
                    "**User:** " + targetUser.getAsMention() + "\n" +
                            "**Action:** " + action.toUpperCase() + " " + levelAmount + " levels\n" +
                            "**Previous Level:** " + currentLevel + " (" + currentExp + " XP)\n" +
                            "**New Level:** " + finalLevel + " (" + finalExp + " XP)\n" +
                            "**Moderator:** " + moderator.getAsMention()))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Level Management Failed",
                    "Failed to manage level: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private long calculateExpForLevel(int level) {
        // Simple XP calculation: level^2 * 100 + level * 50
        return (long) (Math.pow(level, 2) * 100 + level * 50);
    }

    public static CommandData getCommandData() {
        return Commands.slash("level", "Manage user levels")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Action to perform", true)
                                .addChoice("Add Levels", "add")
                                .addChoice("Subtract Levels", "subtract")
                                .addChoice("Set Level", "set"),
                        new OptionData(OptionType.INTEGER, "level", "Number of levels", true)
                                .setMinValue(0)
                                .setMaxValue(1000),
                        new OptionData(OptionType.USER, "user", "Target user", true));
    }

    @Override
    public String getName() {
        return "level";
    }

    @Override
    public String getDescription() {
        return "Manage user levels (add, subtract, set)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.LEVELING;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
