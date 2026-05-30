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
 * Dice rolling gambling command
 */
public class DiceCommand implements SlashCommand {

    private static final Random RANDOM = new Random();

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        long betAmount = event.getOption("amount").getAsLong();
        int prediction = event.getOption("prediction").getAsInt();

        if (betAmount <= 0) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Bet", "Bet amount must be greater than 0.")).setEphemeral(true).queue();
            return;
        }

        if (prediction < 1 || prediction > 6) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Prediction", "Prediction must be between 1 and 6.")).setEphemeral(true).queue();
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
                        .setEphemeral(true).queue();
                return;
            }

            // Roll the dice
            int diceRoll = RANDOM.nextInt(6) + 1;
            boolean won = diceRoll == prediction;

            if (won) {
                long winnings = betAmount * 4; // Net profit: 5x return minus the 1x original bet
                ServerBot.getStorageManager().addBalance(guildId, userId, winnings);
                long newBalance = currentBalance + winnings;

                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "🎲 Dice Roll - YOU WON! 🎉",
                        "**Your Prediction:** " + prediction + "\n" +
                                "**Dice Result:** " + diceRoll + "\n" +
                                "**Bet Amount:** " + betAmount + " points\n" +
                                "**Winnings:** +" + winnings + " points\n" +
                                "**New Balance:** " + newBalance + " points"))
                        .queue();
            } else {
                ServerBot.getStorageManager().removeBalance(guildId, userId, betAmount);
                long newBalance = currentBalance - betAmount;

                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "🎲 Dice Roll - You Lost",
                        "**Your Prediction:** " + prediction + "\n" +
                                "**Dice Result:** " + diceRoll + "\n" +
                                "**Lost Amount:** " + betAmount + " points\n" +
                                "**New Balance:** " + newBalance + " points"))
                        .queue();
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Dice Roll Failed",
                    "Failed to process dice roll: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("dice", "Roll a dice and bet on the outcome")
                .addOptions(
                        new OptionData(OptionType.INTEGER, "amount", "Amount to bet", true)
                                .setMinValue(1)
                                .setMaxValue(10000),
                        new OptionData(OptionType.INTEGER, "prediction", "Predict the dice result (1-6)", true)
                                .setMinValue(1)
                                .setMaxValue(6));
    }

    @Override
    public String getName() {
        return "dice";
    }

    @Override
    public String getDescription() {
        return "Roll a dice and bet on the outcome (5x payout for exact match)";
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
