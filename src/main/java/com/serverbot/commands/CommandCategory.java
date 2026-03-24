package com.serverbot.commands;

import com.serverbot.utils.CustomEmojis;

/**
 * Enum representing different command categories
 */
public enum CommandCategory {
    
    MODERATION("Moderation", "🔨", "Commands for server moderation"),
    UTILITY("Utility", "🛠️", "General utility commands"),
    CONFIGURATION("Configuration", CustomEmojis.SETTING, "Bot and server configuration commands"),
    LEVELING("Leveling", "📈", "Leveling and XP related commands"),
    ECONOMY("Economy", "💰", "Economy and points system commands"),
    GAMES("Games", "🎮", "Interactive games and entertainment"),
    GAMBLING("Gambling", "🎲", "Gambling and betting commands"),
    BANKING("Banking", "🏦", "Banking system commands"),
    RULES("Rules", "📋", "Server rules management"),
    AUTOMOD("AutoMod", "🤖", "Automatic moderation features"),
    REACTION_ROLES("Reaction Roles", "🎭", "Reaction role system"),
    LOGGING("Logging", "📝", "Server logging configuration"),
    SUPPORT("Support", "🎫", "Ticket and support system"),
    GLOBAL_CHAT("Global Chat", "🌐", "Cross-server global chat system"),
    MUSIC("Music", "🎵", "Music playback and queue management");
    
    private final String displayName;
    private final String emoji;
    private final String description;
    
    CommandCategory(String displayName, String emoji, String description) {
        this.displayName = displayName;
        this.emoji = emoji;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getEmoji() {
        return emoji;
    }
    
    public String getDescription() {
        return description;
    }
    
    @Override
    public String toString() {
        return emoji + " " + displayName;
    }
}
