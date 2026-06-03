package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Server statistics and information command
 */
public class ServerStatsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Defer reply as this might take a moment to gather statistics
        event.deferReply().queue();

        try {
            Guild guild = event.getGuild();
            String guildId = guild.getId();

            // Get guild settings for bot configuration info
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Calculate member statistics
            List<Member> members = guild.getMembers();
            int totalMembers = members.size();
            int humanMembers = (int) members.stream().filter(m -> !m.getUser().isBot()).count();
            int botMembers = totalMembers - humanMembers;

            // Calculate online status
            int onlineMembers = (int) members.stream().filter(m -> m.getOnlineStatus() == OnlineStatus.ONLINE).count();
            int idleMembers = (int) members.stream().filter(m -> m.getOnlineStatus() == OnlineStatus.IDLE).count();
            int dndMembers = (int) members.stream().filter(m -> m.getOnlineStatus() == OnlineStatus.DO_NOT_DISTURB)
                    .count();
            int offlineMembers = totalMembers - onlineMembers - idleMembers - dndMembers;

            // Bot configuration status
            Boolean economyEnabled = (Boolean) guildSettings.get("enableEconomy");
            Boolean levelingEnabled = (Boolean) guildSettings.get("enableLeveling");
            Boolean automodEnabled = (Boolean) guildSettings.get("enableAutomod");
            Boolean welcomeEnabled = (Boolean) guildSettings.get("welcomeEnabled");

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(CustomEmojis.INFO + " Server Statistics")
                    .setDescription("Comprehensive statistics for **" + guild.getName() + "**")
                    .setThumbnail(guild.getIconUrl());

            // Server information
            embed.addField(CustomEmojis.INFO + " Server Information",
                    "**Name:** " + guild.getName() + "\n" +
                            "**Owner:** " + guild.getOwner().getAsMention() + "\n" +
                            "**Created:** " + guild.getTimeCreated().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"))
                            + "\n" +
                            "**Verification:** " + guild.getVerificationLevel() + "\n" +
                            "**Boost Level:** " + guild.getBoostTier() + " (" + guild.getBoostCount() + " boosts)",
                    false);

            // Member statistics
            embed.addField(CustomEmojis.INFO + " Members (" + totalMembers + ")",
                    "**Humans:** " + humanMembers + "\n" +
                            "**Bots:** " + botMembers + "\n" +
                            CustomEmojis.ONLINE + "** Online:** " + onlineMembers + "\n" +
                            CustomEmojis.IDLE + "** Idle:** " + idleMembers + "\n" +
                            CustomEmojis.DND + "** DND:** " + dndMembers + "\n" +
                            CustomEmojis.OFFLINE + "** Offline:** " + offlineMembers,
                    true);

            // Channel statistics
            int textChannels = guild.getTextChannels().size();
            int voiceChannels = guild.getVoiceChannels().size();
            int categories = guild.getCategories().size();
            int totalChannels = textChannels + voiceChannels;

            embed.addField(CustomEmojis.INFO + " Channels (" + totalChannels + ")",
                    "**Text:** " + textChannels + "\n" +
                            "**Voice:** " + voiceChannels + "\n" +
                            "**Categories:** " + categories,
                    true);

            // Role statistics
            int totalRoles = guild.getRoles().size();
            embed.addField(CustomEmojis.INFO + " Roles",
                    "**Total:** " + totalRoles,
                    true);

            // Bot features status
            embed.addField(CustomEmojis.INFO + " Bot Features",
                    "**Economy:** " + (Boolean.TRUE.equals(economyEnabled) ? CustomEmojis.ON : CustomEmojis.OFF) + "\n"
                            +
                            "**Leveling:** "
                            + (Boolean.TRUE.equals(levelingEnabled) ? CustomEmojis.ON : CustomEmojis.OFF) + "\n" +
                            "**Auto-mod:** "
                            + (Boolean.TRUE.equals(automodEnabled) ? CustomEmojis.ON : CustomEmojis.OFF) + "\n" +
                            "**Welcome:** "
                            + (Boolean.TRUE.equals(welcomeEnabled) ? CustomEmojis.ON : CustomEmojis.OFF),
                    true);

            // Database statistics
            try {
                // Count user data entries
                int userDataCount = ServerBot.getStorageManager().getUserDataCount(guildId);
                int settingsCount = guildSettings.size();

                embed.addField(CustomEmojis.SAVE + " Database",
                        "**User Records:** " + userDataCount + "\n" +
                                "**Settings:** " + settingsCount,
                        true);
            } catch (Exception e) {
                embed.addField(CustomEmojis.SAVE + " Database",
                        "Unable to fetch statistics",
                        true);
            }

            // Emojis and stickers
            int emojis = guild.getEmojis().size();
            int stickers = guild.getStickers().size();

            embed.addField(CustomEmojis.INFO + " Content",
                    "**Emojis:** " + emojis + "\n" +
                            "**Stickers:** " + stickers,
                    true);

            embed.setFooter("Server ID: " + guild.getId() + " | Requested by " + event.getUser().getEffectiveName());

            event.getHook().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Statistics Error",
                    "Failed to gather server statistics: " + e.getMessage())).queue();
        }
    }

    @Override
    public String getName() {
        return "serverstats";
    }

    @Override
    public String getDescription() {
        return "Display comprehensive server statistics";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        return Commands.slash("serverstats", "Display comprehensive server statistics");
    }
}
