package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.commands.utility.EmbedGuiCommand;
import com.serverbot.utils.EmbedGuiSession;
import com.serverbot.utils.EmbedJsonUtils;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.modals.Modal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles all button and modal interactions for the /embedgui builder.
 *
 * Button IDs:  egui:<action>:<userId>
 * Modal IDs:   egm:<action>:<userId>
 */
public class EmbedGuiListener extends ListenerAdapter {

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("egui:")) return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3) return;
        String action = parts[1];
        String userId  = parts[2];

        if (!event.getUser().getId().equals(userId)) {
            event.reply("This embed builder belongs to someone else.").setEphemeral(true).queue();
            return;
        }

        EmbedGuiSession session = EmbedGuiSession.get(userId);
        if (session == null) {
            event.reply("Your embed builder session has expired. Run `/embedgui` again.").setEphemeral(true).queue();
            return;
        }
        session.touch();

        switch (action) {
            case "title"   -> event.replyModal(titleModal(userId, session)).queue();
            case "desc"    -> event.replyModal(descModal(userId, session)).queue();
            case "color"   -> event.replyModal(colorModal(userId, session)).queue();
            case "author"  -> event.replyModal(authorModal(userId, session)).queue();
            case "footer"  -> event.replyModal(footerModal(userId, session)).queue();
            case "image"   -> event.replyModal(imageModal(userId, session)).queue();
            case "thumb"   -> event.replyModal(thumbModal(userId, session)).queue();
            case "field"   -> event.replyModal(fieldModal(userId)).queue();
            case "addbtn"  -> event.replyModal(btnModal(userId)).queue();
            case "rmfield" -> {
                if (!session.fields.isEmpty())
                    session.fields.remove(session.fields.size() - 1);
                refreshGui(event, session, userId);
            }
            case "rmbtn"   -> {
                if (!session.buttons.isEmpty())
                    session.buttons.remove(session.buttons.size() - 1);
                refreshGui(event, session, userId);
            }
            case "ts"      -> {
                session.timestamp = !session.timestamp;
                refreshGui(event, session, userId);
            }
            case "clear"   -> {
                session.clear();
                refreshGui(event, session, userId);
            }
            case "export"  -> {
                String exportMsg = EmbedJsonUtils.buildExportMessage(session);
                event.reply(exportMsg).setEphemeral(true).queue();
            }
            case "send"    -> handleSend(event, session, userId);
            default        -> event.reply("Unknown action.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith("egm:")) return;

        String[] parts = id.split(":", 3);
        if (parts.length < 3) return;
        String action = parts[1];
        String userId  = parts[2];

        if (!event.getUser().getId().equals(userId)) return;

        EmbedGuiSession session = EmbedGuiSession.get(userId);
        if (session == null) {
            event.reply("Your embed builder session has expired. Run `/embedgui` again.").setEphemeral(true).queue();
            return;
        }
        session.touch();

        switch (action) {
            case "title" -> {
                session.title    = nb(event.getValue("val").getAsString());
                session.titleUrl = nb(event.getValue("url").getAsString());
            }
            case "desc"   -> session.description = nb(event.getValue("val").getAsString());
            case "color"  -> session.colorHex    = nb(event.getValue("val").getAsString());
            case "author" -> {
                session.authorName    = nb(event.getValue("name").getAsString());
                session.authorIconUrl = nb(event.getValue("icon").getAsString());
                session.authorUrl     = nb(event.getValue("url").getAsString());
            }
            case "footer" -> {
                session.footerText    = nb(event.getValue("text").getAsString());
                session.footerIconUrl = nb(event.getValue("icon").getAsString());
            }
            case "image" -> session.imageUrl     = nb(event.getValue("val").getAsString());
            case "thumb" -> session.thumbnailUrl = nb(event.getValue("val").getAsString());
            case "field" -> {
                String name   = event.getValue("name").getAsString().trim();
                String value  = event.getValue("value").getAsString().trim();
                String inlineS = event.getValue("inline").getAsString().trim();
                boolean inline = "yes".equalsIgnoreCase(inlineS) || "true".equalsIgnoreCase(inlineS) || "y".equalsIgnoreCase(inlineS);
                if (!name.isEmpty() && !value.isEmpty() && session.fields.size() < 25)
                    session.fields.add(new EmbedGuiSession.FieldEntry(name, value, inline));
            }
            case "btn" -> {
                String label    = event.getValue("label").getAsString().trim();
                String style    = event.getValue("style").getAsString().trim().toLowerCase();
                String idOrUrl  = event.getValue("id_url").getAsString().trim();
                if (!label.isEmpty() && !style.isEmpty() && !idOrUrl.isEmpty() && session.buttons.size() < 25)
                    session.buttons.add(new EmbedGuiSession.ButtonEntry(label, style, idOrUrl));
            }
        }

        // Acknowledge the modal (ephemeral, instantly deleted) then update the GUI via the stored hook.
        event.deferReply(true).queue(hook -> hook.deleteOriginal().queue());

        if (session.hook != null) {
            TextChannel targetCh = event.isFromGuild()
                ? event.getGuild().getTextChannelById(session.targetChannelId) : null;
            // Update preview (original reply)
            session.hook.editOriginalEmbeds(EmbedGuiCommand.buildPreview(session).build()).queue();
            // Update controls (follow-up by stored ID)
            if (session.controlsMessageId != null) {
                session.hook.editMessageEmbedsById(session.controlsMessageId,
                    EmbedGuiCommand.buildStatus(session, targetCh).build())
                    .setComponents(EmbedGuiCommand.buildRows(userId)).queue();
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Edit the existing GUI messages after a button (non-modal) action. */
    private void refreshGui(ButtonInteractionEvent event, EmbedGuiSession session, String userId) {
        TextChannel targetCh = event.isFromGuild()
            ? event.getGuild().getTextChannelById(session.targetChannelId) : null;
        // The button is ON the controls message — edit it in-place
        event.editMessageEmbeds(
            EmbedGuiCommand.buildStatus(session, targetCh).build()
        ).setComponents(EmbedGuiCommand.buildRows(userId)).queue();
        // Update the preview (original reply)
        session.hook.editOriginalEmbeds(EmbedGuiCommand.buildPreview(session).build()).queue();
    }

    /** Send the built embed to the target channel and close the GUI. */
    private void handleSend(ButtonInteractionEvent event, EmbedGuiSession session, String userId) {
        EmbedBuilder embed = EmbedJsonUtils.buildEmbed(session);
        var built = embed.build();
        if ((built.getTitle() == null || built.getTitle().isBlank())
                && (built.getDescription() == null || built.getDescription().isBlank())
                && built.getFields().isEmpty()) {
            event.reply("❌ Your embed is empty — add at least a title, description, or field before sending.")
                 .setEphemeral(true).queue();
            return;
        }

        if (!event.isFromGuild()) {
            event.reply("Can only send to guild channels.").setEphemeral(true).queue();
            return;
        }

        TextChannel dest = event.getGuild().getTextChannelById(session.targetChannelId);
        if (dest == null) {
            event.reply("❌ Target channel not found. It may have been deleted.").setEphemeral(true).queue();
            return;
        }

        List<Button> userButtons = EmbedJsonUtils.buildUserButtons(session);
        var sendAction = dest.sendMessageEmbeds(built);
        if (!userButtons.isEmpty()) {
            List<ActionRow> rows = new ArrayList<>();
            for (int i = 0; i < userButtons.size(); i += 5)
                rows.add(ActionRow.of(userButtons.subList(i, Math.min(i + 5, userButtons.size()))));
            sendAction = sendAction.setComponents(rows);
        }

        sendAction.queue(
            msg -> {
                InteractionHook savedHook = session.hook;
                EmbedGuiSession.remove(userId);
                // The button was on the controls message — replace it with success embed
                event.editMessageEmbeds(
                    EmbedUtils.createSuccessEmbed("Embed Sent",
                        "✅ Your embed was sent to " + dest.getAsMention() + ".\n" +
                        "[Jump to message](" + msg.getJumpUrl() + ")")
                ).setComponents(Collections.emptyList()).queue();
                // Delete the original reply (preview message)
                if (savedHook != null) {
                    savedHook.deleteOriginal().queue(null, ignored -> {});
                }
            },
            err -> event.reply("❌ Failed to send: " + err.getMessage()).setEphemeral(true).queue()
        );
    }

    /** Convert blank strings to null (so unset fields stay blank in the embed). */
    private static String nb(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ── Modal builders ────────────────────────────────────────────────────────

    private Modal titleModal(String uid, EmbedGuiSession s) {
        TextInput.Builder val = TextInput.create("val", TextInputStyle.SHORT)
            .setPlaceholder("Embed title (leave blank to clear)")
            .setMaxLength(256).setRequired(false);
        if (s.title != null) val.setValue(s.title);
        TextInput.Builder url = TextInput.create("url", TextInputStyle.SHORT)
            .setPlaceholder("Title URL (leave blank for none)")
            .setMaxLength(2048).setRequired(false);
        if (s.titleUrl != null) url.setValue(s.titleUrl);
        return Modal.create("egm:title:"+uid, "Set Embed Title")
            .addComponents(Label.of("Title", val.build()), Label.of("Title URL (optional)", url.build()))
            .build();
    }

    private Modal descModal(String uid, EmbedGuiSession s) {
        TextInput.Builder val = TextInput.create("val", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Embed description (leave blank to clear). Use \\n for newlines.")
            .setMaxLength(4000).setRequired(false);
        if (s.description != null) val.setValue(s.description);
        return Modal.create("egm:desc:"+uid, "Set Description")
            .addComponents(Label.of("Description", val.build()))
            .build();
    }

    private Modal colorModal(String uid, EmbedGuiSession s) {
        TextInput.Builder val = TextInput.create("val", TextInputStyle.SHORT)
            .setPlaceholder("#5865F2  (hex, leave blank for default)")
            .setMaxLength(7).setRequired(false);
        if (s.colorHex != null) val.setValue(s.colorHex);
        return Modal.create("egm:color:"+uid, "Set Color")
            .addComponents(Label.of("Hex Color (#RRGGBB)", val.build()))
            .build();
    }

    private Modal authorModal(String uid, EmbedGuiSession s) {
        TextInput.Builder name = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder("Author name (leave blank to clear)")
            .setMaxLength(256).setRequired(false);
        if (s.authorName != null) name.setValue(s.authorName);
        TextInput.Builder icon = TextInput.create("icon", TextInputStyle.SHORT)
            .setPlaceholder("Author icon URL (leave blank for none)")
            .setMaxLength(2048).setRequired(false);
        if (s.authorIconUrl != null) icon.setValue(s.authorIconUrl);
        TextInput.Builder url = TextInput.create("url", TextInputStyle.SHORT)
            .setPlaceholder("Author link URL (leave blank for none)")
            .setMaxLength(2048).setRequired(false);
        if (s.authorUrl != null) url.setValue(s.authorUrl);
        return Modal.create("egm:author:"+uid, "Set Author")
            .addComponents(Label.of("Author Name", name.build()), Label.of("Icon URL (optional)", icon.build()), Label.of("Link URL (optional)", url.build()))
            .build();
    }

    private Modal footerModal(String uid, EmbedGuiSession s) {
        TextInput.Builder text = TextInput.create("text", TextInputStyle.SHORT)
            .setPlaceholder("Footer text (leave blank to clear)")
            .setMaxLength(2048).setRequired(false);
        if (s.footerText != null) text.setValue(s.footerText);
        TextInput.Builder icon = TextInput.create("icon", TextInputStyle.SHORT)
            .setPlaceholder("Footer icon URL (leave blank for none)")
            .setMaxLength(2048).setRequired(false);
        if (s.footerIconUrl != null) icon.setValue(s.footerIconUrl);
        return Modal.create("egm:footer:"+uid, "Set Footer")
            .addComponents(Label.of("Footer Text", text.build()), Label.of("Icon URL (optional)", icon.build()))
            .build();
    }

    private Modal imageModal(String uid, EmbedGuiSession s) {
        TextInput.Builder val = TextInput.create("val", TextInputStyle.SHORT)
            .setPlaceholder("Image URL (leave blank to clear)")
            .setMaxLength(2048).setRequired(false);
        if (s.imageUrl != null) val.setValue(s.imageUrl);
        return Modal.create("egm:image:"+uid, "Set Image")
            .addComponents(Label.of("Image URL", val.build()))
            .build();
    }

    private Modal thumbModal(String uid, EmbedGuiSession s) {
        TextInput.Builder val = TextInput.create("val", TextInputStyle.SHORT)
            .setPlaceholder("Thumbnail URL (leave blank to clear)")
            .setMaxLength(2048).setRequired(false);
        if (s.thumbnailUrl != null) val.setValue(s.thumbnailUrl);
        return Modal.create("egm:thumb:"+uid, "Set Thumbnail")
            .addComponents(Label.of("Thumbnail URL", val.build()))
            .build();
    }

    private Modal fieldModal(String uid) {
        TextInput name = TextInput.create("name", TextInputStyle.SHORT)
            .setPlaceholder("Field name").setMaxLength(256).setRequired(true).build();
        TextInput value = TextInput.create("value", TextInputStyle.PARAGRAPH)
            .setPlaceholder("Field value").setMaxLength(1024).setRequired(true).build();
        TextInput inline = TextInput.create("inline", TextInputStyle.SHORT)
            .setPlaceholder("Inline? yes / no  (default: no)")
            .setValue("no").setMaxLength(5).setRequired(false).build();
        return Modal.create("egm:field:"+uid, "Add Field")
            .addComponents(Label.of("Field Name", name), Label.of("Field Value", value), Label.of("Inline? (yes/no)", inline))
            .build();
    }

    private Modal btnModal(String uid) {
        TextInput label = TextInput.create("label", TextInputStyle.SHORT)
            .setPlaceholder("Button label text").setMaxLength(80).setRequired(true).build();
        TextInput style = TextInput.create("style", TextInputStyle.SHORT)
            .setPlaceholder("primary | secondary | success | danger | link")
            .setMaxLength(10).setRequired(true).build();
        TextInput idUrl = TextInput.create("id_url", TextInputStyle.SHORT)
            .setPlaceholder("Custom ID (or URL if style is link)").setMaxLength(100).setRequired(true).build();
        return Modal.create("egm:btn:"+uid, "Add Button")
            .addComponents(Label.of("Label", label), Label.of("Style", style), Label.of("Custom ID or URL", idUrl))
            .build();
    }
}