package com.serverbot.utils;

import com.serverbot.ServerBot;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Renders paginated leaderboard messages for both points and XP.
 * Used by /baltop, /leaderboard, /lb, and the LeaderboardButtonListener.
 */
public class LeaderboardRenderer {

    private static final int PAGE_SIZE = 10;

    /** Id prefix used for leaderboard button custom IDs. */
    public static final String LB_PREFIX = "leaderboard:";

    /**
     * Build a points leaderboard page as a MessageEditData.
     *
     * @param jda      JDA instance for user lookups
     * @param guildId  the guild
     * @param page     0-based page index
     * @param viewerId the user viewing (for "your position" row)
     */
    public static MessageEditData buildPointsPage(JDA jda, String guildId, int page, String viewerId) {
        boolean isEconomyEnabled = ServerBot.getStorageManager().isEconomyEnabled(guildId);
        if (!isEconomyEnabled) {
            return msg(EmbedUtils.createErrorEmbed("Economy Disabled",
                    "The economy system is disabled in this server."));
        }

        List<Map.Entry<String, Long>> all = ServerBot.getStorageManager().getTopBalances(guildId, Integer.MAX_VALUE);
        if (all.isEmpty()) {
            return msg(EmbedUtils.createInfoEmbed("Leaderboard",
                    "No users have any balance yet!"));
        }

        String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
        String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
        return buildPage(jda, all, page, viewerId, guildId, currencyIcon + " Points Leaderboard",
                null, currencyIcon, currencyName);
    }

    /**
     * Build an XP leaderboard page as a MessageEditData.
     */
    public static MessageEditData buildXpPage(JDA jda, String guildId, int page, String viewerId) {
        boolean isLevelingEnabled = ServerBot.getStorageManager().isLevelingEnabled(guildId);
        if (!isLevelingEnabled) {
            return msg(EmbedUtils.createErrorEmbed("Leveling Disabled",
                    "The leveling system is disabled in this server."));
        }

        List<Map.Entry<String, Integer>> all = ServerBot.getStorageManager().getTopLevels(guildId, Integer.MAX_VALUE);
        if (all.isEmpty()) {
            return msg(EmbedUtils.createInfoEmbed("Leaderboard",
                    "No users have any XP yet!"));
        }

        return buildXpPage(jda, all, page, viewerId, guildId);
    }

    /** Internal: build a points-style page from a list of (userId, Long) */
    private static MessageEditData buildPage(
            JDA jda, List<Map.Entry<String, Long>> sorted, int page,
            String viewerId, String guildId, String title,
            Color color, String icon, String unit) {

        int totalPages = (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setColor(color != null ? color : Color.GREEN);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            Map.Entry<String, Long> entry = sorted.get(i);
            String userId = entry.getKey();
            long value = entry.getValue();
            String name = resolveName(jda, userId);

            if (i < 3) {
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                sb.append(medal).append(" ").append(name).append("\n└ ").append(icon).append(" **")
                        .append(String.format("%,d", value)).append("** ").append(unit).append("\n\n");
            } else {
                sb.append("**").append(i + 1).append(".** ").append(name).append("\n└ ").append(icon).append(" **")
                        .append(String.format("%,d", value)).append("** ").append(unit).append("\n\n");
            }
        }
        embed.setDescription(sb.toString());

        // "Your position" footer
        long viewerValue = findViewerValue(sorted, viewerId);
        if (viewerValue > 0) {
            int pos = findPosition(sorted, viewerId);
            embed.setFooter("Your rank: #" + pos + "  •  Page " + (page + 1) + "/" + totalPages);
        } else {
            embed.setFooter("Page " + (page + 1) + "/" + totalPages);
        }

        return new MessageEditBuilder()
                .setEmbeds(embed.build())
                .setComponents(buildButtons(page, totalPages, "points"))
                .build();
    }

    /** Internal: build an XP page from a list of (userId, Integer) */
    private static MessageEditData buildXpPage(
            JDA jda, List<Map.Entry<String, Integer>> sorted, int page,
            String viewerId, String guildId) {

        int totalPages = (sorted.size() + PAGE_SIZE - 1) / PAGE_SIZE;
        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, sorted.size());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 XP Leaderboard")
                .setColor(Color.CYAN);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            String userId = entry.getKey();
            int level = entry.getValue();
            long xp = ServerBot.getStorageManager().getExperience(guildId, userId);
            String name = resolveName(jda, userId);

            if (i < 3) {
                String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                sb.append(medal).append(" ").append(name).append("\n└ Level **").append(level)
                        .append("** (").append(String.format("%,d", xp)).append(" XP)\n\n");
            } else {
                sb.append("**").append(i + 1).append(".** ").append(name).append("\n└ Level **").append(level)
                        .append("** (").append(String.format("%,d", xp)).append(" XP)\n\n");
            }
        }
        embed.setDescription(sb.toString());

        long viewerXp = ServerBot.getStorageManager().getExperience(guildId, viewerId);
        if (viewerXp > 0) {
            int pos = findPositionInt(sorted, viewerId);
            embed.setFooter("Your rank: #" + pos + "  •  Page " + (page + 1) + "/" + totalPages);
        } else {
            embed.setFooter("Page " + (page + 1) + "/" + totalPages);
        }

        return new MessageEditBuilder()
                .setEmbeds(embed.build())
                .setComponents(buildButtons(page, totalPages, "xp"))
                .build();
    }

    /** Build nav buttons + type switch */
    private static List<ActionRow> buildButtons(int page, int totalPages, String type) {
        List<Button> nav = new ArrayList<>();
        // Previous
        if (page > 0) {
            nav.add(Button.primary(LB_PREFIX + type + ":prev:" + (page - 1), "◀"));
        }
        // Next
        if (page + 1 < totalPages) {
            nav.add(Button.primary(LB_PREFIX + type + ":next:" + (page + 1), "▶"));
        }
        // Type switch
        String switchType = type.equals("points") ? "xp" : "points";
        String switchLabel = type.equals("points") ? "📊" : "🪙";
        nav.add(Button.secondary(LB_PREFIX + switchType + ":switch:0", switchLabel));

        return List.of(ActionRow.of(nav));
    }

    private static String resolveName(JDA jda, String userId) {
        User u = jda.getUserById(userId);
        return u != null ? u.getName() : "Unknown User";
    }

    private static long findViewerValue(List<Map.Entry<String, Long>> list, String userId) {
        for (Map.Entry<String, Long> e : list) {
            if (e.getKey().equals(userId)) return e.getValue();
        }
        return 0;
    }

    private static int findPosition(List<Map.Entry<String, Long>> list, String userId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equals(userId)) return i + 1;
        }
        return list.size() + 1;
    }

    private static int findPositionInt(List<Map.Entry<String, Integer>> list, String userId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getKey().equals(userId)) return i + 1;
        }
        return list.size() + 1;
    }

    private static MessageEditData msg(net.dv8tion.jda.api.entities.MessageEmbed embed) {
        return new MessageEditBuilder().setEmbeds(embed).build();
    }
}
