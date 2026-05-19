package com.serverbot.commands.config;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.CustomEmojis;
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

import java.util.Map;

/**
 * Anti-spam and auto-moderation configuration command
 */
public class AntiSpamCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.antispam")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need the `admin.antispam` permission to configure anti-spam settings."
            )).setEphemeral(true).queue();
            return;
        }

        // Check if no required parameters provided - show help
        if (event.getOption("setting") == null) {
            showAntiSpamHelp(event);
            return;
        }

        String setting = event.getOption("setting").getAsString();
        
        switch (setting) {
            case "view" -> handleView(event);
            case "message-limit" -> handleMessageLimit(event);
            case "duplicate-limit" -> handleDuplicateLimit(event);
            case "mention-limit" -> handleMentionLimit(event);
            case "caps-limit" -> handleCapsLimit(event);
            case "auto-delete" -> handleAutoDelete(event);
            case "punishment" -> handlePunishment(event);
            case "mute-duration" -> handleMuteDuration(event);
            case "timeout-duration" -> handleTimeoutDuration(event);
            case "ban-duration" -> handleBanDuration(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Setting", 
                    "Invalid setting: `" + setting + "`\n" +
                    "Use `/antispam` without arguments to see all available settings and help."
                )).setEphemeral(true).queue();
            }
        }
    }

    private void showAntiSpamHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("AntiSpam Help")
                .setDescription("Configure anti-spam and auto-moderation settings")
                .setColor(0xFF4444)
                .addField("**Basic Usage**",
                    "`/antispam setting:view` - View current settings\n" +
                    "`/antispam setting:message-limit amount:<1-50>` - Set message rate limit\n" +
                    "`/antispam setting:duplicate-limit amount:<1-20>` - Set duplicate limit\n" +
                    "`/antispam setting:punishment type:<warn/mute/timeout/kick/ban>` - Set punishment", false)
                .addField("**All Settings**",
                    "• `view` - View current configuration\n" +
                    "• `message-limit` - Messages per 10 seconds (1-50)\n" +
                    "• `duplicate-limit` - Duplicate message limit (1-20)\n" +
                    "• `mention-limit` - Mentions per message (1-30)\n" +
                    "• `caps-limit` - Max CAPS percentage (10-100)\n" +
                    "• `auto-delete` - Auto-delete spam messages\n" +
                    "• `punishment` - Action for violations\n" +
                    "• `mute-duration` - Mute duration in minutes\n" +
                    "• `timeout-duration` - Timeout duration in minutes\n" +
                    "• `ban-duration` - Ban duration in minutes", false)
                .addField("**Parameters**",
                    "• `setting` - Which setting to configure (required)\n" +
                    "• `amount` - Numeric value for limits\n" +
                    "• `percentage` - CAPS percentage (for caps-limit)\n" +
                    "• `enabled` - Enable/disable (for auto-delete)\n" +
                    "• `type` - Punishment type (warn/mute/timeout/kick/ban)\n" +
                    "• `duration` - Duration in minutes for timed punishments", false)
                .addField("**Examples**",
                    "`/antispam setting:message-limit amount:8` - Allow 8 messages per 10s\n" +
                    "`/antispam setting:punishment type:mute` - Mute spammers\n" +
                    "`/antispam setting:mute-duration duration:60` - 1 hour mute\n" +
                    "`/antispam setting:auto-delete enabled:true` - Auto-delete spam", false)
                .setFooter("Use -!help to dismiss future help messages • Requires admin.antispam permission");

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private void handleView(SlashCommandInteractionEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle("AntiSpam Config")
                    .setDescription("Current anti-spam settings for **" + event.getGuild().getName() + "**");

            // Message limits
            embed.addField("Message Limits", 
                          "**Messages per 10s:** " + settings.getOrDefault("antiSpamMessageLimit", 10) + "\n" +
                          "**Duplicate messages:** " + settings.getOrDefault("antiSpamDuplicateLimit", 5) + "\n" +
                          "**Mentions per message:** " + settings.getOrDefault("antiSpamMentionLimit", 8) + "\n" +
                          "**Max CAPS percentage:** " + settings.getOrDefault("antiSpamCapsLimit", 100) + "%", 
                          false);

            // Actions
            String punishment = settings.getOrDefault("antiSpamPunishment", "warn").toString();
            String actionsText = "**Auto-delete spam:** " + (Boolean.TRUE.equals(settings.get("antiSpamAutoDelete")) ? CustomEmojis.ON : CustomEmojis.OFF) + "\n" +
                                "**Punishment:** " + punishment;
            
            // Add duration info if applicable
            if (punishment.equals("mute")) {
                actionsText += " (" + settings.getOrDefault("antiSpamMuteDuration", 30) + " min)";
            } else if (punishment.equals("timeout")) {
                actionsText += " (" + settings.getOrDefault("antiSpamTimeoutDuration", 10) + " min)";
            } else if (punishment.equals("ban")) {
                actionsText += " (" + settings.getOrDefault("antiSpamBanDuration", 1440) + " min)";
            }
            
            embed.addField("Actions", actionsText, false);

            // Status
            Boolean automodEnabled = (Boolean) settings.get("enableAutomod");
            embed.addField("Status", 
                          "**Auto-moderation:** " + (Boolean.TRUE.equals(automodEnabled) ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                          "*Configure with `/settings enable-automod`*", 
                          false);

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Settings Error", 
                "Failed to retrieve anti-spam settings: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleMessageLimit(SlashCommandInteractionEvent event) {
        OptionMapping amountOption = event.getOption("amount");
        if (amountOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter [100]", 
                "Please specify the message limit per 10 seconds.\n" +
                "Error Code: **100** - Missing Setting Parameter\n" +
                "Use `/error category:1` for full 1XX-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        long amount = amountOption.getAsLong();
        
        if (amount < 1 || amount > 50) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Amount [101]", 
                "Message limit must be between 1 and 50.\n" +
                "Error Code: **101** - Invalid Message Limit\n" +
                "Use `/error category:1` for full 1XX-series documentation."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamMessageLimit", amount);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Message Limit Updated", 
                "Users can now send up to **" + amount + " messages per 10 seconds** before triggering anti-spam."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed [400]", 
                "Failed to update message limit: " + e.getMessage() + "\n" +
                "Error Code: **400** - Configuration Update Failed\n" +
                "Use `/error category:4` for full 4XX-series documentation."
            )).setEphemeral(true).queue();
        }
    }

    private void handleDuplicateLimit(SlashCommandInteractionEvent event) {
        OptionMapping amountOption = event.getOption("amount");
        if (amountOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the duplicate message limit."
            )).setEphemeral(true).queue();
            return;
        }

        long amount = amountOption.getAsLong();
        
        if (amount < 1 || amount > 20) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Amount", "Duplicate limit must be between 1 and 20."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamDuplicateLimit", amount);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Duplicate Limit Updated", 
                "Users can send the same message up to **" + amount + " times** before triggering anti-spam."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update duplicate limit: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleMentionLimit(SlashCommandInteractionEvent event) {
        OptionMapping amountOption = event.getOption("amount");
        if (amountOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the mention limit per message."
            )).setEphemeral(true).queue();
            return;
        }

        long amount = amountOption.getAsLong();
        
        if (amount < 1 || amount > 30) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Amount", "Mention limit must be between 1 and 30."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamMentionLimit", amount);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Mention Limit Updated", 
                "Messages can contain up to **" + amount + " mentions** before triggering anti-spam."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update mention limit: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleCapsLimit(SlashCommandInteractionEvent event) {
        OptionMapping amountOption = event.getOption("percentage");
        if (amountOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the maximum CAPS percentage."
            )).setEphemeral(true).queue();
            return;
        }

        long amount = amountOption.getAsLong();
        
        if (amount < 10 || amount > 100) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Percentage", "CAPS limit must be between 10% and 100%."
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamCapsLimit", amount);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "CAPS Limit Updated", 
                "Messages with more than **" + amount + "% CAPS** will trigger anti-spam."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update CAPS limit: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleAutoDelete(SlashCommandInteractionEvent event) {
        OptionMapping enableOption = event.getOption("enabled");
        if (enableOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify whether to enable auto-deletion of spam messages."
            )).setEphemeral(true).queue();
            return;
        }

        boolean enabled = enableOption.getAsBoolean();
        
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamAutoDelete", enabled);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Auto-Delete " + (enabled ? "Enabled" : "Disabled"), 
                "Spam messages will " + (enabled ? "**automatically be deleted**" : "**not be automatically deleted**") + "."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update auto-delete setting: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handlePunishment(SlashCommandInteractionEvent event) {
        OptionMapping typeOption = event.getOption("type");
        if (typeOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the punishment type."
            )).setEphemeral(true).queue();
            return;
        }

        String punishment = typeOption.getAsString().toLowerCase();
        
        if (!punishment.matches("warn|mute|timeout|kick|ban")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Punishment", "Punishment must be one of: warn, mute, timeout, kick, ban"
            )).setEphemeral(true).queue();
            return;
        }

        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamPunishment", punishment);
            
            String description = switch (punishment) {
                case "warn" -> "Users will receive a **warning** for spam violations.";
                case "mute" -> "Users will be **temporarily muted** for spam violations.";
                case "timeout" -> "Users will be **timed out** for spam violations.";
                case "kick" -> "Users will be **kicked** for spam violations.";
                case "ban" -> "Users will be **banned** for spam violations.";
                default -> "Punishment updated.";
            };
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Punishment Updated", description
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update punishment setting: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleMuteDuration(SlashCommandInteractionEvent event) {
        OptionMapping durationOption = event.getOption("duration");
        if (durationOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the mute duration in minutes."
            )).setEphemeral(true).queue();
            return;
        }
        
        int duration = durationOption.getAsInt();
        if (duration < 1 || duration > 10080) { // max 7 days
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Duration", "Mute duration must be between 1 and 10080 minutes (7 days)."
            )).setEphemeral(true).queue();
            return;
        }
        
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamMuteDuration", duration);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Mute Duration Updated", 
                "Anti-spam mute duration set to **" + duration + " minutes**."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update mute duration: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleTimeoutDuration(SlashCommandInteractionEvent event) {
        OptionMapping durationOption = event.getOption("duration");
        if (durationOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the timeout duration in minutes."
            )).setEphemeral(true).queue();
            return;
        }
        
        int duration = durationOption.getAsInt();
        if (duration < 1 || duration > 40320) { // max 28 days
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Duration", "Timeout duration must be between 1 and 40320 minutes (28 days)."
            )).setEphemeral(true).queue();
            return;
        }
        
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamTimeoutDuration", duration);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Timeout Duration Updated", 
                "Anti-spam timeout duration set to **" + duration + " minutes**."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update timeout duration: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleBanDuration(SlashCommandInteractionEvent event) {
        OptionMapping durationOption = event.getOption("duration");
        if (durationOption == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the ban duration in minutes."
            )).setEphemeral(true).queue();
            return;
        }
        
        int duration = durationOption.getAsInt();
        if (duration < 1 || duration > 525600) { // max 1 year
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Duration", "Ban duration must be between 1 and 525600 minutes (1 year)."
            )).setEphemeral(true).queue();
            return;
        }
        
        try {
            ServerBot.getStorageManager().updateGuildSettings(event.getGuild().getId(), "antiSpamBanDuration", duration);
            
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Ban Duration Updated", 
                "Anti-spam ban duration set to **" + duration + " minutes**."
            )).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Update Failed", 
                "Failed to update ban duration: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    @Override
    public String getName() {
        return "antispam";
    }

    @Override
    public String getDescription() {
        return "Configure anti-spam and auto-moderation settings";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    // Static method for command registration
    public static CommandData getCommandData() {
        OptionData settingOption = new OptionData(OptionType.STRING, "setting", "Anti-spam setting to configure", true);
        settingOption.addChoice("View Settings", "view");
        settingOption.addChoice("Message Limit", "message-limit");
        settingOption.addChoice("Duplicate Limit", "duplicate-limit");
        settingOption.addChoice("Mention Limit", "mention-limit");
        settingOption.addChoice("CAPS Limit", "caps-limit");
        settingOption.addChoice("Auto-Delete", "auto-delete");
        settingOption.addChoice("Punishment Type", "punishment");
        settingOption.addChoice("Mute Duration", "mute-duration");
        settingOption.addChoice("Timeout Duration", "timeout-duration");
        settingOption.addChoice("Ban Duration", "ban-duration");

        OptionData punishmentOption = new OptionData(OptionType.STRING, "type", "Punishment type for violations", false);
        punishmentOption.addChoice("Warn", "warn");
        punishmentOption.addChoice("Mute", "mute");
        punishmentOption.addChoice("Timeout", "timeout");
        punishmentOption.addChoice("Kick", "kick");
        punishmentOption.addChoice("Ban", "ban");

        return Commands.slash("antispam", "Configure anti-spam and auto-moderation settings")
                .addOptions(
                    settingOption,
                    new OptionData(OptionType.INTEGER, "amount", "Amount/limit for numeric settings", false),
                    new OptionData(OptionType.INTEGER, "percentage", "Percentage for CAPS limit (10-100)", false),
                    new OptionData(OptionType.BOOLEAN, "enabled", "Enable/disable for boolean settings", false),
                    punishmentOption,
                    new OptionData(OptionType.INTEGER, "duration", "Duration in minutes for timed punishments", false)
                );
    }
}
