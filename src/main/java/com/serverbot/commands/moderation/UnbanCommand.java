package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.SchedulerService;
import com.serverbot.utils.AutoLogUtils;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.HashMap;
import java.util.Map;

public class UnbanCommand implements SlashCommand {

    @Override
    public String getName() {
        return "unban";
    }

    @Override
    public String getDescription() {
        return "Unban a user from the server";
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
        if (!PermissionManager.hasPermission(event.getMember(), "mod.unban")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You don't have permission to unban members!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Guild guild = event.getGuild();
        if (guild == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error",
                    "This command can only be used in a server!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String userId = event.getOption("user_id").getAsString();
        String reason = event.getOption("reason") != null
                ? event.getOption("reason").getAsString()
                : "No reason provided";

        try {
            long userIdLong = Long.parseLong(userId);

            // Check if user is actually banned
            guild.retrieveBan(guild.getJDA().getUserById(userIdLong)).queue(
                    ban -> {
                        // User is banned, proceed with unban
                        guild.unban(User.fromId(userId)).reason(reason).queue(
                                success -> {
                                    User user = ban.getUser();

                                    // Cancel any scheduled unban tasks
                                    SchedulerService.getInstance().cancelUserTasks(
                                            guild.getId(), userId, SchedulerService.TaskType.UNBAN);

                                    // Log to database
                                    try {
                                        Map<String, Object> logEntry = new HashMap<>();
                                        logEntry.put("type", "UNBAN");
                                        logEntry.put("userId", user.getId());
                                        logEntry.put("moderatorId", event.getMember().getId());
                                        logEntry.put("reason", reason);
                                        logEntry.put("timestamp", System.currentTimeMillis());

                                        ServerBot.getStorageManager().addModerationLog(event.getGuild().getId(),
                                                logEntry);
                                    } catch (Exception e) {
                                        System.err.println("Failed to log unban: " + e.getMessage());
                                    }

                                    // Log to AutoLog channel
                                    AutoLogUtils.logUnban(event.getGuild(), user, event.getMember().getUser(), reason);

                                    event.replyEmbeds(EmbedUtils.createSuccessEmbed("User Unbanned",
                                            String.format("Successfully unbanned **%s** (%s)\n**Reason:** %s",
                                                    user.getName(), user.getId(), reason)))
                                            .queue();
                                },
                                error -> {
                                    event.replyEmbeds(EmbedUtils.createErrorEmbed("Unban Failed",
                                            "Failed to unban user. Please check permissions.")).setEphemeral(true)
                                            .queue();
                                });
                    },
                    error -> {
                        // User is not banned
                        event.replyEmbeds(EmbedUtils.createErrorEmbed("User Not Banned",
                                "This user is not currently banned from the server.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                    });

        } catch (NumberFormatException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid User ID",
                    "Please provide a valid user ID!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static net.dv8tion.jda.api.interactions.commands.build.CommandData getCommandData() {
        return net.dv8tion.jda.api.interactions.commands.build.Commands.slash("unban", "Unban a user from the server")
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "user_id",
                        "ID of the user to unban", true)
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "reason",
                        "Reason for unbanning", false);
    }
}
