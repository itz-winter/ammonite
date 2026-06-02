package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.models.SuspicionLevel;
import com.serverbot.utils.BotConfig;
import com.serverbot.utils.DmUtils;
import com.serverbot.utils.SafeRestAction;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detects and reports suspicious Discord accounts joining servers
 */
public class SuspiciousAccountListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousAccountListener.class);

    // Thresholds for detecting suspicious accounts
    private static final long NEW_ACCOUNT_DAYS = 7; // Account created within 7 days
    private static final long SAME_DAY_HOURS = 24; // Joined server within 24 hours of account creation
    private static final long VERY_NEW_ACCOUNT_DAYS = 1; // Account created within 1 day

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        User user = member.getUser();
        Guild guild = event.getGuild();

        // Skip bots
        if (user.isBot()) {
            return;
        }

        // Check if account detection is enabled for this guild
        if (!isDetectionEnabled(guild.getId())) {
            return;
        }

        // Analyze the account
        SuspiciousAccountReport report = analyzeAccount(user, guild);

        if (report.isSuspicious()) {
            logger.info("Suspicious account detected: {} in guild {}: {}",
                    user.getName(), guild.getName(), report.getReasonsString());

            // Send alerts
            sendGuildOwnerAlert(guild, user, report);
            sendBotOwnerAlert(guild, user, report);
            sendNotifyListAlerts(guild, user, report);
        }
    }

    /**
     * Analyzes a user account for suspicious patterns
     */
    private SuspiciousAccountReport analyzeAccount(User user, Guild guild) {
        SuspiciousAccountReport report = new SuspiciousAccountReport(user, guild);

        OffsetDateTime accountCreated = user.getTimeCreated();
        OffsetDateTime now = OffsetDateTime.now();

        // Calculate account age
        long accountAgeDays = ChronoUnit.DAYS.between(accountCreated, now);
        long accountAgeHours = ChronoUnit.HOURS.between(accountCreated, now);

        // Track blatant bot indicators
        boolean veryNewAccount = false;
        boolean noAvatar = false;
        boolean suspiciousName = false;

        // Check 1: Very new account (created within 1 day)
        if (accountAgeDays < VERY_NEW_ACCOUNT_DAYS) {
            report.addReason(String.format("Account created %d hours ago (very new account)", accountAgeHours));
            veryNewAccount = true;
        }
        // Check 2: New account (created within 7 days)
        else if (accountAgeDays < NEW_ACCOUNT_DAYS) {
            report.addReason(String.format("Account created %d days ago (new account)", accountAgeDays));
        }

        // Check 3: Joined server same day as account creation
        if (accountAgeHours < SAME_DAY_HOURS) {
            report.addReason(String.format("Joined server within %d hours of account creation", accountAgeHours));
        }

        // Check 4: No profile picture (default avatar)
        if (user.getAvatarUrl() == null) {
            report.addReason("No custom profile picture (using default avatar)");
            noAvatar = true;
        }

        // Check 5: Suspicious username patterns
        String username = user.getName().toLowerCase();
        if (username.matches(".*discord.*")) {
            report.addReason("Username contains 'discord' (potential impersonation)");
            suspiciousName = true;
        }
        if (username.matches(".*\\d{4,}.*")) {
            report.addReason("Username contains 4+ consecutive digits");
            suspiciousName = true;
        }
        if (username.length() < 3) {
            report.addReason("Very short username (less than 3 characters)");
            suspiciousName = true;
        }

        // Check for blatant bot: very new + no avatar + suspicious name
        if (veryNewAccount && noAvatar && suspiciousName) {
            report.markAsBlatantBot();
        }

        return report;
    }

    /**
     * Builds the alert embed sent to guild owners and notify-list members.
     */
    private MessageEmbed buildGuildAlertEmbed(Guild guild, User suspiciousUser,
            SuspiciousAccountReport report, SuspicionLevel level) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(level.getColor())
                .setTitle(level.getEmoji() + " " + level.getDisplayName() + " Account Detected")
                .setDescription("A potentially suspicious account has joined your server.")
                .addField("User", suspiciousUser.getAsMention() + " (`" + suspiciousUser.getName() + "`)", false)
                .addField("User ID", suspiciousUser.getId(), true)
                .addField("User Link",
                        "[Click here](https://discord.com/users/" + suspiciousUser.getId() + ")", true)
                .addField("Server", guild.getName() + " (`" + guild.getId() + "`)", false)
                .addField("Suspicion Level", level.toString(), true)
                .addField("Flags Detected", String.valueOf(report.getFlagCount()), true)
                .addField("Account Created", String.format("<t:%d:F> (<t:%d:R>)",
                        suspiciousUser.getTimeCreated().toEpochSecond(),
                        suspiciousUser.getTimeCreated().toEpochSecond()), false)
                .addField("Suspicious Indicators", report.getReasonsFormatted(), false)
                .setThumbnail(suspiciousUser.getEffectiveAvatarUrl())
                .setFooter("Suspicious Account Detection", guild.getIconUrl())
                .setTimestamp(OffsetDateTime.now());
        String action = report.getEffectiveAction();
        if (action != null && level.hasRecommendedAction()) {
            embed.addField("📋 Recommended Action", action, false);
        }
        return embed.build();
    }

    /**
     * Sends an alert to the guild owner
     */
    private void sendGuildOwnerAlert(Guild guild, User suspiciousUser, SuspiciousAccountReport report) {
        SuspicionLevel level = report.getSuspicionLevel();
        if (level == null)
            return;

        MessageEmbed embed = buildGuildAlertEmbed(guild, suspiciousUser, report, level);

        SafeRestAction.queue(
                guild.retrieveOwner(),
                "retrieve guild owner for suspicious account alert",
                owner -> DmUtils.sendDm(guild, owner.getUser(), embed,
                        v -> logger.debug("Sent suspicious account alert to guild owner {} for user {}",
                                owner.getUser().getName(), suspiciousUser.getName()),
                        error -> logger.warn("Failed to send suspicious account alert to guild owner: {}",
                                error.getMessage())));
    }

    /**
     * Sends alerts to all explicit notify-list users and members with notify roles,
     * excluding the guild owner (already notified by sendGuildOwnerAlert).
     */
    private void sendNotifyListAlerts(Guild guild, User suspiciousUser, SuspiciousAccountReport report) {
        SuspicionLevel level = report.getSuspicionLevel();
        if (level == null) return;

        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());

        Set<String> recipientIds = new LinkedHashSet<>();

        // Explicit notify users
        Object usersObj = settings.get("suspiciousAccountNotifyUsers");
        if (usersObj instanceof List<?>) {
            for (Object obj : (List<?>) usersObj) {
                if (obj instanceof String s) recipientIds.add(s);
            }
        }

        // Role-based notify users — resolved at alert time
        Object rolesObj = settings.get("suspiciousAccountNotifyRoles");
        if (rolesObj instanceof List<?>) {
            for (Object obj : (List<?>) rolesObj) {
                if (obj instanceof String roleId) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        for (Member m : guild.getMembersWithRoles(role)) {
                            recipientIds.add(m.getId());
                        }
                    }
                }
            }
        }

        // Guild owner already gets their own dedicated DM
        recipientIds.remove(guild.getOwnerId());

        if (recipientIds.isEmpty()) return;

        MessageEmbed embed = buildGuildAlertEmbed(guild, suspiciousUser, report, level);

        for (String userId : recipientIds) {
            SafeRestAction.queue(
                    guild.retrieveMemberById(userId),
                    "retrieve notify-list member for suspicious account alert",
                    member -> DmUtils.sendDm(guild, member.getUser(), embed,
                            v -> logger.debug("Sent suspicious account alert to notify user {} for user {}",
                                    member.getUser().getName(), suspiciousUser.getName()),
                            error -> logger.warn("Failed to send suspicious account alert to notify user {}: {}",
                                    error.getMessage())));
        }
    }

    /**
     * Sends an alert to the bot owner(s) with buttons to mark as suspicious or
     * ignore
     */
    private void sendBotOwnerAlert(Guild guild, User suspiciousUser, SuspiciousAccountReport report) {
        SuspicionLevel level = report.getSuspicionLevel();
        if (level == null)
            return;

        BotConfig config = ServerBot.getConfigManager().getConfig();
        List<String> botOwnerIds = config.getAllOwnerIds();

        // Create detection data to store
        Map<String, Object> detectionData = new HashMap<>();
        detectionData.put("guildId", guild.getId());
        detectionData.put("guildName", guild.getName());
        detectionData.put("reasons", report.getReasons());
        detectionData.put("detectedAt", System.currentTimeMillis());
        detectionData.put("suspicionLevel", level.name());
        detectionData.put("flagCount", report.getFlagCount());
        detectionData.put("isBlatantBot", report.isBlatantBot());

        for (String ownerId : botOwnerIds) {
            SafeRestAction.queue(
                    ServerBot.getJda().retrieveUserById(ownerId),
                    "retrieve bot owner for suspicious account alert",
                    botOwner -> {
                        EmbedBuilder embed = new EmbedBuilder()
                                .setColor(level.getColor())
                                .setTitle(level.getEmoji() + " " + level.getDisplayName() + " Account Alert")
                                .setDescription("A potentially suspicious account was detected.")
                                .addField("User",
                                        suspiciousUser.getAsMention() + " (`" + suspiciousUser.getName() + "`)", false)
                                .addField("User ID", suspiciousUser.getId(), true)
                                .addField("User Link",
                                        "[Click here](https://discord.com/users/" + suspiciousUser.getId() + ")", true)
                                .addField("Suspicion Level", level.toString(), true)
                                .addField("Flags Detected", String.valueOf(report.getFlagCount()), true)
                                .addField("Server", guild.getName() + " (`" + guild.getId() + "`)", false)
                                .addField("Server Link",
                                        "[Click here](https://discord.com/channels/" + guild.getId() + ")", true)
                                .addField("Guild Owner",
                                        guild.getOwner() != null ? guild.getOwner().getAsMention() : "Unknown", true)
                                .addField("Account Created", String.format("<t:%d:F> (<t:%d:R>)",
                                        suspiciousUser.getTimeCreated().toEpochSecond(),
                                        suspiciousUser.getTimeCreated().toEpochSecond()), false)
                                .addField("Member Count", String.valueOf(guild.getMemberCount()), true)
                                .addField("Suspicious Indicators", report.getReasonsFormatted(), false)
                                .setThumbnail(suspiciousUser.getEffectiveAvatarUrl())
                                .setFooter("Suspicious Account Detection System",
                                        ServerBot.getJda().getSelfUser().getEffectiveAvatarUrl())
                                .setTimestamp(OffsetDateTime.now());

                        // Add recommended action only for levels that have one (not LOW_SUSPICION)
                        String action = report.getEffectiveAction();
                        if (action != null && level.hasRecommendedAction()) {
                            embed.addField("📋 Suggested Action", action, false);
                        }

                        // Create buttons for managing the report
                        Button markSuspicious = Button.danger(
                                "suspicious_mark:" + suspiciousUser.getId() + ":" + guild.getId(),
                                "🚨 Mark as Suspicious");
                        Button addNote = Button.primary(
                                "suspicious_addnote:" + suspiciousUser.getId() + ":" + guild.getId(),
                                "📝 Add Note");
                        Button setAction = Button.primary(
                                "suspicious_setaction:" + suspiciousUser.getId() + ":" + guild.getId(),
                                "⚡ Set Action");
                        Button viewDetails = Button.secondary(
                                "suspicious_view:" + suspiciousUser.getId(),
                                "👁️ View");
                        Button ignore = Button.secondary(
                                "suspicious_ignore:" + suspiciousUser.getId() + ":" + guild.getId(),
                                "✓ Ignore");

                        SafeRestAction.queue(
                                botOwner.openPrivateChannel(),
                                "open DM channel with bot owner",
                                channel -> SafeRestAction.queue(
                                        channel.sendMessageEmbeds(embed.build())
                                                .setComponents(ActionRow.of(markSuspicious, addNote, setAction,
                                                        viewDetails, ignore)),
                                        "send suspicious account alert to bot owner",
                                        success -> logger.debug(
                                                "Sent suspicious account alert to bot owner {} for user {} in guild {}",
                                                botOwner.getName(), suspiciousUser.getName(), guild.getName()),
                                        error -> logger.warn(
                                                "Failed to send suspicious account alert to bot owner {}: {}",
                                                botOwner.getName(), error.getMessage())));
                    });
        }
    }

    /**
     * Checks if suspicious account detection is enabled for a guild
     */
    private boolean isDetectionEnabled(String guildId) {
        try {
            var settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            Boolean enabled = (Boolean) settings.get("suspiciousAccountDetection");
            return enabled != null ? enabled : true; // Enabled by default
        } catch (Exception e) {
            logger.error("Error checking suspicious account detection setting: {}", e.getMessage());
            return true; // Default to enabled on error
        }
    }

    /**
     * Holds information about a suspicious account analysis
     */
    public static class SuspiciousAccountReport {
        private final User user;
        private final Guild guild;
        private final List<String> reasons;
        private boolean isBlatantBot;
        private String note;
        private String suggestedAction;

        public SuspiciousAccountReport(User user, Guild guild) {
            this.user = user;
            this.guild = guild;
            this.reasons = new ArrayList<>();
            this.isBlatantBot = false;
            this.note = null;
            this.suggestedAction = null;
        }

        public void addReason(String reason) {
            reasons.add(reason);
        }

        public void markAsBlatantBot() {
            this.isBlatantBot = true;
        }

        public boolean isBlatantBot() {
            return isBlatantBot;
        }

        public boolean isSuspicious() {
            return !reasons.isEmpty();
        }

        /**
         * Gets the suspicion level based on the number of flagged qualities.
         */
        public SuspicionLevel getSuspicionLevel() {
            if (!isSuspicious()) {
                return null;
            }
            return SuspicionLevel.fromFlagCount(reasons.size(), isBlatantBot);
        }

        public String getReasonsString() {
            return String.join(", ", reasons);
        }

        public String getReasonsFormatted() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < reasons.size(); i++) {
                sb.append("• ").append(reasons.get(i));
                if (i < reasons.size() - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        public List<String> getReasons() {
            return reasons;
        }

        public int getFlagCount() {
            return reasons.size();
        }

        public User getUser() {
            return user;
        }

        public Guild getGuild() {
            return guild;
        }

        public String getNote() {
            return note;
        }

        public void setNote(String note) {
            this.note = note;
        }

        public String getSuggestedAction() {
            return suggestedAction;
        }

        public void setSuggestedAction(String suggestedAction) {
            this.suggestedAction = suggestedAction;
        }

        /**
         * Gets the effective recommended action - either the custom one or the default
         * for the level.
         */
        public String getEffectiveAction() {
            if (suggestedAction != null && !suggestedAction.isEmpty()) {
                return suggestedAction;
            }
            SuspicionLevel level = getSuspicionLevel();
            return level != null ? level.getDefaultAction() : null;
        }
    }
}
