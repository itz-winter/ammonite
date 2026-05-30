package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.AutoLogUtils;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.HashMap;
import java.util.Map;

public class UnmuteCommand implements SlashCommand {

    @Override
    public String getName() {
        return "unmute";
    }

    @Override
    public String getDescription() {
        return "Unmute a user in the server";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check permissions
        if (!PermissionManager.hasPermission(event.getMember(), "mod.unmute")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You don't have permission to unmute members!")).setEphemeral(true).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error",
                    "This command can only be used in a server!")).setEphemeral(true).queue();
            return;
        }

        User user = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        Member targetMember = guild.getMember(user);
        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("User Not Found",
                    "This user is not in the server!")).setEphemeral(true).queue();
            return;
        }

        // Check if the moderator can interact with the target
        if (!PermissionUtils.canInteractWith(event.getMember(), targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Cannot Unmute",
                    "You cannot unmute this user due to role hierarchy!")).setEphemeral(true).queue();
            return;
        }

        // Check if bot can interact with the target
        if (!PermissionUtils.botCanInteractWith(guild, targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Cannot Unmute",
                    "I cannot unmute this user due to role hierarchy!")).setEphemeral(true).queue();
            return;
        }

        // Find mute role
        Role muteRole = guild.getRolesByName("Muted", true).stream().findFirst().orElse(null);
        if (muteRole == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Mute Role Not Found",
                    "No 'Muted' role found in this server! Please create one first.")).setEphemeral(true).queue();
            return;
        }

        // Check if user is actually muted
        if (!targetMember.getRoles().contains(muteRole)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("User Not Muted",
                    "This user is not currently muted!")).setEphemeral(true).queue();
            return;
        }

        // Remove mute role
        guild.removeRoleFromMember(targetMember, muteRole).reason(reason).queue(
                success -> {
                    // Log to database
                    try {
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("type", "UNMUTE");
                        logEntry.put("userId", user.getId());
                        logEntry.put("moderatorId", event.getMember().getId());
                        logEntry.put("reason", reason);
                        logEntry.put("timestamp", System.currentTimeMillis());

                        ServerBot.getStorageManager().addModerationLog(event.getGuild().getId(), logEntry);
                    } catch (Exception e) {
                        System.err.println("Failed to log unmute: " + e.getMessage());
                    }

                    // Log to AutoLog channel
                    AutoLogUtils.logUnmute(event.getGuild(), user, event.getMember().getUser(), reason);

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed("User Unmuted",
                            String.format("Successfully unmuted **%s**\n**Reason:** %s",
                                    targetMember.getEffectiveName(), reason)))
                            .queue();
                },
                error -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed("Unmute Failed",
                            "Failed to unmute user. Please check permissions.")).setEphemeral(true).queue();
                });
    }

    public static net.dv8tion.jda.api.interactions.commands.build.CommandData getCommandData() {
        return net.dv8tion.jda.api.interactions.commands.build.Commands.slash("unmute", "Unmute a user in the server")
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.USER, "user", "User to unmute", true)
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "reason", "Reason for unmuting",
                        false);
    }
}
