package com.serverbot.models;

import java.time.Instant;

/**
 * Model class for user proxy settings
 * Similar to PluralKit's system settings
 */
public class ProxySettings {

    private String userId;
    private String guildId;
    private boolean proxyEnabled;
    private AutoproxyMode autoproxyMode;
    private String autoproxyMemberId; // For LATCH and MEMBER modes
    private boolean showProxyIndicator; // Show a small indicator that message is proxied
    private boolean caseSensitiveTags;
    private Instant lastSwitchTime;
    private String lastProxiedMemberId;
    private Instant updatedAt;

    public ProxySettings() {
        this.proxyEnabled = true;
        this.autoproxyMode = AutoproxyMode.OFF;
        this.showProxyIndicator = false;
        this.caseSensitiveTags = false;
        this.updatedAt = Instant.now();
    }

    public ProxySettings(String userId, String guildId) {
        this();
        this.userId = userId;
        this.guildId = guildId;
    }

    // Getters and Setters

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public boolean isProxyEnabled() {
        return proxyEnabled;
    }

    public void setProxyEnabled(boolean proxyEnabled) {
        this.proxyEnabled = proxyEnabled;
        this.updatedAt = Instant.now();
    }

    public AutoproxyMode getAutoproxyMode() {
        return autoproxyMode;
    }

    public void setAutoproxyMode(AutoproxyMode autoproxyMode) {
        this.autoproxyMode = autoproxyMode;
        this.updatedAt = Instant.now();
    }

    public String getAutoproxyMemberId() {
        return autoproxyMemberId;
    }

    public void setAutoproxyMemberId(String autoproxyMemberId) {
        this.autoproxyMemberId = autoproxyMemberId;
        this.updatedAt = Instant.now();
    }

    public boolean isShowProxyIndicator() {
        return showProxyIndicator;
    }

    public void setShowProxyIndicator(boolean showProxyIndicator) {
        this.showProxyIndicator = showProxyIndicator;
        this.updatedAt = Instant.now();
    }

    public boolean isCaseSensitiveTags() {
        return caseSensitiveTags;
    }

    public void setCaseSensitiveTags(boolean caseSensitiveTags) {
        this.caseSensitiveTags = caseSensitiveTags;
        this.updatedAt = Instant.now();
    }

    public Instant getLastSwitchTime() {
        return lastSwitchTime;
    }

    public void setLastSwitchTime(Instant lastSwitchTime) {
        this.lastSwitchTime = lastSwitchTime;
    }

    public String getLastProxiedMemberId() {
        return lastProxiedMemberId;
    }

    public void setLastProxiedMemberId(String lastProxiedMemberId) {
        this.lastProxiedMemberId = lastProxiedMemberId;
        this.lastSwitchTime = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Autoproxy modes similar to PluralKit
     */
    public enum AutoproxyMode {
        OFF, // No autoproxy
        FRONT, // Proxy as whoever was last manually proxied (latch mode)
        LATCH, // Same as FRONT (alias)
        MEMBER, // Always proxy as a specific member
        STICKY // Proxy as the last used member until changed
    }
}
