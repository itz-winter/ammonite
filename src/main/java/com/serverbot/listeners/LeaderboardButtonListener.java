package com.serverbot.listeners;

import com.serverbot.utils.LeaderboardRenderer;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Handles button interactions for leaderboard pagination and type switching.
 * Button IDs: leaderboard:{type}:{action}:{page}
 *   type = "points" or "xp"
 *   action = "prev", "next", or "switch"
 *   page = 0-based page number
 */
public class LeaderboardButtonListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("leaderboard:")) return;

        String[] parts = id.split(":", 4);
        if (parts.length < 4) return;

        String type = parts[1];  // "points" or "xp"
        String action = parts[2]; // "prev", "next", "switch"
        int page;
        try {
            page = Integer.parseInt(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }

        event.deferEdit().queue();

        String guildId = event.getGuild().getId();
        String viewerId = event.getUser().getId();

        if ("switch".equals(action)) {
            // Toggle between points and XP
            if ("points".equals(type)) {
                event.getHook().editOriginal(LeaderboardRenderer.buildPointsPage(event.getJDA(), guildId, page, viewerId)).queue();
            } else {
                event.getHook().editOriginal(LeaderboardRenderer.buildXpPage(event.getJDA(), guildId, page, viewerId)).queue();
            }
        } else {
            // prev / next
            if ("points".equals(type)) {
                event.getHook().editOriginal(LeaderboardRenderer.buildPointsPage(event.getJDA(), guildId, page, viewerId)).queue();
            } else {
                event.getHook().editOriginal(LeaderboardRenderer.buildXpPage(event.getJDA(), guildId, page, viewerId)).queue();
            }
        }
    }
}
