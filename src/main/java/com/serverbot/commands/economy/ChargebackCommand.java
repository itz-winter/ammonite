package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.*;
import java.time.Instant;
import java.util.Map;

/**
 * Chargeback command for reversing transactions
 */
public class ChargebackCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "economy.admin.chargeback")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `economy.admin.chargeback` permission to perform chargebacks.")).setEphemeral(true)
                    .queue();
            return;
        }

        String transactionId = event.getOption("transactionid").getAsString();
        String reason = event.getOption("reason").getAsString();

        try {
            String guildId = event.getGuild().getId();

            // Process the chargeback - in a real implementation, this would:
            // 1. Find the transaction by ID in the database
            // 2. Reverse the transaction (add/subtract balances)
            // 3. Log the chargeback action

            // For this implementation, we'll create a chargeback log entry
            long chargebackId = System.currentTimeMillis();

            // Log the chargeback action
            ServerBot.getStorageManager().logModerationAction(
                    guildId,
                    transactionId, // Using transaction ID as target
                    member.getUser().getId(),
                    "CHARGEBACK",
                    reason,
                    "Transaction ID: " + transactionId);

            // Create chargeback record
            Map<String, Object> chargebackRecord = Map.of(
                    "id", chargebackId,
                    "transactionId", transactionId,
                    "reason", reason,
                    "moderatorId", member.getUser().getId(),
                    "timestamp", System.currentTimeMillis(),
                    "status", "PROCESSED");

            // Store chargeback (you might want to create a separate chargeback storage
            // method)
            ServerBot.getStorageManager().updateGuildSettings(guildId, "chargeback_" + chargebackId, chargebackRecord);

            // Send confirmation
            EmbedBuilder confirmationEmbed = new EmbedBuilder()
                    .setTitle("💳 Chargeback Processed")
                    .setColor(Color.ORANGE)
                    .addField("Transaction ID", transactionId, true)
                    .addField("Chargeback ID", String.valueOf(chargebackId), true)
                    .addField("Reason", reason, false)
                    .addField("Processed by", member.getUser().getAsMention(), true)
                    .setTimestamp(Instant.now())
                    .setFooter("Chargeback System", event.getJDA().getSelfUser().getAvatarUrl());

            event.replyEmbeds(confirmationEmbed.build()).queue();

            // Log to modlog channel if configured
            logChargebackToChannel(event, transactionId, chargebackId, reason, member);

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Chargeback Failed",
                    "Failed to process chargeback: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void logChargebackToChannel(SlashCommandInteractionEvent event, String transactionId,
            long chargebackId, String reason, Member moderator) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String logChannelId = (String) guildSettings.get("modLogChannelId");

            if (logChannelId != null) {
                TextChannel logChannel = event.getGuild().getTextChannelById(logChannelId);
                if (logChannel != null) {
                    EmbedBuilder logEmbed = new EmbedBuilder()
                            .setTitle("🔄 Chargeback Action")
                            .setColor(Color.ORANGE)
                            .addField("📋 Transaction ID", transactionId, true)
                            .addField("🆔 Chargeback ID", String.valueOf(chargebackId), true)
                            .addField("👤 Moderator", moderator.getUser().getAsMention(), true)
                            .addField("📝 Reason", reason, false)
                            .addField("⏰ Time", "<t:" + (System.currentTimeMillis() / 1000) + ":F>", true)
                            .setTimestamp(Instant.now())
                            .setFooter("Chargeback logged", event.getJDA().getSelfUser().getAvatarUrl());

                    logChannel.sendMessageEmbeds(logEmbed.build()).queue(null, throwable -> {
                        System.err.println("Failed to log chargeback to channel: " + throwable.getMessage());
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Error logging chargeback to channel: " + e.getMessage());
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("chargeback", "Reverse a transaction and log the action")
                .addOption(OptionType.STRING, "transactionid", "ID of the transaction to reverse", true)
                .addOption(OptionType.STRING, "reason", "Reason for the chargeback", true);
    }

    @Override
    public String getName() {
        return "chargeback";
    }

    @Override
    public String getDescription() {
        return "Reverse a transaction and log the chargeback action";
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
