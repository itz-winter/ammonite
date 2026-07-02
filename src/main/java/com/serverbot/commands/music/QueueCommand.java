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
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

/**
 * Queue command - shows the current music queue and now-playing track.
 */
public class QueueCommand implements SlashCommand {

    private static final int PAGE_SIZE = 10;

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Playing", "The bot is not currently playing music.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        int page = event.getOption("page") != null ? (int) event.getOption("page").getAsLong() : 1;
        event.replyEmbeds(buildQueueEmbed(musicManager, event.getGuild(), page)).queue();
    }

    /**
     * Build the paginated queue embed. Used by both the slash command and prefix handler.
     *
     * @param musicManager the active music manager
     * @param guild        the guild being queried
     * @param page         1-based page number
     * @return the embed to display
     */
    public static net.dv8tion.jda.api.entities.MessageEmbed buildQueueEmbed(
            MusicManager musicManager, net.dv8tion.jda.api.entities.Guild guild, int page) {

        GuildMusicManager gmm = musicManager.getGuildMusicManager(guild);
        AudioTrack currentTrack = gmm.getScheduler().getCurrentTrack();
        List<AudioTrack> queue = gmm.getScheduler().getQueue();

        int totalPages = queue.isEmpty() ? 1 : (int) Math.ceil((double) queue.size() / PAGE_SIZE);
        int clampedPage = Math.max(1, Math.min(page, totalPages));
        int startIndex = (clampedPage - 1) * PAGE_SIZE; // inclusive
        int endIndex = Math.min(startIndex + PAGE_SIZE, queue.size()); // exclusive

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🎶 Music Queue");

        // Now Playing field
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

        // Queue field (paginated)
        if (queue.isEmpty()) {
            embed.addField("Up Next", "Queue is empty. Use `/play` to add tracks!", false);
        } else {
            StringBuilder queueStr = new StringBuilder();
            for (int i = startIndex; i < endIndex; i++) {
                queueStr.append(MusicUtils.formatTrackShort(queue.get(i), i + 1)).append("\n");
            }

            long totalDuration = queue.stream().mapToLong(AudioTrack::getDuration).sum();
            if (currentTrack != null) {
                totalDuration += currentTrack.getDuration() - currentTrack.getPosition();
            }

            embed.addField("Up Next (" + queue.size() + " tracks)", queueStr.toString(), false);

            String footerText = "Page " + clampedPage + "/" + totalPages
                    + " • Total queue time: " + MusicUtils.formatDuration(totalDuration);
            if (totalPages > 1) {
                footerText += " • Use /queue page:<n> to navigate";
            }
            embed.setFooter(footerText);
        }

        // Repeat / shuffle status
        String statusLine = "";
        if (gmm.getScheduler().isRepeating())  statusLine += "🔁 Repeat ON  ";
        if (gmm.getScheduler().isShuffling())  statusLine += "🔀 Shuffle ON";
        if (!statusLine.isEmpty()) {
            embed.addField("Mode", statusLine.trim(), false);
        }

        return embed.build();
    }

    public static CommandData getCommandData() {
        return Commands.slash("queue", "Show what's currently playing.")
                .addOptions(new OptionData(OptionType.INTEGER, "page", "Page number (default: 1)", false)
                        .setMinValue(1));
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
