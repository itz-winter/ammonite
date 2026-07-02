package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.commands.utility.SuspiciousListCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.BotConfig;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.DmUtils;
import com.serverbot.utils.SafeRestAction;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles button interactions for suspicious account alerts
 */
public class SuspiciousAccountButtonListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousAccountButtonListener.class);

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        //  Notify-list pagination (snl:page:<guildId>:<page>) 
        if (componentId.startsWith("snl:page:")) {
            handleListPage(event, componentId);
            return;
        }

        // Masterlist view pagination (slv:page:<page>)
        if (componentId.startsWith("slv:page:")) {
            // Disabled sentinel IDs used for prev/cur/next in boundary pages — silently ignore
            if (componentId.equals("slv:page:prev") || componentId.equals("slv:page:next")
                    || componentId.equals("slv:page:cur")) {
                event.deferEdit().queue();
                return;
            }
            handleMasterlistPage(event, componentId);
            return;
        }

        // Masterlist refresh (slv:refresh:<page>)
        if (componentId.startsWith("slv:refresh:")) {
            handleMasterlistRefresh(event, componentId);
            return;
        }

        // Masterlist review actions (slv:review:<action>:<userId> or slv:review:done)
        if (componentId.startsWith("slv:review:")) {
            handleReviewAction(event, componentId);
            return;
        }

        // Check if this is a suspicious account button
        if (!componentId.startsWith("suspicious_")) {
            return;
        }

        // Verify this is from a bot owner
        BotConfig config = ServerBot.getConfigManager().getConfig();
        List<String> botOwnerIds = config.getAllOwnerIds();
        if (!botOwnerIds.contains(event.getUser().getId())) {
            event.reply("Only bot owners can perform this action.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Parse button ID - supports both formats:
        // Format 1: suspicious_<action>:<userId>:<guildId> (3 parts - legacy detection
        // alerts)
        // Format 2: suspicious_<action>:<userId> (2 parts - report buttons)
        String[] parts = componentId.split(":");

        if (parts.length == 2) {
            // New report button format: suspicious_validate:<userId>,
            // suspicious_invalidate:<userId>, suspicious_view:<userId>,
            // suspicious_remove:<userId>
            String action = parts[0].replace("suspicious_", "");
            String userId = parts[1];

            switch (action) {
                case "validate":
                    handleValidateReport(event, userId);
                    break;
                case "invalidate":
                    handleInvalidateReport(event, userId);
                    break;
                case "view":
                    handleViewDetails(event, userId);
                    break;
                case "remove":
                    handleRemoveUser(event, userId);
                    break;
                default:
                    event.reply("Unknown action.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        } else if (parts.length == 3) {
            // Legacy format: suspicious_<action>:<userId>:<guildId>
            String action = parts[0].replace("suspicious_", "");
            String userId = parts[1];
            String guildId = parts[2];

            switch (action) {
                case "mark":
                    handleMarkSuspicious(event, userId, guildId);
                    break;
                case "ignore":
                    handleIgnore(event, userId, guildId);
                    break;
                case "addnote":
                    handleAddNote(event, userId, guildId);
                    break;
                case "details":
                    handleViewDetailsFromAlert(event, userId, guildId);
                    break;
                default:
                    event.reply("Unknown action.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        } else {
            event.reply("Invalid button data.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    /**
     * Handles validating a suspicious user report
     */
    private void handleValidateReport(ButtonInteractionEvent event, String userId) {
        event.deferEdit().queue();

        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Object> userData = storage.getSuspiciousUserData(userId);

        if (userData == null || userData.isEmpty()) {
            event.getHook().sendMessage("This user is no longer in the suspicious masterlist.").setEphemeral(true)
                    .queue();
            return;
        }

        // Mark as validated
        storage.validateSuspiciousUser(userId, event.getUser().getId());

        // Get user info
        String userInfo = "Unknown User";
        String userAvatar = null;
        try {
            User user = event.getJDA().retrieveUserById(userId).complete();
            if (user != null) {
                userInfo = user.getName() + " (" + user.getId() + ")";
                userAvatar = user.getEffectiveAvatarUrl();
            }
        } catch (Exception ignored) {
        }

        // Get original reason
        String reason = (String) userData.getOrDefault("reason", "No reason specified");

        // Build the updated embed
        EmbedBuilder updatedEmbed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("✅ Report Validated")
                .setDescription("This suspicious user report has been validated and confirmed.")
                .addField("User", userInfo, false)
                .addField("User ID", "`" + userId + "`", true)
                .addField("Validated By", event.getUser().getAsMention(), true)
                .addField("Status", "**CONFIRMED SUSPICIOUS**", false)
                .addField("Original Reason", reason, false)
                .setTimestamp(Instant.now())
                .setFooter("Validated at");

        if (userAvatar != null) {
            updatedEmbed.setThumbnail(userAvatar);
        }

        // Update the current message (the one this owner clicked)
        event.getHook().editOriginalEmbeds(updatedEmbed.build())
                .setComponents() // Remove buttons
                .queue();

        // Update all other bot owners' messages
        updateOtherOwnerMessages(event, userId, updatedEmbed.build(), event.getUser().getId());

        // Notify all servers where this user is present about validation
        notifyServersOfValidation(event.getJDA(), userId, userInfo, reason);

        logger.info("Bot owner {} validated suspicious user report for {}", event.getUser().getId(), userId);
    }

    /**
     * Handles invalidating a suspicious user report
     */
    private void handleInvalidateReport(ButtonInteractionEvent event, String userId) {
        event.deferEdit().queue();

        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Object> userData = storage.getSuspiciousUserData(userId);

        if (userData == null || userData.isEmpty()) {
            event.getHook().sendMessage("This user is no longer in the suspicious masterlist.").setEphemeral(true)
                    .queue();
            return;
        }

        // Get user info before removing
        String userInfo = "Unknown User";
        try {
            User user = event.getJDA().retrieveUserById(userId).complete();
            if (user != null) {
                userInfo = user.getName() + " (" + user.getId() + ")";
            }
        } catch (Exception ignored) {
        }

        // Collect guilds that auto-banned this user before removing the entry
        List<String> autoBannedGuilds = storage.getAutoBannedGuilds(userId);

        // Remove from masterlist
        storage.removeSuspiciousUser(userId);

        // Unban from all guilds that had used /suspiciouslist ban on this user
        if (!autoBannedGuilds.isEmpty()) {
            com.serverbot.commands.utility.SuspiciousListCommand.unbanFromGuilds(
                    event.getJDA(), userId, autoBannedGuilds);
        }

        // Build the updated embed
        EmbedBuilder updatedEmbed = new EmbedBuilder()
                .setColor(Color.GRAY)
                .setTitle("❌ Report Invalidated")
                .setDescription(
                        "This suspicious user report has been reviewed and invalidated.\nThe user has been removed from the masterlist.")
                .addField("User", userInfo, false)
                .addField("User ID", "`" + userId + "`", true)
                .addField("Invalidated By", event.getUser().getAsMention(), true)
                .addField("Status", "Report Dismissed", false)
                .setTimestamp(Instant.now())
                .setFooter("Invalidated at");

        // Update the current message (the one this owner clicked)
        event.getHook().editOriginalEmbeds(updatedEmbed.build())
                .setComponents() // Remove buttons
                .queue();

        // Update all other bot owners' messages
        updateOtherOwnerMessages(event, userId, updatedEmbed.build(), event.getUser().getId());

        logger.info("Bot owner {} invalidated suspicious user report for {}", event.getUser().getId(), userId);
    }

    /**
     * Updates all other bot owners' messages when one owner validates/invalidates a
     * report.
     * This ensures all owners see the same status and who made the decision.
     * 
     * @param event            The button interaction event
     * @param suspiciousUserId The ID of the suspicious user
     * @param updatedEmbed     The updated embed to show
     * @param actingOwnerId    The ID of the owner who made the decision
     */
    private void updateOtherOwnerMessages(ButtonInteractionEvent event, String suspiciousUserId,
            MessageEmbed updatedEmbed, String actingOwnerId) {
        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, String> ownerMessageIds = storage.getPendingReportMessages(suspiciousUserId);

        if (ownerMessageIds.isEmpty()) {
            logger.debug("No pending report messages found for user {}", suspiciousUserId);
            return;
        }

        int updatedCount = 0;
        for (Map.Entry<String, String> entry : ownerMessageIds.entrySet()) {
            String ownerId = entry.getKey();
            String messageId = entry.getValue();

            // Skip the owner who just clicked (their message is already updated)
            if (ownerId.equals(actingOwnerId)) {
                continue;
            }

            try {
                User owner = event.getJDA().retrieveUserById(ownerId).complete();
                if (owner != null) {
                    PrivateChannel channel = owner.openPrivateChannel().complete();
                    channel.editMessageEmbedsById(messageId, updatedEmbed)
                            .setComponents() // Remove buttons
                            .queue(
                                    success -> logger.debug("Updated report message for owner {}", ownerId),
                                    error -> logger.warn("Failed to update report message for owner {}: {}", ownerId,
                                            error.getMessage()));
                    updatedCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to update report message for owner {}: {}", ownerId, e.getMessage());
            }
        }

        // Clean up the pending messages since the report has been resolved
        storage.removePendingReportMessages(suspiciousUserId);
        logger.info("Updated {} other owner messages for suspicious user {}", updatedCount, suspiciousUserId);
    }

    /**
     * Handles viewing suspicious user details
     */
    private void handleViewDetails(ButtonInteractionEvent event, String userId) {
        try {
            FileStorageManager storage = ServerBot.getStorageManager();
            Map<String, Object> userData = storage.getSuspiciousUserData(userId);

            // Get user info from Discord
            String userInfo = "Unknown User";
            String userAvatar = null;
            User user = null;
            try {
                user = event.getJDA().retrieveUserById(userId).complete();
                if (user != null) {
                    userInfo = user.getName() + " (" + user.getId() + ")";
                    userAvatar = user.getEffectiveAvatarUrl();
                }
            } catch (Exception ignored) {
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 User Details")
                    .setColor(Color.ORANGE)
                    .addField("User", userInfo, true)
                    .addField("User ID", "`" + userId + "`", true);

            if (userAvatar != null) {
                embed.setThumbnail(userAvatar);
            }

            // Add Discord profile info
            if (user != null) {
                embed.addField("Account Created", "<t:" + user.getTimeCreated().toEpochSecond() + ":F>", true);
                embed.addField("Default Avatar", user.getAvatarUrl() == null ? "Yes (no custom avatar)" : "No", true);
                embed.addField("User Link", "[View Profile](https://discord.com/users/" + userId + ")", false);
            }

            // If user is in masterlist, show additional info
            if (userData != null && !userData.isEmpty()) {
                embed.setTitle("📋 Suspicious User Details");
                embed.setColor(Color.RED);

                // Add reason
                String reason = (String) userData.getOrDefault("reason", "No reason specified");
                embed.addField("Reason", reason, false);

                // Add marked info
                String markedBy = (String) userData.get("markedBy");
                if (markedBy != null) {
                    embed.addField("Marked By", "<@" + markedBy + ">", true);
                }

                Object markedAtObj = userData.get("markedAt");
                if (markedAtObj != null) {
                    try {
                        long timestamp;
                        if (markedAtObj instanceof Number) {
                            timestamp = ((Number) markedAtObj).longValue();
                            // Convert from millis to seconds if needed
                            if (timestamp > 9999999999L) {
                                timestamp = timestamp / 1000;
                            }
                        } else if (markedAtObj instanceof String) {
                            // Try parsing as ISO instant
                            Instant instant = Instant.parse((String) markedAtObj);
                            timestamp = instant.getEpochSecond();
                        } else {
                            timestamp = 0;
                        }
                        if (timestamp > 0) {
                            embed.addField("Marked At", "<t:" + timestamp + ":F>", true);
                        }
                    } catch (Exception e) {
                        embed.addField("Marked At", markedAtObj.toString(), true);
                    }
                }

                // Add validation status
                Boolean validated = (Boolean) userData.get("validated");
                embed.addField("Status", validated != null && validated ? CustomEmojis.SUCCESS + " Validated"
                        : CustomEmojis.ERROR + " Pending Validation", true);

                // Add detection data if available
                @SuppressWarnings("unchecked")
                Map<String, Object> detectionData = (Map<String, Object>) userData.get("detectionData");
                if (detectionData != null) {
                    StringBuilder details = new StringBuilder();

                    String reportedFromName = (String) detectionData.get("reportedFromName");
                    if (reportedFromName != null) {
                        details.append("**Reported From:** ").append(reportedFromName).append("\n");
                    }

                    String reportedByName = (String) detectionData.get("reportedByName");
                    if (reportedByName != null) {
                        details.append("**Reporter:** ").append(reportedByName).append("\n");
                    }

                    String notes = (String) detectionData.get("notes");
                    if (notes != null) {
                        details.append("**Notes:** ").append(notes).append("\n");
                    }

                    if (details.length() > 0) {
                        embed.addField("Additional Details", details.toString(), false);
                    }
                }
            } else {
                // User not in masterlist, show that
                embed.addField("Masterlist Status", "Not on suspicious masterlist", false);
            }

            embed.setTimestamp(Instant.now());

            event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        } catch (Exception e) {
            logger.error("Error displaying suspicious user details for {}: {}", userId, e.getMessage(), e);
            event.reply("An error occurred while retrieving user details.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    /**
     * Handles removing a user from the suspicious masterlist
     */
    private void handleRemoveUser(ButtonInteractionEvent event, String userId) {
        FileStorageManager storage = ServerBot.getStorageManager();

        if (!storage.isUserSuspicious(userId)) {
            event.reply("This user is no longer in the suspicious masterlist.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Get user info before removing
        String userInfo = "Unknown User";
        try {
            User user = event.getJDA().retrieveUserById(userId).complete();
            if (user != null) {
                userInfo = user.getName() + " (" + user.getId() + ")";
            }
        } catch (Exception ignored) {
        }

        // Remove from masterlist
        storage.removeSuspiciousUser(userId);

        event.reply("✅ User **" + userInfo + "** (`" + userId + "`) has been removed from the suspicious masterlist.")
                .setEphemeral(true)
                .queue();

        logger.info("Bot owner {} removed user {} from suspicious masterlist via button", event.getUser().getId(),
                userId);
    }

    /**
     * Notifies all servers where the validated suspicious user is present
     */
    private void notifyServersOfValidation(net.dv8tion.jda.api.JDA jda, String userId, String userInfo, String reason) {
        for (Guild guild : jda.getGuilds()) {
            try {
                guild.retrieveMemberById(userId).queue(
                        member -> {
                            // User is in this guild, notify the owner
                            guild.retrieveOwner().queue(owner -> {
                                EmbedBuilder alertEmbed = new EmbedBuilder()
                                        .setTitle("🚨 Suspicious User Confirmed")
                                        .setColor(Color.RED)
                                        .setDescription(
                                                "A user in your server has been **confirmed as suspicious** by a bot administrator.")
                                        .addField("User", userInfo, true)
                                        .addField("User ID", "`" + userId + "`", true)
                                        .addField("Reason", reason, false)
                                        .addField("Status", "**VALIDATED - CONFIRMED THREAT**", false)
                                        .setTimestamp(Instant.now())
                                        .setFooter("You may want to take action on this user.");

                                DmUtils.sendDm(guild, owner.getUser(), alertEmbed.build(),
                                        v -> logger.debug("Sent validation alert to owner of {}", guild.getName()),
                                        error -> logger.debug("Failed to send validation alert to owner of {}",
                                                guild.getName()));
                            }, error -> {
                            });
                        },
                        error -> {
                        } // User not in guild
                );
            } catch (Exception e) {
                logger.debug("Error checking guild {} for suspicious user: {}", guild.getName(), e.getMessage());
            }
        }
    }

    /**
     * Handles marking a user as suspicious
     */
    private void handleMarkSuspicious(ButtonInteractionEvent event, String userId, String guildId) {
        // Defer reply while we process
        event.deferEdit().queue();

        // Get user and guild info
        SafeRestAction.queue(
                ServerBot.getJda().retrieveUserById(userId),
                "retrieve suspicious user",
                user -> {
                    // Store in suspicious list
                    Map<String, Object> detectionData = new HashMap<>();
                    detectionData.put("markedFromGuild", guildId);
                    detectionData.put("markedAt", System.currentTimeMillis());

                    ServerBot.getStorageManager().markUserAsSuspicious(
                            userId,
                            event.getUser().getId(),
                            "Marked by bot owner from detection alert",
                            detectionData);

                    // Update the message to show it was marked
                    EmbedBuilder updatedEmbed = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle(CustomEmojis.SUCCESS + " User Marked as Suspicious")
                            .setDescription("This user has been added to the suspicious users list.")
                            .addField("User", user.getAsMention() + " (`" + user.getName() + "`)", false)
                            .addField("User ID", userId, false)
                            .addField("Marked By", event.getUser().getAsMention(), false)
                            .setFooter("All guild owners with this user will be notified")
                            .setTimestamp(OffsetDateTime.now());

                    event.getHook().editOriginalEmbeds(updatedEmbed.build())
                            .setComponents() // Remove buttons
                            .queue();

                    // Send notifications to all guilds with this user
                    notifyGuildsAboutSuspiciousUser(user);

                    logger.info("User {} marked as suspicious by bot owner {}", userId, event.getUser().getId());
                },
                error -> {
                    event.getHook().sendMessage(CustomEmojis.ERROR + " Failed to retrieve user information.")
                            .setEphemeral(true)
                            .queue();
                });
    }

    /**
     * Handles adding a note to a suspicious account alert
     */
    private void handleAddNote(ButtonInteractionEvent event, String userId, String guildId) {
        // Create modal for note input
        TextInput noteInput = TextInput.create("suspicious_note", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Add a note about this suspicious account...")
                .setMinLength(1)
                .setMaxLength(1000)
                .setRequired(true)
                .build();

        TextInput actionInput = TextInput.create("suspicious_action", TextInputStyle.SHORT)
                .setPlaceholder("e.g., Ban immediately, Monitor closely, etc.")
                .setMaxLength(200)
                .setRequired(false)
                .build();

        Modal modal = Modal.create("suspicious_note_submit:" + userId + ":" + guildId,
                "Add Note to Suspicious Report")
                .addComponents(Label.of("Note", noteInput))
                .addComponents(Label.of("Suggested Action (Optional)", actionInput))
                .build();

        event.replyModal(modal).queue();
    }

    /**
     * Handles the modal submission for adding notes
     */
    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String modalId = event.getModalId();

        if (!modalId.startsWith("suspicious_note_submit:")) {
            return;
        }

        // Verify this is from a bot owner
        BotConfig config = ServerBot.getConfigManager().getConfig();
        List<String> botOwnerIds = config.getAllOwnerIds();
        if (!botOwnerIds.contains(event.getUser().getId())) {
            event.reply("Only bot owners can perform this action.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String[] parts = modalId.split(":");
        if (parts.length != 3) {
            event.reply("Invalid modal data.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String userId = parts[1];
        String guildId = parts[2];

        String note = event.getValue("suspicious_note").getAsString();
        String suggestedAction = event.getValue("suspicious_action") != null
                ? event.getValue("suspicious_action").getAsString()
                : null;

        // Store the note and action
        FileStorageManager storage = ServerBot.getStorageManager();
        storage.addSuspiciousUserNote(userId, note, event.getUser().getId(), suggestedAction);

        // Build confirmation embed
        EmbedBuilder confirmEmbed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle(CustomEmojis.SUCCESS + " Note Added")
                .setDescription("Your note has been added to the suspicious user report.")
                .addField("User ID", userId, true)
                .addField("Added By", event.getUser().getAsMention(), true)
                .addField("Note", note, false);

        if (suggestedAction != null && !suggestedAction.trim().isEmpty()) {
            confirmEmbed.addField("Suggested Action", suggestedAction, false);
        }

        confirmEmbed.setTimestamp(Instant.now());

        event.replyEmbeds(confirmEmbed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();

        // Broadcast note to other bot owners
        broadcastNoteToOwners(event, userId, guildId, note, suggestedAction);

        logger.info("Bot owner {} added note to suspicious user {}: {}", event.getUser().getId(), userId, note);
    }

    /**
     * Broadcasts a note to all bot owners
     */
    private void broadcastNoteToOwners(ModalInteractionEvent event, String userId, String guildId, String note,
            String suggestedAction) {
        BotConfig config = ServerBot.getConfigManager().getConfig();
        List<String> botOwnerIds = config.getAllOwnerIds();

        // Get user info
        String userInfo = "User ID: " + userId;
        String userAvatar = null;
        try {
            User user = event.getJDA().retrieveUserById(userId).complete();
            if (user != null) {
                userInfo = user.getName() + " (" + user.getId() + ")";
                userAvatar = user.getEffectiveAvatarUrl();
            }
        } catch (Exception ignored) {
        }

        // Get guild info
        String guildInfo = "Guild ID: " + guildId;
        try {
            Guild guild = event.getJDA().getGuildById(guildId);
            if (guild != null) {
                guildInfo = guild.getName() + " (" + guild.getId() + ")";
            }
        } catch (Exception ignored) {
        }

        EmbedBuilder noteEmbed = new EmbedBuilder()
                .setColor(Color.CYAN)
                .setTitle("📝 Note Added to Suspicious Report")
                .setDescription("A bot owner has added a note to a suspicious user report.")
                .addField("User", userInfo, false)
                .addField("Originating Guild", guildInfo, false)
                .addField("Note By", event.getUser().getAsMention(), true)
                .addField("Note", note, false);

        if (suggestedAction != null && !suggestedAction.trim().isEmpty()) {
            noteEmbed.addField("Suggested Action", "⚠️ " + suggestedAction, false);
        }

        if (userAvatar != null) {
            noteEmbed.setThumbnail(userAvatar);
        }

        noteEmbed.setTimestamp(Instant.now())
                .setFooter("Suspicious Account Detection System");

        for (String ownerId : botOwnerIds) {
            // Don't notify the owner who added the note
            if (ownerId.equals(event.getUser().getId())) {
                continue;
            }

            SafeRestAction.queue(
                    event.getJDA().retrieveUserById(ownerId),
                    "retrieve bot owner for note broadcast",
                    owner -> SafeRestAction.queue(
                            owner.openPrivateChannel(),
                            "open DM with bot owner for note broadcast",
                            channel -> SafeRestAction.queue(
                                    channel.sendMessageEmbeds(noteEmbed.build()),
                                    "send note broadcast to bot owner",
                                    success -> logger.debug("Sent note broadcast to owner {}", ownerId),
                                    error -> logger.warn("Failed to send note broadcast to owner {}: {}", ownerId,
                                            error.getMessage()))));
        }
    }

    /**
     * Handles viewing details of a suspicious account from alert
     */
    private void handleViewDetailsFromAlert(ButtonInteractionEvent event, String userId, String guildId) {
        event.deferReply(true).queue();

        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Object> userData = storage.getSuspiciousUserData(userId);

        // Try to get user info
        SafeRestAction.queue(
                event.getJDA().retrieveUserById(userId),
                "retrieve user for details view",
                user -> {
                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.ORANGE)
                            .setTitle("🔍 Suspicious User Details")
                            .setThumbnail(user.getEffectiveAvatarUrl());

                    embed.addField("Username", user.getName(), true);
                    embed.addField("User ID", user.getId(), true);
                    embed.addField("Account Created", String.format("<t:%d:R>",
                            user.getTimeCreated().toEpochSecond()), true);
                    embed.addField("Default Avatar",
                            user.getDefaultAvatarUrl().equals(user.getEffectiveAvatarUrl()) ? "Yes" : "No", true);
                    embed.addField("Profile Link", "[View Profile](https://discord.com/users/" + user.getId() + ")",
                            true);

                    // Add stored data if available
                    if (userData != null && !userData.isEmpty()) {
                        String reason = (String) userData.getOrDefault("reason", "Not specified");
                        embed.addField("Detection Reason", reason, false);

                        Boolean validated = (Boolean) userData.get("validated");
                        if (validated != null && validated) {
                            embed.addField("Status", "✅ **VALIDATED** - Confirmed Suspicious", false);
                            String validatedBy = (String) userData.get("validatedBy");
                            if (validatedBy != null) {
                                embed.addField("Validated By", "<@" + validatedBy + ">", true);
                            }
                        } else {
                            embed.addField("Status", "⏳ Pending Validation", false);
                        }

                        // Show notes if any
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> notes = (List<Map<String, Object>>) userData.get("notes");
                        if (notes != null && !notes.isEmpty()) {
                            StringBuilder notesStr = new StringBuilder();
                            for (Map<String, Object> noteData : notes) {
                                String noteText = (String) noteData.get("text");
                                String noteBy = (String) noteData.get("addedBy");
                                notesStr.append("• ").append(noteText);
                                if (noteBy != null) {
                                    notesStr.append(" (by <@").append(noteBy).append(">)");
                                }
                                notesStr.append("\n");
                            }
                            embed.addField("📝 Notes", notesStr.toString(), false);
                        }

                        // Show suggested action if set
                        String suggestedAction = (String) userData.get("suggestedAction");
                        if (suggestedAction != null && !suggestedAction.trim().isEmpty()) {
                            embed.addField("⚠️ Suggested Action", suggestedAction, false);
                        }
                    } else {
                        embed.addField("Stored Data", "No additional data stored for this user.", false);
                    }

                    // Add guild info
                    try {
                        Guild guild = event.getJDA().getGuildById(guildId);
                        if (guild != null) {
                            embed.addField("Detected In", guild.getName(), true);
                        }
                    } catch (Exception ignored) {
                    }

                    embed.setTimestamp(Instant.now())
                            .setFooter("Suspicious Account Detection System");

                    event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                },
                error -> {
                    // Fallback if user not retrievable
                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.GRAY)
                            .setTitle("🔍 Suspicious User Details")
                            .setDescription("Could not retrieve user information. User may have deleted their account.")
                            .addField("User ID", userId, false);

                    if (userData != null && !userData.isEmpty()) {
                        String reason = (String) userData.getOrDefault("reason", "Not specified");
                        embed.addField("Detection Reason", reason, false);
                    }

                    event.getHook().sendMessageEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                });
    }

    /**
     * Handles ignoring a suspicious account alert
     */
    private void handleIgnore(ButtonInteractionEvent event, String userId, String guildId) {
        // Update the message to show it was ignored
        EmbedBuilder updatedEmbed = new EmbedBuilder()
                .setColor(Color.GRAY)
                .setTitle("Alert Ignored")
                .setDescription("This suspicious account alert has been dismissed.")
                .addField("User ID", userId, false)
                .addField("Ignored By", event.getUser().getAsMention(), false)
                .setTimestamp(OffsetDateTime.now());

        event.editMessageEmbeds(updatedEmbed.build())
                .setComponents() // Remove buttons
                .queue();

        logger.info("Suspicious account alert for user {} ignored by bot owner {}", userId, event.getUser().getId());
    }

    /**
     * Notifies all guild owners where the suspicious user is a member
     */
    private void notifyGuildsAboutSuspiciousUser(User suspiciousUser) {
        // Get all guilds the bot is in
        List<Guild> guilds = ServerBot.getJda().getGuilds();

        for (Guild guild : guilds) {
            // Check if the suspicious user is in this guild
            SafeRestAction.queue(
                    guild.retrieveMemberById(suspiciousUser.getId()),
                    "check if suspicious user is in guild " + guild.getName(),
                    member -> {
                        // User is in this guild, send notification to owner
                        sendSuspiciousUserNotification(guild, suspiciousUser);
                    },
                    error -> {
                        // User is not in this guild or error retrieving, skip
                        logger.debug("User {} not in guild {} or error retrieving",
                                suspiciousUser.getId(), guild.getName());
                    });
        }
    }

    /**
     * Sends a notification to a guild owner about a suspicious user
     */
    private void sendSuspiciousUserNotification(Guild guild, User suspiciousUser) {
        // Get guild settings to check for additional notification recipients
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());

        // Get suspicious user data
        Map<String, Object> suspiciousData = ServerBot.getStorageManager()
                .getSuspiciousUserData(suspiciousUser.getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle(CustomEmojis.WARN + " Suspicious User Alert")
                .setDescription("A user in your server has been flagged as suspicious by the bot administrators.")
                .addField("User", suspiciousUser.getAsMention() + " (`" + suspiciousUser.getName() + "`)", false)
                .addField("User ID", suspiciousUser.getId(), true)
                .addField("User Link", "[Click here](https://discord.com/users/" + suspiciousUser.getId() + ")", true)
                .addField("Server", guild.getName(), false)
                .addField("Marked At", String.format("<t:%d:R>",
                        ((Number) suspiciousData.getOrDefault("markedAt", System.currentTimeMillis())).longValue()
                                / 1000),
                        true)
                .setThumbnail(suspiciousUser.getEffectiveAvatarUrl())
                .setFooter("Suspicious Account Detection System",
                        ServerBot.getJda().getSelfUser().getEffectiveAvatarUrl())
                .setTimestamp(OffsetDateTime.now());

        // Send to guild owner
        SafeRestAction.queue(
                guild.retrieveOwner(),
                "retrieve guild owner for suspicious user notification",
                owner -> {
                    DmUtils.sendDm(guild, owner.getUser(), embed.build(),
                            v -> logger.info("Sent suspicious user notification to owner of guild {}", guild.getName()),
                            error -> logger.warn("Failed to send suspicious user notification to owner of guild {}: {}",
                                    guild.getName(), error.getMessage()));
                });

        // Send to additional notification recipients if configured
        @SuppressWarnings("unchecked")
        List<String> additionalRecipients = (List<String>) settings.get("suspiciousAccountNotifyUsers");
        if (additionalRecipients != null && !additionalRecipients.isEmpty()) {
            for (String recipientId : additionalRecipients) {
                SafeRestAction.queue(
                        guild.retrieveMemberById(recipientId),
                        "retrieve additional notification recipient",
                        member -> {
                            DmUtils.sendDm(guild, member.getUser(), embed.build(),
                                    v -> logger.debug(
                                            "Sent suspicious user notification to additional recipient {} in guild {}",
                                            recipientId, guild.getName()),
                                    error -> logger.warn("Failed to send notification to recipient {}: {}",
                                            recipientId, error.getMessage()));
                        });
            }
        }
    }

    //  Notify-list pagination 

    private void handleListPage(ButtonInteractionEvent event, String componentId) {
        // Format: snl:page:<guildId>:<pageNumber>
        String[] parts = componentId.split(":");
        if (parts.length != 4) { event.deferEdit().queue(); return; }
        String guildId = parts[2];
        int page;
        try { page = Integer.parseInt(parts[3]); } catch (NumberFormatException e) { event.deferEdit().queue(); return; }
        if (page <= 0) { event.deferEdit().queue(); return; } // disabled button

        Guild guild = event.getJDA().getGuildById(guildId);
        if (guild == null) {
            event.reply("Could not find the server.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        com.serverbot.commands.config.SuspiciousNotifyCommand.PagedListResult result =
                com.serverbot.commands.config.SuspiciousNotifyCommand.buildPage(guild, settings, page);

        event.editMessageEmbeds(result.embed())
                .setComponents(result.navRow() != null
                        ? java.util.List.of(result.navRow())
                        : java.util.Collections.emptyList())
                .queue();
    }

    // ── Masterlist view pagination ──────────────────────────────────────────────

    private void handleMasterlistPage(ButtonInteractionEvent event, String componentId) {
        // Format: slv:page:<pageNumber>
        String[] parts = componentId.split(":");
        if (parts.length != 3) { event.deferEdit().queue(); return; }
        int page;
        try { page = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { event.deferEdit().queue(); return; }
        if (page <= 0) { event.deferEdit().queue(); return; } // disabled button

        // Only bot owners may page through the masterlist
        BotConfig config = ServerBot.getConfigManager().getConfig();
        if (!config.getAllOwnerIds().contains(event.getUser().getId())) {
            event.reply("Only bot owners can navigate this list.").setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();
        java.util.Map<String, java.util.Map<String, Object>> suspiciousUsers = storage.getAllSuspiciousUsers();

        SuspiciousListCommand.PagedViewResult result =
                SuspiciousListCommand.buildViewPage(event.getJDA(), suspiciousUsers, page);

        net.dv8tion.jda.api.components.buttons.Button shareBtn =
                net.dv8tion.jda.api.components.buttons.Button
                        .secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share");

        if (result.navRow() != null) {
            event.editMessageEmbeds(result.embed())
                    .setComponents(result.navRow(),
                            net.dv8tion.jda.api.components.actionrow.ActionRow.of(shareBtn))
                    .queue();
        } else {
            event.editMessageEmbeds(result.embed())
                    .setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(shareBtn))
                    .queue();
        }
    }

    // ── Masterlist refresh ──────────────────────────────────────────────────────

    private void handleMasterlistRefresh(ButtonInteractionEvent event, String componentId) {
        // Format: slv:refresh:<page>
        String[] parts = componentId.split(":");
        if (parts.length < 3) { event.deferEdit().queue(); return; }
        int page;
        try { page = Integer.parseInt(parts[2]); } catch (NumberFormatException e) { event.deferEdit().queue(); return; }

        BotConfig config = ServerBot.getConfigManager().getConfig();
        if (!config.getAllOwnerIds().contains(event.getUser().getId())) {
            event.reply("Only bot owners can refresh this list.").setEphemeral(true).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();
        java.util.Map<String, java.util.Map<String, Object>> suspiciousUsers = storage.getAllSuspiciousUsers();

        SuspiciousListCommand.PagedViewResult result =
                SuspiciousListCommand.buildViewPage(event.getJDA(), suspiciousUsers, page);

        net.dv8tion.jda.api.components.buttons.Button shareBtn =
                net.dv8tion.jda.api.components.buttons.Button
                        .secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share");

        if (result.navRow() != null) {
            event.editMessageEmbeds(result.embed())
                    .setComponents(result.navRow(),
                            net.dv8tion.jda.api.components.actionrow.ActionRow.of(shareBtn))
                    .queue();
        } else {
            event.editMessageEmbeds(result.embed())
                    .setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(shareBtn))
                    .queue();
        }
    }

    // ── Review mode actions ─────────────────────────────────────────────────────

    private void handleReviewAction(ButtonInteractionEvent event, String componentId) {
        BotConfig config = ServerBot.getConfigManager().getConfig();
        if (!config.getAllOwnerIds().contains(event.getUser().getId())) {
            event.reply("Only bot owners can perform review actions.").setEphemeral(true).queue();
            return;
        }

        // slv:review:done
        if (componentId.equals("slv:review:done")) {
            event.editMessageEmbeds(com.serverbot.utils.EmbedUtils.createSuccessEmbed(
                    "Review Complete", "You have finished the review session. Use `/suspiciouslist review` to start a new session."))
                    .setComponents(java.util.Collections.emptyList())
                    .queue();
            return;
        }

        // slv:review:<action>:<userId>
        String[] parts = componentId.split(":");
        if (parts.length < 4) { event.deferEdit().queue(); return; }
        String action = parts[2];
        String userId = parts[3];

        FileStorageManager storage = ServerBot.getStorageManager();

        switch (action) {
            case "validate" -> {
                if (!storage.isUserSuspicious(userId)) {
                    event.reply("This user is no longer on the suspicious list.").setEphemeral(true).queue();
                    return;
                }
                storage.validateSuspiciousUser(userId, event.getUser().getId());
                logger.info("Bot owner {} validated suspicious user {} via review", event.getUser().getId(), userId);
                advanceReview(event, userId, "✅ Validated", "User `" + userId + "` has been **validated** as a confirmed threat.", storage);
            }
            case "safe" -> {
                if (!storage.isUserSuspicious(userId)) {
                    event.reply("This user is no longer on the suspicious list.").setEphemeral(true).queue();
                    return;
                }
                java.util.List<String> autoBannedGuilds = storage.getAutoBannedGuilds(userId);
                storage.removeUserFromSuspiciousList(userId);
                logger.info("Bot owner {} marked suspicious user {} as safe via review", event.getUser().getId(), userId);
                advanceReview(event, userId, "🟢 Marked Safe", "User `" + userId + "` has been **removed** from the suspicious list (marked as safe).", storage);
            }
            case "skip" -> {
                logger.debug("Bot owner {} skipped suspicious user {} in review", event.getUser().getId(), userId);
                advanceReview(event, userId, null, null, storage);
            }
            default -> event.deferEdit().queue();
        }
    }

    /**
     * After taking action on userId, find the next pending entry and show it.
     * If none remain, show a completion message.
     */
    private void advanceReview(ButtonInteractionEvent event, String processedUserId,
                               String actionTitle, String actionDetail, FileStorageManager storage) {
        // Get fresh list of pending entries, excluding the one just processed
        java.util.Map<String, java.util.Map<String, Object>> all = storage.getAllSuspiciousUsers();
        java.util.List<java.util.Map.Entry<String, java.util.Map<String, Object>>> pending = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.Map<String, Object>> entry : all.entrySet()) {
            if (!entry.getKey().equals(processedUserId) && !Boolean.TRUE.equals(entry.getValue().get("validated"))) {
                pending.add(entry);
            }
        }

        if (pending.isEmpty()) {
            String completionMsg = (actionDetail != null ? actionDetail + "\n\n" : "") +
                    "✅ **Review complete!** No more pending entries.";
            event.editMessageEmbeds(com.serverbot.utils.EmbedUtils.createSuccessEmbed(
                    actionTitle != null ? actionTitle + " — Review Complete" : "Review Complete",
                    completionMsg))
                    .setComponents(java.util.Collections.emptyList())
                    .queue();
            return;
        }

        // Show the next pending entry
        java.util.Map.Entry<String, java.util.Map<String, Object>> next = pending.get(0);
        net.dv8tion.jda.api.entities.MessageEmbed nextEmbed =
                com.serverbot.commands.utility.SuspiciousListCommand.buildReviewEmbed(event.getJDA(), next, pending.size());
        net.dv8tion.jda.api.components.actionrow.ActionRow nextRow =
                com.serverbot.commands.utility.SuspiciousListCommand.buildReviewActionRow(next.getKey());

        event.editMessageEmbeds(nextEmbed).setComponents(nextRow).queue();
    }
}