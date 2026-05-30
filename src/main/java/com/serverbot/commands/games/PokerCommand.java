package com.serverbot.commands.games;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Interactive Poker game command
 */
public class PokerCommand extends ListenerAdapter implements SlashCommand {

    private static final Map<String, PokerGame> activeGames = new ConcurrentHashMap<>();
    private static final Random random = new Random();

    // Card representations
    private static final String[] SUITS = { "♠", "♥", "♦", "♣" };
    private static final String[] RANKS = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getUser().getId();
        Long betAmount = event.getOption("bet") != null ? event.getOption("bet").getAsLong() : 50L;

        // Check if user has enough balance
        long userBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
        if (userBalance < betAmount) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    "You need at least " + betAmount + " points to play poker.\n" +
                            "Your current balance: " + userBalance + " points"))
                    .setEphemeral(true).queue();
            return;
        }

        // Check if user is already in a game
        if (activeGames.containsKey(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game in Progress",
                    "You're already playing poker! Finish your current game first.")).setEphemeral(true).queue();
            return;
        }

        // Start new poker game
        PokerGame game = new PokerGame(userId, guildId, betAmount);
        activeGames.put(userId, game);

        // Deduct bet from balance
        ServerBot.getStorageManager().setBalance(guildId, userId, userBalance - betAmount);

        // Create initial embed with hand
        EmbedBuilder embed = createGameEmbed(game);

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(
                        Button.primary("poker_hold", "Hold Current Hand"),
                        Button.secondary("poker_discard", "Discard & Draw"),
                        Button.danger("poker_fold", "Fold")))
                .queue();

        // Schedule game cleanup after 5 minutes
        scheduleGameCleanup(userId);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("poker_")) {
            return;
        }

        String userId = event.getUser().getId();
        PokerGame game = activeGames.get(userId);

        if (game == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game Not Found", "Your poker game has expired or doesn't exist.")).setEphemeral(true).queue();
            return;
        }

        if (!game.userId.equals(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Your Game", "This isn't your poker game!")).setEphemeral(true).queue();
            return;
        }

        switch (event.getComponentId()) {
            case "poker_hold" -> handleHold(event, game);
            case "poker_discard" -> handleDiscard(event, game);
            case "poker_fold" -> handleFold(event, game);
        }
    }

    private void handleHold(ButtonInteractionEvent event, PokerGame game) {
        // Evaluate final hand
        String handType = evaluateHand(game.hand);
        long payout = calculatePayout(handType, game.betAmount);

        // Update balance
        if (payout > 0) {
            long currentBalance = ServerBot.getStorageManager().getBalance(game.guildId, game.userId);
            ServerBot.getStorageManager().setBalance(game.guildId, game.userId, currentBalance + payout);
        }

        EmbedBuilder embed = createFinalEmbed(game, handType, payout);

        event.editMessageEmbeds(embed.build())
                .setComponents() // Remove buttons
                .queue();

        activeGames.remove(game.userId);
    }

    private void handleDiscard(ButtonInteractionEvent event, PokerGame game) {
        if (game.hasDiscarded) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Already Discarded", "You can only discard once per game!")).setEphemeral(true).queue();
            return;
        }

        // For simplicity, discard 2 random cards and draw 2 new ones
        for (int i = 0; i < 2; i++) {
            int indexToReplace = random.nextInt(game.hand.size());
            game.hand.set(indexToReplace, drawCard());
        }

        game.hasDiscarded = true;

        EmbedBuilder embed = createGameEmbed(game);
        embed.setDescription("📝 **Cards discarded and redrawn!**\n" +
                "You can now **Hold** or **Fold** your final hand.");

        event.editMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(
                        Button.primary("poker_hold", "Hold Current Hand"),
                        Button.danger("poker_fold", "Fold")))
                .queue();
    }

    private void handleFold(ButtonInteractionEvent event, PokerGame game) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🃏 Poker - Folded")
                .setDescription("You folded your hand and lost your bet of " + game.betAmount + " points.")
                .setColor(Color.RED)
                .addField("Your Hand", formatHand(game.hand), false);

        event.editMessageEmbeds(embed.build())
                .setComponents() // Remove buttons
                .queue();

        activeGames.remove(game.userId);
    }

    private EmbedBuilder createGameEmbed(PokerGame game) {
        return new EmbedBuilder()
                .setTitle("🃏 Five Card Draw Poker")
                .setDescription("**Your Hand:**\nDecide whether to hold, discard some cards, or fold!")
                .setColor(Color.BLUE)
                .addField("Cards", formatHand(game.hand), false)
                .addField("Bet Amount", game.betAmount + " points", true)
                .addField("Actions", game.hasDiscarded ? "Hold or Fold only" : "Hold, Discard, or Fold", true)
                .setFooter("Game will expire in 5 minutes", null);
    }

    private EmbedBuilder createFinalEmbed(PokerGame game, String handType, long payout) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🃏 Poker - Final Result")
                .addField("Your Hand", formatHand(game.hand), false)
                .addField("Hand Type", handType, true)
                .addField("Bet", game.betAmount + " points", true);

        if (payout > 0) {
            embed.setDescription("🎉 **You won!**")
                    .setColor(Color.GREEN)
                    .addField("Payout", "+" + payout + " points", true);
        } else {
            embed.setDescription("😢 **You lost!**")
                    .setColor(Color.RED)
                    .addField("Loss", "-" + game.betAmount + " points", true);
        }

        long newBalance = ServerBot.getStorageManager().getBalance(game.guildId, game.userId);
        embed.addField("New Balance", newBalance + " points", false);

        return embed;
    }

    private String formatHand(List<Card> hand) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hand.size(); i++) {
            if (i > 0)
                sb.append(" ");
            sb.append(hand.get(i).toString());
        }
        return sb.toString();
    }

    private Card drawCard() {
        String suit = SUITS[random.nextInt(SUITS.length)];
        String rank = RANKS[random.nextInt(RANKS.length)];
        return new Card(suit, rank);
    }

    private List<Card> drawInitialHand() {
        List<Card> hand = new ArrayList<>();
        Set<String> drawn = new HashSet<>();

        while (hand.size() < 5) {
            Card card = drawCard();
            String cardStr = card.toString();
            if (!drawn.contains(cardStr)) {
                hand.add(card);
                drawn.add(cardStr);
            }
        }

        return hand;
    }

    private String evaluateHand(List<Card> hand) {
        // Simple hand evaluation - just check for pairs, three of a kind, etc.
        Map<String, Integer> rankCounts = new HashMap<>();
        Map<String, Integer> suitCounts = new HashMap<>();

        for (Card card : hand) {
            rankCounts.merge(card.rank, 1, Integer::sum);
            suitCounts.merge(card.suit, 1, Integer::sum);
        }

        boolean isFlush = suitCounts.values().stream().anyMatch(count -> count >= 5);
        boolean isStraight = checkStraight(hand);

        int maxRankCount = rankCounts.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        long pairCount = rankCounts.values().stream().mapToLong(count -> count >= 2 ? 1 : 0).sum();

        if (isFlush && isStraight)
            return "Straight Flush";
        if (maxRankCount == 4)
            return "Four of a Kind";
        if (maxRankCount == 3 && pairCount == 2)
            return "Full House";
        if (isFlush)
            return "Flush";
        if (isStraight)
            return "Straight";
        if (maxRankCount == 3)
            return "Three of a Kind";
        if (pairCount == 2)
            return "Two Pair";
        if (pairCount == 1)
            return "One Pair";
        return "High Card";
    }

    private boolean checkStraight(List<Card> hand) {
        // Simplified straight check
        List<Integer> values = new ArrayList<>();
        for (Card card : hand) {
            switch (card.rank) {
                case "A" -> values.add(14);
                case "K" -> values.add(13);
                case "Q" -> values.add(12);
                case "J" -> values.add(11);
                default -> values.add(Integer.parseInt(card.rank));
            }
        }
        Collections.sort(values);

        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) != values.get(i - 1) + 1) {
                return false;
            }
        }
        return true;
    }

    private long calculatePayout(String handType, long betAmount) {
        return switch (handType) {
            case "Straight Flush" -> betAmount * 50;
            case "Four of a Kind" -> betAmount * 25;
            case "Full House" -> betAmount * 9;
            case "Flush" -> betAmount * 6;
            case "Straight" -> betAmount * 4;
            case "Three of a Kind" -> betAmount * 3;
            case "Two Pair" -> betAmount * 2;
            case "One Pair" -> betAmount; // Return bet
            default -> 0; // Lose bet
        };
    }

    private void scheduleGameCleanup(String userId) {
        // Use CompletableFuture.delayedExecutor instead of Timer to avoid daemon
        // threads
        CompletableFuture.delayedExecutor(5, TimeUnit.MINUTES)
                .execute(() -> activeGames.remove(userId));
    }

    public static CommandData getCommandData() {
        return Commands.slash("poker", "Play five-card draw poker")
                .addOption(OptionType.INTEGER, "bet", "Amount to bet (default: 50)", false);
    }

    @Override
    public String getName() {
        return "poker";
    }

    @Override
    public String getDescription() {
        return "Play interactive five-card draw poker";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GAMES;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    // Inner classes
    private static class PokerGame {
        final String userId;
        final String guildId;
        final long betAmount;
        final List<Card> hand;
        boolean hasDiscarded = false;

        PokerGame(String userId, String guildId, long betAmount) {
            this.userId = userId;
            this.guildId = guildId;
            this.betAmount = betAmount;
            this.hand = new PokerCommand().drawInitialHand();
        }
    }

    private static class Card {
        final String suit;
        final String rank;

        Card(String suit, String rank) {
            this.suit = suit;
            this.rank = rank;
        }

        @Override
        public String toString() {
            return rank + suit;
        }
    }
}
