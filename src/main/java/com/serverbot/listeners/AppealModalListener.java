package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;

/**
 * Handles modal interactions for punishment appeals
 */
public class AppealModalListener extends ListenerAdapter {

    private final PunishmentAppealListener appealListener;

    public AppealModalListener() {
        this.appealListener = new PunishmentAppealListener();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if (modalId.startsWith("appeal_submit:")) {
            handleAppealSubmission(event);
        }
    }

    private void handleAppealSubmission(ModalInteractionEvent event) {
        String[] parts = event.getModalId().split(":");
        if (parts.length != 4) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Appeal", "This appeal form is malformed.")).setEphemeral(true).queue();
            return;
        }

        String guildId = parts[1];
        String userId = parts[2];
        String punishmentType = parts[3];

        // Verify the user submitting is the same as the appealing user
        if (!event.getUser().getId().equals(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Access Denied", "You can only submit appeals for your own punishments.")).setEphemeral(true)
                    .queue();
            return;
        }

        // Get guild
        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Server Not Found", "The server for this appeal could not be found.")).setEphemeral(true).queue();
            return;
        }

        // Get form values
        ModalMapping reasonMapping = event.getValue("appeal_reason");
        ModalMapping evidenceMapping = event.getValue("appeal_evidence");

        if (reasonMapping == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Information", "Appeal reason is required.")).setEphemeral(true).queue();
            return;
        }

        String appealReason = reasonMapping.getAsString();
        String appealEvidence = evidenceMapping != null ? evidenceMapping.getAsString() : null;

        // Validate appeal reason
        if (appealReason.trim().length() < 10) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Appeal Too Short", "Your appeal reason must be at least 10 characters long.")).setEphemeral(true)
                    .queue();
            return;
        }

        try {
            // Process the appeal
            appealListener.handleAppealSubmission(event.getModalId(), appealReason, appealEvidence,
                    event.getUser(), guild, punishmentType);

            // Confirm submission to user
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Appeal Submitted",
                    "Your appeal has been submitted successfully. The server moderators will review it and " +
                            "respond accordingly. Thank you for taking the time to explain your situation."))
                    .setEphemeral(true).queue();

        } catch (Exception e) {
            ServerBot.getLogger().error("Error processing appeal submission: {}", e.getMessage(), e);
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Appeal Failed", "There was an error submitting your appeal. Please try again later."))
                    .setEphemeral(true).queue();
        }
    }
}