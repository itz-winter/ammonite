package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Avatar command — fetches a user's avatar as an ephemeral (sender-only) reply.
 * The user option is optional; omitting it returns the caller's own avatar.
 */
public class AvatarCommand implements SlashCommand {

    @Override
    public String getName() {
        return "avatar";
    }

    @Override
    public String getDescription() {
        return "Get a user's avatar";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override // must put this here cause it defaults to true for some fucking reason
    public boolean isGuildOnly() {
        return false;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping userOption = event.getOption("user");
        User target = userOption != null ? userOption.getAsUser() : event.getUser();

        // Highest-resolution avatar URL (up to 4096px), PNG fallback for animated GIFs
        String avatarUrl = target.getEffectiveAvatarUrl() + "?size=4096";
        String avatarUrlPng = target.getEffectiveAvatarUrl().replaceAll("\\.(gif|webp)$", ".png") + "?size=4096";

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(EmbedUtils.INFO_COLOR)
                .setAuthor(target.getName(), null, target.getEffectiveAvatarUrl())
                .setTitle("🖼️ " + target.getName() + "'s Avatar")
                .setImage(avatarUrl)
                .setFooter("User ID: " + target.getId());

        // If the avatar is animated, link to both PNG and GIF variants
        boolean isAnimated = target.getEffectiveAvatarUrl().contains(".gif");
        if (isAnimated) {
            embed.setDescription("[PNG](" + avatarUrlPng + ") • [GIF](" + avatarUrl + ")");
        } else {
            embed.setDescription("[Full Size](" + avatarUrl + ")");
        }

        event.replyEmbeds(embed.build())
                .setEphemeral(true)
                .queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("avatar", "Get a user's avatar")
                .addOption(OptionType.USER, "user", "The user whose avatar to fetch (defaults to yourself)", false);
    }
}
