package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.serverbot.utils.BotConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Manages bot configuration loading and saving
 */
public class ConfigManager {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);
    private static final String CONFIG_FILE = "config.json";
    private final Gson gson;
    private BotConfig config;

    public ConfigManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().setLenient().create();
        loadConfig();
    }

    private void loadConfig() {
        File configFile = new File(CONFIG_FILE);

        if (!configFile.exists()) {
            logger.info("Config file not found, creating default configuration...");
            config = new BotConfig();
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            config = gson.fromJson(reader, BotConfig.class);
            if (config == null) {
                logger.warn("Config file was empty or unreadable, using defaults");
                config = new BotConfig();
            }
            logger.info("Configuration loaded successfully from {}", CONFIG_FILE);

            // Re-save to backfill any new fields that were added since the last version.
            // This ensures the on-disk config.json always contains every known key with
            // its default value so operators can discover and edit them.
            saveConfig();
        } catch (JsonSyntaxException e) {
            logger.error("Config file contains invalid JSON — backing up and recreating. Error: {}", e.getMessage());
            // Back up the broken file so the operator can recover their token/IDs
            try {
                File backup = new File(CONFIG_FILE + ".broken");
                Files.copy(configFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Broken config backed up to {}", backup.getName());
            } catch (IOException backupErr) {
                logger.error("Failed to back up broken config: {}", backupErr.getMessage());
            }
            config = new BotConfig();
            saveConfig();
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            config = new BotConfig();
        }
    }

    public void saveConfig() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            gson.toJson(config, writer);
            logger.info("Configuration saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            logger.error("Failed to save configuration file", e);
        }
    }

    public BotConfig getConfig() {
        return config;
    }

    public void reloadConfig() {
        logger.info("Reloading configuration...");
        loadConfig();
    }
}
