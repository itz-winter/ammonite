package com.serverbot.commands.owner;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import com.serverbot.utils.context.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Bot-owner only command to view and manage error reports.
 *
 * <p>Slash: {@code /reports error [query] [sort] [page]}
 * <p>Prefix: {@code !reports error [query] [sort] [page]}
 *
 * <p>Sort options: most-reported (default), least-reported, newest, oldest.
 * Page is 1-based; 10 reports per page.
 */
public class ReportsCommand implements SlashCommand {

    private static final int PAGE_SIZE = 10;

    // ── Sort choice constants ────────────────────────────────────────────────
    private static final String SORT_MOST   = "most-reported";
    private static final String SORT_LEAST  = "least-reported";
    private static final String SORT_NEWEST = "newest";
    private static final String SORT_OLDEST = "oldest";

    // ── SlashCommand boilerplate ─────────────────────────────────────────────

    @Override
    public String getName() { return "reports"; }

    @Override
    public String getDescription() { return "View and manage bot error reports (bot owner only)"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.UTILITY; }

    @Override
    public boolean requiresPermissions() { return true; }

    @Override
    public boolean isOwnerOnly() { return true; }

    @Override
    public boolean isGuildOnly() { return false; }

    @Override
    public boolean supportsCommandContext() { return true; }

    // ── CommandData ──────────────────────────────────────────────────────────

