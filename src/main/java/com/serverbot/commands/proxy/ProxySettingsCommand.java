package com.serverbot.commands.proxy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.models.ProxyMember;
import com.serverbot.models.ProxySettings;
import com.serverbot.services.ProxyService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.CustomEmojis;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import java.awt.Color;
import java.util.List;

/**
 * Command for managing proxy settings and switching members
 */
public class ProxySettingsCommand implements SlashCommand {

    private final ProxyService proxyService;

    public ProxySettingsCommand() {
        this.proxyService = ServerBot.getProxyService();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error",
                    "Please specify a subcommand.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        switch (subcommand) {
            case "view":
                handleView(event);
                break;
            case "autoproxy":
                handleAutoproxy(event);
                break;
            case "toggle":
                handleToggle(event);
                break;
            case "switch":
                handleSwitch(event);
                break;
            default:
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Error",
                        "Unknown subcommand.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleView(SlashCommandInteractionEvent event) {
        // Use "global" as guildId for DM channels
        String guildId = event.getGuild() != null ? event.getGuild().getId() : "global";

        ProxySettings settings = proxyService.getSettings(
                event.getUser().getId(),
                guildId);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " Proxy Settings")
                .setColor(Color.BLUE)
                .addField("Proxy Enabled", settings.isProxyEnabled() ? "Yes" : "No", true)
                .addField("Autoproxy Mode", settings.getAutoproxyMode().toString(), true)
                .addField("Show Indicator", settings.isShowProxyIndicator() ? "Yes" : "No", true)
                .addField("Case Sensitive Tags", settings.isCaseSensitiveTags() ? "Yes" : "No", true);

        if (settings.getLastProxiedMemberId() != null) {
            ProxyMember lastMember = proxyService.getMember(settings.getLastProxiedMemberId());
            if (lastMember != null) {
                embed.addField("Last Fronter", lastMember.getName(), true);
            }
        }

        if (settings.getAutoproxyMode() == ProxySettings.AutoproxyMode.MEMBER &&
                settings.getAutoproxyMemberId() != null) {
            ProxyMember autoproxyMember = proxyService.getMember(settings.getAutoproxyMemberId());
            if (autoproxyMember != null) {
                embed.addField("Autoproxy Member", autoproxyMember.getName(), true);
            }
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    private void handleAutoproxy(SlashCommandInteractionEvent event) {
        String mode = event.getOption("mode").getAsString().toUpperCase();
        String memberName = event.getOption("member") != null ? event.getOption("member").getAsString() : null;

        // Use "global" as guildId for DM channels
        String guildId = event.getGuild() != null ? event.getGuild().getId() : "global";

        ProxySettings settings = proxyService.getSettings(
                event.getUser().getId(),
                guildId);

        try {
            ProxySettings.AutoproxyMode autoproxyMode = ProxySettings.AutoproxyMode.valueOf(mode);
            settings.setAutoproxyMode(autoproxyMode);

            if (autoproxyMode == ProxySettings.AutoproxyMode.MEMBER) {
                if (memberName == null) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Error",
                            "You must specify a member name for MEMBER autoproxy mode.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    return;
                }

                ProxyMember member = proxyService.getMemberByName(
                        event.getUser().getId(),
                        guildId,
                        memberName);

                if (member == null) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Error 710",
                            "Proxy member not found. Use `/proxy list` to see your members.")).setEphemeral(true)
                            .queue();
                    return;
                }

