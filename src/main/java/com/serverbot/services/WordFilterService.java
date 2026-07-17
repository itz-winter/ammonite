package com.serverbot.services;

import com.serverbot.ServerBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/**
 * Manages per-guild word/phrase filters with optional regex support.
 *
 * Storage key: "wordFilter"  — List<String> of raw patterns (plain or regex)
 * Storage key: "wordFilterEnabled" — Boolean
 * Storage key: "wordFilterAction" — String: "delete" | "warn" | "delete+warn"
 */
public class WordFilterService {

    private static final Logger logger = LoggerFactory.getLogger(WordFilterService.class);

    /** Compiled pattern cache: guildId → list of (rawPattern, compiled Pattern) */
    private static final Map<String, List<Pattern>> compiledCache = new ConcurrentHashMap<>();

    /** Invalidate compiled cache for a guild when its filter list changes. */
    public static void invalidateCache(String guildId) {
        compiledCache.remove(guildId);
    }

    /** Returns true if the word filter is enabled for this guild. */
    public static boolean isEnabled(String guildId) {
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        Object v = settings.get("wordFilterEnabled");
        return Boolean.TRUE.equals(v);
    }

    /** Returns the action for a match: "delete", "warn", or "delete+warn". Default: "delete". */
    public static String getAction(String guildId) {
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        Object v = settings.get("wordFilterAction");
        return (v instanceof String s) ? s : "delete";
    }

    /**
     * Returns all raw filter patterns for the guild.
     */
    @SuppressWarnings("unchecked")
    public static List<String> getPatterns(String guildId) {
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        Object v = settings.get("wordFilter");
        if (v instanceof List<?> list) {
            return new ArrayList<>((List<String>) list);
        }
        return new ArrayList<>();
    }

    /**
     * Add a pattern. Returns an error message string, or null on success.
     */
    public static String addPattern(String guildId, String pattern) {
        // Validate — try to compile it as a regex (plain words are valid regexes too)
        try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
        } catch (PatternSyntaxException e) {
            return "Invalid regex pattern: `" + pattern + "` — " + e.getDescription();
        }
        List<String> patterns = getPatterns(guildId);
        if (patterns.contains(pattern)) {
            return "Pattern `" + pattern + "` is already in the filter list.";
        }
        if (patterns.size() >= 100) {
            return "Maximum of 100 patterns reached. Remove some before adding more.";
        }
        patterns.add(pattern);
        ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilter", patterns);
        invalidateCache(guildId);
        return null;
    }

    /**
     * Remove a pattern by exact string. Returns an error string, or null on success.
     */
    public static String removePattern(String guildId, String pattern) {
        List<String> patterns = getPatterns(guildId);
        if (!patterns.remove(pattern)) {
            return "Pattern `" + pattern + "` not found in the filter list.";
        }
        ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilter", patterns);
        invalidateCache(guildId);
        return null;
    }

    /**
     * Remove a pattern by 1-based index. Returns error string or null on success.
     */
    public static String removePatternByIndex(String guildId, int index) {
        List<String> patterns = getPatterns(guildId);
        if (index < 1 || index > patterns.size()) {
            return "Index " + index + " is out of range (1–" + patterns.size() + ").";
        }
        patterns.remove(index - 1);
        ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilter", patterns);
        invalidateCache(guildId);
        return null;
    }

    /** Clear all patterns. */
    public static void clearPatterns(String guildId) {
        ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilter", new ArrayList<>());
        invalidateCache(guildId);
    }

    /**
     * Check if the given text matches any filter pattern.
     * Returns the first matching raw pattern string, or null if no match.
     */
    public static String findMatch(String guildId, String text) {
        if (text == null || text.isBlank()) return null;
        List<Pattern> compiled = getCompiledPatterns(guildId);
        List<String> raw = getPatterns(guildId);
        for (int i = 0; i < compiled.size() && i < raw.size(); i++) {
            if (compiled.get(i).matcher(text).find()) {
                return raw.get(i);
            }
        }
        return null;
    }

    private static List<Pattern> getCompiledPatterns(String guildId) {
        return compiledCache.computeIfAbsent(guildId, id -> {
            List<String> raw = getPatterns(id);
            return raw.stream()
                    .map(p -> {
                        try {
                            return Pattern.compile(p, Pattern.CASE_INSENSITIVE);
                        } catch (PatternSyntaxException e) {
                            logger.warn("Invalid word filter pattern in guild {}: {}", id, p);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        });
    }
}
