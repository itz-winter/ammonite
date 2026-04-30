package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Presence command to set bot's rich presence (activity, not user status)
 */
public class PresenceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is bot owner
        if (!PermissionUtils.isBotOwner(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Bot Owner Only", "Only the bot owner can change the bot presence."
            )).setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString().toLowerCase();
        
        switch (action) {
            case "set" -> setPresence(event);
            case "remove", "clear" -> removePresence(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Action", "Available actions: `set`, `remove`"
            )).setEphemeral(true).queue();
        }
    }

    private void setPresence(SlashCommandInteractionEvent event) {
        String presenceText = event.getOption("text") != null ? 
                event.getOption("text").getAsString() : null;
        String activityType = event.getOption("type") != null ? 
                event.getOption("type").getAsString() : "playing";
        
        if (presenceText == null || presenceText.trim().isEmpty()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Text", "Please provide presence text when using `set`."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            Activity activity = switch (activityType.toLowerCase()) {
                case "playing" -> Activity.playing(presenceText);
                case "watching" -> Activity.watching(presenceText);
                case "listening" -> Activity.listening(presenceText);
                case "streaming" -> Activity.streaming(presenceText, "https://twitch.tv/placeholder"); // for some fuckass reason the url is required?? DONT TOUCH IT
                case "competing" -> Activity.competing(presenceText);
                default -> Activity.playing(presenceText);
            };
            
            event.getJDA().getPresence().setActivity(activity);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Presence Updated", 
                "Bot presence has been set to: **" + activityType.substring(0, 1).toUpperCase() + activityType.substring(1) + " " + presenceText + "**"
            )).setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Presence Update Failed", "Failed to update bot presence: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void removePresence(SlashCommandInteractionEvent event) {
        try {
            event.getJDA().getPresence().setActivity(null);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Presence Cleared", "Bot presence has been cleared."
            )).setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Presence Clear Failed", "Failed to clear bot presence: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("rpc", "Set the bot's rich presence (activity)")
                .addOptions(
                    new OptionData(OptionType.STRING, "action", "Action to perform", true)
                        .addChoice("Set Presence", "set")
                        .addChoice("Remove Presence", "remove"),
                    new OptionData(OptionType.STRING, "type", "Activity type", false)
                        .addChoice("Playing", "playing")
                        .addChoice("Watching", "watching")
                        .addChoice("Listening", "listening")
                        .addChoice("Streaming", "streaming")
                        .addChoice("Competing", "competing"),
                    new OptionData(OptionType.STRING, "text", "Activity text", false)
                );
    }

    @Override
    public String getName() {
        return "rpc";
    }

    @Override
    public String getDescription() {
        return "Set the bot's rich presence (activity)";
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
