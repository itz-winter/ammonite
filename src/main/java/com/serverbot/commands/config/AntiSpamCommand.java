package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
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
 * Anti-spam configuration command.
 * Running /antispam with no action opens an ephemeral GUI panel.
 * Toggle actions flip current state — no extra boolean needed.
 */
public class AntiSpamCommand implements SlashCommand {

        private static final int DEFAULT_MSG_LIMIT = 10;
        private static final int DEFAULT_TIME_WINDOW = 10;
        private static final int DEFAULT_MENTION_LIMIT = 5;
        private static final int DEFAULT_DUP_LIMIT = 4;
        private static final int DEFAULT_MUTE_DUR = 10;
        private static final int DEFAULT_TIMEOUT_DUR = 10;
        private static final int DEFAULT_BAN_DUR = 0;

        @Override
        public void execute(SlashCommandInteractionEvent event) {
                if (!event.isFromGuild()) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "Servers only.")).setEphemeral(true)
                                        .queue();
                        return;
                }
                if (!PermissionManager.hasPermission(event.getMember(), "admin.antispam")) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                                        "You need `admin.antispam` to configure anti-spam.")).setEphemeral(true)
                                        .queue();
                        return;
                }

                OptionMapping actionOpt = event.getOption("action");
                if (actionOpt == null) {
                        openPanel(event);
                        return;
                }

                switch (actionOpt.getAsString()) {
                        case "toggle" -> handleToggle(event);
                        case "auto-delete" -> handleAutoDelete(event);
                        case "msg-limit" -> handleMsgLimit(event);
                        case "time-window" -> handleTimeWindow(event);
                        case "mention-limit" -> handleMentionLimit(event);
                        case "dup-limit" -> handleDupLimit(event);
                        case "punishment" -> handlePunishment(event);
                        case "mute-dur" -> handleMuteDuration(event);
                        case "timeout-dur" -> handleTimeoutDuration(event);
                        case "ban-dur" -> handleBanDuration(event);
                        default -> openPanel(event);
                }
        }

        // Panel

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
                boolean enabled = Boolean.TRUE.equals(settings.get("antiSpamEnabled"));
                boolean autoDelete = Boolean.TRUE.equals(settings.get("antiSpamAutoDelete"));
                int msgLimit = getInt(settings, "antiSpamMessageLimit", DEFAULT_MSG_LIMIT);
                int timeWindow = getInt(settings, "antiSpamTimeWindow", DEFAULT_TIME_WINDOW);
                int menLimit = getInt(settings, "antiSpamMentionLimit", DEFAULT_MENTION_LIMIT);
                int dupLimit = getInt(settings, "antiSpamDupLimit", DEFAULT_DUP_LIMIT);
                int muteDur = getInt(settings, "antiSpamMuteDuration", DEFAULT_MUTE_DUR);
                int toDur = getInt(settings, "antiSpamTimeoutDuration", DEFAULT_TIMEOUT_DUR);
                int banDur = getInt(settings, "antiSpamBanDuration", DEFAULT_BAN_DUR);
                String punishment = (String) settings.getOrDefault("antiSpamPunishment", "warn");

                String punishDisplay = switch (punishment.toLowerCase()) {
                        case "mute" -> "\uD83D\uDD07 Mute (" + muteDur + " min)";
                        case "timeout" -> "\u23F1\uFE0F Timeout (" + toDur + " min)";
                        case "kick" -> "\uD83D\uDC62 Kick";
                        case "ban" ->
                                CustomEmojis.MOD_BAN + " Ban (" + (banDur == 0 ? "Permanent" : banDur + "h") + ")";
                        default -> "\u26A0\uFE0F Warn";
                };

                return new EmbedBuilder()
                                .setTitle(CustomEmojis.SETTING + "  Anti-Spam \u2014 " + guild.getName())
                                .setColor(enabled ? 0x00FF00 : 0xFF4444)
                                .setThumbnail(guild.getIconUrl())
                                .addField("\uD83D\uDEE1\uFE0F  Status",
                                                (enabled ? CustomEmojis.ON : CustomEmojis.OFF)
                                                                + "  Anti-spam is **"
                                                                + (enabled ? "ENABLED" : "DISABLED") + "**",
                                                false)
                                .addField("\uD83D\uDCAC  Rate Limit",
                                                "Max **" + msgLimit + "** messages in **" + timeWindow + "s**", true)
                                .addField("\uD83D\uDCE2  Mention Limit",
                                                "Max **" + menLimit + "** mentions per message", true)
                                .addField("\uD83D\uDD04  Duplicate Limit",
                                                "Max **" + dupLimit + "** identical messages", true)
                                .addField("\u2694\uFE0F  Punishment", punishDisplay, true)
                                .addField("\uD83D\uDDD1\uFE0F  Auto-Delete",
                                                autoDelete ? CustomEmojis.ON + " **On**"
                                                                : CustomEmojis.OFF + " **Off**",
                                                true)
                                .addField(CustomEmojis.INFO + "  Note",
                                                "Administrators and members with `automod.antispam.bypass` are exempt.",
                                                false)
                                .setFooter("Use buttons to configure  \u2022  Changes apply immediately");
        }

        public static List<ActionRow> buildPanelRows(Map<String, Object> settings, String uid) {
                boolean enabled = Boolean.TRUE.equals(settings.get("antiSpamEnabled"));
                boolean autoDelete = Boolean.TRUE.equals(settings.get("antiSpamAutoDelete"));

                Emoji ON_E = Emoji.fromFormatted(CustomEmojis.ON);
                Emoji OFF_E = Emoji.fromFormatted(CustomEmojis.OFF);
                Emoji REF_E = Emoji.fromFormatted(CustomEmojis.REFRESH);

                Button toggleBtn = enabled
                                ? Button.danger("agui:toggle:" + uid, "Anti-Spam: ON").withEmoji(ON_E)
                                : Button.success("agui:toggle:" + uid, "Anti-Spam: OFF").withEmoji(OFF_E);
                Button delBtn = autoDelete
                                ? Button.danger("agui:auto-delete:" + uid, "Auto-Delete: ON").withEmoji(ON_E)
                                : Button.success("agui:auto-delete:" + uid, "Auto-Delete: OFF").withEmoji(OFF_E);

                return List.of(
                                ActionRow.of(
                                                toggleBtn,
                                                delBtn,
                                                Button.secondary("agui:refresh:" + uid, "Refresh").withEmoji(REF_E)),
                                ActionRow.of(
                                                Button.secondary("agui:msg-limit:" + uid, "Msg Limit")
                                                                .withEmoji(Emoji.fromUnicode("\uD83D\uDCAC")),
                                                Button.secondary("agui:time-window:" + uid, "Time Window")
                                                                .withEmoji(Emoji.fromUnicode("\u23F1\uFE0F")),
                                                Button.secondary("agui:mention-limit:" + uid, "Mention Limit")
                                                                .withEmoji(Emoji.fromUnicode("\uD83D\uDCE2")),
                                                Button.secondary("agui:dup-limit:" + uid, "Dup Limit")
                                                                .withEmoji(Emoji.fromUnicode("\uD83D\uDD04")),
                                                Button.secondary("agui:punishment:" + uid, "Punishment")
                                                                .withEmoji(Emoji.fromUnicode("\u2694\uFE0F"))),
                                ActionRow.of(
                                                Button.secondary("agui:mute-dur:" + uid, "Mute Duration")
                                                                .withEmoji(Emoji.fromUnicode("\uD83D\uDD07")),
                                                Button.secondary("agui:timeout-dur:" + uid, "Timeout Duration")
                                                                .withEmoji(Emoji.fromUnicode("\u23F0")),
                                                Button.secondary("agui:ban-dur:" + uid, "Ban Duration")
                                                                .withEmoji(Emoji.fromFormatted(CustomEmojis.MOD_BAN))));
        }

        // Quick actions

        public void handleToggle(SlashCommandInteractionEvent event) {
                String guildId = event.getGuild().getId();
                boolean next = !Boolean.TRUE
                                .equals(ServerBot.getStorageManager().getGuildSettings(guildId).get("antiSpamEnabled"));
                ServerBot.getStorageManager().updateGuildSettings(guildId, "antiSpamEnabled", next);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                "Anti-Spam " + (next ? "Enabled " + CustomEmojis.ON : "Disabled " + CustomEmojis.OFF),
                                "Anti-spam is now **" + (next ? "on" : "off") + "**.")).queue();
        }

        public void handleAutoDelete(SlashCommandInteractionEvent event) {
                String guildId = event.getGuild().getId();
                Map<String, Object> s = ServerBot.getStorageManager().getGuildSettings(guildId);
                OptionMapping opt = event.getOption("enabled");
                boolean next = opt != null ? opt.getAsBoolean() : !Boolean.TRUE.equals(s.get("antiSpamAutoDelete"));
                ServerBot.getStorageManager().updateGuildSettings(guildId, "antiSpamAutoDelete", next);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                "Auto-Delete " + (next ? "Enabled " + CustomEmojis.ON : "Disabled " + CustomEmojis.OFF),
                                "Spam will " + (next ? "now be auto-deleted." : "no longer be auto-deleted."))).queue();
        }

        public void handleMsgLimit(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Value", "Provide a number 1\u201350."))
                                        .setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 1 || v > 50) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range", "Must be 1\u201350."))
                                        .setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamMessageLimit", v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Message Limit Set",
                                "Max **" + v + "** messages per window.")).queue();
        }

        public void handleTimeWindow(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Value", "Provide a number 1\u201360."))
                                        .setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 1 || v > 60) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range", "Must be 1\u201360 seconds."))
                                        .setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamTimeWindow", v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Time Window Set",
                                "Sliding window is now **" + v + "s**.")).queue();
        }

        public void handleMentionLimit(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Value", "Provide a number 1\u201330."))
                                        .setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 1 || v > 30) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range", "Must be 1\u201330."))
                                        .setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamMentionLimit", v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Mention Limit Set",
                                "Max **" + v + "** mentions per message.")).queue();
        }

        public void handleDupLimit(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Value", "Provide a number 2\u201320."))
                                        .setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 2 || v > 20) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range", "Must be 2\u201320."))
                                        .setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamDupLimit", v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Duplicate Limit Set",
                                "Max **" + v + "** identical messages.")).queue();
        }

        public void handlePunishment(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("type");
                if (opt == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Type",
                                        "Choose warn / mute / timeout / kick / ban.")).setEphemeral(true).queue();
                        return;
                }
                String type = opt.getAsString().toLowerCase();
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamPunishment", type);
                event.replyEmbeds(
                                EmbedUtils.createSuccessEmbed("Punishment Set", "Spammers will be **" + type + "d**."))
                                .queue();
        }

        public void handleMuteDuration(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(
                                        EmbedUtils.createErrorEmbed("Missing Value", "Provide minutes (1\u20131440)."))
                                        .setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 1 || v > 1440) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range", "1\u20131440 minutes."))
                                        .setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamMuteDuration", v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Mute Duration Set",
                                "Mute duration is now **" + v + " min**.")).queue();
        }

        public void handleTimeoutDuration(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(
                                        EmbedUtils.createErrorEmbed("Missing Value", "Provide minutes (1\u201440320)."))
                                        .setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 1 || v > 40320) {
                        event.replyEmbeds(
                                        EmbedUtils.createErrorEmbed("Out of Range", "1\u201440320 minutes (28 days)."))
                                        .setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamTimeoutDuration",
                                v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Timeout Duration Set",
                                "Timeout duration is now **" + v + " min**.")).queue();
        }

        public void handleBanDuration(SlashCommandInteractionEvent event) {
                OptionMapping opt = event.getOption("value");
                if (opt == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Value",
                                        "Provide hours (0\u20138760, 0 = permanent).")).setEphemeral(true).queue();
                        return;
                }
                int v = opt.getAsInt();
                if (v < 0 || v > 8760) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("Out of Range",
                                        "0\u20138760 hours (0 = permanent).")).setEphemeral(true).queue();
                        return;
                }
                ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamBanDuration", v);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Ban Duration Set",
                                v == 0 ? "Bans will be **permanent**." : "Ban duration is now **" + v + "h**."))
                                .queue();
        }

        // Helpers

        public static int getInt(Map<String, Object> settings, String key, int def) {
                Object v = settings.get(key);
                if (v == null)
                        return def;
                if (v instanceof Number n)
                        return n.intValue();
                try {
                        return Integer.parseInt(v.toString());
                } catch (Exception e) {
                        return def;
                }
        }

        // Metadata

        @Override
        public String getName() {
                return "antispam";
        }

        @Override
        public String getDescription() {
                return "Configure anti-spam \u2014 run with no args for the GUI panel";
        }

        @Override
        public CommandCategory getCategory() {
                return CommandCategory.CONFIGURATION;
        }

        @Override
        public boolean requiresPermissions() {
                return true;
        }

        public static CommandData getCommandData() {
                OptionData actionOpt = new OptionData(OptionType.STRING, "action",
                                "Action to perform \u2014 omit to open the interactive GUI panel", false)
                                .addChoice("Toggle Anti-Spam", "toggle")
                                .addChoice("Toggle Auto-Delete", "auto-delete")
                                .addChoice("Set Message Limit", "msg-limit")
                                .addChoice("Set Time Window", "time-window")
                                .addChoice("Set Mention Limit", "mention-limit")
                                .addChoice("Set Duplicate Limit", "dup-limit")
                                .addChoice("Set Punishment", "punishment")
                                .addChoice("Set Mute Duration", "mute-dur")
                                .addChoice("Set Timeout Duration", "timeout-dur")
                                .addChoice("Set Ban Duration", "ban-dur");

                OptionData typeOpt = new OptionData(OptionType.STRING, "type",
                                "Punishment type (for punishment action)", false)
                                .addChoice("Warn", "warn")
                                .addChoice("Mute", "mute")
                                .addChoice("Timeout", "timeout")
                                .addChoice("Kick", "kick")
                                .addChoice("Ban", "ban");

                return Commands.slash("antispam",
                                "Configure anti-spam / auto-moderation \u2014 run with no args for the GUI panel")
                                .addOptions(
                                                actionOpt,
                                                new OptionData(OptionType.INTEGER, "value",
                                                                "Numeric value (for limit/duration actions)", false),
                                                typeOpt,
                                                new OptionData(OptionType.BOOLEAN, "enabled",
                                                                "Explicitly on/off \u2014 omit to toggle (for auto-delete)",
                                                                false));
        }
}
