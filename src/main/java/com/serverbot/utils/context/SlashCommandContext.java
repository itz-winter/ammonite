package com.serverbot.utils.context;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import com.serverbot.ServerBot;
import com.serverbot.utils.EmbedUtils;

/**
 * {@link CommandContext} implementation that wraps a {@link SlashCommandInteractionEvent}.
 *
 * <p>Delegates all option access to Discord's resolved options, and all replies
 * to the underlying interaction event (or hook if deferred).</p>
 */
public class SlashCommandContext extends CommandContext {

    private final SlashCommandInteractionEvent event;

    public SlashCommandContext(SlashCommandInteractionEvent event) {
        this.event = event;
    }

    /** Expose the underlying event for commands that still need raw access. */
    public SlashCommandInteractionEvent getEvent() {
        return event;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public User getUser() {
        return event.getUser();
    }

    @Override
    public Member getMember() {
        return event.getMember();
    }

    @Override
    public Guild getGuild() {
        return event.getGuild();
    }

    @Override
    public MessageChannelUnion getChannel() {
        return event.getChannel();
    }

    @Override
    public JDA getJDA() {
        return event.getJDA();
    }

    @Override
    public boolean isFromGuild() {
        return event.isFromGuild();
    }

    @Override
    public boolean isSlashContext() {
        return true;
    }

    // ── Option access ─────────────────────────────────────────────────────────

    @Override
    public String getStringOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsString() : null;
    }

    @Override
    public Long getLongOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsLong() : null;
    }

    @Override
    public Boolean getBooleanOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsBoolean() : null;
    }

    @Override
    public Double getDoubleOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsDouble() : null;
    }

    @Override
    public Member getMemberOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsMember() : null;
    }

    @Override
    public User getUserOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsUser() : null;
    }

    @Override
    public Role getRoleOption(String name) {
        OptionMapping opt = event.getOption(name);
        return opt != null ? opt.getAsRole() : null;
    }

    @Override
    public GuildChannel getChannelOption(String name) {
        OptionMapping opt = event.getOption(name);
        if (opt == null) return null;
        try {
            return opt.getAsChannel().asGuildMessageChannel();
        } catch (Exception e) {
            try {
                return (GuildChannel) opt.getAsChannel();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    @Override
    public boolean hasOption(String name) {
        return event.getOption(name) != null;
    }

    // ── Responding ────────────────────────────────────────────────────────────

    @Override
    public void reply(MessageEmbed embed) {
        Button shareBtn = EmbedUtils.shareButton(event.getUser().getId());
        if (deferred) {
            event.getHook().sendMessageEmbeds(embed)
                    .setComponents(ActionRow.of(shareBtn))
                    .queue();
        } else {
            event.replyEmbeds(embed)
                    .setComponents(ActionRow.of(shareBtn))
                    .queue();
        }
    }

    @Override
    public void replyEphemeral(MessageEmbed embed) {
        Button shareBtn = EmbedUtils.shareButton(event.getUser().getId());
        if (deferred) {
            event.getHook().sendMessageEmbeds(embed)
                    .setEphemeral(true)
                    .setComponents(ActionRow.of(shareBtn))
                    .queue();
        } else {
            event.replyEmbeds(embed)
                    .setEphemeral(true)
                    .setComponents(ActionRow.of(shareBtn))
                    .queue();
        }
    }

    @Override
    public void reply(String content) {
        if (deferred) {
            event.getHook().sendMessage(content).queue();
        } else {
            event.reply(content).queue();
        }
    }

    @Override
    public void replyEphemeral(String content) {
        if (deferred) {
            event.getHook().sendMessage(content).setEphemeral(true).queue();
        } else {
            event.reply(content).setEphemeral(true).queue();
        }
    }

    @Override
    public void deferReply(boolean ephemeral) {
        this.deferred = true;
        event.deferReply(ephemeral).queue();
    }

    @Override
    public void sendFollowup(MessageEmbed embed) {
        Button shareBtn = EmbedUtils.shareButton(event.getUser().getId());
        event.getHook().sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(shareBtn))
                .queue();
    }

    @Override
    public void sendFollowupEphemeral(MessageEmbed embed) {
        Button shareBtn = EmbedUtils.shareButton(event.getUser().getId());
        event.getHook().sendMessageEmbeds(embed)
                .setEphemeral(true)
                .setComponents(ActionRow.of(shareBtn))
                .queue();
    }

    @Override
    public void replyRespectingPreference(MessageEmbed embed) {
        boolean wantsEphemeral = ServerBot.getStorageManager()
                .getUserEphemeralPreference(event.getUser().getId());
        if (wantsEphemeral) {
            replyEphemeral(embed);
        } else {
            reply(embed);
        }
    }
}
