package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.services.TicketService;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Listener for ticket system events
 */
public class TicketListener extends ListenerAdapter {

    private final TicketService ticketService;

    public TicketListener() {
        this.ticketService = ServerBot.getTicketService();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (event.getGuild() == null)
            return;

        String buttonId = event.getComponentId();

        if (buttonId.equals("ticket_close")) {
            handleTicketCloseButton(event);
        } else if (buttonId.equals("ticket_kiosk_create")) {
            handleTicketKioskCreateButton(event);
        } else if (buttonId.equals("ticket_archive")) {
            handleTicketArchiveButton(event);
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        // Check if user has open ticket and close it if settings allow
        TicketService.TicketSettings settings = ticketService.getTicketSettings(event.getGuild().getId());

        if (settings.isCloseOnLeave()) {
            TicketService.TicketData ticket = ticketService.getUserOpenTicket(
                    event.getGuild().getId(),
                    event.getUser().getId());

            if (ticket != null) {
                // Close the ticket
                CompletableFuture<String> future = ticketService.closeTicket(
                        event.getGuild(),
                        ticket.getTicketId(),
                        event.getJDA().getSelfUser(),
                        "User left the server");

                future.thenAccept(result -> {
                    if (!result.equals("SUCCESS")) {
                        ServerBot.getLogger().warn("Failed to auto-close ticket {} for user {}: {}",
                                ticket.getTicketId(), event.getUser().getId(), result);
                    }
                });
            }
        }
    }

    private void handleTicketCloseButton(ButtonInteractionEvent event) {
        // Check if user has permission to close tickets
        if (!hasClosePermission(event.getMember())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T17",
                    "You don't have permission to close this ticket.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Get ticket data
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This is not a valid ticket channel.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        CompletableFuture<String> future = ticketService.closeTicket(
                event.getGuild(),
                ticket.getTicketId(),
                event.getUser(),
                "Closed via button");

        future.thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Ticket Closing",
                        "This ticket is being closed...")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        "Failed to close ticket. Use `/error category:8` for full 8XX-series documentation.")).queue();
            }
        });
    }

    private void handleTicketKioskCreateButton(ButtonInteractionEvent event) {
        // Check if user already has an open ticket
        if (ticketService.hasOpenTicket(event.getGuild().getId(), event.getUser().getId())) {
            TicketService.TicketData existingTicket = ticketService.getUserOpenTicket(
                    event.getGuild().getId(),
                    event.getUser().getId());

            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Ticket Exists",
                    "You already have an open ticket: <#" + existingTicket.getChannelId() + ">")).setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue();

        CompletableFuture<String> future = ticketService.createTicket(
                event.getGuild(),
                event.getUser(),
                "Created from kiosk");

        future.thenAccept(result -> {
            if (result.startsWith("T") || result.startsWith("8")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                        "Error " + result,
                        "Failed to create ticket. Use `/error category:8` for full 8XX-series documentation.")).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                        "Ticket Created",
                        "Your ticket #" + result + " has been created! Check your channels list.")).queue();
            }
        });
    }

    private void handleTicketArchiveButton(ButtonInteractionEvent event) {
        // Check if user has permission
        if (!hasClosePermission(event.getMember())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T17",
                    "You don't have permission to archive this ticket.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Get ticket data
        TicketService.TicketData ticket = ticketService.getTicketByChannel(event.getChannel().getId());
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error T18",
                    "This is not a valid ticket channel.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.deferReply().queue();

        CompletableFuture<String> future = ticketService.archiveTicket(
                event.getGuild(),
                ticket);

        future.thenAccept(result -> {
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

    private boolean hasClosePermission(net.dv8tion.jda.api.entities.Member member) {
        if (member.hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            return true;
        }

        // Check if user has support role
        TicketService.TicketSettings settings = ticketService.getTicketSettings(member.getGuild().getId());
        for (String roleId : settings.getSupportRoles()) {
            if (member.getRoles().stream().anyMatch(role -> role.getId().equals(roleId))) {
                return true;
            }
        }

        // Check if user is ticket creator
        TicketService.TicketData ticket = ticketService
                .getTicketByChannel(member.getGuild().getTextChannelById(member.getGuild().getId()).getId());
        if (ticket != null && ticket.getCreatorId().equals(member.getId())) {
            return true;
        }

        return false;
    }
}