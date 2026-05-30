package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
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
 * Command to add to a user's balance
 */
public class AddBalanceCommand implements SlashCommand {

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
        if (!PermissionManager.hasPermission(member, "economy.admin.add")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `economy.admin.add` permission to add balance to users."))
                    .setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        long amount = event.getOption("amount").getAsLong();

        try {
            String guildId = event.getGuild().getId();
            String userId = target.getId();

            // Get current balance for logging
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            // Add to the balance
            ServerBot.getStorageManager().addBalance(guildId, userId, amount);
            long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Balance Updated",
                    "**User:** " + target.getAsMention() + "\n" +
                            "**Added:** " + amount + " points\n" +
                            "**New Balance:** " + newBalance + " points\n" +
                            "**Previous Balance:** " + currentBalance + " points"))
                    .queue();

            // Log the action
            ServerBot.getStorageManager().logModerationAction(
                    guildId, userId, event.getUser().getId(),
                    "BALANCE_ADD", "Added " + amount + " points", String.valueOf(amount));

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Balance Operation Failed",
                    "Failed to add balance: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("add", "Add to a user's balance")
                .addSubcommands(
                        new SubcommandData("balance", "Add points to user's balance")
                                .addOption(OptionType.USER, "user", "User to add balance to", true)
                                .addOption(OptionType.INTEGER, "amount", "Amount to add to the balance", true));
    }

    @Override
    public String getName() {
        return "add";
    }

    @Override
    public String getDescription() {
        return "Add points to a user's balance";
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
