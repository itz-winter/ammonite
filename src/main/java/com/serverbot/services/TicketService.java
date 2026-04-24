package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.serverbot.ServerBot;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;


import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for managing Discord ticket system
 * Based on discord-tickets bot functionality
 */
public class TicketService {
    
    private static final String TICKETS_DATA_DIR = "data/tickets/";
    private static final String TICKET_CATEGORIES_FILE = "ticket_categories.json";
    private static final String TICKET_DATA_FILE = "ticket_data.json";
    private static final String TICKET_SETTINGS_FILE = "ticket_settings.json";
    private static final String TICKET_KIOSKS_FILE = "ticket_kiosks.json";
    
    private static final String TICKETS_CATEGORY_NAME = "Tickets";
    private static final String CLOSED_TICKETS_CATEGORY_NAME = "Closed Tickets";
    private static final String ARCHIVED_TICKETS_CATEGORY_NAME = "Archived Tickets";
    private static final long CLOSED_TICKET_DELETE_DELAY_DAYS = 14;
    
    private final Gson gson;
    private final Map<String, TicketCategory> ticketCategories;
    private final Map<String, TicketData> activeTickets;
    private final Map<String, TicketSettings> guildSettings;
    private final Map<String, TicketKiosk> ticketKiosks;
    
    public TicketService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(java.time.Instant.class, new com.serverbot.utils.InstantTypeAdapter())
                .create();
        this.ticketCategories = new HashMap<>();
        this.activeTickets = new HashMap<>();
        this.guildSettings = new HashMap<>();
        this.ticketKiosks = new HashMap<>();
        
        // Create data directory
        new File(TICKETS_DATA_DIR).mkdirs();
        
        // Load existing data
        loadTicketCategories();
        loadTicketData();
        loadTicketSettings();
        loadTicketKiosks();
        
