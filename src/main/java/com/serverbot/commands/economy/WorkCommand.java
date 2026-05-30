package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CooldownManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Instant;
import java.util.HashMap;
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

    private static final Map<String, Long> workCooldowns = new HashMap<>();

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
            String userKey = guildId + ":" + userId;

            // Get guild settings
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check if economy is enabled
            Boolean economyEnabled = (Boolean) guildSettings.get("enableEconomy");
            if (economyEnabled != null && !economyEnabled) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Economy Disabled",
                        "The economy system is disabled on this server.")).setEphemeral(true).queue();
                return;
            }

            // Get configurable settings
            Long workRewardSetting = (Long) guildSettings.get("workReward");
            Long workCooldownSetting = (Long) guildSettings.get("workCooldown");

            int baseWorkReward = workRewardSetting != null ? workRewardSetting.intValue() : 50;
            long cooldownSeconds = workCooldownSetting != null ? workCooldownSetting : 300L; // Default: 5 minutes

            // Check cooldown
            long currentTime = Instant.now().getEpochSecond();
            long lastWorkTime = workCooldowns.getOrDefault(userKey, 0L);
            long timeSinceLastWork = currentTime - lastWorkTime;

            if (timeSinceLastWork < cooldownSeconds) {
                long remainingMinutes = (cooldownSeconds - timeSinceLastWork) / 60;
                long remainingSecondsLeft = (cooldownSeconds - timeSinceLastWork) % 60;

                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Work Cooldown",
                        "You need to rest before working again!\n" +
                                "Time remaining: " + remainingMinutes + " minutes and " + remainingSecondsLeft
                                + " seconds"))
                        .setEphemeral(true).queue();
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

            // Update cooldown
            workCooldowns.put(userKey, currentTime);

            long cooldownMinutes = cooldownSeconds / 60;
            String cooldownText = cooldownMinutes > 0 ? cooldownMinutes + " minutes" : cooldownSeconds + " seconds";

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "💼 Work Complete!",
                    "**Job:** " + workScenario + "\n" +
                            "**Earned:** " + workReward + " points\n" +
                            "**New Balance:** " + newBalance + " points\n" +
                            "\n*You can work again in " + cooldownText + ".*"))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Work Failed",
                    "Failed to complete work: " + e.getMessage())).setEphemeral(true).queue();
        }
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
