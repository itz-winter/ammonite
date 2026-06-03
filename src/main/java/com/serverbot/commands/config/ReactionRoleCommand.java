package com.serverbot.commands.config;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.ReactionRoleService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Command for managing reaction role systems
 */
public class ReactionRoleCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Server Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.reactionroles")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Permissions",
                    "You need the `admin.reactionroles` permission to manage reaction roles.")).setEphemeral(true)
                    .queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Usage", "Please specify a subcommand.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        switch (subcommand) {
            case "create" -> handleCreate(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "list" -> handleList(event);
            case "delete" -> handleDelete(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Subcommand", "Unknown subcommand: `" + subcommand + "`.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption("channel");
        OptionMapping titleOption = event.getOption("title");
        OptionMapping descOption = event.getOption("description");

        if (channelOption == null || titleOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Options", "Please provide both a **channel** and a **title**.")).setEphemeral(true)
                    .queue();
            return;
        }

        TextChannel channel = channelOption.getAsChannel().asTextChannel();
        String title = titleOption.getAsString();
        String description = descOption != null ? descOption.getAsString()
                : "React with an emoji below to receive the corresponding role!";

        event.deferReply().queue();

        try {
            String messageId = ReactionRoleService.getInstance()
                    .createReactionRoleMessage(channel, title, description, event.getMember());

            event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "Reaction Role Created",
                    String.format(
                            "A reaction role message has been posted in %s.\n\n" +
                                    "**Message ID:** `%s`\n\n" +
                                    "Use `/reactionrole add message-id:%s emoji:<emoji> role:<role>` to add emoji-role pairs.",
                            channel.getAsMention(), messageId, messageId)))
                    .queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Creation Failed",
                    "Failed to create the reaction role message: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    /**
     * Add an emoji-role pair to a message.
     * If {@code channel} is provided, the message can be any message in that
     * channel (even one not
     * created by this bot). If {@code channel} is omitted, the message must already
     * be tracked
     * (i.e. created via /reactionrole create).
     */
    private void handleAdd(SlashCommandInteractionEvent event) {
        OptionMapping messageIdOption = event.getOption("message-id");
        OptionMapping emojiOption = event.getOption("emoji");
        OptionMapping roleOption = event.getOption("role");
        OptionMapping channelOption = event.getOption("channel"); // optional

        if (messageIdOption == null || emojiOption == null || roleOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Options", "Please provide a **message-id**, **emoji**, and **role**.")).setEphemeral(true)
                    .queue();
            return;
        }

        String messageId = messageIdOption.getAsString();
        String emojiStr = emojiOption.getAsString();
        Role role = roleOption.getAsRole();

        if (!event.getGuild().getSelfMember().canInteract(role)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Role Hierarchy Error",
                    String.format(
                            "I can't assign %s because that role is above my highest role in the hierarchy.\n" +
                                    "Move my role above it and try again.",
                            role.getAsMention())))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        if (channelOption != null) {
            // Arbitrary message path â€” retrieve it first to confirm it exists
            TextChannel channel = channelOption.getAsChannel().asTextChannel();
            channel.retrieveMessageById(messageId).queue(
                    message -> {
                        try {
                            ReactionRoleService.getInstance().attachReactionRoleToExistingMessage(
                                    event.getGuild().getId(), channel.getId(), messageId,
                                    emojiStr, role.getId(), event.getMember());

                            event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                    "Reaction Role Added",
                                    String.format(
                                            "Added a reaction role to the message in %s.\n\n" +
                                                    "**Emoji:** %s\n" +
                                                    "**Role:** %s\n" +
                                                    "**Message ID:** `%s`\n\n" +
                                                    "Members can now react with %s to receive %s.",
                                            channel.getAsMention(), emojiStr, role.getAsMention(),
                                            messageId, emojiStr, role.getAsMention())))
                                    .queue();
                        } catch (Exception e) {
                            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                    "Failed to Add Reaction Role",
                                    "Something went wrong while registering the reaction role: " + e.getMessage()))
                                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                        }
                    },
                    error -> event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Message Not Found",
                            String.format(
                                    "No message with ID `%s` was found in %s.\n" +
                                            "Double-check the message ID and channel.",
                                    messageId, channel.getAsMention())))
                            .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue());
        } else {
            // Tracked-message path â€” message must exist in storage
            try {
                ReactionRoleService.getInstance().addReactionRole(
                        event.getGuild().getId(), messageId, emojiStr, role.getId());

                event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "Reaction Role Added",
                        String.format(
                                "Added a reaction role to message `%s`.\n\n" +
                                        "**Emoji:** %s\n" +
                                        "**Role:** %s\n\n" +
                                        "Members can now react with %s to receive %s.",
                                messageId, emojiStr, role.getAsMention(), emojiStr, role.getAsMention())))
                        .queue();

            } catch (IllegalArgumentException e) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Message Not Tracked",
                        String.format(
                                "No tracked reaction role message found with ID `%s`.\n\n" +
                                        "â€¢ If you created this message with `/reactionrole create`, double-check the ID.\n"
                                        +
                                        "â€¢ If this is an **external** message (not created by me), include the `channel` option:\n"
                                        +
                                        "  `/reactionrole add channel:#channel message-id:%s emoji:%s role:<role>`",
                                messageId, messageId, emojiStr)))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            } catch (Exception e) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Failed to Add Reaction Role",
                        "Something went wrong: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        }
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        OptionMapping messageIdOption = event.getOption("message-id");
        OptionMapping emojiOption = event.getOption("emoji");

        if (messageIdOption == null || emojiOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Options", "Please provide both a **message-id** and an **emoji**.")).setEphemeral(true)
                    .queue();
            return;
        }

        String messageId = messageIdOption.getAsString();
        String emojiStr = emojiOption.getAsString();

        event.deferReply().queue();

        try {
            boolean removed = ReactionRoleService.getInstance()
                    .removeReactionRole(event.getGuild().getId(), messageId, emojiStr);

            if (removed) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "Reaction Role Removed",
                        String.format(
                                "Removed the %s reaction role from message `%s`.",
                                emojiStr, messageId)))
                        .queue();
            } else {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Not Found",
                        String.format(
                                "No reaction role with emoji %s was found on message `%s`.\n" +
                                        "Use `/reactionrole list` to see all active reaction roles.",
                                emojiStr, messageId)))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Removal Failed",
                    "Something went wrong while removing the reaction role: " + e.getMessage())).setEphemeral(true)
                    .queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        try {
            String listInfo = ReactionRoleService.getInstance()
                    .getReactionRolesList(event.getGuild().getId());

            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "Reaction Roles", listInfo)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "List Failed",
                    "Failed to retrieve the reaction roles list: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        OptionMapping messageIdOption = event.getOption("message-id");

        if (messageIdOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Options", "Please provide the **message-id** of the reaction role message to delete."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String messageId = messageIdOption.getAsString();

        event.deferReply().queue();

        try {
            boolean deleted = ReactionRoleService.getInstance()
                    .deleteReactionRoleMessage(event.getGuild().getId(), messageId);

            if (deleted) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "Reaction Role Deleted",
                        String.format(
                                "Successfully deleted the reaction role message `%s` and removed all associated reaction role entries.",
                                messageId)))
                        .queue();
            } else {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Not Found",
                        String.format(
                                "No tracked reaction role message found with ID `%s`.\n" +
                                        "Use `/reactionrole list` to see all active messages.",
                                messageId)))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Deletion Failed",
                    "Something went wrong while deleting the reaction role message: " + e.getMessage()))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("reactionrole", "Manage reaction role systems")
                .addSubcommands(
                        new SubcommandData("create", "Create a new reaction role message in a channel")
                                .addOption(OptionType.CHANNEL, "channel", "Channel to post the message in", true)
                                .addOption(OptionType.STRING, "title", "Title for the embed", true)
                                .addOption(OptionType.STRING, "description", "Description for the embed (optional)",
                                        false),

                        new SubcommandData("add", "Add an emoji-role pair to a message")
                                .addOption(OptionType.STRING, "message-id", "ID of the reaction role message", true)
                                .addOption(OptionType.STRING, "emoji", "Emoji to react with", true)
                                .addOption(OptionType.ROLE, "role", "Role to assign when reacted", true)
                                .addOption(OptionType.CHANNEL, "channel",
                                        "Required only if the message was NOT created by me", false),

                        new SubcommandData("remove", "Remove an emoji-role pair from a message")
                                .addOption(OptionType.STRING, "message-id", "ID of the reaction role message", true)
                                .addOption(OptionType.STRING, "emoji", "Emoji to remove", true),

                        new SubcommandData("list", "List all reaction role messages in this server"),

                        new SubcommandData("delete", "Delete a reaction role message and all its entries")
                                .addOption(OptionType.STRING, "message-id", "ID of the reaction role message to delete",
                                        true));
    }

    @Override
    public String getName() {
        return "reactionrole";
    }

    @Override
    public String getDescription() {
        return "Manage reaction role systems";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.REACTION_ROLES;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
