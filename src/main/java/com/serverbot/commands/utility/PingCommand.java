package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class PingCommand implements SlashCommand {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Check bot latency and response time";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        JDA jda = event.getJDA();

        long start = System.currentTimeMillis();

        event.reply("Pong!").queue(message -> {
            long responseTime = System.currentTimeMillis() - start;
            long gatewayPing = jda.getGatewayPing();

            String description = String.format(
                    "**Response Time:** %d ms\n" +
                            "**Gateway Ping:** %d ms\n" +
                            "**Shard:** %d/%d",
                    responseTime,
                    gatewayPing,
                    jda.getShardInfo().getShardId() + 1,
                    jda.getShardInfo().getShardTotal());

            message.editOriginalEmbeds(
                    EmbedUtils.createInfoEmbed("Pong!", description)).queue();
        });
    }

    public static net.dv8tion.jda.api.interactions.commands.build.CommandData getCommandData() {
        return net.dv8tion.jda.api.interactions.commands.build.Commands.slash("ping",
                "Check bot latency and response time");
    }
}
