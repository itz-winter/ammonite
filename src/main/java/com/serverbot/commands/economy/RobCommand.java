package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;

/**
 * Rob command for attempting to steal points from other users
 */
public class RobCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(RobCommand.class);
    private final Random random = new Random();

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check if economy is enabled
        if (!isEconomyEnabled(event.getGuild().getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Economy Disabled", "The economy system is disabled in this server.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User robber = event.getUser();
        User target = event.getOption("user").getAsUser();

        // Validation checks
        if (target.isBot()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Target", "You cannot rob bots.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (robber.equals(target)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Target", "You cannot rob yourself.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        try {
            long robberBalance = getUserBalance(event.getGuild().getId(), robber.getId());

            // Check if robber has minimum balance to attempt robbery
            if (robberBalance < 100) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Not Enough Money", "You need at least 100 points to attempt a robbery.")).setEphemeral(true)
                        .queue();
                return;
            }

            long targetBalance = getUserBalance(event.getGuild().getId(), target.getId());

            // Check if target has money to rob
            if (targetBalance <= 0) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "No Money", target.getName() + " has no points to rob!")).queue();
                return;
            }

            // Success rate: 35% chance of success
            boolean success = random.nextDouble() < 0.35;

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder();

            if (success) {
                // Calculate stolen amount (10-25% of target's balance, max 500)
                long maxSteal = Math.min(targetBalance / 4, 500); // 25% max or 500 points
                long minSteal = Math.max(1, targetBalance / 10); // 10% min or 1 point
                long stolenAmount = minSteal + random.nextLong(maxSteal - minSteal + 1);

                // Transfer money
                updateUserBalance(event.getGuild().getId(), robber.getId(), robberBalance + stolenAmount);
                updateUserBalance(event.getGuild().getId(), target.getId(), targetBalance - stolenAmount);

                embed.setColor(EmbedUtils.SUCCESS_COLOR)
                        .setTitle("🎯 Robbery Successful!")
                        .setDescription(
                                "**" + robber.getName() + "** successfully robbed **" + target.getName() + "**!")
                        .addField("💰 Stolen", stolenAmount + " points", true)
                        .addField("🏃 Your Balance", (robberBalance + stolenAmount) + " points", true)
                        .addField("😢 Victim's Balance", (targetBalance - stolenAmount) + " points", true);

            } else {
                // Rob failed - robber loses money as penalty
                long penalty = Math.min(robberBalance / 10, 100); // 10% of balance or 100 points max
                if (penalty > 0) {
                    updateUserBalance(event.getGuild().getId(), robber.getId(), robberBalance - penalty);
                }

                embed.setColor(EmbedUtils.ERROR_COLOR)
                        .setTitle("🚨 Robbery Failed!")
                        .setDescription("**" + robber.getName() + "** tried to rob **" + target.getName()
                                + "** but got caught!")
                        .addField("💸 Fine", penalty + " points", true)
                        .addField("💰 Your Balance", (robberBalance - penalty) + " points", true)
                        .addField("😤 Victim", target.getName() + " kept their money safe!", true);
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            logger.error("Error executing rob command", e);
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Robbery Failed", "An error occurred during the robbery attempt.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private boolean isEconomyEnabled(String guildId) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            return ((Boolean) settings.getOrDefault("enableEconomy", true));
        } catch (Exception e) {
            logger.error("Failed to check economy status", e);
            return false;
        }
    }

    private long getUserBalance(String guildId, String userId) {
        try {
            return ServerBot.getStorageManager().getBalance(guildId, userId);
        } catch (Exception e) {
            logger.error("Failed to get user balance", e);
            return 0;
        }
    }

    private void updateUserBalance(String guildId, String userId, long newBalance) {
        try {
            ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);
        } catch (Exception e) {
            logger.error("Failed to update user balance", e);
        }
    }

    @Override
    public String getName() {
        return "rob";
    }

    @Override
    public String getDescription() {
        return "Attempt to rob points from another user";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        return Commands.slash("rob", "Attempt to rob points from another user")
                .addOption(OptionType.USER, "user", "User to rob", true);
    }
}
