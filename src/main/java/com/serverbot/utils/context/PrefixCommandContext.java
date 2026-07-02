package com.serverbot.utils.context;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@link CommandContext} implementation that wraps a prefix {@link MessageReceivedEvent}
 * and a parsed arguments map.
 *
 * <p>Option access resolves values from the pre-parsed {@code Map<String, String>} args.
 * Member/User options are resolved from the guild when the arg looks like a mention ({@code <@id>})
 * or a raw snowflake ID.</p>
 *
 * <p>Replies are sent to the source channel. {@code deferReply()} is a no-op since
 * prefix commands don't have interaction timeouts.</p>
 */
public class PrefixCommandContext extends CommandContext {

    private static final Pattern MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#(\\d+)>");
    private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&(\\d+)>");
    private static final Pattern SNOWFLAKE_PATTERN = Pattern.compile("^\\d{17,20}$");

    private final MessageReceivedEvent event;
    private final Map<String, String> args;

    /**
     * @param event the received message event
     * @param args  pre-parsed command arguments (from {@code PrefixCommandService.parseArguments()})
     */
    public PrefixCommandContext(MessageReceivedEvent event, Map<String, String> args) {
        this.event = event;
        this.args = args;
    }

    /** Expose the underlying event for commands that need direct message access. */
    public MessageReceivedEvent getEvent() {
        return event;
    }

    /** Expose the parsed args map for direct access. */
    public Map<String, String> getArgs() {
        return args;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    @Override
    public User getUser() {
        return event.getAuthor();
    }

    @Override
    public Member getMember() {
        return event.getMember();
    }

    @Override
    public Guild getGuild() {
        return event.isFromGuild() ? event.getGuild() : null;
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
        return false;
    }

    // ── Option access ─────────────────────────────────────────────────────────

    @Override
    public String getStringOption(String name) {
        return args.get(name);
    }

    @Override
    public Long getLongOption(String name) {
        String val = args.get(name);
        if (val == null) return null;
        try {
            return Long.parseLong(val.replaceAll(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Boolean getBooleanOption(String name) {
        String val = args.get(name);
        if (val == null) return null;
        return val.equalsIgnoreCase("true") || val.equalsIgnoreCase("yes") || val.equals("1");
    }

    @Override
    public Double getDoubleOption(String name) {
        String val = args.get(name);
        if (val == null) return null;
        try {
            return Double.parseDouble(val.replaceAll(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public Member getMemberOption(String name) {
        String val = args.get(name);
        if (val == null || !isFromGuild()) return null;
        String id = extractSnowflake(val, MENTION_PATTERN);
        if (id == null && SNOWFLAKE_PATTERN.matcher(val).matches()) id = val;
        if (id == null) {
            // Try name-based lookup (blocking, last resort)
            try {
                String nameSearch = val.toLowerCase();
                for (Member m : event.getGuild().getMembers()) {
                    if (m.getUser().getName().equalsIgnoreCase(nameSearch)
                            || m.getEffectiveName().equalsIgnoreCase(nameSearch)) {
                        return m;
                    }
                }
            } catch (Exception ignored) {}
            return null;
        }
        try {
            return event.getGuild().retrieveMemberById(id).complete();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public User getUserOption(String name) {
        Member member = getMemberOption(name);
        if (member != null) return member.getUser();

        // Try direct user lookup (may not be in guild)
        String val = args.get(name);
        if (val == null) return null;
        String id = extractSnowflake(val, MENTION_PATTERN);
        if (id == null && SNOWFLAKE_PATTERN.matcher(val).matches()) id = val;
        if (id == null) return null;
        try {
            return event.getJDA().retrieveUserById(id).complete();
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Role getRoleOption(String name) {
        String val = args.get(name);
        if (val == null || !isFromGuild()) return null;
        String id = extractSnowflake(val, ROLE_MENTION_PATTERN);
        if (id == null && SNOWFLAKE_PATTERN.matcher(val).matches()) id = val;
        if (id != null) {
            return event.getGuild().getRoleById(id);
        }
        // Name-based lookup
        String nameSearch = val.toLowerCase();
        for (Role r : event.getGuild().getRoles()) {
            if (r.getName().equalsIgnoreCase(nameSearch)) return r;
        }
        return null;
    }

    @Override
    public GuildChannel getChannelOption(String name) {
        String val = args.get(name);
        if (val == null || !isFromGuild()) return null;
        String id = extractSnowflake(val, CHANNEL_MENTION_PATTERN);
        if (id == null && SNOWFLAKE_PATTERN.matcher(val).matches()) id = val;
        if (id != null) {
            return event.getGuild().getGuildChannelById(id);
        }
        // Name-based lookup
        String nameSearch = val.toLowerCase().replaceFirst("^#", "");
        return event.getGuild().getTextChannelsByName(nameSearch, true).stream().findFirst().orElse(null);
    }

    @Override
    public boolean hasOption(String name) {
        return args.containsKey(name) && args.get(name) != null;
    }

    // ── Responding ────────────────────────────────────────────────────────────

    @Override
    public void reply(MessageEmbed embed) {
        event.getChannel().sendMessageEmbeds(embed).queue();
    }

    @Override
    public void replyEphemeral(MessageEmbed embed) {
        // Prefix commands don't support ephemeral — fall back to public reply
        event.getChannel().sendMessageEmbeds(embed).queue();
    }

    @Override
    public void reply(String content) {
        event.getChannel().sendMessage(content).queue();
    }

    @Override
    public void replyEphemeral(String content) {
        // No ephemeral for prefix — fall back to public
        event.getChannel().sendMessage(content).queue();
    }

    @Override
    public void deferReply(boolean ephemeral) {
        // No-op for prefix commands — there's no interaction timeout
        this.deferred = true;
    }

    @Override
    public void sendFollowup(MessageEmbed embed) {
        // Same as reply for prefix
        event.getChannel().sendMessageEmbeds(embed).queue();
    }

    @Override
    public void sendFollowupEphemeral(MessageEmbed embed) {
        // Same as reply for prefix (no ephemeral support)
        event.getChannel().sendMessageEmbeds(embed).queue();
    }

    @Override
    public void replyRespectingPreference(MessageEmbed embed) {
        // Prefix commands have no ephemeral concept — always reply publicly
        reply(embed);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Extract a snowflake ID from a string using the given pattern.
     * Returns null if the pattern doesn't match.
     */
    private static String extractSnowflake(String input, Pattern pattern) {
        Matcher m = pattern.matcher(input.trim());
        return m.find() ? m.group(1) : null;
    }
}
