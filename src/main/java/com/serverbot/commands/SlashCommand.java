package com.serverbot.commands;

import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

/**
 * Interface for all slash commands
 */
public interface SlashCommand {
    
    /**
     * Execute the slash command
     * @param event The slash command interaction event
     */
    void execute(SlashCommandInteractionEvent event);
    
    /**
     * Get the command name
     * @return Command name
     */
    String getName();
    
    /**
     * Get the command description
     * @return Command description
     */
    String getDescription();
    
    /**
     * Get the command category
     * @return Command category
     */
    CommandCategory getCategory();
    
    /**
     * Check if the command requires special permissions
     * @return true if admin/moderator permissions are required
     */
    default boolean requiresPermissions() {
        return false;
    }
    
    /**
     * Check if the command can only be used in guilds
     * @return true if guild only
     */
    default boolean isGuildOnly() {
        return true;
    }
    
    /**
     * Check if the command is restricted to the bot owner only.
     * Owner-only commands are hidden from the help menu for non-owners.
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
