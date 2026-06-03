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
 * Coin flip gambling command
 */
public class FlipCommand implements SlashCommand {

    private static final Random RANDOM = new Random();

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        long betAmount = event.getOption("amount").getAsLong();
        String prediction = event.getOption("side").getAsString().toLowerCase();

        if (betAmount <= 0) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Bet", "Bet amount must be greater than 0.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
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

            // Flip the coin
            String result = RANDOM.nextBoolean() ? "heads" : "tails";
            boolean won = result.equals(prediction);

            String resultEmoji = result.equals("heads") ? "🪙" : "⚪";
            String predictionEmoji = prediction.equals("heads") ? "🪙" : "⚪";

            if (won) {
                long winnings = betAmount; // Net profit is 1x the bet (2x return minus the original bet)
                ServerBot.getStorageManager().addBalance(guildId, userId, winnings);
                long newBalance = currentBalance + winnings;

                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "🪙 Coin Flip - YOU WON! 🎉",
                        "**Your Prediction:** " + predictionEmoji + " " + prediction.toUpperCase() + "\n" +
                                "**Coin Result:** " + resultEmoji + " " + result.toUpperCase() + "\n" +
                                "**Bet Amount:** " + betAmount + " points\n" +
                                "**Winnings:** +" + winnings + " points\n" +
                                "**New Balance:** " + newBalance + " points"))
                        .queue();
            } else {
                // Subtract the bet amount
                long newBalance = currentBalance - betAmount;
                ServerBot.getStorageManager().addBalance(guildId, userId, -betAmount);

                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "🪙 Coin Flip - You Lost",
                        "**Your Prediction:** " + predictionEmoji + " " + prediction.toUpperCase() + "\n" +
                                "**Coin Result:** " + resultEmoji + " " + result.toUpperCase() + "\n" +
                                "**Lost Amount:** " + betAmount + " points\n" +
                                "**New Balance:** " + newBalance + " points"))
                        .queue();
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Coin Flip Failed",
                    "Failed to process coin flip: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("flip", "Flip a coin and bet on heads or tails")
                .addOptions(
                        new OptionData(OptionType.INTEGER, "amount", "Amount to bet", true)
                                .setMinValue(1)
                                .setMaxValue(10000),
                        new OptionData(OptionType.STRING, "side", "Choose heads or tails", true)
                                .addChoice("Heads 🪙", "heads")
                                .addChoice("Tails ⚪", "tails"));
    }

    @Override
    public String getName() {
        return "flip";
    }

    @Override
    public String getDescription() {
        return "Flip a coin and bet on heads or tails (2x payout)";
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