        // Schedule daily cleanup task for expired tickets
        startCleanupScheduler();
    }
    
    /**
     * Start the scheduler for cleaning up expired closed tickets
     */
    private void startCleanupScheduler() {
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            try {
                // Get JDA instance from ServerBot
                net.dv8tion.jda.api.JDA jda = ServerBot.getJda();
                if (jda != null) {
                    ServerBot.getLogger().info("Running daily ticket cleanup task");
                    // Clean up expired tickets for all guilds
                    for (net.dv8tion.jda.api.entities.Guild guild : jda.getGuilds()) {
                        try {
                            deleteExpiredClosedTickets(guild);
                        } catch (Exception e) {
                            ServerBot.getLogger().error("Error cleaning up tickets for guild " + guild.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                ServerBot.getLogger().error("Error in ticket cleanup scheduler: " + e.getMessage(), e);
            }
        }, 1, 24, java.util.concurrent.TimeUnit.HOURS); // Run daily, starting 1 hour after startup
    }
    
    /**
     * Clean up expired tickets for a specific guild (can be called manually)
     */
    public void cleanupExpiredTickets(Guild guild) {
        deleteExpiredClosedTickets(guild);
    }
    
    /**
     * Create a new ticket
     */
    public CompletableFuture<String> createTicket(Guild guild, User user, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Check if user already has an open ticket
                if (hasOpenTicket(guild.getId(), user.getId())) {
                    return "811"; // User already has ticket
                }
                
                // Find or create tickets category
                Category ticketCategory = findOrCreateTicketsCategory(guild);
                if (ticketCategory == null) {
                    return "822"; // Failed to create category
                }
                
                // Generate ticket ID
                String ticketId = generateTicketId(guild.getId());
                String channelName = "ticket-" + ticketId;
                
                // Get ticket settings for staff roles
                TicketSettings settings = getTicketSettings(guild.getId());
                
                // Create ticket channel
                TextChannel ticketChannel = ticketCategory.createTextChannel(channelName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addMemberPermissionOverride(user.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                    .setTopic("Ticket #" + ticketId + " | " + user.getEffectiveName())
                    .complete();
                
                // Add staff permissions from settings
                if (settings != null && settings.getSupportRoles() != null) {
                    for (String roleId : settings.getSupportRoles()) {
                        Role role = guild.getRoleById(roleId);
                        if (role != null) {
                            ticketChannel.getManager()
                                .putPermissionOverride(role, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                                .queue();
                        }
                    }
                }
                
                // Create ticket data
                TicketData ticketData = new TicketData(
                    ticketId,
                    guild.getId(),
                    user.getId(),
                    ticketChannel.getId(),
                    null, // No category ID
                    reason,
                    Instant.now(),
                    TicketStatus.OPEN
                );
                
                activeTickets.put(ticketId, ticketData);
                saveTicketData();
                
                // Send welcome message
                sendTicketWelcomeMessage(ticketChannel, user, ticketId, reason);
                
                return ticketId;
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error creating ticket: " + e.getMessage(), e);
                return "800"; // Operation failed
            }
        });
    }
    
    /**
     * Close a ticket
     */
    public CompletableFuture<String> closeTicket(Guild guild, String ticketId, User closedBy, String reason) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TicketData ticket = activeTickets.get(ticketId);
                if (ticket == null) {
                    return "810"; // Ticket not found
                }
                
                TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
                if (channel == null) {
                    return "820"; // Channel not found
                }
                
                // Generate and upload transcript (async — done after channel move/save)
                String transcriptUrl = null; // no longer a URL; handled by generateAndUploadTranscript
                
                // Update ticket status
                ticket.setStatus(TicketStatus.CLOSED);
                ticket.setClosedBy(closedBy.getId());
                ticket.setClosedAt(Instant.now());
                ticket.setCloseReason(reason);
                ticket.setTranscriptUrl(null);
                
                // Move to Closed Tickets category
                Category closedCategory = findOrCreateClosedTicketsCategory(guild);
                channel.getManager().setParent(closedCategory).queue();
                
                // Save data
                saveTicketData();
                
                // Send closing message with transcript as JSON file attachment (async)
                generateAndUploadTranscript(channel, ticket, closedBy, reason);
                
                return "SUCCESS";
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error closing ticket: " + e.getMessage(), e);
                return "800"; // Operation failed
            }
        });
    }
    
    /**
     * Add user to ticket
     */
    public CompletableFuture<String> addUserToTicket(Guild guild, String ticketId, User userToAdd) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TicketData ticket = activeTickets.get(ticketId);
                if (ticket == null) {
                    return "810"; // Ticket not found
                }
                
                TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
                if (channel == null) {
                    return "820"; // Channel not found
                }
                
                // Add user permissions
                channel.getManager()
                    .putMemberPermissionOverride(userToAdd.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY), null)
                    .queue();
                
                // Send notification
                channel.sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "User Added",
                    userToAdd.getAsMention() + " has been added to this ticket."
                )).queue();
                
                return "SUCCESS";
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error adding user to ticket: " + e.getMessage(), e);
                return "800"; // Operation failed
            }
        });
    }
    
    /**
     * Remove user from ticket
     */
    public CompletableFuture<String> removeUserFromTicket(Guild guild, String ticketId, User userToRemove) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TicketData ticket = activeTickets.get(ticketId);
                if (ticket == null) {
                    return "810"; // Ticket not found
                }
                
                // Don't allow removing ticket creator
                if (userToRemove.getId().equals(ticket.getCreatorId())) {
                    return "832"; // Cannot remove creator
                }
                
                TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
                if (channel == null) {
                    return "820"; // Channel not found
                }
                
                // Remove user permissions
                channel.getManager()
                    .removePermissionOverride(userToRemove.getIdLong())
                    .queue();
                
                // Send notification
                channel.sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "User Removed",
                    userToRemove.getAsMention() + " has been removed from this ticket."
                )).queue();
                
                return "SUCCESS";
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error removing user from ticket: " + e.getMessage(), e);
                return "T10"; // Remove user failed
            }
        });
    }
    
    /**
     * Create or update ticket category
     */
    public String createTicketCategory(String guildId, String categoryId, String name, String description, 
                                      String emoji, List<String> staffRoles) {
        try {
            TicketCategory category = new TicketCategory(categoryId, name, description, emoji, staffRoles);
            
            String key = guildId + ":" + categoryId;
            ticketCategories.put(key, category);
            
            saveTicketCategories();
            return "SUCCESS";
            
        } catch (Exception e) {
            ServerBot.getLogger().error("Error creating ticket category: " + e.getMessage(), e);
            return "T11"; // Category creation failed
        }
    }
    
    /**
     * Delete ticket category
     */
    public String deleteTicketCategory(String guildId, String categoryId) {
        try {
            String key = guildId + ":" + categoryId;
            
            if (!ticketCategories.containsKey(key)) {
                return "T12"; // Category not found
            }
            
            ticketCategories.remove(key);
            saveTicketCategories();
            
            return "SUCCESS";
            
        } catch (Exception e) {
            ServerBot.getLogger().error("Error deleting ticket category: " + e.getMessage(), e);
            return "T13"; // Category deletion failed
        }
    }
    
    /**
     * Get ticket categories for guild
     */
    public List<TicketCategory> getTicketCategories(String guildId) {
        return ticketCategories.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(guildId + ":"))
            .map(Map.Entry::getValue)
            .sorted(Comparator.comparing(TicketCategory::getName))
            .toList();
    }
    
    /**
     * Get ticket category
     */
    public TicketCategory getTicketCategory(String guildId, String categoryId) {
        return ticketCategories.get(guildId + ":" + categoryId);
    }
    
    /**
     * Get ticket settings
     */
    public TicketSettings getTicketSettings(String guildId) {
        return guildSettings.computeIfAbsent(guildId, k -> new TicketSettings());
    }
    
    /**
     * Update ticket settings
     */
    public String updateTicketSettings(String guildId, TicketSettings settings) {
        try {
            guildSettings.put(guildId, settings);
            saveTicketSettings();
            return "SUCCESS";
            
        } catch (Exception e) {
            ServerBot.getLogger().error("Error updating ticket settings: " + e.getMessage(), e);
            return "T14"; // Settings update failed
        }
    }
    
    /**
     * Check if user has open ticket
     */
    public boolean hasOpenTicket(String guildId, String userId) {
        return activeTickets.values().stream()
            .anyMatch(ticket -> ticket.getGuildId().equals(guildId) && 
                       ticket.getCreatorId().equals(userId) && 
                       ticket.getStatus() == TicketStatus.OPEN);
    }
    
    /**
     * Get user's open ticket
     */
    public TicketData getUserOpenTicket(String guildId, String userId) {
        return activeTickets.values().stream()
            .filter(ticket -> ticket.getGuildId().equals(guildId) && 
                     ticket.getCreatorId().equals(userId) && 
                     ticket.getStatus() == TicketStatus.OPEN)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Get ticket by channel ID
     */
    public TicketData getTicketByChannel(String channelId) {
        return activeTickets.values().stream()
            .filter(ticket -> ticket.getChannelId().equals(channelId))
            .findFirst()
            .orElse(null);
    }
    
    // Helper methods
    
    private String generateTicketId(String guildId) {
        // Simple incremental ID system
        long count = activeTickets.values().stream()
            .filter(ticket -> ticket.getGuildId().equals(guildId))
            .count();
        
        return String.format("%04d", count + 1);
    }
    
    private void sendTicketWelcomeMessage(TextChannel channel, User user, String ticketId, String reason) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🎫 Support Ticket #" + ticketId)
            .setDescription("Thank you for creating a support ticket. Our staff will be with you shortly.")
            .addField("Created by", user.getAsMention(), true)
            .addField("Reason", reason != null ? reason : "No reason provided", false)
            .setColor(Color.BLUE)
            .setTimestamp(Instant.now())
            .setFooter("Ticket System", channel.getJDA().getSelfUser().getAvatarUrl());
        
        Button closeButton = Button.danger("ticket_close", "Close Ticket")
            .withEmoji(Emoji.fromUnicode("🔒"));
        
        channel.sendMessageEmbeds(embed.build())
            .addComponents(ActionRow.of(closeButton))
            .queue();
    }
    
    private void sendTicketClosedMessage(TextChannel channel, User closedBy, String reason, String transcriptNote) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🔒 Ticket Closed")
            .setDescription("This ticket has been closed and moved to the Closed Tickets category.\n\n" +
                          "**The ticket will be automatically deleted in 14 days unless archived.**")
            .addField("Closed by", closedBy.getAsMention(), true)
            .addField("Reason", reason != null ? reason : "No reason provided", false)
            .setColor(Color.RED)
            .setTimestamp(Instant.now());

        if (transcriptNote != null) {
            embed.addField("Transcript", transcriptNote, false);
        }

        Button archiveButton = Button.primary("ticket_archive", "Archive Ticket")
            .withEmoji(Emoji.fromUnicode("📦"));

        channel.sendMessageEmbeds(embed.build())
            .addComponents(ActionRow.of(archiveButton))
            .queue();
    }

    /**
     * Fetch the channel history, serialise it to JSON, upload as a file attachment,
     * then send the closed-ticket embed.
     */
    private void generateAndUploadTranscript(TextChannel channel, TicketData ticket, User closedBy, String reason) {
        channel.getIterableHistory().takeAsync(500).thenAccept(messages -> {
            try {
                // Build JSON array (oldest-first)
                com.google.gson.JsonArray array = new com.google.gson.JsonArray();
                for (int i = messages.size() - 1; i >= 0; i--) {
                    net.dv8tion.jda.api.entities.Message msg = messages.get(i);
                    com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
                    obj.addProperty("id", msg.getId());
                    obj.addProperty("author", msg.getAuthor().getName());
                    obj.addProperty("authorId", msg.getAuthor().getId());
                    obj.addProperty("content", msg.getContentRaw());
                    obj.addProperty("timestamp", msg.getTimeCreated().toInstant().toString());
                    com.google.gson.JsonArray attachments = new com.google.gson.JsonArray();
                    for (net.dv8tion.jda.api.entities.Message.Attachment a : msg.getAttachments()) {
                        attachments.add(a.getUrl());
                    }
                    obj.add("attachments", attachments);
                    array.add(obj);
                }

                String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(array);
                byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                String filename = "ticket-" + ticket.getTicketId() + ".json";

                channel.sendMessageEmbeds(new EmbedBuilder()
                    .setTitle("🔒 Ticket Closed")
                    .setDescription("This ticket has been closed and moved to the Closed Tickets category.\n\n" +
                                  "**The ticket will be automatically deleted in 14 days unless archived.**")
                    .addField("Closed by", closedBy.getAsMention(), true)
                    .addField("Reason", reason != null ? reason : "No reason provided", false)
                    .addField("Transcript", "See attached JSON file.", false)
                    .setColor(Color.RED)
                    .setTimestamp(Instant.now())
                    .build())
                    .addComponents(ActionRow.of(Button.primary("ticket_archive", "Archive Ticket").withEmoji(Emoji.fromUnicode("📦"))))
                    .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(bytes, filename))
                    .queue();
            } catch (Exception e) {
                ServerBot.getLogger().error("Failed to generate ticket transcript: " + e.getMessage(), e);
                sendTicketClosedMessage(channel, closedBy, reason, null);
            }
        }).exceptionally(ex -> {
            ServerBot.getLogger().error("Failed to fetch ticket history: " + ex.getMessage(), ex);
            sendTicketClosedMessage(channel, closedBy, reason, null);
            return null;
        });
    }
    
    // Data persistence methods
    
    private void loadTicketCategories() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_CATEGORIES_FILE);
            if (file.exists() && file.length() > 0) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, TicketCategory>>(){}.getType();
                    Map<String, TicketCategory> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        ticketCategories.putAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            ServerBot.getLogger().error("Failed to load ticket categories: " + e.getMessage(), e);
        }
    }
    
    private void saveTicketCategories() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_CATEGORIES_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(ticketCategories, writer);
            }
        } catch (IOException e) {
            ServerBot.getLogger().error("Failed to save ticket categories: " + e.getMessage(), e);
        }
    }
    
    private void loadTicketData() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_DATA_FILE);
            if (file.exists() && file.length() > 0) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, TicketData>>(){}.getType();
                    Map<String, TicketData> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        // Only load open tickets
                        loaded.entrySet().stream()
                            .filter(entry -> entry.getValue().getStatus() == TicketStatus.OPEN)
                            .forEach(entry -> activeTickets.put(entry.getKey(), entry.getValue()));
                    }
                }
            }
        } catch (Exception e) {
            ServerBot.getLogger().error("Failed to load ticket data: " + e.getMessage(), e);
        }
    }
    
    private void saveTicketData() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_DATA_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(activeTickets, writer);
            }
        } catch (IOException e) {
            ServerBot.getLogger().error("Failed to save ticket data: " + e.getMessage(), e);
        }
    }
    
    private void loadTicketSettings() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_SETTINGS_FILE);
            if (file.exists() && file.length() > 0) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, TicketSettings>>(){}.getType();
                    Map<String, TicketSettings> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        guildSettings.putAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            ServerBot.getLogger().error("Failed to load ticket settings: " + e.getMessage(), e);
        }
    }
    
    private void saveTicketSettings() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_SETTINGS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(guildSettings, writer);
            }
        } catch (IOException e) {
            ServerBot.getLogger().error("Failed to save ticket settings: " + e.getMessage(), e);
        }
    }
    
    // Data classes
    
    public static class TicketCategory {
        private String categoryId;
        private String name;
        private String description;
        private String emoji;
        private List<String> staffRoles;
        
        public TicketCategory(String categoryId, String name, String description, String emoji, List<String> staffRoles) {
            this.categoryId = categoryId;
            this.name = name;
            this.description = description;
            this.emoji = emoji;
            this.staffRoles = staffRoles != null ? new ArrayList<>(staffRoles) : new ArrayList<>();
        }
        
        // Getters and setters
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getEmoji() { return emoji; }
        public void setEmoji(String emoji) { this.emoji = emoji; }
        
        public List<String> getStaffRoles() { return staffRoles; }
        public void setStaffRoles(List<String> staffRoles) { this.staffRoles = staffRoles; }
    }
    
    public static class TicketData {
        private String ticketId;
        private String guildId;
        private String creatorId;
        private String channelId;
        private String categoryId;
        private String reason;
        private Instant createdAt;
        private TicketStatus status;
        private String closedBy;
        private Instant closedAt;
        private String closeReason;
        private String transcriptUrl;
        
        public TicketData(String ticketId, String guildId, String creatorId, String channelId, 
                         String categoryId, String reason, Instant createdAt, TicketStatus status) {
            this.ticketId = ticketId;
            this.guildId = guildId;
            this.creatorId = creatorId;
            this.channelId = channelId;
            this.categoryId = categoryId;
            this.reason = reason;
            this.createdAt = createdAt;
            this.status = status;
        }
        
        // Getters and setters
        public String getTicketId() { return ticketId; }
        public void setTicketId(String ticketId) { this.ticketId = ticketId; }
        
        public String getGuildId() { return guildId; }
        public void setGuildId(String guildId) { this.guildId = guildId; }
        
        public String getCreatorId() { return creatorId; }
        public void setCreatorId(String creatorId) { this.creatorId = creatorId; }
        
        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }
        
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
        
        public TicketStatus getStatus() { return status; }
        public void setStatus(TicketStatus status) { this.status = status; }
        
        public String getClosedBy() { return closedBy; }
        public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
        
        public Instant getClosedAt() { return closedAt; }
        public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
        
        public String getCloseReason() { return closeReason; }
        public void setCloseReason(String closeReason) { this.closeReason = closeReason; }
        
        public String getTranscriptUrl() { return transcriptUrl; }
        public void setTranscriptUrl(String transcriptUrl) { this.transcriptUrl = transcriptUrl; }
    }
    
    public static class TicketSettings {
        private String categoryId;
        private boolean dmTranscripts = true;
        private boolean closeOnLeave = true;
        private String logChannelId;
        private List<String> supportRoles = new ArrayList<>();
        
        // Getters and setters
        public String getCategoryId() { return categoryId; }
        public void setCategoryId(String categoryId) { this.categoryId = categoryId; }
        
        public boolean isDmTranscripts() { return dmTranscripts; }
        public void setDmTranscripts(boolean dmTranscripts) { this.dmTranscripts = dmTranscripts; }
        
        public boolean isCloseOnLeave() { return closeOnLeave; }
        public void setCloseOnLeave(boolean closeOnLeave) { this.closeOnLeave = closeOnLeave; }
        
        public String getLogChannelId() { return logChannelId; }
        public void setLogChannelId(String logChannelId) { this.logChannelId = logChannelId; }
        
        public List<String> getSupportRoles() { return supportRoles; }
        public void setSupportRoles(List<String> supportRoles) { this.supportRoles = supportRoles; }
    }
    
    public enum TicketStatus {
        OPEN, CLOSED, ARCHIVED
    }
    
    public static class TicketKiosk {
        private String kioskId;
        private String guildId;
        private String channelId;
        private String messageId;
        private String title;
        private String description;
        private Instant createdAt;
        
        public TicketKiosk(String kioskId, String guildId, String channelId, String messageId, 
                          String title, String description, Instant createdAt) {
            this.kioskId = kioskId;
            this.guildId = guildId;
            this.channelId = channelId;
            this.messageId = messageId;
            this.title = title;
            this.description = description;
            this.createdAt = createdAt;
        }
        
        // Getters and setters
        public String getKioskId() { return kioskId; }
        public void setKioskId(String kioskId) { this.kioskId = kioskId; }
        
        public String getGuildId() { return guildId; }
        public void setGuildId(String guildId) { this.guildId = guildId; }
        
        public String getChannelId() { return channelId; }
        public void setChannelId(String channelId) { this.channelId = channelId; }
        
        public String getMessageId() { return messageId; }
        public void setMessageId(String messageId) { this.messageId = messageId; }
        
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Instant getCreatedAt() { return createdAt; }
        public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    }
    
    // Kiosk methods
    
    /**
     * Create a ticket kiosk message
     */
    public CompletableFuture<String> createTicketKiosk(Guild guild, TextChannel channel, String title, String description) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎫 " + title)
                    .setDescription(description)
                    .setColor(Color.BLUE)
                    .setTimestamp(Instant.now())
                    .setFooter("Ticket System", guild.getJDA().getSelfUser().getAvatarUrl());
                
                Button createButton = Button.success("ticket_kiosk_create", "Create Ticket")
                    .withEmoji(Emoji.fromUnicode("➕"));
                
                Message message = channel.sendMessageEmbeds(embed.build())
                    .addComponents(ActionRow.of(createButton))
                    .complete();
                
                // Save kiosk data
                String kioskId = guild.getId() + ":" + message.getId();
                TicketKiosk kiosk = new TicketKiosk(
                    kioskId,
                    guild.getId(),
                    channel.getId(),
                    message.getId(),
                    title,
                    description,
                    Instant.now()
                );
                
                ticketKiosks.put(kioskId, kiosk);
                saveTicketKiosks();
                
                return "SUCCESS";
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error creating ticket kiosk: " + e.getMessage(), e);
                return "T20"; // Kiosk creation failed
            }
        });
    }
    
    /**
     * Find or create the Tickets category
     */
    public Category findOrCreateTicketsCategory(Guild guild) {
        return findOrCreateCategoryByName(guild, TICKETS_CATEGORY_NAME);
    }
    
    /**
     * Find or create the Closed Tickets category
     */
    public Category findOrCreateClosedTicketsCategory(Guild guild) {
        return findOrCreateCategoryByName(guild, CLOSED_TICKETS_CATEGORY_NAME);
    }
    
    /**
     * Find or create the Archived Tickets category
     */
    public Category findOrCreateArchivedTicketsCategory(Guild guild) {
        return findOrCreateCategoryByName(guild, ARCHIVED_TICKETS_CATEGORY_NAME);
    }
    
    /**
     * Find or create a category by name
     */
    private Category findOrCreateCategoryByName(Guild guild, String categoryName) {
        // Find existing category
        Category existing = guild.getCategories().stream()
            .filter(cat -> cat.getName().equals(categoryName))
            .findFirst()
            .orElse(null);
        
        if (existing != null) {
            return existing;
        }
        
        // Create new category
        return guild.createCategory(categoryName)
            .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
            .complete();
    }
    
    /**
     * Move ticket to closed category
     */
    public CompletableFuture<String> moveTicketToClosed(Guild guild, TicketData ticket) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
                if (channel == null) {
                    return "820"; // Channel not found
                }
                
                Category closedCategory = findOrCreateClosedTicketsCategory(guild);
                channel.getManager().setParent(closedCategory).queue();
                
                ticket.setStatus(TicketStatus.CLOSED);
                ticket.setClosedAt(Instant.now());
                saveTicketData();
                
                return "SUCCESS";
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error moving ticket to closed: " + e.getMessage(), e);
                return "T21"; // Move failed
            }
        });
    }
    
    /**
     * Archive a ticket
     */
    public CompletableFuture<String> archiveTicket(Guild guild, TicketData ticket) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
                if (channel == null) {
                    return "820"; // Channel not found
                }
                
                Category archivedCategory = findOrCreateArchivedTicketsCategory(guild);
                channel.getManager().setParent(archivedCategory).queue();
                
                ticket.setStatus(TicketStatus.ARCHIVED);
                saveTicketData();
                
                return "SUCCESS";
                
            } catch (Exception e) {
                ServerBot.getLogger().error("Error archiving ticket: " + e.getMessage(), e);
                return "T22"; // Archive failed
            }
        });
    }
    
    /**
     * Get all closed tickets that are older than the deletion delay and not archived
     */
    public List<TicketData> getClosedTicketsForDeletion() {
        Instant cutoff = Instant.now().minus(CLOSED_TICKET_DELETE_DELAY_DAYS, java.time.temporal.ChronoUnit.DAYS);
        
        return activeTickets.values().stream()
            .filter(ticket -> ticket.getStatus() == TicketStatus.CLOSED)
            .filter(ticket -> ticket.getClosedAt() != null && ticket.getClosedAt().isBefore(cutoff))
            .toList();
    }
    
    /**
     * Delete expired closed tickets
     */
    public void deleteExpiredClosedTickets(Guild guild) {
        List<TicketData> ticketsToDelete = getClosedTicketsForDeletion().stream()
            .filter(ticket -> ticket.getGuildId().equals(guild.getId()))
            .toList();
        
        for (TicketData ticket : ticketsToDelete) {
            TextChannel channel = guild.getTextChannelById(ticket.getChannelId());
            if (channel != null) {
                channel.delete().queue(
                    success -> {
                        activeTickets.remove(ticket.getTicketId());
                        saveTicketData();
                        ServerBot.getLogger().info("Deleted expired ticket: {}", ticket.getTicketId());
                    },
                    error -> ServerBot.getLogger().error("Failed to delete expired ticket {}: {}", 
                        ticket.getTicketId(), error.getMessage())
                );
            } else {
                // Channel already deleted, just remove from tracking
                activeTickets.remove(ticket.getTicketId());
                saveTicketData();
            }
        }
    }
    
    // Kiosk persistence methods
    
    private void loadTicketKiosks() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_KIOSKS_FILE);
            if (file.exists() && file.length() > 0) {
                try (FileReader reader = new FileReader(file)) {
                    Type type = new TypeToken<Map<String, TicketKiosk>>(){}.getType();
                    Map<String, TicketKiosk> loaded = gson.fromJson(reader, type);
                    if (loaded != null) {
                        ticketKiosks.putAll(loaded);
                    }
                }
            }
        } catch (Exception e) {
            ServerBot.getLogger().error("Failed to load ticket kiosks: " + e.getMessage(), e);
        }
    }
    
    private void saveTicketKiosks() {
        try {
            File file = new File(TICKETS_DATA_DIR + TICKET_KIOSKS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(ticketKiosks, writer);
            }
        } catch (IOException e) {
            ServerBot.getLogger().error("Failed to save ticket kiosks: " + e.getMessage(), e);
        }
    }
}