    public static CommandData getCommandData() {
        return Commands.slash("reports", "View and manage bot error reports (bot owner only)")
                .addSubcommands(
                        new SubcommandData("error", "List recorded error reports")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "query",
                                                "Filter by command name or error type", false),
                                        new OptionData(OptionType.STRING, "sort",
                                                "Sort order (most-reported / least-reported / newest / oldest)", false)
                                                .addChoice("Most Reported",   SORT_MOST)
                                                .addChoice("Least Reported",  SORT_LEAST)
                                                .addChoice("Newest",          SORT_NEWEST)
                                                .addChoice("Oldest",          SORT_OLDEST),
                                        new OptionData(OptionType.INTEGER, "page",
                                                "Page number (default: 1)", false)
                                                .setMinValue(1)),
                        new SubcommandData("tag", "Add or remove a tag from an error report")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "key",
                                                "The dedupKey of the report (cmdName:errorType)", true),
                                        new OptionData(OptionType.STRING, "action",
                                                "add or remove", true)
                                                .addChoice("Add",    "add")
                                                .addChoice("Remove", "remove"),
                                        new OptionData(OptionType.STRING, "tag",
                                                "The tag to add or remove", true)),
                        new SubcommandData("delete", "Delete an error report")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "key",
                                                "The dedupKey of the report to delete (cmdName:errorType)", true)));
    }

    // ── Slash execution ──────────────────────────────────────────────────────

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isBotOwner(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Command",
                    "The command `/" + event.getName() + "` was not found. Use `/help` to see available commands."))
                    .setEphemeral(true).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if ("error".equals(sub)) {
            String query = event.getOption("query")  != null ? event.getOption("query").getAsString()   : null;
            String sort  = event.getOption("sort")   != null ? event.getOption("sort").getAsString()    : SORT_MOST;
            int    page  = event.getOption("page")   != null ? (int) event.getOption("page").getAsLong(): 1;
            event.replyEmbeds(buildErrorListEmbed(query, sort, page)).setEphemeral(true).queue();

        } else if ("tag".equals(sub)) {
            String key    = event.getOption("key").getAsString();
            String action = event.getOption("action").getAsString();
            String tag    = event.getOption("tag").getAsString();
            handleTagAction(event, key, action, tag);

        } else if ("delete".equals(sub)) {
            String key = event.getOption("key").getAsString();
            handleDelete(event, key);

        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Unknown Subcommand",
                    "Use `/reports error`, `/reports tag`, or `/reports delete`."))
                    .setEphemeral(true).queue();
        }
    }

    // ── CommandContext (prefix) execution ────────────────────────────────────

    @Override
    public void executeWithContext(CommandContext ctx) {
        if (!PermissionUtils.isBotOwner(ctx.getUser())) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Access Denied",
                    "Only bot owners can view error reports."));
            return;
        }

        // Prefix: !reports error [query] [sort] [page]
        // Subcommand name comes from the "subcommand" option set by PrefixCommandService
        String sub = ctx.getStringOption("subcommand");
        if (sub == null) sub = "error"; // default

        if ("error".equalsIgnoreCase(sub)) {
            String query = ctx.getStringOption("query");
            String sort  = ctx.getStringOption("sort");
            int    page  = 1;
            String pageStr = ctx.getStringOption("page");
            if (pageStr != null) {
                try { page = Math.max(1, Integer.parseInt(pageStr)); } catch (NumberFormatException ignored) {}
            }
            if (sort == null) sort = SORT_MOST;
            // Normalise sort string from prefix (fuzzy)
            sort = normaliseSort(sort);
            ctx.reply(buildErrorListEmbed(query, sort, page));
        } else {
            ctx.replyEphemeral(EmbedUtils.createInfoEmbed("Reports",
                    "Prefix sub-commands supported: `error`.\n" +
                    "Use `/reports tag` or `/reports delete` for tag/delete operations (slash only)."));
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Build the paginated error list embed.
     */
    private MessageEmbed buildErrorListEmbed(String query, String sort, int page) {
        int total = ServerBot.getStorageManager().getErrorReportTotalCount(query);
        int totalPages = Math.max(1, (int) Math.ceil((double) total / PAGE_SIZE));
        page = Math.min(page, totalPages);

        List<Map<String, Object>> reports = ServerBot.getStorageManager().getErrorReports(query, sort, page, PAGE_SIZE);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(231, 76, 60))
                .setTitle("📋 Error Reports")
                .setTimestamp(Instant.now())
                .setFooter("Page " + page + "/" + totalPages + " • " + total + " total report(s) • Sort: " + sort);

        if (query != null && !query.isBlank()) {
            eb.setDescription("**Filter:** `" + query + "`");
        }

        if (reports.isEmpty()) {
            eb.setDescription((eb.build().getDescription() != null ? eb.build().getDescription() + "\n\n" : "") +
                    "No error reports found.");
            return eb.build();
        }

        for (int i = 0; i < reports.size(); i++) {
            Map<String, Object> r = reports.get(i);
            String cmd       = String.valueOf(r.getOrDefault("commandName", "unknown"));
            String errType   = String.valueOf(r.getOrDefault("errorType",   "Unknown"));
            String errMsg    = String.valueOf(r.getOrDefault("errorMessage", "(none)"));
            int    count     = ((Number) r.getOrDefault("count", 0)).intValue();
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) r.getOrDefault("tags", List.of());
            String dedupKey  = String.valueOf(r.getOrDefault("dedupKey", cmd + ":" + errType));

            String tagStr = tags.isEmpty() ? "" : " `" + String.join("` `", tags) + "`";
            String fieldName = "`" + ((page - 1) * PAGE_SIZE + i + 1) + ".` **/" + cmd + "** — " +
                    errType + " (×" + count + ")";
            String fieldVal = truncate(errMsg, 200) + tagStr + "\n`" + dedupKey + "`";
            eb.addField(fieldName, fieldVal, false);
        }

        return eb.build();
    }

    private void handleTagAction(SlashCommandInteractionEvent event, String key, String action, String tag) {
        if ("add".equals(action)) {
            boolean ok = ServerBot.getStorageManager().addTagToErrorReport(key, tag);
            event.replyEmbeds(ok
                    ? EmbedUtils.createSuccessEmbed("Tag Added", "Added tag `" + tag + "` to `" + key + "`.")
                    : EmbedUtils.createErrorEmbed("Tag Not Added", "Report not found, or tag already present."))
                    .setEphemeral(true).queue();
        } else {
            boolean ok = ServerBot.getStorageManager().removeTagFromErrorReport(key, tag);
            event.replyEmbeds(ok
                    ? EmbedUtils.createSuccessEmbed("Tag Removed", "Removed tag `" + tag + "` from `" + key + "`.")
                    : EmbedUtils.createErrorEmbed("Tag Not Removed", "Report not found, or tag was not present."))
                    .setEphemeral(true).queue();
        }
    }

    private void handleDelete(SlashCommandInteractionEvent event, String key) {
        boolean ok = ServerBot.getStorageManager().deleteErrorReport(key);
        event.replyEmbeds(ok
                ? EmbedUtils.createSuccessEmbed("Report Deleted", "Error report `" + key + "` has been deleted.")
                : EmbedUtils.createErrorEmbed("Not Found", "No error report found with key `" + key + "`."))
                .setEphemeral(true).queue();
    }

    /**
     * Normalise a prefix-typed sort string to a canonical value.
     * Accepts case-insensitive partial matches.
     */
    private static String normaliseSort(String raw) {
        if (raw == null) return SORT_MOST;
        String s = raw.toLowerCase().replace("-", "").replace(" ", "");
        if (s.startsWith("least"))  return SORT_LEAST;
        if (s.startsWith("new"))    return SORT_NEWEST;
        if (s.startsWith("old"))    return SORT_OLDEST;
        return SORT_MOST; // default
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
