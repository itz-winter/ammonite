package com.serverbot.utils;

import com.google.gson.*;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Utilities for parsing Discord embed JSON → EmbedBuilder,
 * serialising an EmbedGuiSession back to JSON, and building
 * user-defined buttons from session or a JSON array.
 *
 * Accepted embed JSON schema (all fields optional):
 * {
 * "title": "...",
 * "url": "https://...",
 * "description": "...",
 * "color": "#5865F2" | 5793266,
 * "timestamp": true | "2024-01-01T00:00:00Z",
 * "author": { "name": "...", "icon_url": "...", "url": "..." },
 * "footer": { "text": "...", "icon_url": "..." },
 * "image": "https://..." | { "url": "..." },
 * "thumbnail": "https://..." | { "url": "..." },
 * "fields": [
 * { "name": "...", "value": "...", "inline": false }
 * ]
 * }
 *
 * Accepted button JSON schema (array):
 * [
 * { "label": "...", "style": "primary|secondary|success|danger", "id":
 * "custom_id" },
 * { "label": "...", "style": "link", "url": "https://..." }
 * ]
 */
public final class EmbedJsonUtils {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private EmbedJsonUtils() {
    }

    // Embed JSON → EmbedBuilder

    /**
     * Parse the raw JSON string and return the root JsonObject.
     * Accepts a leading '{' at any offset (strips label lines from /embedgui
     * export).
     */
    private static JsonObject parseRootObject(String json) {
        String trimmed = json.strip();
        int start = trimmed.indexOf('{');
        if (start > 0)
            trimmed = trimmed.substring(start);
        try {
            return JsonParser.parseString(trimmed).getAsJsonObject();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage());
        }
    }

    /**
     * Parse a raw JSON string into a populated EmbedBuilder.
     * Accepts either a bare embed object OR a full message payload
     * ({ "embeds": [...], "components": [...] }) — in the latter case embeds[0] is
     * used.
     * Use {@link #parseButtonsFromJson(String)} to extract buttons from the same
     * JSON.
     */
    public static EmbedBuilder parseEmbed(String json) {
        JsonObject root = parseRootObject(json);
        // Unwrap message-payload format: { "embeds": [ {...} ], ... }
        if (root.has("embeds") && root.get("embeds").isJsonArray()) {
            JsonArray embeds = root.getAsJsonArray("embeds");
            if (!embeds.isEmpty() && embeds.get(0).isJsonObject()) {
                return buildEmbedFromObject(embeds.get(0).getAsJsonObject());
            }
        }
        return buildEmbedFromObject(root);
    }

    /**
     * Extract buttons from the JSON string.
     * Accepts two formats:
     * - Legacy "buttons": [ {...}, ... ] (simple flat array used by /embedgui
     * sessions)
     * - Discord API "components": [ { "type":1, "components": [ {"type":2, ...} ] }
     * ]
     * Returns an empty list if neither key is present or the JSON has no buttons.
     */
    public static List<Button> parseButtonsFromJson(String json) {
        JsonObject obj = parseRootObject(json);
        List<Button> result = new ArrayList<>();

        // Discord API format: "components" array of action rows
        if (obj.has("components") && obj.get("components").isJsonArray()) {
            for (JsonElement rowEl : obj.getAsJsonArray("components")) {
                if (!rowEl.isJsonObject())
                    continue;
                JsonObject row = rowEl.getAsJsonObject();
                // type 1 = ActionRow
                if (row.has("type") && row.get("type").getAsInt() != 1)
                    continue;
                if (!row.has("components") || !row.get("components").isJsonArray())
                    continue;
                for (JsonElement compEl : row.getAsJsonArray("components")) {
                    if (!compEl.isJsonObject() || result.size() >= 25)
                        continue;
                    JsonObject comp = compEl.getAsJsonObject();
                    // type 2 = Button
                    if (comp.has("type") && comp.get("type").getAsInt() != 2)
                        continue;
                    Button btn = parseButtonObjectApiFormat(comp);
                    if (btn != null)
                        result.add(btn);
                }
            }
            if (!result.isEmpty())
                return result;
        }

        // Legacy format: "buttons" flat array
        if (!obj.has("buttons") || !obj.get("buttons").isJsonArray())
            return result;
        for (JsonElement el : obj.getAsJsonArray("buttons")) {
            if (!el.isJsonObject() || result.size() >= 25)
                continue;
            Button btn = parseButtonObject(el.getAsJsonObject());
            if (btn != null)
                result.add(btn);
        }
        return result;
    }

    /**
     * Parse a button component in Discord API format:
     * { "type":2, "style":1-5, "label":"...", "custom_id":"...", "url":"...",
     * "emoji":{"name":"...","id":"..."}, "disabled":false }
     */
    private static Button parseButtonObjectApiFormat(JsonObject obj) {
        int style = obj.has("style") ? obj.get("style").getAsInt() : 1;
        String label = obj.has("label") ? obj.get("label").getAsString() : "";
        String url = obj.has("url") ? obj.get("url").getAsString() : null;
        String customId = obj.has("custom_id") ? obj.get("custom_id").getAsString() : null;
        boolean disabled = obj.has("disabled") && obj.get("disabled").getAsBoolean();

        // Emoji — unicode or custom
        net.dv8tion.jda.api.entities.emoji.Emoji emoji = null;
        if (obj.has("emoji") && obj.get("emoji").isJsonObject()) {
            JsonObject em = obj.getAsJsonObject("emoji");
            String emojiName = em.has("name") ? em.get("name").getAsString() : null;
            String emojiId = em.has("id") ? em.get("id").getAsString() : null;
            if (emojiId != null && emojiName != null) {
                boolean animated = em.has("animated") && em.get("animated").getAsBoolean();
                emoji = net.dv8tion.jda.api.entities.emoji.Emoji.fromCustom(emojiName, Long.parseLong(emojiId),
                        animated);
            } else if (emojiName != null) {
                try {
                    emoji = net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode(emojiName);
                } catch (Exception ignored) {
                }
            }
        }

        if (label.isBlank() && emoji == null)
            return null;
        if (customId == null && style != 5)
            customId = "btn_" + java.util.UUID.randomUUID().toString().substring(0, 8);

        Button btn = switch (style) {
            case 1 -> Button.primary(customId, label.isBlank() ? "\u200b" : label);
            case 3 -> Button.success(customId, label.isBlank() ? "\u200b" : label);
            case 4 -> Button.danger(customId, label.isBlank() ? "\u200b" : label);
            case 5 -> url != null ? Button.link(url, label.isBlank() ? "\u200b" : label) : null;
            default -> Button.secondary(customId, label.isBlank() ? "\u200b" : label);
        };
        if (btn == null)
            return null;
        if (emoji != null)
            btn = btn.withEmoji(emoji);
        if (disabled)
            btn = btn.asDisabled();
        return btn;
    }

    private static EmbedBuilder buildEmbedFromObject(JsonObject obj) {
        EmbedBuilder eb = new EmbedBuilder();

        // title + url
        String title = getString(obj, "title");
        String titleUrl = getString(obj, "url");
        if (title != null) {
            eb.setTitle(title, titleUrl);
        }

        // description (support \\n escape)
        String desc = getString(obj, "description");
        if (desc != null) {
            eb.setDescription(desc.replace("\\n", "\n"));
        }

        // color
        String colorStr = getString(obj, "color");
        if (colorStr == null && obj.has("color") && obj.get("color").isJsonPrimitive()
                && obj.get("color").getAsJsonPrimitive().isNumber()) {
            eb.setColor(new Color(obj.get("color").getAsInt()));
        } else if (colorStr != null) {
            eb.setColor(parseColor(colorStr));
        }

        // timestamp
        if (obj.has("timestamp")) {
            JsonElement ts = obj.get("timestamp");
            if (ts.isJsonPrimitive()) {
                JsonPrimitive tsp = ts.getAsJsonPrimitive();
                if (tsp.isBoolean() && tsp.getAsBoolean()) {
                    eb.setTimestamp(Instant.now());
                } else if (tsp.isString()) {
                    try {
                        eb.setTimestamp(Instant.parse(tsp.getAsString()));
                    } catch (Exception ignored) {
                        eb.setTimestamp(Instant.now());
                    }
                }
            }
        }

        // author
        if (obj.has("author") && obj.get("author").isJsonObject()) {
            JsonObject author = obj.getAsJsonObject("author");
            String name = getString(author, "name");
            String iconUrl = getString(author, "icon_url");
            String url = getString(author, "url");
            if (name != null)
                eb.setAuthor(name, url, iconUrl);
        }

        // footer
        if (obj.has("footer") && obj.get("footer").isJsonObject()) {
            JsonObject footer = obj.getAsJsonObject("footer");
            String text = getString(footer, "text");
            String iconUrl = getString(footer, "icon_url");
            if (text != null)
                eb.setFooter(text, iconUrl);
        }

        // image (string or object)
        String imageUrl = resolveUrlField(obj, "image");
        if (imageUrl != null)
            eb.setImage(imageUrl);

        // thumbnail (string or object)
        String thumbUrl = resolveUrlField(obj, "thumbnail");
        if (thumbUrl != null)
            eb.setThumbnail(thumbUrl);

        // fields (skip "buttons" — that's our custom extension key)
        if (obj.has("fields") && obj.get("fields").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("fields")) {
                if (!el.isJsonObject())
                    continue;
                JsonObject field = el.getAsJsonObject();
                String name = getString(field, "name");
                String value = getString(field, "value");
                boolean inline = field.has("inline") && field.get("inline").getAsBoolean();
                if (name != null && value != null) {
                    eb.addField(name, value, inline);
                }
            }
        }

        return eb;
    }

    // Button JSON → List<Button>

    /**
     * Parse a standalone button JSON array string into a list of JDA Buttons.
     * Max 25 buttons. Skips malformed entries.
     */
    public static List<Button> parseButtons(String json) {
        List<Button> result = new ArrayList<>();
        JsonArray arr;
        try {
            String trimmed = json.strip();
            int start = trimmed.indexOf('[');
            if (start > 0)
                trimmed = trimmed.substring(start);
            arr = JsonParser.parseString(trimmed).getAsJsonArray();
        } catch (JsonSyntaxException | IllegalStateException e) {
            throw new IllegalArgumentException("Buttons must be a JSON array: " + e.getMessage());
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject() || result.size() >= 25)
                continue;
            Button btn = parseButtonObject(el.getAsJsonObject());
            if (btn != null)
                result.add(btn);
        }
        return result;
    }

    // EmbedGuiSession → EmbedBuilder / Buttons

    /**
     * Build a JDA EmbedBuilder from a GUI session's current state.
     * Only sets fields that are non-null / enabled.
     */
    public static EmbedBuilder buildEmbed(EmbedGuiSession s) {
        EmbedBuilder eb = new EmbedBuilder();

        if (s.title != null)
            eb.setTitle(s.title, s.titleUrl);
        if (s.description != null)
            eb.setDescription(s.description.replace("\\n", "\n"));
        if (s.colorHex != null) {
            try {
                eb.setColor(parseColor(s.colorHex));
            } catch (Exception ignored) {
            }
        }
        if (s.timestamp)
            eb.setTimestamp(Instant.now());
        if (s.authorName != null)
            eb.setAuthor(s.authorName, s.authorUrl, s.authorIconUrl);
        if (s.footerText != null)
            eb.setFooter(s.footerText, s.footerIconUrl);
        if (s.imageUrl != null)
            eb.setImage(s.imageUrl);
        if (s.thumbnailUrl != null)
            eb.setThumbnail(s.thumbnailUrl);

        for (EmbedGuiSession.FieldEntry f : s.fields) {
            eb.addField(f.name, f.value, f.inline);
        }
        return eb;
    }

    /**
     * Build JDA Buttons from the session's button list.
     */
    public static List<Button> buildUserButtons(EmbedGuiSession s) {
        List<Button> result = new ArrayList<>();
        for (EmbedGuiSession.ButtonEntry be : s.buttons) {
            Button btn = buildButton(be.label, be.style, be.customIdOrUrl);
            if (btn != null)
                result.add(btn);
        }
        return result;
    }

    // EmbedGuiSession → JSON string

    /**
     * Serialise the current session state to a JSON string compatible with /embed.
     */
    public static String toJson(EmbedGuiSession s) {
        JsonObject obj = new JsonObject();

        if (s.title != null)
            obj.addProperty("title", s.title);
        if (s.titleUrl != null)
            obj.addProperty("url", s.titleUrl);
        if (s.description != null)
            obj.addProperty("description", s.description);
        if (s.colorHex != null)
            obj.addProperty("color", s.colorHex);
        if (s.timestamp)
            obj.addProperty("timestamp", true);

        if (s.authorName != null) {
            JsonObject author = new JsonObject();
            author.addProperty("name", s.authorName);
            if (s.authorIconUrl != null)
                author.addProperty("icon_url", s.authorIconUrl);
            if (s.authorUrl != null)
                author.addProperty("url", s.authorUrl);
            obj.add("author", author);
        }

        if (s.footerText != null) {
            JsonObject footer = new JsonObject();
            footer.addProperty("text", s.footerText);
            if (s.footerIconUrl != null)
                footer.addProperty("icon_url", s.footerIconUrl);
            obj.add("footer", footer);
        }

        if (s.imageUrl != null)
            obj.addProperty("image", s.imageUrl);
        if (s.thumbnailUrl != null)
            obj.addProperty("thumbnail", s.thumbnailUrl);

        if (!s.fields.isEmpty()) {
            JsonArray fields = new JsonArray();
            for (EmbedGuiSession.FieldEntry f : s.fields) {
                JsonObject field = new JsonObject();
                field.addProperty("name", f.name);
                field.addProperty("value", f.value);
                field.addProperty("inline", f.inline);
                fields.add(field);
            }
            obj.add("fields", fields);
        }

        if (!s.buttons.isEmpty()) {
            JsonArray btns = new JsonArray();
            for (EmbedGuiSession.ButtonEntry be : s.buttons) {
                JsonObject o = new JsonObject();
                o.addProperty("label", be.label);
                o.addProperty("style", be.style);
                if (be.style.equals("link"))
                    o.addProperty("url", be.customIdOrUrl);
                else
                    o.addProperty("id", be.customIdOrUrl);
                btns.add(o);
            }
            obj.add("buttons", btns);
        }
        return GSON.toJson(obj);
    }

    /**
     * Build the full export message string shown to the user.
     * A single JSON object (with optional "buttons" key) ready to paste into /embed
     * json:.
     */
    public static String buildExportMessage(EmbedGuiSession s) {
        return "**`/embed json:`**\n```json\n" + toJson(s) + "\n```";
    }

    /**
     * Serialise session buttons to a JSON array string.
     */
    public static String buttonsToJson(EmbedGuiSession s) {
        JsonArray arr = new JsonArray();
        for (EmbedGuiSession.ButtonEntry be : s.buttons) {
            JsonObject o = new JsonObject();
            o.addProperty("label", be.label);
            o.addProperty("style", be.style);
            if (be.style.equals("link")) {
                o.addProperty("url", be.customIdOrUrl);
            } else {
                o.addProperty("id", be.customIdOrUrl);
            }
            arr.add(o);
        }
        return GSON.toJson(arr);
    }

    // Helpers

    public static Color parseColor(String hex) {
        if (hex == null || hex.isBlank())
            return new Color(0x5865F2);
        hex = hex.trim();
        if (!hex.startsWith("#"))
            hex = "#" + hex;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex color: " + hex + " — use format #RRGGBB");
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (!obj.has(key))
            return null;
        JsonElement el = obj.get(key);
        if (!el.isJsonPrimitive())
            return null;
        String v = el.getAsString().trim();
        return v.isEmpty() ? null : v;
    }

    private static String resolveUrlField(JsonObject obj, String key) {
        if (!obj.has(key))
            return null;
        JsonElement el = obj.get(key);
        if (el.isJsonPrimitive()) {
            String v = el.getAsString().trim();
            return v.isEmpty() ? null : v;
        }
        if (el.isJsonObject()) {
            return getString(el.getAsJsonObject(), "url");
        }
        return null;
    }

    private static Button parseButtonObject(JsonObject o) {
        String label = getString(o, "label");
        String style = getString(o, "style");
        if (label == null || style == null)
            return null;
        String idOrUrl = getString(o, "id");
        String url = getString(o, "url");
        return buildButton(label, style, idOrUrl != null ? idOrUrl : url);
    }

    private static Button buildButton(String label, String style, String idOrUrl) {
        if (label == null || style == null || idOrUrl == null)
            return null;
        return switch (style.toLowerCase()) {
            case "primary" -> Button.primary(idOrUrl, label);
            case "secondary" -> Button.secondary(idOrUrl, label);
            case "success" -> Button.success(idOrUrl, label);
            case "danger" -> Button.danger(idOrUrl, label);
            case "link" -> Button.link(idOrUrl, label);
            default -> Button.secondary(idOrUrl, label);
        };
    }
}
