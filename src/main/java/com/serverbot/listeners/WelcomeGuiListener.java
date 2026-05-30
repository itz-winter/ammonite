package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.commands.config.WelcomeCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
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
 * Handles button and modal interactions for the /welcome GUI panel.
 *
 * Button IDs: wgui:{action}:{userId}
 * Modal IDs: wgm:{action}:{userId}
 */
public class WelcomeGuiListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("wgui:"))
            return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3)
            return;
        String action = parts[1];
        String userId = parts[2];

        // Only the panel owner can interact
        if (!event.getUser().getId().equals(userId)) {
            event.reply(CustomEmojis.ERROR + " This panel belongs to someone else.").setEphemeral(true).queue();
            return;
        }

        // Permission re-check
        if (!event.isFromGuild() || !PermissionManager.hasPermission(event.getMember(), "admin.welcome")) {
            event.reply(CustomEmojis.ERROR + " You no longer have permission to configure welcome settings.")
                    .setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        String guildId = guild.getId();
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

        switch (action) {
            case "toggle" -> {
                boolean current = Boolean.TRUE.equals(settings.get("welcomeEnabled"));
                boolean next = !current;
                if (next && settings.get("welcomeChannelId") == null) {
                    event.reply(
                            CustomEmojis.WARN + " Please set a welcome channel first (use the **Set Channel** button).")
                            .setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeEnabled", next);
                refreshPanel(event, guildId, guild, userId);
            }
            case "dmtoggle" -> {
                boolean next = !Boolean.TRUE.equals(settings.get("welcomeDMEnabled"));
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeDMEnabled", next);
                refreshPanel(event, guildId, guild, userId);
            }
            case "channel" -> event.replyModal(channelModal(userId, settings)).queue();
            case "message" -> event.replyModal(messageModal(userId, settings)).queue();
            case "color" -> event.replyModal(colorModal(userId, settings)).queue();
            case "autorole" -> event.replyModal(autoRoleModal(userId, settings)).queue();
            case "dmmessage" -> event.replyModal(dmMessageModal(userId, settings)).queue();
            case "test" -> handleTest(event, settings, guild, userId, false);
            case "dmtest" -> handleTest(event, settings, guild, userId, true);
            case "refresh" -> refreshPanel(event, guildId, guild, userId);
            default -> event.reply("Unknown action.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("wgm:"))
            return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3)
            return;
        String action = parts[1];
        String userId = parts[2];

        if (!event.getUser().getId().equals(userId))
            return;
        if (!event.isFromGuild())
            return;

        Guild guild = event.getGuild();
        String guildId = guild.getId();

        switch (action) {
            case "channel" -> {
                String input = event.getValue("channel_input").getAsString().trim();
                // Strip <# > or # prefix
                String chanId = input.replaceAll("[<#>]", "").trim();
                TextChannel tc = null;
                // Try by ID
                try {
                    tc = guild.getTextChannelById(chanId);
                } catch (Exception ignored) {
                }
                // Try by name
                if (tc == null) {
                    String lname = chanId.toLowerCase();
                    tc = guild.getTextChannels().stream()
                            .filter(c -> c.getName().equalsIgnoreCase(lname))
                            .findFirst().orElse(null);
                }
                if (tc == null) {
                    event.reply(
                            CustomEmojis.ERROR + " Channel not found. Use a channel mention `#channel`, name, or ID.")
                            .setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeChannelId", tc.getId());
                event.editMessage(WelcomeCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "message" -> {
                String msg = event.getValue("msg_input").getAsString();
                if (msg.length() > 1000) {
                    event.reply(CustomEmojis.ERROR + " Message too long (max 1000 characters).").setEphemeral(true)
                            .queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeMessage", msg);
                event.editMessage(WelcomeCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "color" -> {
                String color = event.getValue("color_input").getAsString().trim();
                if (!color.startsWith("#"))
                    color = "#" + color;
                if (!color.matches("#[0-9A-Fa-f]{6}")) {
                    event.reply(CustomEmojis.ERROR + " Invalid color. Use hex format like `#FF0000`.")
                            .setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeEmbedColor", color.toUpperCase());
                event.editMessage(WelcomeCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "autorole" -> {
                String input = event.getValue("role_input").getAsString().trim();
                if (input.isEmpty() || input.equalsIgnoreCase("none") || input.equalsIgnoreCase("clear")) {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeAutoRoleId", null);
                    event.editMessage(WelcomeCommand.buildPanelEdit(
                            ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
                    return;
                }
                String roleId = input.replaceAll("[<@&>]", "").trim();
                var role = guild.getRoleById(roleId);
                if (role == null) {
                    String lname = roleId.toLowerCase();
                    role = guild.getRoles().stream()
                            .filter(r -> r.getName().equalsIgnoreCase(lname))
                            .findFirst().orElse(null);
                }
                if (role == null) {
                    event.reply(CustomEmojis.ERROR
                            + " Role not found. Use a role mention `@Role`, name, or ID. Type `clear` to remove.")
                            .setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeAutoRoleId", role.getId());
                event.editMessage(WelcomeCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
            case "dmmessage" -> {
                String msg = event.getValue("msg_input").getAsString();
                if (msg.length() > 1000) {
                    event.reply(CustomEmojis.ERROR + " Message too long (max 1000 characters).").setEphemeral(true)
                            .queue();
                    return;
                }
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeDMMessage", msg);
                event.editMessage(WelcomeCommand.buildPanelEdit(
                        ServerBot.getStorageManager().getGuildSettings(guildId), guild, userId)).queue();
            }
        }
    }

    // Helpers

    private void refreshPanel(ButtonInteractionEvent event, String guildId, Guild guild, String userId) {
        Map<String, Object> fresh = ServerBot.getStorageManager().getGuildSettings(guildId);
        event.editMessage(WelcomeCommand.buildPanelEdit(fresh, guild, userId)).queue();
    }

    private void handleTest(ButtonInteractionEvent event, Map<String, Object> settings, Guild guild, String userId,
            boolean dm) {
        String raw = dm && settings.containsKey("welcomeDMMessage")
                ? (String) settings.get("welcomeDMMessage")
                : (String) settings.getOrDefault("welcomeMessage", "Welcome to {server}, {user}! \uD83C\uDF89");
        String msg = raw
                .replace("{user}", event.getUser().getAsMention())
                .replace("{username}", event.getUser().getEffectiveName())
                .replace("{server}", guild.getName())
                .replace("{membercount}", String.valueOf(guild.getMemberCount()));
        String color = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Integer.parseInt(color.replace("#", ""), 16))
                .setTitle("\uD83D\uDC4B  Welcome" + (dm ? " DM" : "") + "! *(Test)*")
                .setDescription(msg)
                .setThumbnail(dm ? guild.getIconUrl() : event.getUser().getAvatarUrl())
                .setFooter("Member #" + guild.getMemberCount() + " \u2022 Test " + (dm ? "DM" : "preview"));

        if (dm) {
            event.getUser().openPrivateChannel()
                    .flatMap(ch -> ch.sendMessageEmbeds(eb.build()))
                    .queue(
                            s -> event.reply(CustomEmojis.SUCCESS + " Test DM sent to your inbox.").setEphemeral(true)
                                    .queue(),
                            f -> event.reply(CustomEmojis.ERROR + " Could not send DM \u2014 are your DMs open?")
                                    .setEphemeral(true).queue());
        } else {
            event.replyEmbeds(eb.build()).queue();
        }
    }

    // Modals

    private Modal channelModal(String uid, Map<String, Object> settings) {
        String cur = settings.containsKey("welcomeChannelId") ? "<#" + settings.get("welcomeChannelId") + ">" : "";
        TextInput.Builder input = TextInput.create("channel_input", TextInputStyle.SHORT)
                .setPlaceholder("#welcome")
                .setRequired(true)
                .setMaxLength(100);
        if (!cur.isEmpty())
            input.setValue(cur);
        return Modal.create("wgm:channel:" + uid, "Set Welcome Channel")
                .addComponents(Label.of("Channel (#name, name, or ID)", input.build()))
                .build();
    }

    private Modal messageModal(String uid, Map<String, Object> settings) {
        String cur = (String) settings.getOrDefault("welcomeMessage",
                "Welcome to {server}, {user}! We're glad to have you here. \uD83C\uDF89");
        TextInput.Builder input = TextInput.create("msg_input", TextInputStyle.PARAGRAPH)
                .setValue(cur)
                .setRequired(true)
                .setMaxLength(1000);
        return Modal.create("wgm:message:" + uid, "Set Welcome Message")
                .addComponents(Label.of("Message  \u2022  {user} {username} {server} {membercount}", input.build()))
                .build();
    }

    private Modal colorModal(String uid, Map<String, Object> settings) {
        String cur = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");
        TextInput.Builder input = TextInput.create("color_input", TextInputStyle.SHORT)
                .setValue(cur)
                .setPlaceholder("#00FF00")
                .setRequired(true)
                .setMaxLength(7);
        return Modal.create("wgm:color:" + uid, "Set Embed Color")
                .addComponents(Label.of("Hex color code (e.g. #FF0000)", input.build()))
                .build();
    }

    private Modal autoRoleModal(String uid, Map<String, Object> settings) {
        String cur = settings.containsKey("welcomeAutoRoleId") ? "<@&" + settings.get("welcomeAutoRoleId") + ">" : "";
        TextInput.Builder input = TextInput.create("role_input", TextInputStyle.SHORT)
                .setPlaceholder("@Member  or  clear")
                .setRequired(false)
                .setMaxLength(100);
        if (!cur.isEmpty())
            input.setValue(cur);
        return Modal.create("wgm:autorole:" + uid, "Set Auto-Role")
                .addComponents(Label.of("Role (mention, name, ID) \u2014 type 'clear' to remove", input.build()))
                .build();
    }

    private Modal dmMessageModal(String uid, Map<String, Object> settings) {
        String cur = settings.containsKey("welcomeDMMessage")
                ? (String) settings.get("welcomeDMMessage")
                : (String) settings.getOrDefault("welcomeMessage", "Welcome to {server}, {user}! \uD83C\uDF89");
        TextInput.Builder input = TextInput.create("msg_input", TextInputStyle.PARAGRAPH)
                .setValue(cur)
                .setRequired(true)
                .setMaxLength(1000);
        return Modal.create("wgm:dmmessage:" + uid, "Set DM Welcome Message")
                .addComponents(Label.of("DM message (same placeholders apply)", input.build()))
                .build();
    }
}
