package com.serverbot.commands;

import com.serverbot.utils.context.CommandContext;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Interface for all slash commands.
 */
public interface SlashCommand {

    void execute(SlashCommandInteractionEvent event);

    /**
     * Execute via the unified {@link CommandContext} abstraction (slash or prefix).
     * Override alongside {@link #supportsCommandContext()} to enable prefix routing.
     * Default throws {@link UnsupportedOperationException}.
     */
    default void executeWithContext(CommandContext ctx) {
        throw new UnsupportedOperationException(
                getName() + " does not support CommandContext execution yet.");
    }

    /**
     * Return {@code true} if {@link #executeWithContext} is implemented.
     * When true the prefix router calls executeWithContext instead of the manual handler.
     */
    default boolean supportsCommandContext() {
        return false;
    }

    String getName();

    String getDescription();

    CommandCategory getCategory();

    /**
     * Return {@code true} if this command requires admin/moderator permissions.
     */
    default boolean requiresPermissions() {
        return false;
    }

    default boolean isGuildOnly() {
        return true;
    }

    /**
     * Return {@code true} if only the bot owner can use this command.
     * Owner-only commands are hidden from the help menu for non-owners.
     */
    default boolean isOwnerOnly() {
        return false;
    }

    /**
     * Handle autocomplete interactions. Override for options with setAutoComplete(true).
     */
    default void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        event.replyChoices().queue();
    }
}

