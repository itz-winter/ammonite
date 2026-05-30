package com.serverbot.models;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a global chat channel that links text channels across multiple
 * servers.
 * Messages sent in any linked channel are relayed to all other linked channels
 * via webhooks.
 */
public class GlobalChatChannel {

    private String channelId; // Random unique ID (e.g. "gc-a1b2c3")
    private String name;
    private String description;
    private String visibility; // "public" or "private"
    private String key; // Join key (null if no key required)
    private boolean keyRequired;
    private String ownerId; // Discord user ID of the creator
    private Set<String> coOwnerIds; // Discord user IDs of co-owners
    private Set<String> moderatorIds; // Discord user IDs of moderators
    private List<String> rules; // Channel rules list

    // guildId -> textChannelId (one linked channel per guild)
    private Map<String, String> linkedChannels;

    // Moderation state
    private Set<String> bannedServers; // Guild IDs
    private Map<String, Long> mutedServers; // Guild ID -> unmute timestamp (epoch millis), 0 = permanent
    private Map<String, List<String>> warnedServers; // Guild ID -> list of warning reasons
    private Set<String> kickedServers; // Guild IDs that were kicked (can rejoin)

    private long createdAt; // epoch millis

    // Custom message format: used in webhook display name instead of default "[GC]
    // user • server"
    // Default when null: prefix = "[GC]", suffix = "• {serverName}"
    private String messagePrefix; // e.g. "[Lounge]"
    private String messageSuffix; // e.g. "@ {serverName}"

    // Full display name template — overrides prefix+authorName+suffix entirely when
    // non-null.
    // Supports same placeholders: {user}, {username}, {displayname}, {server},
    // {pronouns}
    // e.g. "{user} @ {server}"
    private String nameFormat;

    // Optional text prepended to every relayed message (overridden by nameFormat)
    private String msgPrefix;

    // Optional text appended to every relayed message (overridden by nameFormat)
    private String msgSuffix;

    // Whether to relay via webhook (true, default) or as a plain bot message
    // (false)
    private boolean webhookEnabled = true;

    public GlobalChatChannel() {
        this.coOwnerIds = new HashSet<>();
        this.moderatorIds = new HashSet<>();
        this.rules = new ArrayList<>();
        this.linkedChannels = new ConcurrentHashMap<>();
        this.bannedServers = new HashSet<>();
        this.mutedServers = new ConcurrentHashMap<>();
        this.warnedServers = new ConcurrentHashMap<>();
        this.kickedServers = new HashSet<>();
        this.createdAt = Instant.now().toEpochMilli();
    }

    public GlobalChatChannel(String channelId, String name, String description,
            String visibility, boolean keyRequired, String key, String ownerId) {
        this();
        this.channelId = channelId;
        this.name = name;
        this.description = description;
        this.visibility = visibility;
        this.keyRequired = keyRequired;
        this.key = key;
        this.ownerId = ownerId;
    }

    // Ownership / role checks

    public boolean isOwner(String userId) {
        return ownerId != null && ownerId.equals(userId);
    }

    public boolean isCoOwner(String userId) {
        return coOwnerIds.contains(userId);
    }

    public boolean isModerator(String userId) {
        return moderatorIds.contains(userId);
    }

    public boolean hasManageAccess(String userId) {
        return isOwner(userId) || isCoOwner(userId);
    }

    public boolean hasModerateAccess(String userId) {
        return isOwner(userId) || isCoOwner(userId) || isModerator(userId);
    }

    // Linked channels

    public boolean isGuildLinked(String guildId) {
        return linkedChannels.containsKey(guildId);
    }

    public String getLinkedTextChannel(String guildId) {
        return linkedChannels.get(guildId);
    }

    public void linkChannel(String guildId, String textChannelId) {
        linkedChannels.put(guildId, textChannelId);
    }

    public void unlinkChannel(String guildId) {
        linkedChannels.remove(guildId);
    }

    // Server moderation

    public boolean isServerBanned(String guildId) {
        return bannedServers.contains(guildId);
    }

