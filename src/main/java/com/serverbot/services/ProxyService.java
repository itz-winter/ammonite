package com.serverbot.services;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.serverbot.ServerBot;
import com.serverbot.models.ProxyGroup;
import com.serverbot.models.ProxyMember;
import com.serverbot.models.ProxySettings;
import com.serverbot.utils.AvatarCacheManager;
import com.serverbot.utils.CustomEmojis;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing proxy system functionality (similar to
 * PluralKit/Tupperbox)
 * Handles proxy members, groups, webhooks, and message proxying
 */
public class ProxyService {

    private static final Logger logger = LoggerFactory.getLogger(ProxyService.class);
    private static final String PROXY_DATA_DIR = "data/proxy/";
    private static final String MEMBERS_FILE = "members.json";
    private static final String GROUPS_FILE = "groups.json";
    private static final String SETTINGS_FILE = "settings.json";
    private static final String PROXIED_MESSAGES_FILE = "proxied_messages.json";

    private final Gson gson;
    private final Map<String, ProxyMember> members; // memberId -> ProxyMember
    private final Map<String, ProxyGroup> groups; // groupId -> ProxyGroup
    private final Map<String, ProxySettings> userSettings; // userId:guildId -> ProxySettings
    private final Map<String, ProxiedMessageData> proxiedMessages; // proxyMessageId -> original message data
    private final Map<String, Webhook> channelWebhooks; // channelId -> Webhook
    private final Set<String> originalMessagesBeingProxied; // Track original messages being deleted during proxy

    public ProxyService() {
        // Create Gson with custom Instant adapter for Java 17+ module compatibility
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(Instant.class, new JsonSerializer<Instant>() {
                    @Override
                    public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
                        return new JsonPrimitive(src.toString());
                    }
                })
                .registerTypeAdapter(Instant.class, new JsonDeserializer<Instant>() {
                    @Override
                    public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                            throws JsonParseException {
                        return Instant.parse(json.getAsString());
                    }
                })
                .create();
        this.members = new ConcurrentHashMap<>();
        this.groups = new ConcurrentHashMap<>();
        this.userSettings = new ConcurrentHashMap<>();
        this.proxiedMessages = new ConcurrentHashMap<>();
        this.channelWebhooks = new ConcurrentHashMap<>();
        this.originalMessagesBeingProxied = ConcurrentHashMap.newKeySet();

        // Create data directory
        new File(PROXY_DATA_DIR).mkdirs();

