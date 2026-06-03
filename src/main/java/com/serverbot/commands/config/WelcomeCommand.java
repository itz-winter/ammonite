package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
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
 * Welcome system configuration.
 * Running /welcome with no action opens an ephemeral GUI panel.
 * Toggle actions (enable, dm-enable) flip current state — no extra boolean
 * needed.
 */
public class WelcomeCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "Servers only.")).setEphemeral(true).queue();
            return;
        }
        if (!PermissionManager.hasPermission(event.getMember(), "admin.welcome")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You need `admin.welcome` to configure welcome settings.")).setEphemeral(true).queue();
            return;
        }

        OptionMapping actionOpt = event.getOption("action");
        if (actionOpt == null) {
            openPanel(event);
            return;
        }

        switch (actionOpt.getAsString()) {
            case "enable" -> handleEnable(event);
            case "message" -> handleMessage(event);
            case "color" -> handleEmbedColor(event);
            case "channel" -> handleChannel(event);
            case "auto-role" -> handleAutoRole(event);
            case "test" -> handleTest(event);
            case "dm-enable" -> handleDMEnable(event);
            case "dm-message" -> handleDMMessage(event);
            case "dm-test" -> handleDMTest(event);
            default -> openPanel(event);
        }
    }

    // Panel

    private void openPanel(SlashCommandInteractionEvent event) {
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId());
        event.reply(buildPanel(settings, event.getGuild(), event.getUser().getId())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
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
        boolean chEnabled = Boolean.TRUE.equals(settings.get("welcomeEnabled"));
        boolean dmEnabled = Boolean.TRUE.equals(settings.get("welcomeDMEnabled"));
        String channelId = (String) settings.get("welcomeChannelId");
        String autoRoleId = (String) settings.get("welcomeAutoRoleId");
        String message = (String) settings.getOrDefault("welcomeMessage",
                "Welcome to {server}, {user}! We're glad to have you here. \uD83C\uDF89");
        String dmMessage = (String) settings.get("welcomeDMMessage");
        String colorHex = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");

        int colorInt;
        try {
            colorInt = Integer.parseInt(colorHex.replace("#", ""), 16);
        } catch (Exception e) {
            colorInt = 0x00FF00;
        }

        String chanDisplay = channelId != null ? "<#" + channelId + ">" : "*Not set*";
        String roleDisplay = autoRoleId != null ? "<@&" + autoRoleId + ">" : "*None*";
        String msgPreview = message.length() > 90 ? message.substring(0, 87) + "\u2026" : message;
        String dmPreview = dmMessage != null
                ? (dmMessage.length() > 90 ? dmMessage.substring(0, 87) + "\u2026" : dmMessage)
                : "*Uses channel message as fallback*";

        return new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + "  Welcome System \u2014 " + guild.getName())
                .setColor(colorInt)
                .setThumbnail(guild.getIconUrl())
                .addField("\uD83D\uDCE2  Channel Welcome",
                        (chEnabled ? CustomEmojis.ON : CustomEmojis.OFF)
                                + "  **Status**  |  Channel: " + chanDisplay,
                        false)
                .addField(CustomEmojis.NOTE + "  Welcome Message",
                        "`" + msgPreview + "`", false)
                .addField("\uD83C\uDFA8  Embed Color", "**" + colorHex.toUpperCase() + "**", true)
                .addField("\uD83D\uDC65  Auto-Role", roleDisplay, true)
                .addField("\uD83D\uDCE8  DM Welcome",
                        (dmEnabled ? CustomEmojis.ON : CustomEmojis.OFF)
                                + "  **Status**  |  `" + dmPreview + "`",
                        false)
                .addField("\uD83D\uDCCB  Placeholders",
                        "`{user}`  `{username}`  `{server}`  `{membercount}`", false)
                .setFooter("Use buttons to configure  \u2022  Changes apply immediately");
    }

    public static List<ActionRow> buildPanelRows(Map<String, Object> settings, String uid) {
        boolean chEnabled = Boolean.TRUE.equals(settings.get("welcomeEnabled"));
        boolean dmEnabled = Boolean.TRUE.equals(settings.get("welcomeDMEnabled"));

        Emoji ON_E = Emoji.fromFormatted(CustomEmojis.ON);
        Emoji OFF_E = Emoji.fromFormatted(CustomEmojis.OFF);
        Emoji NOTE_E = Emoji.fromFormatted(CustomEmojis.NOTE);
        Emoji REF_E = Emoji.fromFormatted(CustomEmojis.REFRESH);

        Button toggleBtn = chEnabled
                ? Button.danger("wgui:toggle:" + uid, "Channel: ON").withEmoji(ON_E)
                : Button.success("wgui:toggle:" + uid, "Channel: OFF").withEmoji(OFF_E);
        Button dmToggleBtn = dmEnabled
                ? Button.danger("wgui:dmtoggle:" + uid, "DM: ON").withEmoji(ON_E)
                : Button.success("wgui:dmtoggle:" + uid, "DM: OFF").withEmoji(OFF_E);

        return List.of(
                ActionRow.of(
                        toggleBtn,
                        Button.secondary("wgui:channel:" + uid, "Set Channel")
                                .withEmoji(Emoji.fromUnicode("\uD83D\uDCE2")),
                        Button.secondary("wgui:message:" + uid, "Set Message").withEmoji(NOTE_E),
                        Button.secondary("wgui:color:" + uid, "Set Color").withEmoji(Emoji.fromUnicode("\uD83C\uDFA8")),
                        Button.secondary("wgui:autorole:" + uid, "Auto-Role")
                                .withEmoji(Emoji.fromUnicode("\uD83D\uDC65"))),
                ActionRow.of(
                        dmToggleBtn,
                        Button.secondary("wgui:dmmessage:" + uid, "DM Message").withEmoji(NOTE_E),
                        Button.secondary("wgui:test:" + uid, "Test Welcome")
                                .withEmoji(Emoji.fromUnicode("\uD83D\uDD04")),
                        Button.secondary("wgui:dmtest:" + uid, "Test DM").withEmoji(Emoji.fromUnicode("\uD83D\uDCEC")),
                        Button.secondary("wgui:refresh:" + uid, "Refresh").withEmoji(REF_E)));
    }

    // Quick actions

    public void handleEnable(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> s = ServerBot.getStorageManager().getGuildSettings(guildId);
            OptionMapping opt = event.getOption("enabled");
            boolean enabled = opt != null ? opt.getAsBoolean() : !Boolean.TRUE.equals(s.get("welcomeEnabled"));

            if (enabled && s.get("welcomeChannelId") == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("No Channel Set",
                        "Set a channel first with `/welcome action:channel`.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }
            ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeEnabled", enabled);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Channel Welcome " + (enabled ? "Enabled " + CustomEmojis.ON : "Disabled " + CustomEmojis.OFF),
                    "Channel welcome messages are now " + (enabled ? "**on**." : "**off**."))).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleMessage(SlashCommandInteractionEvent event) {
        OptionMapping opt = event.getOption("text");
        if (opt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Text",
                    "Usage: `/welcome action:message text:<message>`\n"
                            + "Placeholders: `{user}` `{username}` `{server}` `{membercount}`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        String msg = opt.getAsString();
        if (msg.length() > 1000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Too Long", "Must be \u2264 1000 characters."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeMessage", msg);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Welcome Message Updated",
                    msg.length() > 200 ? msg.substring(0, 197) + "\u2026" : msg)).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleEmbedColor(SlashCommandInteractionEvent event) {
        OptionMapping opt = event.getOption("color");
        if (opt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Color",
                    "Usage: `/welcome action:color color:#RRGGBB`")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        String color = opt.getAsString().trim();
        if (!color.startsWith("#"))
            color = "#" + color;
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Color", "Use hex format: `#FF0000`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeEmbedColor",
                    color.toUpperCase());
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Color Updated",
                    "Embed color \u2192 **" + color.toUpperCase() + "**")).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleChannel(SlashCommandInteractionEvent event) {
        OptionMapping opt = event.getOption("channel");
        if (opt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Channel",
                    "Usage: `/welcome action:channel channel:#channel`")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        try {
            TextChannel ch = opt.getAsChannel().asTextChannel();
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeChannelId", ch.getId());
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Welcome Channel Set",
                    "Welcome messages \u2192 " + ch.getAsMention())).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Channel", "Could not resolve that channel."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleAutoRole(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            OptionMapping opt = event.getOption("role");
            if (opt == null) {
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeAutoRoleId", null);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Auto-Role Cleared",
                        "New members will no longer receive an automatic role.")).queue();
            } else {
                Role role = opt.getAsRole();
                ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeAutoRoleId", role.getId());
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Auto-Role Set",
                        "New members will receive " + role.getAsMention() + ".")).queue();
            }
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleTest(SlashCommandInteractionEvent event) {
        try {
            Map<String, Object> s = ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId());
            String raw = (String) s.getOrDefault("welcomeMessage",
                    "Welcome to {server}, {user}! We're glad to have you here. \uD83C\uDF89");
            String msg = applyPlaceholders(raw, event);
            String color = (String) s.getOrDefault("welcomeEmbedColor", "#00FF00");
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Integer.parseInt(color.replace("#", ""), 16))
                    .setTitle("\uD83D\uDC4B  Welcome! *(Test Preview)*")
                    .setDescription(msg)
                    .setThumbnail(event.getUser().getAvatarUrl())
                    .setFooter("Member #" + event.getGuild().getMemberCount() + " \u2022 This is a test preview");
            event.replyEmbeds(eb.build()).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Test Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleDMEnable(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> s = ServerBot.getStorageManager().getGuildSettings(guildId);
            OptionMapping opt = event.getOption("enabled");
            boolean enabled = opt != null ? opt.getAsBoolean() : !Boolean.TRUE.equals(s.get("welcomeDMEnabled"));
            ServerBot.getStorageManager().updateGuildSettings(guildId, "welcomeDMEnabled", enabled);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "DM Welcome " + (enabled ? "Enabled " + CustomEmojis.ON : "Disabled " + CustomEmojis.OFF),
                    "DM welcome messages are now " + (enabled ? "**on**." : "**off**."))).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleDMMessage(SlashCommandInteractionEvent event) {
        OptionMapping opt = event.getOption("text");
        if (opt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Text",
                    "Usage: `/welcome action:dm-message text:<message>`")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        String msg = opt.getAsString();
        if (msg.length() > 1000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Too Long", "Must be \u2264 1000 characters."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "welcomeDMMessage", msg);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("DM Message Updated",
                    msg.length() > 200 ? msg.substring(0, 197) + "\u2026" : msg)).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public void handleDMTest(SlashCommandInteractionEvent event) {
        try {
            Map<String, Object> s = ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId());
            String raw = s.containsKey("welcomeDMMessage")
                    ? (String) s.get("welcomeDMMessage")
                    : (String) s.getOrDefault("welcomeMessage",
                            "Welcome to {server}, {user}! \uD83C\uDF89");
            String msg = applyPlaceholders(raw, event);
            String color = (String) s.getOrDefault("welcomeEmbedColor", "#00FF00");
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Integer.parseInt(color.replace("#", ""), 16))
                    .setTitle("\uD83D\uDC4B  Welcome DM! *(Test)*")
                    .setDescription(msg)
                    .setThumbnail(event.getGuild().getIconUrl())
                    .setFooter("Member #" + event.getGuild().getMemberCount() + " \u2022 Test DM");
            event.getUser().openPrivateChannel()
                    .flatMap(ch -> ch.sendMessageEmbeds(eb.build()))
                    .queue(
                            suc -> event.replyEmbeds(EmbedUtils.createSuccessEmbed("DM Sent",
                                    "Test DM sent to your inbox.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue(),
                            fail -> event.replyEmbeds(EmbedUtils.createErrorEmbed("DM Failed",
                                    "Could not send DM \u2014 are your DMs open?")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue());
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Test Failed", e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    // Helpers

    public static String applyPlaceholders(String template, SlashCommandInteractionEvent event) {
        return template
                .replace("{user}", event.getUser().getAsMention())
                .replace("{username}", event.getUser().getEffectiveName())
                .replace("{server}", event.getGuild().getName())
                .replace("{membercount}", String.valueOf(event.getGuild().getMemberCount()));
    }

    // Metadata

    @Override
    public String getName() {
        return "welcome";
    }

    @Override
    public String getDescription() {
        return "Configure welcome messages \u2014 run with no args for the GUI panel";
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
                .addChoice("Toggle Channel Welcome", "enable")
                .addChoice("Set Message", "message")
                .addChoice("Set Embed Color", "color")
                .addChoice("Set Channel", "channel")
                .addChoice("Set Auto-Role", "auto-role")
                .addChoice("Test Welcome", "test")
                .addChoice("Toggle DM Welcome", "dm-enable")
                .addChoice("Set DM Message", "dm-message")
                .addChoice("Test DM Welcome", "dm-test");

        return Commands
                .slash("welcome", "Configure welcome messages and auto-roles \u2014 run with no args for the GUI panel")
                .addOptions(
                        actionOpt,
                        new OptionData(OptionType.STRING, "text", "Message text (for message/dm-message)", false),
                        new OptionData(OptionType.STRING, "color", "Hex color e.g. #FF0000 (for color)", false),
                        new OptionData(OptionType.ROLE, "role", "Role for new members, omit to clear", false),
                        new OptionData(OptionType.CHANNEL, "channel", "Welcome channel (for channel action)", false),
                        new OptionData(OptionType.BOOLEAN, "enabled", "Explicitly on/off \u2014 omit to toggle",
                                false));
    }
}
