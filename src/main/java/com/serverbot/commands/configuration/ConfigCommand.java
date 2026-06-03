package com.serverbot.commands.configuration;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Map;

/**
 * Configuration command for managing bot settings
 */
public class ConfigCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionUtils.hasManageServerPermissions(member)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need Manage Server permissions to use this command."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String action = event.getOption("action") != null ? event.getOption("action").getAsString() : "show";

        switch (action.toLowerCase()) {
            case "show" -> showConfig(event);
            case "reload" -> reloadConfig(event);
            default -> showConfig(event);
        }
    }

    private void showConfig(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();

        try {
            Map<String, Object> config = ServerBot.getStorageManager().getGuildSettings(guildId);

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("Server Config")
                    .setDescription("Current bot settings for this server");

            // Get values with defaults
            String prefix = (String) config.getOrDefault("prefix", "/");
            boolean levelsEnabled = (Boolean) config.getOrDefault("enableLeveling", false);
            boolean pointsEnabled = (Boolean) config.getOrDefault("enableEconomy", false);
            boolean automodEnabled = (Boolean) config.getOrDefault("enableAutoMod", false);
            boolean autoroleEnabled = (Boolean) config.getOrDefault("enableAutoRole", false);

            // Basic Settings
            embed.addField("Basic",
                    "**Prefix:** " + prefix + "\n" +
                            "**Levels:** "
                            + (levelsEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                            "**Economy:** "
                            + (pointsEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                            "**AutoMod:** "
                            + (automodEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                            "**AutoRole:** "
                            + (autoroleEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled"),
                    false);

            // Logging Channels
            String modLogChannel = (String) config.get("modLogChannel");
            String punishmentLogChannel = (String) config.get("punishmentLogChannel");
            String allLogChannel = (String) config.get("allLogChannel");

            String logChannels = "";
            if (modLogChannel != null) {
                logChannels += "**Mod Log:** <#" + modLogChannel + ">\n";
            }
            if (punishmentLogChannel != null) {
                logChannels += "**Punishment Log:** <#" + punishmentLogChannel + ">\n";
            }
            if (allLogChannel != null) {
                logChannels += "**All Events:** <#" + allLogChannel + ">\n";
            }
            if (logChannels.isEmpty()) {
                logChannels = "No logging channels configured";
            }

            embed.addField("Logchannels", logChannels, false);

            embed.addField("Config Commands",
                    "`/levels enable|disable` - Toggle leveling system\n" +
                            "`/points enable|disable` - Toggle economy system\n" +
                            "`/config reload` - Reload configuration",
                    false);

            event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Error", "Failed to load server configuration: " + e.getMessage())).setEphemeral(true)
                    .queue();
        }
    }

    private void reloadConfig(SlashCommandInteractionEvent event) {
        try {
            // Reload file storage data (reload from files)
            ServerBot.getStorageManager().saveAllData();

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Configuration Reloaded", "Bot configuration has been reloaded successfully.")).setEphemeral(true)
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Reload Failed", "Failed to reload configuration: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("config", "Manage bot configuration")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Action to perform", false)
                                .addChoice("Show Configuration", "show")
                                .addChoice("Reload Configuration", "reload"));
    }

    @Override
    public String getName() {
        return "config";
    }

    @Override
    public String getDescription() {
        return "Manage bot configuration";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public boolean isOwnerOnly() {
        return true;
    }
}
