package com.serverbot;

import com.serverbot.storage.FileStorageManager;
import com.serverbot.listeners.CommandListener;
import com.serverbot.listeners.EventListener;
import com.serverbot.listeners.ConnectionListener;
import com.serverbot.listeners.RolePersistenceListener;
import com.serverbot.listeners.ReactionRoleListener;
import com.serverbot.listeners.PrefixCommandListener;
import com.serverbot.listeners.TicketListener;
import com.serverbot.listeners.PunishmentAppealListener;
import com.serverbot.listeners.AppealModalListener;
import com.serverbot.listeners.ProxyListener;
import com.serverbot.listeners.AutoLogListener;
import com.serverbot.listeners.SuspiciousAccountListener;
import com.serverbot.listeners.SuspiciousAccountButtonListener;
import com.serverbot.listeners.VoiceStateListener;
import com.serverbot.listeners.DismissButtonListener;
import com.serverbot.listeners.BlackjackButtonListener;
import com.serverbot.listeners.GlobalChatListener;
import com.serverbot.listeners.GlobalChatButtonListener;
import com.serverbot.listeners.AutoConfigListener;
import com.serverbot.services.CommandManager;
import com.serverbot.services.ConfigManager;
import com.serverbot.services.SchedulerService;
import com.serverbot.services.TicketService;
import com.serverbot.services.ProxyService;
import com.serverbot.services.GlobalChatService;
import com.serverbot.utils.BotConfig;
import com.serverbot.utils.WarnExpiryManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.List;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import moe.kyokobot.libdave.DaveFactory;
import moe.kyokobot.libdave.NativeDaveFactory;
import moe.kyokobot.libdave.jda.LDJDADaveSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main bot class that initializes and starts the Discord bot
 */
