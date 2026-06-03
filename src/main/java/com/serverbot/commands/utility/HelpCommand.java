package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Map;

/**
 * Help command to show all available commands or specific command help
 */
public class HelpCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String commandName = event.getOption("command") != null ? event.getOption("command").getAsString() : null;

        if (commandName != null) {
            showSpecificCommandHelp(event, commandName);
        } else {
            showAllCommands(event);
        }
    }

    private void showSpecificCommandHelp(SlashCommandInteractionEvent event, String commandName) {
        SlashCommand command = ServerBot.getCommandManager().getCommand(commandName.toLowerCase());

        if (command == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Command Not Found",
                    "The command `" + commandName + "` does not exist.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("📘 Command Help: /" + command.getName())
                .setDescription(command.getDescription())
                .addField("Category", command.getCategory().toString(), true)
                .addField("Guild Only", command.isGuildOnly() ? "Yes" : "No", true)
                .addField("Requires Permissions", command.requiresPermissions() ? "Yes" : "No", true);

        // Add specific usage examples based on command
        String usage = getCommandUsage(command.getName());
        if (usage != null) {
            embed.addField("Usage", usage, false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    private void showAllCommands(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("📚 Bot Commands")
                .setDescription("Here are all available commands organized by category:");

        Map<String, SlashCommand> commands = ServerBot.getCommandManager().getAllCommands();

        // Check if the user is the bot owner to decide whether to show owner-only
        // commands
        boolean isOwner = PermissionUtils.isBotOwner(event.getUser());

        // Group commands by category, filtering out owner-only commands for non-owners
        Map<CommandCategory, StringBuilder> categoryCommands = new java.util.HashMap<>();

        for (SlashCommand command : commands.values()) {
            // Hide owner-only commands from non-owners
            if (command.isOwnerOnly() && !isOwner) {
                continue;
            }
            categoryCommands.computeIfAbsent(command.getCategory(), k -> new StringBuilder())
                    .append("`/").append(command.getName()).append("` - ")
                    .append(command.getDescription()).append("\n");
        }

        // Add each category as a field
        for (Map.Entry<CommandCategory, StringBuilder> entry : categoryCommands.entrySet()) {
            if (entry.getValue().length() > 0) {
                String value = entry.getValue().toString();
                if (value.length() > 1024) {
                    value = value.substring(0, 1020) + "...";
                }
                embed.addField(entry.getKey().toString(), value, false);
            }
        }

        embed.addField("📖 Need More Help?",
                "• Use `/help <command>` for detailed information about a specific command\n" +
                        "• Use `/error` to view comprehensive error code documentation\n" +
                        "• Use `/error category:<letter>` for specific error categories (A-W)",
                false);

        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    private String getCommandUsage(String commandName) {
        return switch (commandName.toLowerCase()) {
            case "ban" -> "`/ban <user> <duration> [reason]`\nExample: `/ban @user 7d Spamming`";
            case "kick" -> "`/kick <user> [reason]`\nExample: `/kick @user Breaking rules`";
            case "warn" -> "`/warn <user> [reason]`\nExample: `/warn @user Please follow the rules`";
            case "mute" -> "`/mute <user> <duration> [reason]`\nExample: `/mute @user 1h Inappropriate language`";
            case "echo" -> "`/echo <message> [channel]`\nExample: `/echo Hello World #general`";
            case "gamble" -> "`/gamble <game> <points>`\nGames: blackjack, cointoss, poker, rockpaperscissors";
            case "permissions" ->
                "`/permissions target:<user/role/everyone> action:<set/remove/view> node:<permission> value:<true/false>`\nExample: `/permissions target:user:@user action:set node:moderation.ban value:true`";
            case "settings" ->
                "`/settings setting:<setting-name> [value:<new-value>]`\nExample: `/settings setting:daily-reward value:100`";
            case "antispam" ->
                "`/antispam setting:<setting-name> [value:<new-value>]`\nExample: `/antispam setting:message-limit amount:10`";
            case "bank" ->
                "`/bank setting:<balance/maxloan/minloan/autocollect> [action:<set/add/remove>] [user:<@user>] [amount:<value>]`\nExample: `/bank setting:balance action:add user:@user amount:1000`";
            case "embed" ->
                "`/embed type:<simple/advanced> title:<title> description:<description> [color:<hex-color>]`\nExample: `/embed type:simple title:Welcome description:Hello everyone! color:#ff0000`";
            case "welcome" ->
                "`/welcome action:<view/message/embed-color/auto-role/enable/test> [value:<new-value>]`\nExample: `/welcome action:message text:Welcome {user} to {server}!`";
            case "status" ->
                "`/status action:<set/clear/online> [type:<playing/watching/listening>] [text:<status-text>]`\nExample: `/status action:set type:playing text:Minecraft`";
            case "pride" ->
                "`/pride type:<avatar/url/custom> flag:<flag-name> [url:<image-url>]`\nExample: `/pride type:avatar flag:rainbow`";
            case "error" -> "`/error [category:<A-W>]`\nExample: `/error category:S` (Settings errors)";
            default -> null;
        };
    }

    public static CommandData getCommandData() {
        return Commands.slash("help", "Get help with bot commands")
                .addOption(OptionType.STRING, "command", "Specific command to get help for", false);
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Get help with bot commands";
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
