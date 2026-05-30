package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.music.MusicManager;
import com.serverbot.music.MusicUtils;
import com.serverbot.utils.EmbedUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

/**
 * Queue command - shows the current music queue and now-playing track.
 */
public class QueueCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Playing", "The bot is not currently playing music.")).setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        AudioTrack currentTrack = gmm.getScheduler().getCurrentTrack();
        List<AudioTrack> queue = gmm.getScheduler().getQueue();

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🎶 Music Queue");

        if (currentTrack != null) {
            String progress = MusicUtils.createProgressBar(currentTrack.getPosition(), currentTrack.getDuration());
            embed.addField("Now Playing",
                    MusicUtils.formatTrack(currentTrack) + "\n" +
                            progress + " `" + MusicUtils.formatDuration(currentTrack.getPosition()) +
                            "/" + MusicUtils.formatDuration(currentTrack.getDuration()) + "`",
                    false);
        } else {
            embed.addField("Now Playing", "Nothing is currently playing.", false);
        }

        if (queue.isEmpty()) {
            embed.addField("Up Next", "Queue is empty. Use `/play` to add tracks!", false);
        } else {
            StringBuilder queueStr = new StringBuilder();
            int displayCount = Math.min(queue.size(), 10);
            for (int i = 0; i < displayCount; i++) {
                queueStr.append(MusicUtils.formatTrackShort(queue.get(i), i + 1)).append("\n");
            }
            if (queue.size() > 10) {
                queueStr.append("*... and ").append(queue.size() - 10).append(" more*");
            }

            // Calculate total queue duration
            long totalDuration = queue.stream().mapToLong(t -> t.getDuration()).sum();
            if (currentTrack != null) {
                totalDuration += currentTrack.getDuration() - currentTrack.getPosition();
            }

            embed.addField("Up Next (" + queue.size() + " tracks)", queueStr.toString(), false);
            embed.setFooter("Total queue time: " + MusicUtils.formatDuration(totalDuration));
        }

        // Show repeat/shuffle status
        String statusLine = "";
        if (gmm.getScheduler().isRepeating())
            statusLine += "🔁 Repeat ON  ";
        if (gmm.getScheduler().isShuffling())
            statusLine += "🔀 Shuffle ON";
        if (!statusLine.isEmpty()) {
            embed.addField("Mode", statusLine.trim(), false);
        }

        event.replyEmbeds(embed.build()).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("queue", "Show what's currently playing.");
    }

    @Override
    public String getName() {
        return "queue";
    }

    @Override
    public String getDescription() {
        return "Show what's currently playing.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MUSIC;
    }
}
