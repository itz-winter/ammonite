package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.serverbot.models.GlobalChatChannel;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;

/**
 * Service for managing cross-server global chat channels.
 * Handles CRUD, webhook management, message relay, and moderation.
 */
public class GlobalChatService {

    private static final Logger logger = LoggerFactory.getLogger(GlobalChatService.class);
    private static final String DATA_DIR = "data/globalchat/";
    private static final String CHANNELS_FILE = "global_channels.json";
    private static final String WEBHOOK_NAME = "ServerBot GlobalChat";
    private static final String ID_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final String KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final Gson gson;
    private final Map<String, GlobalChatChannel> channels;                  // channelId -> channel
    private final Map<String, String> textChannelToGlobal;                  // textChannelId -> globalChannelId (reverse index)
    private final Map<String, Webhook> webhookCache;                        // textChannelId -> Webhook
    private final SecureRandom random;

    // Manage-panel conversation state: userId -> pending action context
    private final Map<String, ManagePanelState> managePanelStates;

    // Message mapping for reaction/reply relay: sourceMessageId -> (targetTextChannelId -> relayedMessageId)
    // Also stores reverse: relayedMessageId -> sourceMessageId
    private final Map<String, Map<String, String>> messageMapping;
    private final Map<String, String> reverseMessageMapping;
    // Track which source text channel a source message came from
    private final Map<String, String> sourceMessageChannel;
    private static final int MAX_MESSAGE_MAP_SIZE = 5000;

    // Track message IDs that the bot is currently deleting (prevents cascading delete loops)
    private final Set<String> pendingDeletes;

    // Track channels where we've already sent a permission error (to avoid spamming)
    private final Map<String, Long> permissionWarningsSent;  // textChannelId -> timestamp
    private static final long PERMISSION_WARNING_COOLDOWN_MS = 300_000; // 5 minutes

    public GlobalChatService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(java.time.Instant.class, new com.serverbot.utils.InstantTypeAdapter())
                .create();
        this.channels = new ConcurrentHashMap<>();
        this.textChannelToGlobal = new ConcurrentHashMap<>();
        this.webhookCache = new ConcurrentHashMap<>();
        this.managePanelStates = new ConcurrentHashMap<>();
        this.messageMapping = new ConcurrentHashMap<>();
        this.reverseMessageMapping = new ConcurrentHashMap<>();
        this.sourceMessageChannel = new ConcurrentHashMap<>();
        this.pendingDeletes = ConcurrentHashMap.newKeySet();
        this.permissionWarningsSent = new ConcurrentHashMap<>();
        this.random = new SecureRandom();

