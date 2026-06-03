package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.CustomEmojis;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Error codes documentation command
 */
public class ErrorCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping categoryOption = event.getOption("category");

        if (categoryOption == null) {
            showErrorCodeOverview(event);
            return;
        }

        String category = categoryOption.getAsString();

        switch (category) {
            case "1" -> show1XXErrors(event);
            case "2" -> show2XXErrors(event);
            case "3" -> show3XXErrors(event);
            case "4" -> show4XXErrors(event);
            case "5" -> show5XXErrors(event);
            case "6" -> show6XXErrors(event);
            case "7" -> show7XXErrors(event);
            case "8" -> show8XXErrors(event);
            default -> {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Category",
                        "Invalid error category: `" + category + "`\n" +
                                "Valid categories: 1, 2, 3, 4, 5, 6, 7, 8"))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            }
        }
    }

    private void showErrorCodeOverview(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🚨 Discord Bot Error Code Guide")
                .setDescription("3-digit error codes organized by category and severity")
                .setColor(0xFF4444)
                .addField("**Error Code Format**",
                        "Format: `(X)(Y)(Z)` - 3 digits (e.g., 100, 215, 403)\n" +
                                "• **First digit (X)** = Error category/system\n" +
                                "• **Last two digits (YZ)** = Specific error (lower = more critical)",
                        false)
                .addField("**1XX: Input/Validation Errors**",
                        "Missing parameters, invalid formats, out of range values\n" +
                                "Use `/error category:1` for details",
                        false)
                .addField("**2XX: Permission/Access Errors**",
                        "Insufficient permissions, access denied, blocked resources\n" +
                                "Use `/error category:2` for details",
                        false)
                .addField("**3XX: Resource Errors**",
                        "Not found, already exists, limit reached\n" +
                                "Use `/error category:3` for details",
                        false)
                .addField("**4XX: Operation Errors**",
                        "Failed operations, rate limits, timeouts\n" +
                                "Use `/error category:4` for details",
                        false)
                .addField("**5XX: System Errors**",
                        "Database failures, internal errors, critical failures\n" +
                                "Use `/error category:5` for details",
                        false)
                .addField("**6XX: Configuration Errors**",
                        "Settings issues, disabled features, invalid configurations\n" +
                                "Use `/error category:6` for details",
                        false)
                .addField("**7XX: Proxy System Errors**",
                        "Proxy member operations, tags, groups (PluralKit-style)\n" +
                                "Use `/error category:7` for details",
                        false)
                .addField("**8XX: Ticket System Errors**",
                        "Ticket operations, categories, permissions\n" +
                                "Use `/error category:8` for details",
                        false)
                .setFooter("Lower last two digits = more critical • Error details provided in error messages");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show1XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📝 1XX: Input & Validation Errors")
                .setDescription("Missing parameters, invalid formats, and validation failures")
                .setColor(0xFFA500)
                .addField("**100: Missing Required Parameter**",
                        "A required parameter was not provided", false)
                .addField("**101: Invalid Parameter Value**",
                        "Parameter value is invalid or outside acceptable range", false)
                .addField("**102: Invalid Format**",
                        "Input doesn't match expected format", false)
                .addField("**103: Value Out of Range**",
                        "Numeric value exceeds minimum or maximum limits", false)
                .addField("**104: Invalid Data Type**",
                        "Expected different data type (e.g., number instead of text)", false)
                .addField("**105: Invalid URL**",
                        "URL is malformed or inaccessible", false)
                .addField("**106: Content Too Long**",
                        "Input exceeds maximum character/size limit", false)
                .addField("**107: Content Too Short**",
                        "Input doesn't meet minimum character/size requirement", false)
                .addField("**108: Invalid Choice**",
                        "Selected option is not in the list of valid choices", false)
                .addField("**109: Validation Failed**",
                        "Input failed general validation checks", false)
                .setFooter("Check command syntax and parameter requirements");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show2XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 2XX: Permission & Access Errors")
                .setDescription("Insufficient permissions and access restrictions")
                .setColor(0xFF0000)
                .addField("**200: Permission Denied**",
                        "You lack required permissions for this action", false)
                .addField("**201: Bot Permission Denied**",
                        "Bot lacks required Discord permissions", false)
                .addField("**202: Role Hierarchy Error**",
                        "Target has equal or higher role than executor/bot", false)
                .addField("**203: Admin Only**",
                        "Command requires administrator permission", false)
                .addField("**204: Owner Only**",
                        "Command restricted to bot owner", false)
                .addField("**205: Channel Access Denied**",
                        "Cannot access specified channel", false)
                .addField("**206: User Blocked**",
                        "User is blocked from performing this action", false)
                .addField("**207: Feature Restricted**",
                        "Feature is restricted in current context", false)
                .setFooter("Contact administrator if you believe this is an error");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show3XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔍 3XX: Resource Errors")
                .setDescription("Resource not found, already exists, or limits reached")
                .setColor(0xFFFF00)
                .addField("**300: Resource Not Found**",
                        "Requested resource doesn't exist", false)
                .addField("**301: User Not Found**",
                        "Specified user not found or not in server", false)
                .addField("**302: Channel Not Found**",
                        "Specified channel doesn't exist or is inaccessible", false)
                .addField("**303: Role Not Found**",
                        "Specified role doesn't exist", false)
                .addField("**304: Data Not Found**",
                        "Requested data doesn't exist in database", false)
                .addField("**310: Resource Already Exists**",
                        "Resource with that identifier already exists", false)
                .addField("**311: Duplicate Entry**",
                        "Entry with same values already exists", false)
                .addField("**320: Limit Reached**",
                        "Maximum limit for this resource has been reached", false)
                .addField("**321: Storage Full**",
                        "Storage capacity has been exceeded", false)
                .addField("**322: Queue Full**",
                        "Queue is at maximum capacity", false)
                .setFooter("Verify resource identifiers and check limits");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show4XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("⚠️ 4XX: Operation Errors")
                .setDescription("Failed operations, rate limits, and timeouts")
                .setColor(0xFF6600)
                .addField("**400: Operation Failed**",
                        "Generic operation failure", false)
                .addField("**401: Data Update Failed**",
                        "Failed to update data", false)
                .addField("**402: Data Creation Failed**",
                        "Failed to create new data", false)
                .addField("**403: Data Deletion Failed**",
                        "Failed to delete data", false)
                .addField("**410: Rate Limit Exceeded**",
                        "Too many requests in short time period", false)
                .addField("**411: Cooldown Active**",
                        "Command is on cooldown", false)
                .addField("**420: Timeout**",
                        "Operation took too long to complete", false)
                .addField("**421: Connection Timeout**",
                        "Connection to external service timed out", false)
                .addField("**430: Transaction Failed**",
                        "Transaction could not be completed", false)
                .addField("**431: Insufficient Funds**",
                        "Not enough currency for this operation", false)
                .setFooter("Wait before retrying or try again later");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show5XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💥 5XX: System Errors")
                .setDescription("Critical failures and internal system errors")
                .setColor(0x8B0000)
                .addField("**500: Internal Error**",
                        "Unexpected internal system error", false)
                .addField("**501: Database Connection Failed**",
                        "Cannot connect to database", false)
                .addField("**502: Database Query Failed**",
                        "Database query execution failed", false)
                .addField("**503: Database Write Failed**",
                        "Failed to write data to database", false)
                .addField("**510: Service Unavailable**",
                        "Required service is temporarily unavailable", false)
                .addField("**511: External API Failed**",
                        "External API request failed", false)
                .addField("**520: Configuration Error**",
                        "System configuration is invalid", false)
                .addField("**521: File System Error**",
                        "Error accessing file system", false)
                .addField("**530: Critical Failure**",
                        "Critical system component failure", false)
                .setFooter("Contact administrator immediately for 5XX errors");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show6XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(CustomEmojis.SETTING + " 6XX: Configuration Errors")
                .setDescription("Feature settings and configuration issues")
                .setColor(0x00CED1)
                .addField("**600: Feature Disabled**",
                        "This feature is currently disabled", false)
                .addField("**601: Feature Not Configured**",
                        "Feature requires configuration before use", false)
                .addField("**602: Invalid Configuration**",
                        "Current configuration contains invalid values", false)
                .addField("**603: Configuration Incomplete**",
                        "Required configuration settings are missing", false)
                .addField("**610: Setting Not Found**",
                        "Specified setting doesn't exist", false)
                .addField("**611: Setting Update Failed**",
                        "Failed to update setting value", false)
                .addField("**620: Channel Not Set**",
                        "Required channel has not been configured", false)
                .addField("**621: Role Not Set**",
                        "Required role has not been configured", false)
                .addField("**630: Incompatible Settings**",
                        "Current settings conflict with each other", false)
                .setFooter("Use configuration commands to resolve these issues");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show7XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎭 7XX: Proxy System Errors")
                .setDescription("Proxy member operations (PluralKit-style)")
                .setColor(0x9B59B6)
                .addField("**700: Proxy Operation Failed**",
                        "General proxy operation failure", false)
                .addField("**701: Data Update Failed**",
                        "Failed to update proxy data", false)
                .addField("**702: Data Creation Failed**",
                        "Failed to create proxy data", false)
                .addField("**703: Data Deletion Failed**",
                        "Failed to delete proxy data", false)
                .addField("**710: Member Not Found**",
                        "Specified proxy member doesn't exist", false)
                .addField("**711: Member Already Exists**",
                        "Proxy member with this name already exists", false)
                .addField("**712: Group Not Found**",
                        "Specified proxy group doesn't exist", false)
                .addField("**720: Invalid Proxy Tag**",
                        "Proxy tag format is invalid", false)
                .addField("**721: Tag Index Out of Range**",
                        "Specified tag index doesn't exist", false)
                .addField("**730: Invalid Field**",
                        "Unknown or invalid field specified", false)
                .addField("**731: Invalid Value**",
                        "Field value is invalid", false)
                .setFooter("Use /proxy commands for proxy member management");

        event.replyEmbeds(embed.build()).queue();
    }

    private void show8XXErrors(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎫 8XX: Ticket System Errors")
                .setDescription("Ticket operations and management")
                .setColor(0x3498DB)
                .addField("**800: Ticket Operation Failed**",
                        "General ticket operation failure", false)
                .addField("**801: Data Update Failed**",
                        "Failed to update ticket data", false)
                .addField("**802: Data Creation Failed**",
                        "Failed to create ticket", false)
                .addField("**803: Data Deletion Failed**",
                        "Failed to close/delete ticket", false)
                .addField("**810: Ticket Not Found**",
                        "Specified ticket doesn't exist", false)
                .addField("**811: Already Has Ticket**",
                        "User already has an active ticket", false)
                .addField("**812: Category Not Found**",
                        "Specified ticket category doesn't exist", false)
                .addField("**820: Channel Not Found**",
                        "Ticket channel was deleted or inaccessible", false)
                .addField("**821: Category Channel Failed**",
                        "Failed to create Discord category channel", false)
                .addField("**830: Cannot Remove Creator**",
                        "Ticket creator cannot be removed", false)
                .addField("**831: User Add Failed**",
                        "Failed to add user to ticket", false)
                .addField("**832: User Remove Failed**",
                        "Failed to remove user from ticket", false)
                .setFooter("Use /ticket commands for ticket management");

        event.replyEmbeds(embed.build()).queue();
    }

    @Override
    public String getName() {
        return "error";
    }

    @Override
    public String getDescription() {
        return "View comprehensive error code documentation";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    public static CommandData getCommandData() {
        OptionData categoryOption = new OptionData(OptionType.STRING, "category", "Error code category to view", false);
        categoryOption.addChoice("1XX (Input/Validation)", "1");
        categoryOption.addChoice("2XX (Permission/Access)", "2");
        categoryOption.addChoice("3XX (Resource)", "3");
        categoryOption.addChoice("4XX (Operation)", "4");
        categoryOption.addChoice("5XX (System)", "5");
        categoryOption.addChoice("6XX (Configuration)", "6");
        categoryOption.addChoice("7XX (Proxy System)", "7");
        categoryOption.addChoice("8XX (Ticket System)", "8");

        return Commands.slash("error", "View comprehensive error code documentation")
                .addOptions(categoryOption);
    }
}
