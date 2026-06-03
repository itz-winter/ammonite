package com.serverbot.commands.tickets;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.TicketService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Command for managing the ticket system
 */
public class TicketCommand implements SlashCommand {

    private final TicketService ticketService;

    public TicketCommand() {
        this.ticketService = ServerBot.getTicketService();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.use")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to use ticket commands.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error",
                    "Please specify a subcommand.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        switch (subcommand) {
            case "create":
                handleCreate(event);
                break;
            case "close":
                handleClose(event);
                break;
            case "add":
                handleAdd(event);
                break;
            case "remove":
                handleRemove(event);
                break;
            case "archive":
                handleArchive(event);
                break;
            case "kiosk":
                handleKiosk(event);
                break;
            case "settings":
                handleSettings(event);
                break;
            case "category":
                handleCategory(event);
                break;
            default:
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Error",
                        "Unknown subcommand.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.create")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to create tickets.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "Ticket created via command";

        event.deferReply(true).queue();

        ticketService.createTicket(
                event.getGuild(),
                event.getUser(),
                reason).thenAccept(result -> {
                    if (result.startsWith("T")) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to create ticket. Use `/error category:8` for full 8XX-series documentation."))
                                .queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Ticket Created",
                                "Your ticket #" + result + " has been created! Check your channels list.")).queue();
                    }
                });
    }

    private void handleClose(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.close")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to close tickets.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        // Get ticket from current channel
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This is not a valid ticket channel.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        ticketService.closeTicket(
                event.getGuild(),
                ticket.getTicketId(),
                event.getUser(),
                reason).thenAccept(result -> {
                    if (result.equals("SUCCESS")) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Ticket Closing",
                                "This ticket is being closed...")).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to close ticket. Use `/error category:8` for full 8XX-series documentation."))
                                .queue();
                    }
                });
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.manage")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to manage tickets.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User user = event.getOption("user").getAsUser();

        // Get ticket from current channel
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This is not a valid ticket channel.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        ticketService.addUserToTicket(
                event.getGuild(),
                ticket.getTicketId(),
                user).thenAccept(result -> {
                    if (result.equals("SUCCESS")) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "User Added",
                                user.getAsMention() + " has been added to this ticket.")).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to add user. Use `/error category:8` for full 8XX-series documentation."))
                                .queue();
                    }
                });
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.manage")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to manage tickets.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User user = event.getOption("user").getAsUser();

        // Get ticket from current channel
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This is not a valid ticket channel.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        ticketService.removeUserFromTicket(
                event.getGuild(),
                ticket.getTicketId(),
                user).thenAccept(result -> {
                    if (result.equals("SUCCESS")) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "User Removed",
                                user.getAsMention() + " has been removed from this ticket.")).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to remove user. Use `/error category:8` for full 8XX-series documentation."))
                                .queue();
                    }
                });
    }

    private void handleArchive(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.manage")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to manage tickets.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Get ticket from current channel
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This is not a valid ticket channel.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        ticketService.archiveTicket(event.getGuild(), ticket).thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Ticket Archived",
                        "This ticket has been archived and moved to the Archived Tickets category.")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        "Failed to archive ticket. Use `/error category:8` for full 8XX-series documentation."))
                        .queue();
            }
        });
    }

    private void handleKiosk(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.admin")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to create ticket kiosks.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : "Support Tickets";
        String description = event.getOption("description") != null ? event.getOption("description").getAsString()
                : "Click the button below to create a support ticket.";

        event.deferReply(true).queue();

        ticketService.createTicketKiosk(
                event.getGuild(),
                event.getChannel().asTextChannel(),
                title,
                description).thenAccept(result -> {
                    if (result.equals("SUCCESS")) {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                                "Kiosk Created",
                                "Ticket kiosk has been created in this channel!")).queue();
                    } else {
                        event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                                "Error " + result,
                                "Failed to create kiosk. Use `/error category:8` for full 8XX-series documentation."))
                                .queue();
                    }
                });
    }

    private void handleSettings(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.admin")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to manage ticket settings.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Get current settings
        TicketService.TicketSettings settings = ticketService.getTicketSettings(event.getGuild().getId());

        // Update settings based on options provided
        boolean updated = false;

        if (event.getOption("dm_transcripts") != null) {
            settings.setDmTranscripts(event.getOption("dm_transcripts").getAsBoolean());
            updated = true;
        }

        if (event.getOption("close_on_leave") != null) {
            settings.setCloseOnLeave(event.getOption("close_on_leave").getAsBoolean());
            updated = true;
        }

        if (event.getOption("log_channel") != null) {
            settings.setLogChannelId(event.getOption("log_channel").getAsChannel().getId());
            updated = true;
        }

        if (updated) {
            event.deferReply(true).queue();

            String result = ticketService.updateTicketSettings(event.getGuild().getId(), settings);

            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Settings Updated",
                        "Ticket settings have been updated successfully.")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        "Failed to update settings. Use `/error category:8` for full 8XX-series documentation."))
                        .queue();
            }
        } else {
            // Just display current settings
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎫 Ticket Settings")
                    .setColor(Color.BLUE)
                    .addField("DM Transcripts", settings.isDmTranscripts() ? "Enabled" : "Disabled", true)
                    .addField("Close on Leave", settings.isCloseOnLeave() ? "Enabled" : "Disabled", true);

            if (settings.getLogChannelId() != null) {
                embed.addField("Log Channel", "<#" + settings.getLogChannelId() + ">", true);
            }

            if (settings.getCategoryId() != null) {
                embed.addField("Ticket Category", "<#" + settings.getCategoryId() + ">", true);
            }

            event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleCategory(SlashCommandInteractionEvent event) {
        if (!PermissionManager.hasPermission(event.getMember(), "tickets.admin")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Denied",
                    "You don't have permission to manage ticket categories.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        String action = event.getOption("action").getAsString();

        switch (action.toLowerCase()) {
            case "create":
                handleCategoryCreate(event);
                break;
            case "delete":
                handleCategoryDelete(event);
                break;
            case "list":
                handleCategoryList(event);
                break;
            default:
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Error",
                        "Invalid action. Valid actions: create, delete, list")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleCategoryCreate(SlashCommandInteractionEvent event) {
        String categoryId = event.getOption("category_id").getAsString();
        String name = event.getOption("name").getAsString();
        String description = event.getOption("description") != null ? event.getOption("description").getAsString()
                : "No description";
        String emoji = event.getOption("emoji") != null ? event.getOption("emoji").getAsString() : "🎫";

        // Staff roles can be added via additional options in the future
        List<String> staffRoles = new ArrayList<>();

        event.deferReply(true).queue();

        String result = ticketService.createTicketCategory(
                event.getGuild().getId(),
                categoryId,
                name,
                description,
                emoji,
                staffRoles);

        if (result.equals("SUCCESS")) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Category Created",
                    "Ticket category **" + name + "** has been created with ID: `" + categoryId + "`")).queue();
        } else {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to create category. Use `/error category:8` for full 8XX-series documentation.")).queue();
        }
    }

    private void handleCategoryDelete(SlashCommandInteractionEvent event) {
        String categoryId = event.getOption("category_id").getAsString();

        event.deferReply(true).queue();

        String result = ticketService.deleteTicketCategory(event.getGuild().getId(), categoryId);

        if (result.equals("SUCCESS")) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Category Deleted",
                    "Ticket category has been deleted.")).queue();
        } else {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to delete category. Use `/error category:8` for full 8XX-series documentation.")).queue();
        }
    }

    private void handleCategoryList(SlashCommandInteractionEvent event) {
        List<TicketService.TicketCategory> categories = ticketService.getTicketCategories(event.getGuild().getId());

        if (categories.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "No Categories",
                    "No ticket categories have been created yet. Create one with `/ticket category create`"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Ticket Categories")
                .setColor(Color.BLUE)
                .setDescription("Total: " + categories.size());

        for (TicketService.TicketCategory category : categories) {
            String value = "**ID:** `" + category.getCategoryId() + "`\n" +
                    "**Description:** " + category.getDescription();
            embed.addField(category.getEmoji() + " " + category.getName(), value, false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("ticket", "Manage support tickets")
                .addSubcommands(
                        new SubcommandData("create", "Create a new support ticket")
                                .addOption(OptionType.STRING, "category", "Ticket category", true)
                                .addOption(OptionType.STRING, "reason", "Reason for opening the ticket", false),

                        new SubcommandData("close", "Close the current ticket")
                                .addOption(OptionType.STRING, "reason", "Reason for closing", false),

                        new SubcommandData("add", "Add a user to the current ticket")
                                .addOption(OptionType.USER, "user", "User to add", true),

                        new SubcommandData("remove", "Remove a user from the current ticket")
                                .addOption(OptionType.USER, "user", "User to remove", true),

                        new SubcommandData("archive", "Archive the current ticket"),

                        new SubcommandData("kiosk", "Create a ticket kiosk message")
                                .addOption(OptionType.STRING, "title", "Kiosk title", false)
                                .addOption(OptionType.STRING, "description", "Kiosk description", false),

                        new SubcommandData("settings", "View or update ticket settings")
                                .addOption(OptionType.BOOLEAN, "dm_transcripts", "Send transcripts via DM", false)
                                .addOption(OptionType.BOOLEAN, "close_on_leave", "Close ticket when user leaves", false)
                                .addOption(OptionType.CHANNEL, "log_channel", "Channel for ticket logs", false),

                        new SubcommandData("category", "Manage ticket categories")
                                .addOption(OptionType.STRING, "action", "Action to perform (create, delete, list)",
                                        true)
                                .addOption(OptionType.STRING, "category_id", "Category ID", false)
                                .addOption(OptionType.STRING, "name", "Category name", false)
                                .addOption(OptionType.STRING, "description", "Category description", false)
                                .addOption(OptionType.STRING, "emoji", "Category emoji", false));
    }

    @Override
    public String getName() {
        return "ticket";
    }

    @Override
    public String getDescription() {
        return "Manage support tickets";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.SUPPORT;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    @Override
    public boolean isGuildOnly() {
        return true;
    }
}
