package com.serverbot.listeners;

import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.events.session.SessionResumeEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors connection events to prevent excessive reconnections
 */
public class ConnectionListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionListener.class);

    // Connection monitoring
    private final AtomicInteger reconnectCount = new AtomicInteger(0);
    private final AtomicLong lastReconnect = new AtomicLong(0);
    private static final long RECONNECT_RESET_TIME = 300000; // 5 minutes
    private static final int MAX_RECONNECTS_PER_PERIOD = 5;

    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Bot connected and ready! User: {}", event.getJDA().getSelfUser().getName());

        // Reset reconnect counter on successful connection
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastReconnect.get() > RECONNECT_RESET_TIME) {
            reconnectCount.set(0);
        }
    }

    @Override
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        logger.warn("Session disconnected: {} (Code: {}, By server: {})",
                event.getCloseCode() != null ? event.getCloseCode().getMeaning() : "Unknown",
                event.getCloseCode() != null ? event.getCloseCode().getCode() : "N/A",
                event.isClosedByServer());

        // Track reconnection attempts
        long currentTime = System.currentTimeMillis();
        int currentCount = reconnectCount.incrementAndGet();
        lastReconnect.set(currentTime);

        if (currentCount > MAX_RECONNECTS_PER_PERIOD) {
            logger.error("Too many reconnection attempts ({}) in the last {} seconds. " +
                    "This may indicate a connection loop. Check bot token and network connectivity.",
                    currentCount, RECONNECT_RESET_TIME / 1000);
        }
    }

    @Override
    public void onSessionResume(SessionResumeEvent event) {
        logger.info("Session resumed successfully");
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        logger.info("Session recreated successfully");
    }
}
