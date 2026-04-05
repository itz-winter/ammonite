package com.serverbot.utils;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Bot configuration class
 */
public class BotConfig {
    
    @SerializedName("bot_token")
    private String botToken = "YOUR_BOT_TOKEN_HERE";
    
    @SerializedName("database_path")
    private String databasePath = "data/serverbot.db";
    
    @SerializedName("default_prefix")
    private String defaultPrefix = "/";
    
    @SerializedName("owner_id")
    private String ownerId = "YOUR_USER_ID_HERE";
    
    @SerializedName("owner_ids")
    private List<String> ownerIds = new ArrayList<>();
    
    @SerializedName("support_server_invite")
    private String supportServerInvite = "";
    
    @SerializedName("bot_version")
    private String botVersion = "1.0.0";
    
    @SerializedName("default_status_message")
    private String defaultStatusMessage = "";
    
    @SerializedName("default_online_status")
    private String defaultOnlineStatus = "online";
    
    @SerializedName("default_rpc_type")
    private String defaultRpcType = "watching";
    
    @SerializedName("default_rpc_text")
    private String defaultRpcText = "for commands";
    
    @SerializedName("hide_owner_commands")
    private boolean hideOwnerCommands = false;
    
    @SerializedName("spotify_client_id")
    private String spotifyClientId = "";
    
    @SerializedName("spotify_client_secret")
    private String spotifyClientSecret = "";
    
    @SerializedName("spotify_country_code")
    private String spotifyCountryCode = "US";
    
    public String getBotToken() {
        return botToken;
    }
    
    public void setBotToken(String botToken) {
        this.botToken = botToken;
    }
    
    public String getDatabasePath() {
        return databasePath;
    }
    
    public void setDatabasePath(String databasePath) {
        this.databasePath = databasePath;
    }
    
    public String getDefaultPrefix() {
        return defaultPrefix;
    }
    
    public void setDefaultPrefix(String defaultPrefix) {
        this.defaultPrefix = defaultPrefix;
    }
    
    public String getOwnerId() {
        return ownerId;
    }
    
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    
    public List<String> getOwnerIds() {
        return ownerIds;
    }
    
    public void setOwnerIds(List<String> ownerIds) {
        this.ownerIds = ownerIds;
    }
    
    /**
     * Get all owner IDs (combines single owner_id and owner_ids array)
     * @return List of all owner IDs
     */
    public List<String> getAllOwnerIds() {
        List<String> allOwners = new ArrayList<>();
        
        // Add owner_ids array if present and not empty
        if (ownerIds != null && !ownerIds.isEmpty()) {
            allOwners.addAll(ownerIds);
        }
        
        // Add single owner_id if present and valid (backward compatibility)
        if (ownerId != null && !ownerId.isEmpty() && 
            !ownerId.equals("YOUR_USER_ID_HERE") && 
            !allOwners.contains(ownerId)) {
            allOwners.add(ownerId);
        }
        
        return allOwners;
    }
    
    /**
     * Check if a user ID is a bot owner
     * @param userId The user ID to check
     * @return true if the user is an owner
     */
    public boolean isOwner(String userId) {
        if (userId == null || userId.isEmpty()) {
            return false;
        }
        return getAllOwnerIds().contains(userId);
    }
    
    public String getSupportServerInvite() {
        return supportServerInvite;
    }
    
    public void setSupportServerInvite(String supportServerInvite) {
        this.supportServerInvite = supportServerInvite;
    }
    
    public String getBotVersion() {
        return botVersion;
    }
    
    public void setBotVersion(String botVersion) {
        this.botVersion = botVersion;
    }
    
    public String getDefaultStatusMessage() {
        return defaultStatusMessage;
    }
    
    public void setDefaultStatusMessage(String defaultStatusMessage) {
        this.defaultStatusMessage = defaultStatusMessage;
    }
    
    public String getDefaultOnlineStatus() {
        return defaultOnlineStatus;
    }
    
    public void setDefaultOnlineStatus(String defaultOnlineStatus) {
        this.defaultOnlineStatus = defaultOnlineStatus;
    }
    
    public String getDefaultRpcType() {
        return defaultRpcType;
    }
    
    public void setDefaultRpcType(String defaultRpcType) {
        this.defaultRpcType = defaultRpcType;
    }
    
    public String getDefaultRpcText() {
        return defaultRpcText;
    }
    
    public void setDefaultRpcText(String defaultRpcText) {
        this.defaultRpcText = defaultRpcText;
    }
    
    public boolean isHideOwnerCommands() {
        return hideOwnerCommands;
    }
    
    public void setHideOwnerCommands(boolean hideOwnerCommands) {
        this.hideOwnerCommands = hideOwnerCommands;
    }
    
    public String getSpotifyClientId() {
        return spotifyClientId;
    }
    
    public void setSpotifyClientId(String spotifyClientId) {
        this.spotifyClientId = spotifyClientId;
    }
    
    public String getSpotifyClientSecret() {
        return spotifyClientSecret;
    }
    
    public void setSpotifyClientSecret(String spotifyClientSecret) {
        this.spotifyClientSecret = spotifyClientSecret;
    }
    
    public String getSpotifyCountryCode() {
        return spotifyCountryCode;
    }
    
    public void setSpotifyCountryCode(String spotifyCountryCode) {
        this.spotifyCountryCode = spotifyCountryCode;
    }
}
