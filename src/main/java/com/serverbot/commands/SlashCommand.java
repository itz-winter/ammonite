package com.serverbot.commands;

import com.serverbot.utils.context.CommandContext;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Interface for all slash commands
 */
public interface SlashCommand {

    /**
     * Execute the slash command
     * 
     * @param event The slash command interaction event
     */
    void execute(SlashCommandInteractionEvent event);

    /**
     * Execute the command via the unified {@link CommandContext} abstraction.
     *
     * <p>Override this method (along with {@link #supportsCommandContext()}) to enable
     * the command to be invoked identically from both slash and prefix paths.</p>
     *
     * <p>Default implementation: throws {@link UnsupportedOperationException} — commands
     * that do not override this will fall back to the manual prefix handler.</p>
     *
     * @param ctx the command context (slash or prefix)
     */
    default void executeWithContext(CommandContext ctx) {
        throw new UnsupportedOperationException(
                getName() + " does not support CommandContext execution yet.");
    }

    /**
     * Whether this command has been migrated to the unified {@link CommandContext} pattern.
     *
     * <p>If {@code true}, the prefix router will call {@link #executeWithContext(CommandContext)}
     * instead of the manual handler in {@code PrefixCommandService}.</p>
     *
     * @return {@code true} if {@link #executeWithContext(CommandContext)} is implemented
     */
    default boolean supportsCommandContext() {
        return false;
    }

    /**
     * Get the command name
     * 
     * @return Command name
     */
    String getName();

    /**
     * Get the command description
     * 
     * @return Command description
     */
    String getDescription();

    /**
     * Get the command category
     * 
     * @return Command category
     */
    CommandCategory getCategory();

    /**
     * Check if the command requires special permissions
     * 
     * @return true if admin/moderator permissions are required
     */
    default boolean requiresPermissions() {
        return false;
    }

    /**
     * Check if the command can only be used in guilds
     * 
     * @return true if guild only
     */
    default boolean isGuildOnly() {
        return true;
    }

    /**
     * Check if the command is restricted to the bot owner only.
     * Owner-only commands are hidden from the help menu for non-owners.
     * 
     * @return true if only the bot owner can use this command
     */
    default boolean isOwnerOnly() {
        return false;
    }

    /**
     * Handle autocomplete interactions for this command.
     * Override in commands that have options marked with setAutoComplete(true).
     */
    default void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        event.replyChoices().queue();
    }
}

