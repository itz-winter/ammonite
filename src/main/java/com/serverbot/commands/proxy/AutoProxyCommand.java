package com.serverbot.commands.proxy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.models.ProxySettings;
import com.serverbot.services.ProxyService;
import com.serverbot.utils.EmbedUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Quick toggle command for autoproxy.
 * Separate from /proxysettings autoproxy for convenience.
 * Accepts an optional boolean; no input = toggle.
 */
public class AutoProxyCommand implements SlashCommand {

    private final ProxyService proxyService;

    public AutoProxyCommand() {
        this.proxyService = ServerBot.getProxyService();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Boolean enabled = event.getOption("enabled") != null ? event.getOption("enabled").getAsBoolean() : null;

        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        ProxySettings settings = proxyService.getSettings(event.getUser().getId(), guildId);

        boolean newState;
        if (enabled == null) {
            // Toggle: flip the current state
            newState = settings.getAutoproxyMode() == ProxySettings.AutoproxyMode.OFF;
        } else {
            newState = enabled;
        }

        event.deferReply().queue();

        if (newState) {
            settings.setAutoproxyMode(ProxySettings.AutoproxyMode.FRONT);
        } else {
            settings.setAutoproxyMode(ProxySettings.AutoproxyMode.OFF);
        }

        proxyService.updateSettings(event.getUser().getId(), guildId, settings).thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Autoproxy " + (newState ? "Enabled" : "Disabled"),
                    "Autoproxy has been **" + (newState ? "enabled" : "disabled") + "**.\n" +
                    "Use `/proxysettings autoproxy` for advanced autoproxy configuration."
                )).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to update autoproxy settings. Use `/error category:7` for full proxy system documentation."
                )).queue();
            }
        });
    }

    public static CommandData getCommandData() {
        return Commands.slash("autoproxy", "Toggle autoproxy on/off")
                .addOption(OptionType.BOOLEAN, "enabled", "Enable or disable autoproxy (leave empty to toggle)", false);
    }

    @Override
    public String getName() {
        return "autoproxy";
    }

    @Override
    public String getDescription() {
        return "Toggle autoproxy on/off";
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
        return false;
    }
}