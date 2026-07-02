package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.context.CommandContext;
import com.serverbot.utils.context.SlashCommandContext;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Balance command to check user's economy balance
 */
public class BalanceCommand implements SlashCommand {

    @Override
    public boolean supportsCommandContext() {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        executeWithContext(new SlashCommandContext(event));
    }

    @Override
    public void executeWithContext(CommandContext ctx) {
        if (!ctx.isFromGuild()) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers."));
            return;
        }

        User targetUser = ctx.hasOption("user") ? ctx.getUserOption("user") : ctx.getUser();

        if (targetUser == null) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed(
                    "User Not Found", "Could not find the specified user."));
            return;
        }

        if (targetUser.isBot()) {
            ctx.replyEphemeral(EmbedUtils.createErrorEmbed("Invalid Target", "Invalid target!"));
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();
        String guildId = ctx.getGuildId();
        String userId = targetUser.getId();

        long balance = storage.getBalance(guildId, userId);
        String currencyIcon = storage.getCurrencyIcon(guildId);
        String currencyName = storage.getCurrencyName(guildId);

        String title = targetUser.equals(ctx.getUser())
                ? currencyIcon + " Your Balance"
                : currencyIcon + " " + targetUser.getName() + "'s Balance";

        String description = String.format("**Balance:** %,d %s", balance, currencyName);

        ctx.replyRespectingPreference(EmbedUtils.createSuccessEmbed(title, description));
    }

    @Override
    public String getName() {
        return "balance";
    }

    @Override
    public String getDescription() {
        return "Check your or another user's balance";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    public static CommandData getCommandData() {
        return Commands.slash("balance", "Check your or another user's balance")
                .addOption(OptionType.USER, "user", "The user to check balance for", false);
    }
}

