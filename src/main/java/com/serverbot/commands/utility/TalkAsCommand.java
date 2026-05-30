package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.models.ProxyMember;
import com.serverbot.services.ProxyService;
import com.serverbot.utils.EmbedJsonUtils;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
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
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * TalkAs command for sending webhook messages with custom names and avatars.
 *
 * Permission behaviour:
 * talkas.use â†’ full mode: arbitrary name, avatar, message, embed_json
 * proxy.use â†’ proxy mode: name must match one of the user's proxy members;
 * that proxy's display-name and avatar are used automatically
 * neither â†’ denied
 */
public class TalkAsCommand implements SlashCommand {

    private static final Logger logger = LoggerFactory.getLogger(TalkAsCommand.class);

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        boolean hasTalkAs = PermissionManager.hasPermission(member, "talkas.use");
        boolean hasProxy = PermissionManager.hasPermission(member, "proxy.use");

        if (!hasTalkAs && !hasProxy) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need `talkas.use` to use this command freely, or `proxy.use` to send as one of your proxy members."))
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping nameOption = event.getOption("name");
        OptionMapping messageOption = event.getOption("message");
        OptionMapping avatarOption = event.getOption("avatar");
        OptionMapping embedJsonOption = event.getOption("embed_json");

        if (nameOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Parameters [T01]",
                    "The `name` parameter is required.")).setEphemeral(true).queue();
            return;
        }

        String nameValue = nameOption.getAsString();
        String name;
        String avatarUrl;

        if (hasTalkAs) {
            // â”€â”€ Full mode
            // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            name = nameValue;
            avatarUrl = avatarOption != null ? avatarOption.getAsString() : null;
        } else {
            // â”€â”€ Proxy-only mode
            // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
            // The value must match one of the user's proxy members.
            String guildId = event.getGuild().getId();
            ProxyService proxyService = ServerBot.getProxyService();
            List<ProxyMember> userProxies = proxyService.getUserMembers(event.getUser().getId(), guildId);

            ProxyMember matched = userProxies.stream()
                    .filter(m -> m.getName().equalsIgnoreCase(nameValue)
                            || m.getDisplayName().equalsIgnoreCase(nameValue))
                    .findFirst().orElse(null);

            if (matched == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Proxy Not Found [T11]",
                        "No proxy member named `" + nameValue + "` was found.\n" +
                                "Use `/proxy list` to see your members, or pick one from the autocomplete dropdown.\n" +
                                "Error Code: **T11** - Proxy Not Found"))
                        .setEphemeral(true).queue();
                return;
            }

            name = matched.getDisplayName(); // getDisplayName() falls back to name if null
            // Prefer original CDN URL for webhooks; fall back to cached local path
            String orig = matched.getOriginalAvatarUrl();
            avatarUrl = (orig != null && !orig.isBlank()) ? orig : matched.getAvatarUrl();
        }

        String message = messageOption != null ? messageOption.getAsString() : null;
        String embedJson = embedJsonOption != null ? embedJsonOption.getAsString() : null;

        // Must have at least message or embed
        if ((message == null || message.isBlank()) && (embedJson == null || embedJson.isBlank())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Content [T01]",
                    "You must provide at least a `message` or `embed_json`.\n" +
                            "Use `/embedgui` to build embeds interactively."))
                    .setEphemeral(true).queue();
            return;
        }

        // Validate name length
        if (name.length() < 1 || name.length() > 80) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Name [T02]",
                    "Name must be between 1 and 80 characters.\n" +
                            "Error Code: **T02** - Invalid TalkAs Name"))
                    .setEphemeral(true).queue();
            return;
        }

        // Validate message length if provided
        if (message != null && message.length() > 2000) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Message [T03]",
                    "Message must be 2000 characters or fewer.\n" +
                            "Error Code: **T03** - Invalid TalkAs Message"))
                    .setEphemeral(true).queue();
            return;
        }

        // Validate avatar URL if provided (only relevant in full mode)
        if (avatarUrl != null && !isValidImageUrl(avatarUrl)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Avatar URL [T04]",
                    "Avatar must be a valid image URL (jpg, jpeg, png, gif, webp).\n" +
                            "Error Code: **T04** - Invalid Avatar URL"))
                    .setEphemeral(true).queue();
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
                                "Use `/embedgui` to build and export valid embed JSON."))
                        .setEphemeral(true).queue();
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
                                "Use `/embedgui` to build and export valid embed JSON."))
                        .setEphemeral(true).queue();
                return;
            }
        }

        TextChannel channel = event.getChannel().asTextChannel();

        event.reply("ðŸ”„ Preparing webhook message...").setEphemeral(true).queue();

        final EmbedBuilder finalEmbed = parsedEmbed;
        final List<Button> finalButtons = buttons;
        final String finalName = name;
        final String finalAvatar = avatarUrl;
        findOrCreateWebhook(channel, finalName, finalAvatar, message, finalEmbed, finalButtons, event);
    }

    // â”€â”€ Autocomplete
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        Member member = event.getMember();
        boolean hasTalkAs = PermissionManager.hasPermission(member, "talkas.use");

        // Full talkas permission â€” they can type any name freely; no suggestions
        // needed.
        if (hasTalkAs) {
            event.replyChoices(Collections.emptyList()).queue();
            return;
        }

        // Proxy-only mode â€” suggest the user's proxy members (up to 25).
        String guildId = event.getGuild().getId();
        ProxyService proxyService = ServerBot.getProxyService();
        List<ProxyMember> userProxies = proxyService.getUserMembers(event.getUser().getId(), guildId);

        String typed = event.getFocusedOption().getValue().toLowerCase();

        List<Command.Choice> choices = userProxies.stream()
                .filter(m -> m.getName().toLowerCase().startsWith(typed)
                        || m.getDisplayName().toLowerCase().startsWith(typed))
                .limit(25)
                .map(m -> new Command.Choice(m.getDisplayName(), m.getName()))
                .collect(Collectors.toList());

        event.replyChoices(choices).queue();
    }

    // â”€â”€ Webhook helpers
    // â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private void findOrCreateWebhook(TextChannel channel, String name, String avatarUrl, String message,
            EmbedBuilder embed, List<Button> buttons, SlashCommandInteractionEvent event) {
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
                                        newWebhook -> sendWebhookMessage(newWebhook, name, avatarUrl, message, embed,
                                                buttons, event),
                                        throwable -> {
                                            logger.warn("Failed to create webhook: {}", throwable.getMessage());
                                            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                                    "Webhook Creation Failed [T05]",
                                                    "Failed to create webhook. Make sure I have 'Manage Webhooks' permission.\n"
                                                            +
                                                            "Error Code: **T05** - Webhook Creation Failed"))
                                                    .queue();
                                        });
                    }
                },
                throwable -> {
                    logger.warn("Failed to retrieve webhooks: {}", throwable.getMessage());
                    event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                            "Webhook Access Failed [T06]",
                            "Failed to access webhooks. Make sure I have 'Manage Webhooks' permission.\n" +
                                    "Error Code: **T06** - Webhook Access Failed"))
                            .queue();
                });
    }

    private void sendWebhookMessage(Webhook webhook, String name, String avatarUrl, String message, EmbedBuilder embed,
            List<Button> buttons, SlashCommandInteractionEvent event) {
        try {
            MessageCreateBuilder builder = new MessageCreateBuilder();
            if (message != null && !message.isBlank())
                builder.setContent(message);
            if (embed != null)
                builder.setEmbeds(embed.build());
            if (!buttons.isEmpty()) {
                List<ActionRow> rows = new ArrayList<>();
                for (int i = 0; i < buttons.size(); i += 5)
                    rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
                builder.setComponents(rows);
            }
            MessageCreateData messageData = builder.build();

            var messageAction = webhook.sendMessage(messageData).setUsername(name);
            if (avatarUrl != null)
                messageAction = messageAction.setAvatarUrl(avatarUrl);

            messageAction.queue(
                    success -> event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                            "Message Sent",
                            "âœ… Webhook message sent successfully as **" + name + "**!")).queue(),
                    throwable -> {
                        logger.warn("Failed to send webhook message: {}", throwable.getMessage());
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Message Send Failed [T07]",
                                "Failed to send webhook message: " + throwable.getMessage() + "\n" +
                                        "Error Code: **T07** - Message Send Failed"))
                                .queue();
                    });
        } catch (Exception e) {
            logger.warn("Error preparing webhook message: {}", e.getMessage());
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Message Preparation Failed [T08]",
                    "Failed to prepare webhook message: " + e.getMessage() + "\n" +
                            "Error Code: **T08** - Message Preparation Failed"))
                    .queue();
        }
    }

    private boolean isValidImageUrl(String url) {
        if (url == null || url.trim().isEmpty())
            return false;
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

    public static CommandData getCommandData() {
        return Commands.slash("talkas", "Send a message as a webhook with custom name and avatar")
                .addOptions(
                        new OptionData(OptionType.STRING, "name",
                                "Your proxy member name, or (with talkas.use) any custom name", true)
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "message",
                                "The message content to send (optional if embed_json provided)", false),
                        new OptionData(OptionType.STRING, "embed_json",
                                "Embed JSON to attach â€” optionally include a \"buttons\" key (use /embedgui)", false),
                        new OptionData(OptionType.STRING, "avatar",
                                "Avatar URL for the webhook â€” only used with talkas.use permission", false));
    }
}
