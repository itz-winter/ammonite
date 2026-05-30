package com.serverbot;

import com.serverbot.services.ConfigManager;
import com.serverbot.utils.BotConfig;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal diagnostic version to test bot startup
 */
public class DiagnosticBot {

    private static final Logger logger = LoggerFactory.getLogger(DiagnosticBot.class);

    public static void main(String[] args) {
        try {
            logger.info("=== DIAGNOSTIC BOT STARTUP ===");

            logger.info("1. Loading configuration...");
            ConfigManager configManager = new ConfigManager();
            BotConfig config = configManager.getConfig();

            if (config.getBotToken() == null || config.getBotToken().isEmpty()) {
                logger.error("❌ Bot token not found in config!");
                System.exit(1);
            }
            logger.info("✅ Bot token found: {}...", config.getBotToken().substring(0, 10));

            logger.info("2. Creating JDA instance with minimal intents...");
            JDA jda = JDABuilder.createDefault(config.getBotToken())
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .build();

            logger.info("3. Waiting for JDA to be ready...");
            jda.awaitReady();

            logger.info("✅ SUCCESS! Bot is ready and connected!");
            logger.info("Bot user: {}", jda.getSelfUser().getName());
            logger.info("Guilds: {}", jda.getGuilds().size());

            // Keep running for 10 seconds then exit
            logger.info("Running for 10 seconds then exiting...");
            Thread.sleep(10000);

            jda.shutdown();
            logger.info("✅ Bot shutdown successful!");

        } catch (Exception e) {
            logger.error("❌ DIAGNOSTIC FAILED: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
