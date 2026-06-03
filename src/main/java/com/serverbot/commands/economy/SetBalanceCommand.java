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
 * Command to set a user's balance
 */
public class SetBalanceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (!"balance".equals(subcommand)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Subcommand", "Unknown subcommand: " + subcommand)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Member member = event.getMember();

        // Check for admin permissions
        if (!PermissionManager.hasPermission(member, "economy.admin.set")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `economy.admin.set` permission to set user balances."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        long amount = event.getOption("amount").getAsLong();

        try {
            String guildId = event.getGuild().getId();
            String userId = target.getId();

            // Get current balance for logging
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            // Set the new balance
            ServerBot.getStorageManager().setBalance(guildId, userId, amount);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "💰 Balance Set",
                    "**User:** " + target.getAsMention() + "\n" +
                            "**New Balance:** " + amount + " points\n" +
                            "**Previous Balance:** " + currentBalance + " points"))
                    .queue();

            // Log the action
            ServerBot.getStorageManager().logModerationAction(
                    guildId, userId, event.getUser().getId(),
                    "BALANCE_SET", "Set balance to " + amount, String.valueOf(amount));

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Balance Operation Failed",
                    "Failed to set balance: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("set", "Set a user's balance")
                .addSubcommands(
                        new SubcommandData("balance", "Set user's balance to a specific amount")
                                .addOption(OptionType.USER, "user", "User to set balance for", true)
                                .addOption(OptionType.INTEGER, "amount", "Amount to set the balance to", true));
    }

    @Override
    public String getName() {
        return "set";
    }

    @Override
    public String getDescription() {
        return "Set a user's balance to a specific amount";
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
