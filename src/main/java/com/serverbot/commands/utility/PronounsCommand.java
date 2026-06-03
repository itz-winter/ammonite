package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Pronouns command for setting user pronoun roles
 */
public class PronounsCommand implements SlashCommand {

    // Common pronoun sets with autocomplete
    public static final String[] COMMON_PRONOUNS = {
            "he/him", "she/her", "they/them", "it/its", "xe/xem", "ze/zir", "ey/em",
            "fae/faer", "per/per", "ve/ver", "co/cos", "ne/nem", "e/em", "ae/aer",
            "any/all", "ask/me", "no/pronouns", "name/only"
    };

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        // Check if pronouns system is enabled
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId());
            boolean pronounsEnabled = Boolean.TRUE.equals(settings.get("enablePronouns"));

            if (!pronounsEnabled) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Feature Disabled",
                        "The pronouns system is disabled in this server.\n" +
                                "Ask an administrator to enable it with `/settings pronouns enable`."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Settings Error",
                    "Failed to check server settings: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User targetUser = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();
        String set1 = event.getOption("set1").getAsString().toLowerCase().trim();
        String set2 = event.getOption("set2") != null ? event.getOption("set2").getAsString().toLowerCase().trim()
                : null;
        String set3 = event.getOption("set3") != null ? event.getOption("set3").getAsString().toLowerCase().trim()
                : null;

        // Check if user is trying to set pronouns for someone else
        if (!targetUser.getId().equals(event.getUser().getId())) {
            Member requester = event.getMember();
            if (!requester.hasPermission(Permission.MANAGE_ROLES)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Insufficient Permissions",
                        "You need Manage Roles permission to set pronouns for other users.")).setEphemeral(true)
                        .queue();
                return;
            }
        }

        Guild guild = event.getGuild();
        Member targetMember = guild.getMember(targetUser);

        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "User Not Found", "The specified user is not in this server.")).setEphemeral(true).queue();
            return;
        }

        try {
            // Remove existing pronoun roles
            removeExistingPronounRoles(guild, targetMember);

            // Collect new pronoun sets
            List<String> pronounSets = new ArrayList<>();
            pronounSets.add(set1);
            if (set2 != null && !set2.isEmpty())
                pronounSets.add(set2);
            if (set3 != null && !set3.isEmpty())
                pronounSets.add(set3);

            // Apply new pronoun roles
            List<Role> addedRoles = new ArrayList<>();
            for (String pronounSet : pronounSets) {
                Role pronounRole = getOrCreatePronounRole(guild, pronounSet);
                if (pronounRole != null) {
                    guild.addRoleToMember(targetMember, pronounRole).queue();
                    addedRoles.add(pronounRole);
                }
            }

            if (addedRoles.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Role Creation Failed",
                        "Failed to create or assign pronoun roles. Please check bot permissions.")).setEphemeral(true)
                        .queue();
                return;
            }

            // Build success message
            StringBuilder rolesText = new StringBuilder();
            for (int i = 0; i < addedRoles.size(); i++) {
                if (i > 0)
                    rolesText.append(", ");
                rolesText.append(addedRoles.get(i).getName());
            }

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "🏷️ Pronouns Updated",
                    "**User:** " + targetUser.getAsMention() + "\n" +
                            "**Pronouns:** " + rolesText.toString() + "\n\n" +
                            "Pronoun roles have been " + (addedRoles.size() == 1 ? "applied" : "applied")
                            + " successfully!"))
                    .queue();

        } catch (PermissionException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Permission Error",
                    "I don't have sufficient permissions to manage roles. Please ensure I have:\n" +
                            "• **Manage Roles** permission\n" +
                            "• My role is above the pronoun roles in the hierarchy"))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Pronouns Update Failed",
                    "Failed to update pronouns: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void removeExistingPronounRoles(Guild guild, Member member) {
        List<Role> toRemove = new ArrayList<>();

        for (Role role : member.getRoles()) {
            String roleName = role.getName().toLowerCase();
            // Check if role looks like a pronoun role (contains "/" or common pronoun
            // patterns)
            if (roleName.contains("/") || isPronounRole(roleName)) {
                toRemove.add(role);
            }
        }

        for (Role role : toRemove) {
            try {
                guild.removeRoleFromMember(member, role).queue();
            } catch (Exception ignored) {
                // Silently ignore permission errors for existing roles
            }
        }
    }

    public static boolean isPronounRole(String roleName) {
        for (String pronoun : COMMON_PRONOUNS) {
            if (roleName.equals(pronoun)) {
                return true;
            }
        }
        return false;
    }

    private Role getOrCreatePronounRole(Guild guild, String pronounSet) {
        // First, try to find existing role
        for (Role role : guild.getRoles()) {
            if (role.getName().equalsIgnoreCase(pronounSet)) {
                return role;
            }
        }

        // Create new role if it doesn't exist
        try {
            Role newRole = guild.createRole()
                    .setName(pronounSet)
                    .setColor(generateRoleColor(pronounSet))
                    .setMentionable(false)
                    .setHoisted(false)
                    .complete();
            return newRole;
        } catch (Exception e) {
            return null;
        }
    }

    private Color generateRoleColor(String pronounSet) {
        // Generate a consistent color based on the pronoun set
        int hash = pronounSet.hashCode();

        // Use HSB color space for better color distribution
        float hue = Math.abs(hash % 360) / 360.0f;
        float saturation = 0.6f + (Math.abs(hash >> 8) % 40) / 100.0f; // 60-100%
        float brightness = 0.7f + (Math.abs(hash >> 16) % 30) / 100.0f; // 70-100%

        return Color.getHSBColor(hue, saturation, brightness);
    }

    public static CommandData getCommandData() {
        return Commands.slash("pronouns", "Set pronoun roles for yourself or another user")
                .addOptions(
                        new OptionData(OptionType.STRING, "set1", "First pronoun set", true)
                                .addChoices(
                                        new Command.Choice("he/him", "he/him"),
                                        new Command.Choice("she/her", "she/her"),
                                        new Command.Choice("they/them", "they/them"),
                                        new Command.Choice("it/its", "it/its"),
                                        new Command.Choice("xe/xem", "xe/xem"),
                                        new Command.Choice("ze/zir", "ze/zir"),
                                        new Command.Choice("ey/em", "ey/em"),
                                        new Command.Choice("fae/faer", "fae/faer"),
                                        new Command.Choice("per/per", "per/per"),
                                        new Command.Choice("ve/ver", "ve/ver"),
                                        new Command.Choice("co/cos", "co/cos"),
                                        new Command.Choice("ne/nem", "ne/nem"),
                                        new Command.Choice("e/em", "e/em"),
                                        new Command.Choice("ae/aer", "ae/aer"),
                                        new Command.Choice("any/all", "any/all"),
                                        new Command.Choice("ask/me", "ask/me"),
                                        new Command.Choice("no/pronouns", "no/pronouns"),
                                        new Command.Choice("name/only", "name/only")))
                .addOption(OptionType.USER, "user", "User to set pronouns for (defaults to yourself)", false)
                .addOptions(
                        new OptionData(OptionType.STRING, "set2", "Second pronoun set (optional)", false)
                                .addChoices(
                                        new Command.Choice("he/him", "he/him"),
                                        new Command.Choice("she/her", "she/her"),
                                        new Command.Choice("they/them", "they/them"),
                                        new Command.Choice("it/its", "it/its"),
                                        new Command.Choice("xe/xem", "xe/xem"),
                                        new Command.Choice("ze/zir", "ze/zir"),
                                        new Command.Choice("ey/em", "ey/em"),
                                        new Command.Choice("fae/faer", "fae/faer"),
                                        new Command.Choice("per/per", "per/per"),
                                        new Command.Choice("ve/ver", "ve/ver"),
                                        new Command.Choice("any/all", "any/all"),
                                        new Command.Choice("ask/me", "ask/me")))
                .addOptions(
                        new OptionData(OptionType.STRING, "set3", "Third pronoun set (optional)", false)
                                .addChoices(
                                        new Command.Choice("he/him", "he/him"),
                                        new Command.Choice("she/her", "she/her"),
                                        new Command.Choice("they/them", "they/them"),
                                        new Command.Choice("it/its", "it/its"),
                                        new Command.Choice("xe/xem", "xe/xem"),
                                        new Command.Choice("ze/zir", "ze/zir"),
                                        new Command.Choice("ey/em", "ey/em"),
                                        new Command.Choice("fae/faer", "fae/faer"),
                                        new Command.Choice("any/all", "any/all"),
                                        new Command.Choice("ask/me", "ask/me")));
    }

    @Override
    public String getName() {
        return "pronouns";
    }

    @Override
    public String getDescription() {
        return "Set pronoun roles for yourself or another user";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }
}
