package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the "📋 Report Bug" button that appears on unexpected command errors.
 *
 * <p>Button ID format: {@code err_rep:<userId>:<cmdName>:<exceptionSimpleName>}
 *
 * <p>Before showing the error embed, CommandListener stores the full report
 * context in {@link #pendingReportContext} so this listener can retrieve it
 * when the button is pressed.
 */
public class ErrorReportButtonListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ErrorReportButtonListener.class);

    /**
     * Temporary store for pending report contexts set by CommandListener.
     * Key = {@code "<userId>:<cmdName>:<exceptionSimpleName>"} (same suffix as button ID after "err_rep:")
     * Cleaned up once the user submits or the entry is superseded.
     */
    public static final Map<String, Map<String, Object>> pendingReportContext = new ConcurrentHashMap<>();

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String buttonId = event.getComponentId();
        if (!buttonId.startsWith("err_rep:")) return;

        // Parse: err_rep:<userId>:<cmdName>:<exceptionSimpleName>
        String[] parts = buttonId.split(":", 4);
        if (parts.length < 4) return;

        String ownerId    = parts[1];
        String cmdName    = parts[2];
        String errClass   = parts[3];
        String contextKey = ownerId + ":" + cmdName + ":" + errClass;
        String dedupKey   = cmdName + ":" + errClass;

        // Only the user who triggered the error can submit the report
        if (!event.getUser().getId().equals(ownerId)) {
            event.reply("Only the person who encountered this error can report it.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check if this error has already been reported before
        boolean alreadyKnown = ServerBot.getStorageManager().hasErrorReport(dedupKey);

        // Retrieve pending context (may be null if bot restarted)
        Map<String, Object> context = pendingReportContext.remove(contextKey);

        if (alreadyKnown && context == null) {
            // Already known, no context available — just acknowledge
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Already Reported",
                    "This error has already been reported and is being tracked. Thank you for your help!\n" +
                    "The report count has been incremented."))
                    .setEphemeral(true).queue();
            // Increment count even without full context
            Map<String, Object> minimal = new HashMap<>();
            minimal.put("dedupKey", dedupKey);
            ServerBot.getStorageManager().saveErrorReport(minimal);
            return;
        }

        if (alreadyKnown) {
            // Increment count on the existing report
            ServerBot.getStorageManager().saveErrorReport(context);
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Already Reported",
                    "This error is already being tracked. The report count has been incremented — " +
                    "thank you for confirming it happens to others too!"))
                    .setEphemeral(true).queue();
            logger.info("Error report count incremented for dedupKey={}", dedupKey);
            return;
        }

        // New report — save it
        if (context == null) {
            // Bot restarted; we can't get the full context. Save a minimal record.
            context = new HashMap<>();
            context.put("dedupKey", dedupKey);
            context.put("commandName", cmdName);
            context.put("errorType", errClass);
            context.put("errorMessage", "(context unavailable — bot may have restarted)");
            context.put("userId", ownerId);
        }
        context.put("dedupKey", dedupKey);

        ServerBot.getStorageManager().saveErrorReport(context);
        logger.info("New error report saved: dedupKey={}, user={}", dedupKey, ownerId);

        // DM all bot owners
        dmBotOwners(event.getUser(), context, event.getJDA());

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Report Submitted",
                "Thank you for reporting this error! The bot owner has been notified.\n\n" +
                "**Command:** `/" + cmdName + "`\n" +
                "**Error:** `" + errClass + "`\n\n" +
                "Your report helps us fix bugs faster. 🙏"))
                .setEphemeral(true).queue();
    }

    /**
     * Send a DM to all configured bot owners with the error details.
     */
    @SuppressWarnings("unchecked")
    private void dmBotOwners(User reporter, Map<String, Object> context, net.dv8tion.jda.api.JDA jda) {
        List<String> ownerIds = ServerBot.getConfigManager().getConfig().getAllOwnerIds();
        if (ownerIds.isEmpty()) {
            logger.warn("No bot owner IDs configured — cannot DM error report");
            return;
        }

        String cmdName    = String.valueOf(context.getOrDefault("commandName", "unknown"));
        String errType    = String.valueOf(context.getOrDefault("errorType",   "Unknown"));
        String errMessage = String.valueOf(context.getOrDefault("errorMessage","(none)"));
        String guildId    = String.valueOf(context.getOrDefault("guildId",     "DM"));
        String guildName  = String.valueOf(context.getOrDefault("guildName",   "Direct Message"));
        String channelId  = String.valueOf(context.getOrDefault("channelId",   "unknown"));
        String userId     = String.valueOf(context.getOrDefault("userId",      reporter.getId()));
        String userTag    = String.valueOf(context.getOrDefault("userTag",     reporter.getName()));
        String stackTrace = String.valueOf(context.getOrDefault("stackTrace",  "(unavailable)"));
        List<String> roles = (List<String>) context.getOrDefault("userRoles", new ArrayList<>());

        // Build DM embed
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(231, 76, 60))
                .setTitle("🐛 New Error Report")
                .addField("Command",     "`/" + cmdName + "`",               true)
                .addField("Error Type",  "`" + errType + "`",                true)
                .addField("Reporter",    "<@" + userId + "> (`" + userTag + "`)", true)
                .addField("Guild",       guildName + " (`" + guildId + "`)", true)
                .addField("Channel",     "<#" + channelId + ">",             true)
                .addField("Roles",       roles.isEmpty() ? "None" : String.join(", ", roles), false)
                .addField("Error Message", truncate(errMessage, 1000),       false)
                .addField("Stack Trace", "```\n" + truncate(stackTrace, 900) + "\n```", false)
                .setFooter("Reported by " + userTag + " | User ID: " + userId)
                .setTimestamp(Instant.now());

        // Server settings snapshot
        if (!guildId.equals("DM")) {
            try {
                Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
                if (!settings.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    settings.forEach((k, v) -> sb.append(k).append(": ").append(v).append("\n"));
                    eb.addField("Server Settings Snapshot", "```\n" + truncate(sb.toString(), 800) + "\n```", false);
                }
            } catch (Exception ignored) {}
        }

        net.dv8tion.jda.api.entities.MessageEmbed dmEmbed = eb.build();
        for (String ownerId : ownerIds) {
            try {
                jda.retrieveUserById(ownerId).queue(owner -> {
                    if (owner == null) return;
                    owner.openPrivateChannel().queue(channel -> {
                        channel.sendMessageEmbeds(dmEmbed).queue(
                                success -> logger.debug("Sent error report DM to owner {}", ownerId),
                                err -> logger.warn("Failed to send error report DM to owner {}: {}", ownerId, err.getMessage()));
                    }, err -> logger.warn("Cannot open DM with owner {}: {}", ownerId, err.getMessage()));
                }, err -> logger.warn("Cannot retrieve owner {}: {}", ownerId, err.getMessage()));
            } catch (Exception e) {
                logger.error("Error while sending error report DM to owner {}", ownerId, e);
            }
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "(null)";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }
}
