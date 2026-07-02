package com.serverbot.utils.context;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;

/**
 * Abstract command context — unified interface for slash and prefix command invocations.
 *
 * <p>All commands that support context-based execution implement their logic here
 * instead of in {@code execute(SlashCommandInteractionEvent)}, eliminating duplication
 * between slash and prefix paths.</p>
 *
 * <p>Usage pattern:</p>
 * <pre>
 *   {@literal @}Override
 *   public boolean supportsCommandContext() { return true; }
 *
 *   {@literal @}Override
 *   public void execute(SlashCommandInteractionEvent event) {
 *       executeWithContext(new SlashCommandContext(event));
 *   }
 *
 *   {@literal @}Override
 *   public void executeWithContext(CommandContext ctx) {
 *       // Unified logic using ctx.getUser(), ctx.reply(), etc.
 *   }
 * </pre>
 */
public abstract class CommandContext {

    /** Whether deferReply() has been called (only meaningful for slash context) */
    protected boolean deferred = false;

    // ── Identity ──────────────────────────────────────────────────────────────

    /** The user who invoked the command. */
    public abstract User getUser();

    /** The guild member who invoked the command. Null in DMs. */
    public abstract Member getMember();

    /** The guild the command was invoked in. Null in DMs. */
    public abstract Guild getGuild();

    /** The channel the command was invoked in. */
    public abstract MessageChannelUnion getChannel();

    /** The JDA instance. */
    public abstract JDA getJDA();

    /** True if the command was invoked in a guild (not a DM). */
    public abstract boolean isFromGuild();

    /** True for slash commands, false for prefix commands. */
    public abstract boolean isSlashContext();

    // ── Option access ─────────────────────────────────────────────────────────

    /**
     * Get a string option by name.
     * For slash: reads from slash option. For prefix: reads from parsed args map.
     * Returns null if the option was not provided.
     */
    public abstract String getStringOption(String name);

    /**
     * Get a long (integer) option by name. Returns null if not provided.
     */
    public abstract Long getLongOption(String name);

    /**
     * Get a boolean option by name. Returns null if not provided.
     */
    public abstract Boolean getBooleanOption(String name);

    /**
     * Get a double (number) option by name. Returns null if not provided.
     */
    public abstract Double getDoubleOption(String name);

    /**
     * Get a Member option by name.
     * For slash: uses Discord's resolved member.
     * For prefix: resolves via mention or ID string from args.
     * Returns null if not provided or not resolvable.
     */
    public abstract Member getMemberOption(String name);

    /**
     * Get a User option by name.
     * For slash: uses Discord's resolved user.
     * For prefix: resolves via mention or ID string from args.
     * Returns null if not provided or not resolvable.
     */
    public abstract User getUserOption(String name);

    /**
     * Get a Role option by name.
     * For slash: uses Discord's resolved role.
     * For prefix: resolves via mention or ID string from args.
     * Returns null if not provided or not resolvable.
     */
    public abstract Role getRoleOption(String name);

    /**
     * Get a Channel option by name.
     * For slash: uses Discord's resolved channel.
     * For prefix: resolves via mention or ID string from args.
     * Returns null if not provided or not resolvable.
     */
    public abstract GuildChannel getChannelOption(String name);

    /**
     * Returns true if an option with the given name was explicitly provided.
     */
    public abstract boolean hasOption(String name);

    // ── Responding ────────────────────────────────────────────────────────────

    /**
     * Reply publicly with an embed.
     * For slash: calls event.replyEmbeds() (or hook if deferred).
     * For prefix: sends to channel.
     */
    public abstract void reply(MessageEmbed embed);

    /**
     * Reply ephemerally with an embed.
     * For slash: calls event.replyEmbeds().setEphemeral(true) (or hook if deferred).
     * For prefix: sends to channel (no ephemeral support, falls back to public).
     */
    public abstract void replyEphemeral(MessageEmbed embed);

    /**
     * Reply publicly with plain text.
     */
    public abstract void reply(String content);

    /**
     * Reply ephemerally with plain text.
     * For prefix: falls back to public reply.
     */
    public abstract void replyEphemeral(String content);

    /**
     * Defer the reply for long operations.
     * For slash: calls event.deferReply(ephemeral).queue() — required before 3s timeout.
     * For prefix: no-op (tracks deferred state internally).
     *
     * @param ephemeral whether to defer ephemerally (slash only)
     */
    public abstract void deferReply(boolean ephemeral);

    /**
     * Send a followup message after deferring.
     * For slash: uses event.getHook().sendMessageEmbeds().
     * For prefix: sends to channel.
     */
    public abstract void sendFollowup(MessageEmbed embed);

    /**
     * Send an ephemeral followup message.
     * For slash: uses hook with ephemeral flag.
     * For prefix: sends to channel.
     */
    public abstract void sendFollowupEphemeral(MessageEmbed embed);

    /**
     * Returns true if deferReply() has been called.
     * For prefix this is always false.
     */
    public boolean isDeferred() {
        return deferred;
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    /**
     * Get the guild ID, or null if not in a guild.
     */
    public String getGuildId() {
        Guild g = getGuild();
        return g != null ? g.getId() : null;
    }

    /**
     * Get the user ID.
     */
    public String getUserId() {
        return getUser().getId();
    }

    /**
     * Reply with an embed, honouring the user's ephemeral preference.
     *
     * <p>If the user has opted to keep responses ephemeral (the default), this
     * behaves like {@link #replyEphemeral(MessageEmbed)}. If they have disabled
     * ephemeral, it behaves like {@link #reply(MessageEmbed)}.</p>
     *
     * <p>Use this method for all command responses that are directed at the
     * invoking user (balance, rank, work rewards, etc.). Responses meant for
     * public visibility (music announcements, level-up messages) should use
     * {@link #reply(MessageEmbed)} directly.</p>
     */
    public abstract void replyRespectingPreference(MessageEmbed embed);
}
