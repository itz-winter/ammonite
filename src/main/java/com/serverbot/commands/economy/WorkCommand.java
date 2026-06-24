package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CooldownManager;
import com.serverbot.utils.EmbedUtils;
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
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            String userId = event.getUser().getId();

            // Get guild settings
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check if economy is enabled (defensive — CommandListener also checks)
            if (!ServerBot.getStorageManager().isEconomyEnabled(guildId)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Economy Disabled",
                        "The economy system is disabled on this server.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            // Get configurable settings (safe extraction from JSON - Gson may return Double)
            Number workRewardNum = (Number) guildSettings.get("workReward");
            Number workCooldownNum = (Number) guildSettings.get("workCooldown");

            int baseWorkReward = workRewardNum != null ? workRewardNum.intValue() : 50;
            // workCooldown is stored in seconds directly (via /settings duration input)
            int cooldownSeconds = workCooldownNum != null ? workCooldownNum.intValue() : 300;

            // Check cooldown via shared CooldownManager
            if (CooldownManager.isOnCooldown(userId, "work", cooldownSeconds)) {
                long remaining = CooldownManager.getRemainingCooldown(userId, "work", cooldownSeconds);
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Work Cooldown",
                        "You need to rest before working again!\nTime remaining: **" + formatDuration(remaining) + "**"))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            // Calculate work reward with randomization (75%-125% of base)
            int variation = (int) (baseWorkReward * 0.25); // 25% variation
            int workReward = baseWorkReward + RANDOM.nextInt(variation * 2 + 1) - variation;

            // Random work scenario — use custom messages if configured
            java.util.List<String> customJobs = ServerBot.getStorageManager().getCustomGuildMessages(guildId, "work");
            String[] jobPool = (customJobs != null && !customJobs.isEmpty())
                    ? customJobs.toArray(String[]::new)
                    : WORK_JOBS;
            String workScenario = jobPool[RANDOM.nextInt(jobPool.length)];

            // Add the reward
            ServerBot.getStorageManager().addBalance(guildId, userId, workReward);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            // Update cooldown via shared CooldownManager
            CooldownManager.setCooldown(userId, "work");

            long cooldownMinutes = cooldownSeconds / 60;
            String cooldownText = cooldownMinutes > 0 ? cooldownMinutes + " minutes" : cooldownSeconds + " seconds";

            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    currencyIcon + " Work Complete!",
                    "**Job:** " + workScenario + "\n" +
                            "**Earned:** " + workReward + " " + currencyName + "\n" +
                            "**New Balance:** " + newBalance + " " + currencyName + "\n" +
                            "\n*You can work again in " + formatDuration(cooldownSeconds) + ".*"))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Work Failed",
                    "Failed to complete work: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    /**
     * Format a duration in seconds to a human-readable string.
     * Examples: 120 -> "2hr", 130 -> "2hr 10min", 3600 -> "1hr"
     */
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
