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
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.InteractionContextType;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Command for managing command prefix settings.
 * Allows server admins to set/add/remove prefixes or view current status.
 */
public class PrefixCommand implements SlashCommand {

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
            case "set"    -> handleSetPrefix(event);
            case "add"    -> handleAddPrefix(event);
            case "remove" -> handleRemovePrefix(event);
            case "status" -> showStatus(event);
            default       -> showStatus(event);
        }
    }

    private void handleSetPrefix(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping prefixOption = event.getOption("prefix");

        if (prefixOption == null) {
            List<String> current = ServerBot.getStorageManager().getPrefixes(guildId);
            DismissibleMessage.reply(event, EmbedUtils.createInfoEmbed(
                "Current Prefixes",
                "Active prefixes: " + String.join("  ", current.stream().map(p -> "`" + p + "`").toList()) + "\n\n" +
                "Use `/prefix set prefix:<new>` to replace all with one prefix.\n" +
                "Use `/prefix add` to add an extra prefix.\n" +
                "Use `/prefix remove` to remove a specific prefix."
            ), true);
            return;
        }

        String newPrefix = prefixOption.getAsString().trim();
        if (!validatePrefix(event, newPrefix)) return;

        ServerBot.getStorageManager().setPrefix(guildId, newPrefix);

        DismissibleMessage.reply(event, new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS_COLOR)
            .setTitle(CustomEmojis.SUCCESS + " Prefix Updated")
            .setDescription("The command prefix has been set to: `" + newPrefix + "`\n" +
                "All previous prefixes have been replaced.")
            .addField("Example Usage", "`" + newPrefix + "help`, `" + newPrefix + "ping`, etc.", false)
            .build(), true);
    }

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
                "You cannot remove the only active prefix. Use `/prefix set` to change it instead."
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

    private void showStatus(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        List<String> prefixes = ServerBot.getStorageManager().getPrefixes(guildId);
        String prefixDisplay = String.join("  ", prefixes.stream().map(p -> "`" + p + "`").toList());

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(EmbedUtils.INFO_COLOR)
            .setTitle(CustomEmojis.SETTING + " Prefix Settings")
            .addField("Active Prefixes", prefixDisplay, false)
            .addField("Usage",
                "`/prefix set prefix:<new>` — Replace all prefixes with one\n" +
                "`/prefix add prefix:<p>` — Add an additional prefix\n" +
                "`/prefix remove prefix:<p>` — Remove a prefix\n" +
                "`/prefix status` — Show this status", false);

        DismissibleMessage.reply(event, embed.build(), true);
    }

    @Override
    public String getName() { return "prefix"; }

    @Override
    public String getDescription() { return "Configure command prefix settings for this server"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.CONFIGURATION; }

    public static CommandData getCommandData() {
        return Commands.slash("prefix", "Configure command prefix settings for this server")
            .addSubcommands(
                new SubcommandData("set", "Replace all prefixes with a single new prefix")
                    .addOption(OptionType.STRING, "prefix", "The new prefix (1-5 characters)", false),
                new SubcommandData("add", "Add an additional command prefix")
                    .addOption(OptionType.STRING, "prefix", "The prefix to add (1-5 characters, no spaces)", true),
                new SubcommandData("remove", "Remove a command prefix")
                    .addOption(OptionType.STRING, "prefix", "The prefix to remove", true),
                new SubcommandData("status", "Show current prefix settings")
            )
            .setContexts(InteractionContextType.GUILD);
    }
}
