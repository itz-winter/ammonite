package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.music.MusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Volume command - sets the music playback volume (0-150).
 */
public class VolumeCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Playing", "The bot is not currently playing music.")).setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        int currentVolume = gmm.getPlayer().getVolume();

        Integer newVolume = event.getOption("level", OptionMapping::getAsInt);
        if (newVolume == null) {
            // Just show current volume
            String volumeBar = createVolumeBar(currentVolume);
            event.replyEmbeds(EmbedUtils.createMusicEmbed(
                    "🔊 Volume",
                    "Current volume: **" + currentVolume + "%**\n" + volumeBar)).queue();
            return;
        }

        newVolume = Math.max(0, Math.min(150, newVolume));
        gmm.getPlayer().setVolume(newVolume);

        String emoji = newVolume == 0 ? "🔇" : newVolume <= 50 ? "🔉" : "🔊";
        String volumeBar = createVolumeBar(newVolume);
        event.replyEmbeds(EmbedUtils.createMusicEmbed(
                emoji + " Volume Set",
                "Volume set to **" + newVolume + "%**\n" + volumeBar)).queue();
    }

    private String createVolumeBar(int volume) {
        int barLength = 10;
        int filled = (int) Math.round((volume / 100.0) * barLength);
        filled = Math.max(0, Math.min(barLength, filled));
        return "▓".repeat(filled) + "░".repeat(barLength - filled);
    }

    public static CommandData getCommandData() {
        return Commands.slash("volume", "Sets the music volume in voicechat.")
                .addOption(OptionType.INTEGER, "level", "Volume level (0-150)", false);
    }

    @Override
    public String getName() {
        return "volume";
    }

    @Override
    public String getDescription() {
        return "Sets the music volume in voicechat.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MUSIC;
    }
}
