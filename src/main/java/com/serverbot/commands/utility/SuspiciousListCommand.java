package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.BotConfig;
import com.serverbot.utils.DmUtils;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Bot owner command for managing the global suspicious users masterlist
 */
public class SuspiciousListCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousListCommand.class);

    @Override
    public String getName() {
        return "suspiciouslist";
    }

    @Override
    public String getDescription() {
        return "Manage the global suspicious users masterlist (Bot Owner Only)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false; // We check bot owner manually
    }

    @Override
    public boolean isGuildOnly() {
        return false; // Can be used in DMs
    }

    public static CommandData getCommandData() {
        return Commands.slash("suspiciouslist", "Manage the global suspicious users masterlist (Bot Owner Only)")
                .addSubcommands(
                        new SubcommandData("view", "View all users on the suspicious masterlist"),
                        new SubcommandData("add", "Add a user to the suspicious masterlist")
                                .addOption(OptionType.STRING, "userid", "The user ID to add", true)
                                .addOption(OptionType.STRING, "reason", "Reason for adding", true),
                        new SubcommandData("remove", "Remove a user from the suspicious masterlist")
                                .addOption(OptionType.STRING, "userid", "The user ID to remove", true),
                        new SubcommandData("check", "Check if a user is on the suspicious masterlist")
                                .addOption(OptionType.STRING, "userid", "The user ID to check", true),
                        new SubcommandData("clear", "Clear ALL users from the suspicious masterlist"),
                        new SubcommandData("validate", "Validate/verify a reported suspicious user")
                                .addOption(OptionType.STRING, "userid", "The user ID to validate", true),
                        new SubcommandData("stats", "View suspicious list statistics"),
                        new SubcommandData("ban", "Mass-ban suspicious users from this server (requires Ban Members)")
                                .addOptions(new OptionData(OptionType.STRING, "filter",
                                        "Which users to ban by classification", true)
                                        .addChoices(
                                                new Command.Choice("Validated – confirmed threats", "validated"),
                                                new Command.Choice("Pending – unverified reports", "pending"),
                                                new Command.Choice("All – validated + pending", "all"))),
                        new SubcommandData("scan",
                                "Scan the masterlist for deleted/suspended Discord accounts and remove them"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Please specify a subcommand.").setEphemeral(true).queue();
            return;
        }

        // /suspiciouslist ban is for server admins, not bot owners
        if ("ban".equals(subcommand)) {
            handleBan(event);
            return;
        }

        // All other subcommands require bot owner
        BotConfig config = ServerBot.getConfigManager().getConfig();
        List<String> botOwners = config.getAllOwnerIds();

        if (!botOwners.contains(event.getUser().getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Access Denied",
                    "This command is only available to bot owners.")).setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "view" -> handleView(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "check" -> handleCheck(event);
            case "clear" -> handleClear(event);
            case "validate" -> handleValidate(event);
            case "stats" -> handleStats(event);
            case "scan" -> handleScan(event);
            default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
        }
    }

    private void handleView(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Map<String, Object>> suspiciousUsers = storage.getAllSuspiciousUsers();

        if (suspiciousUsers.isEmpty()) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "Suspicious Users Masterlist",
                    "The masterlist is currently empty.")).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        int validated = 0;

        for (Map.Entry<String, Map<String, Object>> entry : suspiciousUsers.entrySet()) {
            count++;
            String userId = entry.getKey();
            Map<String, Object> data = entry.getValue();

            String reason = (String) data.getOrDefault("reason", "No reason provided");
            Boolean isValidated = (Boolean) data.get("validated");
            String status = (isValidated != null && isValidated) ? "✅" : "⚠️";
            if (isValidated != null && isValidated)
                validated++;

            // Try to get username
            String userDisplay = userId;
            try {
                User user = event.getJDA().retrieveUserById(userId).complete();
                if (user != null) {
                    userDisplay = user.getName() + " (`" + userId + "`)";
                }
            } catch (Exception ignored) {
            }

            sb.append(String.format("%d. %s %s\n   └ %s\n", count, status, userDisplay, truncate(reason, 50)));

            if (sb.length() > 3500) {
                sb.append("\n*... and more (list truncated)*");
                break;
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("🚨 Suspicious Users Masterlist")
                .setDescription(sb.toString())
                .addField("Total", String.valueOf(suspiciousUsers.size()), true)
                .addField("Validated", String.valueOf(validated), true)
                .addField("Pending", String.valueOf(suspiciousUsers.size() - validated), true)
                .setFooter("✅ = Validated | ⚠️ = Pending validation")
                .setTimestamp(Instant.now());

        Button shareBtn = Button.secondary("share_req:" + event.getUser().getId(), "📤 Share");
        event.getHook().sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(shareBtn))
                .queue();
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        String userId = event.getOption("userid").getAsString().trim();
        String reason = event.getOption("reason").getAsString();

        if (!userId.matches("\\d{17,19}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User ID",
                    "User IDs should be 17-19 digit numbers.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        FileStorageManager storage = ServerBot.getStorageManager();

        // Check if already on list
        if (storage.isUserSuspicious(userId)) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createWarningEmbed(
                    "Already Listed",
                    "User `" + userId + "` is already on the suspicious masterlist.")).queue();
            return;
        }

        // Add to masterlist
        Map<String, Object> detectionData = new HashMap<>();
        detectionData.put("addedManually", true);
        detectionData.put("addedBy", event.getUser().getId());

        storage.markUserAsSuspicious(userId, event.getUser().getId(), reason, detectionData);
        storage.validateSuspiciousUser(userId, event.getUser().getId()); // Auto-validate since bot owner added

        // Try to get user info
        String userInfo = "`" + userId + "`";
        try {
            User user = event.getJDA().retrieveUserById(userId).complete();
            if (user != null) {
                userInfo = user.getName() + " (" + userInfo + ")";
            }
        } catch (Exception ignored) {
        }

        event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "User Added to Masterlist",
                "**User:** " + userInfo + "\n" +
                        "**Reason:** " + reason + "\n\n" +
                        "This user has been validated and all servers with this user will be notified."))
                .queue();

        // Notify all servers where this user is a member
        notifyServersAboutSuspiciousUser(event, userId, reason);

        logger.info("Bot owner {} added user {} to suspicious masterlist: {}",
                event.getUser().getId(), userId, reason);
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        String userId = event.getOption("userid").getAsString().trim();

        if (!userId.matches("\\d{17,19}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User ID",
                    "User IDs should be 17-19 digit numbers.")).setEphemeral(true).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();

        if (!storage.isUserSuspicious(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Found",
                    "User `" + userId + "` is not on the suspicious masterlist.")).setEphemeral(true).queue();
            return;
        }

        // Collect guilds that auto-banned this user before we remove the entry
        List<String> autoBannedGuilds = storage.getAutoBannedGuilds(userId);

        storage.removeUserFromSuspiciousList(userId);

        int unbanned = unbanFromGuilds(event.getJDA(), userId, autoBannedGuilds);
        String extra = unbanned > 0
                ? "\n\nAutomatically unbanned from **" + unbanned + "** server(s) that had used auto-ban."
                : "";

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "User Removed",
                "User `" + userId + "` has been removed from the suspicious masterlist." + extra))
                .setEphemeral(true).queue();

        logger.info("Bot owner {} removed user {} from suspicious masterlist",
                event.getUser().getId(), userId);
    }

    private void handleCheck(SlashCommandInteractionEvent event) {
        String userId = event.getOption("userid").getAsString().trim();

        if (!userId.matches("\\d{17,19}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User ID",
                    "User IDs should be 17-19 digit numbers.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        FileStorageManager storage = ServerBot.getStorageManager();

        if (!storage.isUserSuspicious(userId)) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "User Not Listed",
                    "User `" + userId + "` is **not** on the suspicious masterlist.")).queue();
            return;
        }

        Map<String, Object> data = storage.getSuspiciousUserData(userId);
        Boolean validated = (Boolean) data.get("validated");
        String reason = (String) data.getOrDefault("reason", "No reason provided");
        String markedBy = (String) data.get("markedBy");
        Long markedAt = ((Number) data.get("markedAt")).longValue();

        // Try to get user info
        String userInfo = "`" + userId + "`";
        String avatarUrl = null;
        try {
            User user = event.getJDA().retrieveUserById(userId).complete();
            if (user != null) {
                userInfo = user.getName() + " (" + userInfo + ")";
                avatarUrl = user.getEffectiveAvatarUrl();
            }
        } catch (Exception ignored) {
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("🚨 Suspicious User Found")
                .addField("User", userInfo, false)
                .addField("Reason", reason, false)
                .addField("Status",
                        validated != null && validated ? "✅ Validated"
                                : "⚠️ Pending Validation",
                        true)
                .addField("Marked At", "<t:" + (markedAt / 1000) + ":F>", true);

        if (markedBy != null) {
            embed.addField("Marked By", "<@" + markedBy + ">", true);
        }

        if (avatarUrl != null) {
            embed.setThumbnail(avatarUrl);
        }

        // Create action buttons
        Button viewBtn = Button.secondary("suspicious_view:" + userId, "👁️ View Full Details");
        Button validateBtn = validated != null && validated
                ? Button.success("suspicious_validate:" + userId, "✅ Validated").asDisabled()
                : Button.success("suspicious_validate:" + userId, "✅ Validate");
        Button invalidateBtn = Button.danger("suspicious_invalidate:" + userId, "❌ Invalidate");
        Button removeBtn = Button.danger("suspicious_remove:" + userId, "🗑️ Remove");

        event.getHook().sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(viewBtn, validateBtn, invalidateBtn, removeBtn))
                .queue();
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        FileStorageManager storage = ServerBot.getStorageManager();
        int count = storage.getSuspiciousUserCount();

        if (count == 0) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Masterlist Empty",
                    "The suspicious users masterlist is already empty.")).setEphemeral(true).queue();
            return;
        }

        storage.clearAllSuspiciousUsers();

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Masterlist Cleared",
                "Removed **" + count + "** users from the suspicious masterlist.")).setEphemeral(true).queue();

        logger.info("Bot owner {} cleared the suspicious masterlist ({} users removed)",
                event.getUser().getId(), count);
    }

    private void handleValidate(SlashCommandInteractionEvent event) {
        String userId = event.getOption("userid").getAsString().trim();

        if (!userId.matches("\\d{17,19}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User ID",
                    "User IDs should be 17-19 digit numbers.")).setEphemeral(true).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();

        if (!storage.isUserSuspicious(userId)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Found",
                    "User `" + userId + "` is not on the suspicious masterlist.")).setEphemeral(true).queue();
            return;
        }

        if (storage.isSuspiciousUserValidated(userId)) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Already Validated",
                    "User `" + userId + "` has already been validated.")).setEphemeral(true).queue();
            return;
        }

        storage.validateSuspiciousUser(userId, event.getUser().getId());

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "User Validated",
                "User `" + userId + "` has been validated as suspicious.\n" +
                        "All servers with this user will now be notified."))
                .setEphemeral(true).queue();

        // Notify servers
        Map<String, Object> data = storage.getSuspiciousUserData(userId);
        String reason = (String) data.getOrDefault("reason", "Marked as suspicious by bot owner");
        notifyServersAboutSuspiciousUser(event, userId, reason);

        logger.info("Bot owner {} validated suspicious user {}", event.getUser().getId(), userId);
    }

    private void handleStats(SlashCommandInteractionEvent event) {
        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Map<String, Object>> allUsers = storage.getAllSuspiciousUsers();

        int total = allUsers.size();
        int validated = 0;
        int manuallyAdded = 0;
        int fromReports = 0;

        for (Map<String, Object> data : allUsers.values()) {
            Boolean isValidated = (Boolean) data.get("validated");
            if (isValidated != null && isValidated)
                validated++;

            Map<String, Object> detectionData = (Map<String, Object>) data.get("detectionData");
            if (detectionData != null) {
                Boolean addedManually = (Boolean) detectionData.get("addedManually");
                Boolean fromReport = (Boolean) detectionData.get("fromReport");
                if (addedManually != null && addedManually)
                    manuallyAdded++;
                if (fromReport != null && fromReport)
                    fromReports++;
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.BLUE)
                .setTitle("📊 Suspicious Masterlist Statistics")
                .addField("Total Users", String.valueOf(total), true)
                .addField("Validated", String.valueOf(validated), true)
                .addField("Pending", String.valueOf(total - validated), true)
                .addField("Manually Added", String.valueOf(manuallyAdded), true)
                .addField("From Reports", String.valueOf(fromReports), true)
                .addField("Auto-Detected", String.valueOf(total - manuallyAdded - fromReports), true)
                .setFooter("Suspicious Users Masterlist")
                .setTimestamp(Instant.now());

        Button shareBtn = Button.secondary("share_req:" + event.getUser().getId(), "📤 Share");
        event.replyEmbeds(embed.build())
                .setComponents(ActionRow.of(shareBtn))
                .setEphemeral(true).queue();
    }

    //  /suspiciouslist scan 

    private void handleScan(SlashCommandInteractionEvent event) {
        com.serverbot.services.SuspiciousCleanupService svc =
                com.serverbot.ServerBot.getSuspiciousCleanupService();

        if (svc == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Service Unavailable",
                    "The suspicious cleanup service is not running. Check bot logs."))
                    .setEphemeral(true).queue();
            return;
        }

        int listed = ServerBot.getStorageManager().getSuspiciousUserCount();
        if (listed == 0) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Nothing to Scan",
                    "The suspicious masterlist is currently empty."))
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        svc.runScan().whenComplete((result, ex) -> {
            if (ex != null) {
                logger.error("On-demand suspicious-list scan failed", ex);
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Scan Failed",
                        "An unexpected error occurred during the scan. Check the bot logs.")).queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔍 Suspicious List Scan Complete")
                    .setColor(result.removed() > 0 ? Color.ORANGE : Color.GREEN)
                    .setDescription(result.summary())
                    .addField("Entries Checked", String.valueOf(result.checked()), true)
                    .addField("Deleted Accounts Removed", String.valueOf(result.removed()), true)
                    .addField("Remaining on List",
                            String.valueOf(ServerBot.getStorageManager().getSuspiciousUserCount()), true)
                    .setFooter("Auto-scan runs every 20 minutes")
                    .setTimestamp(Instant.now());

            Button shareBtn = Button.secondary("share_req:" + event.getUser().getId(), "📤 Share");
            event.getHook().sendMessageEmbeds(embed.build())
                    .setComponents(ActionRow.of(shareBtn))
                    .queue();

            logger.info("Bot owner {} triggered on-demand scan: {}", event.getUser().getId(), result.summary());
        });
    }

    //  /suspiciouslist ban 

    private void handleBan(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This subcommand can only be used inside a server."))
                    .setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Permission",
                    "You need the **Ban Members** permission to use this subcommand."))
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Bot Permission",
                    "I need the **Ban Members** permission to execute bans in this server."))
                    .setEphemeral(true).queue();
            return;
        }

        String filter = event.getOption("filter").getAsString();
        event.deferReply(true).queue();

        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Map<String, Object>> allUsers = storage.getAllSuspiciousUsers();

        if (allUsers.isEmpty()) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "No Users", "The suspicious masterlist is currently empty.")).queue();
            return;
        }

        // Build list of matching user IDs
        List<String> targets = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : allUsers.entrySet()) {
            Boolean validated = (Boolean) entry.getValue().get("validated");
            boolean isValidated = validated != null && validated;
            boolean match = switch (filter) {
                case "validated" -> isValidated;
                case "pending"   -> !isValidated;
                default          -> true; // "all"
            };
            if (match) targets.add(entry.getKey());
        }

        if (targets.isEmpty()) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "No Matches",
                    "No users on the masterlist match the `" + filter + "` filter.")).queue();
            return;
        }

        String guildId = guild.getId();
        List<String> ownerIds = ServerBot.getConfigManager().getConfig().getAllOwnerIds();

        int banned = 0, alreadyBanned = 0, failed = 0;

        for (String userId : targets) {
            if (ownerIds.contains(userId)) continue; // never ban bot owners
            try {
                guild.ban(UserSnowflake.fromId(userId), 0, TimeUnit.SECONDS)
                        .reason("Suspicious Masterlist Auto-Ban [" + filter + "] by "
                                + event.getUser().getName())
                        .complete();
                storage.addAutoBanGuild(userId, guildId);
                banned++;
            } catch (net.dv8tion.jda.api.exceptions.ErrorResponseException e) {
                if (e.getErrorCode() == 10026) { // Unknown Ban — treat as already banned
                    storage.addAutoBanGuild(userId, guildId);
                    alreadyBanned++;
                } else {
                    failed++;
                    logger.warn("Failed to ban {} in guild {}: {}", userId, guildId, e.getMessage());
                }
            } catch (Exception e) {
                failed++;
                logger.warn("Failed to ban {} in guild {}: {}", userId, guildId, e.getMessage());
            }
        }

        StringBuilder desc = new StringBuilder()
                .append("**Filter:** `").append(filter).append("`\n")
                .append("**Targeted:** ").append(targets.size()).append(" user(s)\n\n")
                .append("✅ **Newly banned:** ").append(banned).append("\n")
                .append("⚠️ **Already banned:** ").append(alreadyBanned).append("\n");
        if (failed > 0) desc.append("❌ **Failed:** ").append(failed).append("\n");
        desc.append("\n*These users will be automatically unbanned if their")
            .append(" classification is revoked from the masterlist.*");

        event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "Mass-Ban Complete", desc.toString())).queue();

        logger.info("Server admin {} in guild {} mass-banned {} suspicious users (filter={}, failed={})",
                event.getUser().getId(), guildId, banned, filter, failed);
    }

    /**
     * Unbans {@code userId} from every guild in {@code guildIds} where the bot has
     * permission, returning the number of guilds where the unban succeeded.
     * Called when a user's suspicious classification is revoked (remove / invalidate).
     */
    public static int unbanFromGuilds(JDA jda, String userId, List<String> guildIds) {
        int count = 0;
        for (String guildId : guildIds) {
            Guild guild = jda.getGuildById(guildId);
            if (guild != null && guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
                try {
                    guild.unban(UserSnowflake.fromId(userId))
                            .reason("Suspicious classification revoked from masterlist")
                            .complete();
                    count++;
                } catch (Exception ignored) {
                    // User may not actually be banned there — safe to ignore
                }
            }
        }
        return count;
    }

    private void notifyServersAboutSuspiciousUser(SlashCommandInteractionEvent event, String userId, String reason) {
        try {
            User suspiciousUser = event.getJDA().retrieveUserById(userId).complete();
            if (suspiciousUser == null)
                return;

            // Check all guilds
            for (var guild : event.getJDA().getGuilds()) {
                try {
                    guild.retrieveMemberById(userId).queue(
                            member -> {
                                // User is in this guild - notify the guild owner
                                sendCrossServerNotification(guild, suspiciousUser, reason);
                            },
                            error -> {
                            } // User not in this guild, ignore
                    );
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to notify servers about suspicious user {}: {}", userId, e.getMessage());
        }
    }

    private void sendCrossServerNotification(net.dv8tion.jda.api.entities.Guild guild, User suspiciousUser,
            String reason) {
        guild.retrieveOwner().queue(owner -> {
            if (owner == null)
                return;

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("🚨 Cross-Server Suspicious User Alert")
                    .setDescription("A user in your server has been added to the global suspicious users masterlist.")
                    .addField("User", suspiciousUser.getAsMention() + " (`" + suspiciousUser.getName() + "`)", false)
                    .addField("User ID", suspiciousUser.getId(), true)
                    .addField("Your Server", guild.getName(), true)
                    .addField("Reason", reason, false)
                    .setThumbnail(suspiciousUser.getEffectiveAvatarUrl())
                    .setFooter("Cross-Server Suspicious User Alert System")
                    .setTimestamp(Instant.now());

            DmUtils.sendDm(guild, owner.getUser(), embed.build(),
                    v -> logger.debug("Sent cross-server alert to {} for guild {}",
                            owner.getUser().getName(), guild.getName()),
                    error -> logger.debug("Failed to DM guild owner {}", owner.getUser().getName()));
        });
    }

    private String truncate(String str, int maxLength) {
        if (str == null)
            return "";
        if (str.length() <= maxLength)
            return str;
        return str.substring(0, maxLength - 3) + "...";
    }
}
