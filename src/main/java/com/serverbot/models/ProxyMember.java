package com.serverbot.models;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Model class representing a proxy member (similar to PluralKit/Tupperbox)
 * A proxy member is an alternate identity that can send messages through the
 * bot
 */
public class ProxyMember {

    private String memberId;
    private String ownerId;
    private String guildId; // null = global (works everywhere), non-null = guild-specific
    private String name;
    private String displayName;
    private String pronouns;
    private String avatarUrl; // Cached local file:// URL
    private String originalAvatarUrl; // Original Discord CDN URL (may expire)
    private String description;
    private String color; // Hex color for embeds
    private List<ProxyTag> proxyTags;
    private boolean keepProxy; // Whether to keep the proxy tags in the message
    private boolean autoproxyEnabled;
    private Instant createdAt;
    private Instant updatedAt;
    private String groupId; // Optional group this member belongs to

    public ProxyMember() {
        this.proxyTags = new ArrayList<>();
        this.keepProxy = false;
        this.autoproxyEnabled = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public ProxyMember(String memberId, String ownerId, String guildId, String name) {
        this();
        this.memberId = memberId;
        this.ownerId = ownerId;
        this.guildId = guildId;
        this.name = name;
        this.displayName = name;
    }

    // Getters and Setters

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
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

    public String getPronouns() {
        return pronouns;
    }

    public void setPronouns(String pronouns) {
        this.pronouns = pronouns;
        this.updatedAt = Instant.now();
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
        this.updatedAt = Instant.now();
    }

    public String getOriginalAvatarUrl() {
        return originalAvatarUrl;
    }

    public void setOriginalAvatarUrl(String originalAvatarUrl) {
        this.originalAvatarUrl = originalAvatarUrl;
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

    public List<ProxyTag> getProxyTags() {
        return proxyTags;
    }

    public void setProxyTags(List<ProxyTag> proxyTags) {
        this.proxyTags = proxyTags;
        this.updatedAt = Instant.now();
    }

    public void addProxyTag(String prefix, String suffix) {
        this.proxyTags.add(new ProxyTag(prefix, suffix));
        this.updatedAt = Instant.now();
    }

    public void removeProxyTag(int index) {
        if (index >= 0 && index < proxyTags.size()) {
            this.proxyTags.remove(index);
            this.updatedAt = Instant.now();
        }
    }

    public boolean isKeepProxy() {
        return keepProxy;
    }

    public void setKeepProxy(boolean keepProxy) {
        this.keepProxy = keepProxy;
        this.updatedAt = Instant.now();
    }

    public boolean isAutoproxyEnabled() {
        return autoproxyEnabled;
    }

    public void setAutoproxyEnabled(boolean autoproxyEnabled) {
        this.autoproxyEnabled = autoproxyEnabled;
        this.updatedAt = Instant.now();
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

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if a message matches any of this member's proxy tags
     * 
     * @param message The message content
     * @return ProxyMatch if matched, null otherwise
     */
    public ProxyMatch matchesProxyTags(String message) {
        for (ProxyTag tag : proxyTags) {
            if (tag.matches(message)) {
                String content = tag.extractContent(message);
                return new ProxyMatch(this, tag, content);
            }
        }
        return null;
    }

    /**
     * Inner class representing a proxy tag (prefix/suffix pair)
     */
    public static class ProxyTag {
        private String prefix;
        private String suffix;

        public ProxyTag() {
        }

        public ProxyTag(String prefix, String suffix) {
            this.prefix = prefix != null ? prefix : "";
            this.suffix = suffix != null ? suffix : "";
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }

        /**
         * Check if a message matches this proxy tag
         */
        public boolean matches(String message) {
            if (message == null || message.isEmpty()) {
                return false;
            }

            if (prefix.isEmpty() && suffix.isEmpty()) {
                return false;
            }

            boolean prefixMatch = prefix.isEmpty() || message.startsWith(prefix);
            boolean suffixMatch = suffix.isEmpty() || message.endsWith(suffix);

            // Ensure there's content between prefix and suffix
            if (prefixMatch && suffixMatch) {
                int contentLength = message.length() - prefix.length() - suffix.length();
                return contentLength > 0;
            }

            return false;
        }

        /**
         * Extract content from a message by removing the proxy tags
         */
        public String extractContent(String message) {
            if (!matches(message)) {
                return message;
            }

            String content = message;
            if (!prefix.isEmpty()) {
                content = content.substring(prefix.length());
            }
            if (!suffix.isEmpty()) {
                content = content.substring(0, content.length() - suffix.length());
            }

            return content.trim();
        }

        @Override
        public String toString() {
            if (prefix.isEmpty() && suffix.isEmpty()) {
                return "(no tags)";
            } else if (suffix.isEmpty()) {
                return prefix + "text";
            } else if (prefix.isEmpty()) {
                return "text" + suffix;
            } else {
                return prefix + "text" + suffix;
            }
        }
    }

    /**
     * Inner class representing a successful proxy match
     */
    public static class ProxyMatch {
        private final ProxyMember member;
        private final ProxyTag tag;
        private final String content;

        public ProxyMatch(ProxyMember member, ProxyTag tag, String content) {
            this.member = member;
            this.tag = tag;
            this.content = content;
        }

        public ProxyMember getMember() {
            return member;
        }

        public ProxyTag getTag() {
            return tag;
        }

        public String getContent() {
            return content;
        }
    }
}
