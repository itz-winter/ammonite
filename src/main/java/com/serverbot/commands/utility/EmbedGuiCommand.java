package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedGuiSession;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;

/**
 * /embedgui — interactive embed builder.
 *
 * Opens an ephemeral GUI panel with a live preview embed and control buttons.
 * The user clicks buttons to open modals for each embed field, then clicks
 * "Send" to post the final embed to the target channel.
 * "Export JSON" exports the current state as JSON usable with /embed.
 */
public class EmbedGuiCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only","This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }
        Member member = event.getMember();
        if (!PermissionUtils.hasModeratorPermissions(member)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Insufficient Permissions","You need moderation permissions to use the embed builder.")).setEphemeral(true).queue();
            return;
        }

        // Resolve target channel
        GuildMessageChannel targetTmp = event.getChannel().asGuildMessageChannel();
        OptionMapping chOpt = event.getOption("channel");
        if (chOpt != null) {
            try { targetTmp = chOpt.getAsChannel().asGuildMessageChannel(); }
            catch (Exception e) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Channel","That channel cannot receive messages.")).setEphemeral(true).queue();
                return;
            }
        }
        final GuildMessageChannel target = targetTmp;

        String userId = event.getUser().getId();
        EmbedGuiSession session = EmbedGuiSession.getOrCreate(userId, target.getId());

        // Defer the reply — we store the hook in the session so that modal submissions
        // can edit this original ephemeral message.
        event.deferReply(true).queue(hook -> {
            session.hook = hook;
            hook.editOriginal(buildGuiEdit(session, target, userId)).queue();
        });
    }

    // ── Static helpers used by EmbedGuiListener too ───────────────────────────

    /** Build the single GUI message: preview embed + status embed + control buttons. */
    public static MessageEditData buildGuiEdit(EmbedGuiSession s, GuildMessageChannel target, String userId) {
        return new MessageEditBuilder()
            .setEmbeds(buildPreview(s).build(), buildStatus(s, target).build())
            .setComponents(buildRows(userId))
            .build();
    }

    /** The preview embed — exactly what will be sent (or a placeholder if empty). */
    public static EmbedBuilder buildPreview(EmbedGuiSession s) {
        EmbedBuilder eb = new EmbedBuilder();
        boolean hasContent = false;

        if (s.title != null)       { eb.setTitle(s.title, s.titleUrl); hasContent = true; }
        if (s.description != null) { eb.setDescription(s.description.replace("\\n","\n")); hasContent = true; }
        if (s.colorHex != null) {
            try { eb.setColor(Color.decode(s.colorHex.startsWith("#") ? s.colorHex : "#"+s.colorHex)); }
            catch (Exception ignored) {}
        } else {
            eb.setColor(new Color(0x2B2D31)); // near-invisible dark default
        }
        if (s.timestamp)           { eb.setTimestamp(java.time.Instant.now()); hasContent = true; }
        if (s.authorName != null)  { eb.setAuthor(s.authorName, s.authorUrl, s.authorIconUrl); hasContent = true; }
        if (s.footerText != null)  { eb.setFooter(s.footerText, s.footerIconUrl); hasContent = true; }
        if (s.imageUrl != null)    { eb.setImage(s.imageUrl); hasContent = true; }
        if (s.thumbnailUrl != null){ eb.setThumbnail(s.thumbnailUrl); hasContent = true; }
        for (EmbedGuiSession.FieldEntry f : s.fields) { eb.addField(f.name, f.value, f.inline); hasContent = true; }

        if (!hasContent) {
            eb.setColor(new Color(0x5865F2));
            eb.setDescription("*Your embed will appear here as you build it.\nUse the buttons below to add content.*");
        }
        return eb;
    }

    /** A small status embed shown below the preview. */
    public static EmbedBuilder buildStatus(EmbedGuiSession s, GuildMessageChannel target) {
        String targetMention = target != null ? "<#"+target.getId()+">" : "<#"+s.targetChannelId+">";
        return new EmbedBuilder()
            .setColor(new Color(0x36393F))
            .addField("📊 Builder Status",
                "**Fields:** " + s.fields.size() + "/25  •  " +
                "**Buttons:** " + s.buttons.size() + "/25  •  " +
                "**Timestamp:** " + (s.timestamp ? "✅ On" : "❌ Off") + "\n" +
                "**Target:** " + targetMention, false);
    }

    /** The 3 rows of control buttons. */
    public static List<ActionRow> buildRows(String userId) {
        String u = userId;
        Emoji NOTE    = Emoji.fromFormatted("<:NOTE:1436161206233858070>");
        Emoji TRASH   = Emoji.fromFormatted("<:TRASH:1436161220007825458>");
        Emoji SAVE    = Emoji.fromFormatted("<:SAVE:1436161201880170526>");
        Emoji SUCCESS = Emoji.fromFormatted("<:SUCCESS:1436158779996504066>");
        return Arrays.asList(
            ActionRow.of(
                Button.secondary("egui:title:"+u,  "📝 Title"),
                Button.secondary("egui:desc:"+u,   "Description").withEmoji(NOTE),
                Button.secondary("egui:color:"+u,  "🎨 Color"),
                Button.secondary("egui:author:"+u, "👤 Author"),
                Button.secondary("egui:footer:"+u, "📋 Footer")
            ),
            ActionRow.of(
                Button.secondary("egui:image:"+u,  "🖼 Image"),
                Button.secondary("egui:thumb:"+u,  "🔲 Thumbnail"),
                Button.success("egui:field:"+u,    "➕ Add Field"),
                Button.danger("egui:rmfield:"+u,   "Remove Field").withEmoji(TRASH),
                Button.secondary("egui:ts:"+u,     "🕐 Timestamp")
            ),
            ActionRow.of(
                Button.success("egui:addbtn:"+u,   "➕ Add Button"),
                Button.danger("egui:rmbtn:"+u,     "Remove Button").withEmoji(TRASH),
                Button.danger("egui:clear:"+u,     "Clear All").withEmoji(TRASH),
                Button.secondary("egui:export:"+u, "Export JSON").withEmoji(SAVE),
                Button.success("egui:send:"+u,     "Send").withEmoji(SUCCESS)
            )
        );
    }

    public static CommandData getCommandData() {
        return Commands.slash("embedgui","Interactive embed builder with live preview, full controls, and JSON export.")
            .addOptions(
                new OptionData(OptionType.CHANNEL,"channel","Channel to send the final embed to (default: current channel)",false)
            );
    }

    @Override public String getName()              { return "embedgui"; }
    @Override public String getDescription()       { return "Interactive embed builder with live preview."; }
    @Override public CommandCategory getCategory() { return CommandCategory.UTILITY; }
    @Override public boolean requiresPermissions() { return true; }
}