        // Load existing data
        loadMembers();
        loadGroups();
        loadSettings();
        loadProxiedMessages();
    }

    // ========== Member Management ==========

    /**
     * Create a new proxy member
     * 
     * @param guildId Guild ID for guild-specific proxy, or null for global proxy
     */
    public CompletableFuture<String> createMember(String ownerId, String guildId, String name,
            String displayName, String avatarUrl,
            String prefix, String suffix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Generate unique member ID
                String memberId = generateMemberId(ownerId, guildId);

                // Check if member with this name already exists for this user in this scope
                if (getMemberByName(ownerId, guildId, name) != null) {
                    return "711"; // Member already exists
                }

                // Create member
                ProxyMember member = new ProxyMember(memberId, ownerId, guildId, name);
                if (displayName != null) {
                    member.setDisplayName(displayName);
                }
                if (avatarUrl != null) {
                    // Cache the avatar to prevent CDN link expiration
                    String cachedUrl = AvatarCacheManager.cacheAvatar(avatarUrl);
                    member.setAvatarUrl(cachedUrl);
                    member.setOriginalAvatarUrl(avatarUrl); // Keep original for webhooks
                }

                // Add proxy tag if provided
                if (prefix != null || suffix != null) {
                    member.addProxyTag(prefix != null ? prefix : "", suffix != null ? suffix : "");
                }

                // Save member
                members.put(memberId, member);
                saveMembers();

                return memberId;

            } catch (Exception e) {
                logger.error("Error creating proxy member: " + e.getMessage(), e);
                return "702"; // Data creation failed
            }
        });
    }

    /**
     * Edit an existing proxy member
     */
    public CompletableFuture<String> editMember(String memberId, String field, String value) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyMember member = members.get(memberId);
                if (member == null) {
                    return "710"; // Member not found
                }

                switch (field.toLowerCase()) {
                    case "name":
                        member.setName(value);
                        break;
                    case "displayname":
                    case "display":
                        member.setDisplayName(value);
                        break;
                    case "avatar":
                    case "avatarurl":
                        // Cache the avatar to prevent CDN link expiration
                        String cachedUrl = AvatarCacheManager.cacheAvatar(value);
                        member.setAvatarUrl(cachedUrl);
                        member.setOriginalAvatarUrl(value); // Keep original for webhooks
                        break;
                    case "pronouns":
                        member.setPronouns(value);
                        break;
                    case "description":
                    case "desc":
                        member.setDescription(value);
                        break;
                    case "color":
                    case "colour":
                        member.setColor(value);
                        break;
                    case "keepproxy":
                        member.setKeepProxy(Boolean.parseBoolean(value));
                        break;
                    default:
                        return "724"; // Invalid field
                }

                saveMembers();
                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error editing proxy member: " + e.getMessage(), e);
                return "701"; // Operation failed
            }
        });
    }

    /**
     * Delete a proxy member
     */
    public CompletableFuture<String> deleteMember(String memberId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyMember member = members.remove(memberId);
                if (member == null) {
                    return "710"; // Member not found
                }

                // Remove from any groups
                for (ProxyGroup group : groups.values()) {
                    if (group.getGuildId().equals(member.getGuildId()) &&
                            group.getOwnerId().equals(member.getOwnerId())) {
                        group.removeMember(memberId);
                    }
                }

                saveMembers();
                saveGroups();

                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error deleting proxy member: " + e.getMessage(), e);
                return "703"; // Data deletion failed
            }
        });
    }

    /**
     * Add a proxy tag to a member
     */
    public CompletableFuture<String> addProxyTag(String memberId, String prefix, String suffix) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyMember member = members.get(memberId);
                if (member == null) {
                    return "710"; // Member not found
                }

                if ((prefix == null || prefix.isEmpty()) && (suffix == null || suffix.isEmpty())) {
                    return "720"; // Invalid tag
                }

                member.addProxyTag(prefix != null ? prefix : "", suffix != null ? suffix : "");
                saveMembers();

                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error adding proxy tag: " + e.getMessage(), e);
                return "701"; // Operation failed
            }
        });
    }

    /**
     * Remove a proxy tag from a member
     */
    public CompletableFuture<String> removeProxyTag(String memberId, int tagIndex) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyMember member = members.get(memberId);
                if (member == null) {
                    return "710"; // Member not found
                }

                if (tagIndex < 0 || tagIndex >= member.getProxyTags().size()) {
                    return "721"; // Invalid tag index
                }

                member.removeProxyTag(tagIndex);
                saveMembers();

                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error removing proxy tag: " + e.getMessage(), e);
                return "701"; // Operation failed
            }
        });
    }

    // ========== Group Management ==========

    /**
     * Create a new proxy group
     */
    public CompletableFuture<String> createGroup(String ownerId, String guildId, String name, String description) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String groupId = generateGroupId(ownerId, guildId);

                ProxyGroup group = new ProxyGroup(groupId, ownerId, guildId, name);
                if (description != null) {
                    group.setDescription(description);
                }

                groups.put(groupId, group);
                saveGroups();

                return groupId;

            } catch (Exception e) {
                logger.error("Error creating proxy group: " + e.getMessage(), e);
                return "731"; // Group creation failed
            }
        });
    }

    /**
     * Add a member to a group
     */
    public CompletableFuture<String> addMemberToGroup(String groupId, String memberId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyGroup group = groups.get(groupId);
                if (group == null) {
                    return "730"; // Group not found
                }

                ProxyMember member = members.get(memberId);
                if (member == null) {
                    return "710"; // Member not found
                }

                group.addMember(memberId);
                member.setGroupId(groupId);

                saveGroups();
                saveMembers();

                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error adding member to group: " + e.getMessage(), e);
                return "701"; // Operation failed
            }
        });
    }

    /**
     * Remove a member from a group
     */
    public CompletableFuture<String> removeMemberFromGroup(String groupId, String memberId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyGroup group = groups.get(groupId);
                if (group == null) {
                    return "730"; // Group not found
                }

                ProxyMember member = members.get(memberId);
                if (member != null) {
                    member.setGroupId(null);
                }

                group.removeMember(memberId);

                saveGroups();
                saveMembers();

                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error removing member from group: " + e.getMessage(), e);
                return "701"; // Operation failed
            }
        });
    }

    // ========== Settings Management ==========

    /**
     * Get or create proxy settings for a user
     * 
     * @param guildId Guild ID, or null for DMs (uses "DM" as key)
     */
    public ProxySettings getSettings(String userId, String guildId) {
        String key = userId + ":" + (guildId != null ? guildId : "DM");
        return userSettings.computeIfAbsent(key, k -> new ProxySettings(userId, guildId));
    }

    /**
     * Update proxy settings
     * 
     * @param guildId Guild ID, or null for DMs
     */
    public CompletableFuture<String> updateSettings(String userId, String guildId, ProxySettings settings) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = userId + ":" + (guildId != null ? guildId : "DM");
                userSettings.put(key, settings);
                saveSettings();
                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error updating proxy settings: " + e.getMessage(), e);
                return "701"; // Operation failed
            }
        });
    }

    /**
     * Switch to a specific member (for autoproxy)
     */
    public CompletableFuture<String> switchMember(String userId, String guildId, String memberId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ProxyMember member = members.get(memberId);
                if (member == null) {
                    return "710"; // Member not found
                }

                ProxySettings settings = getSettings(userId, guildId);
                settings.setLastProxiedMemberId(memberId);
                settings.setLastSwitchTime(Instant.now());

                saveSettings();
                return "SUCCESS";

            } catch (Exception e) {
                logger.error("Error switching member: " + e.getMessage(), e);
                return "P16"; // Switch failed
            }
        });
    }

    // ========== Message Proxying ==========

    /**
     * Attempt to proxy a message
     * 
     * @return ProxyResult containing success status and proxy message ID if
     *         successful
     */
    public CompletableFuture<ProxyResult> proxyMessage(Message originalMessage) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                User author = originalMessage.getAuthor();
                if (author.isBot()) {
                    return new ProxyResult(false, null, null);
                }

                // Get guild ID (null for DMs)
                String guildId = originalMessage.isFromGuild() ? originalMessage.getGuild().getId() : null;
                ProxySettings settings = getSettings(author.getId(), guildId);

                if (!settings.isProxyEnabled()) {
                    return new ProxyResult(false, null, null);
                }

                String content = originalMessage.getContentRaw();
                if (content.isEmpty()) {
                    return new ProxyResult(false, null, null);
                }

                // Try to match proxy tags
                ProxyMember matchedMember = null;
                String proxiedContent = null;

                // Get all members for this user (includes global and guild-specific)
                List<ProxyMember> userMembers = getUserMembers(author.getId(), guildId);

                for (ProxyMember member : userMembers) {
                    ProxyMember.ProxyMatch match = member.matchesProxyTags(content);
                    if (match != null) {
                        matchedMember = member;
                        proxiedContent = member.isKeepProxy() ? content : match.getContent();
                        break;
                    }
                }

                // If no match, check autoproxy
                if (matchedMember == null && settings.getAutoproxyMode() != ProxySettings.AutoproxyMode.OFF) {
                    matchedMember = getAutoproxyMember(settings, userMembers);
                    proxiedContent = content;
                }

                if (matchedMember == null) {
                    return new ProxyResult(false, null, null);
                }

                // DM proxy support: Only allow global proxies in DMs
                if (!originalMessage.isFromGuild()) {
                    if (matchedMember.getGuildId() != null) {
                        logger.debug("Cannot use guild-specific proxy in DMs");
                        return new ProxyResult(false, null, null);
                    }
                    // Note: DMs don't support webhooks, so we'd need to use embeds or just edit
                    // For now, skip DM proxying (webhooks are guild-only)
                    logger.debug("DM proxying not yet fully implemented");
                    return new ProxyResult(false, null, null);
                }

                // Make final references for lambda
                final ProxyMember finalMatchedMember = matchedMember;
                final String finalProxiedContent = proxiedContent;
                final ProxySettings finalSettings = settings;

                // Get or create webhook for this channel
                TextChannel channel = originalMessage.getChannel().asTextChannel();
                Webhook webhook = getOrCreateWebhook(channel).join();

                if (webhook == null) {
                    logger.warn("Failed to get webhook for channel: " + channel.getId());
                    return new ProxyResult(false, null, null);
                }

                // Send proxied message
                // For webhooks, prefer original URL (works with Discord), fallback to no avatar
                // if only cached
                String avatarUrl = null;
                if (finalMatchedMember.getOriginalAvatarUrl() != null
                        && !finalMatchedMember.getOriginalAvatarUrl().isEmpty()) {
                    // Use original CDN URL for webhooks (while it's still valid)
                    avatarUrl = finalMatchedMember.getOriginalAvatarUrl();
                } else if (finalMatchedMember.getAvatarUrl() != null
                        && !AvatarCacheManager.isCachedAvatar(finalMatchedMember.getAvatarUrl())) {
                    // If avatarUrl is not a cached file, use it directly
                    avatarUrl = finalMatchedMember.getAvatarUrl();
                }
                // If avatarUrl is still null or a file:// URL, webhook will use default

                // Handle attachments
                String messageContent = finalProxiedContent;

                // If this is a reply, add a reference link at the start
                Message referencedMessage = originalMessage.getReferencedMessage();
                if (referencedMessage != null) {
                    String replyRef = String.format("%s [Replying to %s](%s)\n",
                            CustomEmojis.LEFT,
                            referencedMessage.getAuthor().getName(),
                            referencedMessage.getJumpUrl());
                    messageContent = replyRef + messageContent;
                }

                if (finalSettings.isShowProxyIndicator()) {
                    messageContent = messageContent + " `[proxied]`";
                }

                // Track this message as being proxied (for AutoLogListener)
                originalMessagesBeingProxied.add(originalMessage.getId());

                // Note: Webhooks cannot preserve reply references due to Discord API
                // limitations
                // The reply context will be lost when proxying

                // Send webhook message with error handling
                webhook.sendMessage(messageContent)
                        .setUsername(finalMatchedMember.getDisplayName())
                        .setAvatarUrl(avatarUrl)
                        .queue(webhookMessage -> {
                            // Store proxy data
                            ProxiedMessageData proxyData = new ProxiedMessageData(
                                    webhookMessage.getId(),
                                    originalMessage.getId(),
                                    author.getId(),
                                    finalMatchedMember.getMemberId(),
                                    channel.getId(),
                                    guildId,
                                    Instant.now());

                            proxiedMessages.put(webhookMessage.getId(), proxyData);
                            saveProxiedMessages();

                            // Delete original message with error handling
                            originalMessage.delete().queue(
                                    success -> {
                                        logger.debug("Proxied message for member: " + finalMatchedMember.getName());
                                        // Remove from tracking set after successful deletion
                                        originalMessagesBeingProxied.remove(originalMessage.getId());
                                    },
                                    failure -> {
                                        logger.warn("Failed to delete original message: " + failure.getMessage());
                                        // Still remove from tracking set even if deletion failed
                                        originalMessagesBeingProxied.remove(originalMessage.getId());
                                    });

                            // Update last proxied member
                            finalSettings.setLastProxiedMemberId(finalMatchedMember.getMemberId());
                            saveSettings();
                        }, failure -> {
                            // Handle webhook send failure
                            logger.error("Failed to send proxied message: " + failure.getMessage());
                            // Remove from tracking set if webhook send failed
                            originalMessagesBeingProxied.remove(originalMessage.getId());
                        });

                return new ProxyResult(true, finalMatchedMember.getMemberId(), null);

            } catch (Exception e) {
                logger.error("Error proxying message: " + e.getMessage(), e);
                return new ProxyResult(false, null, "P17"); // Proxy failed
            }
        });
    }

    /**
     * Get or create a webhook for a channel
     */
    private CompletableFuture<Webhook> getOrCreateWebhook(TextChannel channel) {
        // Check cache first
        if (channelWebhooks.containsKey(channel.getId())) {
            return CompletableFuture.completedFuture(channelWebhooks.get(channel.getId()));
        }

        // Use async API calls instead of blocking complete()
        return channel.retrieveWebhooks().submit().thenCompose(webhooks -> {
            try {
                Webhook proxyWebhook = null;

                for (Webhook webhook : webhooks) {
                    if (webhook.getName().equals("ServerBot Proxy")) {
                        proxyWebhook = webhook;
                        break;
                    }
                }

                // Create if doesn't exist
                if (proxyWebhook == null) {
                    return channel.createWebhook("ServerBot Proxy").submit().thenApply(newWebhook -> {
                        channelWebhooks.put(channel.getId(), newWebhook);
                        return newWebhook;
                    });
                } else {
                    channelWebhooks.put(channel.getId(), proxyWebhook);
                    return CompletableFuture.completedFuture(proxyWebhook);
                }

            } catch (Exception e) {
                logger.error("Error getting/creating webhook: " + e.getMessage(), e);
                return CompletableFuture.completedFuture(null);
            }
        }).exceptionally(ex -> {
            logger.error("Failed to retrieve webhooks: " + ex.getMessage(), ex);
            return null;
        });
    }

    /**
     * Get the member to use for autoproxy
     */
    private ProxyMember getAutoproxyMember(ProxySettings settings, List<ProxyMember> userMembers) {
        switch (settings.getAutoproxyMode()) {
            case FRONT:
            case LATCH:
            case STICKY:
                String lastMemberId = settings.getLastProxiedMemberId();
                if (lastMemberId != null) {
                    return members.get(lastMemberId);
                }
                break;

            case MEMBER:
                String autoproxyMemberId = settings.getAutoproxyMemberId();
                if (autoproxyMemberId != null) {
                    return members.get(autoproxyMemberId);
                }
                break;

            default:
                break;
        }

        return null;
    }

    // ========== Query Methods ==========

    /**
     * Get a member by ID
     */
    public ProxyMember getMember(String memberId) {
        return members.get(memberId);
    }

    /**
     * Get a member by name (checks global proxies first, then guild-specific)
     * 
     * @param guildId Current guild ID, or null for DMs
     */
    public ProxyMember getMemberByName(String ownerId, String guildId, String name) {
        // First check for guild-specific match
        if (guildId != null) {
            ProxyMember guildSpecific = members.values().stream()
                    .filter(m -> m.getOwnerId().equals(ownerId) &&
                            guildId.equals(m.getGuildId()) &&
                            m.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElse(null);
            if (guildSpecific != null) {
                return guildSpecific;
            }
        }

        // Then check for global match
        return members.values().stream()
                .filter(m -> m.getOwnerId().equals(ownerId) &&
                        m.getGuildId() == null &&
                        m.getName().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Get all members owned by a user across all guilds and global scope.
     * Used for data deletion requests.
     */
    public List<ProxyMember> getAllMembersForOwner(String ownerId) {
        return members.values().stream()
                .filter(m -> m.getOwnerId().equals(ownerId))
                .collect(Collectors.toList());
    }

    /**
     * Get all members for a user (includes both global and guild-specific)
     * 
     * @param guildId Current guild ID, or null for DMs
     */
    public List<ProxyMember> getUserMembers(String ownerId, String guildId) {
        return members.values().stream()
                .filter(m -> m.getOwnerId().equals(ownerId) &&
                        (m.getGuildId() == null || // Global proxies work everywhere
                                (guildId != null && guildId.equals(m.getGuildId())))) // Guild-specific
                .sorted(Comparator.comparing(ProxyMember::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get a group by ID
     */
    public ProxyGroup getGroup(String groupId) {
        return groups.get(groupId);
    }

    /**
     * Get all groups for a user in a guild
     */
    public List<ProxyGroup> getUserGroups(String ownerId, String guildId) {
        return groups.values().stream()
                .filter(g -> g.getOwnerId().equals(ownerId) && g.getGuildId().equals(guildId))
                .sorted(Comparator.comparing(ProxyGroup::getName))
                .collect(Collectors.toList());
    }

    /**
     * Get proxied message data
     */
    public ProxiedMessageData getProxiedMessageData(String messageId) {
        return proxiedMessages.get(messageId);
    }

    /**
     * Check if an original message is currently being proxied (and will be deleted)
     * This is used to prevent logging the deletion as a user action
     */
    public boolean isOriginalMessageBeingProxied(String originalMessageId) {
        return originalMessagesBeingProxied.contains(originalMessageId);
    }

    // ========== Helper Methods ==========

    private String generateMemberId(String ownerId, String guildId) {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private String generateGroupId(String ownerId, String guildId) {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    // ========== Data Persistence ==========

    private void loadMembers() {
        try {
            File file = new File(PROXY_DATA_DIR + MEMBERS_FILE);
            if (file.exists()) {
                // Check if file is empty or only contains whitespace
                if (file.length() <= 2) {
                    logger.warn("Proxy members file is empty or corrupted, skipping load");
                    return;
                }

                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, ProxyMember>>() {
                    }.getType();
                    Map<String, ProxyMember> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        members.putAll(loaded);
                        logger.info("Loaded {} proxy members", loaded.size());
                    }
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            logger.error("Corrupted JSON in proxy members file, starting with empty data: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to load proxy members: " + e.getMessage(), e);
        }
    }

    private void saveMembers() {
        try {
            File file = new File(PROXY_DATA_DIR + MEMBERS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(members, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save proxy members: " + e.getMessage(), e);
        }
    }

    private void loadGroups() {
        try {
            File file = new File(PROXY_DATA_DIR + GROUPS_FILE);
            if (file.exists()) {
                // Check if file is empty or only contains whitespace
                if (file.length() <= 2) {
                    logger.warn("Proxy groups file is empty or corrupted, skipping load");
                    return;
                }

                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, ProxyGroup>>() {
                    }.getType();
                    Map<String, ProxyGroup> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        groups.putAll(loaded);
                        logger.info("Loaded {} proxy groups", loaded.size());
                    }
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            logger.error("Corrupted JSON in proxy groups file, starting with empty data: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to load proxy groups: " + e.getMessage(), e);
        }
    }

    private void saveGroups() {
        try {
            File file = new File(PROXY_DATA_DIR + GROUPS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(groups, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save proxy groups: " + e.getMessage(), e);
        }
    }

    private void loadSettings() {
        try {
            File file = new File(PROXY_DATA_DIR + SETTINGS_FILE);
            if (file.exists()) {
                // Check if file is empty or only contains whitespace
                if (file.length() <= 2) {
                    logger.warn("Proxy settings file is empty or corrupted, skipping load");
                    return;
                }

                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, ProxySettings>>() {
                    }.getType();
                    Map<String, ProxySettings> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        userSettings.putAll(loaded);
                        logger.info("Loaded {} proxy settings", loaded.size());
                    }
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            logger.error("Corrupted JSON in proxy settings file, starting with empty data: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to load proxy settings: " + e.getMessage(), e);
        }
    }

    private void saveSettings() {
        try {
            File file = new File(PROXY_DATA_DIR + SETTINGS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(userSettings, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save proxy settings: " + e.getMessage(), e);
        }
    }

    private void loadProxiedMessages() {
        try {
            File file = new File(PROXY_DATA_DIR + PROXIED_MESSAGES_FILE);
            if (file.exists()) {
                // Check if file is empty or only contains whitespace
                if (file.length() <= 2) {
                    logger.warn("Proxied messages file is empty or corrupted, skipping load");
                    return;
                }

                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, ProxiedMessageData>>() {
                    }.getType();
                    Map<String, ProxiedMessageData> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        proxiedMessages.putAll(loaded);
                        logger.info("Loaded {} proxied messages", loaded.size());
                    }
                }
            }
        } catch (com.google.gson.JsonSyntaxException e) {
            logger.error("Corrupted JSON in proxied messages file, starting with empty data: " + e.getMessage());
        } catch (IOException e) {
            logger.error("Failed to load proxied messages: " + e.getMessage(), e);
        }
    }

    private void saveProxiedMessages() {
        try {
            File file = new File(PROXY_DATA_DIR + PROXIED_MESSAGES_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(proxiedMessages, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save proxied messages: " + e.getMessage(), e);
        }
    }

    // ========== Data Classes ==========

    /**
     * Result of a proxy attempt
     */
    public static class ProxyResult {
        private final boolean success;
        private final String memberId;
        private final String errorCode;

        public ProxyResult(boolean success, String memberId, String errorCode) {
            this.success = success;
            this.memberId = memberId;
            this.errorCode = errorCode;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMemberId() {
            return memberId;
        }

        public String getErrorCode() {
            return errorCode;
        }
    }

    /**
     * Data about a proxied message
     */
    public static class ProxiedMessageData {
        private String proxyMessageId;
        private String originalMessageId;
        private String authorId;
        private String memberId;
        private String channelId;
        private String guildId;
        private Instant timestamp;

        public ProxiedMessageData(String proxyMessageId, String originalMessageId, String authorId,
                String memberId, String channelId, String guildId, Instant timestamp) {
            this.proxyMessageId = proxyMessageId;
            this.originalMessageId = originalMessageId;
            this.authorId = authorId;
            this.memberId = memberId;
            this.channelId = channelId;
            this.guildId = guildId;
            this.timestamp = timestamp;
        }

        // Getters
        public String getProxyMessageId() {
            return proxyMessageId;
        }

        public String getOriginalMessageId() {
            return originalMessageId;
        }

        public String getAuthorId() {
            return authorId;
        }

        public String getMemberId() {
            return memberId;
        }

        public String getChannelId() {
            return channelId;
        }

        public String getGuildId() {
            return guildId;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
