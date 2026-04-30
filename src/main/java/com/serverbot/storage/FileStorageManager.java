package com.serverbot.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based data storage manager that replaces database functionality
 */
public class FileStorageManager {
    
    private static final Logger logger = LoggerFactory.getLogger(FileStorageManager.class);
    private static final String DATA_DIR = "data";
    private final Gson gson;
    private final File dataDir;
    
    // In-memory caches for better performance
    private final Map<String, Map<String, Object>> userEconomyCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> userLevelsCache = new ConcurrentHashMap<>();
    private final Map<String, List<Map<String, Object>>> userWarningsCache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> guildSettingsCache = new ConcurrentHashMap<>();
    
        // Temp punishments cache and operations
    private final Map<String, Map<String, Object>> tempPunishmentsCache = new HashMap<>();
    private final File tempPunishmentsFile;
    
    // Moderation logs cache and operations  
    private final Map<String, List<Map<String, Object>>> moderationLogsCache = new HashMap<>();
    private final File moderationLogsFile;
    
    // Suspicious users cache and operations
    private final Map<String, Map<String, Object>> suspiciousUsersCache = new ConcurrentHashMap<>();
    private final File suspiciousUsersFile;
    
    // Pending report messages cache: userId -> Map<ownerId, messageId>
    // Used to track which bot owner DM messages need updating when one owner validates/invalidates
    private final Map<String, Map<String, String>> pendingReportMessagesCache = new ConcurrentHashMap<>();
    private final File pendingReportMessagesFile;

    // User playlists cache: userId -> List<UserPlaylist>
    private final Map<String, List<UserPlaylist>> userPlaylistsCache = new ConcurrentHashMap<>();
    private final File userPlaylistsFile;

    /** A single entry in a user-created playlist. */
    public static class PlaylistEntry {
        public String url;
        public String title;
        public String author;
        public long duration; // millis, 0 if unknown
        public PlaylistEntry(String url, String title, String author, long duration) {
            this.url = url; this.title = title; this.author = author; this.duration = duration;
        }
    }

    /** A user-created playlist stored in the bot. */
    public static class UserPlaylist {
        public String name;
        public String description;
        public List<PlaylistEntry> entries;
        public long createdAt;
        public UserPlaylist(String name, String description) {
            this.name = name; this.description = description;
            this.entries = new ArrayList<>(); this.createdAt = System.currentTimeMillis();
        }
    }

    public FileStorageManager(String dataDirectory) {
        this.dataDir = new File(DATA_DIR);
        this.tempPunishmentsFile = new File(dataDir, "temp_punishments.json");
        this.moderationLogsFile = new File(dataDir, "moderation_logs.json");
        this.suspiciousUsersFile = new File(dataDir, "suspicious_users.json");
        this.pendingReportMessagesFile = new File(dataDir, "pending_report_messages.json");
        this.userPlaylistsFile = new File(dataDir, "user_playlists.json");
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(java.time.Instant.class, new com.serverbot.utils.InstantTypeAdapter())
                .create();
        initializeDataDirectory();
        loadAllData();
    }
    
    private void initializeDataDirectory() {
        try {
            Path dataPath = Paths.get(DATA_DIR);
            if (!Files.exists(dataPath)) {
                Files.createDirectories(dataPath);
                logger.info("Created data directory: {}", DATA_DIR);
            }
        } catch (IOException e) {
            logger.error("Failed to create data directory", e);
        }
    }
    
    private void loadAllData() {
        loadEconomyData();
        loadLevelsData();
        loadWarningsData();
        loadGuildSettings();
        loadTempPunishments();
        loadModerationLogs();
        loadSuspiciousUsers();
        loadPendingReportMessages();
        loadUserPlaylists();
        logger.info("All data loaded from files");
    }
    
    // Economy methods
    public long getBalance(String guildId, String userId) {
        String key = guildId + ":" + userId;
        Map<String, Object> data = userEconomyCache.get(key);
        if (data == null) return 0;
        return ((Number) data.getOrDefault("balance", 0)).longValue();
    }
    
    public void setBalance(String guildId, String userId, long balance) {
        String key = guildId + ":" + userId;
        Map<String, Object> data = userEconomyCache.computeIfAbsent(key, k -> new HashMap<>());
        data.put("balance", balance);
        data.put("lastUpdated", System.currentTimeMillis());
        saveEconomyData();
    }
    
    public void addBalance(String guildId, String userId, long amount) {
        long currentBalance = getBalance(guildId, userId);
        setBalance(guildId, userId, currentBalance + amount);
    }
    
    public boolean removeBalance(String guildId, String userId, long amount) {
        long currentBalance = getBalance(guildId, userId);
        if (currentBalance < amount) return false;
        setBalance(guildId, userId, currentBalance - amount);
        return true;
    }
    