    public void banServer(String guildId) {
        bannedServers.add(guildId);
        unlinkChannel(guildId);
    }

    public void unbanServer(String guildId) {
        bannedServers.remove(guildId);
    }

    public void kickServer(String guildId) {
        kickedServers.add(guildId);
        unlinkChannel(guildId);
    }

    public boolean isServerMuted(String guildId) {
        if (!mutedServers.containsKey(guildId))
            return false;
        long unmute = mutedServers.get(guildId);
        if (unmute == 0)
            return true; // permanent
        if (Instant.now().toEpochMilli() >= unmute) {
            mutedServers.remove(guildId); // expired
            return false;
        }
        return true;
    }

    public void muteServer(String guildId, long durationMillis) {
        long unmute = durationMillis <= 0 ? 0 : Instant.now().toEpochMilli() + durationMillis;
        mutedServers.put(guildId, unmute);
    }

    public void unmuteServer(String guildId) {
        mutedServers.remove(guildId);
    }

    public void warnServer(String guildId, String reason) {
        warnedServers.computeIfAbsent(guildId, k -> new ArrayList<>()).add(reason);
    }

    public void unwarnServer(String guildId) {
        warnedServers.remove(guildId);
    }

    public List<String> getServerWarnings(String guildId) {
        return warnedServers.getOrDefault(guildId, Collections.emptyList());
    }

    // Getters / setters

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public boolean isKeyRequired() {
        return keyRequired;
    }

    public void setKeyRequired(boolean keyRequired) {
        this.keyRequired = keyRequired;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public Set<String> getCoOwnerIds() {
        return coOwnerIds;
    }

    public void setCoOwnerIds(Set<String> coOwnerIds) {
        this.coOwnerIds = coOwnerIds;
    }

    public Set<String> getModeratorIds() {
        return moderatorIds;
    }

    public void setModeratorIds(Set<String> moderatorIds) {
        this.moderatorIds = moderatorIds;
    }

    public List<String> getRules() {
        return rules;
    }

    public void setRules(List<String> rules) {
        this.rules = rules;
    }

    public Map<String, String> getLinkedChannels() {
        return linkedChannels;
    }

    public void setLinkedChannels(Map<String, String> linkedChannels) {
        this.linkedChannels = linkedChannels;
    }

    public Set<String> getBannedServers() {
        return bannedServers;
    }

    public void setBannedServers(Set<String> bannedServers) {
        this.bannedServers = bannedServers;
    }

    public Map<String, Long> getMutedServers() {
        return mutedServers;
    }

    public void setMutedServers(Map<String, Long> mutedServers) {
        this.mutedServers = mutedServers;
    }

    public Map<String, List<String>> getWarnedServers() {
        return warnedServers;
    }

    public void setWarnedServers(Map<String, List<String>> warnedServers) {
        this.warnedServers = warnedServers;
    }

    public Set<String> getKickedServers() {
        return kickedServers;
    }

    public void setKickedServers(Set<String> kickedServers) {
        this.kickedServers = kickedServers;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getMessagePrefix() {
        return messagePrefix;
    }

    public void setMessagePrefix(String messagePrefix) {
        this.messagePrefix = messagePrefix;
    }

    public String getMessageSuffix() {
        return messageSuffix;
    }

    public void setMessageSuffix(String messageSuffix) {
        this.messageSuffix = messageSuffix;
    }

    public String getNameFormat() {
        return nameFormat;
    }

    public void setNameFormat(String nameFormat) {
        this.nameFormat = nameFormat;
    }

    public String getMsgPrefix() {
        return msgPrefix;
    }

    public void setMsgPrefix(String msgPrefix) {
        this.msgPrefix = msgPrefix;
    }

    public String getMsgSuffix() {
        return msgSuffix;
    }

    public void setMsgSuffix(String msgSuffix) {
        this.msgSuffix = msgSuffix;
    }

    public boolean isWebhookEnabled() {
        return webhookEnabled;
    }

    public void setWebhookEnabled(boolean webhookEnabled) {
        this.webhookEnabled = webhookEnabled;
    }
}
