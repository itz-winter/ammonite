package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.DismissibleMessage;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listener for handling dismiss button interactions, playlist confirmation
 * buttons, and the ephemeral-share flow (share_req / share_yes / share_no).
 */
public class DismissButtonListener extends ListenerAdapter {

    /**
     * Temporary store for embeds pending a share-confirmation.
     * Key = userId, Value = the embed from their ephemeral message.
     * Entries are removed once the user confirms or cancels.
     */
    private static final Map<String, MessageEmbed> pendingShares = new ConcurrentHashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        //  Share flow 
        // Step 1: user clicks "📤 Share" on an ephemeral reply
        if (buttonId.startsWith("share_req:")) {
            String ownerId = buttonId.substring("share_req:".length());
            if (!event.getUser().getId().equals(ownerId)) {
                event.reply("Only the person who ran this command can share it.")
                        .setEphemeral(true).queue();
                return;
            }
            List<MessageEmbed> embeds = event.getMessage().getEmbeds();
            if (embeds.isEmpty()) {
                event.reply("Could not find the embed to share.").setEphemeral(true).queue();
                return;
            }
            // Stash the original embed so the confirm step can retrieve it
            pendingShares.put(ownerId, embeds.get(0));

            event.editMessageEmbeds(
                    EmbedUtils.createInfoEmbed(
                            "📤 Share to Channel?",
                            "This will post the result publicly in this channel so others can see it.\n\n"
                                    + "Click **Post it** to confirm, or **Cancel** to go back."))
                    .setComponents(ActionRow.of(
                            Button.success("share_yes:" + ownerId, "✅ Post it"),
                            Button.secondary("share_no:" + ownerId, "❌ Cancel")))
                    .queue();
            return;
        }

        // Step 2a: user confirms the share
        if (buttonId.startsWith("share_yes:")) {
            String ownerId = buttonId.substring("share_yes:".length());
            if (!event.getUser().getId().equals(ownerId)) {
                event.reply("Only the person who ran this command can confirm this.")
                        .setEphemeral(true).queue();
                return;
            }
            MessageEmbed stored = pendingShares.remove(ownerId);
            if (stored == null) {
                event.editMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Session Expired",
                        "The share session has expired. Run the command again and try sharing once more."))
                        .setComponents(List.of()).queue();
                return;
            }
            if (!(event.getChannel() instanceof GuildMessageChannel channel)) {
                event.editMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Not Available",
                        "Sharing is only available inside a server channel."))
                        .setComponents(List.of()).queue();
                return;
            }
            // Post the original embed publicly with a dismiss button for the owner
            Button dismissBtn = Button.danger(
                    DismissibleMessage.DISMISS_BUTTON_PREFIX + ownerId, "🗑️ Dismiss");
            channel.sendMessageEmbeds(stored)
                    .setComponents(ActionRow.of(dismissBtn))
                    .queue();
            // Update the ephemeral to a confirmation
            event.editMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "Shared!", "The result has been posted to this channel."))
                    .setComponents(List.of()).queue();
            return;
        }

        // Step 2b: user cancels the share
        if (buttonId.startsWith("share_no:")) {
            String ownerId = buttonId.substring("share_no:".length());
            if (!event.getUser().getId().equals(ownerId)) {
                event.reply("Only the person who ran this command can cancel this.")
                        .setEphemeral(true).queue();
                return;
            }
            pendingShares.remove(ownerId);
            event.editMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "Cancelled", "The result was not shared."))
                    .setComponents(List.of()).queue();
            return;
        }

        // Playlist delete confirm
        if (buttonId.startsWith("playlist_delete:")) {
            // format: playlist_delete:{userId}:{playlistName}
            String[] parts = buttonId.split(":", 3);
            if (parts.length < 3)
                return;
            String ownerId = parts[1];
            String playlistName = parts[2];

            if (!event.getUser().getId().equals(ownerId)) {
                event.reply("Only the playlist owner can confirm this deletion.")
                        .setEphemeral(true).queue();
                return;
            }

            boolean deleted = ServerBot.getStorageManager().deleteUserPlaylist(ownerId, playlistName);
            if (deleted) {
                event.editMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "Playlist Deleted",
                        "**" + playlistName + "** has been permanently deleted.")).setComponents(List.of()).queue();
            } else {
                event.editMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Not Found", "Playlist **" + playlistName + "** was not found.")).setComponents(List.of())
                        .queue();
            }
            return;
        }

        // Playlist delete cancel
        if (buttonId.startsWith("playlist_cancel:")) {
            String ownerId = buttonId.split(":", 2)[1];
            if (!event.getUser().getId().equals(ownerId)) {
                event.reply("Only the playlist owner can cancel this.")
                        .setEphemeral(true).queue();
                return;
            }
            event.editMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "Cancelled", "Playlist deletion cancelled.")).setComponents(List.of()).queue();
            return;
        }

        // Dismiss button
        if (!DismissibleMessage.isDismissButton(buttonId)) {
            return;
        }

        String allowedUserId = DismissibleMessage.extractSenderId(buttonId);
        String clickerId = event.getUser().getId();

        if (allowedUserId != null && allowedUserId.equals(clickerId)) {
            event.getMessage().delete().queue(
                    success -> {
                    },
                    failure -> event.reply("Unable to delete message.").setEphemeral(true).queue());
            event.deferEdit().queue();
        } else {
            event.reply("Only the person who ran this command can dismiss this message.")
                    .setEphemeral(true).queue();
        }
    }
}
