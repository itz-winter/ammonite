package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Check command — scan a user (by mention, ID, or Discord URL) to assess
 * how suspicious they are. Also checks the global suspicious masterlist.
 */
public class CheckCommand implements SlashCommand {

    private static final long NEW_ACCOUNT_DAYS = 7;
    private static final long SAME_DAY_HOURS = 24;
    private static final long VERY_NEW_ACCOUNT_DAYS = 1;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member executor = event.getMember();
        if (!PermissionManager.hasPermission(executor, "mod.check")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `mod.check` permission to use this command."))
                    .setEphemeral(true).queue();
            return;
        }

        String input = event.getOption("user").getAsString().trim();
        boolean ephemeral = event.getOption("ephemeral") != null && event.getOption("ephemeral").getAsBoolean();

        // Defer with appropriate visibility
        event.deferReply(ephemeral).queue();

        try {
            User target = resolveUser(event, input);
            if (target == null) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "User Not Found",
                        "Could not resolve a user from `" + input + "`. " +
                        "Try a user mention, ID, or Discord URL (e.g. https://discord.com/users/123456789)."))
                        .queue();
                return;
            }

            if (target.isBot()) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Bot Account",
                        "Bots cannot be checked for suspicious activity.")).queue();
                return;
            }

            // Analyze
            List<String> flags = new ArrayList<>();
            OffsetDateTime created = target.getTimeCreated();
            OffsetDateTime now = OffsetDateTime.now();
            long ageDays = ChronoUnit.DAYS.between(created, now);
            long ageHours = ChronoUnit.HOURS.between(created, now);
            long ageMins = ChronoUnit.MINUTES.between(created, now);
            long ageSecs = ChronoUnit.SECONDS.between(created, now);

            boolean veryNew = false;
            boolean noAvatar = false;
            boolean suspiciousName = false;

            // Account age
            if (ageDays < VERY_NEW_ACCOUNT_DAYS) {
                flags.add(String.format("🚩 Account created **%dh %dm %ds ago** (very new)", ageHours, ageMins % 60, ageSecs % 60));
                veryNew = true;
            } else if (ageDays < NEW_ACCOUNT_DAYS) {
                flags.add(String.format("🚩 Account created **%d days ago** (new account)", ageDays));
            }

            // Joined same day as creation (no guild context for a raw check)
            if (event.isFromGuild()) {
                Member mem = event.getGuild().getMember(target);
                if (mem != null) {
                    long joinDiff = ChronoUnit.HOURS.between(created, mem.getTimeJoined());
                    if (joinDiff < SAME_DAY_HOURS) {
                        flags.add(String.format("🚩 Joined server **%dh %dm** after account creation", joinDiff, ChronoUnit.MINUTES.between(created, mem.getTimeJoined()) % 60));
                    }
                }
            }

            // Avatar
            if (target.getAvatarUrl() == null) {
                flags.add("🚩 No custom avatar (default Discord avatar)");
                noAvatar = true;
            }

            // Username
            String name = target.getName().toLowerCase();
            if (name.contains("discord")) {
                flags.add("🚩 Username contains 'discord' (potential impersonation)");
                suspiciousName = true;
            }
            if (name.length() < 3) {
                flags.add("🚩 Very short username (< 3 characters)");
                suspiciousName = true;
            }
            if (name.matches(".*\\d{4,}.*")) {
                flags.add("⚠️ Username contains 4+ consecutive digits (bot-like pattern)");
            }

            // Check global masterlist
            boolean inMasterlist = ServerBot.getStorageManager().isUserSuspicious(target.getId());
            boolean isMasterlistValidated = false;
            String masterlistReason = null;
            if (inMasterlist) {
                Map<String, Object> data = ServerBot.getStorageManager().getSuspiciousUserData(target.getId());
                if (data != null) {
                    isMasterlistValidated = Boolean.TRUE.equals(data.get("validated"));
                    masterlistReason = (String) data.get("reason");
                }
                flags.add("🔴 **Global masterlist** — " + (isMasterlistValidated ? "✅ Validated threat" : "⚠️ Pending review")
                        + (masterlistReason != null ? " (`" + masterlistReason + "`)" : ""));
            }

            // Blatant bot detection
            boolean blatantBot = veryNew && noAvatar && suspiciousName;

            // Build report
            int severity = flags.size();
            Color reportColor;
            String level;
            if (blatantBot || isMasterlistValidated) {
                reportColor = Color.RED;
                level = "🔴 **CRITICAL**";
            } else if (severity >= 3) {
                reportColor = Color.ORANGE;
                level = "🟠 **HIGH**";
            } else if (severity >= 1) {
                reportColor = Color.YELLOW;
                level = "🟡 **MEDIUM**";
            } else {
                reportColor = Color.GREEN;
                level = "🟢 **LOW** — No flags detected";
            }

            StringBuilder desc = new StringBuilder();
            desc.append("**User:** ").append(target.getAsMention()).append("\n");
            desc.append("**User ID:** `").append(target.getId()).append("`\n");
            desc.append("**Account Created:** <t:").append(created.toEpochSecond()).append(":F> (<t:").append(created.toEpochSecond()).append(":R>)\n\n");
            desc.append("**Suspicion Level:** ").append(level).append("\n");
            if (blatantBot) {
                desc.append("🤖 **Blatant bot indicators detected!**\n");
            }
            if (flags.isEmpty()) {
                desc.append("\n✅ **No suspicious indicators found.**");
            } else {
                desc.append("\n").append(String.join("\n", flags));
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔍 User Check — " + target.getName())
                    .setDescription(desc.toString())
                    .setColor(reportColor)
                    .setThumbnail(target.getEffectiveAvatarUrl())
                    .setFooter("Check performed by " + event.getUser().getName())
                    .setTimestamp(OffsetDateTime.now());

            if (severity > 0 && !flags.isEmpty()) {
                embed.addField("Summary", severity + " flag(s) detected", false);
                String action;
                if (blatantBot || isMasterlistValidated) {
                    action = "Consider banning this user or contacting a bot owner to add to the masterlist.";
                } else if (severity >= 3) {
                    action = "Monitor this user's behavior. Consider restricting permissions.";
                } else if (severity >= 1) {
                    action = "Keep an eye on this user. Likely harmless but worth noting.";
                } else {
                    action = "No action needed.";
                }
                embed.addField("📋 Recommended Action", action, false);
            }

            event.getHook().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Check Failed",
                    "An error occurred: " + e.getMessage())).queue();
        }
    }

    /**
     * Resolve a user from mention (<@id>), ID, or Discord user URL.
     */
    private User resolveUser(SlashCommandInteractionEvent event, String input) {
        // Strip Discord URL prefix
        String id = input;
        if (input.matches("https?://discord\\.com/users/\\d+")) {
            id = input.replaceAll("https?://discord\\.com/users/", "").split("[?/]")[0];
        } else if (input.matches("<@!?(\\d+)>")) {
            id = input.replaceAll("<@!?(\\d+)>", "$1");
        }

        if (!id.matches("\\d+")) return null;

        // Try cache first
        User cached = event.getJDA().getUserById(id);
        if (cached != null) return cached;

        // Try retrieving from Discord
        try {
            return event.getJDA().retrieveUserById(id).complete();
        } catch (Exception e) {
            return null;
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("check", "Check a user for suspicious activity")
                .addOption(OptionType.STRING, "user", "User mention, ID, or Discord URL", true)
                .addOption(OptionType.BOOLEAN, "ephemeral", "Make the response hidden (default false)", false);
    }

    @Override
    public String getName() { return "check"; }

    @Override
    public String getDescription() { return "Check a user for suspicious activity indicators"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MODERATION; }

    @Override
    public boolean requiresPermissions() { return true; }
}
