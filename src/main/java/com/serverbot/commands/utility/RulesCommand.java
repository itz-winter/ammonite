package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Rules command for managing server rules
 */
public class RulesCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        String action = event.getOption("action") != null ? event.getOption("action").getAsString() : "display";

        // Check permissions based on action
        if (!hasPermissionForAction(member, action)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You don't have permission to perform this action.\n" +
                            "Required permission: `rules." + (action.equals("display") ? "use" : action)
                            + "` or `rules.*`"))
                    .setEphemeral(true).queue();
            return;
        }

        switch (action) {
            case "display" -> handleDisplay(event);
            case "create" -> handleCreate(event);
            case "edit" -> handleEdit(event);
            case "delete" -> handleDelete(event);
            case "list" -> handleList(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Action",
                        "Valid actions: `display`, `create`, `edit`, `delete`, `list`")).setEphemeral(true).queue();
            }
        }
    }

    private boolean hasPermissionForAction(Member member, String action) {
        if (PermissionManager.hasPermission(member, "rules.*")) {
            return true;
        }

        return switch (action) {
            case "display" -> PermissionManager.hasPermission(member, "rules.use");
            case "create" -> PermissionManager.hasPermission(member, "rules.create");
            case "edit" -> PermissionManager.hasPermission(member, "rules.edit");
            case "delete" -> PermissionManager.hasPermission(member, "rules.delete");
            case "list" -> PermissionManager.hasPermission(member, "rules.use") ||
                    PermissionManager.hasPermission(member, "rules.create") ||
                    PermissionManager.hasPermission(member, "rules.edit") ||
                    PermissionManager.hasPermission(member, "rules.delete");
            default -> false;
        };
    }

    private void handleDisplay(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) settings.get("serverRules");

            if (rules == null || rules.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createInfoEmbed(
                        "📋 Server Rules",
                        "No rules have been set up for this server yet.\n" +
                                "Administrators can add rules using `/rules action:create`."))
                        .queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 " + event.getGuild().getName() + " Rules")
                    .setColor(0x3498DB)
                    .setThumbnail(event.getGuild().getIconUrl())
                    .setFooter("Please follow these rules to maintain a positive environment");

            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> rule = rules.get(i);
                String title = (String) rule.get("title");
                String description = (String) rule.get("description");

                embed.addField(
                        "**" + (i + 1) + ".** " + title,
                        description,
                        false);
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Display Error",
                    "Failed to display rules: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) {
        OptionMapping titleOption = event.getOption("title");
        OptionMapping descriptionOption = event.getOption("description");

        if (titleOption == null || descriptionOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Parameters [100]",
                    "Both `title` and `description` are required for creating rules.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        String title = titleOption.getAsString();
        String description = descriptionOption.getAsString();

        if (title.length() > 100) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Title Too Long [105]",
                    "Rule title must be 100 characters or less.\n" +
                            "Error Code: **105** - Input Too Long\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        if (description.length() > 500) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Description Too Long [105]",
                    "Rule description must be 500 characters or less.\n" +
                            "Error Code: **105** - Input Too Long\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) settings.get("serverRules");
            if (rules == null) {
                rules = new ArrayList<>();
            }

            // Check rule limit
            if (rules.size() >= 25) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Rule Limit Reached [321]",
                        "Maximum of 25 rules allowed per server.\n" +
                                "Error Code: **321** - Limit Reached\n" +
                                "Use `/error category:3` for full documentation."))
                        .setEphemeral(true).queue();
                return;
            }

            Map<String, Object> newRule = Map.of(
                    "title", title,
                    "description", description,
                    "createdBy", event.getUser().getId(),
                    "createdAt", System.currentTimeMillis());

            rules.add(newRule);
            ServerBot.getStorageManager().updateGuildSettings(guildId, "serverRules", rules);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Rule Created",
                    "**Rule #" + rules.size() + ":** " + title + "\n" +
                            "**Description:** " + description + "\n\n" +
                            "The rule has been added to the server rules list."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Creation Failed",
                    "Failed to create rule: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleEdit(SlashCommandInteractionEvent event) {
        OptionMapping numberOption = event.getOption("number");
        OptionMapping titleOption = event.getOption("title");
        OptionMapping descriptionOption = event.getOption("description");

        if (numberOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Parameter [100]",
                    "Rule number is required for editing.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        if (titleOption == null && descriptionOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Parameters [100]",
                    "At least one of `title` or `description` must be provided for editing.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        int ruleNumber = (int) numberOption.getAsLong();

        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) settings.get("serverRules");

            if (rules == null || rules.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "No Rules Found [300]",
                        "No rules exist to edit.\n" +
                                "Error Code: **300** - Resource Not Found\n" +
                                "Use `/error category:3` for full documentation."))
                        .setEphemeral(true).queue();
                return;
            }

            if (ruleNumber < 1 || ruleNumber > rules.size()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Rule Number [101]",
                        "Rule number must be between 1 and " + rules.size() + ".\n" +
                                "Error Code: **R08** - Invalid Rule Number\n" +
                                "Use `/error category:R` for full R-series documentation."))
                        .setEphemeral(true).queue();
                return;
            }

            Map<String, Object> rule = rules.get(ruleNumber - 1);
            String oldTitle = (String) rule.get("title");
            String oldDescription = (String) rule.get("description");

            // Update fields if provided
            if (titleOption != null) {
                String newTitle = titleOption.getAsString();
                if (newTitle.length() > 100) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Title Too Long [105]",
                            "Rule title must be 100 characters or less.\n" +
                                    "Error Code: **105** - Input Too Long\n" +
                                    "Use `/error category:1` for full documentation."))
                            .setEphemeral(true).queue();
                    return;
                }
                rule = Map.of(
                        "title", newTitle,
                        "description", rule.get("description"),
                        "createdBy", rule.get("createdBy"),
                        "createdAt", rule.get("createdAt"),
                        "lastEditedBy", event.getUser().getId(),
                        "lastEditedAt", System.currentTimeMillis());
            }

            if (descriptionOption != null) {
                String newDescription = descriptionOption.getAsString();
                if (newDescription.length() > 500) {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Description Too Long [105]",
                            "Rule description must be 500 characters or less.\n" +
                                    "Error Code: **105** - Input Too Long\n" +
                                    "Use `/error category:1` for full documentation."))
                            .setEphemeral(true).queue();
                    return;
                }
                rule = Map.of(
                        "title", rule.get("title"),
                        "description", newDescription,
                        "createdBy", rule.get("createdBy"),
                        "createdAt", rule.get("createdAt"),
                        "lastEditedBy", event.getUser().getId(),
                        "lastEditedAt", System.currentTimeMillis());
            }

            rules.set(ruleNumber - 1, rule);
            ServerBot.getStorageManager().updateGuildSettings(guildId, "serverRules", rules);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Rule Updated",
                    "**Rule #" + ruleNumber + " has been updated:**\n" +
                            "**Title:** " + rule.get("title") + "\n" +
                            "**Description:** " + rule.get("description")))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Edit Failed",
                    "Failed to edit rule: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleDelete(SlashCommandInteractionEvent event) {
        OptionMapping numberOption = event.getOption("number");

        if (numberOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Parameter [100]",
                    "Rule number is required for deletion.\n" +
                            "Error Code: **100** - Missing Parameter\n" +
                            "Use `/error category:1` for full documentation."))
                    .setEphemeral(true).queue();
            return;
        }

        int ruleNumber = (int) numberOption.getAsLong();

        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) settings.get("serverRules");

            if (rules == null || rules.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "No Rules Found [300]",
                        "No rules exist to delete.\n" +
                                "Error Code: **300** - Resource Not Found\n" +
                                "Use `/error category:3` for full documentation."))
                        .setEphemeral(true).queue();
                return;
            }

            if (ruleNumber < 1 || ruleNumber > rules.size()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Rule Number [101]",
                        "Rule number must be between 1 and " + rules.size() + ".\n" +
                                "Error Code: **101** - Invalid Value\n" +
                                "Use `/error category:1` for full documentation."))
                        .setEphemeral(true).queue();
                return;
            }

            Map<String, Object> deletedRule = rules.get(ruleNumber - 1);
            rules.remove(ruleNumber - 1);
            ServerBot.getStorageManager().updateGuildSettings(guildId, "serverRules", rules);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Rule Deleted",
                    "**Deleted Rule #" + ruleNumber + ":**\n" +
                            "**Title:** " + deletedRule.get("title") + "\n" +
                            "**Description:** " + deletedRule.get("description") + "\n\n" +
                            "All subsequent rules have been renumbered."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Deletion Failed",
                    "Failed to delete rule: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    private void handleList(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rules = (List<Map<String, Object>>) settings.get("serverRules");

            if (rules == null || rules.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createInfoEmbed(
                        "📋 Rules List",
                        "No rules have been created yet.\n" +
                                "Use `/rules action:create` to add your first rule."))
                        .setEphemeral(true).queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("📋 Rules Management List")
                    .setColor(0x95A5A6)
                    .setDescription("Total rules: **" + rules.size() + "**")
                    .setFooter("Use /rules action:edit or action:delete to modify rules");

            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> rule = rules.get(i);
                String title = (String) rule.get("title");
                String description = (String) rule.get("description");

                // Truncate long descriptions for list view
                String shortDesc = description.length() > 100 ? description.substring(0, 97) + "..." : description;

                embed.addField(
                        "**" + (i + 1) + ".** " + title,
                        shortDesc,
                        false);
            }

            event.replyEmbeds(embed.build()).setEphemeral(true).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "List Error",
                    "Failed to list rules: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    @Override
    public String getName() {
        return "rules";
    }

    @Override
    public String getDescription() {
        return "Manage and display server rules";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        OptionData actionOption = new OptionData(OptionType.STRING, "action", "Rules action to perform", false);
        actionOption.addChoice("Display Rules", "display");
        actionOption.addChoice("Create Rule", "create");
        actionOption.addChoice("Edit Rule", "edit");
        actionOption.addChoice("Delete Rule", "delete");
        actionOption.addChoice("List Rules (Admin)", "list");

        return Commands.slash("rules", "Manage and display server rules")
                .addOptions(
                        actionOption,
                        new OptionData(OptionType.STRING, "title", "Rule title (for create/edit)", false),
                        new OptionData(OptionType.STRING, "description", "Rule description (for create/edit)", false),
                        new OptionData(OptionType.INTEGER, "number", "Rule number (for edit/delete)", false));
    }
}