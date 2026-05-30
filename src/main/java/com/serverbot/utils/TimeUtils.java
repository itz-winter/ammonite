package com.serverbot.utils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for time-related operations
 */
public class TimeUtils {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Parse a duration string (e.g., "1d2h30m" or "30m" or "2h")
     * 
     * @param durationStr The duration string
     * @return Duration object or null if invalid
     */
    public static Duration parseDuration(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return null;
        }

        Matcher matcher = DURATION_PATTERN.matcher(durationStr.toLowerCase().trim());
        if (!matcher.matches()) {
            return null;
        }

        long totalSeconds = 0;

        String days = matcher.group(1);
        String hours = matcher.group(2);
        String minutes = matcher.group(3);
        String seconds = matcher.group(4);

        if (days != null) {
            totalSeconds += Long.parseLong(days) * 86400; // 24 * 60 * 60
        }

        if (hours != null) {
            totalSeconds += Long.parseLong(hours) * 3600; // 60 * 60
        }

        if (minutes != null) {
            totalSeconds += Long.parseLong(minutes) * 60;
        }

        if (seconds != null) {
            totalSeconds += Long.parseLong(seconds);
        }

        if (totalSeconds <= 0) {
            return null;
        }

        return Duration.ofSeconds(totalSeconds);
    }

    /**
     * Format a duration to a readable string
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "Permanent";
        }

        long totalSeconds = duration.getSeconds();

        if (totalSeconds == 0) {
            return "0 seconds";
        }

        StringBuilder sb = new StringBuilder();

        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (days > 0) {
            sb.append(days).append(days == 1 ? " day" : " days");
        }

        if (hours > 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(hours).append(hours == 1 ? " hour" : " hours");
        }

        if (minutes > 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }

        if (seconds > 0) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(seconds).append(seconds == 1 ? " second" : " seconds");
        }

        return sb.toString();
    }

    /**
     * Format a LocalDateTime to a readable string
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(FORMATTER);
    }

    /**
     * Get time until a specific date
     */
    public static String getTimeUntil(LocalDateTime target) {
        Duration duration = Duration.between(LocalDateTime.now(), target);
        if (duration.isNegative()) {
            return "Expired";
        }
        return formatDuration(duration);
    }

    /**
     * Convert TimeUnit to Duration
     */
    public static Duration toDuration(long amount, TimeUnit unit) {
        return Duration.ofNanos(unit.toNanos(amount));
    }

    /**
     * Check if a duration string is valid
     */
    public static boolean isValidDuration(String durationStr) {
        return parseDuration(durationStr) != null;
    }
}
