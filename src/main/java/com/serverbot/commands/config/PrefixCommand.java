package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.DismissibleMessage;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command for managing prefix command settings
 * Allows server owners to:
 * - Enable/disable all prefix commands
 * - Enable/disable specific prefix commands
 * - Change the command prefix
 */
public class PrefixCommand implements SlashCommand {

    // All available prefix commands that can be enabled/disabled
    private static final Set<String> AVAILABLE_PREFIX_COMMANDS = Set.of(
        "work", "ping", "balance", "pay", "baltop", "help", "rank", "leaderboard",
        "echo", "talkas", "permissions", "warn", "ban", "kick", "mute", "timeout",
        "info", "serverinfo", "xp", "daily", "error", "unban", "unmute", "unwarn",
        "gamble", "slots", "flip", "dice", "purge", "automod", "serverstats", "rules",
        "ticket"
    );

    // Commands grouped by category for display
    private static final Map<String, List<String>> COMMAND_CATEGORIES = Map.of(
        "Economy", List.of("work", "balance", "pay", "baltop", "daily", "gamble", "slots", "flip", "dice"),
        "Moderation", List.of("warn", "ban", "kick", "mute", "timeout", "unban", "unmute", "unwarn", "purge"),
        "Utility", List.of("ping", "help", "info", "serverinfo", "serverstats", "echo", "talkas", "rules", "error"),
        "Leveling", List.of("rank", "leaderboard", "xp"),
        "Config", List.of("permissions", "automod", "ticket")
    );

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.prefix")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need the `admin.prefix` permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            showStatus(event);
            return;
        }

        switch (subcommand) {
            case "set" -> handleSetPrefix(event);
            case "add" -> handleAddPrefix(event);
            case "remove" -> handleRemovePrefix(event);
            case "enable" -> handleEnableCommand(event);
            case "disable" -> handleDisableCommand(event);
            case "enable-all" -> handleEnableAll(event);
            case "disable-all" -> handleDisableAll(event);
            case "status" -> showStatus(event);
            case "list" -> listCommands(event);
            default -> showStatus(event);
        }
    }

    /**
     * Handle setting the command prefix (replaces all existing prefixes)
     */
    private void handleSetPrefix(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping prefixOption = event.getOption("prefix");
        
        if (prefixOption == null) {
            // Show current prefixes
            List<String> current = ServerBot.getStorageManager().getPrefixes(guildId);
            DismissibleMessage.reply(event, EmbedUtils.createInfoEmbed(
                "Current Prefixes",
                "Active prefixes: " + String.join("  ", current.stream().map(p -> "`" + p + "`").toList()) + "\n\n" +
                "Use `/prefix set prefix:<new>` to replace all with one prefix.\n" +
                "Use `/prefix add` to add an extra prefix.\n" +
                "Use `/prefix remove` to remove a prefix."
            ), true);
            return;
        }

        String newPrefix = prefixOption.getAsString().trim();
        if (!validatePrefix(event, newPrefix)) return;

        // Save new prefix (replaces all existing prefixes with just this one)
        ServerBot.getStorageManager().setPrefix(guildId, newPrefix);

        DismissibleMessage.reply(event, new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle(CustomEmojis.SUCCESS + " Prefix Updated")
            .setDescription("The command prefix has been set to: `" + newPrefix + "`\n" +
                "All previous prefixes have been replaced.")
            .addField("Example Usage", "`" + newPrefix + "help`, `" + newPrefix + "ping`, etc.", false)
            .build(), true);
    }

    /**
     * Add an additional prefix to the guild's prefix list.
     */
    private void handleAddPrefix(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping prefixOption = event.getOption("prefix");
        if (prefixOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Option", "Please specify a prefix to add."))
                .setEphemeral(true).queue();
            return;
        }
        String newPrefix = prefixOption.getAsString().trim();
        if (!validatePrefix(event, newPrefix)) return;

        boolean added = ServerBot.getStorageManager().addPrefix(guildId, newPrefix);
        if (!added) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Already Exists", "The prefix `" + newPrefix + "` is already active for this server."
            )).setEphemeral(true).queue();
            return;
        }
        List<String> all = ServerBot.getStorageManager().getPrefixes(guildId);
        DismissibleMessage.reply(event, new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle(CustomEmojis.SUCCESS + " Prefix Added")
            .setDescription("Added `" + newPrefix + "` as an active prefix.")
            .addField("All Active Prefixes", String.join("  ", all.stream().map(p -> "`" + p + "`").toList()), false)
            .build(), true);
    }

    /**
     * Remove a prefix from the guild's prefix list.
     */
    private void handleRemovePrefix(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping prefixOption = event.getOption("prefix");
        if (prefixOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Option", "Please specify a prefix to remove."))
                .setEphemeral(true).queue();
            return;
        }
        String removePrefix = prefixOption.getAsString().trim();
        List<String> current = ServerBot.getStorageManager().getPrefixes(guildId);
        if (current.size() == 1 && current.contains(removePrefix)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Cannot Remove",
                "You cannot remove the only active prefix. Use `/prefix set` to change it, or `/prefix disable-all` to turn off prefix commands entirely."
            )).setEphemeral(true).queue();
            return;
        }
        boolean removed = ServerBot.getStorageManager().removePrefix(guildId, removePrefix);
        if (!removed) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Not Found", "The prefix `" + removePrefix + "` is not currently active for this server."
            )).setEphemeral(true).queue();
            return;
        }
        List<String> remaining = ServerBot.getStorageManager().getPrefixes(guildId);
        DismissibleMessage.reply(event, new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle(CustomEmojis.SUCCESS + " Prefix Removed")
            .setDescription("Removed `" + removePrefix + "` from the active prefix list.")
            .addField("Remaining Prefixes", String.join("  ", remaining.stream().map(p -> "`" + p + "`").toList()), false)
            .build(), true);
    }

    /** Validate a prefix string, replying with an error if invalid. Returns true if valid. */
    private boolean validatePrefix(SlashCommandInteractionEvent event, String prefix) {
        if (prefix.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Prefix", "Prefix cannot be empty.")).setEphemeral(true).queue();
            return false;
        }
        if (prefix.length() > 5) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Prefix", "Prefix cannot be longer than 5 characters.")).setEphemeral(true).queue();
            return false;
        }
        if (prefix.contains(" ")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Prefix", "Prefix cannot contain spaces.")).setEphemeral(true).queue();
            return false;
        }
        if (prefix.equals("/")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Prefix", "Cannot use `/` as prefix — it's reserved for slash commands.")).setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    /**
     * Handle enabling a specific command
     */
    private void handleEnableCommand(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping commandOption = event.getOption("command");
        OptionMapping categoryOption = event.getOption("category");

        if (commandOption != null) {
            String commandName = commandOption.getAsString().toLowerCase();
            
            if (!AVAILABLE_PREFIX_COMMANDS.contains(commandName)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Command", "The command `" + commandName + "` is not a valid prefix command.\n" +
                    "Use `/prefix list` to see all available commands."
                )).setEphemeral(true).queue();
                return;
            }

            ServerBot.getStorageManager().enablePrefixCommand(guildId, commandName);

            DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Command Enabled")
                .setDescription("The prefix command `" + commandName + "` has been enabled.")
                .build(), true);

        } else if (categoryOption != null) {
            String category = categoryOption.getAsString();
            List<String> commands = COMMAND_CATEGORIES.get(category);
            
            if (commands == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Category", "The category `" + category + "` is not valid.\n" +
                    "Available categories: " + String.join(", ", COMMAND_CATEGORIES.keySet())
                )).setEphemeral(true).queue();
                return;
            }

            for (String cmd : commands) {
                ServerBot.getStorageManager().enablePrefixCommand(guildId, cmd);
            }

            DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Category Enabled")
                .setDescription("All commands in the `" + category + "` category have been enabled.")
                .addField("Enabled Commands", String.join(", ", commands), false)
                .build(), true);

        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Option", "Please specify a command or category to enable."
            )).setEphemeral(true).queue();
        }
    }

    /**
     * Handle disabling a specific command
     */
    private void handleDisableCommand(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping commandOption = event.getOption("command");
        OptionMapping categoryOption = event.getOption("category");

        if (commandOption != null) {
            String commandName = commandOption.getAsString().toLowerCase();
            
            if (!AVAILABLE_PREFIX_COMMANDS.contains(commandName)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Command", "The command `" + commandName + "` is not a valid prefix command.\n" +
                    "Use `/prefix list` to see all available commands."
                )).setEphemeral(true).queue();
                return;
            }

            ServerBot.getStorageManager().disablePrefixCommand(guildId, commandName);

            DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Command Disabled")
                .setDescription("The prefix command `" + commandName + "` has been disabled.")
                .build(), true);

        } else if (categoryOption != null) {
            String category = categoryOption.getAsString();
            List<String> commands = COMMAND_CATEGORIES.get(category);
            
            if (commands == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Category", "The category `" + category + "` is not valid.\n" +
                    "Available categories: " + String.join(", ", COMMAND_CATEGORIES.keySet())
                )).setEphemeral(true).queue();
                return;
            }

            for (String cmd : commands) {
                ServerBot.getStorageManager().disablePrefixCommand(guildId, cmd);
            }

            DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Category Disabled")
                .setDescription("All commands in the `" + category + "` category have been disabled.")
                .addField("Disabled Commands", String.join(", ", commands), false)
                .build(), true);

        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Option", "Please specify a command or category to disable."
            )).setEphemeral(true).queue();
        }
    }

    /**
     * Handle enabling all prefix commands
     */
    private void handleEnableAll(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        
        ServerBot.getStorageManager().enableAllPrefixCommands(guildId);

        DismissibleMessage.reply(event, new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle(CustomEmojis.SUCCESS + " All Commands Enabled")
            .setDescription("All prefix commands have been enabled for this server.")
            .build(), true);
    }

    /**
     * Handle disabling all prefix commands
     */
    private void handleDisableAll(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        
        ServerBot.getStorageManager().disableAllPrefixCommands(guildId);

        DismissibleMessage.reply(event, new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle(CustomEmojis.SUCCESS + " All Commands Disabled")
            .setDescription("All prefix commands have been disabled for this server.\n" +
                "Slash commands are still available.")
            .build(), true);
    }

    /**
     * Show the current prefix command status
     */
    private void showStatus(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        
        List<String> prefixes = ServerBot.getStorageManager().getPrefixes(guildId);
        boolean allEnabled = ServerBot.getStorageManager().arePrefixCommandsEnabled(guildId);
        Set<String> disabledCommands = ServerBot.getStorageManager().getDisabledPrefixCommands(guildId);

        String prefixDisplay = String.join("  ", prefixes.stream().map(p -> "`" + p + "`").toList());

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(EmbedUtils.INFO_COLOR)
            .setTitle(CustomEmojis.SETTING + " Prefix Command Settings")
            .addField("Active Prefixes", prefixDisplay, true)
            .addField("Status", allEnabled ? CustomEmojis.SUCCESS + " Enabled" : CustomEmojis.ERROR + " Disabled", true);

        if (allEnabled) {
            if (disabledCommands.isEmpty()) {
                embed.addField("Disabled Commands", "None — all commands are enabled", false);
            } else {
                String disabledList = disabledCommands.stream()
                    .sorted()
                    .collect(Collectors.joining(", "));
                embed.addField("Disabled Commands (" + disabledCommands.size() + ")", 
                    "`" + disabledList + "`", false);
            }
        } else {
            embed.setDescription("⚠️ All prefix commands are disabled. Use `/prefix enable-all` to re-enable them.");
        }

        embed.addField("Usage", 
            "`/prefix set prefix:-k` — Replace all prefixes with one\n" +
            "`/prefix add prefix:-k` — Add an additional prefix\n" +
            "`/prefix remove prefix:!` — Remove a prefix\n" +
            "`/prefix enable command:ping` — Enable a command\n" +
            "`/prefix disable command:ping` — Disable a command\n" +
            "`/prefix enable-all` — Re-enable all prefix commands\n" +
            "`/prefix disable-all` — Disable all prefix commands\n" +
            "`/prefix list` — List all commands", false);

        DismissibleMessage.reply(event, embed.build(), true);
    }

    /**
     * List all available prefix commands by category
     */
    private void listCommands(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        Set<String> disabledCommands = ServerBot.getStorageManager().getDisabledPrefixCommands(guildId);
        String prefix = ServerBot.getStorageManager().getPrefix(guildId);

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(EmbedUtils.INFO_COLOR)
            .setTitle(CustomEmojis.INFO + " Available Prefix Commands")
            .setDescription("Current prefix: `" + prefix + "`\n" +
                CustomEmojis.SUCCESS + " = Enabled | " + CustomEmojis.ERROR + " = Disabled");

        for (Map.Entry<String, List<String>> entry : COMMAND_CATEGORIES.entrySet()) {
            String category = entry.getKey();
            List<String> commands = entry.getValue();
            
            StringBuilder sb = new StringBuilder();
            for (String cmd : commands) {
                boolean enabled = !disabledCommands.contains(cmd);
                sb.append(enabled ? CustomEmojis.SUCCESS : CustomEmojis.ERROR)
                  .append(" `").append(cmd).append("`\n");
            }
            
            embed.addField("**" + category + "**", sb.toString(), true);
        }

        DismissibleMessage.reply(event, embed.build(), true);
    }

    @Override
    public String getName() {
        return "prefix";
    }

    @Override
    public String getDescription() {
        return "Configure prefix command settings for this server";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        return Commands.slash("prefix", "Configure prefix command settings for this server")
            .addSubcommands(
                new SubcommandData("set", "Replace all prefixes with a single new prefix for this server")
                    .addOption(OptionType.STRING, "prefix", "The new prefix (1-5 characters)", false),

                new SubcommandData("add", "Add an additional command prefix for this server")
                    .addOption(OptionType.STRING, "prefix", "The prefix to add (1-5 characters, no spaces)", true),

                new SubcommandData("remove", "Remove a command prefix from this server")
                    .addOption(OptionType.STRING, "prefix", "The prefix to remove", true),
                
                new SubcommandData("enable", "Enable a specific prefix command or category")
                    .addOptions(
                        new OptionData(OptionType.STRING, "command", "The command to enable", false)
                            .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "category", "The category to enable", false)
                            .addChoice("Economy", "Economy")
                            .addChoice("Moderation", "Moderation")
                            .addChoice("Utility", "Utility")
                            .addChoice("Leveling", "Leveling")
                            .addChoice("Config", "Config")
                    ),
                
                new SubcommandData("disable", "Disable a specific prefix command or category")
                    .addOptions(
                        new OptionData(OptionType.STRING, "command", "The command to disable", false)
                            .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "category", "The category to disable", false)
                            .addChoice("Economy", "Economy")
                            .addChoice("Moderation", "Moderation")
                            .addChoice("Utility", "Utility")
                            .addChoice("Leveling", "Leveling")
                            .addChoice("Config", "Config")
                    ),
                
                new SubcommandData("enable-all", "Enable all prefix commands"),
                new SubcommandData("disable-all", "Disable all prefix commands"),
                new SubcommandData("status", "Show current prefix command settings"),
                new SubcommandData("list", "List all available prefix commands")
            )
            .setContexts(InteractionContextType.GUILD);
    }

    /**
     * Get available prefix commands for autocomplete
     */
    public static Set<String> getAvailablePrefixCommands() {
        return AVAILABLE_PREFIX_COMMANDS;
    }
}
