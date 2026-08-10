package com.serverbot.commands.owner;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import com.serverbot.utils.context.CommandContext;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.Color;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Bot-owner only command to list all servers the bot is in, with member counts.
 * Supports sorting by member count, name, or ID. 10 servers per page, ephemeral.
 */
public class ListServers implements SlashCommand {

    private static final int SERVERS_PER_PAGE = 10;

    @Override
    public String getName() { return "listservers"; }

    @Override
    public String getDescription() { return "List all servers the bot is in (bot owner only)."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.UTILITY; }

    @Override
    public boolean isOwnerOnly() { return true; }

    @Override
    public boolean isGuildOnly() { return false; }

    @Override
    public boolean supportsCommandContext() { return true; }

    public static CommandData getCommandData() {
        return Commands.slash("listservers", "List all servers the bot is in (bot owner only).")
                .addOptions(
                        new OptionData(OptionType.STRING, "sort",
                                "Sort order (members-desc / members-asc / name / id).", false)
                                .addChoice("Members (High → Low)", "members-desc")
                                .addChoice("Members (Low → High)", "members-asc")
                                .addChoice("Name (A → Z)",         "name")
                                .addChoice("ID",                   "id"),
                        new OptionData(OptionType.INTEGER, "page",
                                "Page number (default: 1).", false)
                                .setMinValue(1));
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

        String sort = event.getOption("sort") != null ? event.getOption("sort").getAsString() : "members-desc";
        int    page = event.getOption("page") != null ? (int) event.getOption("page").getAsLong() : 1;

        event.replyEmbeds(buildEmbed(event.getJDA().getGuilds(), sort, page))
                .setEphemeral(true).queue();
    }

    @Override
    public void executeWithContext(CommandContext ctx) {
        if (!PermissionUtils.isBotOwner(ctx.getUser())) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Access Denied",
                    "Only bot owners can use this command."));
            return;
        }

        int page = 1;
        String pageStr = ctx.getStringOption("page");
        if (pageStr != null) {
            try { page = Math.max(1, Integer.parseInt(pageStr)); } catch (NumberFormatException ignored) {}
        }

        String sort = ctx.getStringOption("sort");
        if (sort == null) sort = "members-desc";

        List<Guild> guilds = ServerBot.getJda() != null ? ServerBot.getJda().getGuilds() : List.of();
        ctx.reply(buildEmbed(guilds, sort, page));
    }

    private MessageEmbed buildEmbed(List<Guild> rawGuilds, String sort, int page) {
        List<Guild> guilds = new java.util.ArrayList<>(rawGuilds);
        switch (sort) {
            case "members-asc" -> guilds.sort(Comparator.comparingInt(Guild::getMemberCount));
            case "name"        -> guilds.sort(Comparator.comparing(Guild::getName, String.CASE_INSENSITIVE_ORDER));
            case "id"          -> guilds.sort(Comparator.comparing(Guild::getId));
            default            -> guilds.sort(Comparator.comparingInt(Guild::getMemberCount).reversed());
        }

        int total      = guilds.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) total / SERVERS_PER_PAGE));
        page = Math.min(page, totalPages);
        int start = (page - 1) * SERVERS_PER_PAGE;
        int end   = Math.min(start + SERVERS_PER_PAGE, total);

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(new Color(88, 101, 242))
                .setTitle("🌐 Server List")
                .setTimestamp(Instant.now())
                .setFooter("Page " + page + "/" + totalPages + " • " + total + " server" + (total == 1 ? "" : "s") + " total");

        if (guilds.isEmpty()) {
            eb.setDescription("The bot is not in any servers.");
            return eb.build();
        }

        for (int i = start; i < end; i++) {
            Guild g    = guilds.get(i);
            int   num  = i + 1;
            eb.addField(
                    num + ". " + g.getName(),
                    "**ID:** `" + g.getId() + "`  •  **Members:** `" + g.getMemberCount() + "`",
                    false);
        }

        return eb.build();
    }
}

