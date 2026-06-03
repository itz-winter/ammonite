package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Appearance command for setting bot online status (online status ONLY)
 */
public class AppearanceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is bot owner
        if (!PermissionUtils.isBotOwner(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Bot Owner Only", "Only the bot owner can change the bot appearance.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String statusType = event.getOption("status").getAsString();

        OnlineStatus status;
        String statusText;

        switch (statusType.toLowerCase()) {
            case "online" -> {
                status = OnlineStatus.ONLINE;
                statusText = "Online " + CustomEmojis.ONLINE;
            }
            case "dnd", "do-not-disturb" -> {
                status = OnlineStatus.DO_NOT_DISTURB;
                statusText = "Do Not Disturb " + CustomEmojis.DND;
            }
            case "idle", "away" -> {
                status = OnlineStatus.IDLE;
                statusText = "Idle " + CustomEmojis.IDLE;
            }
            case "offline" -> {
                status = OnlineStatus.OFFLINE;
                statusText = "Offline " + CustomEmojis.OFFLINE;
            }
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Status", "Valid statuses are: online, dnd, idle, offline")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }
        }

        try {
            event.getJDA().getPresence().setStatus(status);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Appearance Updated", "Bot appearance has been set to: **" + statusText + "**")).setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Appearance Update Failed", "Failed to update bot appearance: " + e.getMessage()))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("appearance", "Set bot online appearance")
                .addOptions(
                        new OptionData(OptionType.STRING, "status", "The status to set", true)
                                .addChoice("Online", "online")
                                .addChoice("Do Not Disturb", "dnd")
                                .addChoice("Idle", "idle")
                                .addChoice("Offline", "offline"));
    }

    @Override
    public String getName() {
        return "appearance";
    }

    @Override
    public String getDescription() {
        return "Set bot online appearance";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public boolean isOwnerOnly() {
        return true;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }
}