        new File(DATA_DIR).mkdirs();
        loadChannels();
    }

    // ── Persistence ──────────────────────────────────────────────────

    private void loadChannels() {
        File file = new File(DATA_DIR, CHANNELS_FILE);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, GlobalChatChannel>>(){}.getType();
            Map<String, GlobalChatChannel> data = gson.fromJson(reader, type);
            if (data != null) {
                channels.putAll(data);
                rebuildReverseIndex();
            }
            logger.info("Loaded {} global chat channels", channels.size());
        } catch (IOException e) {
            logger.error("Failed to load global chat channels", e);
        }
    }

    public void saveChannels() {
        try (FileWriter writer = new FileWriter(new File(DATA_DIR, CHANNELS_FILE))) {
            gson.toJson(channels, writer);
        } catch (IOException e) {
            logger.error("Failed to save global chat channels", e);
        }
    }

    private void rebuildReverseIndex() {
        textChannelToGlobal.clear();
        for (GlobalChatChannel ch : channels.values()) {
            for (Map.Entry<String, String> entry : ch.getLinkedChannels().entrySet()) {
                textChannelToGlobal.put(entry.getValue(), ch.getChannelId());
            }
        }
    }

    // ── ID / Key generation ──────────────────────────────────────────

    public String generateChannelId() {
        String id;
        do {
            StringBuilder sb = new StringBuilder("gc-");
            for (int i = 0; i < 8; i++) sb.append(ID_CHARS.charAt(random.nextInt(ID_CHARS.length())));
            id = sb.toString();
        } while (channels.containsKey(id));
        return id;
    }

    public String generateKey() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(KEY_CHARS.charAt(random.nextInt(KEY_CHARS.length())));
        return sb.toString();
    }

    // ── CRUD ─────────────────────────────────────────────────────────

    public GlobalChatChannel createChannel(String name, String description, String visibility,
                                           boolean keyRequired, String key, String ownerId) {
        return createChannel(name, description, visibility, keyRequired, key, ownerId, null, null, null, true);
    }

    public GlobalChatChannel createChannel(String name, String description, String visibility,
                                           boolean keyRequired, String key, String ownerId,
                                           String messagePrefix, String messageSuffix) {
        return createChannel(name, description, visibility, keyRequired, key, ownerId, messagePrefix, messageSuffix, null, true);
    }

    public GlobalChatChannel createChannel(String name, String description, String visibility,
                                           boolean keyRequired, String key, String ownerId,
                                           String messagePrefix, String messageSuffix,
                                           String nameFormat, boolean webhookEnabled) {
        String id = generateChannelId();
        if (keyRequired && (key == null || key.isEmpty())) {
            key = generateKey();
        }
        GlobalChatChannel channel = new GlobalChatChannel(id, name, description, visibility, keyRequired, key, ownerId);
        if (messagePrefix != null) channel.setMessagePrefix(messagePrefix);
        if (messageSuffix != null) channel.setMessageSuffix(messageSuffix);
        if (nameFormat != null) channel.setNameFormat(nameFormat);
        channel.setWebhookEnabled(webhookEnabled);
        channels.put(id, channel);
        saveChannels();
        return channel;
    }

    public GlobalChatChannel getChannel(String channelId) {
        return channels.get(channelId);
    }

    public boolean deleteChannel(String channelId) {
        GlobalChatChannel ch = channels.remove(channelId);
        if (ch != null) {
            // Clean up reverse index
            for (String textChId : ch.getLinkedChannels().values()) {
                textChannelToGlobal.remove(textChId);
                webhookCache.remove(textChId);
            }
            saveChannels();
            return true;
        }
        return false;
    }

    public List<GlobalChatChannel> getChannelsByOwner(String userId) {
        return channels.values().stream()
                .filter(c -> c.isOwner(userId) || c.isCoOwner(userId))
                .collect(Collectors.toList());
    }

    public List<GlobalChatChannel> getPublicChannels() {
        return channels.values().stream()
                .filter(c -> "public".equalsIgnoreCase(c.getVisibility()))
                .collect(Collectors.toList());
    }

    // ── Linking ──────────────────────────────────────────────────────

    /**
     * Link a guild's text channel to a global chat channel.
     * Returns null on success, or an error message string on failure.
     */
    public String linkChannel(String globalChannelId, String guildId, String textChannelId, String providedKey) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        if (gc.isServerBanned(guildId)) return "This server is banned from this global chat channel.";
        if (gc.isGuildLinked(guildId)) return "This server already has a channel linked to this global chat.";

        // Key validation
        if (gc.isKeyRequired()) {
            if (providedKey == null || !providedKey.equals(gc.getKey())) {
                return "Invalid or missing key for this global chat channel.";
            }
        }

        // Check if the text channel is already linked elsewhere
        if (textChannelToGlobal.containsKey(textChannelId)) {
            return "This text channel is already linked to another global chat channel.";
        }

        gc.linkChannel(guildId, textChannelId);
        textChannelToGlobal.put(textChannelId, globalChannelId);
        saveChannels();
        return null; // success
    }

    public String unlinkChannel(String guildId, String textChannelId) {
        String globalId = textChannelToGlobal.get(textChannelId);
        if (globalId == null) return "This channel is not linked to any global chat channel.";

        GlobalChatChannel gc = channels.get(globalId);
        if (gc == null) {
            textChannelToGlobal.remove(textChannelId);
            return "Global chat channel data not found. Link removed.";
        }

        gc.unlinkChannel(guildId);
        textChannelToGlobal.remove(textChannelId);
        webhookCache.remove(textChannelId);
        saveChannels();
        return null;
    }

    /**
     * Owner/co-owner unlinking any guild from their global channel.
     */
    public String unlinkGuildFromChannel(String globalChannelId, String guildId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        if (!gc.isGuildLinked(guildId)) return "That server is not linked to this channel.";

        String textChId = gc.getLinkedTextChannel(guildId);
        gc.unlinkChannel(guildId);
        if (textChId != null) {
            textChannelToGlobal.remove(textChId);
            webhookCache.remove(textChId);
        }
        saveChannels();
        return null;
    }

    // ── Reverse lookup ───────────────────────────────────────────────

    public String getGlobalChannelIdByTextChannel(String textChannelId) {
        return textChannelToGlobal.get(textChannelId);
    }

    public GlobalChatChannel getGlobalChannelByTextChannel(String textChannelId) {
        String id = textChannelToGlobal.get(textChannelId);
        return id == null ? null : channels.get(id);
    }

    // ── Webhook management ───────────────────────────────────────────

    public CompletableFuture<Webhook> getOrCreateWebhook(TextChannel channel) {
        if (webhookCache.containsKey(channel.getId())) {
            return CompletableFuture.completedFuture(webhookCache.get(channel.getId()));
        }

        // Check required permissions before attempting — JDA throws synchronously if missing
        net.dv8tion.jda.api.entities.Member self = channel.getGuild().getSelfMember();
        List<Permission> missing = new ArrayList<>();
        if (!self.hasPermission(channel, Permission.MANAGE_WEBHOOKS)) missing.add(Permission.MANAGE_WEBHOOKS);
        if (!self.hasPermission(channel, Permission.VIEW_CHANNEL))    missing.add(Permission.VIEW_CHANNEL);

        if (!missing.isEmpty()) {
            String missingStr = missing.stream().map(Permission::getName).collect(Collectors.joining(", "));
            logger.warn("Missing permissions [{}] in channel {} (guild {})", missingStr, channel.getId(), channel.getGuild().getId());

            // Rate-limit warnings to once per 5 minutes per channel
            long now = System.currentTimeMillis();
            Long lastWarning = permissionWarningsSent.get(channel.getId());
            if (lastWarning == null || (now - lastWarning) >= PERMISSION_WARNING_COOLDOWN_MS) {
                permissionWarningsSent.put(channel.getId(), now);

                net.dv8tion.jda.api.entities.MessageEmbed errorEmbed = EmbedUtils.createErrorEmbed(
                        "Global Chat — Missing Permissions",
                        "I can't relay messages to this channel because I'm missing the following permission(s):\n\n**"
                                + missingStr + "**\n\nPlease grant these permissions to resume global chat.");

                if (self.hasPermission(channel, Permission.MESSAGE_SEND)) {
                    // Can still send in the channel — post the error there (no auto-delete)
                    channel.sendMessageEmbeds(errorEmbed).queue(msg -> {}, err -> {});
                } else {
                    // Can't send in the channel at all — DM the guild owner (respects DM toggle)
                    Guild guild = channel.getGuild();
                    guild.retrieveOwner().queue(owner -> {
                        if (owner != null) {
                            com.serverbot.utils.DmUtils.sendDm(guild, owner.getUser(), errorEmbed);
                        }
                    }, err -> {});
                }
            }
            return CompletableFuture.completedFuture(null);
        }

        try {
            return channel.retrieveWebhooks().submit().thenCompose(webhooks -> {
                for (Webhook wh : webhooks) {
                    if (WEBHOOK_NAME.equals(wh.getName())) {
                        webhookCache.put(channel.getId(), wh);
                        return CompletableFuture.completedFuture(wh);
                    }
                }
                return channel.createWebhook(WEBHOOK_NAME).submit().thenApply(wh -> {
                    webhookCache.put(channel.getId(), wh);
                    return wh;
                });
            }).exceptionally(ex -> {
                logger.error("Failed to get/create webhook for channel {}: {}", channel.getId(), ex.getMessage());
                return null;
            });
        } catch (Exception ex) {
            logger.error("Failed to get/create webhook for channel {}: {}", channel.getId(), ex.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    // ── Message relay ────────────────────────────────────────────────

    /**
     * Relay a message from one linked channel to all other linked channels of the same global chat.
     */
    public void relayMessage(String sourceTextChannelId, String authorName, String authorAvatarUrl,
                             String content, JDA jda) {
        relayMessage(sourceTextChannelId, authorName, authorAvatarUrl, content, jda, null, null, null, null, null, null, null);
    }

    /**
     * Relay a message with optional reply context.
     * @param replyContent        Short quoted text from the message being replied to (null if not a reply)
     * @param replyAuthor         Display name of the user being replied to (null if not a reply)
     * @param sourceMessageId     The ID of the original message being relayed, for mapping reactions/replies
     * @param username            The user's actual Discord username (e.g. "kelpy")
     * @param displayName         The user's global display name (not server nickname), null falls back to username
     * @param pronouns            The user's pronoun string (e.g. "they/them"), null or empty if none
     * @param referencedMessageId The ID of the message being replied to (for actual Discord reply), null if not a reply
     */
    public void relayMessage(String sourceTextChannelId, String authorName, String authorAvatarUrl,
                             String content, JDA jda,
                             String replyContent, String replyAuthor, String sourceMessageId,
                             String username, String displayName, String pronouns,
                             String referencedMessageId) {
        String globalId = textChannelToGlobal.get(sourceTextChannelId);
        if (globalId == null) return;

        GlobalChatChannel gc = channels.get(globalId);
        if (gc == null) return;

        // Find source guild
        String sourceGuildId = null;
        for (Map.Entry<String, String> entry : gc.getLinkedChannels().entrySet()) {
            if (entry.getValue().equals(sourceTextChannelId)) {
                sourceGuildId = entry.getKey();
                break;
            }
        }

        // Check if source server is muted
        if (sourceGuildId != null && gc.isServerMuted(sourceGuildId)) {
            return; // silently drop
        }

        // Build display name using custom prefix/suffix or defaults
        // null = use default, empty string = intentionally empty (set via "{}")
        String prefix = gc.getMessagePrefix() != null ? gc.getMessagePrefix() : "[GC]";
        String suffix = gc.getMessageSuffix() != null ? gc.getMessageSuffix() : "• {server}";

        // Resolve placeholders in both prefix and suffix
        String sourceGuildName = null;
        if (sourceGuildId != null) {
            Guild sourceGuild = jda.getGuildById(sourceGuildId);
            if (sourceGuild != null) sourceGuildName = sourceGuild.getName();
        }
        String safeServer = sourceGuildName != null ? sourceGuildName : "Unknown";
        String safeUsername = username != null ? username : authorName;
        String safeDisplayName = displayName != null ? displayName : safeUsername;
        String safePronouns = pronouns != null && !pronouns.isEmpty() ? pronouns : "";

        // Build display name
        // If nameFormat is set it overrides prefix+authorName+suffix entirely.
        // Otherwise the traditional prefix + authorName + suffix assembly is used.
        String webhookDisplayName;
        if (gc.getNameFormat() != null) {
            webhookDisplayName = resolvePlaceholders(gc.getNameFormat(), authorName, safeServer, safeUsername, safeDisplayName, safePronouns);
        } else {
            String resolvedPrefix = resolvePlaceholders(prefix, authorName, safeServer, safeUsername, safeDisplayName, safePronouns);
            String resolvedSuffix = resolvePlaceholders(suffix, authorName, safeServer, safeUsername, safeDisplayName, safePronouns);

            // Build webhook display name, omitting empty prefix/suffix without extra spaces
            StringBuilder displayBuilder = new StringBuilder();
            if (!resolvedPrefix.isEmpty()) {
                displayBuilder.append(resolvedPrefix).append(" ");
            }
            displayBuilder.append(authorName);
            if (!resolvedSuffix.isEmpty()) {
                displayBuilder.append(" ").append(resolvedSuffix);
            }
            webhookDisplayName = displayBuilder.toString();
        }
        // Discord webhook usernames max 80 chars
        if (webhookDisplayName.length() > 80) {
            webhookDisplayName = webhookDisplayName.substring(0, 80);
        }

        // Resolve the referenced message to its source ID for cross-channel reply mapping
        // If the user replied to a relayed webhook message, find the original source message ID
        // If they replied to a source message, use it directly
        String resolvedReplySourceId = null;
        if (referencedMessageId != null) {
            if (messageMapping.containsKey(referencedMessageId)) {
                resolvedReplySourceId = referencedMessageId;
            } else if (reverseMessageMapping.containsKey(referencedMessageId)) {
                resolvedReplySourceId = reverseMessageMapping.get(referencedMessageId);
            }
        }
        final String replySourceId = resolvedReplySourceId;

        // Prepare message mapping for this source message
        Map<String, String> targetMap = null;
        if (sourceMessageId != null) {
            targetMap = new ConcurrentHashMap<>();
            messageMapping.put(sourceMessageId, targetMap);
            sourceMessageChannel.put(sourceMessageId, sourceTextChannelId);
            // Evict old entries if cache is too large
            if (messageMapping.size() > MAX_MESSAGE_MAP_SIZE) {
                evictOldestMappings();
            }
        }
        final Map<String, String> finalTargetMap = targetMap;
        final String finalDisplayName = webhookDisplayName;
        final String baseContent = content; // original content without reply prefix

        for (Map.Entry<String, String> entry : gc.getLinkedChannels().entrySet()) {
            String guildId = entry.getKey();
            String textChId = entry.getValue();

            // Don't relay back to source
            if (textChId.equals(sourceTextChannelId)) continue;

            // Skip muted servers (they can't receive either per spec)
            if (gc.isServerMuted(guildId)) continue;

            Guild guild = jda.getGuildById(guildId);
            if (guild == null) continue;
            TextChannel target = guild.getTextChannelById(textChId);
            if (target == null) continue;

            // Build per-channel reply content with a jump link to the relayed copy in this channel
            String perChannelContent;
            if (replyContent != null && replyAuthor != null) {
                String preview = replyContent.length() > 100 ? replyContent.substring(0, 100) + "…" : replyContent;
                // Try to find the relayed copy of the replied-to message in this target channel
                String replyMsgId = null;
                if (replySourceId != null) {
                    Map<String, String> replyTargets = messageMapping.get(replySourceId);
                    if (replyTargets != null) {
                        replyMsgId = replyTargets.get(textChId);
                    }
                    // If the reply source itself was sent in this target channel, reference it directly
                    String replySrcChannel = sourceMessageChannel.get(replySourceId);
                    if (replyMsgId == null && textChId.equals(replySrcChannel)) {
                        replyMsgId = replySourceId;
                    }
                }
                if (replyMsgId != null) {
                    // Include a jump link so users can click to the replied-to message
                    String jumpLink = "https://discord.com/channels/" + guildId + "/" + textChId + "/" + replyMsgId;
                    perChannelContent = "> [**↩ " + replyAuthor + ":**](" + jumpLink + ") " + preview + "\n" + baseContent;
                } else {
                    perChannelContent = "> **↩ " + replyAuthor + ":** " + preview + "\n" + baseContent;
                }
            } else {
                perChannelContent = baseContent;
            }
            final String messageContent = perChannelContent;

            final String targetTextChId = textChId;
            if (gc.isWebhookEnabled()) {
                getOrCreateWebhook(target).thenAccept(webhook -> {
                    if (webhook == null) return;
                    webhook.sendMessage(messageContent)
                            .setUsername(finalDisplayName)
                            .setAvatarUrl(authorAvatarUrl)
                            .queue(
                                    sentMsg -> {
                                        // Store the mapping: source message -> relayed message in this channel
                                        if (finalTargetMap != null && sourceMessageId != null) {
                                            finalTargetMap.put(targetTextChId, sentMsg.getId());
                                            reverseMessageMapping.put(sentMsg.getId(), sourceMessageId);
                                        }
                                    },
                                    err -> {
                                        logger.warn("Failed to relay message to {}: {}", targetTextChId, err.getMessage());
                                        // Evict stale webhook so the next message triggers re-creation
                                        webhookCache.remove(targetTextChId);
                                    }
                            );
                });
            } else {
                // Webhook disabled — send as the bot itself
                String botContent = "**" + finalDisplayName + ":** " + messageContent;
                target.sendMessage(botContent).queue(
                        sentMsg -> {
                            if (finalTargetMap != null && sourceMessageId != null) {
                                finalTargetMap.put(targetTextChId, sentMsg.getId());
                                reverseMessageMapping.put(sentMsg.getId(), sourceMessageId);
                            }
                        },
                        err -> logger.warn("Failed to relay message (text mode) to {}: {}", targetTextChId, err.getMessage())
                );
            }
        }
    }

    /**
     * Delete all relayed copies of a message across linked channels.
     * Handles both source messages (original sender deleted) and relayed messages
     * (a webhook copy was deleted — propagate to all others including source).
     * Uses webhooks to delete relayed copies (they own those messages) and
     * tracks pending deletes to prevent cascading delete event loops.
     */
    public void deleteRelayedMessages(String deletedMessageId, String deletedInChannelId, JDA jda) {
        String sourceMessageId;
        boolean deletedWasSource;

        if (messageMapping.containsKey(deletedMessageId)) {
            // The deleted message is the source
            sourceMessageId = deletedMessageId;
            deletedWasSource = true;
        } else if (reverseMessageMapping.containsKey(deletedMessageId)) {
            // The deleted message is a relayed copy
            sourceMessageId = reverseMessageMapping.get(deletedMessageId);
            deletedWasSource = false;
        } else {
            return; // not a tracked global chat message
        }

        Map<String, String> targets = messageMapping.get(sourceMessageId);
        if (targets == null) return;

        String globalId = textChannelToGlobal.get(deletedInChannelId);
        if (globalId == null) return;
        GlobalChatChannel gc = channels.get(globalId);
        if (gc == null) return;

        // Register all message IDs we're about to delete as pending, to prevent
        // the resulting MessageDeleteEvents from triggering another cascade
        for (String relayedMsgId : targets.values()) {
            pendingDeletes.add(relayedMsgId);
        }
        pendingDeletes.add(sourceMessageId);

        // Delete all relayed webhook copies (skip the channel where deletion already happened)
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            String targetChId = entry.getKey();
            String relayedMsgId = entry.getValue();

            if (targetChId.equals(deletedInChannelId)) continue;

            Guild guild = null;
            for (Map.Entry<String, String> linked : gc.getLinkedChannels().entrySet()) {
                if (linked.getValue().equals(targetChId)) {
                    guild = jda.getGuildById(linked.getKey());
                    break;
                }
            }
            if (guild == null) continue;
            TextChannel tc = guild.getTextChannelById(targetChId);
            if (tc == null) continue;

            // Use the webhook to delete its own message (no MANAGE_MESSAGES permission needed)
            getOrCreateWebhook(tc).thenAccept(webhook -> {
                if (webhook != null) {
                    webhook.deleteMessageById(relayedMsgId).queue(
                        s -> {
                            logger.debug("Deleted relayed message {} in channel {}", relayedMsgId, targetChId);
                            pendingDeletes.remove(relayedMsgId);
                        },
                        err -> {
                            logger.warn("Failed to delete relayed message {} in {}: {}", relayedMsgId, targetChId, err.getMessage());
                            pendingDeletes.remove(relayedMsgId);
                        }
                    );
                } else {
                    pendingDeletes.remove(relayedMsgId);
                }
            });
        }

        // If a relayed copy was deleted, also delete the source message in the original channel
        if (!deletedWasSource) {
            String srcChannelId = sourceMessageChannel.get(sourceMessageId);
            if (srcChannelId != null && !srcChannelId.equals(deletedInChannelId)) {
                for (Map.Entry<String, String> linked : gc.getLinkedChannels().entrySet()) {
                    if (linked.getValue().equals(srcChannelId)) {
                        Guild guild = jda.getGuildById(linked.getKey());
                        if (guild != null) {
                            TextChannel tc = guild.getTextChannelById(srcChannelId);
                            if (tc != null) {
                                tc.deleteMessageById(sourceMessageId).queue(
                                    s -> {
                                        logger.debug("Deleted source message {} in channel {}", sourceMessageId, srcChannelId);
                                        pendingDeletes.remove(sourceMessageId);
                                    },
                                    err -> {
                                        logger.warn("Failed to delete source message: {}", err.getMessage());
                                        pendingDeletes.remove(sourceMessageId);
                                    }
                                );
                            } else {
                                pendingDeletes.remove(sourceMessageId);
                            }
                        } else {
                            pendingDeletes.remove(sourceMessageId);
                        }
                        break;
                    }
                }
            } else {
                pendingDeletes.remove(sourceMessageId);
            }
        } else {
            pendingDeletes.remove(sourceMessageId);
        }

        // Clean up mappings
        for (String relayedId : targets.values()) {
            reverseMessageMapping.remove(relayedId);
        }
        messageMapping.remove(sourceMessageId);
        sourceMessageChannel.remove(sourceMessageId);
    }

    /**
     * Relay a reaction from one linked channel to all other linked channels of the same global chat.
     */
    public void relayReaction(String messageId, String textChannelId, EmojiUnion emoji, JDA jda) {
        // Check if the message is a source message
        Map<String, String> targets = messageMapping.get(messageId);
        String sourceChannel = textChannelId;

        if (targets == null) {
            // Check if it's a relayed message — find the source message
            String sourceMsgId = reverseMessageMapping.get(messageId);
            if (sourceMsgId == null) return; // unknown message
            targets = messageMapping.get(sourceMsgId);
            if (targets == null) return;
            sourceChannel = sourceMessageChannel.get(sourceMsgId);
            messageId = sourceMsgId; // work with the source from now on
        }

        String globalId = textChannelToGlobal.get(textChannelId);
        if (globalId == null) return;
        GlobalChatChannel gc = channels.get(globalId);
        if (gc == null) return;

        // React on the source message in the source channel (if reaction came from a relay target)
        final String srcChannel = sourceMessageChannel.get(messageId);
        if (srcChannel != null && !srcChannel.equals(textChannelId)) {
            // Find the source guild/channel and add reaction to the source message
            for (Map.Entry<String, String> entry : gc.getLinkedChannels().entrySet()) {
                if (entry.getValue().equals(srcChannel)) {
                    Guild guild = jda.getGuildById(entry.getKey());
                    if (guild != null) {
                        TextChannel tc = guild.getTextChannelById(srcChannel);
                        if (tc != null) {
                            tc.addReactionById(messageId, emoji).queue(s -> {}, err -> {});
                        }
                    }
                    break;
                }
            }
        }

        // React on all relayed copies
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            String targetChId = entry.getKey();
            String relayedMsgId = entry.getValue();

            // Don't react on the channel where the reaction came from
            if (targetChId.equals(textChannelId)) continue;

            // Find the guild for this channel
            for (Map.Entry<String, String> linked : gc.getLinkedChannels().entrySet()) {
                if (linked.getValue().equals(targetChId)) {
                    Guild guild = jda.getGuildById(linked.getKey());
                    if (guild != null) {
                        TextChannel tc = guild.getTextChannelById(targetChId);
                        if (tc != null) {
                            tc.addReactionById(relayedMsgId, emoji).queue(s -> {}, err -> {});
                        }
                    }
                    break;
                }
            }
        }
    }

    /**
     * Look up the relayed message ID in a target channel for a given source message.
     */
    public String getRelayedMessageId(String sourceMessageId, String targetTextChannelId) {
        Map<String, String> targets = messageMapping.get(sourceMessageId);
        if (targets == null) return null;
        return targets.get(targetTextChannelId);
    }

    /**
     * Check if a message ID belongs to a global chat relay (source or relayed copy).
     */
    public boolean isGlobalChatMessage(String messageId) {
        return messageMapping.containsKey(messageId) || reverseMessageMapping.containsKey(messageId);
    }

    /**
     * Check if a message deletion is already being handled by the bot (to prevent cascading loops).
     */
    public boolean isDeletePending(String messageId) {
        return pendingDeletes.contains(messageId);
    }

    private void evictOldestMappings() {
        // Simple eviction: remove oldest 20% of entries
        int toRemove = MAX_MESSAGE_MAP_SIZE / 5;
        Iterator<Map.Entry<String, Map<String, String>>> it = messageMapping.entrySet().iterator();
        int removed = 0;
        while (it.hasNext() && removed < toRemove) {
            Map.Entry<String, Map<String, String>> entry = it.next();
            // Clean up reverse mappings
            for (String relayedId : entry.getValue().values()) {
                reverseMessageMapping.remove(relayedId);
            }
            sourceMessageChannel.remove(entry.getKey());
            it.remove();
            removed++;
        }
    }

    // ── Moderation actions (with notifications) ──────────────────────

    public String kickServer(String globalChannelId, String guildId, String reason, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        if (!gc.isGuildLinked(guildId)) return "That server is not linked to this channel.";

        String textChId = gc.getLinkedTextChannel(guildId);
        notifyLinkedChannel(jda, guildId, textChId, CustomEmojis.MOD_BAN + " This server has been **kicked** from the global chat channel **" + gc.getName() + "**.\nReason: " + (reason != null ? reason : "No reason provided."));

        gc.kickServer(guildId);
        textChannelToGlobal.remove(textChId);
        webhookCache.remove(textChId);
        saveChannels();
        return null;
    }

    public String banServer(String globalChannelId, String guildId, String reason, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";

        String textChId = gc.getLinkedTextChannel(guildId);
        if (textChId != null) {
            notifyLinkedChannel(jda, guildId, textChId, CustomEmojis.ERROR + " This server has been **banned** from the global chat channel **" + gc.getName() + "**.\nReason: " + (reason != null ? reason : "No reason provided."));
            textChannelToGlobal.remove(textChId);
            webhookCache.remove(textChId);
        }

        gc.banServer(guildId);
        saveChannels();
        return null;
    }

    public String unbanServer(String globalChannelId, String guildId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        if (!gc.isServerBanned(guildId)) return "That server is not banned.";
        gc.unbanServer(guildId);
        saveChannels();
        return null;
    }

    public String warnServer(String globalChannelId, String guildId, String reason, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        if (!gc.isGuildLinked(guildId)) return "That server is not linked to this channel.";

        gc.warnServer(guildId, reason != null ? reason : "No reason provided.");
        String textChId = gc.getLinkedTextChannel(guildId);
        notifyLinkedChannel(jda, guildId, textChId, CustomEmojis.WARN + " This server has received a **warning** in the global chat channel **" + gc.getName() + "**.\nReason: " + (reason != null ? reason : "No reason provided."));
        saveChannels();
        return null;
    }

    public String unwarnServer(String globalChannelId, String guildId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        gc.unwarnServer(guildId);
        saveChannels();
        return null;
    }

    public String muteServer(String globalChannelId, String guildId, long durationMillis, String reason, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        if (!gc.isGuildLinked(guildId)) return "That server is not linked to this channel.";

        gc.muteServer(guildId, durationMillis);
        String textChId = gc.getLinkedTextChannel(guildId);
        String durationStr = durationMillis <= 0 ? "permanently" : "for " + formatDuration(durationMillis);
        notifyLinkedChannel(jda, guildId, textChId, CustomEmojis.OFF + " This server has been **muted** " + durationStr + " in the global chat channel **" + gc.getName() + "**.\nReason: " + (reason != null ? reason : "No reason provided."));
        saveChannels();
        return null;
    }

    public String unmuteServer(String globalChannelId, String guildId, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";

        gc.unmuteServer(guildId);
        String textChId = gc.getLinkedTextChannel(guildId);
        if (textChId != null) {
            notifyLinkedChannel(jda, guildId, textChId, CustomEmojis.ON + " This server has been **unmuted** in the global chat channel **" + gc.getName() + "**.");
        }
        saveChannels();
        return null;
    }

    public String addModerator(String globalChannelId, String userId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        gc.getModeratorIds().add(userId);
        saveChannels();
        return null;
    }

    public String removeModerator(String globalChannelId, String userId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        gc.getModeratorIds().remove(userId);
        saveChannels();
        return null;
    }

    public String addCoOwner(String globalChannelId, String userId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        gc.getCoOwnerIds().add(userId);
        saveChannels();
        return null;
    }

    public String removeCoOwner(String globalChannelId, String userId) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return "Global chat channel not found.";
        gc.getCoOwnerIds().remove(userId);
        saveChannels();
        return null;
    }

    // ── Rules ────────────────────────────────────────────────────────

    public void setRules(String globalChannelId, List<String> rules, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null) return;
        gc.setRules(rules);
        saveChannels();

        // Notify all linked channels of updated rules
        String rulesText = formatRules(gc);
        for (Map.Entry<String, String> entry : gc.getLinkedChannels().entrySet()) {
            notifyLinkedChannel(jda, entry.getKey(), entry.getValue(),
                    CustomEmojis.NOTE + " The rules for global chat channel **" + gc.getName() + "** have been updated:\n" + rulesText);
        }
    }

    public void sendRulesToChannel(String globalChannelId, String guildId, JDA jda) {
        GlobalChatChannel gc = channels.get(globalChannelId);
        if (gc == null || gc.getRules().isEmpty()) return;
        String textChId = gc.getLinkedTextChannel(guildId);
        if (textChId == null) return;
        notifyLinkedChannel(jda, guildId, textChId,
                CustomEmojis.NOTE + " **Rules for global chat channel " + gc.getName() + ":**\n" + formatRules(gc));
    }

    public String formatRules(GlobalChatChannel gc) {
        if (gc.getRules().isEmpty()) return "No rules set.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gc.getRules().size(); i++) {
            sb.append(i + 1).append(". ").append(gc.getRules().get(i)).append("\n");
        }
        return sb.toString();
    }

    // ── Manage panel state ───────────────────────────────────────────

    public void setManagePanelState(String userId, ManagePanelState state) {
        managePanelStates.put(userId, state);
    }

    public ManagePanelState getManagePanelState(String userId) {
        return managePanelStates.get(userId);
    }

    public void clearManagePanelState(String userId) {
        managePanelStates.remove(userId);
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private void notifyLinkedChannel(JDA jda, String guildId, String textChannelId, String message) {
        if (textChannelId == null) return;
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) return;
        TextChannel tc = guild.getTextChannelById(textChannelId);
        if (tc == null) return;
        tc.sendMessageEmbeds(EmbedUtils.createInfoEmbed("Global Chat", message)).queue(
                success -> {},
                err -> logger.warn("Failed to send notification to {}: {}", textChannelId, err.getMessage())
        );
    }

    private String formatDuration(long millis) {
        long seconds = millis / 1000;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        long days = hours / 24;
        return days + "d";
    }

    // ── Placeholder resolution ─────────────────────────────────────

    /**
     * Resolve all supported placeholders in a format string.
     * Supported: {user}, {server}, {username}, {displayname}, {pronouns}
     */
    private String resolvePlaceholders(String format, String user, String server,
                                       String username, String displayName, String pronouns) {
        return format
                .replace("{user}", user)
                .replace("{server}", server)
                .replace("{username}", username)
                .replace("{displayname}", displayName)
                .replace("{pronouns}", pronouns);
    }

    // ── Manage panel state model ─────────────────────────────────────

    public static class ManagePanelState {
        private final String globalChannelId;
        private String pendingAction;   // e.g. "edit_name", "kick_server", "add_rule" etc.
        private String pendingData;     // any interim data for multi-step flows

        public ManagePanelState(String globalChannelId) {
            this.globalChannelId = globalChannelId;
        }

        public String getGlobalChannelId() { return globalChannelId; }
        public String getPendingAction() { return pendingAction; }
        public void setPendingAction(String pendingAction) { this.pendingAction = pendingAction; }
        public String getPendingData() { return pendingData; }
        public void setPendingData(String pendingData) { this.pendingData = pendingData; }
    }
}
