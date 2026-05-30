package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.ServerBot;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing reaction role systems
 */
public class ReactionRoleService {
    private static ReactionRoleService instance;
    private final Gson gson;
    private static final String DATA_DIR = "data";
    private static final String REACTION_ROLES_FILE = "reaction_roles.json";

    private ReactionRoleService() {
        this.gson = new Gson();
        new File(DATA_DIR).mkdirs();
    }

    public static ReactionRoleService getInstance() {
        if (instance == null) {
            instance = new ReactionRoleService();
        }
        return instance;
    }

    /**
     * Create a new reaction role message
     */
    public String createReactionRoleMessage(TextChannel channel, String title, String description, Member creator) {
        try {
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("🎭 " + title)
                    .setDescription(description)
                    .setFooter("Created by " + creator.getUser().getName(), creator.getUser().getEffectiveAvatarUrl());

            CompletableFuture<Message> messageFuture = new CompletableFuture<>();

            channel.sendMessageEmbeds(embed.build()).queue(
                    message -> {
                        // Store the reaction role message data
                        ReactionRoleMessage rrMessage = new ReactionRoleMessage();
                        rrMessage.messageId = message.getId();
                        rrMessage.channelId = channel.getId();
                        rrMessage.guildId = channel.getGuild().getId();
                        rrMessage.title = title;
                        rrMessage.description = description;
                        rrMessage.createdBy = creator.getId();
                        rrMessage.createdAt = System.currentTimeMillis();
                        rrMessage.reactions = new HashMap<>();

                        saveReactionRoleMessage(rrMessage);
                        messageFuture.complete(message);
                    },
                    error -> {
                        messageFuture.completeExceptionally(error);
                    });

            Message message = messageFuture.get();
            return message.getId();

        } catch (Exception e) {
            throw new RuntimeException("Failed to create reaction role message", e);
        }
    }

