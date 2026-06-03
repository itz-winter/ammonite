package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedJsonUtils;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;

import java.util.ArrayList;
import java.util.List;

public class EmbedCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        /*
         * if (!event.isFromGuild()) {
         * event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only"
         * ,"This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
         * return;
         * }
         */
        Member member = event.getMember();
        if (!PermissionUtils.hasModeratorPermissions(member)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Insufficient Permissions",
                    "You need moderation permissions to send embeds.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        String jsonRaw = event.getOption("json", OptionMapping::getAsString);
        if (jsonRaw == null || jsonRaw.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing JSON",
                    "Please provide embed JSON.\n\n💡 Use /embedgui for an interactive builder with live preview and JSON export."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        EmbedBuilder embed;
        try {
            embed = EmbedJsonUtils.parseEmbed(jsonRaw);
        } catch (IllegalArgumentException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Embed JSON",
                    e.getMessage() + "\n\n💡 Use /embedgui to build embeds visually and export JSON."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        if (!hasContent(embed)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Empty Embed",
                    "The embed must have at least a title, description, or one field.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        List<Button> buttons = new ArrayList<>();
        try {
            buttons = EmbedJsonUtils.parseButtonsFromJson(jsonRaw);
        } catch (IllegalArgumentException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Buttons JSON", e.getMessage())).setEphemeral(true)
                    .queue();
            return;
        }
        GuildMessageChannel target = event.getChannel().asGuildMessageChannel();
        OptionMapping channelOption = event.getOption("channel");
        if (channelOption != null) {
            try {
                target = channelOption.getAsChannel().asGuildMessageChannel();
            } catch (Exception e) {
                event.replyEmbeds(
                        EmbedUtils.createErrorEmbed("Invalid Channel", "That channel cannot receive messages."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }
        }
        MessageCreateBuilder msg = new MessageCreateBuilder().addEmbeds(embed.build());
        if (!buttons.isEmpty()) {
            List<ActionRow> rows = new ArrayList<>();
            for (int i = 0; i < buttons.size(); i += 5)
                rows.add(ActionRow.of(buttons.subList(i, Math.min(i + 5, buttons.size()))));
            msg.setComponents(rows);
        }
        final GuildMessageChannel dest = target;
        event.deferReply(true).queue();
        dest.sendMessage(msg.build()).queue(
                sent -> event.getHook()
                        .editOriginalEmbeds(EmbedUtils.createSuccessEmbed("Embed Sent",
                                "Your embed was sent to " + dest.getAsMention() + "."))
                        .queue(),
                err -> event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed("Send Failed", err.getMessage()))
                        .queue());
    }

    private static boolean hasContent(EmbedBuilder eb) {
        var built = eb.build();
        return (built.getTitle() != null && !built.getTitle().isBlank())
                || (built.getDescription() != null && !built.getDescription().isBlank())
                || !built.getFields().isEmpty();
    }

    public static CommandData getCommandData() {
        return Commands.slash("embed", "Send a custom embed using JSON. Use /embedgui for an interactive builder.")
                .addOptions(
                        new OptionData(OptionType.STRING, "json",
                                "Embed JSON — optionally include a \"buttons\" key for buttons (use /embedgui to generate)",
                                true),
                        new OptionData(OptionType.CHANNEL, "channel", "Channel to send to (default: current channel)",
                                false));
    }

    @Override
    public String getName() {
        return "embed";
    }

    @Override
    public String getDescription() {
        return "Send a custom embed using JSON.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}