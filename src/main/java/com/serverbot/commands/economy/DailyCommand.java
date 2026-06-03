package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CooldownManager;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Random;

/**
 * Daily reward command for claiming daily points
 */
public class DailyCommand implements SlashCommand {

    private static final Random RANDOM = new Random();

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "economy.daily")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You don't have permission to use the daily command!"))
                    .setEphemeral(true).queue();
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            String userId = event.getUser().getId();

            // Get guild settings
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check if economy is enabled
            Boolean economyEnabled = (Boolean) guildSettings.get("enableEconomy");
            if (economyEnabled != null && !economyEnabled) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Economy Disabled",
                        "The economy system is disabled on this server.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            // Get configurable daily reward amount with proper randomization
            @SuppressWarnings("unchecked")
            Map<String, Object> dailySettings = (Map<String, Object>) guildSettings.get("dailyRewards");
            int minReward = 100;
            int maxReward = 300;

            if (dailySettings != null) {
                Number min = (Number) dailySettings.get("minAmount");
                Number max = (Number) dailySettings.get("maxAmount");
                if (min != null)
                    minReward = min.intValue();
                if (max != null)
                    maxReward = max.intValue();
            } else {
                // Fallback to old setting
                Object dailyRewardSetting = guildSettings.get("dailyReward");
                if (dailyRewardSetting instanceof Number) {
                    int baseReward = ((Number) dailyRewardSetting).intValue();
                    minReward = (int) (baseReward * 0.8); // 80% of base
                    maxReward = (int) (baseReward * 1.2); // 120% of base
                }
            }

            // Check last daily claim for this user
            String lastDaily = (String) guildSettings.get("lastDaily_" + userId);
            String today = LocalDate.now(ZoneId.systemDefault()).toString();

            if (today.equals(lastDaily)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Already Claimed",
                        "You have already claimed your daily reward today!\n" +
                                "Come back tomorrow to claim your next reward."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            // Calculate random reward amount between min and max
            int rewardAmount = minReward + RANDOM.nextInt(maxReward - minReward + 1);

            // Check for streak bonus
            String yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString();
            boolean hasStreak = yesterday.equals(lastDaily);
            int streakBonus = 0;

            if (hasStreak) {
                // Streak bonus: 25% to 50% of the reward amount
                int bonusMin = (int) (rewardAmount * 0.25);
                int bonusMax = (int) (rewardAmount * 0.5);
                streakBonus = bonusMin + RANDOM.nextInt(bonusMax - bonusMin + 1);
                rewardAmount += streakBonus;
            }

            // Add the reward
            ServerBot.getStorageManager().addBalance(guildId, userId, rewardAmount);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            // Update last daily claim
            ServerBot.getStorageManager().updateGuildSettings(guildId, "lastDaily_" + userId, today);

            String description = "**Daily Reward:** " + (rewardAmount - streakBonus) + " points\n";
            if (hasStreak) {
                description += "**Streak Bonus:** " + streakBonus + " points 🔥\n";
            }
            description += "**Total Earned:** " + rewardAmount + " points\n" +
                    "**New Balance:** " + newBalance + " points\n";

            if (!hasStreak && lastDaily != null) {
                description += "\n*Your streak was broken! Claim daily to build up streak bonuses.*";
            } else if (hasStreak) {
                description += "\n*Great! You maintained your daily streak! 🔥*";
            } else {
                description += "\n*Start your daily streak by claiming tomorrow!*";
            }

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "💰 Daily Reward Claimed!",
                    description)).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Daily Claim Failed",
                    "Failed to claim daily reward: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("daily", "Claim your daily reward");
    }

    @Override
    public String getName() {
        return "daily";
    }

    @Override
    public String getDescription() {
        return "Claim your daily reward (100-500 points + streak bonus)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }
}
