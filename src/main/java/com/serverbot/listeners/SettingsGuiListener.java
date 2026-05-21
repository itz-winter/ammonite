package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.commands.config.SettingsCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.util.Map;

/**
 * Handles button and modal interactions for the /settings GUI panel.
 *
 * Button IDs:  sgui:{action}:{userId}
 * Modal IDs:   sgm:{action}:{userId}
 */
public class SettingsGuiListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("sgui:")) return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3) return;
        String action = parts[1];
        String userId = parts[2];

        if (!event.getUser().getId().equals(userId)) {
            event.reply(CustomEmojis.ERROR + " This panel belongs to someone else.").setEphemeral(true).queue();
            return;
        }
        if (!event.isFromGuild() || !PermissionManager.hasPermission(event.getMember(), "admin.settings")) {
            event.reply(CustomEmojis.ERROR + " You no longer have permission to configure settings.").setEphemeral(true).queue();
            return;
        }

        Guild  guild   = event.getGuild();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

        switch (action) {
            case "economy"  -> { toggle(guildId, "economyEnabled",         settings); refreshPanel(event, guildId, guild, userId); }
            case "leveling" -> { toggle(guildId, "levelingEnabled",        settings); refreshPanel(event, guildId, guild, userId); }
            case "automod"  -> { toggle(guildId, "automodEnabled",         settings); refreshPanel(event, guildId, guild, userId); }
            case "pronouns" -> { toggle(guildId, "pronounsEnabled",        settings); refreshPanel(event, guildId, guild, userId); }
            case "dm-notify"-> { toggle(guildId, "dmNotificationsEnabled", settings); refreshPanel(event, guildId, guild, userId); }

            case "daily-reward"    -> event.replyModal(intModal(userId, "sgm:daily-reward",    "Set Daily Reward",        "Coins (1\u20131,000,000)",   1, 1_000_000, SettingsCommand.getInt(settings,"dailyReward",500))).queue();
            case "work-reward"     -> event.replyModal(intModal(userId, "sgm:work-reward",     "Set Work Reward",         "Coins (1\u20131,000,000)",   1, 1_000_000, SettingsCommand.getInt(settings,"workReward",100))).queue();
            case "work-cooldown"   -> event.replyModal(intModal(userId, "sgm:work-cooldown",   "Set Work Cooldown",       "Minutes (1\u20131440)",       1, 1440,      SettingsCommand.getInt(settings,"workCooldown",60))).queue();
            case "points-per-msg"  -> event.replyModal(intModal(userId, "sgm:points-per-msg",  "Set Points per Message",  "Points (0\u201310000)",       0, 10000,     SettingsCommand.getInt(settings,"pointsPerMessage",1))).queue();
            case "xp-per-msg"      -> event.replyModal(intModal(userId, "sgm:xp-per-msg",      "Set XP per Message",      "XP (0\u201310000)",           0, 10000,     SettingsCommand.getInt(settings,"xpPerMessage",10))).queue();
            case "max-warnings"    -> event.replyModal(intModal(userId, "sgm:max-warnings",    "Set Max Warnings",        "Warnings (1\u201350)",        1, 50,        SettingsCommand.getInt(settings,"maxWarnings",3))).queue();
            case "warn-expiry"     -> event.replyModal(intModal(userId, "sgm:warn-expiry",     "Set Warning Expiry",      "Days (1\u2013365)",           1, 365,       SettingsCommand.getInt(settings,"warnExpiry",30))).queue();
            case "mute-role"       -> event.replyModal(roleModal(userId, settings)).queue();
            case "log-channel"     -> event.replyModal(channelModal(userId, settings)).queue();
            case "refresh"         -> refreshPanel(event, guildId, guild, userId);
            default                -> event.reply("Unknown action.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("sgm:")) return;

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
            case "daily-reward", "work-reward", "work-cooldown",
                 "points-per-msg", "xp-per-msg", "max-warnings", "warn-expiry" -> {
                int v;
                try { v = Integer.parseInt(raw); }
                catch (NumberFormatException e) {
                    event.reply(CustomEmojis.ERROR + " Enter a valid number.").setEphemeral(true).queue();
                    return;
                }
                String storageKey = switch (action) {
                    case "daily-reward"   -> "dailyReward";
                    case "work-reward"    -> "workReward";
                    case "work-cooldown"  -> "workCooldown";
                    case "points-per-msg" -> "pointsPerMessage";
                    case "xp-per-msg"     -> "xpPerMessage";
                    case "max-warnings"   -> "maxWarnings";
                    case "warn-expiry"    -> "warnExpiry";
                    default               -> throw new IllegalStateException();
                };
                ServerBot.getStorageManager().updateGuildSettings(guildId, storageKey, v);
                event.editMessage(SettingsCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "mute-role" -> {
                if (raw.isEmpty() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("clear")) {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "muteRoleId", null);
                    event.editMessage(SettingsCommand.buildPanelEdit(
                            ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
                    return;
                }
                String roleId = raw.replaceAll("[<@&>]", "").trim();
                var role = guild.getRoleById(roleId);
                if (role == null) {
                    String lower = roleId.toLowerCase();
                    role = guild.getRoles().stream().filter(r -> r.getName().equalsIgnoreCase(lower)).findFirst().orElse(null);
                }
                if (role == null) {
                    event.reply(CustomEmojis.ERROR + " Role not found. Use a mention, name, or ID. Type `clear` to remove.").setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "muteRoleId", role.getId());
                event.editMessage(SettingsCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "log-channel" -> {
                if (raw.isEmpty() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("clear")) {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "logChannelId", null);
                    event.editMessage(SettingsCommand.buildPanelEdit(
                            ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
                    return;
                }
                String chanId = raw.replaceAll("[<#>]", "").trim();
                TextChannel tc = null;
                try { tc = guild.getTextChannelById(chanId); } catch (Exception ignored) {}
                if (tc == null) {
                    String lower = chanId.toLowerCase();
                    tc = guild.getTextChannels().stream().filter(c -> c.getName().equalsIgnoreCase(lower)).findFirst().orElse(null);
                }
                if (tc == null) {
                    event.reply(CustomEmojis.ERROR + " Channel not found. Use a mention, name, or ID. Type `clear` to remove.").setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "logChannelId", tc.getId());
                event.editMessage(SettingsCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void toggle(String guildId, String key, Map<String, Object> settings) {
        boolean next = !Boolean.TRUE.equals(settings.get(key));
        ServerBot.getStorageManager().updateGuildSettings(guildId, key, next);
    }

    private void refreshPanel(ButtonInteractionEvent event, String guildId, Guild guild, String userId) {
        event.editMessage(SettingsCommand.buildPanelEdit(
                ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
    }

    private Modal intModal(String uid, String modalId, String title, String labelText, int min, int max, int current) {
        TextInput.Builder input = TextInput.create("val", TextInputStyle.SHORT)
                .setValue(String.valueOf(current))
                .setPlaceholder(min + " \u2013 " + max)
                .setRequired(true)
                .setMaxLength(7);
        return Modal.create(modalId + ":" + uid, title)
                .addComponents(Label.of(labelText, input.build()))
                .build();
    }

    private Modal roleModal(String uid, Map<String, Object> settings) {
        String cur = settings.containsKey("muteRoleId") ? "<@&" + settings.get("muteRoleId") + ">" : "";
        TextInput.Builder input = TextInput.create("val", TextInputStyle.SHORT)
                .setPlaceholder("@Muted  or  clear")
                .setRequired(false)
                .setMaxLength(100);
        if (!cur.isEmpty()) input.setValue(cur);
        return Modal.create("sgm:mute-role:" + uid, "Set Mute Role")
                .addComponents(Label.of("Role (mention, name, or ID) \u2014 type 'clear' to remove", input.build()))
                .build();
    }

    private Modal channelModal(String uid, Map<String, Object> settings) {
        String cur = settings.containsKey("logChannelId") ? "<#" + settings.get("logChannelId") + ">" : "";
        TextInput.Builder input = TextInput.create("val", TextInputStyle.SHORT)
                .setPlaceholder("#logs  or  clear")
                .setRequired(false)
                .setMaxLength(100);
        if (!cur.isEmpty()) input.setValue(cur);
        return Modal.create("sgm:log-channel:" + uid, "Set Log Channel")
                .addComponents(Label.of("Channel (#name, name, or ID) \u2014 type 'clear' to remove", input.build()))
                .build();
    }
}
