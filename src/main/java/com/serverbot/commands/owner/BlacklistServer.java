package com.serverbot.commands.owner;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import com.serverbot.utils.context.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bot-owner only command to blacklist servers or users from interacting with the bot.
 * Blacklisted guilds are left immediately; the owner receives a DM with an appeal button.
 * Duration format: 7d / 24h / 30m / 60s — omit for permanent.
 */
public class BlacklistServer implements SlashCommand {

    @Override
    public String getName() { return "blacklist"; }

    @Override
    public String getDescription() { return "Blacklist a server or user from using the bot (bot owner only)."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.UTILITY; }

    @Override
    public boolean isOwnerOnly() { return true; }

    @Override
    public boolean isGuildOnly() { return false; }

    @Override
    public boolean supportsCommandContext() { return true; }

    public static CommandData getCommandData() {
        return Commands.slash("blacklist", "Blacklist a server or user from using the bot (bot owner only).")
                .addSubcommands(
                        new SubcommandData("server", "Blacklist a server from using the bot.")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "id",
                                                "The server ID to blacklist.", true),
                                        new OptionData(OptionType.STRING, "reason",
                                                "Reason for blacklisting.", false),
                                        new OptionData(OptionType.STRING, "duration",
                                                "Duration (e.g. 7d, 30d). Omit for permanent.", false)),
                        new SubcommandData("user", "Blacklist a user from using the bot.")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "id",
                                                "The user ID to blacklist.", true),
                                        new OptionData(OptionType.STRING, "reason",
                                                "Reason for blacklisting.", false),
                                        new OptionData(OptionType.STRING, "duration",
                                                "Duration (e.g. 7d, 30d). Omit for permanent.", false)),
                        new SubcommandData("remove", "Remove a server or user from the blacklist.")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "id",
                                                "The server or user ID to remove.", true)),
                        new SubcommandData("list", "List all currently blacklisted servers and users.")
                                .addOptions(
                                        new OptionData(OptionType.INTEGER, "page",
                                                "Page number (default: 1).", false)
                                                .setMinValue(1)));
    }

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
        event.deferReply(true).queue();

        if ("server".equals(sub)) {
            String id     = event.getOption("id").getAsString();
            String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided.";
            String durStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : null;
            long   dur    = parseDuration(durStr);
            handleBlacklistServer(event, id, reason, dur);

        } else if ("user".equals(sub)) {
            String id     = event.getOption("id").getAsString();
            String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided.";
            String durStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : null;
            long   dur    = parseDuration(durStr);
            handleBlacklistUser(event, id, reason, dur);

        } else if ("remove".equals(sub)) {
            String id = event.getOption("id").getAsString();
            handleRemove(event, id);

        } else if ("list".equals(sub)) {
            int page = event.getOption("page") != null ? (int) event.getOption("page").getAsLong() : 1;
            event.getHook().sendMessageEmbeds(buildListEmbed(page)).setEphemeral(true).queue();

        } else {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Unknown Subcommand",
                    "Use `/blacklist server`, `/blacklist user`, `/blacklist remove`, or `/blacklist list`."))
                    .setEphemeral(true).queue();
        }
    }

    @Override
    public void executeWithContext(CommandContext ctx) {
        if (!PermissionUtils.isBotOwner(ctx.getUser())) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Access Denied",
                    "Only bot owners can use this command."));
            return;
        }

        String sub = ctx.getStringOption("subcommand");
        if (sub == null) sub = "";

        switch (sub.toLowerCase()) {
            case "server" -> {
                String id     = ctx.getStringOption("id");
                String reason = ctx.getStringOption("reason");
                String durStr = ctx.getStringOption("duration");
                if (id == null) { ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Missing ID", "Provide a server ID.")); return; }
                blacklistGuild(ctx.getUser(), id, reason != null ? reason : "No reason provided.", parseDuration(durStr), ctx);
            }
            case "user" -> {
                String id     = ctx.getStringOption("id");
                String reason = ctx.getStringOption("reason");
                String durStr = ctx.getStringOption("duration");
                if (id == null) { ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Missing ID", "Provide a user ID.")); return; }
                blacklistUser(ctx.getUser(), id, reason != null ? reason : "No reason provided.", parseDuration(durStr), ctx);
            }
            case "remove" -> {
                String id = ctx.getStringOption("id");
                if (id == null) { ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Missing ID", "Provide a guild or user ID to remove.")); return; }
                boolean removed = ServerBot.getStorageManager().removeBlacklist(id);
                ctx.reply(removed
                        ? EmbedUtils.createSuccessEmbed("Blacklist Removed", "ID `" + id + "` has been removed from the blacklist.")
                        : EmbedUtils.createErrorEmbed("Not Found", "No active blacklist entry found for ID `" + id + "`."));
            }
            case "list" -> {
                String pageStr = ctx.getStringOption("page");
                int page = 1;
                if (pageStr != null) { try { page = Math.max(1, Integer.parseInt(pageStr)); } catch (NumberFormatException ignored) {} }
                ctx.reply(buildListEmbed(page));
            }
            default -> ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Usage",
                    "Usage: `!blacklist <server|user|remove|list> <id> [reason] [duration]`"));
        }
    }

    private void handleBlacklistServer(SlashCommandInteractionEvent event, String guildId, String reason, long duration) {
        blacklistGuild(event.getUser(), guildId, reason, duration, null);
        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setColor(new Color(0xED4245))
                .setTitle("✅ Server Blacklisted")
                .addField("Server ID", "`" + guildId + "`", true)
                .addField("Duration", formatDuration(duration), true)
                .addField("Reason", reason, false)
                .setTimestamp(Instant.now())
                .build()).setEphemeral(true).queue();
    }

    private void handleBlacklistUser(SlashCommandInteractionEvent event, String userId, String reason, long duration) {
        blacklistUser(event.getUser(), userId, reason, duration, null);
        event.getHook().sendMessageEmbeds(new EmbedBuilder()
                .setColor(new Color(0xED4245))
                .setTitle("✅ User Blacklisted")
                .addField("User ID", "`" + userId + "`", true)
                .addField("Duration", formatDuration(duration), true)
                .addField("Reason", reason, false)
                .setTimestamp(Instant.now())
                .build()).setEphemeral(true).queue();
    }

    private void handleRemove(SlashCommandInteractionEvent event, String id) {
        boolean removed = ServerBot.getStorageManager().removeBlacklist(id);
        if (removed) {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.createSuccessEmbed("Blacklist Removed",
                            "ID `" + id + "` has been removed from the blacklist."))
                    .setEphemeral(true).queue();
        } else {
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.createErrorEmbed("Not Found",
                            "No active blacklist entry found for ID `" + id + "`."))
                    .setEphemeral(true).queue();
        }
    }

    /**
     * Persists the guild blacklist entry, notifies the owner via DM, and leaves the guild.
     * {@code ctx} is unused when called from slash — passed only for future prefix-reply use.
     */
    private void blacklistGuild(User moderator, String guildId, String reason, long duration, CommandContext ctx) {
        ServerBot.getStorageManager().addGuildBlacklist(guildId, reason, duration, moderator.getId());
        Guild guild = ServerBot.getJda() != null ? ServerBot.getJda().getGuildById(guildId) : null;
        if (guild != null) {
            guild.retrieveOwner().queue(owner -> {
                if (owner != null) sendGuildBlacklistDm(owner.getUser(), guild, reason, duration);
                guild.leave().queue();
            }, err -> guild.leave().queue());
        }
    }

    /**
     * Persists the user blacklist entry and notifies the user via DM if reachable.
     * {@code ctx} is unused when called from slash — passed only for future prefix-reply use.
     */
    private void blacklistUser(User moderator, String userId, String reason, long duration, CommandContext ctx) {
        ServerBot.getStorageManager().addUserBlacklist(userId, reason, duration, moderator.getId());
        if (ServerBot.getJda() != null) {
            ServerBot.getJda().retrieveUserById(userId).queue(
                    user -> sendUserBlacklistDm(user, reason, duration),
                    err -> { /* user not found or DMs closed */ });
        }
    }

    private void sendGuildBlacklistDm(User owner, Guild guild, String reason, long duration) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xED4245))
                .setTitle("Your server has been blacklisted.")
                .setDescription(
                        "**Server Name:** `" + guild.getName() + "`\n\n" +
                        "**Server ID:** `" + guild.getId() + "`\n\n" +
                        "**Duration:** `" + formatDuration(duration) + "`\n\n" +
                        "**Reason(s):** `" + reason + "`")
                .setThumbnail(guild.getIconUrl())
                .setTimestamp(Instant.now())
                .setFooter("You may appeal this blacklisting using the button below.");

        owner.openPrivateChannel().queue(ch ->
                ch.sendMessageEmbeds(embed.build())
                  .setComponents(ActionRow.of(Button.danger("blk_appeal:guild:" + guild.getId(), "Appeal")))
                  .queue(null, err -> { /* DMs closed */ }));
    }

    private void sendUserBlacklistDm(User user, String reason, long duration) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(0xED4245))
                .setTitle("You have been blacklisted from using this bot.")
                .setDescription(
                        "**Duration:** `" + formatDuration(duration) + "`\n\n" +
                        "**Reason(s):** `" + reason + "`")
                .setTimestamp(Instant.now())
                .setFooter("You may appeal this blacklisting using the button below.");

        user.openPrivateChannel().queue(ch ->
                ch.sendMessageEmbeds(embed.build())
                  .setComponents(ActionRow.of(Button.danger("blk_appeal:user:" + user.getId(), "Appeal")))
                  .queue(null, err -> { /* DMs closed */ }));
    }

    private static final int LIST_PAGE_SIZE = 10;

    private net.dv8tion.jda.api.entities.MessageEmbed buildListEmbed(int page) {
        Map<String, Map<String, Object>> all = ServerBot.getStorageManager().getAllBlacklisted();
        List<Map.Entry<String, Map<String, Object>>> entries = new ArrayList<>(all.entrySet());
        entries.sort((a, b) -> {
            long ta = ((Number) a.getValue().getOrDefault("timestamp", 0L)).longValue();
            long tb = ((Number) b.getValue().getOrDefault("timestamp", 0L)).longValue();
            return Long.compare(tb, ta); // newest first
        });

        int total      = entries.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / LIST_PAGE_SIZE));
        page = Math.min(page, totalPages);
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end   = Math.min(start + LIST_PAGE_SIZE, total);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(0xED4245))
                .setTitle("🚫 Blacklist")
                .setTimestamp(Instant.now())
                .setFooter("Page " + page + "/" + totalPages + " • " + total + " total entr" + (total == 1 ? "y" : "ies"));

        if (entries.isEmpty()) {
            eb.setDescription("The blacklist is empty.");
            return eb.build();
        }

        for (int i = start; i < end; i++) {
            Map.Entry<String, Map<String, Object>> e = entries.get(i);
            Map<String, Object> v = e.getValue();
            String id     = e.getKey();
            String type   = String.valueOf(v.getOrDefault("type", "?"));
            String reason = String.valueOf(v.getOrDefault("reason", "N/A"));
            long   dur    = ((Number) v.getOrDefault("duration", -1L)).longValue();
            String icon   = "guild".equals(type) ? "🏠" : "👤";
            eb.addField(icon + " `" + id + "` (" + type + ")",
                    "**Reason:** " + truncate(reason, 120) + "\n**Duration:** " + formatDuration(dur),
                    false);
        }

        return eb.build();
    }

    /**
     * Parses a duration string (7d / 24h / 30m / 60s) into milliseconds.
     * Returns -1 for permanent or unrecognised input.
     */
    private static long parseDuration(String raw) {
        if (raw == null || raw.isBlank()) return -1L;
        raw = raw.trim().toLowerCase();
        try {
            if (raw.endsWith("d"))  return Long.parseLong(raw.replace("d", "")) * 86_400_000L;
            if (raw.endsWith("h"))  return Long.parseLong(raw.replace("h", "")) * 3_600_000L;
            if (raw.endsWith("m"))  return Long.parseLong(raw.replace("m", "")) * 60_000L;
            if (raw.endsWith("s"))  return Long.parseLong(raw.replace("s", "")) * 1_000L;
        } catch (NumberFormatException ignored) {}
        return -1L;
    }

    private static String formatDuration(long ms) {
        if (ms < 0) return "Permanent";
        long secs = ms / 1000;
        if (secs < 60)    return secs + "s";
        if (secs < 3600)  return (secs / 60) + "m";
        if (secs < 86400) return (secs / 3600) + "h";
        return (secs / 86400) + "d";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
