package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Command to subtract from a user's balance
 */
public class SubtractBalanceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (!"balance".equals(subcommand)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Subcommand", "Unknown subcommand: " + subcommand)).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();

        // Check for admin permissions
        if (!PermissionManager.hasPermission(member, "economy.admin.remove")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `economy.admin.remove` permission to subtract user balances.")).setEphemeral(true)
                    .queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        long amount = event.getOption("amount").getAsLong();

        try {
            String guildId = event.getGuild().getId();
            String userId = target.getId();

            // Get current balance for logging
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            // Subtract from the balance (ensure it doesn't go below 0)
            long newBalance = Math.max(0, currentBalance - amount);
            ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "💰 Balance Updated",
                    "**User:** " + target.getAsMention() + "\n" +
                            "**Subtracted:** " + amount + " points\n" +
                            "**New Balance:** " + newBalance + " points\n" +
                            "**Previous Balance:** " + currentBalance + " points"))
                    .queue();

            // Log the action
            ServerBot.getStorageManager().logModerationAction(
                    guildId, userId, event.getUser().getId(),
                    "BALANCE_SUBTRACT", "Subtracted " + amount + " points", String.valueOf(amount));

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Balance Operation Failed",
                    "Failed to subtract balance: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("subtract", "Subtract from a user's balance")
                .addSubcommands(
                        new SubcommandData("balance", "Subtract points from user's balance")
                                .addOption(OptionType.USER, "user", "User to subtract balance from", true)
                                .addOption(OptionType.INTEGER, "amount", "Amount to subtract from the balance", true));
    }

    @Override
    public String getName() {
        return "subtract";
    }

    @Override
    public String getDescription() {
        return "Subtract points from a user's balance";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
