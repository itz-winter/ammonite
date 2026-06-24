package com.serverbot.commands.economy;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.ServerBot;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Currency command to show/edit the server's currency name and icon
 * Default: Points | 🪙
 */
public class CurrencyCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "economy.admin.currency")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You don't have permission to manage the currency settings!"))
                    .setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String subcommand = event.getSubcommandName();

        if (subcommand == null) {
            // Show current settings
            String name = ServerBot.getStorageManager().getCurrencyName(guildId);
            String icon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Server Currency",
                    "**Name:** " + name + "\n**Icon:** " + icon + "\n\n" +
                    "Use `/currency name <text>` or `/currency icon <emoji>` to change."))
                    .setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "name" -> {
                String name = event.getOption("name").getAsString();
                if (name.length() > 50) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Too Long", "Currency name must be 50 characters or less."))
                            .setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().setCurrencyName(guildId, name);
                String icon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "Currency Updated",
                        "**Name:** " + name + " " + icon)).setEphemeral(true).queue();
            }
            case "icon" -> {
                String icon = event.getOption("icon").getAsString();
                if (icon.length() > 10) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Too Long", "Currency icon must be 10 characters or less."))
                            .setEphemeral(true).queue();
                    return;
                }
                ServerBot.getStorageManager().setCurrencyIcon(guildId, icon);
                String name = ServerBot.getStorageManager().getCurrencyName(guildId);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "Currency Updated",
                        "**Name:** " + name + " " + icon)).setEphemeral(true).queue();
            }
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Subcommand", "Use `name` or `icon`.")).setEphemeral(true).queue();
            }
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("currency", "Set the server's currency name and icon")
                .addSubcommands(
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("name", "Set currency name (default: Points)")
                                .addOption(OptionType.STRING, "name", "Currency name", true),
                        new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("icon", "Set currency icon (default: 🪙)")
                                .addOption(OptionType.STRING, "icon", "Currency icon emoji", true));
    }

    @Override
    public String getName() { return "currency"; }

    @Override
    public String getDescription() { return "Set the server's currency name and icon"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.ECONOMY; }

    @Override
    public boolean requiresPermissions() { return true; }
}