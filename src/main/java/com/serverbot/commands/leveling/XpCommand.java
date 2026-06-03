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
 * XP management command for adding, subtracting, and setting XP
 */
public class XpCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "leveling.admin.xp")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `leveling.admin.xp` permission to manage XP."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        int expAmount = event.getOption("exp").getAsInt();
        User targetUser = event.getOption("user").getAsUser();

        try {
            long currentExp = ServerBot.getStorageManager().getExperience(event.getGuild().getId(), targetUser.getId());
            long newExp = currentExp;

            switch (action.toLowerCase()) {
                case "add" -> {
                    newExp = currentExp + expAmount;
                    ServerBot.getStorageManager().addExperience(event.getGuild().getId(), targetUser.getId(),
                            expAmount);
                }
                case "subtract" -> {
                    newExp = Math.max(0, currentExp - expAmount);
                    // Use negative addExperience to subtract
                    ServerBot.getStorageManager().addExperience(event.getGuild().getId(), targetUser.getId(),
                            -expAmount);
                }
                case "set" -> {
                    newExp = Math.max(0, expAmount);
                    // Calculate difference and use addExperience
                    long difference = newExp - currentExp;
                    ServerBot.getStorageManager().addExperience(event.getGuild().getId(), targetUser.getId(),
                            difference);
                }
                default -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Invalid Action", "Valid actions are: add, subtract, set")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    return;
                }
            }

            int newLevel = ServerBot.getStorageManager().getLevel(event.getGuild().getId(), targetUser.getId());

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "XP " + action.substring(0, 1).toUpperCase() + action.substring(1) + "ed",
                    "**User:** " + targetUser.getAsMention() + "\n" +
                            "**Action:** " + action.toUpperCase() + " " + expAmount + " XP\n" +
                            "**Previous XP:** " + currentExp + "\n" +
                            "**New XP:** " + newExp + "\n" +
                            "**Current Level:** " + newLevel + "\n" +
                            "**Moderator:** " + moderator.getAsMention()))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "XP Management Failed",
                    "Failed to manage XP: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("xp", "Manage user XP")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Action to perform", true)
                                .addChoice("Add XP", "add")
                                .addChoice("Subtract XP", "subtract")
                                .addChoice("Set XP", "set"),
                        new OptionData(OptionType.INTEGER, "exp", "Amount of XP", true)
                                .setMinValue(0)
                                .setMaxValue(1000000),
                        new OptionData(OptionType.USER, "user", "Target user", true));
    }

    @Override
    public String getName() {
        return "xp";
    }

    @Override
    public String getDescription() {
        return "Manage user XP (add, subtract, set)";
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