public class ServerBot {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerBot.class);
    private static JDA jda;
    private static FileStorageManager storageManager;
    private static CommandManager commandManager;
    private static ConfigManager configManager;
    private static TicketService ticketService;
    private static ProxyService proxyService;
    private static GlobalChatService globalChatService;
    
    public static void main(String[] args) {
        BotConfig config = null;
        
        try {
            logger.info("=== Starting Discord Server Bot ===");
            logger.info("Java Version: " + System.getProperty("java.version"));
            logger.info("Working Directory: " + System.getProperty("user.dir"));
            
            // Initialize configuration
            try {
                configManager = new ConfigManager();
                config = configManager.getConfig();
                logger.info("Configuration loaded successfully");
                
                if (config.getBotToken() == null || config.getBotToken().isEmpty()) {
                    logger.error("Bot token not found! Please set your bot token in the config file.");
                    System.exit(1);
                }
            } catch (Exception e) {
                logger.error("Failed to load configuration", e);
                System.exit(1);
            }
            
            // Initialize file storage
            try {
                storageManager = new FileStorageManager("data");
                logger.info("File storage initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize file storage", e);
                throw e;
            }
            
            // Initialize ticket service
            try {
                ticketService = new TicketService();
                logger.info("Ticket service initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize ticket service", e);
                throw e;
            }
            
            // Initialize proxy service
            try {
                proxyService = new ProxyService();
                logger.info("Proxy service initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize proxy service", e);
                throw e;
            }
            
            // Initialize global chat service
            try {
                globalChatService = new GlobalChatService();
                logger.info("Global chat service initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize global chat service", e);
                throw e;
            }
            
            // Initialize command manager
            try {
                commandManager = new CommandManager();
                logger.info("Command manager initialized successfully");
            } catch (Exception e) {
                logger.error("Failed to initialize command manager", e);
                throw e;
            }
            
            // Initialize DAVE Protocol (Discord Audio & Video E2EE) - Required since March 1, 2026
            DaveFactory daveFactory = new NativeDaveFactory();
            LDJDADaveSessionFactory daveSessionFactory = new LDJDADaveSessionFactory(daveFactory);

            // Build JDA instance
            jda = JDABuilder.createDefault(config.getBotToken())
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .enableCache(CacheFlag.VOICE_STATE)
                    .setAudioModuleConfig(new AudioModuleConfig()
                            .withDaveSessionFactory(daveSessionFactory))
                    .enableIntents(
                            // Required for: role persistence (tracking member join/leave), suspicious account
                            // detection (member screening), welcome messages, and member cache for moderation
                            GatewayIntent.GUILD_MEMBERS,
                            // Required for: global chat relay, auto-moderation, prefix commands, and event logging
                            GatewayIntent.GUILD_MESSAGES,
                            // Required for: proxy tag matching in message content, prefix command parsing,
                            // global chat relay content, and auto-moderation content filtering
                            GatewayIntent.MESSAGE_CONTENT,
                            // Required for: reaction role assignment and removal
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            // Required for: suspicious account detection (account age + status analysis)
                            GatewayIntent.GUILD_PRESENCES,
                            // Required for: punishment appeal DMs and ticket DM notifications
                            GatewayIntent.DIRECT_MESSAGES,
                            // Required for: music bot voice channel connections and tracking
                            GatewayIntent.GUILD_VOICE_STATES
                    )
                    .setActivity(buildActivityFromConfig(config))
                    .setStatus(buildOnlineStatusFromConfig(config))
                    .setAutoReconnect(true)
                    .setRequestTimeoutRetry(true)
                    .setMaxReconnectDelay(32) // Max 32 seconds between reconnect attempts
                    .addEventListeners(
                            new CommandListener(commandManager),
                            new EventListener(),
                            new RolePersistenceListener(),
                            new ReactionRoleListener(),
                            new PrefixCommandListener(commandManager),
                            new TicketListener(),
                            new PunishmentAppealListener(),
                            new AppealModalListener(),
                            new ProxyListener(),
                            new AutoLogListener(),
                            new SuspiciousAccountListener(),
                            new SuspiciousAccountButtonListener(),
                            new ConnectionListener(),
                            new DismissButtonListener(),
                            new VoiceStateListener(),
                            new BlackjackButtonListener(),
                            new GlobalChatListener(),
                            new GlobalChatButtonListener(),
                            new AutoConfigListener(),
                            new com.serverbot.commands.games.PokerCommand(),
                            new com.serverbot.commands.utility.DeleteDataCommand()
                    )
                    .build();
            
            // Wait for JDA to be ready
            logger.info("Waiting for JDA to connect...");
            jda.awaitReady();
            logger.info("✓ JDA connection established!");
            logger.info("✓ Connected as: " + jda.getSelfUser().getAsTag());
            logger.info("✓ Connected to " + jda.getGuilds().size() + " guild(s)");
            
            // Initialize scheduler service with JDA
            try {
                logger.info("Starting scheduler service...");
                SchedulerService.getInstance().setJDA(jda);
                logger.info("✓ Scheduler service started");
            } catch (Exception e) {
                logger.error("Failed to start scheduler service", e);
                // Non-fatal, continue
            }
            
            // Start warning expiry manager
            try {
                logger.info("Starting warning expiry manager...");
                WarnExpiryManager.start();
                logger.info("✓ Warning expiry manager started");
            } catch (Exception e) {
                logger.error("Failed to start warning expiry manager", e);
                // Non-fatal, continue
            }
            
            // Add shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received, cleaning up...");
                shutdown();
            }));
            
            // Register slash commands (only once at startup)
            logger.info("Registering slash commands...");
            try {
                commandManager.updateGlobalCommands(jda);
                logger.info("Slash commands registered successfully!");
            } catch (IllegalArgumentException e) {
                logger.error("Command validation error: " + e.getMessage(), e);
                throw new RuntimeException("Invalid command configuration: " + e.getMessage(), e);
            } catch (Exception e) {
                logger.error("Failed to register commands: " + e.getMessage(), e);
                logger.warn("Bot will continue without slash commands. Commands may need to be registered manually.");
            }
            
            logger.info("==============================================");
            logger.info("✓ Bot successfully started and is ready!");
            logger.info("==============================================");
            logger.info("Commands may take up to 1 hour to appear in Discord due to global command caching.");
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (InterruptedException e) {
            logger.error("Bot startup was interrupted.", e);
            Thread.currentThread().interrupt();
            notifyOwnerOfError("Bot startup was interrupted: " + e.getMessage());
            shutdown();
            System.exit(1);
        } catch (net.dv8tion.jda.api.exceptions.InvalidTokenException e) {
            logger.error("Invalid bot token! Please check your config.json file.");
            notifyOwnerOfError("Invalid bot token - check config.json");
            System.exit(1);
        } catch (IllegalArgumentException e) {
            logger.error("Configuration error: " + e.getMessage(), e);
            notifyOwnerOfError("Configuration error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            logger.error("An unexpected error occurred during bot startup.", e);
            notifyOwnerOfError("Bot startup failed: " + e.getMessage());
            shutdown();
            System.exit(1);
        }
    }
    
    public static JDA getJda() {
        return jda;
    }
    
    public static FileStorageManager getStorageManager() {
        return storageManager;
    }
    
    public static CommandManager getCommandManager() {
        return commandManager;
    }
    
    public static ConfigManager getConfigManager() {
        return configManager;
    }
    
    public static TicketService getTicketService() {
        return ticketService;
    }
    
    public static ProxyService getProxyService() {
        return proxyService;
    }
    
    public static GlobalChatService getGlobalChatService() {
        return globalChatService;
    }
    
    public static Logger getLogger() {
        return logger;
    }
    
    /**
     * Notify the bot owner(s) of a critical error via DM
     */
    private static void notifyOwnerOfError(String errorMessage) {
        try {
            if (jda != null && configManager != null) {
                List<String> ownerIds = configManager.getConfig().getAllOwnerIds();
                
                if (!ownerIds.isEmpty()) {
                    for (String ownerId : ownerIds) {
                        jda.retrieveUserById(ownerId).queue(
                            owner -> {
                                owner.openPrivateChannel().queue(
                                    channel -> {
                                        channel.sendMessage("🚨 **Bot Startup Error** 🚨\n\n" +
                                            "The bot encountered a critical error during startup and has shut down:\n\n" +
                                            "```\n" + errorMessage + "\n```\n\n" +
                                            "Please check the logs and configuration before restarting.")
                                            .queue(
                                                success -> logger.info("Error notification sent to bot owner: " + ownerId),
                                                failure -> logger.warn("Failed to send error notification to " + ownerId + ": " + failure.getMessage())
                                            );
                                    },
                                    failure -> logger.warn("Failed to open DM channel with bot owner " + ownerId + ": " + failure.getMessage())
                                );
                            },
                            failure -> logger.warn("Failed to retrieve bot owner " + ownerId + ": " + failure.getMessage())
                        );
                    }
                    
                    // Wait a moment for the messages to send
                    Thread.sleep(2000);
                } else {
                    logger.warn("Bot owner ID not configured, cannot send error notification");
                }
            }
        } catch (Exception e) {
            logger.warn("Failed to notify owner of error: " + e.getMessage());
        }
    }
    
    /**
     * Build the Activity from config settings.
     * Priority: if default_status_message is set, use custom status.
     * Otherwise, use default_rpc_type + default_rpc_text.
     */
    private static Activity buildActivityFromConfig(BotConfig config) {
        // If a custom status message is set, use it (overrides RPC)
        String statusMessage = config.getDefaultStatusMessage();
        if (statusMessage != null && !statusMessage.isEmpty()) {
            return Activity.customStatus(statusMessage);
        }
        
        // Otherwise build RPC activity from type + text
        String rpcText = config.getDefaultRpcText();
        if (rpcText == null || rpcText.isEmpty()) {
            rpcText = "for commands";
        }
        
        String rpcType = config.getDefaultRpcType();
        if (rpcType == null || rpcType.isEmpty()) {
            rpcType = "watching";
        }
        
        switch (rpcType.toLowerCase()) {
            case "playing":
                return Activity.playing(rpcText);
            case "listening":
                return Activity.listening(rpcText);
            case "streaming":
                return Activity.streaming(rpcText, "https://twitch.tv/placeholder");
            case "competing":
                return Activity.competing(rpcText);
            case "watching":
            default:
                return Activity.watching(rpcText);
        }
    }
    
    /**
     * Build the OnlineStatus from config settings.
     */
    private static OnlineStatus buildOnlineStatusFromConfig(BotConfig config) {
        String status = config.getDefaultOnlineStatus();
        if (status == null || status.isEmpty()) {
            return OnlineStatus.ONLINE;
        }
        
        switch (status.toLowerCase()) {
            case "dnd":
            case "do_not_disturb":
                return OnlineStatus.DO_NOT_DISTURB;
            case "idle":
                return OnlineStatus.IDLE;
            case "invisible":
            case "offline":
                return OnlineStatus.INVISIBLE;
            case "online":
            default:
                return OnlineStatus.ONLINE;
        }
    }
    
    public static void shutdown() {
        logger.info("Shutting down bot...");
        
        // Stop warning expiry manager
        WarnExpiryManager.stop();
        
        // Stop scheduler service
        if (SchedulerService.getInstance() != null) {
            SchedulerService.getInstance().shutdown();
        }
        
        if (storageManager != null) {
            storageManager.close();
        }
        
        if (jda != null) {
            try {
                // Shutdown JDA properly to prevent connection loops
                jda.shutdownNow();
                if (!jda.awaitShutdown(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    logger.warn("JDA shutdown timed out, forcing shutdown");
                }
            } catch (InterruptedException e) {
                logger.warn("JDA shutdown interrupted", e);
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Bot shutdown complete.");
    }
}
