package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

/**
 * Allows users to configure personal bot preferences that persist across servers.
 *
 * <p>Current preferences:
 * <ul>
 *   <li><b>ephemeral</b> — whether command responses are private (only visible to you).
 *       Default: {@code true} (responses are ephemeral / private).</li>
 * </ul>
 *
 * <p>These settings are per-user and stored in {@code data/user_preferences.json}.
 */
public class PreferenceCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "Please specify a subcommand."))
                    .setEphemeral(true).queue();
            return;
        }

        switch (sub) {
            case "ephemeral" -> handleEphemeral(event);
            case "view"      -> handleView(event);
            default -> event.replyEmbeds(EmbedUtils.createErrorEmbed("Unknown Subcommand",
                    "Unknown subcommand: `" + sub + "`")).setEphemeral(true).queue();
        }
    }

    // ── Subcommand handlers ────────────────────────────────────────────────────

    /**
     * `/preference ephemeral <true|false>` — Toggle whether command responses
     * are ephemeral (visible only to you) or public.
     */
    private void handleEphemeral(SlashCommandInteractionEvent event) {
        Boolean value = event.getOption("enabled") != null
                ? event.getOption("enabled").getAsBoolean()
                : null;

        String userId = event.getUser().getId();

        if (value == null) {
            // Read current value
            boolean current = ServerBot.getStorageManager().getUserEphemeralPreference(userId);
            event.replyEmbeds(buildEphemeralStatusEmbed(current).build()).setEphemeral(true).queue();
            return;
        }

        // Write new value
        ServerBot.getStorageManager().setUserEphemeralPreference(userId, value);

        String status = value
                ? "✅ Ephemeral responses **enabled**.\nMy replies to your commands will be **private** (only visible to you)."
                : "📢 Ephemeral responses **disabled**.\nMy replies to your commands will be **public** (visible to everyone in the channel).";

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Preference Updated", status))
                .setEphemeral(true) // always ephemeral for settings changes
                .queue();
    }

    /**
     * `/preference view` — Show all current user preferences.
     */
    private void handleView(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        boolean ephemeral = ServerBot.getStorageManager().getUserEphemeralPreference(userId);

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("⚙️ Your Preferences")
                .setDescription("These settings apply to **your** interactions with the bot across all servers.")
                .addField("Ephemeral Responses",
                        (ephemeral ? "✅ Enabled" : "❌ Disabled")
                                + "\n*Command responses are " + (ephemeral ? "private (only visible to you)" : "public") + "*",
                        false)
                .setFooter("Use /preference <setting> to change a preference");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static EmbedBuilder buildEphemeralStatusEmbed(boolean enabled) {
        return EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🔒 Ephemeral Preference")
                .setDescription("Your current setting: **" + (enabled ? "Enabled" : "Disabled") + "**\n\n"
                        + (enabled
                        ? "✅ Command responses are **private** — only visible to you."
                        : "📢 Command responses are **public** — visible to everyone in the channel."))
                .addField("Change It",
                        "`/preference ephemeral enabled:true` — make responses private\n"
                                + "`/preference ephemeral enabled:false` — make responses public",
                        false);
    }

    // ── Command metadata ───────────────────────────────────────────────────────

    public static CommandData getCommandData() {
        return Commands.slash("preference", "Manage your personal bot preferences")
                .addSubcommands(
                        new SubcommandData("ephemeral", "Configure whether command responses are private (ephemeral) or public")
                                .addOption(OptionType.BOOLEAN, "enabled",
                                        "true = responses visible only to you; false = responses visible to everyone", false),
                        new SubcommandData("view", "View all of your current preferences")
                );
    }

    @Override
    public String getName() {
        return "preference";
    }

    @Override
    public String getDescription() {
        return "Manage your personal bot preferences";
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
    public boolean isGuildOnly() {
        return false;
    }
}
