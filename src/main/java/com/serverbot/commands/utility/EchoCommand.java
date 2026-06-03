package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Echo command for sending messages to channels
 */
public class EchoCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        /*
         * if (!event.isFromGuild()) {
         * event.replyEmbeds(EmbedUtils.createErrorEmbed(
         * "Guild Only", "This command can only be used in servers."
         * )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
         * return;
         * }
         */

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "utility.echo")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need moderation permissions to use this command."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String message = event.getOption("message").getAsString();

        // Check message length (Discord's limit is 2000 characters)
        if (message.length() > 2000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Message Too Long",
                    "❌ Message too long! The maximum length is 2000 characters. Your message is " + message.length()
                            + " characters."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        GuildMessageChannel targetChannel = event.getOption("channel") != null
                ? (GuildMessageChannel) event.getOption("channel").getAsChannel()
                : (GuildMessageChannel) event.getChannel();

        // Check if bot can send messages in target channel
        if (!targetChannel.canTalk()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Send Message", "I don't have permission to send messages in that channel."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Send the echo message
        targetChannel.sendMessage(message).queue(
                success -> {
                    if (targetChannel.equals(event.getChannel())) {
                        // If same channel, just acknowledge
                        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                "Message Sent", "Your message has been sent.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    } else {
                        // If different channel, show where it was sent
                        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                "Message Sent", "Your message has been sent to " + targetChannel.getAsMention()))
                                .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    }
                },
                error -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Send Failed", "Failed to send message: " + error.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                });
    }

    public static CommandData getCommandData() {
        return Commands.slash("echo", "Send a message to a channel")
                .addOption(OptionType.STRING, "message", "Message to send", true)
                .addOptions(new OptionData(OptionType.CHANNEL, "channel",
                        "Channel to send message to (defaults to current)", false)
                        .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS));
    }

    @Override
    public String getName() {
        return "echo";
    }

    @Override
    public String getDescription() {
        return "Send a message to a channel";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
