package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Restart command for bot owner only
 */
public class RestartCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isBotOwner(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Access Denied",
                    "This command can only be used by the bot owner.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.createInfoEmbed(
                "🔄 Restarting Bot",
                "The bot is restarting... This may take a few moments.")).queue(message -> {
                    // Schedule the restart after a short delay to allow the message to be sent
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000); // Wait 2 seconds

                            System.out.println("Bot restart initiated by owner: " + event.getUser().getName());

                            // Save all data before shutdown
                            ServerBot.getStorageManager().saveAllData();

                            // Shutdown JDA
                            event.getJDA().shutdown();

                            // Exit the application - This would normally be handled by a process manager
                            // that would restart the bot automatically (like systemd, pm2, etc.)
                            System.exit(0);

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            System.err.println("Restart interrupted: " + e.getMessage());
                        }
                    }).start();
                });
    }

    public static CommandData getCommandData() {
        return Commands.slash("restart", "Restart the bot (Owner only)");
    }

    @Override
    public String getName() {
        return "restart";
    }

    @Override
    public String getDescription() {
        return "Restart the bot (Owner only)";
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
}
