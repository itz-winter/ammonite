package com.serverbot.commands.music;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.music.MusicManager;
import com.serverbot.music.MusicUtils;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.EmbedUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * /playlist command — manage and play user-created custom playlists stored in the bot.
 *
 * Subcommands:
 *   create <name> [description]  — create a new playlist
 *   delete <name>                — delete a playlist
 *   add    <name> <url>          — add a YouTube/direct URL to a playlist
 *   remove <name> <position>     — remove track at 1-based position
 *   list                         — list all your playlists
 *   show   <name>                — show tracks in a playlist
 *   play   <name> [position]     — queue a playlist (optionally start from position)
 */
public class PlaylistCommand implements SlashCommand {

    @Override
    public String getName() { return "playlist"; }

    @Override
    public String getDescription() { return "Create, manage, and play your custom playlists."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MUSIC; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) { event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "No subcommand specified.")).setEphemeral(true).queue(); return; }
        switch (sub) {
            case "create" -> handleCreate(event);
            case "delete" -> handleDelete(event);
            case "add"    -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list"   -> handleList(event);
            case "show"   -> handleShow(event);
            case "play"   -> handlePlay(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed("Unknown Subcommand", "Unknown subcommand: " + sub)).setEphemeral(true).queue();
        }
    }

    // ─── create ──────────────────────────────────────────────────────────────

    private void handleCreate(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = event.getOption("name", OptionMapping::getAsString);
        String desc = event.getOption("description", "", OptionMapping::getAsString);
        if (name == null || name.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Name", "Please provide a playlist name.")).setEphemeral(true).queue();
            return;
        }
        if (name.length() > 50) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Name Too Long", "Playlist name must be 50 characters or less.")).setEphemeral(true).queue();
            return;
        }
        boolean created = ServerBot.getStorageManager().createUserPlaylist(userId, name, desc);
        if (!created) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Already Exists", "You already have a playlist named **" + name + "**.")).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle("✅ Playlist Created")
            .setDescription("**" + name + "** has been created.\nUse `/playlist add name:" + name + " url:<url>` to add tracks.")
            .build()).setEphemeral(true).queue();
    }

    // ─── delete ──────────────────────────────────────────────────────────────

    private void handleDelete(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = event.getOption("name", OptionMapping::getAsString);
        if (name == null || name.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Name", "Please provide a playlist name.")).setEphemeral(true).queue();
            return;
        }
        boolean deleted = ServerBot.getStorageManager().deleteUserPlaylist(userId, name);
        if (!deleted) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found", "You don't have a playlist named **" + name + "**.")).setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle("✅ Playlist Deleted")
            .setDescription("**" + name + "** has been deleted.")
            .build()).setEphemeral(true).queue();
    }

    // ─── add ─────────────────────────────────────────────────────────────────

    private void handleAdd(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = event.getOption("name", OptionMapping::getAsString);
        String url  = event.getOption("url",  OptionMapping::getAsString);
        if (name == null || url == null || name.isBlank() || url.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Options", "Please provide both a playlist name and URL.")).setEphemeral(true).queue();
            return;
        }
        FileStorageManager.UserPlaylist pl = ServerBot.getStorageManager().getUserPlaylist(userId, name);
        if (pl == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found", "You don't have a playlist named **" + name + "**.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        MusicManager.getInstance().loadAndPlay(url, event.getGuild(),
            new MusicManager.MusicLoadCallback() {
                @Override
                public void onTrackLoaded(AudioTrack track) {
                    FileStorageManager.PlaylistEntry entry = new FileStorageManager.PlaylistEntry(
                        url, track.getInfo().title, track.getInfo().author, track.getDuration());
                    ServerBot.getStorageManager().addTrackToPlaylist(userId, name, entry);
                    event.getHook().editOriginalEmbeds(new EmbedBuilder()
                        .setColor(EmbedUtils.SUCCESS_COLOR)
                        .setTitle("✅ Track Added")
                        .setDescription("**" + track.getInfo().title + "** added to **" + name + "**.\n" +
                            "Position: **" + pl.entries.size() + "**")
                        .build()).queue();
                }
                @Override
                public void onPlaylistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                    // Add first track from playlist resolve
                    AudioTrack track = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                    if (track != null) {
                        FileStorageManager.PlaylistEntry entry = new FileStorageManager.PlaylistEntry(
                            url, track.getInfo().title, track.getInfo().author, track.getDuration());
                        ServerBot.getStorageManager().addTrackToPlaylist(userId, name, entry);
                        event.getHook().editOriginalEmbeds(new EmbedBuilder()
                            .setColor(EmbedUtils.SUCCESS_COLOR)
                            .setTitle("✅ Track Added")
                            .setDescription("**" + track.getInfo().title + "** added to **" + name + "**.")
                            .build()).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("No Track", "Couldn't resolve a track from that URL.")).queue();
                    }
                }
                @Override public void onNoMatches() { }
                @Override public void onLoadFailed(String message) { event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("Load Failed", message)).queue(); }
            });
    }

    // ─── remove ──────────────────────────────────────────────────────────────

    private void handleRemove(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = event.getOption("name", OptionMapping::getAsString);
        Integer pos  = event.getOption("position", OptionMapping::getAsInt);
        if (name == null || pos == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Options", "Please provide the playlist name and track position.")).setEphemeral(true).queue();
            return;
        }
        int idx = pos - 1; // convert to 0-based
        FileStorageManager.UserPlaylist pl = ServerBot.getStorageManager().getUserPlaylist(userId, name);
        if (pl == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found", "You don't have a playlist named **" + name + "**.")).setEphemeral(true).queue();
            return;
        }
        if (idx < 0 || idx >= pl.entries.size()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Position", "Position must be between 1 and " + pl.entries.size() + ".")).setEphemeral(true).queue();
            return;
        }
        String removed = pl.entries.get(idx).title;
        ServerBot.getStorageManager().removeTrackFromPlaylist(userId, name, idx);
        event.replyEmbeds(new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle("✅ Track Removed")
            .setDescription("Removed **" + removed + "** from **" + name + "**.")
            .build()).setEphemeral(true).queue();
    }

    // ─── list ────────────────────────────────────────────────────────────────

    private void handleList(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        List<FileStorageManager.UserPlaylist> playlists = ServerBot.getStorageManager().getUserPlaylists(userId);
        if (playlists.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed("No Playlists", "You have no saved playlists. Use `/playlist create` to make one.")).setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < playlists.size(); i++) {
            FileStorageManager.UserPlaylist pl = playlists.get(i);
            sb.append("`").append(i + 1).append(".` **").append(pl.name).append("** — ")
              .append(pl.entries.size()).append(" track(s)");
            if (pl.description != null && !pl.description.isBlank())
                sb.append("\n    *").append(pl.description).append("*");
            sb.append("\n");
        }
        event.replyEmbeds(new EmbedBuilder()
            .setColor(EmbedUtils.INFO_COLOR)
            .setTitle("🎵 Your Playlists")
            .setDescription(sb.toString())
            .setFooter("Use /playlist show name:<name> to see tracks")
            .build()).setEphemeral(true).queue();
    }

    // ─── show ────────────────────────────────────────────────────────────────

    private void handleShow(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = event.getOption("name", OptionMapping::getAsString);
        if (name == null || name.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Name", "Please provide a playlist name.")).setEphemeral(true).queue();
            return;
        }
        FileStorageManager.UserPlaylist pl = ServerBot.getStorageManager().getUserPlaylist(userId, name);
        if (pl == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found", "You don't have a playlist named **" + name + "**.")).setEphemeral(true).queue();
            return;
        }
        if (pl.entries.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed("Empty Playlist", "**" + name + "** has no tracks yet. Use `/playlist add` to add some.")).setEphemeral(true).queue();
            return;
        }
        StringBuilder sb = new StringBuilder();
        int max = Math.min(pl.entries.size(), 20);
        for (int i = 0; i < max; i++) {
            FileStorageManager.PlaylistEntry e = pl.entries.get(i);
            sb.append("`").append(i + 1).append(".` ").append(e.title != null ? e.title : e.url);
            if (e.duration > 0) sb.append(" `").append(MusicUtils.formatDuration(e.duration)).append("`");
            sb.append("\n");
        }
        if (pl.entries.size() > 20) sb.append("*... and ").append(pl.entries.size() - 20).append(" more*");
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(EmbedUtils.INFO_COLOR)
            .setTitle("🎵 " + pl.name)
            .setDescription(sb.toString())
            .setFooter(pl.entries.size() + " track(s) total");
        if (pl.description != null && !pl.description.isBlank()) embed.addField("Description", pl.description, false);
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    // ─── play ────────────────────────────────────────────────────────────────

    private void handlePlay(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        String name = event.getOption("name", OptionMapping::getAsString);
        if (name == null || name.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Name", "Please provide the playlist name to play.")).setEphemeral(true).queue();
            return;
        }
        FileStorageManager.UserPlaylist pl = ServerBot.getStorageManager().getUserPlaylist(userId, name);
        if (pl == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not Found", "You don't have a playlist named **" + name + "**.")).setEphemeral(true).queue();
            return;
        }
        if (pl.entries.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Empty Playlist", "**" + name + "** has no tracks. Add some with `/playlist add`.")).setEphemeral(true).queue();
            return;
        }

        // Check voice channel
        Member member = event.getMember();
        GuildVoiceState vs = member == null ? null : member.getVoiceState();
        if (vs == null || !vs.inAudioChannel()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Not in Voice Channel", "You need to be in a voice channel to play music.")).setEphemeral(true).queue();
            return;
        }
        AudioChannel voiceChannel = vs.getChannel();

        Integer startPos = event.getOption("position", OptionMapping::getAsInt);
        int startIdx = (startPos != null) ? Math.max(0, startPos - 1) : 0;
        List<FileStorageManager.PlaylistEntry> toPlay = pl.entries.subList(startIdx, pl.entries.size());

        event.deferReply().queue();

        MusicManager musicManager = MusicManager.getInstance();
        // Join the channel first
        musicManager.joinChannel(voiceChannel);

        AtomicInteger queued = new AtomicInteger(0);
        AtomicInteger total  = new AtomicInteger(toPlay.size());
        String playlistName  = pl.name;

        // Queue all tracks sequentially; report after first track is loaded
        for (int i = 0; i < toPlay.size(); i++) {
            final int trackNum = i;
            FileStorageManager.PlaylistEntry entry = toPlay.get(i);
            musicManager.loadAndPlay(entry.url, event.getGuild(),
                new MusicManager.MusicLoadCallback() {
                    @Override
                    public void onTrackLoaded(AudioTrack track) {
                        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        gmm.getScheduler().queue(track);
                        int done = queued.incrementAndGet();
                        if (trackNum == 0) {
                            // Reply after first track resolves
                            event.getHook().editOriginalEmbeds(new EmbedBuilder()
                                .setColor(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playing Playlist: " + playlistName)
                                .setDescription("Queuing **" + total.get() + "** track(s)…\nFirst track: **" + track.getInfo().title + "**")
                                .setFooter("Starting from position " + (startIdx + 1))
                                .build()).queue();
                        }
                    }
                    @Override
                    public void onPlaylistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                        if (!playlist.getTracks().isEmpty()) {
                            GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                            gmm.getScheduler().queue(playlist.getTracks().get(0));
                        }
                        queued.incrementAndGet();
                        if (trackNum == 0) {
                            event.getHook().editOriginalEmbeds(new EmbedBuilder()
                                .setColor(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playing Playlist: " + playlistName)
                                .setDescription("Queuing **" + total.get() + "** track(s)…")
                                .build()).queue();
                        }
                    }
                    @Override public void onNoMatches() { queued.incrementAndGet(); }
                    @Override public void onLoadFailed(String message) { queued.incrementAndGet(); }
                });
        }
    }

    // ─── command data ─────────────────────────────────────────────────────────

    public static CommandData getCommandData() {
        return Commands.slash("playlist", "Create, manage, and play your custom playlists.")
            .addSubcommands(
                new SubcommandData("create", "Create a new playlist")
                    .addOption(OptionType.STRING, "name", "Playlist name (max 50 chars)", true)
                    .addOption(OptionType.STRING, "description", "Optional description", false),
                new SubcommandData("delete", "Delete one of your playlists")
                    .addOption(OptionType.STRING, "name", "Playlist name", true),
                new SubcommandData("add", "Add a YouTube/direct URL track to a playlist")
                    .addOption(OptionType.STRING, "name", "Playlist name", true)
                    .addOption(OptionType.STRING, "url", "YouTube or direct URL of the track", true),
                new SubcommandData("remove", "Remove a track from a playlist by position")
                    .addOption(OptionType.STRING, "name", "Playlist name", true)
                    .addOption(OptionType.INTEGER, "position", "1-based position of the track to remove", true),
                new SubcommandData("list", "List all your playlists"),
                new SubcommandData("show", "Show all tracks in a playlist")
                    .addOption(OptionType.STRING, "name", "Playlist name", true),
                new SubcommandData("play", "Queue a saved playlist in your voice channel")
                    .addOption(OptionType.STRING, "name", "Playlist name", true)
                    .addOption(OptionType.INTEGER, "position", "Start from this track position (default: 1)", false)
            )
            .setContexts(InteractionContextType.GUILD);
    }
}
