package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.models.GlobalChatChannel;
import com.serverbot.services.GlobalChatService;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Handles button and modal interactions for the /globalchat manage panel (DMs).
 * All management actions use modal forms — no conversational follow-up messages.
 *
 * Button IDs: gc_{action}:{channelId}
 * Modal IDs:  gcm:{action}:{channelId}
 */
public class GlobalChatButtonListener extends ListenerAdapter {

    // ── Buttons ───────────────────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("gc_")) return;

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        String[] parts = componentId.split(":", 2);
        if (parts.length < 2) return;
        String action    = parts[0];
        String channelId = parts[1];

        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Channel not found.")).setEphemeral(true).queue();
            return;
        }

        String userId = event.getUser().getId();

        switch (action) {

            case "gc_edit" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildEditModal(gc)).queue();
            }

            case "gc_display" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildDisplayModal(gc)).queue();
            }

            case "gc_setrules" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildSetRulesModal(gc)).queue();
            }

            case "gc_addmod" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildOneFieldModal(
                    "gcm:addmod:" + channelId, "Add Moderator",
                    "userid", "User ID", "Enter the User ID to add as moderator",
                    null, true)).queue();
            }

            case "gc_kick" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildServerActionModal("gcm:kick:" + channelId, "Kick Server")).queue();
            }

            case "gc_ban" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildServerActionModal("gcm:ban:" + channelId, "Ban Server")).queue();
            }

            case "gc_warn" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildServerActionModal("gcm:warn:" + channelId, "Warn Server")).queue();
            }

            case "gc_mute" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildMuteModal(channelId)).queue();
            }

            case "gc_unmute" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildOneFieldModal(
                    "gcm:unmute:" + channelId, "Unmute Server",
                    "serverid", "Server ID", "Server ID to unmute",
                    null, true)).queue();
            }

            case "gc_unwarn" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                event.replyModal(buildOneFieldModal(
                    "gcm:unwarn:" + channelId, "Clear Warnings",
                    "serverid", "Server ID", "Server ID to clear warnings for",
                    null, true)).queue();
            }

            case "gc_delete" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                if (gc.isCoOwner(userId) && !gc.isOwner(userId)) {
                    event.getJDA().retrieveUserById(gc.getOwnerId()).queue(owner ->
                        owner.openPrivateChannel().queue(dm ->
                            dm.sendMessageEmbeds(new EmbedBuilder()
                                .setTitle(CustomEmojis.WARN + " Channel Deletion Request")
                                .setDescription("Co-owner <@" + userId + "> wants to delete **"
                                    + gc.getName() + "** (`" + channelId + "`).\nDo you approve?")
                                .setColor(EmbedUtils.WARNING_COLOR).setTimestamp(Instant.now()).build())
                            .addComponents(ActionRow.of(
                                Button.danger("gc_confirm_delete:" + channelId, "Confirm Delete"),
                                Button.secondary("gc_cancel_delete:" + channelId, "Cancel")))
                            .queue(s -> {}, err -> {}), err -> {}), err -> {});
                    event.reply("The channel owner has been notified to confirm the deletion.")
                         .setEphemeral(true).queue();
                } else {
                    service.deleteChannel(channelId);
                    event.reply(CustomEmojis.SUCCESS + " Global chat channel **" + gc.getName() + "** has been deleted.").queue();
                }
            }

            case "gc_confirm_delete" -> {
                if (!gc.isOwner(userId)) { noAccess(event); return; }
                service.deleteChannel(channelId);
                event.reply(CustomEmojis.SUCCESS + " Global chat channel **" + gc.getName() + "** has been deleted.").queue();
            }

            case "gc_cancel_delete" ->
                event.reply("Deletion cancelled.").setEphemeral(true).queue();

            case "gc_linked" ->
                handleViewLinked(event, gc);

            default ->
                event.reply("Unknown action.").setEphemeral(true).queue();
        }
    }

    // ── Modals ────────────────────────────────────────────────────────────────

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("gcm:")) return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3) return;
        String action    = parts[1];
        String channelId = parts[2];

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            event.reply(CustomEmojis.ERROR + " Channel no longer exists.").queue();
            return;
        }

        String userId = event.getUser().getId();

        switch (action) {

            case "edit" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                String name        = event.getValue("name").getAsString().trim();
                String description = event.getValue("description").getAsString().trim();
                String visibility  = event.getValue("visibility").getAsString().trim().toLowerCase();
                String key         = event.getValue("key").getAsString().trim();
                String webhookmode = event.getValue("webhookmode").getAsString().trim().toLowerCase();

                if (!name.isEmpty())        gc.setName(name);
                if (!description.isEmpty()) gc.setDescription(description);
                if (visibility.equals("public") || visibility.equals("private"))
                    gc.setVisibility(visibility);
                if (!key.isEmpty()) {
                    if (key.equalsIgnoreCase("none") || key.equalsIgnoreCase("clear")) {
                        gc.setKey(null); gc.setKeyRequired(false);
                    } else {
                        gc.setKey(key); gc.setKeyRequired(true);
                    }
                }
                if (webhookmode.equals("webhook"))   gc.setWebhookEnabled(true);
                else if (webhookmode.equals("text")) gc.setWebhookEnabled(false);

                service.saveChannels();
                event.reply(CustomEmojis.SUCCESS + " Channel **" + gc.getName() + "** has been updated.").queue();
            }

            case "display" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                String nameformat = event.getValue("nameformat").getAsString().trim();
                String prefix     = event.getValue("prefix").getAsString().trim();
                String suffix     = event.getValue("suffix").getAsString().trim();

                if (!nameformat.isEmpty()) {
                    if (nameformat.equalsIgnoreCase("reset") || nameformat.equalsIgnoreCase("clear"))
                        gc.setNameFormat(null);
                    else
                        gc.setNameFormat("{}".equals(nameformat) ? "" : nameformat);
                }
                if (!prefix.isEmpty()) {
                    if ("reset".equalsIgnoreCase(prefix)) gc.setMsgPrefix(null);
                    else gc.setMsgPrefix("{}".equals(prefix) ? "" : prefix);
                }
                if (!suffix.isEmpty()) {
                    if ("reset".equalsIgnoreCase(suffix)) gc.setMsgSuffix(null);
                    else gc.setMsgSuffix("{}".equals(suffix) ? "" : suffix);
                }

                service.saveChannels();
                event.reply(CustomEmojis.SUCCESS + " Display format for **" + gc.getName() + "** updated.").queue();
            }

            case "setrules" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                String rawRules = event.getValue("rules").getAsString();
                List<String> rules = new ArrayList<>();
                for (String r : rawRules.split("\\|")) {
                    String t = r.trim();
                    if (!t.isEmpty()) rules.add(t);
                }
                service.setRules(channelId, rules, event.getJDA());
                event.reply(CustomEmojis.SUCCESS + " Rules updated:\n" + service.formatRules(gc)).queue();
            }

            case "addmod" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                String modUserId = event.getValue("userid").getAsString().trim();
                String error = service.addModerator(channelId, modUserId);
                if (error != null) { event.reply(CustomEmojis.ERROR + " " + error).queue(); return; }
                event.reply(CustomEmojis.SUCCESS + " <@" + modUserId + "> added as a moderator.").queue();
            }

            case "kick" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                String serverId = event.getValue("serverid").getAsString().trim();
                String reason   = nb(event.getValue("reason").getAsString());
                String error = service.kickServer(channelId, serverId, reason, event.getJDA());
                event.reply(error != null ? CustomEmojis.ERROR + " " + error
                    : CustomEmojis.SUCCESS + " Server `" + serverId + "` has been kicked.").queue();
            }

            case "ban" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                String serverId = event.getValue("serverid").getAsString().trim();
                String reason   = nb(event.getValue("reason").getAsString());
                String error = service.banServer(channelId, serverId, reason, event.getJDA());
                event.reply(error != null ? CustomEmojis.ERROR + " " + error
                    : CustomEmojis.SUCCESS + " Server `" + serverId + "` has been banned.").queue();
            }

            case "warn" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                String serverId = event.getValue("serverid").getAsString().trim();
                String reason   = nb(event.getValue("reason").getAsString());
                String error = service.warnServer(channelId, serverId, reason, event.getJDA());
                event.reply(error != null ? CustomEmojis.ERROR + " " + error
                    : CustomEmojis.SUCCESS + " Server `" + serverId + "` has been warned.").queue();
            }

            case "mute" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                String serverId    = event.getValue("serverid").getAsString().trim();
                String durationStr = event.getValue("duration").getAsString().trim();
                String reason      = nb(event.getValue("reason").getAsString());
                long durationMs    = parseDuration(durationStr.isEmpty() ? "0" : durationStr);
                String error = service.muteServer(channelId, serverId, durationMs, reason, event.getJDA());
                event.reply(error != null ? CustomEmojis.ERROR + " " + error
                    : CustomEmojis.SUCCESS + " Server `" + serverId + "` muted"
                        + (durationMs <= 0 ? " permanently." : " for " + durationStr + ".")).queue();
            }

            case "unmute" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                String serverId = event.getValue("serverid").getAsString().trim();
                String error = service.unmuteServer(channelId, serverId, event.getJDA());
                event.reply(error != null ? CustomEmojis.ERROR + " " + error
                    : CustomEmojis.SUCCESS + " Server `" + serverId + "` has been unmuted.").queue();
            }

            case "unwarn" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                String serverId = event.getValue("serverid").getAsString().trim();
                String error = service.unwarnServer(channelId, serverId);
                event.reply(error != null ? CustomEmojis.ERROR + " " + error
                    : CustomEmojis.SUCCESS + " Warnings for `" + serverId + "` cleared.").queue();
            }

            default -> event.reply("Unknown action.").queue();
        }
    }

    // ── View linked ───────────────────────────────────────────────────────────

    private void handleViewLinked(ButtonInteractionEvent event, GlobalChatChannel gc) {
        Map<String, String> linked = gc.getLinkedChannels();
        if (linked.isEmpty()) {
            event.reply("No servers are currently linked to this channel.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.TAG + " Linked Servers — " + gc.getName())
                .setColor(EmbedUtils.INFO_COLOR)
                .setTimestamp(Instant.now());

        for (Map.Entry<String, String> entry : linked.entrySet()) {
            String guildId    = entry.getKey();
            String textChanId = entry.getValue();
            Guild  guild      = event.getJDA().getGuildById(guildId);
            String guildName  = guild != null ? guild.getName() : "Unknown Server";
            String status     = "";
            if (gc.isServerMuted(guildId))
                status = " " + CustomEmojis.OFF + " Muted";
            if (!gc.getServerWarnings(guildId).isEmpty())
                status += " " + CustomEmojis.WARN + " " + gc.getServerWarnings(guildId).size() + " warnings";
            eb.addField(guildName + " (`" + guildId + "`)",
                        "Channel: <#" + textChanId + ">" + status, false);
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // ── Modal builders ────────────────────────────────────────────────────────

    private Modal buildEditModal(GlobalChatChannel gc) {
        String cid = gc.getChannelId();

        TextInput.Builder name = TextInput.create("name", TextInputStyle.SHORT)
                .setPlaceholder("Leave blank to keep current").setMaxLength(64).setRequired(false);
        if (gc.getName() != null) name.setValue(gc.getName());

        TextInput.Builder desc = TextInput.create("description", TextInputStyle.SHORT)
                .setPlaceholder("Channel description").setMaxLength(200).setRequired(false);
        if (gc.getDescription() != null) desc.setValue(gc.getDescription());

        TextInput.Builder vis = TextInput.create("visibility", TextInputStyle.SHORT)
                .setPlaceholder("public or private").setMaxLength(7).setRequired(false);
        vis.setValue(gc.getVisibility() != null ? gc.getVisibility() : "public");

        TextInput.Builder key = TextInput.create("key", TextInputStyle.SHORT)
                .setPlaceholder("Join key — 'none' to remove, blank to keep").setMaxLength(64).setRequired(false);
        if (gc.getKey() != null) key.setValue(gc.getKey());

        TextInput.Builder wm = TextInput.create("webhookmode", TextInputStyle.SHORT)
                .setPlaceholder("webhook or text").setMaxLength(7).setRequired(false);
        wm.setValue(gc.isWebhookEnabled() ? "webhook" : "text");

        return Modal.create("gcm:edit:" + cid, "Edit Channel — " + gc.getName())
                .addComponents(
                    Label.of("Channel Name", name.build()),
                    Label.of("Description", desc.build()),
                    Label.of("Visibility (public / private)", vis.build()),
                    Label.of("Join Key ('none' to remove)", key.build()),
                    Label.of("Webhook Mode (webhook / text)", wm.build()))
                .build();
    }

    private Modal buildDisplayModal(GlobalChatChannel gc) {
        String cid = gc.getChannelId();

        TextInput.Builder nameformat = TextInput.create("nameformat", TextInputStyle.SHORT)
                .setPlaceholder("{user} @ {server} — 'reset' to clear, {} for empty, blank to keep")
                .setMaxLength(100).setRequired(false);
        String nf = gc.getNameFormat();
        if (nf != null) nameformat.setValue(nf.isEmpty() ? "{}" : nf);

        TextInput.Builder prefix = TextInput.create("prefix", TextInputStyle.SHORT)
                .setPlaceholder("'reset' for [GC], {} for none, blank to keep")
                .setMaxLength(50).setRequired(false);
        String mp = gc.getMsgPrefix();
        if (mp != null) prefix.setValue(mp.isEmpty() ? "{}" : mp);

        TextInput.Builder suffix = TextInput.create("suffix", TextInputStyle.SHORT)
                .setPlaceholder("'reset' for • {server}, {} for none, blank to keep")
                .setMaxLength(50).setRequired(false);
        String ms = gc.getMsgSuffix();
        if (ms != null) suffix.setValue(ms.isEmpty() ? "{}" : ms);

        return Modal.create("gcm:display:" + cid, "Display Format — " + gc.getName())
                .addComponents(
                    Label.of("Name Format (overrides prefix/suffix)", nameformat.build()),
                    Label.of("Message Prefix", prefix.build()),
                    Label.of("Message Suffix", suffix.build()))
                .build();
    }

    private Modal buildSetRulesModal(GlobalChatChannel gc) {
        TextInput.Builder rules = TextInput.create("rules", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Rule 1 | Rule 2 | Rule 3 ...")
                .setMaxLength(2000).setRequired(false);
        if (!gc.getRules().isEmpty()) rules.setValue(String.join(" | ", gc.getRules()));
        return Modal.create("gcm:setrules:" + gc.getChannelId(), "Set Rules — " + gc.getName())
                .addComponents(Label.of("Rules (separated by |)", rules.build()))
                .build();
    }

    private Modal buildServerActionModal(String modalId, String title) {
        TextInput.Builder serverid = TextInput.create("serverid", TextInputStyle.SHORT)
                .setPlaceholder("Server ID (18-digit number)").setMaxLength(20).setRequired(true);
        TextInput.Builder reason = TextInput.create("reason", TextInputStyle.SHORT)
                .setPlaceholder("Optional reason").setMaxLength(200).setRequired(false);
        return Modal.create(modalId, title)
                .addComponents(
                    Label.of("Server ID", serverid.build()),
                    Label.of("Reason (optional)", reason.build()))
                .build();
    }

    private Modal buildMuteModal(String channelId) {
        TextInput.Builder serverid = TextInput.create("serverid", TextInputStyle.SHORT)
                .setPlaceholder("Server ID (18-digit number)").setMaxLength(20).setRequired(true);
        TextInput.Builder duration = TextInput.create("duration", TextInputStyle.SHORT)
                .setPlaceholder("1h, 30m, 7d — blank or 0 for permanent").setMaxLength(10).setRequired(false);
        TextInput.Builder reason = TextInput.create("reason", TextInputStyle.SHORT)
                .setPlaceholder("Optional reason").setMaxLength(200).setRequired(false);
        return Modal.create("gcm:mute:" + channelId, "Mute Server")
                .addComponents(
                    Label.of("Server ID", serverid.build()),
                    Label.of("Duration (blank = permanent)", duration.build()),
                    Label.of("Reason (optional)", reason.build()))
                .build();
    }

    private Modal buildOneFieldModal(String modalId, String title, String inputId,
                                     String labelText, String placeholder,
                                     String currentValue, boolean required) {
        TextInput.Builder input = TextInput.create(inputId, TextInputStyle.SHORT)
                .setPlaceholder(placeholder).setMaxLength(64).setRequired(required);
        if (currentValue != null) input.setValue(currentValue);
        return Modal.create(modalId, title)
                .addComponents(Label.of(labelText, input.build()))
                .build();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void noAccess(ButtonInteractionEvent event) {
        event.replyEmbeds(EmbedUtils.createErrorEmbed("No Access",
                "You don't have permission to perform this action.")).setEphemeral(true).queue();
    }

    private void noAccess(ModalInteractionEvent event) {
        event.reply(CustomEmojis.ERROR + " You don't have permission to perform this action.").queue();
    }

    private static String nb(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private long parseDuration(String input) {
        if (input == null || input.equals("0")) return 0;
        try {
            input = input.trim().toLowerCase();
            long value = Long.parseLong(input.substring(0, input.length() - 1));
            char unit  = input.charAt(input.length() - 1);
            return switch (unit) {
                case 's' -> TimeUnit.SECONDS.toMillis(value);
                case 'm' -> TimeUnit.MINUTES.toMillis(value);
                case 'h' -> TimeUnit.HOURS.toMillis(value);
                case 'd' -> TimeUnit.DAYS.toMillis(value);
                default  -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }
}