                settings.setAutoproxyMemberId(member.getMemberId());
            }

            event.deferReply().queue();

            proxyService.updateSettings(
                    event.getUser().getId(),
                    event.getGuild().getId(),
                    settings).thenAccept(result -> {
                        if (result.equals("SUCCESS")) {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                    "Autoproxy Updated",
                                    "Autoproxy mode set to: **" + autoproxyMode.toString() + "**")).queue();
                        } else {
                            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                    "Error " + result,
                                    "Failed to update settings. Use `/error category:7` for full proxy system documentation."))
                                    .queue();
                        }
                    });

        } catch (IllegalArgumentException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error",
                    "Invalid autoproxy mode. Valid modes: OFF, FRONT, LATCH, MEMBER, STICKY")).setEphemeral(true)
                    .queue();
        }
    }

    private void handleToggle(SlashCommandInteractionEvent event) {
        String setting = event.getOption("setting").getAsString().toLowerCase();
        boolean value = event.getOption("enabled").getAsBoolean();

        // Use "global" as guildId for DM channels
        String guildId = event.getGuild() != null ? event.getGuild().getId() : "global";

        ProxySettings settings = proxyService.getSettings(
                event.getUser().getId(),
                guildId);

        switch (setting) {
            case "proxy":
                settings.setProxyEnabled(value);
                break;
            case "indicator":
                settings.setShowProxyIndicator(value);
                break;
            case "casesensitive":
                settings.setCaseSensitiveTags(value);
                break;
            default:
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Error",
                        "Invalid setting. Valid settings: proxy, indicator, casesensitive")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
        }

        event.deferReply().queue();

        proxyService.updateSettings(
                event.getUser().getId(),
                guildId,
                settings).thenAccept(result -> {
                    if (result.equals("SUCCESS")) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Setting Updated",
                                "**" + setting + "** set to: **" + (value ? "Enabled" : "Disabled") + "**")).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to update settings. Use `/error category:7` for full proxy system documentation."))
                                .queue();
                    }
                });
    }

    private void handleSwitch(SlashCommandInteractionEvent event) {
        String memberName = event.getOption("member") != null ? event.getOption("member").getAsString() : null;

        // Use "global" as guildId for DM channels
        String guildId = event.getGuild() != null ? event.getGuild().getId() : "global";

        if (memberName == null) {
            // Switch out (clear)
            ProxySettings settings = proxyService.getSettings(
                    event.getUser().getId(),
                    guildId);
            settings.setLastProxiedMemberId(null);
            settings.setLastSwitchTime(null);

            event.deferReply().queue();

            proxyService.updateSettings(
                    event.getUser().getId(),
                    event.getGuild().getId(),
                    settings).thenAccept(result -> {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Switched Out",
                                "Cleared current fronter.")).queue();
                    });
            return;
        }

        // Switch to member
        ProxyMember member = proxyService.getMemberByName(
                event.getUser().getId(),
                guildId,
                memberName);

        if (member == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error 710",
                    "Proxy member not found. Use `/proxy list` to see your members.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        proxyService.switchMember(
                event.getUser().getId(),
                guildId,
                member.getMemberId()).thenAccept(result -> {
                    if (result.equals("SUCCESS")) {
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle(CustomEmojis.SUCCESS + "Switched")
                                .setDescription("Now fronting: **" + member.getName() + "**")
                                .setColor(Color.GREEN);

                        if (member.getAvatarUrl() != null) {
                            embed.setThumbnail(member.getAvatarUrl());
                        }

                        event.getHook().editOriginalEmbeds(embed.build()).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to switch. Use `/error category:7` for full proxy system documentation."))
                                .queue();
                    }
                });
    }

    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String focusedOption = event.getFocusedOption().getName();
        
        switch (focusedOption) {
            case "member":
                // Get user's proxy members for autocomplete
                String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
                ProxyService proxyService = ServerBot.getProxyService();
                List<ProxyMember> members = proxyService.getUserMembers(event.getUser().getId(), guildId);
                String typed = event.getFocusedOption().getValue().toLowerCase();
                
                // Filter and limit to 25 choices (Discord limit)
                List<Command.Choice> choices = members.stream()
                    .filter(member -> 
                        member.getName().toLowerCase().startsWith(typed) ||
                        (member.getDisplayName() != null && member.getDisplayName().toLowerCase().startsWith(typed))
                    )
                    .limit(25)
                    .map(member -> {
                        String choiceName = member.getDisplayName() != null ? 
                            member.getDisplayName() + " (" + member.getName() + ")" : 
                            member.getName();
                        return new Command.Choice(choiceName, member.getName());
                    })
                    .toList();
                
                event.replyChoices(choices).queue();
                break;
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("proxysettings", "Manage proxy settings and switch members")
                .addSubcommands(
                        new SubcommandData("view", "View your current proxy settings"),

                        new SubcommandData("autoproxy", "Set autoproxy mode")
                                .addOption(OptionType.STRING, "mode",
                                        "Autoproxy mode (OFF, FRONT, LATCH, MEMBER, STICKY)", true)
                                .addOption(OptionType.STRING, "member", "Member name (required for MEMBER mode)",
                                        false),

                        new SubcommandData("toggle", "Toggle a proxy setting")
                                .addOption(OptionType.STRING, "setting",
                                        "Setting to toggle (proxy, indicator, casesensitive)", true)
                                .addOption(OptionType.BOOLEAN, "enabled", "Enable or disable", true),

                        new SubcommandData("switch", "Switch to a member (or switch out)")
                                .addOptions(new OptionData(OptionType.STRING, "member", "Name of the member to switch to", true)
                                        .setAutoComplete(true)));
    }

    @Override
    public String getName() {
        return "proxysettings";
    }

    @Override
    public String getDescription() {
        return "Manage proxy settings and switch members";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    @Override
    public boolean isGuildOnly() {
        return false; // Allow in DMs for global proxy management
    }
}
