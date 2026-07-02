package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CooldownManager;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.context.CommandContext;
import com.serverbot.utils.context.SlashCommandContext;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Map;
import java.util.Random;

/**
 * Work command for earning points through various jobs
 */
public class WorkCommand implements SlashCommand {

    private static final Random RANDOM = new Random();

    // Work scenarios
    private static final String[] WORK_JOBS = {
            "You worked as a programmer and debugged some code",
            "You delivered packages around town",
            "You helped at a local restaurant",
            "You walked dogs at the animal shelter",
            "You worked as a cashier at a store",
            "You did freelance graphic design",
            "You tutored students online",
            "You worked at a coffee shop",
            "You did yard work for neighbors",
            "You helped organize an event"
    };

    @Override
    public boolean supportsCommandContext() {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        executeWithContext(new SlashCommandContext(event));
    }

    @Override
    public void executeWithContext(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers."));
            return;
        }

        try {
            String guildId = ctx.getGuildId();
            String userId = ctx.getUserId();

            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            if (!ServerBot.getStorageManager().isEconomyEnabled(guildId)) {
                ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                        "Economy Disabled", "The economy system is disabled on this server."));
                return;
            }

            Number workRewardNum = (Number) guildSettings.get("workReward");
            Number workCooldownNum = (Number) guildSettings.get("workCooldown");

            int baseWorkReward = workRewardNum != null ? workRewardNum.intValue() : 50;
            int cooldownSeconds = workCooldownNum != null ? workCooldownNum.intValue() : 300;

            if (CooldownManager.isOnCooldown(userId, "work", cooldownSeconds)) {
                long remaining = CooldownManager.getRemainingCooldown(userId, "work", cooldownSeconds);
                ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                        "Work Cooldown",
                        "You need to rest before working again!\nTime remaining: **" + formatDuration(remaining) + "**"));
                return;
            }

            int variation = (int) (baseWorkReward * 0.25);
            int workReward = baseWorkReward + RANDOM.nextInt(variation * 2 + 1) - variation;

            java.util.List<String> customJobs = ServerBot.getStorageManager().getCustomGuildMessages(guildId, "work");
            String[] jobPool = (customJobs != null && !customJobs.isEmpty())
                    ? customJobs.toArray(String[]::new)
                    : WORK_JOBS;
            String workScenario = jobPool[RANDOM.nextInt(jobPool.length)];

            ServerBot.getStorageManager().addBalance(guildId, userId, workReward);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            CooldownManager.setCooldown(userId, "work");

            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            ctx.replyRespectingPreference(EmbedUtils.createSuccessEmbed(
                    currencyIcon + " Work Complete!",
                    "**Job:** " + workScenario + "\n" +
                            "**Earned:** " + workReward + " " + currencyName + "\n" +
                            "**New Balance:** " + newBalance + " " + currencyName + "\n" +
                            "\n*You can work again in " + formatDuration(cooldownSeconds) + ".*"));

        } catch (Exception e) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                    "Work Failed", "Failed to complete work: " + e.getMessage()));
        }
    }

    private String formatDuration(long totalSeconds) {
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long secs = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("hr ");
        if (minutes > 0) sb.append(minutes).append("min ");
        if (secs > 0 && days == 0 && hours == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    public static CommandData getCommandData() {
        return Commands.slash("work", "Work a job to earn points");
    }

    @Override
    public String getName() {
        return "work";
    }

    @Override
    public String getDescription() {
        return "Work a job to earn points (30 minute cooldown)";
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

