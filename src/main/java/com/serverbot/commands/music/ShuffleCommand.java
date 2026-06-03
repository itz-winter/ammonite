package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.music.MusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Shuffle command - toggles shuffle mode for the queue.
 */
public class ShuffleCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Playing", "The bot is not currently playing music.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean newState = !gmm.getScheduler().isShuffling();
        gmm.getScheduler().setShuffling(newState);

        if (newState) {
            event.replyEmbeds(EmbedUtils.createMusicEmbed(
                    "🔀 Shuffle Enabled",
                    "The queue will now be shuffled.")).queue();
        } else {
            event.replyEmbeds(EmbedUtils.createMusicEmbed(
                    "🔀 Shuffle Disabled",
                    "Shuffle mode has been turned off.")).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("shuffle", "Sets whether the playlist should be shuffled.");
    }

    @Override
    public String getName() {
        return "shuffle";
    }

    @Override
    public String getDescription() {
        return "Sets whether the playlist should be shuffled.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MUSIC;
    }
}
