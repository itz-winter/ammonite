package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gambling command with various games
 */
public class GambleCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(GambleCommand.class);
    private final Random random = new Random();

    @Override
    public String getName() {
        return "gamble";
    }

    @Override
    public String getDescription() {
        return "Try your luck with various gambling games";
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

        String game = event.getOption("game").getAsString().toLowerCase();
        long points = event.getOption("points").getAsLong();
        User user = event.getUser();
        String guildId = event.getGuild().getId();

        if (points <= 0) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "You must bet at least 1 point.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (points > 10000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Bet Too High", "You cannot bet more than 10,000 coins at once.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        long userBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());

        if (userBalance < points) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You only have %,d coins but tried to bet %,d coins.", userBalance, points)))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        switch (game) {
            case "coinflip":
            case "flip":
                handleCoinFlip(event, user, points, userBalance, guildId);
                break;
            case "dice":
                handleDiceRoll(event, user, points, userBalance, guildId);
                break;
            case "slots":
                handleSlots(event, user, points, userBalance, guildId);
                break;
            case "blackjack":
                handleBlackjack(event, user, points, userBalance, guildId);
                break;
            default:
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Game",
                        "Available games: `coinflip`, `dice`, `slots`, `blackjack`")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleCoinFlip(SlashCommandInteractionEvent event, User user, long points, long balance,
            String guildId) {
        boolean userWin = random.nextBoolean();
        String result = userWin ? "Heads" : "Tails";
        String userChoice = "Heads"; // For simplicity, user always chooses heads

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(userWin ? Color.GREEN : Color.RED)
                .setTitle("Coinflip")
                .addField("Your Choice", userChoice, true)
                .addField("Result", result, true)
                .addField("Bet Amount", String.format("%,d coins", points), true);

        if (userWin) {
            long winnings = points; // 2x return (1x profit)
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance + winnings);
            embed.setDescription(String.format("**You won!** \n+%,d coins", winnings))
                    .setColor(Color.GREEN);
        } else {
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance - points);
            embed.setDescription(String.format("**You lost!** \n-%,d coins", points))
                    .setColor(Color.RED);
        }

        long newBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());
        embed.addField("New Balance", String.format("%,d coins", newBalance), false);

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleDiceRoll(SlashCommandInteractionEvent event, User user, long points, long balance,
            String guildId) {
        int roll = random.nextInt(6) + 1;
        boolean won = roll >= 4; // Win on 4, 5, or 6

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(won ? Color.GREEN : Color.RED)
                .setTitle("Dice Roll")
                .addField("Your Roll", String.valueOf(roll), true)
                .addField("Win Condition", "Roll 4, 5, or 6", true)
                .addField("Bet Amount", String.format("%,d coins", points), true);

        if (won) {
            long winnings = points; // 2x return
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance + winnings);
            embed.setDescription(String.format("**You won!** \n+%,d coins", winnings));
        } else {
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance - points);
            embed.setDescription(String.format("**You lost!** \n-%,d coins", points));
        }

        long newBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());
        embed.addField("New Balance", String.format("%,d coins", newBalance), false);

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleSlots(SlashCommandInteractionEvent event, User user, long points, long balance, String guildId) {
        String[] symbols = { "🍒", "🍋", "🍊", "🍇", "🔔", "💎" };
        String[] results = new String[3];

        for (int i = 0; i < 3; i++) {
            results[i] = symbols[random.nextInt(symbols.length)];
        }

        String slotsDisplay = String.join(" | ", results);
        boolean won = false;
        long multiplier = 0;

        // Check for wins
        if (results[0].equals(results[1]) && results[1].equals(results[2])) {
            // All three match
            won = true;
            if (results[0].equals("💎")) {
                multiplier = 10; // Diamond jackpot
            } else if (results[0].equals("🔔")) {
                multiplier = 5; // Bell
            } else {
                multiplier = 3; // Other matches
            }
        } else if (results[0].equals(results[1]) || results[1].equals(results[2]) || results[0].equals(results[2])) {
            // Two match
            won = true;
            multiplier = 1; // 2x return (1x profit)
        }

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(won ? Color.GREEN : Color.RED)
                .setTitle("Slots")
                .addField("Result", slotsDisplay, false)
                .addField("Bet Amount", String.format("%,d coins", points), true);

        if (won) {
            long winnings = points * multiplier;
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance + winnings);
            embed.setDescription(String.format("**You won!** (%dx multiplier)\n+%,d coins", multiplier, winnings));
        } else {
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance - points);
            embed.setDescription(String.format("**You lost!** \n-%,d coins", points));
        }

        long newBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());
        embed.addField("New Balance", String.format("%,d coins", newBalance), false);

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleBlackjack(SlashCommandInteractionEvent event, User user, long points, long balance,
            String guildId) {
        // Check if the user already has an active blackjack game
        if (BlackjackGame.hasActiveGame(user.getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game In Progress",
                    "You already have an active blackjack game! Finish it before starting a new one."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Deduct the bet upfront
        ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance - points);

        // Create a new blackjack game
        BlackjackGame game = new BlackjackGame(user.getId(), guildId, points, balance);

        // Deal initial cards
        game.dealInitialCards();

        int playerTotal = game.getPlayerTotal();
        int dealerShowing = game.getDealerCards().get(0);

        // Check for natural blackjack
        if (playerTotal == 21) {
            // Natural blackjack - instant win at 3:2
            long winnings = (long) (points * 2.5); // Original bet + 1.5x profit
            ServerBot.getStorageManager().setBalance(guildId, user.getId(), balance - points + winnings);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, user.getId());

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(Color.GREEN)
                    .setTitle("🃏 Blackjack - NATURAL BLACKJACK! 🎉")
                    .setDescription("**BLACKJACK!** You hit 21! 3:2 payout!")
                    .addField("Your Hand (" + playerTotal + ")", game.formatPlayerCards(), true)
                    .addField("Dealer Hand (" + game.getDealerTotal() + ")", game.formatDealerCards(false), true)
                    .addField("Bet Amount", String.format("%,d coins", points), true)
                    .addField("Winnings", String.format("+%,d coins", winnings - points), true)
                    .addField("New Balance", String.format("%,d coins", newBalance), false);

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
        return Commands.slash("gamble", "Try your luck with various gambling games")
                .addOptions(
                        new OptionData(OptionType.STRING, "game", "The game to play", true)
                                .addChoices(
                                        new Command.Choice("Coin Flip", "coinflip"),
                                        new Command.Choice("Dice Roll", "dice"),
                                        new Command.Choice("Slot Machine", "slots"),
                                        new Command.Choice("Blackjack", "blackjack")))
                .addOption(OptionType.INTEGER, "points", "Amount of coins to bet", true);
    }
}
