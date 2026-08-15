package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets any user submit a bug report directly via a modal form.
 * Reports are stored via {@link ServerBot#getStorageManager()} using the same
 * {@code saveErrorReport} path that the automatic error-button flow uses, so
 * they all appear in {@code /reports error}.
 */
public class BugReportCommand implements SlashCommand {

    public static final String MODAL_ID = "bugreport_modal";

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        TextInput title = TextInput.create("title", TextInputStyle.SHORT)
                .setPlaceholder("e.g. /rank crashes when user has no XP")
                .setMinLength(5)
                .setMaxLength(100)
                .setRequired(true)
                .build();

        TextInput description = TextInput.create("description", TextInputStyle.PARAGRAPH)
                .setPlaceholder("Describe the bug in as much detail as possible.\n"
                        + "Include the command you ran, what you expected, and what actually happened.")
                .setMinLength(20)
                .setMaxLength(1000)
                .setRequired(true)
                .build();

        TextInput steps = TextInput.create("steps", TextInputStyle.PARAGRAPH)
                .setPlaceholder("1. Run /rank\n2. See error")
                .setMaxLength(500)
                .setRequired(false)
                .build();

        Modal modal = Modal.create(MODAL_ID, "Submit a Bug Report")
                .addComponents(Label.of("Short title", title))
                .addComponents(Label.of("What happened?", description))
                .addComponents(Label.of("Steps to reproduce (optional)", steps))
                .build();

        event.replyModal(modal).queue();
    }

    /**
     * Called by {@link com.serverbot.listeners.EventListener} when the modal is submitted.
     */
    public static void handleModal(ModalInteractionEvent event) {
        String titleVal = event.getValue("title") != null
                ? event.getValue("title").getAsString() : "(no title)";
        String descVal = event.getValue("description") != null
                ? event.getValue("description").getAsString() : "(no description)";
        String stepsVal = event.getValue("steps") != null
                ? event.getValue("steps").getAsString() : null;

        User user = event.getUser();
        String guildName = event.isFromGuild() ? event.getGuild().getName() : "DM";
        String guildId   = event.isFromGuild() ? event.getGuild().getId()   : "DM";

        // Build a dedupKey from the title so identical reports are collapsed
        String dedupKey = "bugreport:" + titleVal.toLowerCase()
                .replaceAll("[^a-z0-9 ]", "")
                .trim()
                .replaceAll("\\s+", "_");
        if (dedupKey.length() > 80) dedupKey = dedupKey.substring(0, 80);

        Map<String, Object> reportData = new HashMap<>();
        reportData.put("dedupKey",        dedupKey);
        reportData.put("commandName",     "bugreport");
        reportData.put("exceptionType",   "UserReport");
        reportData.put("title",           titleVal);
        reportData.put("description",     descVal);
        if (stepsVal != null && !stepsVal.isBlank()) {
            reportData.put("steps", stepsVal);
        }
        reportData.put("reportedBy",      user.getId());
        reportData.put("reportedByName",  user.getName());
        reportData.put("guildId",         guildId);
        reportData.put("guildName",       guildName);
        reportData.put("timestamp",       Instant.now().toString());
        reportData.put("count",           1);

        try {
            ServerBot.getStorageManager().saveErrorReport(reportData);
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Submission Failed",
                    "Could not save your report: " + e.getMessage()
            )).setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(new Color(0x57F287))
                .setTitle("✅ Bug Report Submitted")
                .setDescription("Thank you for taking the time to report this!\n"
                        + "The bot owner will review your report shortly.")
                .addField("Title", titleVal, false)
                .addField("Summary", descVal.length() > 200
                        ? descVal.substring(0, 200) + "…" : descVal, false)
                .setFooter("Submitted by " + user.getName())
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    @Override
    public String getName()        { return "bugreport"; }

    @Override
    public String getDescription() { return "Submit a bug report to the bot owner"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.UTILITY; }

    @Override
    public boolean isGuildOnly()   { return false; }

    @Override
    public boolean requiresPermissions() { return false; }

    public static CommandData getCommandData() {
        return Commands.slash("bugreport", "Submit a bug report to the bot owner");
    }
}