    public List<Map.Entry<String, Long>> getTopBalances(String guildId, int limit) {
        List<Map.Entry<String, Long>> topBalances = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : userEconomyCache.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(guildId + ":")) {
                String userId = key.substring(guildId.length() + 1);
                long balance = ((Number) entry.getValue().getOrDefault("balance", 0)).longValue();
                topBalances.add(new AbstractMap.SimpleEntry<>(userId, balance));
            }
        }
        
        topBalances.sort(Map.Entry.<String, Long>comparingByValue().reversed());
        return topBalances.subList(0, Math.min(limit, topBalances.size()));
    }
    
    // Leveling methods
    public long getExperience(String guildId, String userId) {
        String key = guildId + ":" + userId;
        Map<String, Object> data = userLevelsCache.get(key);
        if (data == null) return 0;
        return ((Number) data.getOrDefault("experience", 0)).longValue();
    }
    
    public int getLevel(String guildId, String userId) {
        String key = guildId + ":" + userId;
        Map<String, Object> data = userLevelsCache.get(key);
        if (data == null) return 0;
        return ((Number) data.getOrDefault("level", 0)).intValue();
    }
    
    public void addExperience(String guildId, String userId, long experience) {
        String key = guildId + ":" + userId;
        Map<String, Object> data = userLevelsCache.computeIfAbsent(key, k -> new HashMap<>());
        
        long currentExp = ((Number) data.getOrDefault("experience", 0)).longValue();
        
        long newExp = currentExp + experience;
        int newLevel = calculateLevel(newExp);
        
        data.put("experience", newExp);
        data.put("level", newLevel);
        data.put("lastUpdated", System.currentTimeMillis());
        
        saveLevelsData();
    }
    
    public List<Map.Entry<String, Integer>> getTopLevels(String guildId, int limit) {
        List<Map.Entry<String, Integer>> topLevels = new ArrayList<>();
        
        for (Map.Entry<String, Map<String, Object>> entry : userLevelsCache.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(guildId + ":")) {
                String userId = key.substring(guildId.length() + 1);
                int level = ((Number) entry.getValue().getOrDefault("level", 0)).intValue();
                topLevels.add(new AbstractMap.SimpleEntry<>(userId, level));
            }
        }
        
        topLevels.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
        return topLevels.subList(0, Math.min(limit, topLevels.size()));
    }
    
    public Map<String, Map<String, Object>> getAllLevelData() {
        return new HashMap<>(userLevelsCache);
    }
    
    private int calculateLevel(long experience) {
        return (int) Math.floor(Math.sqrt(experience / 100.0));
    }
    
    // Warning methods
    public void addWarning(String guildId, String userId, String reason, String moderatorId) {
        String key = guildId + ":" + userId;
        List<Map<String, Object>> warnings = userWarningsCache.computeIfAbsent(key, k -> new ArrayList<>());
        
        Map<String, Object> warning = new HashMap<>();
        warning.put("id", System.currentTimeMillis());
        warning.put("reason", reason);
        warning.put("moderatorId", moderatorId);
        warning.put("timestamp", System.currentTimeMillis());
        
        warnings.add(warning);
        saveWarningsData();
    }
    
    public List<Map<String, Object>> getWarnings(String guildId, String userId) {
        String key = guildId + ":" + userId;
        return new ArrayList<>(userWarningsCache.getOrDefault(key, new ArrayList<>()));
    }
    
    public int getWarningCount(String guildId, String userId) {
        return getWarnings(guildId, userId).size();
    }
    
    public void clearWarnings(String guildId, String userId) {
        String key = guildId + ":" + userId;
        userWarningsCache.remove(key);
        saveWarningsData();
    }
    
    /**
     * Delete ALL stored data for a specific user across all guilds.
     * This is used to comply with data-deletion requests (GDPR / Discord Developer ToS).
     * Removes: economy, leveling, warnings, moderation logs (as subject), suspicious user data,
     * and pending report messages for that user.
     *
     * @param userId The Discord user ID whose data should be purged
     * @return the number of data categories that had data removed
     */
    public int deleteAllUserData(String userId) {
        int categoriesCleared = 0;
        
        // 1. Economy data — remove all guild:userId keys
        int removed = removeKeysEndingWith(userEconomyCache, userId);
        if (removed > 0) { categoriesCleared++; saveEconomyData(); }
        
        // 2. Leveling data
        removed = removeKeysEndingWith(userLevelsCache, userId);
        if (removed > 0) { categoriesCleared++; saveLevelsData(); }
        
        // 3. Warnings
        removed = removeKeysEndingWith(userWarningsCache, userId);
        if (removed > 0) { categoriesCleared++; saveWarningsData(); }
        
        // 4. Moderation logs where this user is the subject
        for (Map.Entry<String, List<Map<String, Object>>> entry : moderationLogsCache.entrySet()) {
            int sizeBefore = entry.getValue().size();
            entry.getValue().removeIf(log -> userId.equals(log.get("userId")) || userId.equals(log.get("targetId")));
            if (entry.getValue().size() != sizeBefore) categoriesCleared++;
        }
        saveModerationLogs();
        
        // 5. Suspicious user data
        if (suspiciousUsersCache.remove(userId) != null) {
            categoriesCleared++;
            saveSuspiciousUsers();
        }
        
        // 6. Pending report messages
        if (pendingReportMessagesCache.remove(userId) != null) {
            categoriesCleared++;
            savePendingReportMessages();
        }
        
        logger.info("Deleted all stored data for user {} — {} categories cleared", userId, categoriesCleared);
        return categoriesCleared;
    }
    
    /**
     * Helper: removes all entries from a map whose key ends with ":userId".
     */
    private <V> int removeKeysEndingWith(Map<String, V> map, String userId) {
        String suffix = ":" + userId;
        int count = 0;
        var iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            if (iter.next().getKey().endsWith(suffix)) {
                iter.remove();
                count++;
            }
        }
        return count;
    }
    
    /**
     * Get all warnings for all users in a guild
     */
    public Map<String, List<Map<String, Object>>> getAllWarnings(String guildId) {
        Map<String, List<Map<String, Object>>> guildWarnings = new HashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : userWarningsCache.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(guildId + ":")) {
                String userId = key.substring(guildId.length() + 1);
                guildWarnings.put(userId, new ArrayList<>(entry.getValue()));
            }
        }
        return guildWarnings;
    }
    
    /**
     * Update a user's warnings list (used for expiry management)
     */
    public void updateUserWarnings(String guildId, String userId, List<Map<String, Object>> warnings) {
        String key = guildId + ":" + userId;
        if (warnings.isEmpty()) {
            userWarningsCache.remove(key);
        } else {
            userWarningsCache.put(key, new ArrayList<>(warnings));
        }
        saveWarningsData();
    }
    
    /**
     * Get all guild settings (for expiry checking)
     */
    public Map<String, Object> getAllGuildSettings() {
        return new HashMap<>(guildSettingsCache);
    }
    
    public void logModerationAction(String guildId, String targetId, String moderatorId, String action, String reason, String duration) {
        List<Map<String, Object>> logs = moderationLogsCache.computeIfAbsent(guildId, k -> new ArrayList<>());
        
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("id", System.currentTimeMillis());
        logEntry.put("targetId", targetId);
        logEntry.put("moderatorId", moderatorId);
        logEntry.put("action", action);
        logEntry.put("reason", reason);
        logEntry.put("duration", duration);
        logEntry.put("timestamp", System.currentTimeMillis());
        
        logs.add(logEntry);
        saveModerationLogs();
    }
    
    // Guild settings methods
    public Map<String, Object> getGuildSettings(String guildId) {
        return new HashMap<>(guildSettingsCache.getOrDefault(guildId, getDefaultGuildSettings()));
    }
    
    public void updateGuildSettings(String guildId, String key, Object value) {
        Map<String, Object> settings = guildSettingsCache.computeIfAbsent(guildId, k -> getDefaultGuildSettings());
        if (value == null) {
            settings.remove(key);
        } else {
            settings.put(key, value);
        }
        saveGuildSettings();
    }
    
    /**
     * Remove a specific key from guild settings.
     */
    public void removeGuildSetting(String guildId, String key) {
        Map<String, Object> settings = guildSettingsCache.get(guildId);
        if (settings != null) {
            settings.remove(key);
            saveGuildSettings();
        }
    }
    
    // ─── Custom guild messages (configurable per-server) ─────────────────────

    private String messageKey(String type) { return "customMessages." + type; }

    /**
     * Get custom messages for a given type (e.g. "work", "daily").
     * Returns null if no custom list is set, so callers can fall back to defaults.
     */
    @SuppressWarnings("unchecked")
    public List<String> getCustomGuildMessages(String guildId, String type) {
        Object val = guildSettingsCache.getOrDefault(guildId, new HashMap<>()).get(messageKey(type));
        if (val instanceof List<?> list && !list.isEmpty()) {
            return new ArrayList<>((List<String>) list);
        }
        return null;
    }

    /** Set the full custom message list for a type. Passing null or empty resets to defaults. */
    public void setCustomGuildMessages(String guildId, String type, List<String> messages) {
        if (messages == null || messages.isEmpty()) {
            removeGuildSetting(guildId, messageKey(type));
        } else {
            updateGuildSettings(guildId, messageKey(type), new ArrayList<>(messages));
        }
    }

    /** Add one message to a custom list. Returns the new size. */
    @SuppressWarnings("unchecked")
    public int addCustomGuildMessage(String guildId, String type, String message) {
        Map<String, Object> settings = guildSettingsCache.computeIfAbsent(guildId, k -> getDefaultGuildSettings());
        Object val = settings.get(messageKey(type));
        List<String> list = (val instanceof List<?> l) ? new ArrayList<>((List<String>) l) : new ArrayList<>();
        list.add(message);
        settings.put(messageKey(type), list);
        saveGuildSettings();
        return list.size();
    }

    /** Remove message at 0-based index. Returns false if index out of range. */
    @SuppressWarnings("unchecked")
    public boolean removeCustomGuildMessage(String guildId, String type, int index) {
        Map<String, Object> settings = guildSettingsCache.get(guildId);
        if (settings == null) return false;
        Object val = settings.get(messageKey(type));
        if (!(val instanceof List<?> l)) return false;
        List<String> list = new ArrayList<>((List<String>) l);
        if (index < 0 || index >= list.size()) return false;
        list.remove(index);
        if (list.isEmpty()) settings.remove(messageKey(type));
        else settings.put(messageKey(type), list);
        saveGuildSettings();
        return true;
    }

    // New methods for getting specific settings
    public long getXpPerMessage(String guildId) {
        Map<String, Object> settings = getGuildSettings(guildId);
        Object value = settings.get("xpPerMessage");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 15L; // Default value
    }
    
    public long getPointsPerMessage(String guildId) {
        Map<String, Object> settings = getGuildSettings(guildId);
        Object value = settings.get("pointsPerMessage");
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 5L; // Default value
    }
    
    private Map<String, Object> getDefaultGuildSettings() {
        Map<String, Object> defaultSettings = new HashMap<>();
        defaultSettings.put("prefix", "/");
        defaultSettings.put("muteRoleName", "Muted");
        defaultSettings.put("maxWarnings", 3);
        defaultSettings.put("enableLeveling", true);
        defaultSettings.put("enableEconomy", true);
        defaultSettings.put("xpPerMessage", 15L); // Default XP per message
        defaultSettings.put("pointsPerMessage", 5L); // Default points per message
        defaultSettings.put("logChannelId", null);
        defaultSettings.put("welcomeChannelId", null);
        defaultSettings.put("autoRoleId", null);
        defaultSettings.put("dmNotifications", true);  // Whether bot sends DMs (except punishment DMs to target)
        return defaultSettings;
    }
    
    // File I/O methods
    private void loadEconomyData() {
        try {
            File file = new File(DATA_DIR, "economy.json");
            if (file.exists()) {
                Type type = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
                userEconomyCache.putAll(gson.fromJson(new FileReader(file), type));
            }
        } catch (IOException e) {
            logger.warn("Failed to load economy data", e);
        }
    }
    
    private void saveEconomyData() {
        try {
            File file = new File(DATA_DIR, "economy.json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(userEconomyCache, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save economy data", e);
        }
    }
    
    private void loadLevelsData() {
        try {
            File file = new File(DATA_DIR, "levels.json");
            if (file.exists()) {
                Type type = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
                userLevelsCache.putAll(gson.fromJson(new FileReader(file), type));
            }
        } catch (IOException e) {
            logger.warn("Failed to load levels data", e);
        }
    }
    
    private void saveLevelsData() {
        try {
            File file = new File(DATA_DIR, "levels.json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(userLevelsCache, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save levels data", e);
        }
    }
    
    private void loadWarningsData() {
        try {
            File file = new File(DATA_DIR, "warnings.json");
            if (file.exists()) {
                Type type = new TypeToken<Map<String, List<Map<String, Object>>>>(){}.getType();
                userWarningsCache.putAll(gson.fromJson(new FileReader(file), type));
            }
        } catch (IOException e) {
            logger.warn("Failed to load warnings data", e);
        }
    }
    
    private void saveWarningsData() {
        try {
            File file = new File(DATA_DIR, "warnings.json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(userWarningsCache, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save warnings data", e);
        }
    }
    
    private void loadGuildSettings() {
        try {
            File file = new File(DATA_DIR, "guilds.json");
            if (file.exists()) {
                Type type = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
                guildSettingsCache.putAll(gson.fromJson(new FileReader(file), type));
            }
        } catch (IOException e) {
            logger.warn("Failed to load guild settings", e);
        }
    }
    
    public void saveGuildSettings() {
        try {
            File file = new File(DATA_DIR, "guilds.json");
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(guildSettingsCache, writer);
            }
        } catch (IOException e) {
            logger.error("Failed to save guild settings", e);
        }
    }
    
    public void saveAllData() {
        saveEconomyData();
        saveLevelsData();
        saveWarningsData();
        saveGuildSettings();
        saveTempPunishments();
        saveModerationLogs();
        logger.info("All data saved to files");
    }
    
    // Temp Punishments Management
    public void storeTempPunishment(String key, Map<String, Object> punishmentData) {
        tempPunishmentsCache.put(key, punishmentData);
        saveTempPunishments();
    }
    
    public Map<String, Map<String, Object>> getAllTempPunishments() {
        return new HashMap<>(tempPunishmentsCache);
    }
    
    public void removeTempPunishment(String key) {
        tempPunishmentsCache.remove(key);
        saveTempPunishments();
    }
    
    private void loadTempPunishments() {
        tempPunishmentsCache.clear();
        if (!tempPunishmentsFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(tempPunishmentsFile)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
            Map<String, Map<String, Object>> data = gson.fromJson(reader, type);
            if (data != null) {
                tempPunishmentsCache.putAll(data);
            }
            logger.debug("Loaded {} temp punishments", tempPunishmentsCache.size());
        } catch (IOException e) {
            logger.error("Failed to load temp punishments", e);
        }
    }
    
    private void saveTempPunishments() {
        try (FileWriter writer = new FileWriter(tempPunishmentsFile)) {
            gson.toJson(tempPunishmentsCache, writer);
        } catch (IOException e) {
            logger.error("Failed to save temp punishments", e);
        }
    }
    
    // Moderation Logs Management
    public void addModerationLog(String guildId, Map<String, Object> logEntry) {
        moderationLogsCache.computeIfAbsent(guildId, k -> new ArrayList<>()).add(logEntry);
        saveModerationLogs();
    }
    
    public List<Map<String, Object>> getModerationLogs(String guildId) {
        return new ArrayList<>(moderationLogsCache.getOrDefault(guildId, new ArrayList<>()));
    }
    
    private void loadModerationLogs() {
        moderationLogsCache.clear();
        if (!moderationLogsFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(moderationLogsFile)) {
            Type type = new TypeToken<Map<String, List<Map<String, Object>>>>(){}.getType();
            Map<String, List<Map<String, Object>>> data = gson.fromJson(reader, type);
            if (data != null) {
                moderationLogsCache.putAll(data);
            }
            logger.debug("Loaded moderation logs for {} guilds", moderationLogsCache.size());
        } catch (IOException e) {
            logger.error("Failed to load moderation logs", e);
        }
    }
    
    private void saveModerationLogs() {
        try (FileWriter writer = new FileWriter(moderationLogsFile)) {
            gson.toJson(moderationLogsCache, writer);
        } catch (IOException e) {
            logger.error("Failed to save moderation logs", e);
        }
    }
    
    // Suspicious users methods
    /**
     * Mark a user as suspicious
     * @param userId The ID of the user to mark
     * @param markedBy The ID of the user who marked them (usually bot owner)
     * @param reason The reason they were marked
     * @param detectionData Original detection data
     */
    public void markUserAsSuspicious(String userId, String markedBy, String reason, Map<String, Object> detectionData) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("markedBy", markedBy);
        data.put("markedAt", System.currentTimeMillis());
        data.put("reason", reason);
        data.put("detectionData", detectionData);
        
        suspiciousUsersCache.put(userId, data);
        saveSuspiciousUsers();
        logger.info("Marked user {} as suspicious by {}", userId, markedBy);
    }
    
    /**
     * Check if a user is marked as suspicious
     */
    public boolean isUserSuspicious(String userId) {
        return suspiciousUsersCache.containsKey(userId);
    }
    
    /**
     * Get suspicious user data
     */
    public Map<String, Object> getSuspiciousUserData(String userId) {
        return suspiciousUsersCache.get(userId);
    }
    
    /**
     * Get all suspicious users
     */
    public Map<String, Map<String, Object>> getAllSuspiciousUsers() {
        return new HashMap<>(suspiciousUsersCache);
    }
    
    /**
     * Remove a user from the suspicious list
     */
    public void removeUserFromSuspiciousList(String userId) {
        suspiciousUsersCache.remove(userId);
        saveSuspiciousUsers();
        logger.info("Removed user {} from suspicious list", userId);
    }
    
    /**
     * Clear all users from the suspicious list
     */
    public void clearAllSuspiciousUsers() {
        int count = suspiciousUsersCache.size();
        suspiciousUsersCache.clear();
        saveSuspiciousUsers();
        logger.info("Cleared all {} suspicious users from list", count);
    }
    
    /**
     * Validate a suspicious user report (marks as verified by bot owner)
     */
    public void validateSuspiciousUser(String userId, String validatedBy) {
        Map<String, Object> data = suspiciousUsersCache.get(userId);
        if (data != null) {
            data.put("validated", true);
            data.put("validatedBy", validatedBy);
            data.put("validatedAt", System.currentTimeMillis());
            saveSuspiciousUsers();
            logger.info("Validated suspicious user {} by {}", userId, validatedBy);
        }
    }
    
    /**
     * Add a note to a suspicious user report
     */
    @SuppressWarnings("unchecked")
    public void addSuspiciousUserNote(String userId, String note, String addedBy, String suggestedAction) {
        Map<String, Object> data = suspiciousUsersCache.get(userId);
        if (data == null) {
            // Create entry if doesn't exist
            data = new HashMap<>();
            data.put("markedAt", System.currentTimeMillis());
            data.put("reason", "Added via note");
            suspiciousUsersCache.put(userId, data);
        }
        
        // Add note to notes list
        List<Map<String, Object>> notes = (List<Map<String, Object>>) data.get("notes");
        if (notes == null) {
            notes = new java.util.ArrayList<>();
            data.put("notes", notes);
        }
        
        Map<String, Object> noteData = new HashMap<>();
        noteData.put("text", note);
        noteData.put("addedBy", addedBy);
        noteData.put("addedAt", System.currentTimeMillis());
        notes.add(noteData);
        
        // Update suggested action if provided
        if (suggestedAction != null && !suggestedAction.trim().isEmpty()) {
            data.put("suggestedAction", suggestedAction);
        }
        
        saveSuspiciousUsers();
        logger.info("Added note to suspicious user {} by {}", userId, addedBy);
    }
    
    /**
     * Remove a user from the suspicious users list
     */
    public void removeSuspiciousUser(String userId) {
        if (suspiciousUsersCache.containsKey(userId)) {
            suspiciousUsersCache.remove(userId);
            saveSuspiciousUsers();
            logger.info("Removed suspicious user {}", userId);
        }
    }
    
    /**
     * Check if a suspicious user has been validated
     */
    public boolean isSuspiciousUserValidated(String userId) {
        Map<String, Object> data = suspiciousUsersCache.get(userId);
        if (data != null) {
            Boolean validated = (Boolean) data.get("validated");
            return validated != null && validated;
        }
        return false;
    }
    
    /**
     * Get count of suspicious users
     */
    public int getSuspiciousUserCount() {
        return suspiciousUsersCache.size();
    }
    
    private void loadSuspiciousUsers() {
        suspiciousUsersCache.clear();
        if (!suspiciousUsersFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(suspiciousUsersFile)) {
            Type type = new TypeToken<Map<String, Map<String, Object>>>(){}.getType();
            Map<String, Map<String, Object>> data = gson.fromJson(reader, type);
            if (data != null) {
                suspiciousUsersCache.putAll(data);
            }
            logger.debug("Loaded {} suspicious users", suspiciousUsersCache.size());
        } catch (IOException e) {
            logger.error("Failed to load suspicious users", e);
        }
    }
    
    private void saveSuspiciousUsers() {
        try (FileWriter writer = new FileWriter(suspiciousUsersFile)) {
            gson.toJson(suspiciousUsersCache, writer);
        } catch (IOException e) {
            logger.error("Failed to save suspicious users", e);
        }
    }
    
    // ==================== Pending Report Messages ====================
    // Used to track which bot owner DM messages need updating when one owner validates/invalidates
    
    /**
     * Store message IDs for a suspicious user report sent to bot owners
     * @param userId The suspicious user's ID
     * @param ownerMessageIds Map of ownerId -> messageId
     */
    public void storePendingReportMessages(String userId, Map<String, String> ownerMessageIds) {
        pendingReportMessagesCache.put(userId, new ConcurrentHashMap<>(ownerMessageIds));
        savePendingReportMessages();
        logger.debug("Stored {} pending report message IDs for user {}", ownerMessageIds.size(), userId);
    }
    
    /**
     * Get all pending report message IDs for a suspicious user
     * @param userId The suspicious user's ID
     * @return Map of ownerId -> messageId, or empty map if not found
     */
    public Map<String, String> getPendingReportMessages(String userId) {
        return pendingReportMessagesCache.getOrDefault(userId, new ConcurrentHashMap<>());
    }
    
    /**
     * Remove pending report messages for a user (after validation/invalidation)
     * @param userId The suspicious user's ID
     */
    public void removePendingReportMessages(String userId) {
        pendingReportMessagesCache.remove(userId);
        savePendingReportMessages();
        logger.debug("Removed pending report messages for user {}", userId);
    }
    
    private void loadPendingReportMessages() {
        pendingReportMessagesCache.clear();
        if (!pendingReportMessagesFile.exists()) {
            return;
        }
        
        try (FileReader reader = new FileReader(pendingReportMessagesFile)) {
            Type type = new TypeToken<Map<String, Map<String, String>>>(){}.getType();
            Map<String, Map<String, String>> data = gson.fromJson(reader, type);
            if (data != null) {
                for (Map.Entry<String, Map<String, String>> entry : data.entrySet()) {
                    pendingReportMessagesCache.put(entry.getKey(), new ConcurrentHashMap<>(entry.getValue()));
                }
            }
            logger.debug("Loaded {} pending report message entries", pendingReportMessagesCache.size());
        } catch (IOException e) {
            logger.error("Failed to load pending report messages", e);
        }
    }
    
    private void savePendingReportMessages() {
        try (FileWriter writer = new FileWriter(pendingReportMessagesFile)) {
            gson.toJson(pendingReportMessagesCache, writer);
        } catch (IOException e) {
            logger.error("Failed to save pending report messages", e);
        }
    }
    
    // Statistics and utility methods
    public int getUserDataCount(String guildId) {
        int count = 0;
        
        // Count economy data
        for (String key : userEconomyCache.keySet()) {
            if (key.startsWith(guildId + ":")) {
                count++;
            }
        }
        
        // Count level data
        for (String key : userLevelsCache.keySet()) {
            if (key.startsWith(guildId + ":")) {
                count++;
            }
        }
        
        // Count warning data
        for (String key : userWarningsCache.keySet()) {
            if (key.startsWith(guildId + ":")) {
                count++;
            }
        }
        
        return count;
    }
    
    // Prefix command settings methods
    
    /**
     * Get all active command prefixes for a guild.
     * Returns a list; the first element is the "primary" prefix shown in help text.
     */
    @SuppressWarnings("unchecked")
    public List<String> getPrefixes(String guildId) {
        Map<String, Object> settings = getGuildSettings(guildId);
        Object prefixes = settings.get("commandPrefixes");
        if (prefixes instanceof List && !((List<?>) prefixes).isEmpty()) {
            return new ArrayList<>((List<String>) prefixes);
        }
        // Fall back to legacy single-prefix field, then default
        Object legacy = settings.get("commandPrefix");
        List<String> result = new ArrayList<>();
        result.add(legacy != null ? legacy.toString() : "!");
        return result;
    }

    /**
     * Get the primary (first) command prefix for a guild.
     */
    public String getPrefix(String guildId) {
        return getPrefixes(guildId).get(0);
    }

    /**
     * Replace all prefixes for a guild with a single prefix.
     */
    public void setPrefix(String guildId, String prefix) {
        List<String> list = new ArrayList<>();
        list.add(prefix);
        updateGuildSettings(guildId, "commandPrefixes", list);
        updateGuildSettings(guildId, "commandPrefix", prefix); // keep legacy field in sync
    }

    /**
     * Add a prefix to the guild's active prefix list (no-op if already present).
     */
    public boolean addPrefix(String guildId, String prefix) {
        List<String> prefixes = getPrefixes(guildId);
        if (prefixes.contains(prefix)) return false;
        prefixes.add(prefix);
        updateGuildSettings(guildId, "commandPrefixes", prefixes);
        updateGuildSettings(guildId, "commandPrefix", prefixes.get(0));
        return true;
    }

    /**
     * Remove a prefix from the guild's active prefix list.
     * @return false if the prefix wasn't present or it's the only one remaining
     */
    public boolean removePrefix(String guildId, String prefix) {
        List<String> prefixes = getPrefixes(guildId);
        if (!prefixes.contains(prefix)) return false;
        if (prefixes.size() == 1) return false; // must keep at least one
        prefixes.remove(prefix);
        updateGuildSettings(guildId, "commandPrefixes", prefixes);
        updateGuildSettings(guildId, "commandPrefix", prefixes.get(0));
        return true;
    }
    
    /**
     * Check if prefix commands are globally enabled for a guild
     */
    public boolean arePrefixCommandsEnabled(String guildId) {
        Map<String, Object> settings = getGuildSettings(guildId);
        Object enabled = settings.get("prefixCommandsEnabled");
        // Default to true if not set
        return enabled == null || Boolean.TRUE.equals(enabled);
    }
    
    /**
     * Set whether prefix commands are globally enabled for a guild
     */
    public void setPrefixCommandsEnabled(String guildId, boolean enabled) {
        updateGuildSettings(guildId, "prefixCommandsEnabled", enabled);
    }
    
    /**
     * Get the set of disabled prefix commands for a guild
     */
    @SuppressWarnings("unchecked")
    public Set<String> getDisabledPrefixCommands(String guildId) {
        Map<String, Object> settings = getGuildSettings(guildId);
        Object disabled = settings.get("disabledPrefixCommands");
        if (disabled instanceof List) {
            return new HashSet<>((List<String>) disabled);
        }
        return new HashSet<>();
    }
    
    /**
     * Enable a specific prefix command for a guild
     */
    public void enablePrefixCommand(String guildId, String command) {
        Set<String> disabled = getDisabledPrefixCommands(guildId);
        disabled.remove(command.toLowerCase());
        updateGuildSettings(guildId, "disabledPrefixCommands", new ArrayList<>(disabled));
    }
    
    /**
     * Disable a specific prefix command for a guild
     */
    public void disablePrefixCommand(String guildId, String command) {
        Set<String> disabled = getDisabledPrefixCommands(guildId);
        disabled.add(command.toLowerCase());
        updateGuildSettings(guildId, "disabledPrefixCommands", new ArrayList<>(disabled));
    }
    
    /**
     * Check if a specific prefix command is enabled for a guild
     */
    public boolean isPrefixCommandEnabled(String guildId, String command) {
        // First check if prefix commands are globally enabled
        if (!arePrefixCommandsEnabled(guildId)) {
            return false;
        }
        // Then check if this specific command is disabled
        Set<String> disabled = getDisabledPrefixCommands(guildId);
        return !disabled.contains(command.toLowerCase());
    }
    
    /**
     * Enable all prefix commands for a guild
     */
    public void enableAllPrefixCommands(String guildId) {
        setPrefixCommandsEnabled(guildId, true);
        updateGuildSettings(guildId, "disabledPrefixCommands", new ArrayList<>());
    }
    
    /**
     * Disable all prefix commands for a guild
     */
    public void disableAllPrefixCommands(String guildId) {
        setPrefixCommandsEnabled(guildId, false);
    }

    // ─── User Playlist methods ────────────────────────────────────────────────

    private void loadUserPlaylists() {
        try {
            if (userPlaylistsFile.exists()) {
                Type type = new TypeToken<Map<String, List<UserPlaylist>>>(){}.getType();
                Map<String, List<UserPlaylist>> loaded = gson.fromJson(new FileReader(userPlaylistsFile), type);
                if (loaded != null) userPlaylistsCache.putAll(loaded);
            }
        } catch (IOException e) {
            logger.warn("Failed to load user playlists", e);
        }
    }

    private void saveUserPlaylists() {
        try (FileWriter writer = new FileWriter(userPlaylistsFile)) {
            gson.toJson(userPlaylistsCache, writer);
        } catch (IOException e) {
            logger.error("Failed to save user playlists", e);
        }
    }

    /** Get all playlists for a user (read-only copy). */
    public List<UserPlaylist> getUserPlaylists(String userId) {
        return new ArrayList<>(userPlaylistsCache.getOrDefault(userId, new ArrayList<>()));
    }

    /** Get a specific playlist by name (case-insensitive), or null. */
    public UserPlaylist getUserPlaylist(String userId, String name) {
        return userPlaylistsCache.getOrDefault(userId, new ArrayList<>()).stream()
            .filter(p -> p.name.equalsIgnoreCase(name))
            .findFirst().orElse(null);
    }

    /** Create a new playlist. Returns false if name already exists. */
    public boolean createUserPlaylist(String userId, String name, String description) {
        List<UserPlaylist> list = userPlaylistsCache.computeIfAbsent(userId, k -> new ArrayList<>());
        if (list.stream().anyMatch(p -> p.name.equalsIgnoreCase(name))) return false;
        list.add(new UserPlaylist(name, description));
        saveUserPlaylists();
        return true;
    }

    /** Delete a playlist by name. Returns false if not found. */
    public boolean deleteUserPlaylist(String userId, String name) {
        List<UserPlaylist> list = userPlaylistsCache.get(userId);
        if (list == null) return false;
        boolean removed = list.removeIf(p -> p.name.equalsIgnoreCase(name));
        if (removed) saveUserPlaylists();
        return removed;
    }

    /** Add a track to a playlist. Returns false if playlist not found. */
    public boolean addTrackToPlaylist(String userId, String playlistName, PlaylistEntry entry) {
        UserPlaylist pl = getUserPlaylist(userId, playlistName);
        if (pl == null) return false;
        pl.entries.add(entry);
        saveUserPlaylists();
        return true;
    }

    /** Remove a track by 0-based index. Returns false if index out of range or playlist not found. */
    public boolean removeTrackFromPlaylist(String userId, String playlistName, int index) {
        UserPlaylist pl = getUserPlaylist(userId, playlistName);
        if (pl == null || index < 0 || index >= pl.entries.size()) return false;
        pl.entries.remove(index);
        saveUserPlaylists();
        return true;
    }

    /** Rename a playlist. Returns false if not found or newName already taken. */
    public boolean renameUserPlaylist(String userId, String oldName, String newName) {
        List<UserPlaylist> list = userPlaylistsCache.get(userId);
        if (list == null) return false;
        UserPlaylist pl = list.stream().filter(p -> p.name.equalsIgnoreCase(oldName)).findFirst().orElse(null);
        if (pl == null) return false;
        if (list.stream().anyMatch(p -> p.name.equalsIgnoreCase(newName) && !p.name.equalsIgnoreCase(oldName))) return false;
        pl.name = newName;
        saveUserPlaylists();
        return true;
    }

    /** Move a track from one 0-based index to another. Returns false on invalid indices or playlist not found. */
    public boolean reorderTrackInPlaylist(String userId, String playlistName, int fromIdx, int toIdx) {
        UserPlaylist pl = getUserPlaylist(userId, playlistName);
        if (pl == null || fromIdx < 0 || fromIdx >= pl.entries.size()
                || toIdx < 0 || toIdx >= pl.entries.size()) return false;
        PlaylistEntry entry = pl.entries.remove(fromIdx);
        pl.entries.add(toIdx, entry);
        saveUserPlaylists();
        return true;
    }

    public void close() {
        saveAllData();
        logger.info("File storage manager closed");
    }
}
