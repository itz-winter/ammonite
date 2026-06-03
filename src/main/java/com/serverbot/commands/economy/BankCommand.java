package com.serverbot.commands.economy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Bank management command for economy system
 */
public class BankCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        // Check if no required parameters provided - show help
        if (event.getOption("setting") == null) {
            showBankHelp(event);
            return;
        }

        String setting = event.getOption("setting").getAsString();

        switch (setting) {
            case "balance" -> handleBalance(event);
            case "maxloan" -> handleMaxLoan(event);
            case "minloan" -> handleMinLoan(event);
            case "autocollect" -> handleAutoCollect(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Setting",
                        "Invalid setting: `" + setting + "`\n" +
                                "Valid settings: `balance`, `maxloan`, `minloan`, `autocollect`\n\n" +
                                "Use `/bank` without arguments to see the help guide."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        }
    }

    private void showBankHelp(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🏦 Bank Command Help")
                .setDescription("Manage banking system, user balances, and loan settings")
                .setColor(0x00AA00)
                .addField("**Basic Usage**",
                        "`/bank setting:balance [action:view] [user:@user]` - View/modify balance\n" +
                                "`/bank setting:maxloan amount:<points>` - Set max loan amount\n" +
                                "`/bank setting:minloan amount:<points>` - Set min loan amount\n" +
                                "`/bank setting:autocollect action:enable/disable` - Toggle auto-collect",
                        false)
                .addField("**Settings**",
                        "• `balance` - View or modify user balances\n" +
                                "• `maxloan` - Set maximum loan amount\n" +
                                "• `minloan` - Set minimum loan amount\n" +
                                "• `autocollect` - Auto loan collection toggle",
                        false)
                .addField("**Balance Actions**",
                        "• `view` - View balance (default)\n" +
                                "• `set` - Set exact balance\n" +
                                "• `add` - Add points to balance\n" +
                                "• `subtract` - Remove points from balance",
                        false)
                .addField("**Parameters**",
                        "• `setting` - Which bank setting to manage (required)\n" +
                                "• `action` - Action for balance or autocollect settings\n" +
                                "• `user` - Target user (defaults to yourself)\n" +
                                "• `amount` - Points amount for balance/loan operations",
                        false)
                .addField("**Examples**",
                        "`/bank setting:balance user:@user` - View user's balance\n" +
                                "`/bank setting:balance action:add user:@user amount:100` - Give 100 points\n" +
                                "`/bank setting:maxloan amount:1000` - Set max loan to 1000 points\n" +
                                "`/bank setting:autocollect action:enable` - Enable auto loan collection",
                        false)
                .setFooter("Use -!help to dismiss future help messages • Permissions vary by setting");

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    private void handleBalance(SlashCommandInteractionEvent event) {
        Member member = event.getMember();

        String action = event.getOption("action") != null ? event.getOption("action").getAsString() : "view";
        User target = event.getOption("user") != null ? event.getOption("user").getAsUser() : event.getUser();
        Long amount = event.getOption("amount") != null ? event.getOption("amount").getAsLong() : null;

        // Check permissions for modifying other users' balances
        if (!target.getId().equals(event.getUser().getId())
                && !PermissionManager.hasPermission(member, "economy.admin.view")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `economy.admin.view` permission to view other users' balances.")).setEphemeral(true)
                    .queue();
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            String userId = target.getId();
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

            switch (action.toLowerCase()) {
                case "view" -> {
                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "💰 Bank Balance",
                            "**User:** " + target.getAsMention() + "\n" +
                                    "**Balance:** " + currentBalance + " points"))
                            .queue();
                }
                case "set" -> {
                    if (amount == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                "Missing Amount", "Please specify the amount to set.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                        return;
                    }

                    ServerBot.getStorageManager().setBalance(guildId, userId, amount);

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "💰 Balance Updated",
                            "**User:** " + target.getAsMention() + "\n" +
                                    "**New Balance:** " + amount + " points\n" +
                                    "**Previous Balance:** " + currentBalance + " points"))
                            .queue();

                    // Log the action
                    ServerBot.getStorageManager().logModerationAction(
                            guildId, userId, event.getUser().getId(),
                            "BALANCE_SET", "Set balance to " + amount, String.valueOf(amount));
                }
                case "add" -> {
                    if (amount == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                "Missing Amount", "Please specify the amount to add.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                        return;
                    }

                    ServerBot.getStorageManager().addBalance(guildId, userId, amount);
                    long newBalance = ServerBot.getStorageManager().getBalance(guildId, userId);

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "💰 Balance Updated",
                            "**User:** " + target.getAsMention() + "\n" +
                                    "**Added:** " + amount + " points\n" +
                                    "**New Balance:** " + newBalance + " points\n" +
                                    "**Previous Balance:** " + currentBalance + " points"))
                            .queue();

                    // Log the action
                    ServerBot.getStorageManager().logModerationAction(
                            guildId, userId, event.getUser().getId(),
                            "BALANCE_ADD", "Added " + amount + " points", String.valueOf(amount));
                }
                case "subtract" -> {
                    if (amount == null) {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                "Missing Amount", "Please specify the amount to subtract.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                        return;
                    }

                    long newBalance = Math.max(0, currentBalance - amount);
                    ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);

                    event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                            "💰 Balance Updated",
                            "**User:** " + target.getAsMention() + "\n" +
                                    "**Subtracted:** " + amount + " points\n" +
                                    "**New Balance:** " + newBalance + " points\n" +
                                    "**Previous Balance:** " + currentBalance + " points"))
                            .queue();

                    // Log the action
                    ServerBot.getStorageManager().logModerationAction(
                            guildId, userId, event.getUser().getId(),
                            "BALANCE_SUBTRACT", "Subtracted " + amount + " points", String.valueOf(amount));
                }
                default -> {
                    event.replyEmbeds(EmbedUtils.createErrorEmbed(
                            "Invalid Action", "Valid actions are: view, set, add, subtract")).setEphemeral(true)
                            .queue();
                }
            }

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Balance Operation Failed [400]",
                    "Failed to handle balance operation: " + e.getMessage() + "\n" +
                            "Error Code: **400** - Operation Failed\n" +
                            "Use `/error category:4` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleMaxLoan(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "economy.admin.config")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions [200]",
                    "You need the `economy.admin.config` permission to configure loan settings.\n" +
                            "Error Code: **200** - Permission Denied\n" +
                            "Use `/error category:2` for full documentation."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Long amount = event.getOption("amount").getAsLong();

        try {
            String guildId = event.getGuild().getId();
            ServerBot.getStorageManager().updateGuildSettings(guildId, "bankMaxLoan", amount);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "🏦 Max Loan Updated",
                    "**Maximum loan amount set to:** " + amount + " points\n" +
                            "Users can now borrow up to this amount from the bank."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Update Failed",
                    "Failed to update max loan setting: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleMinLoan(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "economy.admin.config")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `economy.admin.config` permission to configure loan settings.")).setEphemeral(true)
                    .queue();
            return;
        }

        Long amount = event.getOption("amount").getAsLong();

        try {
            String guildId = event.getGuild().getId();
            ServerBot.getStorageManager().updateGuildSettings(guildId, "bankMinLoan", amount);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "🏦 Min Loan Updated",
                    "**Minimum loan amount set to:** " + amount + " points\n" +
                            "Users must borrow at least this amount from the bank."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Update Failed",
                    "Failed to update min loan setting: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private void handleAutoCollect(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "economy.admin.config")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `economy.admin.config` permission to configure auto-collect settings."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString();
        boolean enabled = action.equalsIgnoreCase("enable");

        try {
            String guildId = event.getGuild().getId();
            ServerBot.getStorageManager().updateGuildSettings(guildId, "bankAutoCollect", enabled);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "🏦 Auto-Collect " + (enabled ? "Enabled" : "Disabled"),
                    "**Auto-collect is now:** " + (enabled ? "**Enabled**" : "**Disabled**") + "\n" +
                            (enabled ? "The bank will automatically collect loan payments when due."
                                    : "Users will need to manually repay their loans.")))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Update Failed",
                    "Failed to update auto-collect setting: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        OptionData settingOption = new OptionData(OptionType.STRING, "setting", "Bank setting to manage", true);
        settingOption.addChoice("Balance", "balance");
        settingOption.addChoice("Max Loan", "maxloan");
        settingOption.addChoice("Min Loan", "minloan");
        settingOption.addChoice("Auto-Collect", "autocollect");

        OptionData actionOption = new OptionData(OptionType.STRING, "action", "Action to perform", false);
        actionOption.addChoice("View", "view");
        actionOption.addChoice("Set", "set");
        actionOption.addChoice("Add", "add");
        actionOption.addChoice("Subtract", "subtract");
        actionOption.addChoice("Enable", "enable");
        actionOption.addChoice("Disable", "disable");

        return Commands.slash("bank", "Manage banking system and user balances")
                .addOptions(
                        settingOption,
                        actionOption,
                        new OptionData(OptionType.USER, "user", "Target user (defaults to yourself)", false),
                        new OptionData(OptionType.INTEGER, "amount", "Amount for balance/loan operations", false));
    }

    @Override
    public String getName() {
        return "bank";
    }

    @Override
    public String getDescription() {
        return "Manage banking system, user balances, and loan settings";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.ECONOMY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
