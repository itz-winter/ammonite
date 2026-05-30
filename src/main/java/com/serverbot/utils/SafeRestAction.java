package com.serverbot.utils;

import net.dv8tion.jda.api.requests.RestAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

/**
 * Utility class for safely executing Discord API RestActions with error
 * handling
 * Prevents API spam and provides consistent error logging
 */
public class SafeRestAction {

    private static final Logger logger = LoggerFactory.getLogger(SafeRestAction.class);

    /**
     * Queue a RestAction with automatic error handling
     * 
     * @param action  The RestAction to queue
     * @param context Context description for logging (e.g., "delete message", "send
     *                embed")
     * @param <T>     The type returned by the RestAction
     */
    public static <T> void queue(RestAction<T> action, String context) {
        action.queue(
                success -> logger.trace("Successfully completed: {}", context),
                failure -> {
                    if (isRateLimitError(failure)) {
                        logger.warn("Rate limited while {}: {}", context, failure.getMessage());
                    } else if (isPermissionError(failure)) {
                        logger.debug("Missing permission while {}: {}", context, failure.getMessage());
                    } else if (isNotFoundError(failure)) {
                        logger.debug("Resource not found while {}: {}", context, failure.getMessage());
                    } else {
                        logger.error("Failed to {}: {}", context, failure.getMessage());
                    }
                });
    }

    /**
     * Queue a RestAction with custom success handler and automatic error handling
     * 
     * @param action    The RestAction to queue
     * @param context   Context description for logging
     * @param onSuccess Custom success handler
     * @param <T>       The type returned by the RestAction
     */
    public static <T> void queue(RestAction<T> action, String context, Consumer<T> onSuccess) {
        action.queue(
                result -> {
                    try {
                        onSuccess.accept(result);
                        logger.trace("Successfully completed: {}", context);
                    } catch (Exception e) {
                        logger.error("Error in success handler for {}: {}", context, e.getMessage(), e);
                    }
                },
                failure -> {
                    if (isRateLimitError(failure)) {
                        logger.warn("Rate limited while {}: {}", context, failure.getMessage());
                    } else if (isPermissionError(failure)) {
                        logger.debug("Missing permission while {}: {}", context, failure.getMessage());
                    } else if (isNotFoundError(failure)) {
                        logger.debug("Resource not found while {}: {}", context, failure.getMessage());
                    } else {
                        logger.error("Failed to {}: {}", context, failure.getMessage());
                    }
                });
    }

    /**
     * Queue a RestAction with custom success and error handlers
     * 
     * @param action    The RestAction to queue
     * @param context   Context description for logging
     * @param onSuccess Custom success handler
     * @param onFailure Custom failure handler
     * @param <T>       The type returned by the RestAction
     */
    public static <T> void queue(RestAction<T> action, String context,
            Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        action.queue(
                result -> {
                    try {
                        onSuccess.accept(result);
                        logger.trace("Successfully completed: {}", context);
                    } catch (Exception e) {
                        logger.error("Error in success handler for {}: {}", context, e.getMessage(), e);
                    }
                },
                failure -> {
                    try {
                        onFailure.accept(failure);
                    } catch (Exception e) {
                        logger.error("Error in failure handler for {}: {}", context, e.getMessage(), e);
                    }

                    // Still log the original error
                    if (isRateLimitError(failure)) {
                        logger.warn("Rate limited while {}: {}", context, failure.getMessage());
                    } else if (isPermissionError(failure)) {
                        logger.debug("Missing permission while {}: {}", context, failure.getMessage());
                    } else if (isNotFoundError(failure)) {
                        logger.debug("Resource not found while {}: {}", context, failure.getMessage());
                    } else {
                        logger.error("Failed to {}: {}", context, failure.getMessage());
                    }
                });
    }

    private static boolean isRateLimitError(Throwable error) {
        String message = error.getMessage();
        return message != null && (message.contains("rate limit") ||
                message.contains("429") ||
                message.contains("Too Many Requests"));
    }

    private static boolean isPermissionError(Throwable error) {
        String message = error.getMessage();
        return message != null && (message.contains("permission") ||
                message.contains("Missing Access") ||
                message.contains("403") ||
                message.contains("Forbidden"));
    }

    private static boolean isNotFoundError(Throwable error) {
        String message = error.getMessage();
        return message != null && (message.contains("Unknown") ||
                message.contains("not found") ||
                message.contains("404"));
    }
}
