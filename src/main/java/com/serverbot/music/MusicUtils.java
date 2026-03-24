package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

/**
 * Utility class for music-related formatting and parsing.
 */
public class MusicUtils {

    private MusicUtils() {}

    /**
     * Format a duration in milliseconds to a human-readable string (MM:SS or HH:MM:SS).
     */
    public static String formatDuration(long durationMs) {
        long seconds = durationMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format("%d:%02d", minutes, secs);
    }

    /**
     * Format a track for display (title - author [duration]).
     */
    public static String formatTrack(AudioTrack track) {
        AudioTrackInfo info = track.getInfo();
        return String.format("[%s](%s) by **%s** `[%s]`",
                info.title, info.uri, info.author, formatDuration(info.length));
    }

    /**
     * Format a track for compact display in queue listings.
     */
    public static String formatTrackShort(AudioTrack track, int index) {
        AudioTrackInfo info = track.getInfo();
        String title = info.title.length() > 45 ? info.title.substring(0, 42) + "..." : info.title;
        return String.format("`%d.` [%s](%s) `[%s]`", index, title, info.uri, formatDuration(info.length));
    }

    /**
     * Parse an index range string like "4:10", "5:", ":9", or "" (empty).
     * Returns an int array [startIndex, endIndex] (1-based).
     * -1 means "use default" (start=1 or end=last).
     *
     * Examples:
     *   "4:10" -> [4, 10]   (tracks 4 through 10)
     *   "5:"   -> [5, -1]   (track 5 through end)
     *   ":9"   -> [1, 9]    (track 1 through 9)
     *   "7"    -> [7, 7]    (single track 7)
     *   ""     -> [1, -1]   (all tracks)
     */
    public static int[] parseIndexRange(String indexStr) {
        if (indexStr == null || indexStr.isBlank()) {
            return new int[]{1, -1}; // All tracks
        }

        indexStr = indexStr.trim();

        if (indexStr.contains(":")) {
            String[] parts = indexStr.split(":", 2);
            int start = parts[0].isBlank() ? 1 : parseIntSafe(parts[0], 1);
            int end = parts[1].isBlank() ? -1 : parseIntSafe(parts[1], -1);
            return new int[]{Math.max(1, start), end};
        } else {
            // Single index — select just that one track
            int index = parseIntSafe(indexStr, 1);
            return new int[]{Math.max(1, index), index};
        }
    }

    /**
     * Generate a text progress bar for the current track position.
     */
    public static String createProgressBar(long position, long duration) {
        int barLength = 15;
        int filled = duration > 0 ? (int) ((position * barLength) / duration) : 0;
        filled = Math.max(0, Math.min(barLength, filled));

        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < barLength; i++) {
            if (i == filled) {
                bar.append("🔘");
            } else if (i < filled) {
                bar.append("▬");
            } else {
                bar.append("▬");
            }
        }
        return bar.toString();
    }

    private static int parseIntSafe(String str, int defaultValue) {
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
