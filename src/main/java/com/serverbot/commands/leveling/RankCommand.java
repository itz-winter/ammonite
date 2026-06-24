package com.serverbot.commands.leveling;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.Map;

/**
 * Rank command for displaying user level/XP information
 */
public class RankCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user") != null
                ? event.getOption("user").getAsUser()
                : event.getUser();

        try {
            String guildId = event.getGuild().getId();
            String userId = targetUser.getId();

            long exp = ServerBot.getStorageManager().getExperience(guildId, userId);
            int level = ServerBot.getStorageManager().getLevel(guildId, userId);

            // Calculate XP for current and next level
            long currentLevelExp = calculateExpForLevel(level);
            long nextLevelExp = calculateExpForLevel(level + 1);
            long expInCurrentLevel = exp - currentLevelExp;
            long expNeededForNext = nextLevelExp - exp;

            // Calculate progress percentage
            long expRequiredThisLevel = nextLevelExp - currentLevelExp;
            double progressPercent = expRequiredThisLevel > 0
                    ? (double) expInCurrentLevel / expRequiredThisLevel * 100
                    : 100.0;

            // Create progress bar
            String progressBar = createProgressBar(progressPercent);

            // Get rank (position in leaderboard)
            int rank = getUserRank(guildId, userId);

            String description = "**Level:** " + level + "\n" +
                    "**Total XP:** " + exp + "\n" +
                    "**Rank:** #" + rank + "\n" +
                    "**Progress:** " + expInCurrentLevel + "/" + expRequiredThisLevel + " XP\n" +
                    "**XP to Next Level:** " + expNeededForNext + "\n" +
                    progressBar + " " + String.format("%.1f", progressPercent) + "%";

            event.replyEmbeds(
                    EmbedUtils.createDefaultEmbed(
                            targetUser.getName() + "'s Rank",
                            description))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Rank Display Failed",
                    "Failed to display rank information: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private long calculateExpForLevel(int level) {
        return com.serverbot.ServerBot.getStorageManager().calculateXpForLevel(level);
    }

    private String createProgressBar(double percent) {
        int filledBars = (int) (percent / 10);
        int emptyBars = 10 - filledBars;

        StringBuilder bar = new StringBuilder();
        bar.append("▰".repeat(Math.max(0, filledBars)));
        bar.append("▱".repeat(Math.max(0, emptyBars)));

        return bar.toString();
    }

    private int getUserRank(String guildId, String userId) {
        try {
            // Get top level users using the available method
            List<Map.Entry<String, Integer>> topUsers = ServerBot.getStorageManager().getTopLevels(guildId, 1000);

            // Find the user's position
            for (int i = 0; i < topUsers.size(); i++) {
                Map.Entry<String, Integer> user = topUsers.get(i);
                if (userId.equals(user.getKey())) {
                    return i + 1;
                }
            }

            // User not found in rankings
            return topUsers.size() + 1;

        } catch (Exception e) {
            return 1; // Default rank if calculation fails
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("rank", "Display rank information for a user")
                .addOptions(
                        new OptionData(OptionType.USER, "user", "User to check rank for (optional)", false));
    }

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public String getDescription() {
        return "Display rank information for yourself or another user";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.LEVELING;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }
}
