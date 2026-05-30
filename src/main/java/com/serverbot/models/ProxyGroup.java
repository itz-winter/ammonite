package com.serverbot.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a proxy group (collection of related proxy members)
 * Similar to PluralKit's group functionality
 */
public class ProxyGroup {

    private String groupId;
    private String ownerId;
    private String guildId;
    private String name;
    private String displayName;
    private String description;
    private String color; // Hex color
    private String iconUrl;
    private List<String> memberIds;
    private Instant createdAt;
    private Instant updatedAt;

    public ProxyGroup() {
        this.memberIds = new ArrayList<>();
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ProxyGroup(String groupId, String ownerId, String guildId, String name) {
        this();
        this.groupId = groupId;
        this.ownerId = ownerId;
        this.guildId = guildId;
        this.name = name;
        this.displayName = name;
    }

    // Getters and Setters

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = Instant.now();
    }

    public String getDisplayName() {
        return displayName != null ? displayName : name;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.updatedAt = Instant.now();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.updatedAt = Instant.now();
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
        this.updatedAt = Instant.now();
    }

    public String getIconUrl() {
        return iconUrl;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
        this.updatedAt = Instant.now();
    }

    public List<String> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(List<String> memberIds) {
        this.memberIds = memberIds;
        this.updatedAt = Instant.now();
    }

    public void addMember(String memberId) {
        if (!this.memberIds.contains(memberId)) {
            this.memberIds.add(memberId);
            this.updatedAt = Instant.now();
        }
    }

    public void removeMember(String memberId) {
        this.memberIds.remove(memberId);
        this.updatedAt = Instant.now();
    }

    public boolean hasMember(String memberId) {
        return this.memberIds.contains(memberId);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
