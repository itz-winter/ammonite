package com.serverbot.services;

import com.serverbot.storage.FileStorageManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically (every 20 minutes) checks the suspicious-user masterlist for
 * entries that correspond to deleted or suspended Discord accounts (HTTP 10013
 * Unknown User) and removes them automatically.
 *
 * <p>All Discord API requests are submitted via JDA's async queue so JDA's
 * built-in rate-limit handling is respected; nothing blocks the main thread.
 *
 * <p>A manual on-demand scan can be triggered via {@link #runScan()}, which
 * returns a {@link CompletableFuture}{@code <}{@link ScanResult}{@code >} that
 * completes when every pending API request has returned.
 */
public class SuspiciousCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(SuspiciousCleanupService.class);

    /** How often the automatic cleanup runs (in minutes). */
    private static final long INTERVAL_MINUTES = 20;

    /** JDA error code for "Unknown User" (deleted / suspended account). */
    private static final int UNKNOWN_USER = ErrorResponse.UNKNOWN_USER.getCode();

    private final JDA jda;
    private final FileStorageManager storage;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "suspicious-cleanup");
                t.setDaemon(true);
                return t;
            });

    /** Guard so only one scan runs at a time. */
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    /** The future that will complete when the current auto-scan finishes (null when idle). */
    private volatile CompletableFuture<ScanResult> currentScan = null;

    //  Result record 

    public record ScanResult(int checked, int removed, int failed) {
        /** Human-readable summary line. */
        public String summary() {
            return String.format(
                    "Checked **%d** user(s) — removed **%d** deleted account(s)%s.",
                    checked, removed,
                    failed > 0 ? " (⚠️ " + failed + " request(s) failed)" : "");
        }
    }

    //  Lifecycle 

    public SuspiciousCleanupService(JDA jda, FileStorageManager storage) {
        this.jda = jda;
        this.storage = storage;
    }

    /**
     * Starts the 20-minute recurring auto-scan.
     * The first scan is delayed by 30 seconds to let JDA finish initialising.
     */
    public void start() {
        scheduler.scheduleAtFixedRate(
                this::autoScan,
                30, INTERVAL_MINUTES * 60, TimeUnit.SECONDS);
        logger.info("SuspiciousCleanupService started — scanning every {} minutes.", INTERVAL_MINUTES);
    }

    /** Stops the scheduler cleanly. */
    public void stop() {
        scheduler.shutdownNow();
        logger.info("SuspiciousCleanupService stopped.");
    }

    //  Public API 

    /**
     * Triggers an on-demand scan immediately (regardless of the schedule).
     * If a scan is already in progress the existing future is returned instead
     * of starting a duplicate.
     *
     * @return a {@link CompletableFuture} that completes with the {@link ScanResult}
     *         when every API request has settled.
     */
    public CompletableFuture<ScanResult> runScan() {
        if (!scanInProgress.compareAndSet(false, true)) {
            // Already running — return the in-progress future so the caller can wait on it
            CompletableFuture<ScanResult> inProgress = currentScan;
            if (inProgress != null) return inProgress;
            // Rare race where currentScan was just cleared — start a fresh one
        }

        CompletableFuture<ScanResult> future = new CompletableFuture<>();
        currentScan = future;
        doScan(future);
        return future;
    }

    //  Internal 

    /** Wrapper called by the scheduler — swallows exceptions so the schedule survives. */
    private void autoScan() {
        try {
            CompletableFuture<ScanResult> f = runScan();
            f.whenComplete((result, ex) -> {
                if (ex != null) {
                    logger.error("Auto suspicious-list cleanup failed", ex);
                } else {
                    logger.info("[auto-scan] {}", result.summary());
                }
            });
        } catch (Exception e) {
            logger.error("Unexpected error starting auto suspicious-list cleanup", e);
        }
    }

    /**
     * The actual scan logic.
     * <p>
     * Snapshot the user-ID list, then fire off one async
     * {@code retrieveUserById} per entry. A shared {@link AtomicInteger} counts
     * down to zero; when it reaches zero the future is completed.
     */
    private void doScan(CompletableFuture<ScanResult> future) {
        Map<String, Map<String, Object>> allUsers = storage.getAllSuspiciousUsers();
        List<String> userIds = new ArrayList<>(allUsers.keySet());

        if (userIds.isEmpty()) {
            finishScan(future, 0, 0, 0);
            return;
        }

        logger.info("[suspicious-cleanup] Scanning {} masterlist entries for deleted accounts…", userIds.size());

        AtomicInteger remaining = new AtomicInteger(userIds.size());
        AtomicInteger removed  = new AtomicInteger(0);
        AtomicInteger failed   = new AtomicInteger(0);

        for (String userId : userIds) {
            jda.retrieveUserById(userId).queue(
                    // success — user still exists, do nothing
                    user -> {
                        if (remaining.decrementAndGet() == 0) {
                            finishScan(future, userIds.size(), removed.get(), failed.get());
                        }
                    },
                    // failure
                    throwable -> {
                        if (throwable instanceof ErrorResponseException err
                                && err.getErrorCode() == UNKNOWN_USER) {
                            // Account no longer exists on Discord — remove from list
                            storage.removeSuspiciousUser(userId);
                            removed.incrementAndGet();
                            logger.info("[suspicious-cleanup] Removed deleted account {} from masterlist.", userId);
                        } else {
                            // Transient error (rate-limit, network) — leave the entry alone
                            failed.incrementAndGet();
                            logger.debug("[suspicious-cleanup] Non-fatal error for user {}: {}", userId,
                                    throwable.getMessage());
                        }
                        if (remaining.decrementAndGet() == 0) {
                            finishScan(future, userIds.size(), removed.get(), failed.get());
                        }
                    });
        }
    }

    private void finishScan(CompletableFuture<ScanResult> future, int checked, int removed, int failed) {
        scanInProgress.set(false);
        currentScan = null;
        ScanResult result = new ScanResult(checked, removed, failed);
        future.complete(result);
        logger.info("[suspicious-cleanup] Scan complete — {}", result.summary());
    }
}
