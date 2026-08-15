package com.serverbot.listeners;

import com.serverbot.commands.utility.BugReportCommand;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Handles modal submissions for the /bugreport command.
 */
public class BugReportModalListener extends ListenerAdapter {

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (!event.getModalId().equals(BugReportCommand.MODAL_ID)) return;
        BugReportCommand.handleModal(event);
    }
}
