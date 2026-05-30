package com.serverbot.database;

import com.serverbot.ServerBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages database connections and initialization
 */
public class DatabaseManager {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private Connection connection;
    private String databasePath;

    public void initialize() {
        this.databasePath = ServerBot.getConfigManager().getConfig().getDatabasePath();

        // Create data directory if it doesn't exist
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        try {
            // Initialize SQLite connection
            String url = "jdbc:sqlite:" + databasePath;
            connection = DriverManager.getConnection(url);
            logger.info("Database connection established: {}", databasePath);

            // Create tables
            createTables();

        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Guild settings table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS guild_settings (
                            guild_id TEXT PRIMARY KEY,
                            prefix TEXT DEFAULT '/',
                            mod_log_channel TEXT,
                            punishment_log_channel TEXT,
                            all_log_channel TEXT,
                            autorole_enabled BOOLEAN DEFAULT FALSE,
                            levels_enabled BOOLEAN DEFAULT FALSE,
                            points_enabled BOOLEAN DEFAULT FALSE,
                            automod_enabled BOOLEAN DEFAULT FALSE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // User warnings table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS user_warnings (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            user_id TEXT NOT NULL,
                            moderator_id TEXT NOT NULL,
                            reason TEXT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            active BOOLEAN DEFAULT TRUE
                        )
                    """);

            // User levels table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS user_levels (
                            guild_id TEXT NOT NULL,
                            user_id TEXT NOT NULL,
                            level INTEGER DEFAULT 0,
                            experience INTEGER DEFAULT 0,
                            messages_sent INTEGER DEFAULT 0,
                            last_message TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (guild_id, user_id)
                        )
                    """);

            // User economy table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS user_economy (
                            guild_id TEXT NOT NULL,
                            user_id TEXT NOT NULL,
                            balance INTEGER DEFAULT 0,
                            bank_balance INTEGER DEFAULT 0,
                            total_earned INTEGER DEFAULT 0,
                            total_spent INTEGER DEFAULT 0,
                            daily_streak INTEGER DEFAULT 0,
                            last_daily TIMESTAMP,
                            PRIMARY KEY (guild_id, user_id)
                        )
                    """);

            // Automod settings table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS automod_settings (
                            guild_id TEXT PRIMARY KEY,
                            caps_filter BOOLEAN DEFAULT FALSE,
                            slurs_filter BOOLEAN DEFAULT FALSE,
                            swears_filter BOOLEAN DEFAULT FALSE,
                            spam_filter BOOLEAN DEFAULT FALSE,
                            warn_on_caps BOOLEAN DEFAULT TRUE,
                            warn_on_slurs BOOLEAN DEFAULT TRUE,
                            warn_on_swears BOOLEAN DEFAULT TRUE,
                            warn_on_spam BOOLEAN DEFAULT TRUE,
                            dm_punished_users BOOLEAN DEFAULT FALSE
                        )
                    """);

            // Reaction roles table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS reaction_roles (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            message_id TEXT NOT NULL,
                            channel_id TEXT NOT NULL,
                            emoji TEXT NOT NULL,
                            role_id TEXT NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Server rules table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS server_rules (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            title TEXT NOT NULL,
                            rules TEXT NOT NULL,
                            position INTEGER DEFAULT 0,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Level roles table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS level_roles (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            role_id TEXT NOT NULL,
                            required_level INTEGER NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Autoroles table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS autoroles (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            role_id TEXT NOT NULL,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Temporary punishments table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS temp_punishments (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            user_id TEXT NOT NULL,
                            punishment_type TEXT NOT NULL,
                            expires_at TIMESTAMP NOT NULL,
                            moderator_id TEXT NOT NULL,
                            reason TEXT,
                            active BOOLEAN DEFAULT TRUE,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            // Transaction log table
            stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS transaction_log (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            guild_id TEXT NOT NULL,
                            user_id TEXT NOT NULL,
                            transaction_type TEXT NOT NULL,
                            amount INTEGER NOT NULL,
                            balance_after INTEGER NOT NULL,
                            description TEXT,
                            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);

            logger.info("Database tables created successfully");
        }
    }

    public Connection getConnection() {
        try {
            // Check if connection is still valid
            if (connection == null || connection.isClosed()) {
                String url = "jdbc:sqlite:" + databasePath;
                connection = DriverManager.getConnection(url);
            }
            return connection;
        } catch (SQLException e) {
            logger.error("Failed to get database connection", e);
            throw new RuntimeException("Database connection failed", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
        }
    }
}
