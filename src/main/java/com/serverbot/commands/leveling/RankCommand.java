package com.serverbot.commands.leveling;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.context.CommandContext;
import com.serverbot.utils.context.SlashCommandContext;
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

        User targetUser = ctx.hasOption("user") ? ctx.getUserOption("user") : ctx.getUser();
        if (targetUser == null) targetUser = ctx.getUser();

        try {
            String guildId = ctx.getGuildId();
            String userId = targetUser.getId();

            long exp = ServerBot.getStorageManager().getExperience(guildId, userId);
            int level = ServerBot.getStorageManager().getLevel(guildId, userId);

            long currentLevelExp = calculateExpForLevel(level);
            long nextLevelExp = calculateExpForLevel(level + 1);
            long expInCurrentLevel = exp - currentLevelExp;
            long expNeededForNext = nextLevelExp - exp;

            long expRequiredThisLevel = nextLevelExp - currentLevelExp;
            double progressPercent = expRequiredThisLevel > 0
                    ? (double) expInCurrentLevel / expRequiredThisLevel * 100
                    : 100.0;

            String progressBar = createProgressBar(progressPercent);
            int rank = getUserRank(guildId, userId);

            String description = "**Level:** " + level + "\n" +
                    "**Total XP:** " + exp + "\n" +
                    "**Rank:** #" + rank + "\n" +
                    "**Progress:** " + expInCurrentLevel + "/" + expRequiredThisLevel + " XP\n" +
                    "**XP to Next Level:** " + expNeededForNext + "\n" +
                    progressBar + " " + String.format("%.1f", progressPercent) + "%";

            ctx.replyRespectingPreference(EmbedUtils.createDefaultEmbed(targetUser.getName() + "'s Rank", description));

        } catch (Exception e) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                    "Rank Display Failed", "Failed to display rank information: " + e.getMessage()));
        }
    }

    private long calculateExpForLevel(int level) {
        return ServerBot.getStorageManager().calculateXpForLevel(level);
    }

    private String createProgressBar(double percent) {
        int filledBars = (int) (percent / 10);
        int emptyBars = 10 - filledBars;
        return "â–°".repeat(Math.max(0, filledBars)) + "â–±".repeat(Math.max(0, emptyBars));
    }

    private int getUserRank(String guildId, String userId) {
        try {
            List<Map.Entry<String, Integer>> topUsers = ServerBot.getStorageManager().getTopLevels(guildId, 1000);
            for (int i = 0; i < topUsers.size(); i++) {
                if (userId.equals(topUsers.get(i).getKey())) {
                    return i + 1;
                }
            }
            return topUsers.size() + 1;
        } catch (Exception e) {
            return 1;
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("rank", "Display rank information for a user")
                .addOptions(new OptionData(OptionType.USER, "user", "User to check rank for (optional)", false));
    }

    @Override
    public String getName() { return "rank"; }

    @Override
    public String getDescription() { return "Display rank information for yourself or another user"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.LEVELING; }

    @Override
    public boolean requiresPermissions() { return false; }
}
