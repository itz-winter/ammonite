package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.List;
import java.util.Map;

/**
 * Server settings configuration command.
 * Running /settings with no action opens an ephemeral GUI panel.
 * Feature toggles flip current state — no boolean needed.
 */
public class SettingsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "Servers only.")).setEphemeral(true).queue();
            return;
        }
        if (!PermissionManager.hasPermission(event.getMember(), "admin.settings")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You need `admin.settings` to configure server settings.")).setEphemeral(true).queue();
            return;
        }

        OptionMapping actionOpt = event.getOption("action");
        if (actionOpt == null) { openPanel(event); return; }

        switch (actionOpt.getAsString()) {
            case "economy"           -> handleEconomyToggle(event);
            case "leveling"          -> handleLevelingToggle(event);
            case "automod"           -> handleAutomodToggle(event);
            case "pronouns"          -> handlePronounsToggle(event);
            case "dm-notifications"  -> handleDmNotifyToggle(event);
            case "daily-reward"      -> handleInt(event, "dailyReward",        "Daily Reward",            1, 1_000_000);
            case "work-reward"       -> handleInt(event, "workReward",         "Work Reward",             1, 1_000_000);
            case "work-cooldown"     -> handleInt(event, "workCooldown",       "Work Cooldown (minutes)", 1, 1440);
            case "points-per-msg"    -> handleInt(event, "pointsPerMessage",   "Points per Message",      0, 10000);
            case "xp-per-msg"        -> handleInt(event, "xpPerMessage",       "XP per Message",          0, 10000);
            case "max-warnings"      -> handleInt(event, "maxWarnings",        "Max Warnings",            1, 50);
            case "warn-expiry"       -> handleInt(event, "warnExpiry",         "Warning Expiry (days)",   1, 365);
            case "mute-role"         -> handleMuteRole(event);
            case "log-channel"       -> handleLogChannel(event);
            default                  -> openPanel(event);
        }
    }

    // ── Panel ─────────────────────────────────────────────────────────────────

    private void openPanel(SlashCommandInteractionEvent event) {
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId());
        event.reply(buildPanel(settings, event.getGuild(), event.getUser().getId())).setEphemeral(true).queue();
    }

    public static MessageCreateData buildPanel(Map<String, Object> settings, Guild guild, String userId) {
        return new MessageCreateBuilder()
                .setEmbeds(buildPanelEmbed(settings, guild).build())
                .setComponents(buildPanelRows(settings, userId))
                .build();
    }

    public static MessageEditData buildPanelEdit(Map<String, Object> settings, Guild guild, String userId) {
        return new MessageEditBuilder()
                .setEmbeds(buildPanelEmbed(settings, guild).build())
                .setComponents(buildPanelRows(settings, userId))
                .build();
    }

    public static EmbedBuilder buildPanelEmbed(Map<String, Object> settings, Guild guild) {
        boolean economy  = Boolean.TRUE.equals(settings.get("economyEnabled"));
        boolean leveling = Boolean.TRUE.equals(settings.get("levelingEnabled"));
        boolean automod  = Boolean.TRUE.equals(settings.get("automodEnabled"));
        boolean pronouns = Boolean.TRUE.equals(settings.get("pronounsEnabled"));
        boolean dmNotify = Boolean.TRUE.equals(settings.get("dmNotificationsEnabled"));

        int  dailyReward  = getInt(settings, "dailyReward",      500);
        int  workReward   = getInt(settings, "workReward",       100);
        int  workCooldown = getInt(settings, "workCooldown",     60);
        int  pointsPerMsg = getInt(settings, "pointsPerMessage", 1);
        int  xpPerMsg     = getInt(settings, "xpPerMessage",     10);
        int  maxWarnings  = getInt(settings, "maxWarnings",      3);
        int  warnExpiry   = getInt(settings, "warnExpiry",       30);

        String muteRoleId  = (String) settings.get("muteRoleId");
        String logChanId   = (String) settings.get("logChannelId");
        String muteDisplay = muteRoleId  != null ? "<@&" + muteRoleId + ">"  : "*Not set*";
        String logDisplay  = logChanId   != null ? "<#"  + logChanId  + ">"  : "*Not set*";

        String on  = CustomEmojis.ON;
        String off = CustomEmojis.OFF;

        return new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + "  Server Settings \u2014 " + guild.getName())
                .setColor(0x5865F2)
                .setThumbnail(guild.getIconUrl())
                .addField("\uD83C\uDFAE  Features",
                        (economy  ? on : off) + " Economy   " +
                        (leveling ? on : off) + " Leveling   " +
                        (automod  ? on : off) + " AutoMod\n" +
                        (pronouns ? on : off) + " Pronouns   " +
                        (dmNotify ? on : off) + " DM Notifications", false)
                .addField("\uD83D\uDCB0  Economy",
                        "Daily: **" + dailyReward + "**  |  Work: **" + workReward +
                        "**  |  Cooldown: **" + workCooldown + " min**\n" +
                        "Points/msg: **" + pointsPerMsg + "**", true)
                .addField("\uD83D\uDCC8  Leveling",
                        "XP/msg: **" + xpPerMsg + "**", true)
                .addField("\uD83D\uDEE1\uFE0F  Moderation",
                        "Max Warnings: **" + maxWarnings + "**  |  Expiry: **" + warnExpiry + " days**", false)
                .addField("\uD83D\uDD07  Mute Role", muteDisplay, true)
                .addField("\uD83D\uDCDD  Log Channel", logDisplay, true)
                .setFooter("Use buttons to configure  \u2022  Changes apply immediately");
    }

    public static List<ActionRow> buildPanelRows(Map<String, Object> settings, String uid) {
        boolean economy  = Boolean.TRUE.equals(settings.get("economyEnabled"));
        boolean leveling = Boolean.TRUE.equals(settings.get("levelingEnabled"));
        boolean automod  = Boolean.TRUE.equals(settings.get("automodEnabled"));
        boolean pronouns = Boolean.TRUE.equals(settings.get("pronounsEnabled"));
        boolean dmNotify = Boolean.TRUE.equals(settings.get("dmNotificationsEnabled"));

        Emoji ON_E  = Emoji.fromFormatted(CustomEmojis.ON);
        Emoji OFF_E = Emoji.fromFormatted(CustomEmojis.OFF);
        Emoji REF_E = Emoji.fromFormatted(CustomEmojis.REFRESH);

        Button econBtn = economy
                ? Button.danger ("sgui:economy:"  + uid, "Economy: ON" ).withEmoji(ON_E)
                : Button.success("sgui:economy:"  + uid, "Economy: OFF").withEmoji(OFF_E);
        Button levBtn = leveling
                ? Button.danger ("sgui:leveling:" + uid, "Leveling: ON" ).withEmoji(ON_E)
                : Button.success("sgui:leveling:" + uid, "Leveling: OFF").withEmoji(OFF_E);
        Button autoBtn = automod
                ? Button.danger ("sgui:automod:"  + uid, "AutoMod: ON" ).withEmoji(ON_E)
                : Button.success("sgui:automod:"  + uid, "AutoMod: OFF").withEmoji(OFF_E);
        Button pronBtn = pronouns
                ? Button.danger ("sgui:pronouns:" + uid, "Pronouns: ON" ).withEmoji(ON_E)
                : Button.success("sgui:pronouns:" + uid, "Pronouns: OFF").withEmoji(OFF_E);
        Button dmBtn = dmNotify
                ? Button.danger ("sgui:dm-notify:" + uid, "DM Notify: ON" ).withEmoji(ON_E)
                : Button.success("sgui:dm-notify:" + uid, "DM Notify: OFF").withEmoji(OFF_E);

        return List.of(
                ActionRow.of(econBtn, levBtn, autoBtn, pronBtn, dmBtn),
                ActionRow.of(
                        Button.secondary("sgui:daily-reward:"   + uid, "Daily Reward"   ).withEmoji(Emoji.fromUnicode("\uD83D\uDCB0")),
                        Button.secondary("sgui:work-reward:"    + uid, "Work Reward"    ).withEmoji(Emoji.fromUnicode("\uD83D\uDCBC")),
                        Button.secondary("sgui:work-cooldown:"  + uid, "Work Cooldown"  ).withEmoji(Emoji.fromUnicode("\u23F0")),
                        Button.secondary("sgui:points-per-msg:" + uid, "Points/Msg"     ).withEmoji(Emoji.fromUnicode("\uD83D\uDCCA")),
                        Button.secondary("sgui:xp-per-msg:"     + uid, "XP/Msg"         ).withEmoji(Emoji.fromUnicode("\uD83D\uDCC8"))
                ),
                ActionRow.of(
                        Button.secondary("sgui:max-warnings:"  + uid, "Max Warnings"  ).withEmoji(Emoji.fromUnicode("\u26A0\uFE0F")),
                        Button.secondary("sgui:warn-expiry:"   + uid, "Warn Expiry"   ).withEmoji(Emoji.fromUnicode("\uD83D\uDCC5")),
                        Button.secondary("sgui:mute-role:"     + uid, "Mute Role"     ).withEmoji(Emoji.fromUnicode("\uD83D\uDD07")),
                        Button.secondary("sgui:log-channel:"   + uid, "Log Channel"   ).withEmoji(Emoji.fromUnicode("\uD83D\uDCDD")),
                        Button.secondary("sgui:refresh:"       + uid, "Refresh"       ).withEmoji(REF_E)
                )
        );
    }

    // ── Toggle handlers ───────────────────────────────────────────────────────

    public void handleEconomyToggle(SlashCommandInteractionEvent event) {
        toggle(event, "economyEnabled", "Economy");
    }

    public void handleLevelingToggle(SlashCommandInteractionEvent event) {
        toggle(event, "levelingEnabled", "Leveling");
    }

    public void handleAutomodToggle(SlashCommandInteractionEvent event) {
        toggle(event, "automodEnabled", "AutoMod");
    }

    public void handlePronounsToggle(SlashCommandInteractionEvent event) {
        toggle(event, "pronounsEnabled", "Pronouns");
    }

    public void handleDmNotifyToggle(SlashCommandInteractionEvent event) {
        toggle(event, "dmNotificationsEnabled", "DM Notifications");
    }

    private void toggle(SlashCommandInteractionEvent event, String key, String label) {
        String guildId = event.getGuild().getId();
        boolean next = !Boolean.TRUE.equals(ServerBot.getStorageManager().getGuildSettings(guildId).get(key));
        ServerBot.getStorageManager().updateGuildSettings(guildId, key, next);
        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                label + " " + (next ? "Enabled " + CustomEmojis.ON : "Disabled " + CustomEmojis.OFF),
                label + " is now **" + (next ? "on" : "off") + "**.")).queue();
    }

    // ── Numeric handlers ──────────────────────────────────────────────────────

    private void handleInt(SlashCommandInteractionEvent event, String key, String label, int min, int max) {
        OptionMapping opt = event.getOption("value");
        if (opt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Value",
                    "Provide a number " + min + "\u2013" + max + " for **" + label + "**.")).setEphemeral(true).queue();
            return;
        }
        int v = opt.getAsInt();
        if (v < min || v > max) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range",
                    label + " must be " + min + "\u2013" + max + ".")).setEphemeral(true).queue();
            return;
        }
        ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), key, v);
        event.replyEmbeds(EmbedUtils.createSuccessEmbed(label + " Updated",
                label + " is now **" + v + "**.")).queue();
    }

    // ── Role / Channel handlers ───────────────────────────────────────────────

    public void handleMuteRole(SlashCommandInteractionEvent event) {
        OptionMapping opt = event.getOption("role");
        if (opt == null) {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "muteRoleId", null);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Mute Role Cleared",
                    "Mute role has been removed.")).queue();
            return;
        }
        Role role = opt.getAsRole();
        ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "muteRoleId", role.getId());
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Mute Role Set",
                "Muted members will receive " + role.getAsMention() + ".")).queue();
    }

    public void handleLogChannel(SlashCommandInteractionEvent event) {
        OptionMapping opt = event.getOption("channel");
        if (opt == null) {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "logChannelId", null);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Log Channel Cleared",
                    "Log channel has been removed.")).queue();
            return;
        }
        try {
            TextChannel ch = opt.getAsChannel().asTextChannel();
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "logChannelId", ch.getId());
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Log Channel Set",
                    "Logs will be sent to " + ch.getAsMention() + ".")).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Channel",
                    "That is not a text channel.")).setEphemeral(true).queue();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public static int getInt(Map<String, Object> settings, String key, int def) {
        Object v = settings.get(key);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return def; }
    }

    // ── Metadata ──────────────────────────────────────────────────────────────

    @Override public String getName()              { return "settings"; }
    @Override public String getDescription()       { return "Configure server settings \u2014 run with no args for the GUI panel"; }
    @Override public CommandCategory getCategory() { return CommandCategory.CONFIGURATION; }
    @Override public boolean requiresPermissions() { return true; }

    public static CommandData getCommandData() {
        OptionData actionOpt = new OptionData(OptionType.STRING, "action",
                "Setting to configure \u2014 omit to open the GUI panel", false)
                .addChoice("Toggle Economy",           "economy")
                .addChoice("Toggle Leveling",          "leveling")
                .addChoice("Toggle AutoMod",           "automod")
                .addChoice("Toggle Pronouns",          "pronouns")
                .addChoice("Toggle DM Notifications",  "dm-notifications")
                .addChoice("Set Daily Reward",         "daily-reward")
                .addChoice("Set Work Reward",          "work-reward")
                .addChoice("Set Work Cooldown",        "work-cooldown")
                .addChoice("Set Points per Message",   "points-per-msg")
                .addChoice("Set XP per Message",       "xp-per-msg")
                .addChoice("Set Max Warnings",         "max-warnings")
                .addChoice("Set Warning Expiry",       "warn-expiry")
                .addChoice("Set Mute Role",            "mute-role")
                .addChoice("Set Log Channel",          "log-channel");

        return Commands.slash("settings",
                "Configure server settings \u2014 run with no args for the GUI panel")
                .addOptions(
                        actionOpt,
                        new OptionData(OptionType.INTEGER, "value",
                                "Numeric value (for reward/cooldown/warning settings)", false),
                        new OptionData(OptionType.ROLE,    "role",
                                "Role (for mute-role — omit to clear)", false),
                        new OptionData(OptionType.CHANNEL, "channel",
                                "Channel (for log-channel — omit to clear)", false)
                );
    }
}
