package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.CustomEmojis;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.lang.management.ManagementFactory;

/**
 * Info command showing bot information and statistics
 */
public class InfoCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        JDA jda = event.getJDA();
        
        // Get runtime information
        long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = uptimeMillis / 1000;
        String uptime = formatUptime(uptimeSeconds);
        
        // Get memory usage
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long memoryTotal = runtime.totalMemory() / (1024 * 1024);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🐚 " + jda.getSelfUser().getName())
                .setThumbnail(jda.getSelfUser().getEffectiveAvatarUrl());

        // Bot Stats
        embed.addField("📊 Bot Statistics",
                "**Servers:** " + jda.getGuilds().size() + "\n" +
                "**Users:** " + jda.getUsers().size() + "\n" +
                "**Commands:** " + ServerBot.getCommandManager().getAllCommands().size(),
                true);

        // Technical Info
        embed.addField(CustomEmojis.SETTING + " Technical Info",
                "**Uptime:** " + uptime + "\n" +
                "**Memory:** " + memoryUsed + "/" + memoryTotal + " MB\n" +
                "**Java:** " + System.getProperty("java.version"),
                true);

        // Version Info
        String version = ServerBot.getConfigManager().getConfig().getBotVersion();
        embed.addField("📋 Version Info",
                "**Bot Version:** " + version + "\n" +
                "**JDA Version:** 6.4.1\n" +
                "**API Version:** " + jda.getGatewayIntents().size() + " intents",
                true);

        // Features
        embed.addField("🎯 Features",
                "• Moderation & Auto-Moderation\n" +
                "• Economy & Banking\n" +
                "• Leveling & XP\n" +
                "• Music Playback\n" +
                "• Games (Chess, Poker, Blackjack)\n" +
                "• Reaction Roles\n" +
                "• Proxy System\n" +
                "• Cross-Server Global Chat\n" +
                "• Ticket System\n" +
                "• Comprehensive Logging",
                false);

        // Support
        String supportInvite = ServerBot.getConfigManager().getConfig().getSupportServerInvite();
        if (supportInvite != null && !supportInvite.isEmpty()) {
            embed.addField("🆘 Support", "[Join Support Server](" + supportInvite + ")", true);
        }

        embed.setFooter("Made with ❤️ using JDA", jda.getSelfUser().getEffectiveAvatarUrl());

        event.replyEmbeds(embed.build()).queue();
    }

    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 || sb.length() == 0) sb.append(secs).append("s");

        return sb.toString().trim();
    }

    public static CommandData getCommandData() {
        return Commands.slash("info", "Show bot information and statistics");
    }

    @Override
    public String getName() {
        return "info";
    }

    @Override
    public String getDescription() {
        return "Show bot information and statistics";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }
}
