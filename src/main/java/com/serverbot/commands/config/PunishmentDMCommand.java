package com.serverbot.commands.config;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.PunishmentNotificationService;
import com.serverbot.services.PunishmentNotificationService.PunishmentDMSettings;
// import com.serverbot.services.PunishmentNotificationService.PunishmentType; // unused-removed so then the compiler will shut the fuck up
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
// import net.dv8tion.jda.api.interactions.commands.OptionMapping; // unused
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.*;

/**
 * Command for managing punishment DM notification settings
 */
public class PunishmentDMCommand implements SlashCommand {

    private final PunishmentNotificationService notificationService;

    public PunishmentDMCommand() {
        this.notificationService = PunishmentNotificationService.getInstance();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.punishment_dm")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need admin permissions to manage punishment DM settings."))
                    .setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            showSettings(event);
            return;
        }

        switch (subcommand) {
            case "enable" -> handleEnable(event);
            case "disable" -> handleDisable(event);
            case "set-appeal-channel" -> handleSetAppealChannel(event);
            case "settings" -> showSettings(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Subcommand", "Please use a valid subcommand.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleEnable(SlashCommandInteractionEvent event) {
        PunishmentDMSettings settings = notificationService.getDMSettings(event.getGuild().getId());
        settings.setEnabled(true);
        notificationService.saveDMSettings(event.getGuild().getId(), settings);

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Punishment DMs Enabled",
                "Punishment DM notifications have been enabled for this server.\n" +
                        "Users will now receive DMs for all punishment actions (warn, kick, ban, mute, timeout, automod)."))
                .queue();
    }

    private void handleDisable(SlashCommandInteractionEvent event) {
        PunishmentDMSettings settings = notificationService.getDMSettings(event.getGuild().getId());
        settings.setEnabled(false);
        notificationService.saveDMSettings(event.getGuild().getId(), settings);

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Punishment DMs Disabled",
                "Punishment DM notifications have been disabled for this server.")).queue();
    }

    private void handleSetAppealChannel(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getOption("channel").getAsChannel().asTextChannel();

        PunishmentDMSettings settings = notificationService.getDMSettings(event.getGuild().getId());
        settings.setAppealChannelId(channel.getId());
        notificationService.saveDMSettings(event.getGuild().getId(), settings);

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Appeal Channel Set",
                "Appeal channel has been set to " + channel.getAsMention() +
                        ". Users will now see an appeal button in their punishment DMs that opens a form."))
                .queue();
    }

    private void showSettings(SlashCommandInteractionEvent event) {
        PunishmentDMSettings settings = notificationService.getDMSettings(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚙️ Punishment DM Settings")
                .setColor(settings.isEnabled() ? Color.GREEN : Color.RED)
                .addField("Status",
                        settings.isEnabled() ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled", true);

        if (settings.getAppealChannelId() != null && !settings.getAppealChannelId().isEmpty()) {
            embed.addField("Appeal Channel", "<#" + settings.getAppealChannelId() + ">", true);
        } else {
            embed.addField("Appeal Channel", "Not set (appeals disabled)", true);
        }

        embed.setDescription("When enabled, users will receive DMs for:\n" +
                "• Warnings\n" +
                "• Kicks\n" +
                "• Bans\n" +
                "• Mutes/Timeouts\n" +
                "• Automod actions\n\n" +
                "If an appeal channel is set, punishment DMs will include a button to submit an appeal via a form.");

        event.replyEmbeds(embed.build()).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("punishment-dm", "Manage punishment DM notification settings")
                .addSubcommands(
                        new SubcommandData("enable", "Enable punishment DM notifications for all punishment types"),
                        new SubcommandData("disable", "Disable punishment DM notifications"),
                        new SubcommandData("set-appeal-channel",
                                "Set the appeal channel (enables appeal button in DMs)")
                                .addOption(OptionType.CHANNEL, "channel", "Channel where appeals will be sent", true),
                        new SubcommandData("settings", "View current punishment DM settings"));
    }

    @Override
    public String getName() {
        return "punishment-dm";
    }

    @Override
    public String getDescription() {
        return "Manage punishment DM notification settings";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}