package com.serverbot.commands.moderation;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.WordFilterService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.Color;
import java.util.List;

/**
 * /wordfilter — manage the server's custom word/regex filter.
 *
 * Subcommands:
 *   add <pattern>         — add a plain word or regex pattern
 *   remove <pattern>      — remove by exact pattern
 *   removeat <index>      — remove by 1-based list index
 *   list                  — show all patterns
 *   clear                 — remove all patterns
 *   enable                — enable the filter
 *   disable               — disable the filter
 *   action <action>       — set action: delete | warn | delete+warn
 */
public class WordFilterCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "This command can only be used in servers."))
                    .setEphemeral(true).queue();
            return;
        }
        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "mod.wordfilter")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `mod.wordfilter` permission to manage the word filter."))
                    .setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Unknown Subcommand", "Please specify a subcommand."))
                    .setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "add" -> {
                String pattern = event.getOption("pattern").getAsString().trim();
                String err = WordFilterService.addPattern(guildId, pattern);
                if (err != null) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed("Add Failed", err)).setEphemeral(true).queue();
                } else {
                    event.replyEmbeds(EmbedUtils.createSuccessEmbed("Pattern Added",
                            "Pattern `" + pattern + "` was added to the filter.\n"
                                    + "Total patterns: " + WordFilterService.getPatterns(guildId).size()))
                            .setEphemeral(true).queue();
                }
            }
            case "remove" -> {
                String pattern = event.getOption("pattern").getAsString().trim();
                String err = WordFilterService.removePattern(guildId, pattern);
                if (err != null) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed("Remove Failed", err)).setEphemeral(true).queue();
                } else {
                    event.replyEmbeds(EmbedUtils.createSuccessEmbed("Pattern Removed",
                            "Pattern `" + pattern + "` was removed from the filter.")).setEphemeral(true).queue();
                }
            }
            case "removeat" -> {
                int index = event.getOption("index").getAsInt();
                String err = WordFilterService.removePatternByIndex(guildId, index);
                if (err != null) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed("Remove Failed", err)).setEphemeral(true).queue();
                } else {
                    event.replyEmbeds(EmbedUtils.createSuccessEmbed("Pattern Removed",
                            "Pattern at index " + index + " was removed.")).setEphemeral(true).queue();
                }
            }
            case "list" -> {
                List<String> patterns = WordFilterService.getPatterns(guildId);
                boolean enabled = WordFilterService.isEnabled(guildId);
                String action = WordFilterService.getAction(guildId);
                if (patterns.isEmpty()) {
                    event.replyEmbeds(EmbedUtils.createSuccessEmbed("Word Filter",
                            "No patterns configured yet. Use `/wordfilter add` to add one.")).setEphemeral(true).queue();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < patterns.size(); i++) {
                    sb.append("`").append(i + 1).append("` ").append(patterns.get(i)).append("\n");
                }
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(enabled ? Color.GREEN : Color.GRAY)
                        .setTitle("📋 Word Filter Patterns")
                        .setDescription(sb.toString().trim())
                        .addField("Status", enabled ? "✅ Enabled" : "❌ Disabled", true)
                        .addField("Action", action, true)
                        .addField("Count", String.valueOf(patterns.size()), true);
                event.replyEmbeds(embed.build()).setEphemeral(true).queue();
            }
            case "clear" -> {
                WordFilterService.clearPatterns(guildId);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Word Filter Cleared",
                        "All filter patterns have been removed.")).setEphemeral(true).queue();
            }
            case "enable" -> {
                com.serverbot.ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilterEnabled", true);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Word Filter Enabled",
                        "The word filter is now **enabled**.")).setEphemeral(true).queue();
            }
            case "disable" -> {
                com.serverbot.ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilterEnabled", false);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Word Filter Disabled",
                        "The word filter is now **disabled**.")).setEphemeral(true).queue();
            }
            case "action" -> {
                String actionVal = event.getOption("action").getAsString().toLowerCase();
                if (!actionVal.equals("delete") && !actionVal.equals("warn") && !actionVal.equals("delete+warn")) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Action",
                            "Valid actions: `delete`, `warn`, `delete+warn`")).setEphemeral(true).queue();
                    return;
                }
                com.serverbot.ServerBot.getStorageManager().updateGuildSettings(guildId, "wordFilterAction", actionVal);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Action Updated",
                        "Word filter action set to `" + actionVal + "`.")).setEphemeral(true).queue();
            }
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed("Unknown Subcommand",
                    "Unknown subcommand: " + sub)).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("wordfilter", "Manage the server's word/phrase filter")
                .addSubcommands(
                        new SubcommandData("add", "Add a word or regex pattern to the filter")
                                .addOption(OptionType.STRING, "pattern", "Plain word or regex pattern to block", true),
                        new SubcommandData("remove", "Remove a pattern by exact text")
                                .addOption(OptionType.STRING, "pattern", "The exact pattern to remove", true),
                        new SubcommandData("removeat", "Remove a pattern by its list index")
                                .addOption(OptionType.INTEGER, "index", "1-based index from /wordfilter list", true),
                        new SubcommandData("list", "Show all current filter patterns"),
                        new SubcommandData("clear", "Remove all filter patterns"),
                        new SubcommandData("enable", "Enable the word filter"),
                        new SubcommandData("disable", "Disable the word filter"),
                        new SubcommandData("action", "Set what happens when a message matches a filter")
                                .addOptions(new OptionData(OptionType.STRING, "action",
                                        "Action to take: delete, warn, or delete+warn", true)
                                        .addChoice("Delete message", "delete")
                                        .addChoice("Warn user", "warn")
                                        .addChoice("Delete + Warn", "delete+warn")));
    }

    @Override
    public String getName() {
        return "wordfilter";
    }

    @Override
    public String getDescription() {
        return "Manage the server's word/phrase filter";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }
}
