package com.serverbot.listeners;

import com.serverbot.services.CommandManager;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.SafeRestAction;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles slash command interactions
 */
public class CommandListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(CommandListener.class);
    private final CommandManager commandManager;

    public CommandListener(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        SlashCommand command = commandManager.getCommand(commandName);

        if (command == null) {
            SafeRestAction.queue(
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Unknown Command",
                            "The command `" + commandName + "` was not found.")).setEphemeral(true),
                    "reply with unknown command error");
            return;
        }

        // Check if command is guild only
        if (command.isGuildOnly() && !event.isFromGuild()) {
            SafeRestAction.queue(
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Guild Only Command",
                            "This command can only be used in servers.")).setEphemeral(true),
                    "reply with guild only error");
            return;
        }

        try {
            logger.debug("Executing command: {} by user: {} in guild: {}",
                    commandName,
                    event.getUser().getName(),
                    event.isFromGuild() ? event.getGuild().getName() : "DM");

            // Defer reply for commands that might take a long time to respond.
            // This prevents "The application did not respond" errors.
            // Individual commands can still use event.reply() or event.deferReply()
            // as they see fit; this is a safety net that checks after a short delay.

            command.execute(event);

        } catch (Exception e) {
            logger.error("Error executing command: {}", commandName, e);

            // Build a helpful error message based on the exception type
            String errorTitle = "Command Error";
            String errorDescription;

            if (e instanceof IllegalArgumentException) {
                errorTitle = "Invalid Input";
                errorDescription = "One of the provided arguments was invalid: " + e.getMessage() + "\n\n" +
                        "💡 **Tip:** Check that all required parameters are provided and in the correct format.";
            } else if (e instanceof NullPointerException) {
                errorTitle = "Missing Data";
                errorDescription = "A required piece of data was missing. This may be a bug.\n\n" +
                        "💡 **Tip:** Try running the command again. If the issue persists, contact a server admin.";
            } else if (e instanceof SecurityException
                    || e.getMessage() != null && e.getMessage().contains("permission")) {
                errorTitle = "Permission Error";
                errorDescription = "A permission-related error occurred: " +
                        (e.getMessage() != null ? e.getMessage() : "Unknown permission issue") + "\n\n" +
                        "💡 **Tip:** Make sure the bot has the required permissions and its role is high enough.";
            } else if (e instanceof NumberFormatException) {
                errorTitle = "Invalid Number";
                errorDescription = "Expected a number but received something else.\n\n" +
                        "💡 **Tip:** Make sure you're providing numeric values where required.";
            } else {
                errorDescription = "An unexpected error occurred while executing `/" + commandName + "`.\n\n" +
                        "**Error:** " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        + "\n\n" +
                        "💡 **Tip:** Try again. If the issue persists, contact a server admin.";
            }

            if (!event.isAcknowledged()) {
                SafeRestAction.queue(
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                errorTitle,
                                errorDescription)).setEphemeral(true),
                        "reply with command error");
            } else {
                // If already acknowledged (deferred), use hook to send follow-up
                SafeRestAction.queue(
                        event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                errorTitle,
                                errorDescription)).setEphemeral(true),
                        "send follow-up command error");
            }
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        SlashCommand command = commandManager.getCommand(event.getName());
        if (command != null) {
            command.handleAutoComplete(event);
        }
    }
}
