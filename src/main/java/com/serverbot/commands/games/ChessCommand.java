package com.serverbot.commands.games;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * Interactive Chess game command
 */
public class ChessCommand extends ListenerAdapter implements SlashCommand {

    private static final Map<String, ChessGame> activeGames = new ConcurrentHashMap<>();

    // Chess piece Unicode symbols
    private static final String[][] INITIAL_BOARD = {
            { "♜", "♞", "♝", "♛", "♚", "♝", "♞", "♜" },
            { "♟", "♟", "♟", "♟", "♟", "♟", "♟", "♟" },
            { "⬜", "⬛", "⬜", "⬛", "⬜", "⬛", "⬜", "⬛" },
            { "⬛", "⬜", "⬛", "⬜", "⬛", "⬜", "⬛", "⬜" },
            { "⬜", "⬛", "⬜", "⬛", "⬜", "⬛", "⬜", "⬛" },
            { "⬛", "⬜", "⬛", "⬜", "⬛", "⬜", "⬛", "⬜" },
            { "♙", "♙", "♙", "♙", "♙", "♙", "♙", "♙" },
            { "♖", "♘", "♗", "♕", "♔", "♗", "♘", "♖" }
    };

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "chess.use")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You don't have permission to play chess!")).setEphemeral(true).queue();
            return;
        }

        User player1 = event.getUser();
        User opponent = event.getOption("opponent") != null ? event.getOption("opponent").getAsUser() : null;

        // If no opponent specified, play against the bot
        boolean vsBot = false;
        if (opponent == null || opponent.getId().equals(event.getJDA().getSelfUser().getId())) {
            vsBot = true;
            opponent = event.getJDA().getSelfUser();
        }

        if (!vsBot && opponent.getId().equals(player1.getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Opponent", "You can't play chess against yourself!")).setEphemeral(true).queue();
            return;
        }

        if (!vsBot && opponent.isBot()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Opponent",
                    "You can't play chess against other bots! Use `/chess` with no opponent to play against me."))
                    .setEphemeral(true).queue();
            return;
        }

        if (!vsBot) {
            Member opponentMember = event.getGuild().getMember(opponent);
            if (opponentMember == null || !PermissionManager.hasPermission(opponentMember, "chess.use")) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Opponent", "Your opponent doesn't have permission to play chess!")).setEphemeral(true)
                        .queue();
                return;
            }
        }

        // Check if either player is already in a game
        String gameId1 = "chess_" + player1.getId();
        String gameId2 = vsBot ? null : "chess_" + opponent.getId();

        if (activeGames.containsKey(gameId1) || (!vsBot && activeGames.containsKey(gameId2))) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game in Progress",
                    "One of the players is already in a chess game!")).setEphemeral(true).queue();
            return;
        }

        // Create new chess game
        ChessGame game = new ChessGame(player1.getId(), opponent.getId(), event.getGuild().getId(), vsBot);
        activeGames.put(gameId1, game);
        if (!vsBot) {
            activeGames.put(gameId2, game);
        }

        // Create initial game embed
        EmbedBuilder embed = createGameEmbed(game, player1, opponent);

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(
                        Button.primary("chess_move", "Make Move"),
                        Button.secondary("chess_resign", "Resign"),
                        Button.danger("chess_draw", "Offer Draw")))
                .queue();

        // Schedule game cleanup after 30 minutes
        scheduleGameCleanup(gameId1, vsBot ? null : gameId2);
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("chess_")) {
            return;
        }

        String userId = event.getUser().getId();
        String gameId = "chess_" + userId;
        ChessGame game = activeGames.get(gameId);

        if (game == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Game Not Found", "Your chess game has expired or doesn't exist.")).setEphemeral(true).queue();
            return;
        }

        // In bot games, only the human player interacts
        if (game.vsBot) {
            if (!userId.equals(game.whitePlayerId)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Not Your Game", "This isn't your chess game!")).setEphemeral(true).queue();
                return;
            }
        } else if (!game.isPlayerTurn(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Your Turn", "Please wait for your opponent to make their move!")).setEphemeral(true).queue();
            return;
        }

        switch (event.getComponentId()) {
            case "chess_move" -> handleMove(event, game);
            case "chess_resign" -> handleResign(event, game);
            case "chess_draw" -> handleDrawOffer(event, game);
        }
    }

    private void handleMove(ButtonInteractionEvent event, ChessGame game) {
        // Make the player's move (simplified random simulation)
        Random random = new Random();
        boolean validMove = makeRandomMove(game, random, game.isWhiteTurn);

        if (!validMove) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "No Valid Moves", "Unable to find a valid move. Try again!")).setEphemeral(true).queue();
            return;
        }

        User currentPlayer = event.getJDA().getUserById(game.whitePlayerId);
        User opponent = event.getJDA().getUserById(game.blackPlayerId);

        if (game.isGameOver()) {
            EmbedBuilder embed = createGameEmbed(game, currentPlayer, opponent);
            embed.setDescription("🏁 **Game Over!**\n" + game.getGameResult());
            embed.setColor(Color.GREEN);

            event.editMessageEmbeds(embed.build())
                    .setComponents()
                    .queue();

            activeGames.remove("chess_" + game.whitePlayerId);
            if (!game.vsBot) {
                activeGames.remove("chess_" + game.blackPlayerId);
            }
            return;
        }

        game.switchTurns();

        // If playing against the bot, make the bot's move automatically
        if (game.vsBot && !game.isWhiteTurn) {
            boolean botMoved = makeRandomMove(game, random, false);
            if (botMoved) {
                game.switchTurns();
            }

            if (game.isGameOver()) {
                EmbedBuilder embed = createGameEmbed(game, currentPlayer, opponent);
                embed.setDescription("🏁 **Game Over!**\n" + game.getGameResult());
                embed.setColor(Color.GREEN);

                event.editMessageEmbeds(embed.build())
                        .setComponents()
                        .queue();

                activeGames.remove("chess_" + game.whitePlayerId);
                return;
            }
        }

        EmbedBuilder embed = createGameEmbed(game, currentPlayer, opponent);
        embed.setFooter("Turn: " + (game.isWhiteTurn ? "White" : "Black") +
                " | Move " + game.moveCount + " | Game will expire in 30 minutes", null);

        event.editMessageEmbeds(embed.build()).queue();
    }

    /**
     * Attempt a random valid move for the given side.
     * White pieces: ♙♖♘♗♕♔ Black pieces: ♟♜♞♝♛♚
     */
    private boolean makeRandomMove(ChessGame game, Random random, boolean isWhite) {
        // Identify which pieces belong to this side
        Set<String> friendlyPieces = isWhite
                ? Set.of("♙", "♖", "♘", "♗", "♕", "♔")
                : Set.of("♟", "♜", "♞", "♝", "♛", "♚");

        // Collect all friendly piece positions
        List<int[]> piecePositions = new ArrayList<>();
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                if (friendlyPieces.contains(game.board[r][c])) {
                    piecePositions.add(new int[] { r, c });
                }
            }
        }

        // Shuffle and try moves
        Collections.shuffle(piecePositions, random);
        for (int[] pos : piecePositions) {
            int fromRow = pos[0], fromCol = pos[1];
            // Try random target squares
            List<int[]> targets = new ArrayList<>();
            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    if (r == fromRow && c == fromCol)
                        continue;
                    String target = game.board[r][c];
                    // Can move to empty squares or capture enemy pieces
                    if (!friendlyPieces.contains(target)) {
                        if (Math.abs(fromRow - r) <= 2 && Math.abs(fromCol - c) <= 2) {
                            targets.add(new int[] { r, c });
                        }
                    }
                }
            }
            if (!targets.isEmpty()) {
                Collections.shuffle(targets, random);
                int[] to = targets.get(0);
                String piece = game.board[fromRow][fromCol];
                game.board[fromRow][fromCol] = (fromRow + fromCol) % 2 == 0 ? "⬜" : "⬛";
                game.board[to[0]][to[1]] = piece;
                game.moveCount++;
                return true;
            }
        }
        return false;
    }

    private void handleResign(ButtonInteractionEvent event, ChessGame game) {
        String resigningPlayer = event.getUser().getId();
        String winner = resigningPlayer.equals(game.whitePlayerId) ? game.blackPlayerId : game.whitePlayerId;

        User winnerUser = event.getJDA().getUserById(winner);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("♔ Chess - Game Resigned")
                .setDescription("🏳️ " + event.getUser().getAsMention() + " resigned!\n" +
                        "🏆 " + (winnerUser != null ? winnerUser.getAsMention() : "Opponent") + " wins!")
                .setColor(Color.RED)
                .addField("Game Duration", game.getGameDuration(), true);

        event.editMessageEmbeds(embed.build())
                .setComponents() // Remove buttons
                .queue();

        // Clean up game
        activeGames.remove("chess_" + game.whitePlayerId);
        if (!game.vsBot) {
            activeGames.remove("chess_" + game.blackPlayerId);
        }
    }

    private void handleDrawOffer(ButtonInteractionEvent event, ChessGame game) {
        // In a full implementation, this would offer a draw to the opponent
        // For bot games, auto-accept. For PvP, accept immediately (simplified).

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("♔ Chess - Draw Agreed")
                .setDescription(
                        "🤝 " + (game.vsBot ? "You and the bot agreed to a draw!" : "Both players agreed to a draw!"))
                .setColor(Color.YELLOW)
                .addField("Game Duration", game.getGameDuration(), true);

        event.editMessageEmbeds(embed.build())
                .setComponents() // Remove buttons
                .queue();

        // Clean up game
        activeGames.remove("chess_" + game.whitePlayerId);
        if (!game.vsBot) {
            activeGames.remove("chess_" + game.blackPlayerId);
        }
    }

    private EmbedBuilder createGameEmbed(ChessGame game, User player1, User player2) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("♔ Chess Game" + (game.vsBot ? " (vs Bot)" : ""))
                .setColor(Color.BLUE)
                .addField("White Player", player1 != null ? player1.getAsMention() : "Unknown", true)
                .addField("Black Player",
                        game.vsBot ? "🤖 Bot" : (player2 != null ? player2.getAsMention() : "Unknown"), true)
                .addField("Turn", game.isWhiteTurn ? "White" : "Black", true);

        // Add the chess board
        StringBuilder boardStr = new StringBuilder();
        boardStr.append("```\n  A B C D E F G H\n");

        for (int row = 0; row < 8; row++) {
            boardStr.append(8 - row).append(" ");
            for (int col = 0; col < 8; col++) {
                boardStr.append(game.board[row][col]).append(" ");
            }
            boardStr.append(8 - row).append("\n");
        }

        boardStr.append("  A B C D E F G H\n```");
        embed.addField("Board", boardStr.toString(), false);

        embed.addField("Moves Made", String.valueOf(game.moveCount), true);
        embed.setFooter("Use buttons to play | Game will expire in 30 minutes", null);

        return embed;
    }

    private void scheduleGameCleanup(String gameId1, String gameId2) {
        CompletableFuture.delayedExecutor(30, TimeUnit.MINUTES)
                .execute(() -> {
                    activeGames.remove(gameId1);
                    if (gameId2 != null) {
                        activeGames.remove(gameId2);
                    }
                });
    }

    public static CommandData getCommandData() {
        return Commands.slash("chess", "Start a chess game against another player or the bot")
                .addOption(OptionType.USER, "opponent", "The player to challenge (leave empty to play against the bot)",
                        false);
    }

    @Override
    public String getName() {
        return "chess";
    }

    @Override
    public String getDescription() {
        return "Play interactive chess against another player or the bot";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GAMES;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    // Inner class for chess game logic
    private static class ChessGame {
        final String whitePlayerId;
        final String blackPlayerId;
        final long startTime;
        final boolean vsBot;
        String[][] board;
        boolean isWhiteTurn = true;
        int moveCount = 0;

        ChessGame(String player1Id, String player2Id, String guildId, boolean vsBot) {
            this.whitePlayerId = player1Id;
            this.blackPlayerId = player2Id;
            this.startTime = System.currentTimeMillis();
            this.board = copyBoard(INITIAL_BOARD);
            this.vsBot = vsBot;
        }

        private String[][] copyBoard(String[][] original) {
            String[][] copy = new String[8][8];
            for (int i = 0; i < 8; i++) {
                System.arraycopy(original[i], 0, copy[i], 0, 8);
            }
            return copy;
        }

        boolean isPlayerTurn(String userId) {
            return (isWhiteTurn && userId.equals(whitePlayerId)) ||
                    (!isWhiteTurn && userId.equals(blackPlayerId));
        }

        String getCurrentPlayerId() {
            return isWhiteTurn ? whitePlayerId : blackPlayerId;
        }

        String getOpponentId() {
            return isWhiteTurn ? blackPlayerId : whitePlayerId;
        }

        void switchTurns() {
            isWhiteTurn = !isWhiteTurn;
        }

        boolean isGameOver() {
            // Simplified: game ends after 30 moves or if no pieces can move
            return moveCount >= 30 || hasNoValidMoves();
        }

        private boolean hasNoValidMoves() {
            // Simple check - in real chess this would be much more complex
            int pieceCount = 0;
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (!board[row][col].equals("⬜") && !board[row][col].equals("⬛")) {
                        pieceCount++;
                    }
                }
            }
            return pieceCount < 4; // End game if very few pieces left
        }

        String getGameResult() {
            if (moveCount >= 30) {
                return "Game ended in a draw after 30 moves!";
            } else if (hasNoValidMoves()) {
                return (isWhiteTurn ? "Black" : "White") + " wins - opponent has no valid moves!";
            }
            return "Game in progress...";
        }

        String getGameDuration() {
            long duration = System.currentTimeMillis() - startTime;
            long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
            long seconds = TimeUnit.MILLISECONDS.toSeconds(duration) % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
    }
}
