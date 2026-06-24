package com.serverbot.commands.owner;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.awt.Color;
import java.time.Instant;

/**
 * Bot-owner only command to send update announcements to configured announcement channels.
 */
public class AnnounceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!PermissionUtils.isBotOwner(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Command",
                    "The command `" + event.getName() + "` was not found. Use `/help` to see available commands."))
                    .setEphemeral(true).queue();
            return;
        }

        String title = event.getOption("title").getAsString();
        String message = event.getOption("message").getAsString();
        String colorStr = event.getOption("color") != null ? event.getOption("color").getAsString() : null;

        event.deferReply(true).queue();

        try {
            // Build the announcement embed
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(message)
                    .setTimestamp(Instant.now())
                    .setFooter("Bot Update Announcement");

            if (colorStr != null) {
                try {
                    embed.setColor(Color.decode(colorStr));
                } catch (Exception e) {
                    embed.setColor(new Color(100, 149, 237)); // cornflower blue
                }
            } else {
                embed.setColor(new Color(100, 149, 237));
            }

            // Send to all guilds that have an announcement channel configured
            int sentCount = 0;
            for (Guild guild : event.getJDA().getGuilds()) {
                String channelId = getAnnouncementChannel(guild.getId());
                if (channelId == null) continue;

                TextChannel channel = guild.getTextChannelById(channelId);
                if (channel == null) continue;

                // Check permissions
                if (!guild.getSelfMember().hasPermission(channel, 
                        net.dv8tion.jda.api.Permission.MESSAGE_SEND,
                        net.dv8tion.jda.api.Permission.MESSAGE_EMBED_LINKS)) continue;

                channel.sendMessageEmbeds(embed.build()).queue(
                        success -> {}, 
                        err -> {});
                sentCount++;
            }

            event.getHook().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "Announcement Sent",
                    "**Title:** " + title + "\n" +
                    "**Sent to:** " + sentCount + " server(s)\n" +
                    "**Note:** Servers without an announcement channel configured were skipped."))
                    .queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Announcement Failed",
                    "Failed to send announcement: " + e.getMessage()))
                    .queue();
        }
    }

    /**
     * Get the configured announcement channel for a guild.
     * Stored in guild settings under "announcementChannel".
     */
    private String getAnnouncementChannel(String guildId) {
        java.util.Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        Object val = settings.get("announcementChannel");
        return val instanceof String ? (String) val : null;
    }

    public static CommandData getCommandData() {
        return Commands.slash("announce", "Send a bot update announcement to all configured channels (bot owner only)")
                .addOption(OptionType.STRING, "title", "Announcement title", true)
                .addOption(OptionType.STRING, "message", "Announcement message content", true)
                .addOption(OptionType.STRING, "color", "Embed color in hex (e.g. #FF69B4)", false);
    }

    @Override
    public String getName() {
        return "announce";
    }

    @Override
    public String getDescription() {
        return "Send bot update announcements (bot owner only)";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public boolean isOwnerOnly() {
        return true;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }
}