package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.EmbedUtils;
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
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();

        if (targetUser.isBot()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Target",
                    "Invalid target!")).setEphemeral(true).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();
        String guildId = event.getGuild().getId();
        String userId = targetUser.getId();

        long balance = storage.getBalance(guildId, userId);

        String title = targetUser.equals(event.getUser()) ? "Your Balance" : targetUser.getName() + "'s Balance";

        String description = String.format("**Balance:** %,d coins", balance);

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(title, description)).queue();
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
