package com.serverbot.utils;

import net.dv8tion.jda.api.interactions.InteractionHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory session for the /embedgui interactive embed builder.
 * One session per user; expires after 30 minutes of inactivity.
 */
public class EmbedGuiSession {

    // Session store

    private static final Map<String, EmbedGuiSession> SESSIONS = new ConcurrentHashMap<>();
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    public static EmbedGuiSession getOrCreate(String userId, String targetChannelId) {
        cleanup();
        return SESSIONS.computeIfAbsent(userId, id -> {
            EmbedGuiSession s = new EmbedGuiSession();
            s.userId = id;
            s.targetChannelId = targetChannelId;
            return s;
        });
    }

    public static EmbedGuiSession get(String userId) {
        cleanup();
        return SESSIONS.get(userId);
    }

    public static void remove(String userId) {
        SESSIONS.remove(userId);
    }

    private static void cleanup() {
        long now = System.currentTimeMillis();
        SESSIONS.entrySet().removeIf(e -> now - e.getValue().lastActivity > TIMEOUT_MS);
    }

    // Session state

    public String userId;
    public String targetChannelId;

    /** Whether this user has permission to send the built embed to a channel. */
    public boolean canSend = false;

    /** Reference to the original slash-command hook so we can edit the GUI message after modals. */
    public InteractionHook hook;

    /** Message ID of the ephemeral controls follow-up message (has the buttons), so it can be updated. */
    public String controlsMessageId;

    // Embed fields
    public String title;
    public String titleUrl;
    public String description;
    public String colorHex;
    public String authorName;
    public String authorIconUrl;
    public String authorUrl;
    public String footerText;
    public String footerIconUrl;
    public String imageUrl;
    public String thumbnailUrl;
    public boolean timestamp = false;

    public final List<FieldEntry> fields = new ArrayList<>();
    /** Buttons the user wants to attach to the sent embed (not the GUI control buttons). */
    public final List<ButtonEntry> buttons = new ArrayList<>();

    public long lastActivity = System.currentTimeMillis();

    public void touch() {
        lastActivity = System.currentTimeMillis();
    }

    /** Reset all embed content but keep target channel. */
    public void clear() {
        title = null;
        titleUrl = null;
        description = null;
        colorHex = null;
        authorName = null;
        authorIconUrl = null;
        authorUrl = null;
        footerText = null;
        footerIconUrl = null;
        imageUrl = null;
        thumbnailUrl = null;
        timestamp = false;
        fields.clear();
        buttons.clear();
    }

    // Nested types

    public static class FieldEntry {
        public final String name;
        public final String value;
        public final boolean inline;

        public FieldEntry(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
    }

    public static class ButtonEntry {
        public final String label;
        /** primary | secondary | success | danger | link */
        public final String style;
        /** custom_id for non-link buttons, URL for link buttons */
        public final String customIdOrUrl;

        public ButtonEntry(String label, String style, String customIdOrUrl) {
            this.label = label;
            this.style = style.toLowerCase();
            this.customIdOrUrl = customIdOrUrl;
        }
    }
}
