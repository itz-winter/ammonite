package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.context.CommandContext;
import com.serverbot.utils.context.SlashCommandContext;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Random;

public class DailyCommand implements SlashCommand {

    private static final Random RANDOM = new Random();

    @Override
    public boolean supportsCommandContext() { return true; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        executeWithContext(new SlashCommandContext(event));
    }

    @Override
    public void executeWithContext(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Guild Only", "This command can only be used in servers."));
            return;
        }
        if (!PermissionManager.hasPermission(ctx.getMember(), "economy.daily")) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Insufficient Permissions", "You don'\''t have permission to use the daily command!"));
            return;
        }
        try {
            String guildId = ctx.getGuildId();
            String userId = ctx.getUserId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
            if (!ServerBot.getStorageManager().isEconomyEnabled(guildId)) {
                ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Economy Disabled", "The economy system is disabled on this server."));
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dailySettings = (Map<String, Object>) guildSettings.get("dailyRewards");
            int minReward = 100, maxReward = 300;
            if (dailySettings != null) {
                Number min = (Number) dailySettings.get("minAmount");
                Number max = (Number) dailySettings.get("maxAmount");
                if (min != null) minReward = min.intValue();
                if (max != null) maxReward = max.intValue();
            } else {
                Object dailyRewardSetting = guildSettings.get("dailyReward");
                if (dailyRewardSetting instanceof Number) {
                    int base = ((Number) dailyRewardSetting).intValue();
                    minReward = (int) (base * 0.8);
                    maxReward = (int) (base * 1.2);
                }
            }
            String lastDaily = (String) guildSettings.get("lastDaily_" + userId);
            String today = LocalDate.now(ZoneId.systemDefault()).toString();
            if (today.equals(lastDaily)) {
                ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Already Claimed", "You have already claimed your daily reward today!\nCome back tomorrow."));
                return;
            }
            int rewardAmount = minReward + RANDOM.nextInt(Math.max(1, maxReward - minReward + 1));
            String yesterday = LocalDate.now(ZoneId.systemDefault()).minusDays(1).toString();
            boolean hasStreak = yesterday.equals(lastDaily);
            int streakBonus = 0;
            if (hasStreak) {
                int bonusMin = (int)(rewardAmount * 0.25), bonusMax = (int)(rewardAmount * 0.5);
                streakBonus = bonusMin + RANDOM.nextInt(Math.max(1, bonusMax - bonusMin + 1));
                rewardAmount += streakBonus;
            }
            ServerBot.getStorageManager().addBalance(guildId, userId, rewardAmount);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
            ServerBot.getStorageManager().updateGuildSettings(guildId, "lastDaily_" + userId, today);
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            String description = "**Daily Reward:** " + (rewardAmount - streakBonus) + " " + currencyName + "\n";
            if (hasStreak) description += "**Streak Bonus:** " + streakBonus + " " + currencyName + " \uD83D\uDD25\n";
            description += "**Total Earned:** " + rewardAmount + " " + currencyName + "\n**New Balance:** " + newBalance + " " + currencyName + "\n";
            if (!hasStreak && lastDaily != null) description += "\n*Your streak was broken! Claim daily to build up streak bonuses.*";
            else if (hasStreak) description += "\n*Great! You maintained your daily streak! \uD83D\uDD25*";
            else description += "\n*Start your daily streak by claiming tomorrow!*";
            ctx.replyRespectingPreference(EmbedUtils.createSuccessEmbed(currencyIcon + " Daily Reward Claimed!", description));
        } catch (Exception e) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Daily Claim Failed", "Failed to claim daily reward: " + e.getMessage()));
        }
    }

    public static CommandData getCommandData() { return Commands.slash("daily", "Claim your daily reward"); }
    @Override public String getName() { return "daily"; }
    @Override public String getDescription() { return "Claim your daily reward (100-500 points + streak bonus)"; }
    @Override public CommandCategory getCategory() { return CommandCategory.ECONOMY; }
    @Override public boolean requiresPermissions() { return false; }
}