package com.serverbot.models;

import java.awt.Color;

/**
 * Represents the level of suspicion for a detected account.
 * 
 * LOW_SUSPICION: 1 suspicious quality flagged - no recommended action
 * SUSPICIOUS: 2+ suspicious qualities flagged - default monitoring action
 * HIGHLY_SUSPICIOUS: Blatant bot account - stronger action recommended
 * CONFIRMED_SUSPICIOUS: Validated report with evidence - confirmed threat
 */
public enum SuspicionLevel {

    LOW_SUSPICION(
            "Low Suspicion",
            "⚪",
            new Color(169, 169, 169), // Gray
            null// No recommended action for low suspicion
    ),

    SUSPICIOUS(
            "Suspicious",
            "🟡",
            new Color(255, 165, 0), // Orange
            "Monitor user closely. No immediate action needed."),

    HIGHLY_SUSPICIOUS(
            "Highly Suspicious",
            "🔴",
            new Color(255, 0, 0), // Red
            "Consider restricting user permissions or banning. High likelihood of malicious intent."),

    CONFIRMED_SUSPICIOUS(
            "Confirmed Suspicious",
            "⛔",
            new Color(139, 0, 0), // Dark Red
            "Validated threat. Immediate action recommended - consider banning.");

    private final String displayName;
    private final String emoji;
    private final Color color;
    private final String defaultAction;

    SuspicionLevel(String displayName, String emoji, Color color, String defaultAction) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.color = color;
        this.defaultAction = defaultAction;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmoji() {
        return emoji;
    }

    public Color getColor() {
        return color;
    }

    /**
     * Gets the default recommended action for this suspicion level.
     * 
     * @return The default action string, or null if no action is recommended
     *         (LOW_SUSPICION)
     */
    public String getDefaultAction() {
        return defaultAction;
    }

    /**
     * Whether this level should include a recommended action in notifications.
     */
    public boolean hasRecommendedAction() {
        return defaultAction != null;
    }

    /**
     * Determines the suspicion level based on the number of flagged qualities.
     * 
     * @param flagCount    The number of suspicious qualities detected
     * @param isBlatantBot Whether the account is a blatant bot (e.g., very new + no
     *                     avatar + suspicious name)
     * @return The appropriate suspicion level
     */
    public static SuspicionLevel fromFlagCount(int flagCount, boolean isBlatantBot) {
        if (isBlatantBot) {
            return HIGHLY_SUSPICIOUS;
        } else if (flagCount >= 2) {
            return SUSPICIOUS;
        } else if (flagCount == 1) {
            return LOW_SUSPICION;
        } else {
            return null; // Not suspicious at all
        }
    }

    /**
     * Gets a suspicion level by its name (case-insensitive).
     */
    public static SuspicionLevel fromString(String name) {
        if (name == null)
            return null;
        String normalized = name.toUpperCase().replace(" ", "_").replace("-", "_");
        try {
            return SuspicionLevel.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            // Try matching display names
            for (SuspicionLevel level : values()) {
                if (level.displayName.equalsIgnoreCase(name)) {
                    return level;
                }
            }
            return null;
        }
    }

    @Override
    public String toString() {
        return emoji + " " + displayName;
    }
}
