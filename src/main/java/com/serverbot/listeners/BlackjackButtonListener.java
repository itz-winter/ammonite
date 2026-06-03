package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.commands.economy.BlackjackGame;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;

/**
 * Handles button interactions for the interactive blackjack game.
 */
public class BlackjackButtonListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(BlackjackButtonListener.class);

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        if (!buttonId.startsWith("bj_")) {
            return; // Not a blackjack button
        }

        // Parse button: bj_<action>_<gameId>
        String[] parts = buttonId.split("_", 3);
        if (parts.length < 3)
            return;

        String action = parts[1];
        String gameId = parts[2];

        BlackjackGame game = BlackjackGame.getGame(gameId);
        if (game == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game Expired", "This blackjack game has expired. Start a new one with `/gamble game:blackjack`."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Only the game owner can interact
        if (!event.getUser().getId().equals(game.getUserId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Your Game", "Only the person who started this game can play it.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        switch (action) {
            case "hit" -> handleHit(event, game);
            case "stand" -> handleStand(event, game);
            case "double" -> handleDoubleDown(event, game);
            default -> event.deferEdit().queue();
        }
    }

    private void handleHit(ButtonInteractionEvent event, BlackjackGame game) {
        game.playerHit();
        int playerTotal = game.getPlayerTotal();

        if (playerTotal > 21) {
            // Player busted
            finishGame(event, game, false, "💥 **BUST!** You went over 21!");
        } else if (playerTotal == 21) {
            // Auto-stand on 21
            handleStand(event, game);
        } else {
            // Show updated hand with buttons
            int dealerShowing = game.getDealerCards().get(0);

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(Color.BLUE)
                    .setTitle("🃏 Blackjack")
                    .setDescription("Choose your action!")
                    .addField("Your Hand (" + playerTotal + ")", game.formatPlayerCards(), true)
                    .addField("Dealer Shows", game.formatSingleCard(dealerShowing) + " + ?", true)
                    .addField("Bet Amount", String.format("%,d coins", game.getEffectiveBet()), true);

            Button hitButton = Button.primary("bj_hit_" + game.getGameId(), "Hit 🃏");
            Button standButton = Button.success("bj_stand_" + game.getGameId(), "Stand ✋");

            event.editMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(hitButton, standButton))
                    .queue();
        }
    }

    private void handleStand(ButtonInteractionEvent event, BlackjackGame game) {
        // Dealer plays
        game.dealerPlay();

        int playerTotal = game.getPlayerTotal();
        int dealerTotal = game.getDealerTotal();

        if (dealerTotal > 21) {
            finishGame(event, game, true, "🎉 **Dealer busted!** You win!");
        } else if (playerTotal > dealerTotal) {
            finishGame(event, game, true, "🎉 **You win!** Your hand beats the dealer!");
        } else if (dealerTotal > playerTotal) {
            finishGame(event, game, false, "😞 **You lose!** Dealer's hand beats yours.");
        } else {
            // Push (tie) - return bet
            finishGame(event, game, null, "🤝 **Push!** It's a tie. Your bet has been returned.");
        }
    }

    private void handleDoubleDown(ButtonInteractionEvent event, BlackjackGame game) {
        String guildId = game.getGuildId();
        String userId = game.getUserId();
        long betAmount = game.getBetAmount();

        // Check if player can afford to double
        long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
        if (currentBalance < betAmount) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You need %,d more coins to double down.", betAmount - currentBalance)))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Deduct additional bet
        ServerBot.getStorageManager().setBalance(guildId, userId, currentBalance - betAmount);
        game.setDoubledDown(true);

        // Draw exactly one more card then stand
        game.playerHit();
        int playerTotal = game.getPlayerTotal();

        if (playerTotal > 21) {
            finishGame(event, game, false, "💥 **BUST!** You went over 21! (Double Down)");
        } else {
            // Auto-stand after double down
            game.dealerPlay();
            int dealerTotal = game.getDealerTotal();

            if (dealerTotal > 21) {
                finishGame(event, game, true, "🎉 **Dealer busted!** You win! (Double Down)");
            } else if (playerTotal > dealerTotal) {
                finishGame(event, game, true, "🎉 **You win!** (Double Down)");
            } else if (dealerTotal > playerTotal) {
                finishGame(event, game, false, "😞 **You lose!** (Double Down)");
            } else {
                finishGame(event, game, null, "🤝 **Push!** It's a tie. Your bet has been returned. (Double Down)");
            }
        }
    }

    /**
     * Finish the game and settle the bet.
     * 
     * @param won true=win, false=loss, null=push(tie)
     */
    private void finishGame(ButtonInteractionEvent event, BlackjackGame game, Boolean won, String resultText) {
        String guildId = game.getGuildId();
        String userId = game.getUserId();
        long effectiveBet = game.getEffectiveBet();

        Color color;
        if (won == null) {
            // Push - return the bet
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
            ServerBot.getStorageManager().setBalance(guildId, userId, currentBalance + effectiveBet);
            color = Color.YELLOW;
        } else if (won) {
            // Win - return bet + winnings (2x bet total)
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
            ServerBot.getStorageManager().setBalance(guildId, userId, currentBalance + effectiveBet * 2);
            color = Color.GREEN;
        } else {
            // Loss - bet was already deducted
            color = Color.RED;
        }

        long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(color)
                .setTitle("🃏 Blackjack - Game Over")
                .setDescription(resultText)
                .addField("Your Hand (" + game.getPlayerTotal() + ")", game.formatPlayerCards(), true)
                .addField("Dealer Hand (" + game.getDealerTotal() + ")", game.formatDealerCards(false), true)
                .addField("Bet Amount", String.format("%,d coins", effectiveBet), true);

        if (won != null && won) {
            embed.addField("Winnings", String.format("+%,d coins", effectiveBet), true);
        } else if (won != null) {
            embed.addField("Lost", String.format("%,d coins", effectiveBet), true);
        } else {
            embed.addField("Returned", String.format("%,d coins", effectiveBet), true);
        }

        embed.addField("New Balance", String.format("%,d coins", newBalance), false);

        // Remove buttons and update message
        event.editMessageEmbeds(embed.build())
                .setComponents() // Remove all buttons
                .queue();

        // Clean up game state
        game.removeGame();
    }
}