    /**
     * Attach a reaction role to any existing message (not created by the bot)
     */
    public void attachReactionRoleToExistingMessage(String guildId, String channelId, String messageId, String emoji,
            String roleId, Member creator) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);

            // Create a new reaction role message entry if it doesn't exist
            ReactionRoleMessage rrMessage = guildMessages.get(messageId);
            if (rrMessage == null) {
                rrMessage = new ReactionRoleMessage();
                rrMessage.messageId = messageId;
                rrMessage.channelId = channelId;
                rrMessage.guildId = guildId;
                rrMessage.title = "External Message Reaction Roles";
                rrMessage.description = "Reaction roles attached to existing message";
                rrMessage.createdBy = creator.getId();
                rrMessage.createdAt = System.currentTimeMillis();
                rrMessage.reactions = new HashMap<>();
            }

            // Add the emoji-role mapping
            rrMessage.reactions.put(emoji, roleId);

            // Add the reaction to the message
            Guild guild = getGuildById(guildId);
            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(channelId);
                if (channel != null) {
                    channel.retrieveMessageById(messageId).queue(
                            message -> {
                                Emoji emojiObj = parseEmoji(emoji);
                                if (emojiObj != null) {
                                    message.addReaction(emojiObj).queue(
                                            null,
                                            error -> System.err.println("Failed to add reaction to message " + messageId
                                                    + ": " + error.getMessage()));
                                }
                            },
                            new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                }
            }

            // Save the data
            saveReactionRoleMessage(rrMessage);

        } catch (Exception e) {
            throw new RuntimeException("Failed to attach reaction role to existing message", e);
        }
    }

    /**
     * Add a reaction role to an existing message
     */
    public void addReactionRole(String guildId, String messageId, String emoji, String roleId) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);
            ReactionRoleMessage rrMessage = guildMessages.get(messageId);

            if (rrMessage == null) {
                throw new IllegalArgumentException("Reaction role message not found with ID: " + messageId);
            }

            // Add the emoji-role mapping
            rrMessage.reactions.put(emoji, roleId);

            // Update the message with the new reaction
            Guild guild = getGuildById(guildId);
            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(rrMessage.channelId);
                if (channel != null) {
                    channel.retrieveMessageById(messageId).queue(
                            message -> {
                                // Add the reaction to the message
                                Emoji emojiObj = parseEmoji(emoji);
                                if (emojiObj != null) {
                                    message.addReaction(emojiObj).queue(
                                            null,
                                            error -> System.err.println("Failed to add reaction to message " + messageId
                                                    + ": " + error.getMessage()));
                                }

                                // Update the embed with the new role list
                                updateReactionRoleEmbed(message, rrMessage);
                            },
                            new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                }
            }

            // Save the updated data
            saveReactionRoleMessage(rrMessage);

        } catch (Exception e) {
            throw new RuntimeException("Failed to add reaction role", e);
        }
    }

    /**
     * Remove a reaction role from a message
     */
    public boolean removeReactionRole(String guildId, String messageId, String emoji) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);
            ReactionRoleMessage rrMessage = guildMessages.get(messageId);

            if (rrMessage == null || !rrMessage.reactions.containsKey(emoji)) {
                return false;
            }

            // Remove the emoji-role mapping
            rrMessage.reactions.remove(emoji);

            // Update the message
            Guild guild = getGuildById(guildId);
            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(rrMessage.channelId);
                if (channel != null) {
                    channel.retrieveMessageById(messageId).queue(
                            message -> {
                                // Remove the reaction from the message
                                Emoji emojiObj = parseEmoji(emoji);
                                if (emojiObj != null) {
                                    message.clearReactions(emojiObj).queue();
                                }

                                // Update the embed
                                updateReactionRoleEmbed(message, rrMessage);
                            },
                            new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                }
            }

            // Save the updated data
            saveReactionRoleMessage(rrMessage);
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to remove reaction role", e);
        }
    }

    /**
     * Delete a reaction role message entirely
     */
    public boolean deleteReactionRoleMessage(String guildId, String messageId) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);
            ReactionRoleMessage rrMessage = guildMessages.remove(messageId);

            if (rrMessage == null) {
                return false;
            }

            // Delete the actual Discord message
            Guild guild = getGuildById(guildId);
            if (guild != null) {
                TextChannel channel = guild.getTextChannelById(rrMessage.channelId);
                if (channel != null) {
                    channel.deleteMessageById(messageId).queue(
                            null,
                            new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MESSAGE));
                }
            }

            // Save the updated data
            saveGuildReactionRoles(guildId, guildMessages);
            return true;

        } catch (Exception e) {
            throw new RuntimeException("Failed to delete reaction role message", e);
        }
    }

    /**
     * Get a formatted list of all reaction roles for a guild
     */
    public String getReactionRolesList(String guildId) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);

            if (guildMessages.isEmpty()) {
                return "No reaction role messages have been created for this server.\n\n" +
                        "Use `/reactionrole create` to create your first reaction role message!";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("**Server Reaction Roles:**\n\n");

            int count = 1;
            for (ReactionRoleMessage rrMessage : guildMessages.values()) {
                sb.append(String.format("**%d.** %s\n", count, rrMessage.title));
                sb.append(String.format("├ **Message ID:** `%s`\n", rrMessage.messageId));
                sb.append(String.format("├ **Channel:** <#%s>\n", rrMessage.channelId));
                sb.append(String.format("├ **Reactions:** %d\n", rrMessage.reactions.size()));

                if (!rrMessage.reactions.isEmpty()) {
                    sb.append("└ **Roles:**\n");
                    for (Map.Entry<String, String> entry : rrMessage.reactions.entrySet()) {
                        sb.append(String.format("   • %s → <@&%s>\n", entry.getKey(), entry.getValue()));
                    }
                } else {
                    sb.append("└ *No reactions added yet*\n");
                }
                sb.append("\n");
                count++;
            }

            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to get reaction roles list", e);
        }
    }

    /**
     * Handle reaction add event
     */
    public void handleReactionAdd(String guildId, String messageId, String userId, String emoji) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);
            ReactionRoleMessage rrMessage = guildMessages.get(messageId);

            if (rrMessage == null || !rrMessage.reactions.containsKey(emoji)) {
                return;
            }

            String roleId = rrMessage.reactions.get(emoji);
            Guild guild = getGuildById(guildId);

            if (guild != null) {
                guild.retrieveMemberById(userId).queue(
                        member -> {
                            Role role = guild.getRoleById(roleId);
                            if (role != null && guild.getSelfMember().canInteract(role)) {
                                guild.addRoleToMember(member, role)
                                        .reason("Reaction role: " + emoji)
                                        .queue(
                                                null,
                                                new ErrorHandler()
                                                        .ignore(ErrorResponse.UNKNOWN_MEMBER)
                                                        .handle(ErrorResponse.MISSING_PERMISSIONS,
                                                                (response) -> System.err.println(
                                                                        "Missing permissions to assign role in "
                                                                                + guild.getName())));
                            }
                        },
                        new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
            }

        } catch (Exception e) {
            System.err.println("Failed to handle reaction add: " + e.getMessage());
        }
    }

    /**
     * Handle reaction remove event
     */
    public void handleReactionRemove(String guildId, String messageId, String userId, String emoji) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(guildId);
            ReactionRoleMessage rrMessage = guildMessages.get(messageId);

            if (rrMessage == null || !rrMessage.reactions.containsKey(emoji)) {
                return;
            }

            String roleId = rrMessage.reactions.get(emoji);
            Guild guild = getGuildById(guildId);

            if (guild != null) {
                guild.retrieveMemberById(userId).queue(
                        member -> {
                            Role role = guild.getRoleById(roleId);
                            if (role != null && guild.getSelfMember().canInteract(role)) {
                                guild.removeRoleFromMember(member, role)
                                        .reason("Reaction role removed: " + emoji)
                                        .queue(
                                                null,
                                                new ErrorHandler()
                                                        .ignore(ErrorResponse.UNKNOWN_MEMBER)
                                                        .handle(ErrorResponse.MISSING_PERMISSIONS,
                                                                (response) -> System.err.println(
                                                                        "Missing permissions to remove role in "
                                                                                + guild.getName())));
                            }
                        },
                        new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
            }

        } catch (Exception e) {
            System.err.println("Failed to handle reaction remove: " + e.getMessage());
        }
    }

    private void updateReactionRoleEmbed(Message message, ReactionRoleMessage rrMessage) {
        try {
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("🎭 " + rrMessage.title)
                    .setDescription(rrMessage.description);

            if (!rrMessage.reactions.isEmpty()) {
                StringBuilder roleList = new StringBuilder();
                roleList.append("\n**Available Roles:**\n");

                for (Map.Entry<String, String> entry : rrMessage.reactions.entrySet()) {
                    roleList.append(String.format("%s → <@&%s>\n", entry.getKey(), entry.getValue()));
                }

                embed.setDescription(rrMessage.description + roleList.toString());
            }

            message.editMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            System.err.println("Failed to update reaction role embed: " + e.getMessage());
        }
    }

    private Emoji parseEmoji(String emojiStr) {
        // Detect custom emoji format: <:name:id> or <a:name:id>
        if (emojiStr.matches("<a?:[a-zA-Z0-9_]+:\\d+>")) {
            return Emoji.fromFormatted(emojiStr);
        }
        // Otherwise treat as a unicode emoji
        return Emoji.fromUnicode(emojiStr);
    }

    private Guild getGuildById(String guildId) {
        try {
            return com.serverbot.ServerBot.getJda().getGuildById(guildId);
        } catch (Exception e) {
            System.err.println("Failed to get guild by ID: " + e.getMessage());
            return null;
        }
    }

    private Map<String, ReactionRoleMessage> loadGuildReactionRoles(String guildId) {
        try {
            Map<String, Map<String, ReactionRoleMessage>> allData = loadAllReactionRoles();
            return allData.getOrDefault(guildId, new HashMap<>());
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private void saveGuildReactionRoles(String guildId, Map<String, ReactionRoleMessage> guildMessages) {
        try {
            Map<String, Map<String, ReactionRoleMessage>> allData = loadAllReactionRoles();
            allData.put(guildId, guildMessages);
            saveAllReactionRoles(allData);
        } catch (Exception e) {
            System.err.println("Failed to save guild reaction roles: " + e.getMessage());
        }
    }

    private void saveReactionRoleMessage(ReactionRoleMessage rrMessage) {
        try {
            Map<String, ReactionRoleMessage> guildMessages = loadGuildReactionRoles(rrMessage.guildId);
            guildMessages.put(rrMessage.messageId, rrMessage);
            saveGuildReactionRoles(rrMessage.guildId, guildMessages);
        } catch (Exception e) {
            System.err.println("Failed to save reaction role message: " + e.getMessage());
        }
    }

    private Map<String, Map<String, ReactionRoleMessage>> loadAllReactionRoles() {
        try {
            File file = new File(DATA_DIR, REACTION_ROLES_FILE);
            if (!file.exists()) {
                return new HashMap<>();
            }

            Type mapType = new TypeToken<Map<String, Map<String, ReactionRoleMessage>>>() {
            }.getType();
            Map<String, Map<String, ReactionRoleMessage>> data = gson.fromJson(new FileReader(file), mapType);
            return data != null ? data : new HashMap<>();
        } catch (IOException e) {
            System.err.println("Failed to load reaction roles data: " + e.getMessage());
            return new HashMap<>();
        }
    }

    private void saveAllReactionRoles(Map<String, Map<String, ReactionRoleMessage>> data) {
        try {
            File file = new File(DATA_DIR, REACTION_ROLES_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(data, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save reaction roles data: " + e.getMessage());
        }
    }

    // Data class for storing reaction role message information
    public static class ReactionRoleMessage {
        public String messageId;
        public String channelId;
        public String guildId;
        public String title;
        public String description;
        public String createdBy;
        public long createdAt;
        public Map<String, String> reactions = new HashMap<>(); // emoji -> roleId
    }
}