package com.serverbot.commands.moderation;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.PermissionOverride;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.EnumSet;

/**
 * Lockdown command for locking/unlocking channels
 */
public class LockdownCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "mod.lockdown")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need moderation permissions to use this command."))
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping channelOption = event.getOption("channel");
        TextChannel targetChannel;

        if (channelOption != null) {
            targetChannel = channelOption.getAsChannel().asTextChannel();
        } else {
            targetChannel = event.getChannel().asTextChannel();
        }

        // Check if bot has permissions to modify channel
        if (!targetChannel.getGuild().getSelfMember().hasPermission(targetChannel, Permission.MANAGE_CHANNEL)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Permissions",
                    "I don't have permission to manage the channel " + targetChannel.getAsMention())).setEphemeral(true)
                    .queue();
            return;
        }

        Role everyoneRole = event.getGuild().getPublicRole();
        PermissionOverride everyoneOverride = targetChannel.getPermissionOverride(everyoneRole);

        // Check current state - if SEND_MESSAGES is already denied, unlock. Otherwise,
        // lock.
        boolean isLocked = everyoneOverride != null && everyoneOverride.getDenied().contains(Permission.MESSAGE_SEND);

        if (isLocked) {
            // Unlock the channel
            unlockChannel(event, targetChannel, everyoneRole);
        } else {
            // Lock the channel
            lockChannel(event, targetChannel, everyoneRole);
        }
    }

    private void lockChannel(SlashCommandInteractionEvent event, TextChannel channel, Role everyoneRole) {
        try {
            // Deny SEND_MESSAGES and ADD_REACTIONS for @everyone
            channel.getManager()
                    .putRolePermissionOverride(everyoneRole.getIdLong(),
                            EnumSet.noneOf(Permission.class),
                            EnumSet.of(Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION))
                    .queue(
                            success -> {
                                // Send lockdown message to the channel
                                channel.sendMessageEmbeds(EmbedUtils.createWarningEmbed(
                                        "🔒 Channel Locked",
                                        "This channel has been locked by " + event.getUser().getAsMention() + ".\n" +
                                                "Only moderators can send messages during lockdown."))
                                        .queue();

                                // Confirm to the user
                                String responseMessage = channel.equals(event.getChannel())
                                        ? "This channel has been locked."
                                        : channel.getAsMention() + " has been locked.";

                                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                        "Channel Locked", responseMessage)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                            },
                            error -> {
                                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                        "Lock Failed", "Failed to lock channel: " + error.getMessage()))
                                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                            });
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Lock Error", "An error occurred while locking the channel: " + e.getMessage())).setEphemeral(true)
                    .queue();
        }
    }

    private void unlockChannel(SlashCommandInteractionEvent event, TextChannel channel, Role everyoneRole) {
        try {
            // Remove the permission overrides for @everyone (or reset to neutral)
            PermissionOverride override = channel.getPermissionOverride(everyoneRole);
            if (override != null) {
                // Remove SEND_MESSAGES and ADD_REACTIONS from denied permissions
                EnumSet<Permission> newDenied = EnumSet.copyOf(override.getDenied());
                newDenied.remove(Permission.MESSAGE_SEND);
                newDenied.remove(Permission.MESSAGE_ADD_REACTION);

                if (newDenied.isEmpty() && override.getAllowed().isEmpty()) {
                    // If no permissions are left, delete the override
                    override.delete().queue(
                            success -> handleUnlockSuccess(event, channel),
                            error -> handleUnlockError(event, error));
                } else {
                    // Update the override
                    channel.getManager()
                            .putRolePermissionOverride(everyoneRole.getIdLong(),
                                    override.getAllowed(),
                                    newDenied)
                            .queue(
                                    success -> handleUnlockSuccess(event, channel),
                                    error -> handleUnlockError(event, error));
                }
            } else {
                handleUnlockSuccess(event, channel);
            }
        } catch (Exception e) {
            handleUnlockError(event, e);
        }
    }

    private void handleUnlockSuccess(SlashCommandInteractionEvent event, TextChannel channel) {
        // Send unlock message to the channel
        channel.sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "🔓 Channel Unlocked",
                "This channel has been unlocked by " + event.getUser().getAsMention() + ".\n" +
                        "Normal conversation can now resume."))
                .queue();

        // Confirm to the user
        String responseMessage = channel.equals(event.getChannel())
                ? "This channel has been unlocked."
                : channel.getAsMention() + " has been unlocked.";

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Channel Unlocked", responseMessage)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    private void handleUnlockError(SlashCommandInteractionEvent event, Throwable error) {
        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Unlock Failed", "Failed to unlock channel: " + error.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("lockdown", "Lock or unlock a channel to prevent regular users from sending messages")
                .addOption(OptionType.CHANNEL, "channel", "Channel to lock/unlock (defaults to current channel)",
                        false);
    }

    @Override
    public String getName() {
        return "lockdown";
    }

    @Override
    public String getDescription() {
        return "Lock or unlock a channel to prevent regular users from sending messages";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
