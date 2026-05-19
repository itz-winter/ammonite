package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.models.GlobalChatChannel;
import com.serverbot.services.GlobalChatService;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Handles button interactions and conversational follow-ups
 * for the /globalchat manage panel (DMs).
 */
public class GlobalChatButtonListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GlobalChatButtonListener.class);

    // ── Button clicks ────────────────────────────────────────────────

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("gc_")) return;

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        String[] parts = componentId.split(":", 2);
        if (parts.length < 2) return;
        String action = parts[0]; // e.g. "gc_edit"
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
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("edit_name");
                event.reply("Please enter the new channel name:\n> Original name: **" + gc.getName() + "**\n\n*Type `skip` to keep the current value.*").queue();
            }
            case "gc_delete" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                if (gc.isCoOwner(userId) && !gc.isOwner(userId)) {
                    // Co-owner: notify owner
                    event.getJDA().retrieveUserById(gc.getOwnerId()).queue(owner -> {
                        owner.openPrivateChannel().queue(dm -> {
                            dm.sendMessageEmbeds(new EmbedBuilder()
                                    .setTitle(CustomEmojis.WARN + " Channel Deletion Request")
                                    .setDescription("Co-owner <@" + userId + "> wants to delete **" + gc.getName() + "** (`" + channelId + "`).\nDo you approve?")
                                    .setColor(EmbedUtils.WARNING_COLOR).setTimestamp(Instant.now()).build())
                                    .addComponents(ActionRow.of(
                                            net.dv8tion.jda.api.components.buttons.Button.danger("gc_confirm_delete:" + channelId, "Confirm Delete"),
                                            net.dv8tion.jda.api.components.buttons.Button.secondary("gc_cancel_delete:" + channelId, "Cancel")))
                                    .queue(s -> {}, err -> {});
                        }, err -> {});
                    }, err -> {});
                    event.reply("The channel owner has been notified to confirm the deletion.").setEphemeral(true).queue();
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
            case "gc_cancel_delete" -> {
                event.reply("Deletion cancelled.").setEphemeral(true).queue();
            }
            case "gc_setrules" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("set_rules");
                event.reply("Please enter the new rules (separate each rule with `|`):\n" +
                        (gc.getRules().isEmpty() ? "> No rules currently set." : "> Current rules:\n" + service.formatRules(gc))).queue();
            }
            case "gc_kick" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("kick_server");
                event.reply("Please enter the **Server ID** to kick:").queue();
            }
            case "gc_ban" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("ban_server");
                event.reply("Please enter the **Server ID** to ban:").queue();
            }
            case "gc_warn" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("warn_server");
                event.reply("Please enter the **Server ID** to warn:").queue();
            }
            case "gc_mute" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("mute_server");
                event.reply("Please enter the **Server ID** to mute:").queue();
            }
            case "gc_unmute" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("unmute_server_id");
                event.reply("Please enter the **Server ID** to unmute:").queue();
            }
            case "gc_unwarn" -> {
                if (!gc.hasModerateAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("unwarn_server_id");
                event.reply("Please enter the **Server ID** to clear warnings for:").queue();
            }
            case "gc_addmod" -> {
                if (!gc.hasManageAccess(userId)) { noAccess(event); return; }
                service.setManagePanelState(userId, new GlobalChatService.ManagePanelState(channelId));
                service.getManagePanelState(userId).setPendingAction("addmod_user_id");
                event.reply("Please enter the **User ID** to add as a moderator:").queue();
            }
            case "gc_linked" -> {
                handleViewLinked(event, gc, service);
            }
            default -> event.reply("Unknown action.").setEphemeral(true).queue();
        }
    }

    // ── DM message follow-ups (conversational input) ─────────────────

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.isFromGuild()) return; // DMs only for manage panel

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        String userId = event.getAuthor().getId();
        GlobalChatService.ManagePanelState state = service.getManagePanelState(userId);
        if (state == null) return;

        String input = event.getMessage().getContentRaw().trim();
        String channelId = state.getGlobalChannelId();
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            event.getChannel().sendMessage(CustomEmojis.ERROR + " Channel no longer exists.").queue();
            service.clearManagePanelState(userId);
            return;
        }

        String action = state.getPendingAction();
        if (action == null) return;

        switch (action) {
            // ── Edit flow (multi-step) ──
            case "edit_name" -> {
                if (!"skip".equalsIgnoreCase(input)) gc.setName(input);
                state.setPendingAction("edit_description");
                event.getChannel().sendMessage("Please enter the new channel description:\n> Current: **" + gc.getDescription() + "**\n\n*Type `skip` to keep the current value.*").queue();
            }
            case "edit_description" -> {
                if (!"skip".equalsIgnoreCase(input)) gc.setDescription(input);
                state.setPendingAction("edit_visibility");
                event.getChannel().sendMessage("Please enter the new visibility (`public` or `private`):\n> Current: **" + gc.getVisibility() + "**\n\n*Type `skip` to keep the current value.*").queue();
            }
            case "edit_visibility" -> {
                if (!"skip".equalsIgnoreCase(input)) {
                    if (!input.equalsIgnoreCase("public") && !input.equalsIgnoreCase("private")) {
                        event.getChannel().sendMessage("Invalid value. Please enter `public` or `private`, or `skip`.").queue();
                        return;
                    }
                    gc.setVisibility(input.toLowerCase());
                }
                state.setPendingAction("edit_key");
                event.getChannel().sendMessage("Please enter a new join key:\n> Current: **" + (gc.getKey() != null ? gc.getKey() : "none") + "**\n\n*Type `skip` to keep the current value, or `none` to remove the key.*").queue();
            }
            case "edit_key" -> {
                if (!"skip".equalsIgnoreCase(input)) {
                    if ("none".equalsIgnoreCase(input)) {
                        gc.setKey(null);
                        gc.setKeyRequired(false);
                    } else {
                        gc.setKey(input);
                        gc.setKeyRequired(true);
                    }
                }
                state.setPendingAction("edit_nameformat");
                event.getChannel().sendMessage(
                        "Enter the full display name template (e.g. `{user} @ {server}`) to override prefix/suffix:\n" +
                        "> Current: **" + (gc.getNameFormat() != null ? gc.getNameFormat() : "*(not set — uses prefix/suffix)*") + "**\n\n" +
                        "*Type `skip` to keep, `reset` to clear (back to prefix/suffix mode), or a new template.*").queue();
            }
            case "edit_nameformat" -> {
                if (!"skip".equalsIgnoreCase(input)) {
                    if (input.equalsIgnoreCase("reset") || input.equalsIgnoreCase("none") || input.equalsIgnoreCase("default")) {
                        gc.setNameFormat(null);
                    } else if ("{}".equals(input)) {
                        gc.setNameFormat("");
                    } else {
                        gc.setNameFormat(input);
                    }
                }
                state.setPendingAction("edit_webhookmode");
                event.getChannel().sendMessage(
                        "Enable webhook mode?\n> Current: **" + (gc.isWebhookEnabled() ? "webhook" : "text") + "**\n\n" +
                        "*Type `webhook` (messages sent via webhook) or `text` (messages sent as the bot), or `skip` to keep.*").queue();
            }
            case "edit_webhookmode" -> {
                if (!input.equalsIgnoreCase("skip")) {
                    if (input.equalsIgnoreCase("webhook")) {
                        gc.setWebhookEnabled(true);
                    } else if (input.equalsIgnoreCase("text")) {
                        gc.setWebhookEnabled(false);
                    } else {
                        event.getChannel().sendMessage("Invalid value. Please enter `webhook`, `text`, or `skip`.").queue();
                        return;
                    }
                }
                service.saveChannels();
                event.getChannel().sendMessage(CustomEmojis.SUCCESS + " Channel **" + gc.getName() + "** has been updated.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Set rules ──
            case "set_rules" -> {
                List<String> rules = new ArrayList<>();
                for (String r : input.split("\\|")) {
                    String trimmed = r.trim();
                    if (!trimmed.isEmpty()) rules.add(trimmed);
                }
                service.setRules(channelId, rules, event.getJDA());
                event.getChannel().sendMessage(CustomEmojis.SUCCESS + " Rules updated:\n" + service.formatRules(gc)).queue();
                service.clearManagePanelState(userId);
            }

            // ── Kick ──
            case "kick_server" -> {
                state.setPendingData(input);
                state.setPendingAction("kick_reason");
                event.getChannel().sendMessage("Please enter the reason for kicking (or `none`):").queue();
            }
            case "kick_reason" -> {
                String reason = "none".equalsIgnoreCase(input) ? null : input;
                String error = service.kickServer(channelId, state.getPendingData(), reason, event.getJDA());
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Server kicked.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Ban ──
            case "ban_server" -> {
                state.setPendingData(input);
                state.setPendingAction("ban_reason");
                event.getChannel().sendMessage("Please enter the reason for banning (or `none`):").queue();
            }
            case "ban_reason" -> {
                String reason = "none".equalsIgnoreCase(input) ? null : input;
                String error = service.banServer(channelId, state.getPendingData(), reason, event.getJDA());
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Server banned.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Warn ──
            case "warn_server" -> {
                state.setPendingData(input);
                state.setPendingAction("warn_reason");
                event.getChannel().sendMessage("Please enter the reason for warning (or `none`):").queue();
            }
            case "warn_reason" -> {
                String reason = "none".equalsIgnoreCase(input) ? null : input;
                String error = service.warnServer(channelId, state.getPendingData(), reason, event.getJDA());
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Server warned.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Mute ──
            case "mute_server" -> {
                state.setPendingData(input);
                state.setPendingAction("mute_duration");
                event.getChannel().sendMessage("Please enter the mute duration (e.g. `1h`, `30m`, `7d`, `0` for permanent):").queue();
            }
            case "mute_duration" -> {
                long durationMs = parseDuration(input);
                state.setPendingAction("mute_reason");
                state.setPendingData(state.getPendingData() + "|" + durationMs);
                event.getChannel().sendMessage("Please enter the reason for muting (or `none`):").queue();
            }
            case "mute_reason" -> {
                String[] dataParts = state.getPendingData().split("\\|", 2);
                String serverId = dataParts[0];
                long durationMs = Long.parseLong(dataParts[1]);
                String reason = "none".equalsIgnoreCase(input) ? null : input;
                String error = service.muteServer(channelId, serverId, durationMs, reason, event.getJDA());
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Server muted.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Unmute ──
            case "unmute_server_id" -> {
                String error = service.unmuteServer(channelId, input, event.getJDA());
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Server unmuted.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Unwarn ──
            case "unwarn_server_id" -> {
                String error = service.unwarnServer(channelId, input);
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Warnings cleared.").queue();
                service.clearManagePanelState(userId);
            }

            // ── Add mod ──
            case "addmod_user_id" -> {
                String error = service.addModerator(channelId, input);
                event.getChannel().sendMessage(error != null ? CustomEmojis.ERROR + " " + error : CustomEmojis.SUCCESS + " Moderator added.").queue();
                service.clearManagePanelState(userId);
            }

            default -> {
                service.clearManagePanelState(userId);
            }
        }
    }

    // ── View linked servers ──────────────────────────────────────────

    private void handleViewLinked(ButtonInteractionEvent event, GlobalChatChannel gc, GlobalChatService service) {
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
            String guildId = entry.getKey();
            String channelIdStr = entry.getValue();
            Guild guild = event.getJDA().getGuildById(guildId);
            String guildName = guild != null ? guild.getName() : "Unknown Server";
            String status = "";
            if (gc.isServerMuted(guildId)) status = " " + CustomEmojis.OFF + " Muted";
            if (!gc.getServerWarnings(guildId).isEmpty()) status += " " + CustomEmojis.WARN + " " + gc.getServerWarnings(guildId).size() + " warnings";
            eb.addField(guildName + " (`" + guildId + "`)",
                    "Channel: <#" + channelIdStr + ">" + status, false);
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void noAccess(ButtonInteractionEvent event) {
        event.replyEmbeds(EmbedUtils.createErrorEmbed("No Access",
                "You don't have permission to perform this action.")).setEphemeral(true).queue();
    }

    private long parseDuration(String input) {
        if (input == null || input.equals("0")) return 0;
        try {
            input = input.trim().toLowerCase();
            long value = Long.parseLong(input.substring(0, input.length() - 1));
            char unit = input.charAt(input.length() - 1);
            return switch (unit) {
                case 's' -> TimeUnit.SECONDS.toMillis(value);
                case 'm' -> TimeUnit.MINUTES.toMillis(value);
                case 'h' -> TimeUnit.HOURS.toMillis(value);
                case 'd' -> TimeUnit.DAYS.toMillis(value);
                default -> 0;
            };
        } catch (Exception e) {
            return 0;
        }
    }
}
