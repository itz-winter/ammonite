package com.serverbot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Manages caching of avatar images from Discord CDN to prevent link expiration
 */
public class AvatarCacheManager {
    private static final Logger logger = LoggerFactory.getLogger(AvatarCacheManager.class);
    private static final Path CACHE_DIR = Paths.get("data", "avatar_cache");

    static {
        try {
            Files.createDirectories(CACHE_DIR);
        } catch (IOException e) {
            logger.error("Failed to create avatar cache directory", e);
        }
    }

    /**
     * Downloads and caches an avatar from a Discord CDN URL
     * 
     * @param cdnUrl The Discord CDN URL
     * @return A file:// URL pointing to the cached image, or the original URL if
     *         caching fails
     */
    public static String cacheAvatar(String cdnUrl) {
        if (cdnUrl == null || cdnUrl.isEmpty()) {
            return null;
        }

        // If it's already a file:// URL, return it as-is
        if (cdnUrl.startsWith("file://")) {
            return cdnUrl;
        }

        // Only cache Discord CDN URLs
        if (!isDiscordCdnUrl(cdnUrl)) {
            return cdnUrl;
        }

        try {
            // Generate a unique filename based on the URL
            String filename = generateFilename(cdnUrl);
            Path cachePath = CACHE_DIR.resolve(filename);

            // If already cached, return the cached version
            if (Files.exists(cachePath)) {
                logger.debug("Avatar already cached: {}", filename);
                return cachePath.toUri().toString();
            }

            // Download the image
            logger.info("Downloading avatar from CDN: {}", cdnUrl);
            URL url = new URL(cdnUrl);
            try (InputStream in = url.openStream()) {
                Files.copy(in, cachePath, StandardCopyOption.REPLACE_EXISTING);
                logger.info("Avatar cached successfully: {}", filename);
                return cachePath.toUri().toString();
            }

        } catch (Exception e) {
            logger.error("Failed to cache avatar from URL: " + cdnUrl, e);
            // Return original URL if caching fails
            return cdnUrl;
        }
    }

    /**
     * Gets the local file path for a cached avatar
     * 
     * @param fileUrl A file:// URL
     * @return The file path, or null if not a valid file URL
     */
    public static String getLocalPath(String fileUrl) {
        if (fileUrl != null && fileUrl.startsWith("file:")) {
            try {
                return Paths.get(new URI(fileUrl)).toString();
            } catch (Exception e) {
                logger.error("Failed to parse file URL: " + fileUrl, e);
            }
        }
        return null;
    }

    /**
     * Gets an InputStream for a cached avatar
     * 
     * @param fileUrl A file:// URL or regular URL
     * @return An InputStream, or null if the file doesn't exist
     */
    public static InputStream getInputStream(String fileUrl) {
        if (fileUrl == null) {
            return null;
        }

        try {
            if (fileUrl.startsWith("file:")) {
                // Local cached file
                String localPath = getLocalPath(fileUrl);
                if (localPath != null) {
                    return Files.newInputStream(Paths.get(localPath));
                }
            } else {
                // Remote URL - download it
                URL url = new URL(fileUrl);
                return url.openStream();
            }
        } catch (IOException e) {
            logger.error("Failed to get input stream for: " + fileUrl, e);
        }
        return null;
    }

    /**
     * Checks if a URL is a file:// URL pointing to a cached avatar
     */
    public static boolean isCachedAvatar(String url) {
        return url != null && url.startsWith("file:");
    }

    /**
     * Gets the file extension from a file URL
     */
    public static String getFileExtension(String fileUrl) {
        String localPath = getLocalPath(fileUrl);
        if (localPath != null) {
            int dotIndex = localPath.lastIndexOf('.');
            if (dotIndex > 0 && dotIndex < localPath.length() - 1) {
                return localPath.substring(dotIndex);
            }
        }
        return ".png"; // Default
    }

    /**
     * Checks if a URL is a Discord CDN URL
     */
    private static boolean isDiscordCdnUrl(String url) {
        return url != null && (url.contains("cdn.discordapp.com") ||
                url.contains("media.discordapp.net") ||
                url.contains("images-ext-") // Discord's external image proxy
        );
    }

    /**
     * Generates a unique filename for an avatar URL using hash
     */
    private static String generateFilename(String url) {
        try {
            // Extract extension from URL
            String extension = ".png"; // Default
            if (url.contains(".jpg") || url.contains(".jpeg")) {
                extension = ".jpg";
            } else if (url.contains(".gif")) {
                extension = ".gif";
            } else if (url.contains(".webp")) {
                extension = ".webp";
            }

            // Create hash of URL
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(url.getBytes());
            String hashStr = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

            // Use first 16 characters of hash for filename
            return hashStr.substring(0, 16) + extension;

        } catch (Exception e) {
            logger.error("Failed to generate filename for URL: " + url, e);
            // Fallback to timestamp-based filename
            return "avatar_" + System.currentTimeMillis() + ".png";
        }
    }

    /**
     * Deletes a cached avatar file
     * 
     * @param fileUrl A file:// URL
     * @return true if deleted successfully
     */
    public static boolean deleteCachedAvatar(String fileUrl) {
        String localPath = getLocalPath(fileUrl);
        if (localPath != null) {
            try {
                Files.deleteIfExists(Paths.get(localPath));
                logger.info("Deleted cached avatar: {}", localPath);
                return true;
            } catch (IOException e) {
                logger.error("Failed to delete cached avatar: " + localPath, e);
            }
        }
        return false;
    }

    /**
     * Gets the file size of a cached avatar
     * 
     * @param fileUrl A file:// URL
     * @return The file size in bytes, or -1 if not found
     */
    public static long getCachedFileSize(String fileUrl) {
        String localPath = getLocalPath(fileUrl);
        if (localPath != null) {
            try {
                return Files.size(Paths.get(localPath));
            } catch (IOException e) {
                logger.error("Failed to get file size: " + localPath, e);
            }
        }
        return -1;
    }
}
