package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * Balance top command to show richest users
 */
public class BaltopCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(BaltopCommand.class);

    @Override
    public String getName() {
        return "baltop";
    }

    @Override
    public String getDescription() {
        return "Shows the server's richest users";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        try {
            String guildId = event.getGuild().getId();

            // Get top 10 users from file storage
            List<Map.Entry<String, Long>> sortedUsers = ServerBot.getStorageManager().getTopBalances(guildId, 10);

            if (sortedUsers.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createInfoEmbed(
                        "No Economy Data",
                        "No users have any balance yet. Use `/balance` to get started!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            // Build leaderboard embed
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(Color.GREEN) // Green color
                    .setTitle("Leaderboard")
                    .setDescription("Top " + Math.min(sortedUsers.size(), 10) + " richest users in the server:");

            StringBuilder leaderboard = new StringBuilder();
            for (int i = 0; i < sortedUsers.size(); i++) {
                Map.Entry<String, Long> entry = sortedUsers.get(i);
                String userId = entry.getKey();
                Long balance = entry.getValue();

                String medal = getMedal(i + 1);
                leaderboard.append(String.format("%s **#%d** <@%s> - **%,d** coins\n",
                        medal, i + 1, userId, balance));
            }

            embed.setDescription(leaderboard.toString());

            // Show user's position if not in top 10
            String currentUserId = event.getUser().getId();
            long currentUserBalance = ServerBot.getStorageManager().getBalance(guildId, currentUserId);

            // Check if current user is in top 10
            boolean userInTop10 = sortedUsers.stream()
                    .anyMatch(entry -> entry.getKey().equals(currentUserId));

            if (!userInTop10 && currentUserBalance > 0) {
                // Calculate user's position by getting all users and finding position
                List<Map.Entry<String, Long>> allUsers = ServerBot.getStorageManager().getTopBalances(guildId,
                        Integer.MAX_VALUE);

                int userPosition = 1;
                for (Map.Entry<String, Long> entry : allUsers) {
                    if (entry.getKey().equals(currentUserId)) {
                        break;
                    }
                    userPosition++;
                }

                embed.addField("Your Position",
                        String.format("#%d with %,d coins", userPosition, currentUserBalance), false);
            }

            event.replyEmbeds(embed.build()).queue();

            logger.info("Displayed balance leaderboard for guild {}", guildId);

        } catch (Exception e) {
            logger.error("Error executing baltop command", e);
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error",
                    "An error occurred while retrieving the leaderboard.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private String getMedal(int position) {
        switch (position) {
            case 1:
                return "🥇";
            case 2:
                return "🥈";
            case 3:
                return "🥉";
            default:
                return "🏅";
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("baltop", "Shows top balances in the server");
    }
}
