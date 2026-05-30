package com.serverbot.commands.config;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Permission management command with autocomplete on the node option.
 * Supports viewing, setting, and removing permissions for users, roles,
 * and @everyone.
 */
public class PermissionsCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "Servers only.")).setEphemeral(true).queue();
            return;
        }
        if (!PermissionManager.hasPermission(event.getMember(), "admin.permissions")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("No Permission",
                    "You need `admin.permissions` to manage permissions.")).setEphemeral(true).queue();
            return;
        }

        OptionMapping actionOpt = event.getOption("action");
        if (actionOpt == null) {
            showHelp(event);
            return;
        }

        switch (actionOpt.getAsString()) {
            case "view" -> handleView(event);
            case "set" -> handleSet(event);
            case "remove" -> handleRemove(event);
            default -> showHelp(event);
        }
    }

    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String focused = event.getFocusedOption().getName();
        if (!"node".equals(focused)) {
            event.replyChoices().queue();
            return;
        }

        String input = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = PermissionManager.getAllPermissionNodes().stream()
                .filter(n -> n.toLowerCase().contains(input))
                .sorted()
                .limit(25)
                .map(n -> new Command.Choice(n, n))
                .collect(Collectors.toList());
        event.replyChoices(choices).queue();
    }

    // Handlers

    private void handleView(SlashCommandInteractionEvent event) {
        String guildId = event.getGuild().getId();
        OptionMapping targetOpt = event.getOption("target");

        if (targetOpt == null) {
            // Show @everyone permissions
            Map<String, Boolean> perms = PermissionManager.getEveryonePermissions(guildId);
            event.replyEmbeds(buildPermEmbed("@everyone", null, perms, event.getGuild().getName()).build())
                    .setEphemeral(true).queue();
            return;
        }

        try {
            Member member = targetOpt.getAsMember();
            if (member != null) {
                Map<String, Boolean> perms = PermissionManager.getUserPermissions(guildId, member.getId());
                event.replyEmbeds(
                        buildPermEmbed(member.getEffectiveName(), "\uD83D\uDC64", perms, event.getGuild().getName())
                                .build())
                        .setEphemeral(true).queue();
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            Role role = targetOpt.getAsRole();
            Map<String, Boolean> perms = PermissionManager.getRolePermissions(guildId, role.getId());
            event.replyEmbeds(buildPermEmbed(role.getName(), "\uD83C\uDFAD", perms, event.getGuild().getName()).build())
                    .setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "Could not resolve target.")).setEphemeral(true)
                    .queue();
        }
    }

    private void handleSet(SlashCommandInteractionEvent event) {
        OptionMapping targetOpt = event.getOption("target");
        OptionMapping nodeOpt = event.getOption("node");

        if (nodeOpt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Node",
                    "Specify a permission node. Use autocomplete to browse available nodes.")).setEphemeral(true)
                    .queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String node = nodeOpt.getAsString();
        OptionMapping valueOpt = event.getOption("value");
        boolean allow = valueOpt == null || valueOpt.getAsBoolean(); // default: allow

        if (targetOpt == null) {
            // @everyone
            PermissionManager.setEveryonePermission(guildId, node, allow);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Permission " + (allow ? "Granted" : "Denied"),
                    "@everyone — `" + node + "` \u2192 **" + (allow ? "ALLOW" : "DENY") + "**")).queue();
            return;
        }

        try {
            Member member = targetOpt.getAsMember();
            if (member != null) {
                PermissionManager.setUserPermission(guildId, member.getId(), node, allow);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                        "Permission " + (allow ? "Granted" : "Denied"),
                        member.getAsMention() + " — `" + node + "` \u2192 **" + (allow ? "ALLOW" : "DENY") + "**"))
                        .queue();
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            Role role = targetOpt.getAsRole();
            PermissionManager.setRolePermission(guildId, role.getId(), node, allow);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Permission " + (allow ? "Granted" : "Denied"),
                    role.getAsMention() + " — `" + node + "` \u2192 **" + (allow ? "ALLOW" : "DENY") + "**")).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "Could not resolve target.")).setEphemeral(true)
                    .queue();
        }
    }

    private void handleRemove(SlashCommandInteractionEvent event) {
        OptionMapping targetOpt = event.getOption("target");
        OptionMapping nodeOpt = event.getOption("node");

        if (nodeOpt == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Missing Node",
                    "Specify a permission node to remove.")).setEphemeral(true).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        String node = nodeOpt.getAsString();

        if (targetOpt == null) {
            PermissionManager.removeEveryonePermission(guildId, node);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Permission Removed",
                    "@everyone — `" + node + "` reset to default.")).queue();
            return;
        }

        try {
            Member member = targetOpt.getAsMember();
            if (member != null) {
                PermissionManager.removeUserPermission(guildId, member.getId(), node);
                event.replyEmbeds(EmbedUtils.createSuccessEmbed("Permission Removed",
                        member.getAsMention() + " — `" + node + "` reset to default.")).queue();
                return;
            }
        } catch (Exception ignored) {
        }

        try {
            Role role = targetOpt.getAsRole();
            PermissionManager.removeRolePermission(guildId, role.getId(), node);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed("Permission Removed",
                    role.getAsMention() + " — `" + node + "` reset to default.")).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error", "Could not resolve target.")).setEphemeral(true)
                    .queue();
        }
    }

    // UI helpers

    private EmbedBuilder buildPermEmbed(String name, String icon, Map<String, Boolean> perms, String guildName) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + "  Permissions \u2014 " + (icon != null ? icon + " " : "") + name)
                .setColor(0x5865F2)
                .setFooter(guildName + "  \u2022  Explicit overrides only \u2014 defaults not shown");

        if (perms.isEmpty()) {
            eb.setDescription("*No explicit overrides. All permissions use their server defaults.*");
        } else {
            StringBuilder sb = new StringBuilder();
            perms.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .limit(40)
                    .forEach(e -> sb.append(e.getValue() ? CustomEmojis.ON : CustomEmojis.OFF)
                            .append("  `").append(e.getKey()).append("`\n"));
            if (perms.size() > 40)
                sb.append("\u2026 and ").append(perms.size() - 40).append(" more.");
            eb.setDescription(sb.toString());
        }
        return eb;
    }

    private void showHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle(CustomEmojis.INFO + "  /permissions — Help")
                .setColor(0x5865F2)
                .setDescription("Manage fine-grained permission nodes for users, roles, and @everyone.")
                .addField("Actions",
                        "**view** — Show explicit overrides for a target (omit target \u2192 @everyone)\n"
                                + "**set** — Grant or deny a node (`value: true` = allow, `false` = deny)\n"
                                + "**remove** — Reset a node back to server default",
                        false)
                .addField("Tips",
                        "\u2022 Omitting `target` targets `@everyone`.\n"
                                + "\u2022 The `node` field has autocomplete \u2014 just start typing!\n"
                                + "\u2022 Wildcard nodes like `mod.*` grant/deny entire groups.\n"
                                + "\u2022 Explicit deny always wins over allow.",
                        false)
                .setFooter("Guild owners and Discord Administrators bypass all permission checks.");
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    // Metadata

    @Override
    public String getName() {
        return "permissions";
    }

    @Override
    public String getDescription() {
        return "Manage server permission nodes for users, roles, and @everyone";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    public static CommandData getCommandData() {
        OptionData actionOpt = new OptionData(OptionType.STRING, "action",
                "What to do", false)
                .addChoice("View permissions", "view")
                .addChoice("Set / override", "set")
                .addChoice("Remove override", "remove");

        OptionData nodeOpt = new OptionData(OptionType.STRING, "node",
                "Permission node (e.g. mod.ban, admin.*) \u2014 start typing for autocomplete", false)
                .setAutoComplete(true);

        return Commands.slash("permissions", "Manage server permission nodes for users, roles, and @everyone")
                .addOptions(
                        actionOpt,
                        new OptionData(OptionType.MENTIONABLE, "target",
                                "User or role to manage (omit for @everyone)", false),
                        nodeOpt,
                        new OptionData(OptionType.BOOLEAN, "value",
                                "true = allow, false = deny (default: true)", false));
    }
}
