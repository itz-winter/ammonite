package com.serverbot.commands.support;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.TicketService;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Comprehensive ticket system command
 * Based on discord-tickets functionality
 */
public class TicketCommand implements SlashCommand {

    private final TicketService ticketService;

    public TicketCommand() {
        this.ticketService = ServerBot.getTicketService();
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T00",
                    "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T15",
                    "Invalid subcommand. Use `/error category:8` for ticket error details.")).setEphemeral(true)
                    .queue();
            return;
        }

        switch (subcommand) {
            case "create" -> handleCreate(event);
            case "close" -> handleClose(event);
            case "add" -> handleAdd(event);
            case "remove" -> handleRemove(event);
            case "category-create" -> handleCategoryCreate(event);
            case "category-delete" -> handleCategoryDelete(event);
            case "category-list" -> handleCategoryList(event);
            case "settings" -> handleSettings(event);
            case "info" -> handleInfo(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T15",
                    "Unknown subcommand. Use `/error category:8` for ticket error details.")).setEphemeral(true)
                    .queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        String reason = event.getOption("reason", OptionMapping::getAsString);

        if (reason == null) {
            reason = "Ticket created via command";
        }

        event.deferReply(true).queue();

        CompletableFuture<String> future = ticketService.createTicket(
                event.getGuild(),
                event.getUser(),
                reason);

        future.thenAccept(result -> {
            if (result.startsWith("T")) {
                // Error code
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        getErrorMessage(result) + "\nUse `/error category:8` for full 8XX-series documentation."))
                        .queue();
            } else {
                // Success - ticket ID returned
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Ticket Created",
                        "Your ticket #" + result + " has been created successfully!")).queue();
            }
        });
    }

    private void handleClose(SlashCommandInteractionEvent event) {
        // Check if user has permission to close tickets
        if (!hasTicketPermission(event.getMember(), "close")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T17",
                    "You don't have permission to close tickets.")).setEphemeral(true).queue();
            return;
        }

        String reason = event.getOption("reason", OptionMapping::getAsString);

        // Check if this is a ticket channel
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This command can only be used in ticket channels.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        CompletableFuture<String> future = ticketService.closeTicket(
                event.getGuild(),
                ticket.getTicketId(),
                event.getUser(),
                reason);

        future.thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Ticket Closing",
                        "This ticket is being closed...")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        getErrorMessage(result) + "\nUse `/error category:8` for full 8XX-series documentation."))
                        .queue();
            }
        });
    }

    private void handleAdd(SlashCommandInteractionEvent event) {
        if (!hasTicketPermission(event.getMember(), "manage")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T17",
                    "You don't have permission to manage tickets.")).setEphemeral(true).queue();
            return;
        }

        User userToAdd = event.getOption("user", OptionMapping::getAsUser);
        if (userToAdd == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T19",
                    "Please specify a user to add.")).setEphemeral(true).queue();
            return;
        }

        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This command can only be used in ticket channels.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        CompletableFuture<String> future = ticketService.addUserToTicket(
                event.getGuild(),
                ticket.getTicketId(),
                userToAdd);

        future.thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "User Added",
                        userToAdd.getAsMention() + " has been added to this ticket.")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        getErrorMessage(result) + "\nUse `/error category:8` for full 8XX-series documentation."))
                        .queue();
            }
        });
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        if (!hasTicketPermission(event.getMember(), "manage")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T17",
                    "You don't have permission to manage tickets.")).setEphemeral(true).queue();
            return;
        }

        User userToRemove = event.getOption("user", OptionMapping::getAsUser);
        if (userToRemove == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T20",
                    "Please specify a user to remove.")).setEphemeral(true).queue();
            return;
        }

        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This command can only be used in ticket channels.")).setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        CompletableFuture<String> future = ticketService.removeUserFromTicket(
                event.getGuild(),
                ticket.getTicketId(),
                userToRemove);

        future.thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "User Removed",
                        userToRemove.getAsMention() + " has been removed from this ticket.")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        getErrorMessage(result) + "\nUse `/error category:8` for full 8XX-series documentation."))
                        .queue();
            }
        });
    }

    private void handleCategoryCreate(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T21",
                    "You need Administrator permission to manage ticket categories.")).setEphemeral(true).queue();
            return;
        }

        String categoryId = event.getOption("id", OptionMapping::getAsString);
        String name = event.getOption("name", OptionMapping::getAsString);
        String description = event.getOption("description", OptionMapping::getAsString);
        String emoji = event.getOption("emoji", "🎫", OptionMapping::getAsString);
        Role staffRole = event.getOption("staff-role", OptionMapping::getAsRole);

        if (categoryId == null || name == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T22",
                    "Category ID and name are required.")).setEphemeral(true).queue();
            return;
        }

        List<String> staffRoles = new ArrayList<>();
        if (staffRole != null) {
            staffRoles.add(staffRole.getId());
        }

        String result = ticketService.createTicketCategory(
                event.getGuild().getId(),
                categoryId,
                name,
                description != null ? description : "No description provided",
                emoji,
                staffRoles);

        if (result.equals("SUCCESS")) {
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Category Created",
                    "Ticket category `" + categoryId + "` has been created successfully!")).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    getErrorMessage(result) + "\nUse `/error category:8` for full 8XX-series documentation."))
                    .setEphemeral(true).queue();
        }
    }

    private void handleCategoryDelete(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T21",
                    "You need Administrator permission to manage ticket categories.")).setEphemeral(true).queue();
            return;
        }

        String categoryId = event.getOption("id", OptionMapping::getAsString);
        if (categoryId == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T23",
                    "Category ID is required.")).setEphemeral(true).queue();
            return;
        }

        String result = ticketService.deleteTicketCategory(event.getGuild().getId(), categoryId);

        if (result.equals("SUCCESS")) {
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Category Deleted",
                    "Ticket category `" + categoryId + "` has been deleted successfully!")).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    getErrorMessage(result) + "\nUse `/error category:8` for full 8XX-series documentation."))
                    .setEphemeral(true).queue();
        }
    }

    private void handleCategoryList(SlashCommandInteractionEvent event) {
        List<TicketService.TicketCategory> categories = ticketService.getTicketCategories(event.getGuild().getId());

        if (categories.isEmpty()) {
            event.replyEmbeds(EmbedUtils.createInfoEmbed(
                    "No Categories",
                    "No ticket categories have been configured for this server.\n" +
                            "Use `/ticket category-create` to create one."))
                    .setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Ticket Categories")
                .setDescription("Available ticket categories for this server:")
                .setColor(Color.BLUE);

        for (TicketService.TicketCategory category : categories) {
            embed.addField(
                    category.getEmoji() + " " + category.getName(),
                    "**ID:** `" + category.getCategoryId() + "`\n" +
                            "**Description:** " + category.getDescription() + "\n" +
                            "**Staff Roles:** "
                            + (category.getStaffRoles().isEmpty() ? "None" : category.getStaffRoles().size()),
                    false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleSettings(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T21",
                    "You need Administrator permission to manage ticket settings.")).setEphemeral(true).queue();
            return;
        }

        TicketService.TicketSettings settings = ticketService.getTicketSettings(event.getGuild().getId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Ticket Settings")
                .setDescription("Current ticket system settings:")
                .addField("Tickets Category",
                        settings.getCategoryId() != null ? "<#" + settings.getCategoryId() + ">" : "Auto-create", true)
                .addField("DM Transcripts", settings.isDmTranscripts() ? "Enabled" : "Disabled", true)
                .addField("Close on Leave", settings.isCloseOnLeave() ? "Enabled" : "Disabled", true)
                .addField("Log Channel",
                        settings.getLogChannelId() != null ? "<#" + settings.getLogChannelId() + ">" : "Not set", true)
                .addField("Support Roles",
                        settings.getSupportRoles().isEmpty() ? "None"
                                : String.valueOf(settings.getSupportRoles().size()),
                        true)
                .setColor(Color.BLUE);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleInfo(SlashCommandInteractionEvent event) {
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This command can only be used in ticket channels.")).setEphemeral(true).queue();
            return;
        }

        User creator = event.getJDA().getUserById(ticket.getCreatorId());
        TicketService.TicketCategory category = ticketService.getTicketCategory(ticket.getGuildId(),
                ticket.getCategoryId());

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 Ticket Information")
                .addField("Ticket ID", "#" + ticket.getTicketId(), true)
                .addField("Status", ticket.getStatus().toString(), true)
                .addField("Category", category != null ? category.getEmoji() + " " + category.getName() : "Unknown",
                        true)
                .addField("Created by", creator != null ? creator.getAsMention() : "Unknown User", true)
                .addField("Created", "<t:" + ticket.getCreatedAt().getEpochSecond() + ":F>", true)
                .addField("Reason", ticket.getReason() != null ? ticket.getReason() : "No reason provided", false)
                .setColor(Color.BLUE);

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private boolean hasTicketPermission(net.dv8tion.jda.api.entities.Member member, String action) {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }

        // Check if user has support role
        TicketService.TicketSettings settings = ticketService.getTicketSettings(member.getGuild().getId());
        for (String roleId : settings.getSupportRoles()) {
            if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }

        // Check if user is ticket creator (for close action)
        if (action.equals("close")) {
            TicketService.TicketData ticket = ticketService
                    .getTicketByChannel(member.getGuild().getTextChannelById(member.getGuild().getId()).getId());
            if (ticket != null && ticket.getCreatorId().equals(member.getId())) {
                return true;
            }
        }

        return false;
    }

    private String getErrorMessage(String errorCode) {
        return switch (errorCode) {
            case "811" -> "You already have an open ticket in this server.";
            case "821" -> "Invalid ticket category. Use `/ticket category-list` to see available categories.";
            case "822" -> "Failed to create tickets category. Please check bot permissions.";
            case "810" -> "Ticket not found or already closed.";
            case "820" -> "Ticket channel not found.";
            case "832" -> "Cannot remove the ticket creator from their own ticket.";
            case "831" -> "Failed to create ticket category.";
            case "830" -> "Ticket category not found.";
            case "800" -> "Operation failed due to an internal error.";
            case "100" -> "Missing required parameter.";
            case "200" -> "Insufficient permissions for this action.";
            default -> "Unknown error occurred.";
        };
    }

    public CommandData getCommandData() {
        return Commands.slash("ticket", "Manage support tickets")
                .addSubcommands(
                        new SubcommandData("create", "Create a new support ticket")
                                .addOption(OptionType.STRING, "category", "Ticket category", true)
                                .addOption(OptionType.STRING, "reason", "Reason for creating the ticket", false),

                        new SubcommandData("close", "Close the current ticket")
                                .addOption(OptionType.STRING, "reason", "Reason for closing", false),

                        new SubcommandData("add", "Add a user to the current ticket")
                                .addOption(OptionType.USER, "user", "User to add", true),

                        new SubcommandData("remove", "Remove a user from the current ticket")
                                .addOption(OptionType.USER, "user", "User to remove", true),

                        new SubcommandData("category-create", "Create a new ticket category")
                                .addOption(OptionType.STRING, "id", "Category ID", true)
                                .addOption(OptionType.STRING, "name", "Category name", true)
                                .addOption(OptionType.STRING, "description", "Category description", false)
                                .addOption(OptionType.STRING, "emoji", "Category emoji", false)
                                .addOption(OptionType.ROLE, "staff-role", "Staff role for this category", false),

                        new SubcommandData("category-delete", "Delete a ticket category")
                                .addOption(OptionType.STRING, "id", "Category ID", true),

                        new SubcommandData("category-list", "List all ticket categories"),

                        new SubcommandData("settings", "View ticket system settings"),

                        new SubcommandData("info", "View information about the current ticket"));
    }

    @Override
    public String getName() {
        return "ticket";
    }

    @Override
    public String getDescription() {
        return "Comprehensive ticket system for server support";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.SUPPORT;
    }
}