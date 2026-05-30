package com.serverbot.models;

import java.time.LocalDateTime;

/**
 * Model class for guild settings
 */
public class GuildSettings {

    private String guildId;
    private String prefix;
    private String modLogChannel;
    private String punishmentLogChannel;
    private String allLogChannel;
    private boolean autoroleEnabled;
    private boolean levelsEnabled;
    private boolean pointsEnabled;
    private boolean automodEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public GuildSettings() {
    }

    public GuildSettings(String guildId) {
        this.guildId = guildId;
        this.prefix = "/";
        this.autoroleEnabled = false;
        this.levelsEnabled = false;
        this.pointsEnabled = false;
        this.automodEnabled = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getModLogChannel() {
        return modLogChannel;
    }

    public void setModLogChannel(String modLogChannel) {
        this.modLogChannel = modLogChannel;
    }

    public String getPunishmentLogChannel() {
        return punishmentLogChannel;
    }

    public void setPunishmentLogChannel(String punishmentLogChannel) {
        this.punishmentLogChannel = punishmentLogChannel;
    }

    public String getAllLogChannel() {
        return allLogChannel;
    }

    public void setAllLogChannel(String allLogChannel) {
        this.allLogChannel = allLogChannel;
    }

    public boolean isAutoroleEnabled() {
        return autoroleEnabled;
    }

    public void setAutoroleEnabled(boolean autoroleEnabled) {
        this.autoroleEnabled = autoroleEnabled;
    }

    public boolean isLevelsEnabled() {
        return levelsEnabled;
    }

    public void setLevelsEnabled(boolean levelsEnabled) {
        this.levelsEnabled = levelsEnabled;
    }

    public boolean isPointsEnabled() {
        return pointsEnabled;
    }

    public void setPointsEnabled(boolean pointsEnabled) {
        this.pointsEnabled = pointsEnabled;
    }

    public boolean isAutomodEnabled() {
        return automodEnabled;
    }

    public void setAutomodEnabled(boolean automodEnabled) {
        this.automodEnabled = automodEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
