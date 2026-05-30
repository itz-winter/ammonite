package com.serverbot.models;

import java.time.LocalDateTime;

/**
 * Model class for user warnings
 */
public class UserWarning {

    private int id;
    private String guildId;
    private String userId;
    private String moderatorId;
    private String reason;
    private LocalDateTime createdAt;
    private boolean active;

    public UserWarning() {
    }

    public UserWarning(String guildId, String userId, String moderatorId, String reason) {
        this.guildId = guildId;
        this.userId = userId;
        this.moderatorId = moderatorId;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }

    // Getters and Setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getModeratorId() {
        return moderatorId;
    }

    public void setModeratorId(String moderatorId) {
        this.moderatorId = moderatorId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
