package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.models.GlobalChatChannel;
import com.serverbot.services.GlobalChatService;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * /globalchat command — cross-server chat management
 */
public class GlobalChatCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(GlobalChatCommand.class);

    @Override
    public String getName() {
        return "globalchat";
    }

    @Override
    public String getDescription() {
        return "Manage cross-server global chat channels";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.GLOBAL_CHAT;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    } // link/unlink guard themselves; all other subcommands work in DMs

    public static CommandData getCommandData() {
        return Commands.slash("globalchat", "Manage cross-server global chat channels")
                // create
                .addSubcommands(new SubcommandData("create", "Create a new global chat channel")
                        .addOption(OptionType.STRING, "name", "Channel name", true)
                        .addOption(OptionType.STRING, "description", "Channel description", true)
                        .addOption(OptionType.STRING, "visibility", "public or private (default: public)", false)
                        .addOption(OptionType.BOOLEAN, "keyrequired", "Whether a key is required to join", false)
                        .addOption(OptionType.STRING, "key", "Custom join key (auto-generated if not provided)", false)
                        .addOption(OptionType.STRING, "prefix",
                                "Custom message prefix (default: [GC]) — use {} for empty", false)
                        .addOption(OptionType.STRING, "suffix",
                                "Custom message suffix (default: • {server}) — use {} for empty", false)
                        .addOption(OptionType.STRING, "nameformat",
                                "Full display name template e.g. '{user} @ {server}' — overrides prefix/suffix", false)
                        .addOption(OptionType.STRING, "webhookmode",
                                "'webhook' (default) or 'text' — text sends as the bot itself", false))
                // edit
                .addSubcommands(new SubcommandData("edit", "Edit your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "name", "New name", false)
                        .addOption(OptionType.STRING, "description", "New description", false)
                        .addOption(OptionType.STRING, "visibility", "New visibility (public/private)", false)
                        .addOption(OptionType.STRING, "key", "New join key", false)
                        .addOption(OptionType.STRING, "prefix",
                                "New message prefix — 'reset' for default, {} for empty", false)
                        .addOption(OptionType.STRING, "suffix",
                                "New message suffix — 'reset' for default, {} for empty", false)
                        .addOption(OptionType.STRING, "nameformat",
                                "Full display name template — 'reset' to clear, {} for empty", false)
                        .addOption(OptionType.STRING, "webhookmode", "'webhook' or 'text'", false))
                // delete
                .addSubcommands(new SubcommandData("delete", "Delete your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true)))
                // link
                .addSubcommands(new SubcommandData("link", "Link a server channel to a global chat")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOptions(new OptionData(OptionType.STRING, "channel",
                                "Server channel to link (#name or channel ID)", true).setAutoComplete(true))
                        .addOption(OptionType.STRING, "key", "Join key (if required)", false))
                // unlink
                .addSubcommands(new SubcommandData("unlink", "Unlink a server channel from its global chat")
                        .addOptions(new OptionData(OptionType.STRING, "channel",
                                "Server channel to unlink (#name or channel ID)", true).setAutoComplete(true)))
                // info
                .addSubcommands(new SubcommandData("info", "View info about a global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true)))
                // list
                .addSubcommands(new SubcommandData("list", "List your global chat channels"))
                // setrules
                .addSubcommands(new SubcommandData("setrules", "Set rules for your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "rules", "Rules (separate each rule with |)", true))
                // manage
                .addSubcommands(
                        new SubcommandData("manage", "Open the management panel for a global chat channel (DM only)")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                                .setAutoComplete(true)))
                // kick
                .addSubcommands(new SubcommandData("kick", "Kick a server from your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to kick", true)
                        .addOption(OptionType.STRING, "reason", "Reason for kick", false))
                // ban
                .addSubcommands(new SubcommandData("ban", "Ban a server from your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to ban", true)
                        .addOption(OptionType.STRING, "reason", "Reason for ban", false))
                // unban
                .addSubcommands(new SubcommandData("unban", "Unban a server from your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to unban", true))
                // warn
                .addSubcommands(new SubcommandData("warn", "Warn a server in your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to warn", true)
                        .addOption(OptionType.STRING, "reason", "Reason for warning", false))
                // unwarn
                .addSubcommands(new SubcommandData("unwarn", "Clear warnings for a server")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to unwarn", true))
                // mute
                .addSubcommands(new SubcommandData("mute", "Mute a server in your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to mute", true)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g. 1h, 30m, 7d, 0 for permanent)", false)
                        .addOption(OptionType.STRING, "reason", "Reason for mute", false))
                // unmute
                .addSubcommands(new SubcommandData("unmute", "Unmute a server in your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.STRING, "serverid", "Server ID to unmute", true))
                // addmod
                .addSubcommands(new SubcommandData("addmod", "Add a moderator to your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.USER, "user", "User to add as moderator", true))
                // removemod
                .addSubcommands(new SubcommandData("removemod", "Remove a moderator from your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.USER, "user", "User to remove as moderator", true))
                // addcoowner
                .addSubcommands(new SubcommandData("addcoowner", "Add a co-owner to your global chat channel")
                        .addOptions(new OptionData(OptionType.STRING, "channelid", "Global chat channel ID", true)
                                .setAutoComplete(true))
                        .addOption(OptionType.USER, "user", "User to add as co-owner", true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.reply("Unknown subcommand.").setEphemeral(true).queue();
            return;
        }

        GlobalChatService service = ServerBot.getGlobalChatService();

        switch (sub) {
            case "create" -> handleCreate(event, service);
            case "edit" -> handleEdit(event, service);
            case "delete" -> handleDelete(event, service);
            case "link" -> handleLink(event, service);
            case "unlink" -> handleUnlink(event, service);
            case "info" -> handleInfo(event, service);
            case "list" -> handleList(event, service);
            case "setrules" -> handleSetRules(event, service);
            case "manage" -> handleManage(event, service);
            case "kick" -> handleKick(event, service);
            case "ban" -> handleBan(event, service);
            case "unban" -> handleUnban(event, service);
            case "warn" -> handleWarn(event, service);
            case "unwarn" -> handleUnwarn(event, service);
            case "mute" -> handleMute(event, service);
            case "unmute" -> handleUnmute(event, service);
            case "addmod" -> handleAddMod(event, service);
            case "removemod" -> handleRemoveMod(event, service);
            case "addcoowner" -> handleAddCoOwner(event, service);
            default -> event.reply("Unknown subcommand.").setEphemeral(true).queue();
        }
    }

    // create

    private void handleCreate(SlashCommandInteractionEvent event, GlobalChatService service) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String desc = event.getOption("description", OptionMapping::getAsString);
        String vis = event.getOption("visibility", "public", OptionMapping::getAsString);
        boolean keyReq = event.getOption("keyrequired", false, OptionMapping::getAsBoolean);
        String key = event.getOption("key", (String) null, OptionMapping::getAsString);
        String msgPrefix = event.getOption("prefix", (String) null, OptionMapping::getAsString);
        String msgSuffix = event.getOption("suffix", (String) null, OptionMapping::getAsString);
        String nameFormat = event.getOption("nameformat", (String) null, OptionMapping::getAsString);
        String webhookModeStr = event.getOption("webhookmode", (String) null, OptionMapping::getAsString);

        // Handle {} as intentionally empty
        if ("{}".equals(msgPrefix))
            msgPrefix = "";
        if ("{}".equals(msgSuffix))
            msgSuffix = "";
        if ("{}".equals(nameFormat))
            nameFormat = "";

        boolean webhookEnabled = webhookModeStr == null || !webhookModeStr.equalsIgnoreCase("text");

        if (!vis.equalsIgnoreCase("public") && !vis.equalsIgnoreCase("private")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Visibility",
                    "Visibility must be `public` or `private`.")).setEphemeral(true).queue();
            return;
        }

        GlobalChatChannel channel = service.createChannel(name, desc, vis.toLowerCase(), keyReq, key,
                event.getUser().getId(), msgPrefix, msgSuffix, nameFormat, webhookEnabled);

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SUCCESS + " Global Chat Channel Created")
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .addField("Name", channel.getName(), true)
                .addField("ID", "`" + channel.getChannelId() + "`", true)
                .addField("Visibility", channel.getVisibility(), true)
                .setTimestamp(Instant.now());

        if (channel.isKeyRequired()) {
            eb.addField("Key", "`" + channel.getKey() + "`", true);
        }
        if (channel.getMessagePrefix() != null) {
            eb.addField("Prefix",
                    channel.getMessagePrefix().isEmpty() ? "*(empty)*" : "`" + channel.getMessagePrefix() + "`", true);
        }
        if (channel.getMessageSuffix() != null) {
            eb.addField("Suffix",
                    channel.getMessageSuffix().isEmpty() ? "*(empty)*" : "`" + channel.getMessageSuffix() + "`", true);
        }
        if (channel.getNameFormat() != null) {
            eb.addField("Name Format",
                    channel.getNameFormat().isEmpty() ? "*(empty)*" : "`" + channel.getNameFormat() + "`", true);
        }
        eb.addField("Webhook Mode", channel.isWebhookEnabled() ? "webhook" : "text", true);

        // DM the user the channel details (only if not in a guild, or guild has DMs
        // enabled)
        if (!event.isFromGuild() || com.serverbot.utils.DmUtils.areDmsEnabled(event.getGuild())) {
            event.getUser().openPrivateChannel().queue(dm -> {
                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setTitle(CustomEmojis.INFO + " Your New Global Chat Channel")
                        .setColor(EmbedUtils.INFO_COLOR)
                        .addField("Channel ID", "`" + channel.getChannelId() + "`", false)
                        .addField("Name", channel.getName(), true)
                        .addField("Description", channel.getDescription(), false)
                        .addField("Visibility", channel.getVisibility(), true);
                if (channel.isKeyRequired()) {
                    dmEmbed.addField("Key", "`" + channel.getKey() + "`", true);
                }
                String effectivePrefix = channel.getMessagePrefix() != null ? channel.getMessagePrefix() : "[GC]";
                String effectiveSuffix = channel.getMessageSuffix() != null ? channel.getMessageSuffix() : "• {server}";
                if (channel.getNameFormat() != null) {
                    dmEmbed.addField("Name Format (overrides prefix/suffix)", "`" + channel.getNameFormat() + "`",
                            false);
                } else {
                    dmEmbed.addField("Message Format", "`" + effectivePrefix + " {user} " + effectiveSuffix + "`",
                            false);
                }
                dmEmbed.addField("Webhook Mode", channel.isWebhookEnabled() ? "webhook" : "text", true);
                dmEmbed.addField("Valid Placeholders",
                        "`{user}` — server nickname\n`{username}` — Discord username\n`{displayname}` — global display name\n`{server}` — server name\n`{pronouns}` — pronoun roles",
                        false);
                dmEmbed.setFooter("Use /globalchat manage " + channel.getChannelId() + " to manage this channel")
                        .setTimestamp(Instant.now());
                dm.sendMessageEmbeds(dmEmbed.build()).queue(s -> {
                }, err -> {
                });
            }, err -> {
            });
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // edit

    private void handleEdit(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasManageAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String newName = event.getOption("name", (String) null, OptionMapping::getAsString);
        String newDesc = event.getOption("description", (String) null, OptionMapping::getAsString);
        String newVis = event.getOption("visibility", (String) null, OptionMapping::getAsString);
        String newKey = event.getOption("key", (String) null, OptionMapping::getAsString);
        String newPrefix = event.getOption("prefix", (String) null, OptionMapping::getAsString);
        String newSuffix = event.getOption("suffix", (String) null, OptionMapping::getAsString);
        String newNameFormat = event.getOption("nameformat", (String) null, OptionMapping::getAsString);
        String newWebhookMode = event.getOption("webhookmode", (String) null, OptionMapping::getAsString);

        if (newName != null)
            gc.setName(newName);
        if (newDesc != null)
            gc.setDescription(newDesc);
        if (newVis != null) {
            if (!newVis.equalsIgnoreCase("public") && !newVis.equalsIgnoreCase("private")) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Visibility",
                        "Visibility must be `public` or `private`.")).setEphemeral(true).queue();
                return;
            }
            gc.setVisibility(newVis.toLowerCase());
        }
        if (newKey != null) {
            gc.setKey(newKey);
            gc.setKeyRequired(true);
        }
        if (newPrefix != null) {
            if (newPrefix.equalsIgnoreCase("none") || newPrefix.equalsIgnoreCase("default")
                    || newPrefix.equalsIgnoreCase("reset")) {
                gc.setMessagePrefix(null); // reset to default [GC]
            } else if (newPrefix.equals("{}")) {
                gc.setMessagePrefix(""); // intentionally empty
            } else {
                gc.setMessagePrefix(newPrefix);
            }
        }
        if (newSuffix != null) {
            if (newSuffix.equalsIgnoreCase("none") || newSuffix.equalsIgnoreCase("default")
                    || newSuffix.equalsIgnoreCase("reset")) {
                gc.setMessageSuffix(null); // reset to default • {server}
            } else if (newSuffix.equals("{}")) {
                gc.setMessageSuffix(""); // intentionally empty
            } else {
                gc.setMessageSuffix(newSuffix);
            }
        }
        if (newNameFormat != null) {
            if (newNameFormat.equalsIgnoreCase("none") || newNameFormat.equalsIgnoreCase("default")
                    || newNameFormat.equalsIgnoreCase("reset")) {
                gc.setNameFormat(null); // revert to prefix/suffix mode
            } else if (newNameFormat.equals("{}")) {
                gc.setNameFormat(""); // intentionally blank name
            } else {
                gc.setNameFormat(newNameFormat);
            }
        }
        if (newWebhookMode != null) {
            gc.setWebhookEnabled(!newWebhookMode.equalsIgnoreCase("text"));
        }

        service.saveChannels();

        String effectivePrefix = gc.getMessagePrefix() != null ? gc.getMessagePrefix() : "[GC]";
        String effectiveSuffix = gc.getMessageSuffix() != null ? gc.getMessageSuffix() : "• {server}";
        String formatPreview = gc.getNameFormat() != null
                ? "Name Format: `" + gc.getNameFormat() + "`"
                : "`" + effectivePrefix + " {user} " + effectiveSuffix + "`";

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Channel Updated",
                "Global chat channel **" + gc.getName() + "** has been updated.\n\n**Display name:** " + formatPreview +
                        "\n**Webhook mode:** " + (gc.isWebhookEnabled() ? "webhook" : "text")))
                .setEphemeral(true).queue();
    }

    // delete

    private void handleDelete(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }

        String userId = event.getUser().getId();

        // Co-owner attempting delete → notify owner for confirmation
        if (gc.isCoOwner(userId) && !gc.isOwner(userId)) {
            event.getJDA().retrieveUserById(gc.getOwnerId()).queue(owner -> {
                owner.openPrivateChannel().queue(dm -> {
                    dm.sendMessageEmbeds(new EmbedBuilder()
                            .setTitle(CustomEmojis.WARN + " Channel Deletion Request")
                            .setDescription(
                                    "Co-owner <@" + userId + "> has requested to delete your global chat channel **"
                                            + gc.getName() + "** (`" + gc.getChannelId() + "`).\n\nDo you approve?")
                            .setColor(EmbedUtils.WARNING_COLOR)
                            .setTimestamp(Instant.now())
                            .build())
                            .addComponents(ActionRow.of(
                                    Button.danger("gc_confirm_delete:" + channelId, "Confirm Delete"),
                                    Button.secondary("gc_cancel_delete:" + channelId, "Cancel")))
                            .queue(s -> {
                            }, err -> {
                            });
                }, err -> {
                });
            }, err -> {
            });
            event.replyEmbeds(EmbedUtils.createInfoEmbed("Deletion Requested",
                    "The channel owner has been notified and must confirm the deletion.")).setEphemeral(true).queue();
            return;
        }

        if (!gc.isOwner(userId)) {
            replyNoAccess(event);
            return;
        }

        // Notify all linked channels
        for (Map.Entry<String, String> entry : gc.getLinkedChannels().entrySet()) {
            notifyChannel(event.getJDA(), entry.getKey(), entry.getValue(),
                    CustomEmojis.TRASH + " The global chat channel **" + gc.getName()
                            + "** has been deleted. This channel is no longer linked.");
        }

        service.deleteChannel(channelId);
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Channel Deleted",
                "Global chat channel **" + gc.getName() + "** has been deleted.")).setEphemeral(true).queue();
    }

    // link

    private void handleLink(SlashCommandInteractionEvent event, GlobalChatService service) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "This command must be used in a server."))
                    .setEphemeral(true).queue();
            return;
        }

        // Permission check
        if (!PermissionManager.hasPermission(event.getMember(), "globalchat.link")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You need the `globalchat.link` permission to link channels.")).setEphemeral(true).queue();
            return;
        }

        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String channelInput = event.getOption("channel", OptionMapping::getAsString);
        String key = event.getOption("key", (String) null, OptionMapping::getAsString);

        TextChannel target = resolveTextChannel(event.getGuild(), channelInput);
        if (target == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Channel",
                    "Could not find a text channel matching `" + channelInput
                            + "`. Use the autocomplete or paste a channel ID."))
                    .setEphemeral(true).queue();
            return;
        }

        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }

        String error = service.linkChannel(channelId, event.getGuild().getId(), target.getId(), key);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Link Failed", error)).setEphemeral(true).queue();
            return;
        }

        // Send rules if they exist
        service.sendRulesToChannel(channelId, event.getGuild().getId(), event.getJDA());

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Channel Linked",
                target.getAsMention() + " is now linked to global chat **" + gc.getName() + "**.")).setEphemeral(true)
                .queue();
    }

    // unlink

    private void handleUnlink(SlashCommandInteractionEvent event, GlobalChatService service) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "This command must be used in a server."))
                    .setEphemeral(true).queue();
            return;
        }

        String channelInput = event.getOption("channel", OptionMapping::getAsString);
        String globalId = null;

        TextChannel target = resolveTextChannel(event.getGuild(), channelInput);
        if (target == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Channel",
                    "Could not find a text channel matching `" + channelInput
                            + "`. Use the autocomplete or paste a channel ID."))
                    .setEphemeral(true).queue();
            return;
        }

        globalId = service.getGlobalChannelIdByTextChannel(target.getId());

        if (globalId == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Linked",
                    "That channel is not linked to any global chat.")).setEphemeral(true).queue();
            return;
        }

        GlobalChatChannel gc = service.getChannel(globalId);

        // Owner/co-owner can always unlink; others need permission
        boolean isChannelOwner = gc != null && gc.hasManageAccess(event.getUser().getId());
        if (!isChannelOwner && !PermissionManager.hasPermission(event.getMember(), "globalchat.unlink")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You need the `globalchat.unlink` permission to unlink channels.")).setEphemeral(true).queue();
            return;
        }

        String error = service.unlinkChannel(event.getGuild().getId(), target.getId());
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Unlink Failed", error)).setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Channel Unlinked",
                target.getAsMention() + " has been unlinked from global chat" +
                        (gc != null ? " **" + gc.getName() + "**" : "") + "."))
                .setEphemeral(true).queue();
    }

    // info

    private void handleInfo(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }

        // Public channels: anyone can see. Private: only owner/co-owner/mods
        if ("private".equals(gc.getVisibility()) && !gc.hasModerateAccess(event.getUser().getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Private Channel",
                    "This channel is private. Only the owner and moderators can view its info.")).setEphemeral(true)
                    .queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.INFO + " " + gc.getName())
                .setDescription(gc.getDescription())
                .setColor(EmbedUtils.INFO_COLOR)
                .addField("Channel ID", "`" + gc.getChannelId() + "`", true)
                .addField("Visibility", gc.getVisibility(), true)
                .addField("Key Required", gc.isKeyRequired() ? "Yes" : "No", true)
                .addField("Owner", "<@" + gc.getOwnerId() + ">", true)
                .addField("Linked Servers", String.valueOf(gc.getLinkedChannels().size()), true)
                .addField("Moderators", String.valueOf(gc.getModeratorIds().size()), true)
                .setTimestamp(Instant.now());

        String effectivePrefix = gc.getMessagePrefix() != null ? gc.getMessagePrefix() : "[GC]";
        String effectiveSuffix = gc.getMessageSuffix() != null ? gc.getMessageSuffix() : "• {server}";
        if (gc.getNameFormat() != null) {
            eb.addField("Name Format", "`" + gc.getNameFormat() + "` *(overrides prefix/suffix)*", false);
        } else {
            eb.addField("Message Format", "`" + effectivePrefix + " {user} " + effectiveSuffix + "`", false);
        }
        eb.addField("Webhook Mode", gc.isWebhookEnabled() ? "webhook" : "text", true);
        eb.addField("Valid Placeholders",
                "`{user}` — server nickname\n`{username}` — Discord username\n`{displayname}` — global display name\n`{server}` — server name\n`{pronouns}` — pronoun roles",
                false);

        if (!gc.getRules().isEmpty()) {
            eb.addField("Rules", service.formatRules(gc), false);
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // list

    private void handleList(SlashCommandInteractionEvent event, GlobalChatService service) {
        List<GlobalChatChannel> owned = service.getChannelsByOwner(event.getUser().getId());
        if (owned.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed("No Channels",
                    "You don't own or co-own any global chat channels.\nUse `/globalchat create` to make one!"))
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.INFO + " Your Global Chat Channels")
                .setColor(EmbedUtils.INFO_COLOR)
                .setTimestamp(Instant.now());

        for (GlobalChatChannel gc : owned) {
            String role = gc.isOwner(event.getUser().getId()) ? "Owner" : "Co-Owner";
            eb.addField(gc.getName() + " (`" + gc.getChannelId() + "`)",
                    gc.getDescription() + "\n" +
                            "Visibility: " + gc.getVisibility() +
                            " | Linked: " + gc.getLinkedChannels().size() +
                            " | Role: " + role,
                    false);
        }

        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // setrules

    private void handleSetRules(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasManageAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String rulesRaw = event.getOption("rules", OptionMapping::getAsString);
        List<String> rules = Arrays.stream(rulesRaw.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        service.setRules(channelId, rules, event.getJDA());
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Rules Updated",
                "Rules for **" + gc.getName() + "** have been updated.\n" + service.formatRules(gc))).setEphemeral(true)
                .queue();
    }

    // manage (DM panel)

    private void handleManage(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        boolean isOwnerOrCoOwner = gc.hasManageAccess(event.getUser().getId());

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Manage: " + gc.getName())
                .setDescription(
                        "Use the buttons below to manage this global chat channel.\n" +
                                "**ID:** `" + gc.getChannelId() + "`\n" +
                                "**Linked Servers:** " + gc.getLinkedChannels().size() + "\n" +
                                "**Visibility:** " + gc.getVisibility() + "\n\n" +
                                "*All actions open a form — no need to type in chat.*")
                .setColor(EmbedUtils.INFO_COLOR)
                .setTimestamp(Instant.now());

        List<Button> row1 = new ArrayList<>();
        List<Button> row2 = new ArrayList<>();
        List<Button> row3 = new ArrayList<>();

        if (isOwnerOrCoOwner) {
            row1.add(Button.primary("gc_edit:" + channelId, "Edit Channel")
                    .withEmoji(Emoji.fromFormatted(CustomEmojis.NOTE)));
            row1.add(Button.primary("gc_display:" + channelId, "Display Format").withEmoji(Emoji.fromUnicode("🎨")));
            row1.add(Button.primary("gc_setrules:" + channelId, "Set Rules")
                    .withEmoji(Emoji.fromFormatted(CustomEmojis.SAVE)));
            row1.add(Button.primary("gc_addmod:" + channelId, "Add Mod").withEmoji(Emoji.fromUnicode("🛡️")));
            row1.add(Button.danger("gc_delete:" + channelId, "Delete")
                    .withEmoji(Emoji.fromFormatted(CustomEmojis.TRASH)));
        }

        row2.add(Button.danger("gc_kick:" + channelId, "Kick Server")
                .withEmoji(Emoji.fromFormatted(CustomEmojis.MOD_BAN)));
        row2.add(Button.danger("gc_ban:" + channelId, "Ban Server").withEmoji(Emoji.fromFormatted(CustomEmojis.ERROR)));
        row2.add(Button.secondary("gc_warn:" + channelId, "Warn Server")
                .withEmoji(Emoji.fromFormatted(CustomEmojis.WARN)));
        row2.add(Button.secondary("gc_mute:" + channelId, "Mute Server").withEmoji(Emoji.fromUnicode("🔇")));

        row3.add(Button.secondary("gc_unmute:" + channelId, "Unmute Server").withEmoji(Emoji.fromUnicode("🔊")));
        row3.add(Button.secondary("gc_unwarn:" + channelId, "Unwarn Server")
                .withEmoji(Emoji.fromFormatted(CustomEmojis.SUCCESS)));
        row3.add(Button.primary("gc_linked:" + channelId, "View Linked")
                .withEmoji(Emoji.fromFormatted(CustomEmojis.TAG)));

        // Send to DMs
        event.getUser().openPrivateChannel().queue(dm -> {
            var msgAction = dm.sendMessageEmbeds(eb.build());
            if (!row1.isEmpty())
                msgAction = msgAction.addComponents(ActionRow.of(row1));
            msgAction = msgAction.addComponents(ActionRow.of(row2)).addComponents(ActionRow.of(row3));
            msgAction.queue(s -> {
            }, err -> {
            });
        }, err -> {
        });

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Management Panel Sent",
                "The management panel has been sent to your DMs.")).setEphemeral(true).queue();
    }

    // Moderation subcommands

    private void handleKick(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        String reason = event.getOption("reason", (String) null, OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String error = service.kickServer(channelId, serverId, reason, event.getJDA());
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Server Kicked", "Server `" + serverId + "` has been kicked."))
                .setEphemeral(true).queue();
    }

    private void handleBan(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        String reason = event.getOption("reason", (String) null, OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String error = service.banServer(channelId, serverId, reason, event.getJDA());
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Server Banned", "Server `" + serverId + "` has been banned."))
                .setEphemeral(true).queue();
    }

    private void handleUnban(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasManageAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String error = service.unbanServer(channelId, serverId);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(
                EmbedUtils.createSuccessEmbed("Server Unbanned", "Server `" + serverId + "` has been unbanned."))
                .setEphemeral(true).queue();
    }

    private void handleWarn(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        String reason = event.getOption("reason", (String) null, OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String error = service.warnServer(channelId, serverId, reason, event.getJDA());
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Server Warned", "Server `" + serverId + "` has been warned."))
                .setEphemeral(true).queue();
    }

    private void handleUnwarn(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String error = service.unwarnServer(channelId, serverId);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Warnings Cleared",
                "Warnings for server `" + serverId + "` have been cleared.")).setEphemeral(true).queue();
    }

    private void handleMute(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        String durationStr = event.getOption("duration", "0", OptionMapping::getAsString);
        String reason = event.getOption("reason", (String) null, OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        long durationMs = parseDuration(durationStr);

        String error = service.muteServer(channelId, serverId, durationMs, reason, event.getJDA());
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Server Muted",
                "Server `" + serverId + "` has been muted" + (durationMs <= 0 ? " permanently" : " for " + durationStr)
                        + "."))
                .setEphemeral(true).queue();
    }

    private void handleUnmute(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        String serverId = event.getOption("serverid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasModerateAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String error = service.unmuteServer(channelId, serverId, event.getJDA());
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(
                EmbedUtils.createSuccessEmbed("Server Unmuted", "Server `" + serverId + "` has been unmuted."))
                .setEphemeral(true).queue();
    }

    private void handleAddMod(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasManageAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String userId = event.getOption("user", OptionMapping::getAsUser).getId();
        String error = service.addModerator(channelId, userId);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(
                EmbedUtils.createSuccessEmbed("Moderator Added", "<@" + userId + "> has been added as a moderator."))
                .setEphemeral(true).queue();
    }

    private void handleRemoveMod(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.hasManageAccess(event.getUser().getId())) {
            replyNoAccess(event);
            return;
        }

        String userId = event.getOption("user", OptionMapping::getAsUser).getId();
        String error = service.removeModerator(channelId, userId);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Moderator Removed",
                "<@" + userId + "> has been removed as a moderator.")).setEphemeral(true).queue();
    }

    private void handleAddCoOwner(SlashCommandInteractionEvent event, GlobalChatService service) {
        String channelId = event.getOption("channelid", OptionMapping::getAsString);
        GlobalChatChannel gc = service.getChannel(channelId);
        if (gc == null) {
            replyNotFound(event);
            return;
        }
        if (!gc.isOwner(event.getUser().getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Access", "Only the channel owner can add co-owners."))
                    .setEphemeral(true).queue();
            return;
        }

        String userId = event.getOption("user", OptionMapping::getAsUser).getId();
        String error = service.addCoOwner(channelId, userId);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", error)).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(
                EmbedUtils.createSuccessEmbed("Co-Owner Added", "<@" + userId + "> has been added as a co-owner."))
                .setEphemeral(true).queue();
    }

    // Helpers

    private void replyNotFound(SlashCommandInteractionEvent event) {
        event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found",
                "Global chat channel not found. Check the channel ID.")).setEphemeral(true).queue();
    }

    private void replyNoAccess(SlashCommandInteractionEvent event) {
        event.replyEmbeds(EmbedUtils.createErrorEmbed("No Access",
                "You don't have permission to perform this action on this global chat channel.")).setEphemeral(true)
                .queue();
    }

    private void notifyChannel(net.dv8tion.jda.api.JDA jda, String guildId, String textChannelId, String message) {
        if (textChannelId == null)
            return;
        net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
        if (guild == null)
            return;
        TextChannel tc = guild.getTextChannelById(textChannelId);
        if (tc == null)
            return;
        tc.sendMessageEmbeds(EmbedUtils.createInfoEmbed("Global Chat", message)).queue(s -> {
        }, err -> {
        });
    }

    private long parseDuration(String input) {
        if (input == null || input.equals("0"))
            return 0;
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

    /**
     * Resolve a user-supplied channel string (ID, <#id>, or #name) to a TextChannel
     * in the given guild.
     */
    private static TextChannel resolveTextChannel(Guild guild, String input) {
        if (guild == null || input == null || input.isBlank())
            return null;
        // Strip <#...> mention or leading #
        String id = input.trim().replaceAll("[<#>]", "");
        // Try by ID first
        try {
            TextChannel tc = guild.getTextChannelById(id);
            if (tc != null)
                return tc;
        } catch (NumberFormatException ignored) {
        }
        // Try by name (case-insensitive)
        String lower = id.toLowerCase();
        return guild.getTextChannels().stream()
                .filter(tc -> tc.getName().equalsIgnoreCase(lower))
                .findFirst().orElse(null);
    }

    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        GlobalChatService service = ServerBot.getGlobalChatService();
        String sub = event.getSubcommandName();
        String focused = event.getFocusedOption().getName();
        String input = event.getFocusedOption().getValue().toLowerCase();

        if ("channelid".equals(focused)) {
            // For link / info: show public channels + channels the user manages
            // For management subcommands: only channels the user owns/co-owns
            List<GlobalChatChannel> candidates;
            if ("link".equals(sub) || "info".equals(sub)) {
                // Merge owned + public (owned first, no duplicates)
                List<GlobalChatChannel> owned = service.getChannelsByOwner(event.getUser().getId());
                List<GlobalChatChannel> pub = service.getPublicChannels();
                Set<String> seen = new java.util.LinkedHashSet<>();
                candidates = new ArrayList<>();
                for (GlobalChatChannel gc : owned) {
                    if (seen.add(gc.getChannelId()))
                        candidates.add(gc);
                }
                for (GlobalChatChannel gc : pub) {
                    if (seen.add(gc.getChannelId()))
                        candidates.add(gc);
                }
            } else {
                candidates = service.getChannelsByOwner(event.getUser().getId());
            }

            List<Command.Choice> choices = candidates.stream()
                    .filter(gc -> input.isEmpty()
                            || gc.getName().toLowerCase().contains(input)
                            || gc.getChannelId().toLowerCase().contains(input))
                    .limit(25)
                    .map(gc -> new Command.Choice(gc.getName() + " (" + gc.getChannelId() + ")", gc.getChannelId()))
                    .collect(Collectors.toList());

            event.replyChoices(choices).queue();

        } else if ("channel".equals(focused)) {
            // Server text channel autocomplete — only valid in guilds
            if (!event.isFromGuild()) {
                event.replyChoices().queue();
                return;
            }
            Guild guild = event.getGuild();
            Member member = event.getMember();

            List<Command.Choice> choices;
            if ("unlink".equals(sub)) {
                // Only show channels that are currently linked to a global chat
                choices = guild.getTextChannels().stream()
                        .filter(tc -> service.getGlobalChannelIdByTextChannel(tc.getId()) != null)
                        .filter(tc -> input.isEmpty()
                                || tc.getName().toLowerCase().contains(input)
                                || tc.getId().contains(input))
                        .limit(25)
                        .map(tc -> new Command.Choice("#" + tc.getName(), tc.getId()))
                        .collect(Collectors.toList());
            } else {
                // link: show text channels the member has MANAGE_CHANNEL or can at least see
                choices = guild.getTextChannels().stream()
                        .filter(tc -> member == null
                                || member.hasPermission(tc, Permission.MANAGE_CHANNEL)
                                || tc.canTalk(member))
                        .filter(tc -> input.isEmpty()
                                || tc.getName().toLowerCase().contains(input)
                                || tc.getId().contains(input))
                        .limit(25)
                        .map(tc -> new Command.Choice("#" + tc.getName(), tc.getId()))
                        .collect(Collectors.toList());
            }
            event.replyChoices(choices).queue();

        } else {
            event.replyChoices().queue();
        }
    }
}
