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
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.util.List;
import java.util.Set;

/**
 * /servermessages — configure per-server message pools for various bot
 * responses.
 *
 * Supported types: work, daily, daily_claimed
 *
 * Subcommands:
 * list [type] — list all messages for a type
 * add <type> <message> — add a message
 * remove <type> <pos> — remove message at 1-based position
 * reset <type> — reset to default messages
 */
public class ServerMessagesCommand implements SlashCommand {

    /** Valid message types and their human-readable label. */
    private static final Set<String> VALID_TYPES = Set.of("work", "daily", "daily_claimed");

    private static final String TYPE_LIST = String.join(", ", VALID_TYPES.stream().sorted().toList());

    @Override
    public String getName() {
        return "servermessages";
    }

    @Override
    public String getDescription() {
        return "Configure per-server message pools for bot responses (work, daily, etc.)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean isOwnerOnly() {
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "This command can only be used in servers."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.settings")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Insufficient Permissions",
                    "You need the `admin.settings` permission.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String sub = event.getSubcommandName();
        if (sub == null) {
            showHelp(event);
            return;
        }
        switch (sub) {
            case "list" -> handleList(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "reset" -> handleReset(event);
            default -> showHelp(event);
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String type = event.getOption("type", OptionMapping::getAsString);

        if (type == null) {
            // Show summary of all types
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(EmbedUtils.INFO_COLOR)
                    .setTitle(CustomEmojis.INFO + " Server Message Pools")
                    .setDescription("Use `/servermessages list type:<type>` to see messages for a specific type.\n" +
                            "Available types: `" + TYPE_LIST + "`");
            for (String t : VALID_TYPES.stream().sorted().toList()) {
                List<String> msgs = ServerBot.getStorageManager().getCustomGuildMessages(guildId, t);
                embed.addField(t, msgs == null ? "*Using defaults (" + getDefaultCount(t) + " messages)*"
                        : msgs.size() + " custom message(s)", true);
            }
            DismissibleMessage.reply(event, embed.build(), true);
            return;
        }

        if (!VALID_TYPES.contains(type)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Type", "Valid types: `" + TYPE_LIST + "`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        List<String> msgs = ServerBot.getStorageManager().getCustomGuildMessages(guildId, type);
        if (msgs == null || msgs.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed("Default Messages",
                    "No custom messages set for `" + type
                            + "`. Using the built-in defaults.\nUse `/servermessages add` to add custom ones."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < msgs.size(); i++) {
            sb.append("`").append(i + 1).append(".` ").append(msgs.get(i)).append("\n");
        }
        DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.INFO_COLOR)
                .setTitle("📝 Custom Messages: " + type)
                .setDescription(sb.toString())
                .setFooter(msgs.size() + " message(s)")
                .build(), true);
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String type = event.getOption("type", OptionMapping::getAsString);
        String message = event.getOption("message", OptionMapping::getAsString);

        if (type == null || message == null || message.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Options", "Please provide both type and message."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        if (!VALID_TYPES.contains(type)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Type", "Valid types: `" + TYPE_LIST + "`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        if (message.length() > 500) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Too Long", "Messages must be 500 characters or less."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        int newSize = ServerBot.getStorageManager().addCustomGuildMessage(guildId, type, message);
        DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Message Added")
                .setDescription("Added message #" + newSize + " to `" + type + "`:\n> " + message)
                .build(), true);
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String type = event.getOption("type", OptionMapping::getAsString);
        Integer pos = event.getOption("position", OptionMapping::getAsInt);

        if (type == null || pos == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Options", "Please provide type and position."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        if (!VALID_TYPES.contains(type)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Type", "Valid types: `" + TYPE_LIST + "`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        boolean removed = ServerBot.getStorageManager().removeCustomGuildMessage(guildId, type, pos - 1);
        if (!removed) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Position",
                    "No message at position " + pos + " for `" + type + "`.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Message Removed")
                .setDescription("Removed message #" + pos + " from `" + type + "`.")
                .build(), true);
    }

    private void handleReset(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        String type = event.getOption("type", OptionMapping::getAsString);

        if (type == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Type", "Please specify the message type to reset."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        if (!VALID_TYPES.contains(type)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Type", "Valid types: `" + TYPE_LIST + "`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        ServerBot.getStorageManager().setCustomGuildMessages(guildId, type, null);
        DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Messages Reset")
                .setDescription("Custom messages for `" + type
                        + "` have been removed. The bot will now use the built-in defaults.")
                .build(), true);
    }

    private void showHelp(SlashCommandInteractionEvent event) {
        DismissibleMessage.reply(event, new EmbedBuilder()
                .setColor(EmbedUtils.INFO_COLOR)
                .setTitle(CustomEmojis.SETTING + " Server Messages")
                .setDescription("Configure custom message pools for bot responses.")
                .addField("Available Types", "`" + TYPE_LIST + "`", false)
                .addField("Commands",
                        "`/servermessages list [type]` — view messages\n" +
                                "`/servermessages add <type> <message>` — add a message\n" +
                                "`/servermessages remove <type> <position>` — remove a message\n" +
                                "`/servermessages reset <type>` — restore defaults",
                        false)
                .build(), true);
    }

    private int getDefaultCount(String type) {
        return switch (type) {
            case "work" -> 10;
            default -> 0;
        };
    }

    public static CommandData getCommandData() {
        OptionData typeOption = new OptionData(OptionType.STRING, "type", "Message type", true)
                .addChoice("work", "work")
                .addChoice("daily", "daily")
                .addChoice("daily_claimed", "daily_claimed");
        OptionData typeOptionOptional = new OptionData(OptionType.STRING, "type", "Message type", false)
                .addChoice("work", "work")
                .addChoice("daily", "daily")
                .addChoice("daily_claimed", "daily_claimed");

        return Commands.slash("servermessages", "Configure per-server message pools for bot responses")
                .addSubcommands(
                        new SubcommandData("list", "List custom messages for a type")
                                .addOptions(typeOptionOptional),
                        new SubcommandData("add", "Add a custom message to a pool")
                                .addOptions(typeOption)
                                .addOption(OptionType.STRING, "message", "The message text (max 500 chars)", true),
                        new SubcommandData("remove", "Remove a custom message by position")
                                .addOptions(typeOption)
                                .addOption(OptionType.INTEGER, "position", "1-based position in the list", true),
                        new SubcommandData("reset", "Reset a type to default messages")
                                .addOptions(typeOption))
                .setContexts(InteractionContextType.GUILD);
    }
}
