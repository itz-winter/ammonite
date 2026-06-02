package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.BotConfig;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.DmUtils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SuspiciousNotifyCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousNotifyCommand.class);
    private static final String NOTIFY_USERS_KEY = "suspiciousAccountNotifyUsers";
    private static final String NOTIFY_ROLES_KEY = "suspiciousAccountNotifyRoles";
    public  static final int PAGE_SIZE = 10;
    /** Button ID prefix used by the pagination buttons: {@code snl:page:<guildId>:<page>} */
    public  static final String PAGE_BTN = "snl:page";

    @Override
    public String getName() {
        return "suspiciousnotify";
    }

    @Override
    public String getDescription() {
        return "Manage suspicious account notification settings and submit reports";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }

    public static CommandData getCommandData() {
        return Commands.slash("suspiciousnotify", "Manage suspicious account notification settings and submit reports")
                .addSubcommands(
                        new SubcommandData("add", "Add a user to receive suspicious account notifications")
                                .addOption(OptionType.USER, "user", "The user to add to notifications", true),
                        new SubcommandData("remove", "Remove a user from suspicious account notifications")
                                .addOption(OptionType.USER, "user", "The user to remove from notifications", true),
                        new SubcommandData("addrole", "Add a role — all members with this role receive notifications")
                                .addOption(OptionType.ROLE, "role", "The role to add", true),
                        new SubcommandData("removerole", "Remove a role from the notification list")
                                .addOption(OptionType.ROLE, "role", "The role to remove", true),
                        new SubcommandData("list", "List all users and roles who receive suspicious account notifications")
                                .addOption(OptionType.INTEGER, "page", "Page number to view (default: 1)", false),
                        new SubcommandData("clear", "Clear all users and roles from the notification list"),
                        new SubcommandData("test", "Send a test notification to verify the system works"),
                        new SubcommandData("report", "Report a suspicious user to the bot owner")
                                .addOption(OptionType.STRING, "userid", "The user ID of the suspicious user", true)
                                .addOption(OptionType.STRING, "reason", "Reason for reporting this user", true)
                                .addOption(OptionType.STRING, "notes", "Additional notes or evidence", false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.reply("This command can only be used in a server.").setEphemeral(true).queue();
            return;
        }
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Please specify a subcommand.").setEphemeral(true).queue();
            return;
        }
        switch (subcommand) {
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "addrole" -> handleAddRole(event);
            case "removerole" -> handleRemoveRole(event);
            case "list" -> handleList(event);
            case "clear" -> handleClear(event);
            case "test" -> handleTest(event);
            case "report" -> handleReport(event);
            default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
        }
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("Could not find that member in this server.").setEphemeral(true).queue();
            return;
        }
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        List<String> notifyUsers = new ArrayList<>();
        Object existing = settings.get(NOTIFY_USERS_KEY);
        if (existing instanceof List<?>) {
            for (Object obj : (List<?>) existing) {
                if (obj instanceof String) {
                    notifyUsers.add((String) obj);
                }
            }
        }
        if (notifyUsers.contains(targetMember.getId())) {
            event.reply(targetMember.getAsMention() + " is already on the notification list.").setEphemeral(true)
                    .queue();
            return;
        }
        notifyUsers.add(targetMember.getId());
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_USERS_KEY, notifyUsers);
        logger.info("Added {} to suspicious account notifications in guild {}", targetMember.getUser().getName(),
                guild.getName());
        event.reply("Added " + targetMember.getAsMention() + " to suspicious account notifications.").setEphemeral(true)
                .queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member targetMember = event.getOption("user").getAsMember();
        if (targetMember == null) {
            event.reply("Could not find that member in this server.").setEphemeral(true).queue();
            return;
        }
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        List<String> notifyUsers = new ArrayList<>();
        Object existing = settings.get(NOTIFY_USERS_KEY);
        if (existing instanceof List<?>) {
            for (Object obj : (List<?>) existing) {
                if (obj instanceof String) {
                    notifyUsers.add((String) obj);
                }
            }
        }
        if (!notifyUsers.contains(targetMember.getId())) {
            event.reply(targetMember.getAsMention() + " is not on the notification list.").setEphemeral(true).queue();
            return;
        }
        notifyUsers.remove(targetMember.getId());
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_USERS_KEY, notifyUsers);
        logger.info("Removed {} from suspicious account notifications in guild {}", targetMember.getUser().getName(),
                guild.getName());
        event.reply("Removed " + targetMember.getAsMention() + " from suspicious account notifications.")
                .setEphemeral(true).queue();
    }

    private void handleList(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        OptionMapping pageOpt = event.getOption("page");
        int page = pageOpt != null ? Math.max(1, (int) pageOpt.getAsLong()) : 1;
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());
        PagedListResult result = buildPage(guild, settings, page);
        event.replyEmbeds(result.embed)
                .setComponents(result.navRow != null ? List.of(result.navRow) : Collections.emptyList())
                .setEphemeral(true).queue();
    }

    //  Static page builder (also called from SuspiciousAccountButtonListener) 

    public record PagedListResult(MessageEmbed embed, ActionRow navRow) {}

    /**
     * Builds one page of the notify list.
     * Sections: server owner (always page 1), roles, explicit users — each item
     * costs one slot from PAGE_SIZE.
     */
    public static PagedListResult buildPage(Guild guild, Map<String, Object> settings, int requestedPage) {
        //  Collect all line items 
        // Section 0: server owner (always shown; 1 fixed slot on every page 1)
        String ownerId = guild.getOwnerId();
        Member ownerMember = guild.getMemberById(ownerId);
        String ownerLine = ownerMember != null
                ? "🔑 " + ownerMember.getAsMention() + " (" + ownerMember.getUser().getName() + ") — *server owner*"
                : "🔑 <@" + ownerId + "> — *server owner*";

        // Section 1: notify roles
        List<String> roleLines = new ArrayList<>();
        Object rolesObj = settings.get(NOTIFY_ROLES_KEY);
        if (rolesObj instanceof List<?>) {
            for (Object obj : (List<?>) rolesObj) {
                if (obj instanceof String roleId) {
                    Role role = guild.getRoleById(roleId);
                    if (role != null) {
                        int mc = guild.getMembersWithRoles(role).size();
                        roleLines.add("🎭 " + role.getAsMention() + " — " + mc + " member(s)");
                    } else {
                        roleLines.add("🎭 ~~Unknown Role~~ (`" + roleId + "`)");
                    }
                }
            }
        }

        // Section 2: explicit users
        List<String> userLines = new ArrayList<>();
        Object usersObj = settings.get(NOTIFY_USERS_KEY);
        if (usersObj instanceof List<?>) {
            for (Object obj : (List<?>) usersObj) {
                if (obj instanceof String userId) {
                    Member m = guild.getMemberById(userId);
                    if (m != null) {
                        userLines.add("👤 " + m.getAsMention() + " (" + m.getUser().getName() + ")");
                    } else {
                        userLines.add("👤 Unknown User (ID: `" + userId + "`)");
                    }
                }
            }
        }

        //  Build flat ordered list: owner first, then roles, then explicit users 
        // Owner is always entry 0 — it appears on page 1 only, consuming 1 slot.
        // The owner entry is special: it's always included in the total count but
        // always displayed on page 1 ahead of the paginated rows.
        List<String> paginatedRows = new ArrayList<>(roleLines);
        paginatedRows.addAll(userLines);

        int totalPaginatedRows = paginatedRows.size();
        // Page 1 has PAGE_SIZE-1 paginated rows (one slot taken by owner); rest have PAGE_SIZE.
        int page1Capacity = PAGE_SIZE - 1;
        int totalPages;
        if (totalPaginatedRows <= page1Capacity) {
            totalPages = 1;
        } else {
            totalPages = 1 + (int) Math.ceil((totalPaginatedRows - page1Capacity) / (double) PAGE_SIZE);
        }
        totalPages = Math.max(1, totalPages);

        int page = Math.min(requestedPage, totalPages);

        // Slice the rows visible on this page
        List<String> pageRows;
        if (page == 1) {
            pageRows = paginatedRows.subList(0, Math.min(page1Capacity, totalPaginatedRows));
        } else {
            int startIdx = page1Capacity + (page - 2) * PAGE_SIZE;
            int endIdx = Math.min(startIdx + PAGE_SIZE, totalPaginatedRows);
            pageRows = startIdx < totalPaginatedRows ? paginatedRows.subList(startIdx, endIdx) : Collections.emptyList();
        }

        //  Build embed 
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.INFO + " Suspicious Account Notification List")
                .setColor(Color.CYAN)
                .setTimestamp(Instant.now());

        // Header: always show owner on page 1
        if (page == 1) {
            eb.setDescription("When a suspicious account joins **" + guild.getName()
                    + "**, the following will receive a DM alert.\n\n"
                    + ownerLine);
        } else {
            eb.setDescription("Notification list for **" + guild.getName() + "** — continued.");
        }

        // Body rows — group into sections for readability
        if (pageRows.isEmpty() && page == 1) {
            eb.addField("� Roles & Explicit Users",
                    "*None configured.*\n"
                    + "Use `/suspiciousnotify addrole @role` or `/suspiciousnotify add @user` to add entries.",
                    false);
        } else if (!pageRows.isEmpty()) {
            StringBuilder body = new StringBuilder();
            int globalNum = (page == 1) ? 1 : page1Capacity + (page - 2) * PAGE_SIZE + 1;
            for (String line : pageRows) {
                body.append("`").append(String.format("%2d", globalNum)).append(".` ").append(line).append("\n");
                globalNum++;
            }
            eb.addField("📋 Roles & Explicit Users", body.toString().trim(), false);
        }

        // Stats footer
        int totalEntries = 1 + totalPaginatedRows; // owner + paginated
        eb.setFooter("Page " + page + " of " + totalPages
                + "  •  " + totalEntries + " total recipient source(s)"
                + "  •  Role members resolved at alert time");

        //  Navigation buttons 
        ActionRow navRow = null;
        if (totalPages > 1) {
            Button prev = page > 1
                    ? Button.secondary(PAGE_BTN + ":" + guild.getId() + ":" + (page - 1), "◀ Previous")
                    : Button.secondary(PAGE_BTN + ":" + guild.getId() + ":0", "◀ Previous").asDisabled();
            Button next = page < totalPages
                    ? Button.secondary(PAGE_BTN + ":" + guild.getId() + ":" + (page + 1), "Next ▶")
                    : Button.secondary(PAGE_BTN + ":" + guild.getId() + ":0", "Next ▶").asDisabled();
            Button counter = Button.secondary(PAGE_BTN + ":" + guild.getId() + ":0",
                    page + " / " + totalPages).asDisabled();
            navRow = ActionRow.of(prev, counter, next);
        }

        return new PagedListResult(eb.build(), navRow);
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

        int userCount = 0;
        Object usersObj = settings.get(NOTIFY_USERS_KEY);
        if (usersObj instanceof List<?>) userCount = ((List<?>) usersObj).size();

        int roleCount = 0;
        Object rolesObj = settings.get(NOTIFY_ROLES_KEY);
        if (rolesObj instanceof List<?>) roleCount = ((List<?>) rolesObj).size();

        if (userCount == 0 && roleCount == 0) {
            event.reply("The notification list is already empty.").setEphemeral(true).queue();
            return;
        }
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_USERS_KEY, new ArrayList<String>());
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_ROLES_KEY, new ArrayList<String>());
        logger.info("Cleared suspicious account notification list in guild {} ({} users, {} roles removed)",
                guild.getName(), userCount, roleCount);
        event.reply("Cleared the notification list. Removed " + userCount + " user(s) and " + roleCount + " role(s).\n"
                + "*(The server owner always receives alerts regardless.)*")
                .setEphemeral(true).queue();
    }

    private void handleTest(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

        // Collect all effective recipients (same logic as real alerts)
        Set<String> recipientIds = new LinkedHashSet<>();

        // Guild owner is always notified
        recipientIds.add(guild.getOwnerId());

        // Explicit notify users
        Object usersObj = settings.get(NOTIFY_USERS_KEY);
        if (usersObj instanceof List<?>) {
            for (Object obj : (List<?>) usersObj) {
                if (obj instanceof String s) recipientIds.add(s);
            }
        }

        // Role members
        Object rolesObj = settings.get(NOTIFY_ROLES_KEY);
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

        event.deferReply(true).queue();
        EmbedBuilder testEmbed = new EmbedBuilder()
                .setTitle("Test Notification")
                .setDescription("This is a test notification from the suspicious account alert system.")
                .addField("Server", guild.getName(), true)
                .addField("Triggered By", event.getUser().getAsMention(), true)
                .setColor(Color.BLUE)
                .setFooter("This is only a test - no actual suspicious activity detected")
                .setTimestamp(Instant.now());
        int successCount = 0;
        int failCount = 0;
        for (String userId : recipientIds) {
            try {
                User user = event.getJDA().retrieveUserById(userId).complete();
                if (user != null) {
                    user.openPrivateChannel().complete().sendMessageEmbeds(testEmbed.build()).complete();
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to send test notification to user {}: {}", userId, e.getMessage());
                failCount++;
            }
        }
        String result = String.format(
                "Test notifications sent to **%d** effective recipient(s) (server owner + explicit users + role members)!\n\n"
                        + "✅ Successful: %d\n❌ Failed: %d\n\n"
                        + "If you didn't receive a DM, make sure your DMs are open for this server.",
                recipientIds.size(), successCount, failCount);
        event.getHook().sendMessage(result).queue();
    }

    private void handleAddRole(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Role role = event.getOption("role").getAsRole();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        List<String> notifyRoles = new ArrayList<>();
        Object existing = settings.get(NOTIFY_ROLES_KEY);
        if (existing instanceof List<?>) {
            for (Object obj : (List<?>) existing) {
                if (obj instanceof String s) notifyRoles.add(s);
            }
        }
        if (notifyRoles.contains(role.getId())) {
            event.reply(role.getAsMention() + " is already in the notify role list.").setEphemeral(true).queue();
            return;
        }
        notifyRoles.add(role.getId());
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_ROLES_KEY, notifyRoles);
        int memberCount = guild.getMembersWithRoles(role).size();
        logger.info("Added role {} to suspicious account notifications in guild {}", role.getName(), guild.getName());
        event.reply(CustomEmojis.SUCCESS + " Added " + role.getAsMention() + " to suspicious account notifications. "
                + memberCount + " current member(s) with this role will be alerted.")
                .setEphemeral(true).queue();
    }

    private void handleRemoveRole(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Role role = event.getOption("role").getAsRole();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        List<String> notifyRoles = new ArrayList<>();
        Object existing = settings.get(NOTIFY_ROLES_KEY);
        if (existing instanceof List<?>) {
            for (Object obj : (List<?>) existing) {
                if (obj instanceof String s) notifyRoles.add(s);
            }
        }
        if (!notifyRoles.contains(role.getId())) {
            event.reply(role.getAsMention() + " is not in the notify role list.").setEphemeral(true).queue();
            return;
        }
        notifyRoles.remove(role.getId());
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_ROLES_KEY, notifyRoles);
        logger.info("Removed role {} from suspicious account notifications in guild {}", role.getName(), guild.getName());
        event.reply(CustomEmojis.SUCCESS + " Removed " + role.getAsMention()
                + " from suspicious account notifications. Members who were only notified via this role will no longer receive alerts.")
                .setEphemeral(true).queue();
    }

    private void handleReport(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (!guild.getOwnerId().equals(member.getId())) {
            event.reply("Only the server owner can submit suspicious user reports.").setEphemeral(true).queue();
            return;
        }
        String userId = event.getOption("userid").getAsString().trim();
        String reason = event.getOption("reason").getAsString();
        String notes = event.getOption("notes") != null ? event.getOption("notes").getAsString() : null;
        if (!userId.matches("\\d{17,19}")) {
            event.reply(
                    "Invalid user ID format. User IDs should be 17-19 digit numbers.\nYou can get a user ID by enabling Developer Mode and right-clicking on a user.")
                    .setEphemeral(true).queue();
            return;
        }
        event.deferReply(true).queue();

        String userInfo = "Unknown User";
        String userAvatar = null;
        User reportedUser = null;
        try {
            reportedUser = event.getJDA().retrieveUserById(userId).complete();
            if (reportedUser != null) {
                userInfo = reportedUser.getName() + " (" + reportedUser.getAsTag() + ")";
                userAvatar = reportedUser.getEffectiveAvatarUrl();
            }
        } catch (Exception e) {
            logger.debug("Could not retrieve user info for ID {}: {}", userId, e.getMessage());
        }

        // Add user to suspicious masterlist
        FileStorageManager storage = ServerBot.getStorageManager();
        Map<String, Object> detectionData = new HashMap<>();
        detectionData.put("reportedBy", member.getId());
        detectionData.put("reportedByName", member.getUser().getName());
        detectionData.put("reportedFrom", guild.getId());
        detectionData.put("reportedFromName", guild.getName());
        detectionData.put("reportedAt", Instant.now().toString());
        if (notes != null && !notes.isEmpty()) {
            detectionData.put("notes", notes);
        }

        storage.markUserAsSuspicious(userId, member.getId(), reason, detectionData);
        logger.info("Added user {} to suspicious masterlist. Reported by {} from guild {}", userId,
                member.getUser().getName(), guild.getName());

        // Build report embed for bot owners
        EmbedBuilder reportEmbed = new EmbedBuilder()
                .setTitle(CustomEmojis.MOD_BAN + " Suspicious User Report")
                .setColor(Color.RED)
                .setDescription(
                        "A guild owner has submitted a suspicious user report.\n**User has been added to the masterlist (pending validation).**")
                .addField("Reported User", userInfo, false)
                .addField("User ID", "`" + userId + "`", true)
                .addField("Server", guild.getName() + "\n`" + guild.getId() + "`", true)
                .addField("Reported By", member.getUser().getName() + "\n`" + member.getId() + "`", true)
                .addField("Reason", reason, false)
                .setTimestamp(Instant.now())
                .setFooter("Click buttons below to validate or invalidate this report");
        if (notes != null && !notes.isEmpty()) {
            reportEmbed.addField("Additional Notes", notes, false);
        }
        if (userAvatar != null) {
            reportEmbed.setThumbnail(userAvatar);
        }

        // Find other servers where this user is a member
        List<Guild> otherGuilds = new ArrayList<>();
        for (Guild otherGuild : event.getJDA().getGuilds()) {
            if (otherGuild.getId().equals(guild.getId()))
                continue;
            try {
                Member suspiciousMember = otherGuild.retrieveMemberById(userId).complete();
                if (suspiciousMember != null) {
                    otherGuilds.add(otherGuild);
                }
            } catch (Exception ignored) {
                // User not in this guild
            }
        }

        if (!otherGuilds.isEmpty()) {
            StringBuilder otherServers = new StringBuilder();
            for (Guild otherGuild : otherGuilds) {
                otherServers.append("• ").append(otherGuild.getName()).append(" (`").append(otherGuild.getId())
                        .append("`)\n");
            }
            reportEmbed.addField(CustomEmojis.WARN + " Also Found In (" + otherGuilds.size() + " servers)",
                    otherServers.toString(), false);
        }

        // Validate/Invalidate buttons
        Button validateBtn = Button.success("suspicious_validate:" + userId, CustomEmojis.SUCCESS + " Validate Report");
        Button invalidateBtn = Button.danger("suspicious_invalidate:" + userId,
                CustomEmojis.ERROR + " Invalidate Report");
        Button viewBtn = Button.secondary("suspicious_view:" + userId, CustomEmojis.INFO + " View Details");

        BotConfig config = ServerBot.getConfigManager().getConfig();
        List<String> botOwners = config.getAllOwnerIds();
        if (botOwners.isEmpty()) {
            event.getHook().sendMessage("No bot owners configured. Report could not be sent.").setEphemeral(true)
                    .queue();
            return;
        }

        int successCount = 0;
        int failCount = 0;
        Map<String, String> ownerMessageIds = new HashMap<>(); // Track message IDs for cross-owner updates

        for (String ownerId : botOwners) {
            try {
                User owner = event.getJDA().retrieveUserById(ownerId).complete();
                if (owner != null) {
                    Message sentMessage = owner.openPrivateChannel().complete()
                            .sendMessageEmbeds(reportEmbed.build())
                            .setComponents(ActionRow.of(validateBtn, invalidateBtn, viewBtn))
                            .complete();

                    // Store the message ID for this owner
                    ownerMessageIds.put(ownerId, sentMessage.getId());
                    successCount++;
                    logger.info("Sent suspicious user report to bot owner {} from guild {}", owner.getName(),
                            guild.getName());
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                logger.warn("Failed to send suspicious user report to bot owner {}: {}", ownerId, e.getMessage());
                failCount++;
            }
        }

        // Store message IDs in storage for cross-owner panel updates
        if (!ownerMessageIds.isEmpty()) {
            storage.storePendingReportMessages(userId, ownerMessageIds);
        }

        // Notify other servers where this user is present (send to guild owners)
        int notifiedGuilds = 0;
        if (!otherGuilds.isEmpty()) {
            for (Guild otherGuild : otherGuilds) {
                try {
                    // Respect the target guild's DM notification toggle
                    if (!DmUtils.areDmsEnabled(otherGuild)) {
                        logger.debug("DM notifications disabled for guild {}, skipping suspicious user alert",
                                otherGuild.getName());
                        continue;
                    }
                    User guildOwner = otherGuild.retrieveOwner().complete().getUser();
                    EmbedBuilder alertEmbed = new EmbedBuilder()
                            .setTitle(CustomEmojis.WARN + " Suspicious User Alert")
                            .setColor(Color.ORANGE)
                            .setDescription(
                                    "A user in your server has been reported as suspicious by another guild owner.")
                            .addField("User", userInfo, true)
                            .addField("User ID", "`" + userId + "`", true)
                            .addField("Reason", reason, false)
                            .addField("Reported From", guild.getName(), true)
                            .addField("Status", CustomEmojis.INFO + " Pending Validation", true)
                            .setTimestamp(Instant.now())
                            .setFooter("This report is pending bot owner review. You may want to monitor this user.");

                    if (userAvatar != null) {
                        alertEmbed.setThumbnail(userAvatar);
                    }

                    guildOwner.openPrivateChannel().complete()
                            .sendMessageEmbeds(alertEmbed.build())
                            .complete();
                    notifiedGuilds++;
                    logger.info("Sent suspicious user alert to guild owner of {} for user {}", otherGuild.getName(),
                            userId);
                } catch (Exception e) {
                    logger.debug("Could not notify guild owner of {}: {}", otherGuild.getName(), e.getMessage());
                }
            }
        }

        if (successCount > 0) {
            StringBuilder response = new StringBuilder();
            response.append("**Report Submitted Successfully!**\n\n");
            response.append(CustomEmojis.SUCCESS + " User has been added to the suspicious masterlist\n");
            response.append(CustomEmojis.SUCCESS + " Report sent to ").append(successCount).append(" bot owner(s)");
            if (failCount > 0) response.append(" (").append(failCount).append(" failed)");
            response.append("\n");
            if (notifiedGuilds > 0) {
                response.append(CustomEmojis.SUCCESS + " Alert sent to ").append(notifiedGuilds)
                        .append(" other server owner(s) where this user is present\n");
            }
            response.append("\n**Report Summary:**\n");
            response.append("User ID: `").append(userId).append("`\n");
            response.append("Reason: ").append(reason).append("\n");
            response.append("Status: Pending validation");

            event.getHook().sendMessage(response.toString()).setEphemeral(true).queue();
        } else {
            event.getHook().sendMessage(
                    "Failed to send the report. The bot owners may have DMs disabled.\nHowever, the user has been added to the masterlist.")
                    .setEphemeral(true).queue();
        }
    }
}
