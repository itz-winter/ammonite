package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Random;

/**
 * Slot machine gambling command
 */
public class SlotsCommand implements SlashCommand {

    private static final Random RANDOM = new Random();
    private static final String[] SYMBOLS = { "🍎", "🍊", "🍋", "🍇", "🍓", "⭐", "💎" };
    private static final double[] SYMBOL_WEIGHTS = { 0.25, 0.25, 0.20, 0.15, 0.10, 0.04, 0.01 }; // Probabilities

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        long betAmount = event.getOption("amount").getAsLong();

        if (betAmount <= 0) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Bet", "Bet amount must be greater than 0.")).setEphemeral(true).queue();
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            String userId = event.getUser().getId();

            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            if (currentBalance < betAmount) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Insufficient Funds",
                        "You don't have enough points to place this bet.\n" +
                                "Your balance: " + currentBalance + " points"))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            // Spin the slots
            String symbol1 = getRandomSymbol();
            String symbol2 = getRandomSymbol();
            String symbol3 = getRandomSymbol();

            String slotResult = symbol1 + " " + symbol2 + " " + symbol3;

            // Calculate winnings (net profit, not including the original bet)
            long netWinnings = calculateNetWinnings(betAmount, symbol1, symbol2, symbol3);

            if (netWinnings > 0) {
                ServerBot.getStorageManager().addBalance(guildId, userId, netWinnings);
                long newBalance = currentBalance + netWinnings;

                String winType = getWinType(symbol1, symbol2, symbol3);

                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "🎰 SLOTS - " + winType + " 🎉",
                        "```\n" +
                                "┌┐\n" +
                                "│  " + slotResult + "  │\n" +
                                "└┘\n" +
                                "```\n" +
                                "**Bet Amount:** " + betAmount + " points\n" +
                                "**Winnings:** +" + netWinnings + " points\n" +
                                "**New Balance:** " + newBalance + " points"))
                        .queue();
            } else {
                // Subtract the bet amount
                long newBalance = currentBalance - betAmount;
                ServerBot.getStorageManager().addBalance(guildId, userId, -betAmount);

                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "🎰 SLOTS - No Win",
                        "```\n" +
                                "┌┐\n" +
                                "│  " + slotResult + "  │\n" +
                                "└┘\n" +
                                "```\n" +
                                "**Lost Amount:** " + betAmount + " points\n" +
                                "**New Balance:** " + newBalance + " points"))
                        .queue();
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Slots Failed",
                    "Failed to spin slots: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private String getRandomSymbol() {
        double random = RANDOM.nextDouble();
        double cumulative = 0.0;

        for (int i = 0; i < SYMBOLS.length; i++) {
            cumulative += SYMBOL_WEIGHTS[i];
            if (random <= cumulative) {
                return SYMBOLS[i];
            }
        }

        return SYMBOLS[0]; // Fallback
    }

    /**
     * Calculate net winnings (profit only, not including the original bet back).
     * Returns 0 for no win. The bet is NOT deducted upfront — on a win, only
     * the net profit is added; on a loss, the bet is subtracted separately.
     */
    private long calculateNetWinnings(long betAmount, String s1, String s2, String s3) {
        // Three of the same (multiplier is total return, so net = multiplier - 1)
        if (s1.equals(s2) && s2.equals(s3)) {
            switch (s1) {
                case "💎" -> {
                    return betAmount * 49;
                } // Diamond jackpot (50x return - 1x bet)
                case "⭐" -> {
                    return betAmount * 19;
                } // Star (20x return - 1x bet)
                case "🍓" -> {
                    return betAmount * 9;
                } // Strawberry (10x return - 1x bet)
                case "🍇" -> {
                    return betAmount * 7;
                } // Grape (8x return - 1x bet)
                case "🍋" -> {
                    return betAmount * 5;
                } // Lemon (6x return - 1x bet)
                case "🍊" -> {
                    return betAmount * 3;
                } // Orange (4x return - 1x bet)
                case "🍎" -> {
                    return betAmount * 2;
                } // Apple (3x return - 1x bet)
            }
        }

        // Two of the same
        if (s1.equals(s2) || s1.equals(s3) || s2.equals(s3)) {
            String matchSymbol = s1.equals(s2) ? s1 : (s1.equals(s3) ? s1 : s2);
            switch (matchSymbol) {
                case "💎", "⭐" -> {
                    return betAmount;
                } // 2x return - 1x bet = 1x net
                case "🍓", "🍇" -> {
                    return (long) (betAmount * 0.5);
                } // 1.5x return - 1x bet = 0.5x net
            }
        }

        return 0; // No win
    }

    private String getWinType(String s1, String s2, String s3) {
        if (s1.equals(s2) && s2.equals(s3)) {
            if (s1.equals("💎"))
                return "DIAMOND JACKPOT!";
            if (s1.equals("⭐"))
                return "STAR TRIPLE!";
            return "TRIPLE WIN!";
        }
        return "WIN!";
    }

    public static CommandData getCommandData() {
        return Commands.slash("slots", "Play the slot machine")
                .addOptions(
                        new OptionData(OptionType.INTEGER, "amount", "Amount to bet", true)
                                .setMinValue(1)
                                .setMaxValue(10000));
    }

    @Override
    public String getName() {
        return "slots";
    }

    @Override
    public String getDescription() {
        return "Play the slot machine and try to win big!";
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
