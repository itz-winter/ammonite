package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Pay command for transferring coins between users
 */
public class PayCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        User sender = event.getUser();
        User recipient = event.getOption("user").getAsUser();
        long amount = event.getOption("amount").getAsLong();

        if (recipient.isBot()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Recipient",
                    "You cannot pay bots!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (recipient.equals(sender)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Recipient",
                    "You cannot pay yourself!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (amount <= 0) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Amount",
                    "Amount must be greater than 0!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();
        String guildId = event.getGuild().getId();
        String senderId = sender.getId();
        String recipientId = recipient.getId();

        long senderBalance = storage.getBalance(guildId, senderId);

        if (senderBalance < amount) {
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Insufficient Funds",
                    String.format("You need %,d %s but only have %,d %s!",
                            amount, currencyName, senderBalance, currencyName)))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Perform the transfer
        storage.removeBalance(guildId, senderId, amount);
        storage.addBalance(guildId, recipientId, amount);

        String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
        String description = String.format(
                "**%s** paid **%,d %s** to **%s**\n\n" +
                        "**%s's new balance:** %,d %s\n" +
                        "**%s's new balance:** %,d %s",
                sender.getName(), amount, currencyName, recipient.getName(),
                sender.getName(), storage.getBalance(guildId, senderId), currencyName,
                recipient.getName(), storage.getBalance(guildId, recipientId), currencyName);

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("💸 Payment Sent", description)).queue();
    }

    @Override
    public String getName() {
        return "pay";
    }

    @Override
    public String getDescription() {
        return "Pay coins to another user";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    public static CommandData getCommandData() {
        return Commands.slash("pay", "Pay coins to another user")
                .addOption(OptionType.USER, "user", "The user to pay", true)
                .addOption(OptionType.INTEGER, "amount", "Amount of coins to pay", true);
    }
}
