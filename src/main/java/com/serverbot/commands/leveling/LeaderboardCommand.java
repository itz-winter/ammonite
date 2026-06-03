package com.serverbot.commands.leveling;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Leaderboard command to show XP/level rankings
 */
public class LeaderboardCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check if leveling is enabled
        if (!isLevelingEnabled(event.getGuild().getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Leveling Disabled", "The leveling system is disabled in this server.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        List<LevelUser> topUsers = getTopUsers(event.getGuild().getId(), 10);

        if (topUsers.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Leaderboard", "No users found with XP yet!")).queue();
            return;
        }

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("Leaderboard")
                .setDescription("Top " + Math.min(topUsers.size(), 10) + " users by level and XP");

        StringBuilder leaderboard = new StringBuilder();
        for (int i = 0; i < topUsers.size() && i < 10; i++) {
            LevelUser user = topUsers.get(i);
            String position = switch (i) {
                case 0 -> "🥇";
                case 1 -> "🥈";
                case 2 -> "🥉";
                default -> (i + 1) + ".";
            };

            String username = event.getJDA().getUserById(user.userId) != null
                    ? event.getJDA().getUserById(user.userId).getName()
                    : "Unknown User";

            leaderboard.append(position).append(" **").append(username).append("**\n")
                    .append("└ Level ").append(user.level).append(" (").append(user.experience).append(" XP)\n\n");
        }

        embed.setDescription(leaderboard.toString());

        // Show user's position if not in top 10
        int userPosition = getUserPosition(event.getGuild().getId(), event.getUser().getId());
        if (userPosition > 10) {
            LevelUser userStats = getUserStats(event.getGuild().getId(), event.getUser().getId());
            if (userStats != null) {
                embed.addField("Your Position",
                        "#" + userPosition + " - Level " + userStats.level + " (" + userStats.experience + " XP)",
                        false);
            }
        }

        event.replyEmbeds(embed.build()).queue();
    }

    private boolean isLevelingEnabled(String guildId) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            return (Boolean) settings.getOrDefault("enableLeveling", false);
        } catch (Exception e) {
            return false;
        }
    }

    private List<LevelUser> getTopUsers(String guildId, int limit) {
        List<LevelUser> users = new ArrayList<>();

        try {
            // Get all user level data and sort by level and experience
            for (Map.Entry<String, Map<String, Object>> entry : ServerBot.getStorageManager().getAllLevelData()
                    .entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(guildId + ":")) {
                    String userId = key.substring(guildId.length() + 1);
                    Map<String, Object> data = entry.getValue();

                    int level = ((Number) data.getOrDefault("level", 0)).intValue();
                    long experience = ((Number) data.getOrDefault("experience", 0)).longValue();

                    users.add(new LevelUser(userId, level, experience));
                }
            }

            // Sort by level first, then by experience
            users.sort((u1, u2) -> {
                if (u1.level != u2.level) {
                    return Integer.compare(u2.level, u1.level); // Higher level first
                }
                return Long.compare(u2.experience, u1.experience); // Higher XP first within same level
            });

            return users.subList(0, Math.min(limit, users.size()));

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private int getUserPosition(String guildId, String userId) {
        try {
            List<LevelUser> allUsers = getTopUsers(guildId, Integer.MAX_VALUE);
            for (int i = 0; i < allUsers.size(); i++) {
                if (allUsers.get(i).userId.equals(userId)) {
                    return i + 1;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    private LevelUser getUserStats(String guildId, String userId) {
        try {
            long experience = ServerBot.getStorageManager().getExperience(guildId, userId);
            int level = ServerBot.getStorageManager().getLevel(guildId, userId);
            if (experience > 0 || level > 0) {
                return new LevelUser(userId, level, experience);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static CommandData getCommandData() {
        return Commands.slash("leaderboard", "Show the XP/level leaderboard");
    }

    public static CommandData getLbCommandData() {
        return Commands.slash("lb", "Show the XP/level leaderboard (short form)");
    }

    @Override
    public String getName() {
        return "leaderboard";
    }

    @Override
    public String getDescription() {
        return "Show the XP/level leaderboard";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.LEVELING;
    }

    // Helper class for storing user level data
    private static class LevelUser {
        final String userId;
        final int level;
        final long experience;

        LevelUser(String userId, int level, long experience) {
            this.userId = userId;
            this.level = level;
            this.experience = experience;
        }
    }
}
