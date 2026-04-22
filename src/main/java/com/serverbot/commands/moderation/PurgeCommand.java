package com.serverbot.commands.moderation;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.listeners.AutoLogListener;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Purge command with composable filters.
 *
 * All options are optional and freely combinable:
 *   amount      – cap on how many messages to delete (1–1000, default 100)
 *   user        – only messages from this user
 *   after       – ISO-8601 timestamp OR message URL; delete messages sent AFTER it
 *   before      – ISO-8601 timestamp OR message URL; delete messages sent BEFORE it
 *   from_msg    – message URL; inclusive start of a range
 *   to_msg      – message URL; inclusive end of a range
 *   all         – bypass the 2-week Discord bulk-delete limit (slower, one-by-one)
 *   bots        – only delete bot messages
 *   humans      – only delete human messages
 *   has_link    – only delete messages containing a URL
 *   has_embed   – only delete messages containing an embed or attachment
 *   contains    – only delete messages whose text contains this substring
 *
 * Contradictions caught early:
 *   - bots + humans at the same time
 *   - from_msg/to_msg combined with after/before (overlapping range semantics)
 */
public class PurgeCommand implements SlashCommand {

    // Discord message URL  …/channels/<guild>/<channel>/<message>
    private static final Pattern MSG_URL =
            Pattern.compile("https://(?:ptb\\.|canary\\.)?discord(?:app)?\\.com/channels/(\\d+)/(\\d+)/(\\d+)");

    private static final int FETCH_CHUNK = 100;
    private static final int MAX_FETCH   = 1000;

