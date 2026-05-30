package com.serverbot.utils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages command cooldowns to prevent spam
 */
public class CooldownManager {

    private static final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();

    /**
     * Check if a user is on cooldown for a specific command
     * 
     * @param userId          The user ID
     * @param commandName     The command name
     * @param cooldownSeconds The cooldown time in seconds
     * @return true if the user is on cooldown, false otherwise
     */
    public static boolean isOnCooldown(String userId, String commandName, int cooldownSeconds) {
        String key = userId + ":" + commandName;
        Long lastUsed = cooldowns.get(key);

        if (lastUsed == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        long cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds);

        return (currentTime - lastUsed) < cooldownMillis;
    }

    /**
     * Get the remaining cooldown time in seconds
     * 
     * @param userId          The user ID
     * @param commandName     The command name
     * @param cooldownSeconds The total cooldown time in seconds
     * @return The remaining cooldown time in seconds, or 0 if not on cooldown
     */
    public static long getRemainingCooldown(String userId, String commandName, int cooldownSeconds) {
        String key = userId + ":" + commandName;
        Long lastUsed = cooldowns.get(key);

        if (lastUsed == null) {
            return 0;
        }

        long currentTime = System.currentTimeMillis();
        long cooldownMillis = TimeUnit.SECONDS.toMillis(cooldownSeconds);
        long elapsed = currentTime - lastUsed;

        if (elapsed >= cooldownMillis) {
            return 0;
        }

        return TimeUnit.MILLISECONDS.toSeconds(cooldownMillis - elapsed);
    }

    /**
     * Set the cooldown for a user and command
     * 
     * @param userId      The user ID
     * @param commandName The command name
     */
    public static void setCooldown(String userId, String commandName) {
        String key = userId + ":" + commandName;
        cooldowns.put(key, System.currentTimeMillis());
    }

    /**
     * Clear a specific cooldown
     * 
     * @param userId      The user ID
     * @param commandName The command name
     */
    public static void clearCooldown(String userId, String commandName) {
        String key = userId + ":" + commandName;
        cooldowns.remove(key);
    }

    /**
     * Clear all cooldowns (use with caution)
     */
    public static void clearAllCooldowns() {
        cooldowns.clear();
    }

    /**
     * Clean up expired cooldowns (call periodically)
     * 
     * @param maxCooldownSeconds The maximum cooldown time to consider
     */
    public static void cleanup(int maxCooldownSeconds) {
        long currentTime = System.currentTimeMillis();
        long maxCooldownMillis = TimeUnit.SECONDS.toMillis(maxCooldownSeconds);

        cooldowns.entrySet().removeIf(entry -> (currentTime - entry.getValue()) > maxCooldownMillis);
    }
}
