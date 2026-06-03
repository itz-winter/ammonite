package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Softban command - bans and immediately unbans to delete messages
 */
public class SoftbanCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.softban")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `mod.softban` permission to use this command."))
                    .setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        if (targetUser.equals(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Target", "You cannot softban yourself.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (targetUser.equals(event.getJDA().getSelfUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Target", "I cannot softban myself.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember != null && !event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Softban", "I cannot softban this user due to role hierarchy.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Perform softban (ban then unban to delete messages)
        event.getGuild().ban(targetUser, 7, TimeUnit.DAYS)
                .reason(reason + " (Softban by " + moderator.getEffectiveName() + ")")
                .flatMap(v -> event.getGuild().unban(targetUser))
                .queue(
                        success -> {
                            // Log the softban
                            try {
                                Map<String, Object> logEntry = new HashMap<>();
                                logEntry.put("type", "SOFTBAN");
                                logEntry.put("userId", targetUser.getId());
                                logEntry.put("moderatorId", moderator.getId());
                                logEntry.put("reason", reason);
                                logEntry.put("timestamp", System.currentTimeMillis());

                                ServerBot.getStorageManager().addModerationLog(event.getGuild().getId(), logEntry);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                    "User Softbanned",
                                    "Successfully softbanned " + targetUser.getAsMention() +
                                            "\n**Reason:** " + reason +
                                            "\n**Moderator:** " + moderator.getAsMention()))
                                    .queue();
                        },
                        error -> {
                            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                    "Softban Failed",
                                    "Failed to softban user: " + error.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                        });
    }

    public static CommandData getCommandData() {
        return Commands.slash("softban", "Softban a user (ban and immediately unban to delete messages)")
                .addOption(OptionType.USER, "user", "The user to softban", true)
                .addOption(OptionType.STRING, "reason", "Reason for the softban", false);
    }

    @Override
    public String getName() {
        return "softban";
    }

    @Override
    public String getDescription() {
        return "Softban a user (ban and immediately unban to delete messages)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
