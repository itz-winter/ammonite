package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.commands.config.AntiSpamCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.util.Map;

/**
 * Handles button and modal interactions for the /antispam GUI panel.
 *
 * Button IDs:  agui:{action}:{userId}
 * Modal IDs:   agm:{action}:{userId}
 */
public class AntiSpamGuiListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("agui:")) return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3) return;
        String action = parts[1];
        String userId = parts[2];

        if (!event.getUser().getId().equals(userId)) {
            event.reply(CustomEmojis.ERROR + " This panel belongs to someone else.").setEphemeral(true).queue();
            return;
        }
        if (!event.isFromGuild() || !PermissionManager.hasPermission(event.getMember(), "admin.antispam")) {
            event.reply(CustomEmojis.ERROR + " You no longer have permission to configure anti-spam.").setEphemeral(true).queue();
            return;
        }

        Guild guild   = event.getGuild();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

        switch (action) {
            case "toggle" -> {
                boolean next = !Boolean.TRUE.equals(settings.get("antiSpamEnabled"));
                ServerBot.getStorageManager().updateGuildSettings(guildId, "antiSpamEnabled", next);
                refreshPanel(event, guildId, guild, userId);
            }
            case "auto-delete" -> {
                boolean next = !Boolean.TRUE.equals(settings.get("antiSpamAutoDelete"));
                ServerBot.getStorageManager().updateGuildSettings(guildId, "antiSpamAutoDelete", next);
                refreshPanel(event, guildId, guild, userId);
            }
            case "msg-limit"      -> event.replyModal(intModal(userId, "agm:msg-limit",     "Set Message Limit",    "Messages per window (1\u201350)", 1, 50,   AntiSpamCommand.getInt(settings,"antiSpamMessageLimit",   10))).queue();
            case "time-window"    -> event.replyModal(intModal(userId, "agm:time-window",   "Set Time Window",      "Seconds (1\u201360)",             1, 60,   AntiSpamCommand.getInt(settings,"antiSpamTimeWindow",    10))).queue();
            case "mention-limit"  -> event.replyModal(intModal(userId, "agm:mention-limit", "Set Mention Limit",    "Mentions per message (1\u201330)", 1, 30,   AntiSpamCommand.getInt(settings,"antiSpamMentionLimit",  5))).queue();
            case "dup-limit"      -> event.replyModal(intModal(userId, "agm:dup-limit",     "Set Duplicate Limit",  "Max identical msgs (2\u201320)",   2, 20,   AntiSpamCommand.getInt(settings,"antiSpamDupLimit",      4))).queue();
            case "punishment"     -> event.replyModal(punishModal(userId, settings)).queue();
            case "mute-dur"       -> event.replyModal(intModal(userId, "agm:mute-dur",     "Set Mute Duration",    "Minutes (1\u20131440)",            1, 1440, AntiSpamCommand.getInt(settings,"antiSpamMuteDuration",  10))).queue();
            case "timeout-dur"    -> event.replyModal(intModal(userId, "agm:timeout-dur",  "Set Timeout Duration", "Minutes (1\u201340320)",           1, 40320,AntiSpamCommand.getInt(settings,"antiSpamTimeoutDuration",10))).queue();
            case "ban-dur"        -> event.replyModal(intModal(userId, "agm:ban-dur",      "Set Ban Duration",     "Hours (0=permanent, max 8760)",   0, 8760, AntiSpamCommand.getInt(settings,"antiSpamBanDuration",   0))).queue();
            case "refresh"        -> refreshPanel(event, guildId, guild, userId);
            default               -> event.reply("Unknown action.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("agm:")) return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3) return;
        String action = parts[1];
        String userId = parts[2];

        if (!event.getUser().getId().equals(userId)) return;
        if (!event.isFromGuild()) return;

        Guild  guild   = event.getGuild();
        String guildId = guild.getId();
        String raw     = event.getValue("val").getAsString().trim();

        switch (action) {
            case "msg-limit", "time-window", "mention-limit", "dup-limit",
                 "mute-dur", "timeout-dur", "ban-dur" -> {
                int v;
                try { v = Integer.parseInt(raw); }
                catch (NumberFormatException e) {
                    event.reply(CustomEmojis.ERROR + " Enter a valid number.").setEphemeral(true).queue();
                    return;
                }
                String storageKey = switch (action) {
                    case "msg-limit"     -> "antiSpamMessageLimit";
                    case "time-window"   -> "antiSpamTimeWindow";
                    case "mention-limit" -> "antiSpamMentionLimit";
                    case "dup-limit"     -> "antiSpamDupLimit";
                    case "mute-dur"      -> "antiSpamMuteDuration";
                    case "timeout-dur"   -> "antiSpamTimeoutDuration";
                    case "ban-dur"       -> "antiSpamBanDuration";
                    default              -> throw new IllegalStateException();
                };
                ServerBot.getStorageManager().updateGuildSettings(guildId, storageKey, v);
                event.editMessage(AntiSpamCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "punishment" -> {
                String type = raw.toLowerCase();
                if (!type.matches("warn|mute|timeout|kick|ban")) {
                    event.reply(CustomEmojis.ERROR + " Invalid type. Use: `warn`, `mute`, `timeout`, `kick`, `ban`.").setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "antiSpamPunishment", type);
                event.editMessage(AntiSpamCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void refreshPanel(ButtonInteractionEvent event, String guildId, Guild guild, String userId) {
        event.editMessage(AntiSpamCommand.buildPanelEdit(
                ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
    }

    private Modal intModal(String uid, String modalId, String title, String labelText, int min, int max, int current) {
        TextInput.Builder input = TextInput.create("val", TextInputStyle.SHORT)
                .setValue(String.valueOf(current))
                .setPlaceholder(min + " \u2013 " + max)
                .setRequired(true)
                .setMaxLength(6);
        return Modal.create(modalId + ":" + uid, title)
                .addComponents(Label.of(labelText, input.build()))
                .build();
    }

    private Modal punishModal(String uid, Map<String, Object> settings) {
        String cur = (String) settings.getOrDefault("antiSpamPunishment", "warn");
        TextInput.Builder input = TextInput.create("val", TextInputStyle.SHORT)
                .setValue(cur)
                .setPlaceholder("warn / mute / timeout / kick / ban")
                .setRequired(true)
                .setMaxLength(10);
        return Modal.create("agm:punishment:" + uid, "Set Punishment Type")
                .addComponents(Label.of("Type: warn, mute, timeout, kick, ban", input.build()))
                .build();
    }
}
