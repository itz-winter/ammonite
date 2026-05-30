package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.MusicManager;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Skip command - skips the current track or a number of tracks in the queue.
 */
public class SkipCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Playing", "The bot is not currently playing music.")).setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        if (gmm.getScheduler().getCurrentTrack() == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Nothing Playing", "There is no track currently playing.")).setEphemeral(true).queue();
            return;
        }

        int count = event.getOption("count", 1, OptionMapping::getAsInt);
        count = Math.max(1, count);

        int skipped = gmm.getScheduler().skip(count);

        var currentTrack = gmm.getScheduler().getCurrentTrack();
        String nowPlaying = currentTrack != null
                ? "Now playing: **" + currentTrack.getInfo().title + "**"
                : "Queue is now empty.";

        event.replyEmbeds(EmbedUtils.createMusicEmbed(
                "⏭️ Skipped",
                "Skipped **" + skipped + "** track" + (skipped != 1 ? "s" : "") + ".\n" + nowPlaying)).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("skip", "Skips a number of tracks in the music queue.")
                .addOption(OptionType.INTEGER, "count", "Number of tracks to skip (default: 1)", false);
    }

    @Override
    public String getName() {
        return "skip";
    }

    @Override
    public String getDescription() {
        return "Skips a number of tracks in the music queue.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MUSIC;
    }
}
