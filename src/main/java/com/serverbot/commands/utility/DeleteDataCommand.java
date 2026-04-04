package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.ProxyService;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Allows users to request deletion of all their personal data stored by the bot.
 * Uses a confirmation button to prevent accidental data loss.
 * Required for compliance with the Discord Developer Terms of Service.
 */
public class DeleteDataCommand extends ListenerAdapter implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(DeleteDataCommand.class);

    // Track pending deletion confirmations: userId -> expiry timestamp
    private static final Map<String, Long> pendingConfirmations = new ConcurrentHashMap<>();
    private static final long CONFIRMATION_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(2);

    private static final String BUTTON_CONFIRM = "deletedata_confirm";
    private static final String BUTTON_CANCEL = "deletedata_cancel";

    @Override
    public String getName() {
        return "deletedata";
    }

    @Override
    public String getDescription() {
        return "Delete all your personal data stored by the bot";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.WARNING_COLOR)
                .setTitle(CustomEmojis.WARN + " Data Deletion Request")
                .setDescription("You are about to **permanently delete** all of your personal data stored by this bot.")
                .addField("⚠️ This will delete:",
                        "• Economy balances and transaction history\n" +
                        "• Leveling XP and progress\n" +
                        "• Warning records (where you are the subject)\n" +
                        "• Moderation log entries (where you are the target)\n" +
                        "• Suspicious account records\n" +
                        "• Pending report messages\n" +
                        "• Proxy members and groups you own", false)
                .addField(CustomEmojis.ERROR + " Warning",
                        "**This action is irreversible.** Your economy balance, levels, and all other " +
                        "data will be permanently erased across all servers.", false)
                .setFooter("This confirmation expires in 2 minutes.");

        // Store pending confirmation
        pendingConfirmations.put(userId, System.currentTimeMillis() + CONFIRMATION_TIMEOUT_MS);

        event.replyEmbeds(embed.build())
                .addComponents(ActionRow.of(
                        Button.danger(BUTTON_CONFIRM + ":" + userId, "Confirm Deletion")
                                .withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromFormatted(CustomEmojis.TRASH)),
                        Button.secondary(BUTTON_CANCEL + ":" + userId, "Cancel")
                ))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith(BUTTON_CONFIRM + ":")) {
            handleConfirm(event, componentId.substring((BUTTON_CONFIRM + ":").length()));
        } else if (componentId.startsWith(BUTTON_CANCEL + ":")) {
            handleCancel(event, componentId.substring((BUTTON_CANCEL + ":").length()));
        }
    }

    private void handleConfirm(ButtonInteractionEvent event, String targetUserId) {
        String clickerId = event.getUser().getId();

        // Only the requesting user can confirm
        if (!clickerId.equals(targetUserId)) {
            event.reply(CustomEmojis.ERROR + " You can only delete your own data.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check expiry
        Long expiry = pendingConfirmations.get(clickerId);
        if (expiry == null || System.currentTimeMillis() > expiry) {
            pendingConfirmations.remove(clickerId);
            event.reply(CustomEmojis.ERROR + " This confirmation has expired. Please run `/deletedata` again.")
                    .setEphemeral(true).queue();
            return;
        }
        pendingConfirmations.remove(clickerId);

        // Disable buttons
        event.editComponents().queue();

        // Perform deletion
        int categoriesCleared = 0;
        int proxyMembersDeleted = 0;

        try {
            // Delete from FileStorageManager
            FileStorageManager storage = ServerBot.getStorageManager();
            if (storage != null) {
                categoriesCleared = storage.deleteAllUserData(clickerId);
            }

            // Delete proxy members owned by this user
            ProxyService proxyService = ServerBot.getProxyService();
            if (proxyService != null) {
                proxyMembersDeleted = deleteProxyDataForUser(proxyService, clickerId);
            }
        } catch (Exception e) {
            logger.error("Error during data deletion for user {}: {}", clickerId, e.getMessage(), e);
            event.getHook().sendMessageEmbeds(
                    EmbedUtils.createErrorEmbed("Data Deletion Error",
                            "An error occurred while deleting your data. Please contact the bot owner.")
            ).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder result = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Data Deleted")
                .setDescription("Your personal data has been permanently deleted.")
                .addField("Summary",
                        "• **" + categoriesCleared + "** data categories cleared\n" +
                        "• **" + proxyMembersDeleted + "** proxy members removed", false)
                .addField("Note",
                        "Server-level configuration data (e.g. guild settings, permissions) is managed by " +
                        "server administrators and is not affected by this command.", false);

        event.getHook().sendMessageEmbeds(result.build()).setEphemeral(true).queue();
        logger.info("User {} deleted all their personal data ({} categories, {} proxy members)",
                clickerId, categoriesCleared, proxyMembersDeleted);
    }

    private void handleCancel(ButtonInteractionEvent event, String targetUserId) {
        String clickerId = event.getUser().getId();

        if (!clickerId.equals(targetUserId)) {
            event.reply(CustomEmojis.ERROR + " You can only cancel your own request.")
                    .setEphemeral(true).queue();
            return;
        }

        pendingConfirmations.remove(clickerId);
        event.editComponents().queue();
        event.getHook().sendMessageEmbeds(
                EmbedUtils.createInfoEmbed("Cancelled", "Data deletion cancelled. Your data has not been modified.")
        ).setEphemeral(true).queue();
    }

    /**
     * Deletes all proxy members and groups owned by a specific user.
     * @return the number of proxy members deleted
     */
    private int deleteProxyDataForUser(ProxyService proxyService, String userId) {
        int count = 0;
        try {
            // Get all members owned by this user across all guilds
            java.util.List<com.serverbot.models.ProxyMember> allMembers = proxyService.getAllMembersForOwner(userId);
            if (allMembers != null) {
                for (com.serverbot.models.ProxyMember member : allMembers) {
                    String result = proxyService.deleteMember(member.getMemberId()).join();
                    if ("SUCCESS".equals(result)) {
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error deleting proxy data for user {}: {}", userId, e.getMessage());
        }
        return count;
    }

    public static CommandData getCommandData() {
        return Commands.slash("deletedata", "Delete all your personal data stored by the bot");
    }
}
