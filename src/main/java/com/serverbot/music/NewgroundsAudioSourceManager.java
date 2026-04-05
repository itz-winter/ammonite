package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Audio source manager for Newgrounds audio tracks.
 * Handles URLs matching: https://www.newgrounds.com/audio/listen/{id}
 *
 * Newgrounds embeds audio metadata in JavaScript on the page. This source manager
 * scrapes the page HTML to extract the MP3 URL, title, artist, and duration,
 * then creates an AudioTrack that LavaPlayer can play.
 */
public class NewgroundsAudioSourceManager implements AudioSourceManager {

    private static final Logger logger = LoggerFactory.getLogger(NewgroundsAudioSourceManager.class);
    private static final String SOURCE_NAME = "newgrounds";

    private final HttpInterfaceManager httpInterfaceManager;

    public NewgroundsAudioSourceManager() {
        this.httpInterfaceManager = HttpClientTools.createDefaultThreadLocalManager();
    }

    /**
     * Get an HTTP interface for making requests.
     * Used by NewgroundsAudioTrack to stream audio.
     */
    public HttpInterface getHttpInterface() {
        return httpInterfaceManager.getInterface();
    }

    // Matches: https://www.newgrounds.com/audio/listen/489111
    // Also handles http:// and optional trailing slash
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://(?:www\\.)?newgrounds\\.com/audio/listen/(\\d+)/?(?:\\?.*)?$"
    );

    // The embedded JS on NG pages contains a JSON blob with the MP3 URL.
    // Look for the "filename" field which holds the CDN URL like:
    //   "filename":"https:\/\/audio.ngfiles.com\/489000\/489111_Nuke-Powder.mp3"
    // It may also appear URL-encoded or escaped.
    private static final Pattern FILENAME_PATTERN = Pattern.compile(
            "\"filename\"\\s*:\\s*\"(https?:[^\"]+\\.mp3[^\"]*)\"",
            Pattern.CASE_INSENSITIVE
    );

    // Fallback: extract from the embedded player params.
    // Some pages use "url" instead of "filename"
    private static final Pattern URL_FIELD_PATTERN = Pattern.compile(
            "\"url\"\\s*:\\s*\"(https?://audio\\.ngfiles\\.com/[^\"]+)\"",
            Pattern.CASE_INSENSITIVE
    );

    // Title: <title>Nuke Powder by MaelouX - Newgrounds</title>
    // or from og:title meta tag
    private static final Pattern TITLE_PATTERN = Pattern.compile(
            "<title>\\s*(.+?)\\s*(?:-\\s*Newgrounds)?\\s*</title>",
            Pattern.CASE_INSENSITIVE
    );

    // og:title for cleaner data: <meta property="og:title" content="Nuke Powder" />
    private static final Pattern OG_TITLE_PATTERN = Pattern.compile(
            "<meta\\s+property=[\"']og:title[\"']\\s+content=[\"']([^\"']+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    // og:site_name or artist from the page: artist link like href="https://maeloux.newgrounds.com/"
    // The page has: <a href="https://USERNAME.newgrounds.com/" ...>USERNAME</a>
    private static final Pattern ARTIST_PATTERN = Pattern.compile(
            "<a\\s+href=[\"']https?://([^.]+)\\.newgrounds\\.com/[\"'][^>]*>\\s*\\1\\s*</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Fallback artist from the <title> tag: "Title by Artist"
    private static final Pattern TITLE_ARTIST_PATTERN = Pattern.compile(
            "^(.+?)\\s+by\\s+(.+?)$"
    );

    // Duration from the page metadata: "3 min 9 sec" or data attributes
    // Look for the embedded duration in the JS params: "duration":189
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "\"duration\"\\s*:\\s*(\\d+(?:\\.\\d+)?)"
    );

    @Override
    public String getSourceName() {
        return SOURCE_NAME;
    }

    @Override
    public AudioItem loadItem(AudioPlayerManager manager, AudioReference reference) {
        String url = reference.identifier;
        Matcher matcher = URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            return null; // Not a Newgrounds URL, let other sources handle it
        }

        String audioId = matcher.group(1);
        logger.info("Loading Newgrounds audio: id={}", audioId);

        try {
            return loadTrack(audioId, url);
        } catch (Exception e) {
            logger.error("Failed to load Newgrounds track {}: {}", audioId, e.getMessage(), e);
            throw new FriendlyException(
                    "Failed to load Newgrounds audio: " + e.getMessage(),
                    FriendlyException.Severity.SUSPICIOUS, e
            );
        }
    }

    private AudioTrack loadTrack(String audioId, String pageUrl) throws IOException {
        String html = fetchPage(pageUrl);

        // Extract the MP3 URL from embedded JavaScript
        String mp3Url = extractMp3Url(html, audioId);
        if (mp3Url == null) {
            throw new FriendlyException(
                    "Could not find audio stream URL for Newgrounds track " + audioId,
                    FriendlyException.Severity.COMMON, null
            );
        }

        // Extract metadata
        String title = extractTitle(html);
        String artist = extractArtist(html, title);
        long duration = extractDuration(html);

        // Clean up title if it contains "by Artist"
        if (title != null && artist != null) {
            Matcher titleArtist = TITLE_ARTIST_PATTERN.matcher(title);
            if (titleArtist.matches()) {
                title = titleArtist.group(1).trim();
            }
        }

        if (title == null || title.isEmpty()) {
            title = "Newgrounds Audio " + audioId;
        }
        if (artist == null || artist.isEmpty()) {
            artist = "Unknown Artist";
        }

        AudioTrackInfo info = new AudioTrackInfo(
                title,
                artist,
                duration, // duration in milliseconds, 0 if unknown (will be detected on play)
                audioId,  // identifier
                false,    // not a stream
                pageUrl   // URI for display
        );

        logger.info("Loaded Newgrounds track: '{}' by '{}' ({}ms) -> {}", title, artist, duration, mp3Url);
        return new NewgroundsAudioTrack(info, mp3Url, this);
    }

    /**
     * Fetch the raw HTML of a page.
     */
    private String fetchPage(String pageUrl) throws IOException {
        URL url = new URL(pageUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("Newgrounds returned HTTP " + status + " for " + pageUrl);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Extract the MP3 URL from the page HTML.
     * Newgrounds embeds audio data in a JavaScript call on the page.
     */
    private String extractMp3Url(String html, String audioId) {
        // Try the "filename" field first (most common)
        Matcher m = FILENAME_PATTERN.matcher(html);
        if (m.find()) {
            String mp3Url = m.group(1)
                    .replace("\\/", "/")  // Unescape JSON forward slashes
                    .replace("\\u002F", "/");
            logger.debug("Found MP3 URL via 'filename' field: {}", mp3Url);
            return mp3Url;
        }

        // Try the "url" field as fallback
        m = URL_FIELD_PATTERN.matcher(html);
        if (m.find()) {
            String mp3Url = m.group(1).replace("\\/", "/");
            logger.debug("Found MP3 URL via 'url' field: {}", mp3Url);
            return mp3Url;
        }

        // Last resort: use the download redirect URL.
        // This does a 302 redirect to the actual MP3. LavaPlayer's HTTP source
        // follows redirects, so this works as a playable URL.
        String downloadUrl = "https://www.newgrounds.com/audio/download/" + audioId;
        logger.debug("Using download redirect URL as fallback: {}", downloadUrl);
        return downloadUrl;
    }

    /**
     * Extract the track title from the page.
     */
    private String extractTitle(String html) {
        // Try og:title first (cleanest)
        Matcher m = OG_TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return unescapeHtml(m.group(1).trim());
        }

        // Fall back to <title> tag
        m = TITLE_PATTERN.matcher(html);
        if (m.find()) {
            return unescapeHtml(m.group(1).trim());
        }

        return null;
    }

    /**
     * Extract the artist name from the page.
     */
    private String extractArtist(String html, String title) {
        // Try extracting from <title> "Title by Artist" pattern
        if (title != null) {
            // The <title> tag format is typically: "Title by Artist - Newgrounds"
            // We already stripped " - Newgrounds" in TITLE_PATTERN, so check for "by"
            // But og:title doesn't have "by Artist", so check the full title tag
            Matcher titleTag = Pattern.compile(
                    "<title>\\s*(.+?)\\s*-\\s*Newgrounds\\s*</title>",
                    Pattern.CASE_INSENSITIVE
            ).matcher(html);
            if (titleTag.find()) {
                String fullTitle = titleTag.group(1).trim();
                Matcher byPattern = TITLE_ARTIST_PATTERN.matcher(fullTitle);
                if (byPattern.matches()) {
                    return unescapeHtml(byPattern.group(2).trim());
                }
            }
        }

        // Try the artist link pattern
        Matcher m = ARTIST_PATTERN.matcher(html);
        if (m.find()) {
            return unescapeHtml(m.group(1).trim());
        }

        return null;
    }

    /**
     * Extract the duration in milliseconds from the embedded JS data.
     */
    private long extractDuration(String html) {
        Matcher m = DURATION_PATTERN.matcher(html);
        if (m.find()) {
            try {
                double seconds = Double.parseDouble(m.group(1));
                return (long) (seconds * 1000);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        // Try to extract from "X min Y sec" text format
        Matcher textDuration = Pattern.compile("(\\d+)\\s*min\\s*(\\d+)\\s*sec").matcher(html);
        if (textDuration.find()) {
            int minutes = Integer.parseInt(textDuration.group(1));
            int seconds = Integer.parseInt(textDuration.group(2));
            return (minutes * 60L + seconds) * 1000L;
        }

        return 0; // Unknown duration — LavaPlayer will detect it from the stream
    }

    /**
     * Basic HTML entity unescaping.
     */
    private String unescapeHtml(String text) {
        if (text == null) return null;
        return text
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'");
    }

    @Override
    public boolean isTrackEncodable(AudioTrack track) {
        return true;
    }

    @Override
    public void encodeTrack(AudioTrack track, DataOutput output) throws IOException {
        // Encode the MP3 URL so it can be reconstructed without re-scraping the page
        NewgroundsAudioTrack ngTrack = (NewgroundsAudioTrack) track;
        output.writeUTF(ngTrack.getMp3Url());
    }

    @Override
    public AudioTrack decodeTrack(AudioTrackInfo trackInfo, DataInput input) throws IOException {
        String mp3Url = input.readUTF();
        return new NewgroundsAudioTrack(trackInfo, mp3Url, this);
    }

    @Override
    public void shutdown() {
        try {
            httpInterfaceManager.close();
        } catch (Exception e) {
            logger.warn("Error closing HTTP interface manager: {}", e.getMessage());
        }
    }
}
