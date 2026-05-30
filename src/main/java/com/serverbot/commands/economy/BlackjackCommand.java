package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;

/**
 * Standalone /blackjack command — mirrors the blackjack sub-game from /gamble
 * but as its own top-level slash command for quick access.
 * Uses the same BlackjackGame state + BlackjackButtonListener for interactions.
 */
public class BlackjackCommand implements SlashCommand {

    @Override
    public String getName() {
        return "blackjack";
    }

    @Override
    public String getDescription() {
        return "Play a game of blackjack";
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

        User user = event.getUser();
        String guildId = event.getGuild().getId();
        long points = event.getOption("bet").getAsLong();

        if (points <= 0) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "You must bet at least 1 coin.")).setEphemeral(true).queue();
            return;
        }

        if (points > 10000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Bet Too High", "You cannot bet more than 10,000 coins at once.")).setEphemeral(true).queue();
            return;
        }

        long userBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());

        if (userBalance < points) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You only have %,d coins but tried to bet %,d coins.", userBalance, points)))
                    .setEphemeral(true).queue();
            return;
        }

        // Check if the user already has an active blackjack game
        if (BlackjackGame.hasActiveGame(user.getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game In Progress",
                    "You already have an active blackjack game! Finish it before starting a new one."))
                    .setEphemeral(true).queue();
            return;
        }

        // Deduct the bet upfront
        ServerBot.getStorageManager().setBalance(guildId, user.getId(), userBalance - points);

        // Create a new blackjack game
        BlackjackGame game = new BlackjackGame(user.getId(), guildId, points, userBalance);

        // Deal initial cards
        game.dealInitialCards();

        int playerTotal = game.getPlayerTotal();
        int dealerShowing = game.getDealerCards().get(0);

        // Check for natural blackjack
        if (playerTotal == 21) {
            long winnings = (long) (points * 2.5);
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), userBalance - points + winnings);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(Color.GREEN)
                    .setTitle("🃏 Blackjack - NATURAL BLACKJACK! 🎉")
                    .setDescription("**BLACKJACK!** You hit 21! 3:2 payout!")
                    .addField("Your Hand (" + playerTotal + ")", game.formatPlayerCards(), true)
                    .addField("Dealer Hand (" + game.getDealerTotal() + ")", game.formatDealerCards(false), true)
                    .addField("Bet Amount", String.format("%,d coins", points), true)
                    .addField("Winnings", String.format("+%,d coins", winnings - points), true)
                    .addField("New Balance", String.format("%,d coins", newBalance), false);

            game.removeGame();
            event.replyEmbeds(embed.build()).queue();
            return;
        }

        // Show the game state with buttons
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(Color.BLUE)
                .setTitle("🃏 Blackjack")
                .setDescription("Choose your action!")
                .addField("Your Hand (" + playerTotal + ")", game.formatPlayerCards(), true)
                .addField("Dealer Shows", game.formatSingleCard(dealerShowing) + " + ?", true)
                .addField("Bet Amount", String.format("%,d coins", points), true);

        // Only allow double down on first two cards and if player has enough balance
        long currentBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());
        boolean canDoubleDown = game.getPlayerCards().size() == 2 && currentBalance >= points;

        String gameId = game.getGameId();

        Button hitButton = Button.primary("bj_hit_" + gameId, "Hit 🃏");
        Button standButton = Button.success("bj_stand_" + gameId, "Stand ✋");
        Button doubleButton = Button.danger("bj_double_" + gameId, "Double Down 💰");

        if (canDoubleDown) {
            event.replyEmbeds(embed.build())
                    .addComponents(ActionRow.of(hitButton, standButton, doubleButton))
                    .queue();
        } else {
            event.replyEmbeds(embed.build())
                    .addComponents(ActionRow.of(hitButton, standButton))
                    .queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("blackjack", "Play a game of blackjack")
                .addOption(OptionType.INTEGER, "bet", "Amount of coins to bet", true);
    }
}
