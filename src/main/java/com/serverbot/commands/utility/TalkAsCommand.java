package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedJsonUtils;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * TalkAs command for sending webhook messages with custom names and avatars
 */
public class TalkAsCommand implements SlashCommand {
    
    private static final Logger logger = LoggerFactory.getLogger(TalkAsCommand.class);

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "talkas.use")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", 
                "You need the `talkas.use` permission to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        OptionMapping nameOption = event.getOption("name");
        OptionMapping messageOption = event.getOption("message");
        OptionMapping avatarOption = event.getOption("avatar");
        OptionMapping embedJsonOption = event.getOption("embed_json");

        if (nameOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters [T01]",
                "The `name` parameter is required.\n" +
                "Error Code: **T01** - Missing TalkAs Parameters"
            )).setEphemeral(true).queue();
            return;
        }

        String name = nameOption.getAsString();
        String message = messageOption != null ? messageOption.getAsString() : null;
        String avatarUrl = avatarOption != null ? avatarOption.getAsString() : null;
        String embedJson = embedJsonOption != null ? embedJsonOption.getAsString() : null;

        // Must have at least message or embed
        if ((message == null || message.isBlank()) && (embedJson == null || embedJson.isBlank())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Content [T01]",
                "You must provide at least a `message` or `embed_json`.\n" +
                "Use `/embedgui` to build embeds interactively."
            )).setEphemeral(true).queue();
            return;
        }

        // Validate name length
        if (name.length() < 1 || name.length() > 80) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Name [T02]",
                "Name must be between 1 and 80 characters.\n" +
                "Error Code: **T02** - Invalid TalkAs Name"
            )).setEphemeral(true).queue();
            return;
        }

        // Validate message length if provided
        if (message != null && message.length() > 2000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Message [T03]",
                "Message must be 2000 characters or fewer.\n" +
                "Error Code: **T03** - Invalid TalkAs Message"
            )).setEphemeral(true).queue();
            return;
        }

        // Validate avatar URL if provided
        if (avatarUrl != null && !isValidImageUrl(avatarUrl)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Avatar URL [T04]",
                "Avatar must be a valid image URL (jpg, jpeg, png, gif, webp).\n" +
                "Error Code: **T04** - Invalid Avatar URL"
            )).setEphemeral(true).queue();
            return;
        }

        // Parse embed JSON if provided
        EmbedBuilder parsedEmbed = null;
        if (embedJson != null && !embedJson.isBlank()) {
            try {
                parsedEmbed = EmbedJsonUtils.parseEmbed(embedJson);
            } catch (Exception e) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Embed JSON [T09]",
                    "Failed to parse embed JSON: " + e.getMessage() + "\n" +
                    "Use `/embedgui` to build and export valid embed JSON."
                )).setEphemeral(true).queue();
                return;
            }
        }

        // Parse buttons from embed JSON if provided
        List<Button> buttons = new ArrayList<>();
        if (embedJson != null && !embedJson.isBlank()) {
            try {
                buttons = EmbedJsonUtils.parseButtonsFromJson(embedJson);
            } catch (Exception e) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Buttons JSON [T10]",
                    "Failed to parse buttons from embed JSON: " + e.getMessage() + "\n" +
                    "Use `/embedgui` to build and export valid embed JSON."
                )).setEphemeral(true).queue();
                return;
            }
        }

        TextChannel channel = event.getChannel().asTextChannel();

        // Send initial response
        event.reply("🔄 Preparing webhook message...").setEphemeral(true).queue();

        // Find or create webhook
        final EmbedBuilder finalEmbed = parsedEmbed;
        final List<Button> finalButtons = buttons;
        findOrCreateWebhook(channel, name, avatarUrl, message, finalEmbed, finalButtons, event);
    }

    private void findOrCreateWebhook(TextChannel channel, String name, String avatarUrl, String message, EmbedBuilder embed, List<Button> buttons, SlashCommandInteractionEvent event) {
        channel.retrieveWebhooks().queue(
            webhooks -> {
                Webhook webhook = webhooks.stream()
                    .filter(w -> w.getName().equals("ServerBot TalkAs"))
                    .findFirst()
                    .orElse(null);

                if (webhook != null) {
                    sendWebhookMessage(webhook, name, avatarUrl, message, embed, buttons, event);
                } else {
                    channel.createWebhook("ServerBot TalkAs")
                        .queue(
                            newWebhook -> sendWebhookMessage(newWebhook, name, avatarUrl, message, embed, buttons, event),
                            throwable -> {
                                logger.warn("Failed to create webhook: {}", throwable.getMessage());
                                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                    "Webhook Creation Failed [T05]",
                                    "Failed to create webhook. Make sure I have 'Manage Webhooks' permission.\n" +
                                    "Error Code: **T05** - Webhook Creation Failed"
                                )).queue();
                            }
                        );
                }
            },
            throwable -> {
                logger.warn("Failed to retrieve webhooks: {}", throwable.getMessage());
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Webhook Access Failed [T06]",
                    "Failed to access webhooks. Make sure I have 'Manage Webhooks' permission.\n" +
                    "Error Code: **T06** - Webhook Access Failed"
                )).queue();
            }
        );
    }

    private void sendWebhookMessage(Webhook webhook, String name, String avatarUrl, String message, EmbedBuilder embed, List<Button> buttons, SlashCommandInteractionEvent event) {
        try {
            MessageCreateBuilder builder = new MessageCreateBuilder();
            if (message != null && !message.isBlank()) {
                builder.setContent(message);
            }
            if (embed != null) {
                builder.setEmbeds(embed.build());
            }
            // Add buttons in rows of 5
            if (!buttons.isEmpty()) {
                List<ActionRow> rows = new ArrayList<>();
                for (int i = 0; i < buttons.size(); i += 5) {
                    rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
                }
                builder.setComponents(rows);
            }
            MessageCreateData messageData = builder.build();

            var messageAction = webhook.sendMessage(messageData).setUsername(name);
            if (avatarUrl != null) {
                messageAction = messageAction.setAvatarUrl(avatarUrl);
            }

            messageAction.queue(
                success -> event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Message Sent",
                    "✅ Webhook message sent successfully as **" + name + "**!"
                )).queue(),
                throwable -> {
                    logger.warn("Failed to send webhook message: {}", throwable.getMessage());
                    event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Message Send Failed [T07]",
                        "Failed to send webhook message: " + throwable.getMessage() + "\n" +
                        "Error Code: **T07** - Message Send Failed"
                    )).queue();
                }
            );
        } catch (Exception e) {
            logger.warn("Error preparing webhook message: {}", e.getMessage());
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "Message Preparation Failed [T08]",
                "Failed to prepare webhook message: " + e.getMessage() + "\n" +
                "Error Code: **T08** - Message Preparation Failed"
            )).queue();
        }
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return false;
        }
        
        try {
            URL urlObj = new URL(url);
            String path = urlObj.getPath().toLowerCase();
            return path.endsWith(".jpg") || path.endsWith(".jpeg") || 
                   path.endsWith(".png") || path.endsWith(".gif") || 
                   path.endsWith(".webp");
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getName() {
        return "talkas";
    }

    @Override
    public String getDescription() {
        return "Send a message as a webhook with custom name and avatar";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        return Commands.slash("talkas", "Send a message as a webhook with custom name and avatar")
                .addOptions(
                    new OptionData(OptionType.STRING, "name", "The name to display for the webhook message", true),
                    new OptionData(OptionType.STRING, "message", "The message content to send (optional if embed_json provided)", false),
                    new OptionData(OptionType.STRING, "embed_json", "Embed JSON to attach — optionally include a \"buttons\" key (use /embedgui)", false),
                    new OptionData(OptionType.STRING, "avatar", "Avatar URL for the webhook (optional)", false)
                );
    }
}