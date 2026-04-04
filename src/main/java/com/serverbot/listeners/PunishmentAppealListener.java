package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.*;
import java.time.OffsetDateTime;

/**
 * Handles appeal button interactions from punishment DMs
 */
public class PunishmentAppealListener extends ListenerAdapter {
    
    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        
        // Handle appeal buttons
        if (buttonId.startsWith("appeal:")) {
            handleAppealButton(event);
        }
    }
    
    private void handleAppealButton(ButtonInteractionEvent event) {
        String[] parts = event.getComponentId().split(":");
        if (parts.length != 4) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Appeal", "This appeal button is malformed."
            )).setEphemeral(true).queue();
            return;
        }
        
        String guildId = parts[1];
        String userId = parts[2];
        String punishmentType = parts[3];
        
        // Verify the user clicking is the same as the appealing user
        if (!event.getUser().getId().equals(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Access Denied", "You can only submit appeals for your own punishments."
            )).setEphemeral(true).queue();
            return;
        }
        
        // Get guild and check if appeal channel exists
        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Server Not Found", "The server for this appeal could not be found."
            )).setEphemeral(true).queue();
            return;
        }
        
        // Get appeal channel from punishment DM settings
        String appealChannelId = getAppealChannelId(guildId);
        if (appealChannelId == null || appealChannelId.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Appeal System Disabled", "This server has not configured an appeal system."
            )).setEphemeral(true).queue();
            return;
        }
        
        TextChannel appealChannel = guild.getTextChannelById(appealChannelId);
        if (appealChannel == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Appeal Channel Not Found", "The appeal channel for this server no longer exists."
            )).setEphemeral(true).queue();
            return;
        }
        
        // Create appeal modal
        TextInput reasonInput = TextInput.create("appeal_reason", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Explain why you believe this punishment should be appealed...")
            .setMinLength(10)
            .setMaxLength(1000)
            .setRequired(true)
            .build();
        
        TextInput evidenceInput = TextInput.create("appeal_evidence", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Provide any additional context or evidence...")
            .setMaxLength(1000)
            .setRequired(false)
            .build();
        
        Modal modal = Modal.create("appeal_submit:" + guildId + ":" + userId + ":" + punishmentType, 
                "Appeal " + punishmentType + " Punishment")
            .addComponents(Label.of("Reason for Appeal", reasonInput))
            .addComponents(Label.of("Additional Evidence (Optional)", evidenceInput))
            .build();
        
        event.replyModal(modal).queue();
    }
    
    /**
     * Handle modal submission for appeals
     */
    public void handleAppealSubmission(String modalId, String appealReason, String appealEvidence, 
            User appellant, Guild guild, String punishmentType) {
        
        String appealChannelId = getAppealChannelId(guild.getId());
        if (appealChannelId == null) {
            return;
        }
        
        TextChannel appealChannel = guild.getTextChannelById(appealChannelId);
        if (appealChannel == null) {
            return;
        }
        
        // Create appeal embed
        EmbedBuilder appealEmbed = new EmbedBuilder()
            .setTitle("📝 New Punishment Appeal")
            .setColor(Color.ORANGE)
            .setDescription("A user has submitted an appeal for their " + punishmentType.toLowerCase() + " punishment.")
            .addField("User", appellant.getAsMention() + " (" + appellant.getEffectiveName() + ")", true)
            .addField("User ID", appellant.getId(), true)
            .addField("Punishment Type", punishmentType, true)
            .addField("Appeal Reason", appealReason, false)
            .setThumbnail(appellant.getAvatarUrl())
            .setTimestamp(OffsetDateTime.now())
            .setFooter("Appeal submitted via DM");
        
        if (appealEvidence != null && !appealEvidence.trim().isEmpty()) {
            appealEmbed.addField("Additional Evidence", appealEvidence, false);
        }
        
        // Send appeal to channel
        appealChannel.sendMessageEmbeds(appealEmbed.build()).queue(
            success -> ServerBot.getLogger().info("Appeal submitted by user {} in guild {}", 
                appellant.getId(), guild.getId()),
            failure -> ServerBot.getLogger().error("Failed to send appeal to channel: {}", failure.getMessage())
        );
    }
    
    /**
     * Get appeal channel ID from punishment DM settings
     */
    private String getAppealChannelId(String guildId) {
        try {
            // Get from PunishmentNotificationService
            var settings = com.serverbot.services.PunishmentNotificationService.getInstance().getDMSettings(guildId);
            return settings.getAppealChannelId();
        } catch (Exception e) {
            ServerBot.getLogger().error("Error getting appeal channel ID: {}", e.getMessage());
            return null;
        }
    }
}