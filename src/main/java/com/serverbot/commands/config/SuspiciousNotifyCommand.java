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
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SuspiciousNotifyCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousNotifyCommand.class);
    private static final String NOTIFY_USERS_KEY = "suspiciousAccountNotifyUsers";

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
                        new SubcommandData("list", "List all users who receive suspicious account notifications"),
                        new SubcommandData("clear", "Clear all users from the notification list"),
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
        if (notifyUsers.isEmpty()) {
            event.reply(
                    "No users are set to receive suspicious account notifications.\nUse `/suspiciousnotify add @user` to add someone.")
                    .setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**Users receiving suspicious account notifications:**\n\n");
        int count = 0;
        for (String userId : notifyUsers) {
            count++;
            Member member = guild.getMemberById(userId);
            if (member != null) {
                sb.append(count).append(". ").append(member.getAsMention()).append(" (")
                        .append(member.getUser().getName()).append(")\n");
            } else {
                sb.append(count).append(". Unknown User (ID: `").append(userId).append("`)\n");
            }
        }
        sb.append("\n**Total:** ").append(notifyUsers.size()).append(" user(s)");
        event.reply(sb.toString()).setEphemeral(true).queue();
    }

    private void handleClear(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        int previousCount = 0;
        Object existing = settings.get(NOTIFY_USERS_KEY);
        if (existing instanceof List<?>) {
            previousCount = ((List<?>) existing).size();
        }
        if (previousCount == 0) {
            event.reply("The notification list is already empty.").setEphemeral(true).queue();
            return;
        }
        ServerBot.getStorageManager().updateGuildSettings(guildId, NOTIFY_USERS_KEY, new ArrayList<String>());
        logger.info("Cleared suspicious account notification list in guild {} ({} users removed)", guild.getName(),
                previousCount);
        event.reply("Cleared the notification list. Removed " + previousCount + " user(s).").setEphemeral(true).queue();
    }

    private void handleTest(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
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
        if (notifyUsers.isEmpty()) {
            event.reply("No users are set to receive notifications. Add users first with `/suspiciousnotify add`.")
                    .setEphemeral(true).queue();
            return;
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
        for (String userId : notifyUsers) {
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
                "Test notifications sent!\n\nSuccessful: %d\nFailed: %d\n\nIf you didn't receive a DM, make sure your DMs are open for this server.",
                successCount, failCount);
        event.getHook().sendMessage(result).queue();
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
            response.append(CustomEmojis.SUCCESS + " Report sent to ").append(successCount).append(" bot owner(s)\n");
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