    // ── Entry point ───────────────────────────────────────────────────────────────

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "mod.purge")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need moderation permissions to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        // ── Resolve target channel (defaults to the channel the command was run in) ──

        OptionMapping channelOpt = event.getOption("channel");
        GuildMessageChannel channel;
        if (channelOpt != null) {
            net.dv8tion.jda.api.entities.channel.middleman.GuildChannel gc =
                    channelOpt.getAsChannel();
            if (!(gc instanceof GuildMessageChannel)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Channel", "The `channel` option must be a text or announcement channel."
                )).setEphemeral(true).queue();
                return;
            }
            channel = (GuildMessageChannel) gc;
        } else {
            channel = (GuildMessageChannel) event.getChannel();
        }

        if (!event.getGuild().getSelfMember().hasPermission(channel,
                Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Permissions",
                "I need **Manage Messages** and **Read Message History** in " + channel.getAsMention() + "."
            )).setEphemeral(true).queue();
            return;
        }

        // ── Parse options ────────────────────────────────────────────────────────

        boolean all          = event.getOption("all",       false, OptionMapping::getAsBoolean);
        boolean amountExplicit = event.getOption("amount") != null;
        // `all:true` without an explicit amount means "delete everything" (up to MAX_FETCH)
        int amount = amountExplicit
                ? event.getOption("amount", 100, OptionMapping::getAsInt)
                : (all ? MAX_FETCH : 100);

        if (amount < 1 || amount > MAX_FETCH) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Amount", "Amount must be between 1 and " + MAX_FETCH + "."
            )).setEphemeral(true).queue();
            return;
        }

        User   filterUser    = event.getOption("user",      null,  OptionMapping::getAsUser);
        String afterStr      = event.getOption("after",     null,  OptionMapping::getAsString);
        String beforeStr     = event.getOption("before",    null,  OptionMapping::getAsString);
        String fromMsgStr    = event.getOption("from_msg",  null,  OptionMapping::getAsString);
        String toMsgStr      = event.getOption("to_msg",    null,  OptionMapping::getAsString);
        boolean filterBots   = event.getOption("bots",      false, OptionMapping::getAsBoolean);
        boolean filterHumans = event.getOption("humans",    false, OptionMapping::getAsBoolean);
        boolean filterLinks  = event.getOption("has_link",  false, OptionMapping::getAsBoolean);
        boolean filterEmbeds = event.getOption("has_embed", false, OptionMapping::getAsBoolean);
        String  contains     = event.getOption("contains",  null,  OptionMapping::getAsString);

        // ── Contradiction checks ─────────────────────────────────────────────────

        if (filterBots && filterHumans) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Contradicting Filters",
                "`bots` and `humans` cannot both be set — no messages would ever match."
            )).setEphemeral(true).queue();
            return;
        }

        boolean hasRange        = fromMsgStr != null || toMsgStr != null;
        boolean hasAfterBefore  = afterStr   != null || beforeStr != null;
        if (hasRange && hasAfterBefore) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Contradicting Filters",
                "`from_msg`/`to_msg` and `after`/`before` both define a time range. Use one or the other."
            )).setEphemeral(true).queue();
            return;
        }

        // ── Resolve timestamps / message URL anchors ─────────────────────────────

        OffsetDateTime afterTime  = null;
        OffsetDateTime beforeTime = null;
        long fromMsgId = -1;
        long toMsgId   = -1;

        if (afterStr != null) {
            Long id = extractMsgId(afterStr);
            afterTime = (id != null) ? snowflakeToTime(id) : parseTimestamp(afterStr);
            if (afterTime == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid `after`",
                    "Could not parse `" + afterStr + "` as a timestamp or message URL.\n" +
                    "Accepted formats: `2024-03-15T14:30:00Z`, `2024-03-15`, or a message link."
                )).setEphemeral(true).queue();
                return;
            }
        }

        if (beforeStr != null) {
            Long id = extractMsgId(beforeStr);
            beforeTime = (id != null) ? snowflakeToTime(id) : parseTimestamp(beforeStr);
            if (beforeTime == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid `before`",
                    "Could not parse `" + beforeStr + "` as a timestamp or message URL.\n" +
                    "Accepted formats: `2024-03-15T14:30:00Z`, `2024-03-15`, or a message link."
                )).setEphemeral(true).queue();
                return;
            }
        }

        if (fromMsgStr != null) {
            fromMsgId = extractMsgIdOrFail(event, "from_msg", fromMsgStr);
            if (fromMsgId == -1) return;
        }

        if (toMsgStr != null) {
            toMsgId = extractMsgIdOrFail(event, "to_msg", toMsgStr);
            if (toMsgId == -1) return;
        }

        // Allow range to be provided in either order
        if (fromMsgId != -1 && toMsgId != -1 && fromMsgId > toMsgId) {
            long tmp = fromMsgId; fromMsgId = toMsgId; toMsgId = tmp;
        }

        if (afterTime != null && beforeTime != null && !afterTime.isBefore(beforeTime)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Time Range", "`after` must be earlier than `before`."
            )).setEphemeral(true).queue();
            return;
        }

        // Capture finals for lambda capture
        final OffsetDateTime fAfter   = afterTime;
        final OffsetDateTime fBefore  = beforeTime;
        final long fFromId = fromMsgId;
        final long fToId   = toMsgId;
        final int  fAmount = amount;
        final User fUser   = filterUser;

        event.deferReply(true).queue();

        // ── Collect & delete ─────────────────────────────────────────────────────

        List<Message> collected = new ArrayList<>();
        fetchChunk(channel, collected, fAmount, fFromId, fToId,
                fAfter, fBefore, fUser, filterBots, filterHumans,
                filterLinks, filterEmbeds, contains, all, null,
                result -> {
                    if (result.isEmpty()) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createInfoEmbed(
                            "No Messages Found",
                            "No messages matched the given filters."
                        )).queue();
                        return;
                    }
                    String summary = buildSummary(fAmount, fUser, afterStr, beforeStr,
                            fromMsgStr, toMsgStr, filterBots, filterHumans,
                            filterLinks, filterEmbeds, contains, all, channel, (GuildMessageChannel) event.getChannel());
                    deleteMessages(event, channel, result, all, summary);
                },
                err -> event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "History Error", "Failed to retrieve message history: " + err.getMessage()
                )).queue()
        );
    }

    // ── Paginated history fetcher ─────────────────────────────────────────────────

    private void fetchChunk(GuildMessageChannel channel,
                            List<Message> collected,
                            int limit,
                            long fromMsgId, long toMsgId,
                            OffsetDateTime afterTime, OffsetDateTime beforeTime,
                            User filterUser,
                            boolean filterBots, boolean filterHumans,
                            boolean filterLinks, boolean filterEmbeds,
                            String contains,
                            boolean all,
                            String cursorId,   // pagination: null = latest
                            java.util.function.Consumer<List<Message>> onDone,
                            java.util.function.Consumer<Throwable>     onError) {

        if (cursorId != null) {
            channel.getHistoryBefore(cursorId, FETCH_CHUNK).queue(
                history -> processHistoryChunk(history, channel, collected, limit,
                        fromMsgId, toMsgId, afterTime, beforeTime, filterUser,
                        filterBots, filterHumans, filterLinks, filterEmbeds,
                        contains, all, onDone, onError),
                onError::accept);
        } else {
            channel.getHistory().retrievePast(FETCH_CHUNK).queue(
                messages -> processRawChunk(messages, channel, collected, limit,
                        fromMsgId, toMsgId, afterTime, beforeTime, filterUser,
                        filterBots, filterHumans, filterLinks, filterEmbeds,
                        contains, all, onDone, onError),
                onError::accept);
        }
    }

    private void processHistoryChunk(MessageHistory history,
                                     GuildMessageChannel channel,
                                     List<Message> collected,
                                     int limit,
                                     long fromMsgId, long toMsgId,
                                     OffsetDateTime afterTime, OffsetDateTime beforeTime,
                                     User filterUser,
                                     boolean filterBots, boolean filterHumans,
                                     boolean filterLinks, boolean filterEmbeds,
                                     String contains,
                                     boolean all,
                                     java.util.function.Consumer<List<Message>> onDone,
                                     java.util.function.Consumer<Throwable>     onError) {
        processRawChunk(history.getRetrievedHistory(), channel, collected, limit,
                fromMsgId, toMsgId, afterTime, beforeTime, filterUser,
                filterBots, filterHumans, filterLinks, filterEmbeds,
                contains, all, onDone, onError);
    }

    private void processRawChunk(List<Message> messages,
                                 GuildMessageChannel channel,
                                 List<Message> collected,
                                 int limit,
                                 long fromMsgId, long toMsgId,
                                 OffsetDateTime afterTime, OffsetDateTime beforeTime,
                                 User filterUser,
                                 boolean filterBots, boolean filterHumans,
                                 boolean filterLinks, boolean filterEmbeds,
                                 String contains,
                                 boolean all,
                                 java.util.function.Consumer<List<Message>> onDone,
                                 java.util.function.Consumer<Throwable>     onError) {

        OffsetDateTime twoWeeksAgo = OffsetDateTime.now().minusWeeks(2).plusMinutes(1);

        if (messages.isEmpty()) {
            onDone.accept(Collections.unmodifiableList(collected));
            return;
        }

        boolean stop = false;

        for (Message msg : messages) {
            if (collected.size() >= limit) { stop = true; break; }

            long           msgId   = msg.getIdLong();
            OffsetDateTime msgTime = msg.getTimeCreated();

            // ── Range / time filters ─────────────────────────────────────
            if (toMsgId   != -1 && msgId > toMsgId)   continue; // newer than range end
            if (fromMsgId != -1 && msgId < fromMsgId) { stop = true; break; } // past range start

            if (afterTime  != null && !msgTime.isAfter(afterTime))   continue;
            if (beforeTime != null && !msgTime.isBefore(beforeTime)) continue;

            // Discord bulk-delete window (skip old msgs unless `all` is set)
            if (!all && msgTime.isBefore(twoWeeksAgo)) {
                // If there's no lower-bound anchor we can stop — nothing older will qualify
                if (afterTime == null && fromMsgId == -1) { stop = true; break; }
                continue;
            }

            // ── Content / author filters ─────────────────────────────────
            if (filterUser   != null && !msg.getAuthor().getId().equals(filterUser.getId())) continue;
            if (filterBots   && !msg.getAuthor().isBot())  continue;
            if (filterHumans &&  msg.getAuthor().isBot())  continue;
            if (filterLinks  && !hasUrl(msg.getContentRaw())) continue;
            if (filterEmbeds && msg.getEmbeds().isEmpty() && msg.getAttachments().isEmpty()) continue;
            if (contains     != null && !msg.getContentRaw().toLowerCase().contains(contains.toLowerCase())) continue;

            collected.add(msg);
        }

        if (stop || collected.size() >= limit || messages.size() < FETCH_CHUNK) {
            onDone.accept(Collections.unmodifiableList(collected));
            return;
        }

        // Paginate using the oldest message in this batch as the new cursor
        String next = messages.get(messages.size() - 1).getId();
        fetchChunk(channel, collected, limit, fromMsgId, toMsgId,
                afterTime, beforeTime, filterUser, filterBots, filterHumans,
                filterLinks, filterEmbeds, contains, all, next, onDone, onError);
    }

    // ── Deletion ──────────────────────────────────────────────────────────────────

    private void deleteMessages(SlashCommandInteractionEvent event,
                                GuildMessageChannel channel,
                                List<Message> messages,
                                boolean forceIndividual,
                                String summary) {

        OffsetDateTime twoWeeksAgo = OffsetDateTime.now().minusWeeks(2).plusMinutes(1);
        List<Message> bulk = new ArrayList<>();
        List<Message> old  = new ArrayList<>();

        for (Message m : messages) {
            if (!forceIndividual && m.getTimeCreated().isAfter(twoWeeksAgo)) bulk.add(m);
            else                                                               old.add(m);
        }

        int total = messages.size();

        // Snapshot needed for the completion callback closure
        final List<Message> oldToLog = new ArrayList<>(old);
        final String channelMention  = channel.getAsMention();

        // Called once all deletions are done (bulk + individual)
        Runnable onComplete = () -> {
            sendSuccess(event, total, summary);
            // Log old messages that were individually deleted as a group
            if (!oldToLog.isEmpty() && event.isFromGuild()) {
                AutoLogListener.logOldMessagePurge(
                    event.getGuild(), channelMention, oldToLog, event.getUser()
                );
            }
        };

        if (!old.isEmpty()) {
            // Tell AutoLogListener to ignore the individual MessageDeleteEvents for these
            List<String> oldIds = new ArrayList<>();
            for (Message m : old) oldIds.add(m.getId());
            AutoLogListener.suppressBatchDelete(oldIds);
        }

        if (old.isEmpty()) {
            // All recent — single bulk call; MessageBulkDeleteEvent handles the log
            doBulk(event, channel, bulk, onComplete);
        } else if (bulk.isEmpty()) {
            // All older than 2 weeks — individual deletions
            doIndividual(event, channel, new ArrayList<>(old), 0, onComplete);
        } else {
            // Mixed — bulk first, then individual
            CompletableFuture<Void> bf = (bulk.size() == 1)
                    ? bulk.get(0).delete().submit().thenApply(v -> null)
                    : channel.deleteMessages(bulk).submit().thenApply(v -> null);
            bf.thenRun(() -> doIndividual(event, channel, new ArrayList<>(old), 0, onComplete))
              .exceptionally(err -> { sendError(event, err); return null; });
        }
    }

    private void doBulk(SlashCommandInteractionEvent event, GuildMessageChannel channel,
                        List<Message> msgs, Runnable onComplete) {
        if (msgs.size() == 1) {
            msgs.get(0).delete().queue(
                v   -> onComplete.run(),
                err -> sendError(event, err)
            );
        } else {
            channel.deleteMessages(msgs).queue(
                v   -> onComplete.run(),
                err -> sendError(event, err)
            );
        }
    }

    private void doIndividual(SlashCommandInteractionEvent event, GuildMessageChannel channel,
                              List<Message> msgs, int index, Runnable onComplete) {
        if (index >= msgs.size()) { onComplete.run(); return; }
        msgs.get(index).delete().queue(
            v   -> doIndividual(event, channel, msgs, index + 1, onComplete),
            err -> doIndividual(event, channel, msgs, index + 1, onComplete) // skip on error
        );
    }

    // ── Response helpers ──────────────────────────────────────────────────────────

    private void sendSuccess(SlashCommandInteractionEvent event, int count, String summary) {
        String desc = "Deleted **" + count + "** message" + (count != 1 ? "s" : "") + ".";
        if (!summary.isBlank()) desc += "\n\n**Filters applied:** " + summary;
        event.getHook().editOriginalEmbeds(
            EmbedUtils.createSuccessEmbed("🗑️ Purge Complete", desc)
        ).queue();
    }

    private void sendError(SlashCommandInteractionEvent event, Throwable err) {
        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
            "Purge Failed", err.getMessage()
        )).queue();
    }

    // ── Utility helpers ───────────────────────────────────────────────────────────

    /** Returns the snowflake ID from a Discord message URL, or null if it's not a URL. */
    private static Long extractMsgId(String input) {
        if (input == null) return null;
        Matcher m = MSG_URL.matcher(input.trim());
        if (!m.find()) return null;
        try { return Long.parseUnsignedLong(m.group(3)); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Like {@link #extractMsgId} but replies with an error and returns -1 on failure.
     * Only call before deferReply.
     */
    private static long extractMsgIdOrFail(SlashCommandInteractionEvent event,
                                           String optionName, String input) {
        Long id = extractMsgId(input);
        if (id == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid `" + optionName + "`", "Please provide a valid Discord message link."
            )).setEphemeral(true).queue();
            return -1;
        }
        return id;
    }

    /** Convert a Discord snowflake ID into the OffsetDateTime it was created. */
    private static OffsetDateTime snowflakeToTime(long id) {
        long ms = (id >> 22) + 1420070400000L; // Discord epoch
        return OffsetDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneOffset.UTC);
    }

    /** Try to parse a user-supplied timestamp string. Returns null on failure. */
    private static OffsetDateTime parseTimestamp(String input) {
        if (input == null) return null;
        // Plain date shorthand: yyyy-MM-dd
        if (input.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try { return OffsetDateTime.parse(input + "T00:00:00Z", DateTimeFormatter.ISO_OFFSET_DATE_TIME); }
            catch (DateTimeParseException ignored) {}
        }
        // ISO-8601 with offset or Z
        try { return OffsetDateTime.parse(input); } catch (DateTimeParseException ignored) {}
        // ISO-8601 instant (e.g. 2024-03-15T14:30:00Z treated as UTC)
        try {
            return OffsetDateTime.ofInstant(Instant.parse(input), ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {}
        return null;
    }

    private static boolean hasUrl(String text) {
        return text.contains("http://") || text.contains("https://") || text.contains("www.");
    }

    private static String buildSummary(int amount, User user,
                                       String after, String before,
                                       String fromMsg, String toMsg,
                                       boolean bots, boolean humans,
                                       boolean links, boolean embeds,
                                       String contains, boolean all,
                                       GuildMessageChannel target, GuildMessageChannel invokedIn) {
        List<String> parts = new ArrayList<>();
        if (!target.getId().equals(invokedIn.getId()))
                                  parts.add("in " + target.getAsMention());
        if (amount != 100)        parts.add("up to **" + amount + "** messages");
        if (user   != null)       parts.add("from " + user.getAsMention());
        if (after  != null)       parts.add("after `" + after + "`");
        if (before != null)       parts.add("before `" + before + "`");
        if (fromMsg != null)      parts.add("from message URL");
        if (toMsg   != null)      parts.add("to message URL");
        if (bots)                 parts.add("bots only");
        if (humans)               parts.add("humans only");
        if (links)                parts.add("containing links");
        if (embeds)               parts.add("containing embeds/attachments");
        if (contains != null)     parts.add("containing `" + contains + "`");
        if (all)                  parts.add("including messages older than 2 weeks");
        return String.join(", ", parts);
    }

    // ── Command definition ────────────────────────────────────────────────────────

    public static CommandData getCommandData() {
        return Commands.slash("purge", "Bulk-delete messages with optional composable filters.")
                .addOptions(
                    new OptionData(OptionType.CHANNEL, "channel",
                            "Channel to purge (defaults to current channel)", false)
                            .setChannelTypes(ChannelType.TEXT, ChannelType.NEWS),
                    new OptionData(OptionType.INTEGER, "amount",
                            "Max messages to delete (1–1000, default 100)", false)
                            .setMinValue(1).setMaxValue(MAX_FETCH),
                    new OptionData(OptionType.USER, "user",
                            "Only delete messages from this user", false),
                    new OptionData(OptionType.STRING, "after",
                            "Delete messages after this time (ISO-8601 or message URL)", false),
                    new OptionData(OptionType.STRING, "before",
                            "Delete messages before this time (ISO-8601 or message URL)", false),
                    new OptionData(OptionType.STRING, "from_msg",
                            "Delete messages starting from this message URL (inclusive)", false),
                    new OptionData(OptionType.STRING, "to_msg",
                            "Delete messages up to this message URL (inclusive)", false),
                    new OptionData(OptionType.BOOLEAN, "all",
                            "Also delete messages older than 2 weeks (slower)", false),
                    new OptionData(OptionType.BOOLEAN, "bots",
                            "Only delete messages from bots", false),
                    new OptionData(OptionType.BOOLEAN, "humans",
                            "Only delete messages from humans", false),
                    new OptionData(OptionType.BOOLEAN, "has_link",
                            "Only delete messages containing a URL", false),
                    new OptionData(OptionType.BOOLEAN, "has_embed",
                            "Only delete messages containing an embed or attachment", false),
                    new OptionData(OptionType.STRING, "contains",
                            "Only delete messages whose text contains this substring", false)
                );
    }

    @Override public String getName()        { return "purge"; }
    @Override public String getDescription() { return "Bulk-delete messages with optional composable filters."; }
    @Override public CommandCategory getCategory() { return CommandCategory.MODERATION; }
    @Override public boolean requiresPermissions()  { return true; }
}
