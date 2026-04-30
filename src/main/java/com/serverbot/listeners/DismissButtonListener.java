package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.DismissibleMessage;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import java.util.List;

/**
 * Listener for handling dismiss button interactions and playlist confirmation buttons.
 */
public class DismissButtonListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();

        // ── Playlist delete confirm ──────────────────────────────────────────
        if (buttonId.startsWith("playlist_delete:")) {
            // format: playlist_delete:{userId}:{playlistName}
            String[] parts = buttonId.split(":", 3);
            if (parts.length < 3) return;
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
                    "**" + playlistName + "** has been permanently deleted."
                )).setComponents(List.of()).queue();
            } else {
                event.editMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Found", "Playlist **" + playlistName + "** was not found."
                )).setComponents(List.of()).queue();
            }
            return;
        }

        // ── Playlist delete cancel ───────────────────────────────────────────
        if (buttonId.startsWith("playlist_cancel:")) {
            String ownerId = buttonId.split(":", 2)[1];
            if (!event.getUser().getId().equals(ownerId)) {
                event.reply("Only the playlist owner can cancel this.")
                    .setEphemeral(true).queue();
                return;
            }
            event.editMessageEmbeds(EmbedUtils.createInfoEmbed(
                "Cancelled", "Playlist deletion cancelled."
            )).setComponents(List.of()).queue();
            return;
        }

        // ── Dismiss button ───────────────────────────────────────────────────
        if (!DismissibleMessage.isDismissButton(buttonId)) {
            return;
        }

        String allowedUserId = DismissibleMessage.extractSenderId(buttonId);
        String clickerId = event.getUser().getId();

        if (allowedUserId != null && allowedUserId.equals(clickerId)) {
            event.getMessage().delete().queue(
                success -> {},
                failure -> event.reply("Unable to delete message.").setEphemeral(true).queue()
            );
            event.deferEdit().queue();
        } else {
            event.reply("Only the person who ran this command can dismiss this message.")
                .setEphemeral(true).queue();
        }
    }
}

