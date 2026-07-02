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
                    "The command `" + commandName + "` does not exist.\nUse `/help` to see all available commands."))
                    .setEphemeral(true)
                    .setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                            net.dv8tion.jda.api.components.buttons.Button.secondary(
                                    "share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share")))
                    .queue();
            return;
        }

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("📘 Command: /" + command.getName())
                .setDescription(command.getDescription());

        // ── Core fields ──────────────────────────────────────────────────────
        embed.addField("Category", command.getCategory().toString(), true);
        embed.addField("Guild Only", command.isGuildOnly() ? "Yes" : "No", true);

        String permStr = command.requiresPermissions() ? "Yes" : "No";
        String permNode = getPermissionNode(command.getName());
        if (permNode != null) permStr += "\n`" + permNode + "`";
        embed.addField("Requires Permissions", permStr, true);

        if (command.isOwnerOnly()) {
            embed.addField("Owner Only", "✅ This command is restricted to the bot owner.", false);
        }

        // ── Cooldown ─────────────────────────────────────────────────────────
        String cooldown = getCommandCooldown(command.getName());
        if (cooldown != null) {
            embed.addField("Cooldown", cooldown, true);
        }

        // ── Prefix aliases ───────────────────────────────────────────────────
        String aliases = getPrefixAliases(command.getName());
        if (aliases != null) {
            embed.addField("Prefix Aliases", aliases, false);
        }

        // ── Subcommands ──────────────────────────────────────────────────────
        String subcommands = getSubcommandList(command.getName());
        if (subcommands != null) {
            embed.addField("Subcommands", subcommands, false);
        }

        // ── Usage ─────────────────────────────────────────────────────────────
        String usage = getCommandUsage(command.getName());
        if (usage != null) {
            embed.addField("Usage & Examples", usage, false);
        }

        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Returns the permission node required for a command, or null. */
    private static String getPermissionNode(String cmd) {
        return switch (cmd.toLowerCase()) {
            case "ban"       -> "moderation.ban";
            case "kick"      -> "moderation.kick";
            case "mute"      -> "moderation.mute";
            case "warn"      -> "moderation.warn";
            case "unwarn"    -> "moderation.warn";
            case "timeout"   -> "moderation.timeout";
            case "purge"     -> "moderation.purge";
            case "lockdown"  -> "moderation.lockdown";
            case "softban"   -> "moderation.softban";
            case "unban"     -> "moderation.unban";
            case "unmute"    -> "moderation.unmute";
            case "hist", "warns", "check" -> "moderation.view";
            case "setbalance", "addbalance", "subtractbalance" -> "economy.admin";
            case "chargeback"    -> "economy.admin";
            case "xp", "level"  -> "leveling.admin";
            case "settings"     -> "admin.settings";
            case "antispam"     -> "admin.antispam";
            case "welcome"      -> "admin.welcome";
            case "logging", "log" -> "admin.logging";
            case "permissions"  -> "admin.permissions";
            case "reactionrole" -> "admin.reactionrole";
            case "autoconfig"   -> "admin.settings";
            case "suspiciouslist" -> "admin.suspicious";
            default -> null;
        };
    }

    /** Returns the cooldown description for a command, or null. */
    private static String getCommandCooldown(String cmd) {
        return switch (cmd.toLowerCase()) {
            case "work"  -> "Configurable (default: 1 hour)";
            case "daily" -> "24 hours";
            case "rob"   -> "Configurable";
            case "dice", "flip", "slots", "gamble", "blackjack" -> "Configurable (default: none)";
            default -> null;
        };
    }

    /** Returns the prefix alias line for a command, or null. */
    private static String getPrefixAliases(String cmd) {
        return switch (cmd.toLowerCase()) {
            case "balance"          -> "`!balance` / `!bal`";
            case "work"             -> "`!work`";
            case "daily"            -> "`!daily`";
            case "balt op"          -> "`!baltop`";
            case "pay"              -> "`!pay <@user> <amount>`";
            case "addbalance"       -> "`!add <@user> <amount>` / `!addbalance`";
            case "subtractbalance"  -> "`!subtract <@user> <amount>` / `!subtractbalance`";
            case "chargeback"       -> "`!chargeback`";
            case "ban"              -> "`!ban <@user> <duration> [reason]`";
            case "kick"             -> "`!kick <@user> [reason]`";
            case "mute"             -> "`!mute <@user> <duration> [reason]`";
            case "unmute"           -> "`!unmute <@user>`";
            case "unban"            -> "`!unban <userId>`";
            case "warn"             -> "`!warn <@user> [reason]`";
            case "unwarn"           -> "`!unwarn <userId>`";
            case "timeout"          -> "`!timeout <@user> <duration> [reason]`";
            case "purge"            -> "`!purge <amount>`";
            case "hist"             -> "`!hist <@user>`";
            case "warns"            -> "`!warns <@user>`";
            case "check"            -> "`!check <@user>`";
            case "rank"             -> "`!rank [@user]`";
            case "leaderboard", "lb" -> "`!leaderboard` / `!lb`";
            case "help"             -> "`!help [command]`";
            case "ping"             -> "`!ping`";
            case "info"             -> "`!info`";
            case "queue"            -> "`!queue [page]`";
            case "play"             -> "`!play <url or search>`";
            case "skip"             -> "`!skip`";
            case "stop"             -> "`!stop`";
            case "pause"            -> "`!pause`";
            case "volume"           -> "`!volume <0-100>`";
            case "repeat"           -> "`!repeat`";
            case "shuffle"          -> "`!shuffle`";
            case "playlist"         -> "`!playlist <list|create|delete|play|add>` / `!pl`";
            case "antispam"         -> "`!antispam`";
            case "rolepersistence"  -> "`!rolepersistence <enable|disable|status>` / `!rp`";
            case "reactionrole"     -> "`!reactionrole` / `!rr`";
            case "suspiciouslist"   -> "`!suspiciouslist [page]` / `!sl`";
            case "settings"         -> "`!settings`";
            case "autoconfig"       -> "`!autoconfig` / `!setup`";
            case "preference"       -> "`!preference`";
            default -> null;
        };
    }

    /** Returns a newline-delimited list of subcommands for commands that have them, or null. */
    private static String getSubcommandList(String cmd) {
        return switch (cmd.toLowerCase()) {
            case "bank" ->
                "`balance` — View your bank balance and loan status\n" +
                "`maxloan` — Set the maximum loan amount\n" +
                "`minloan` — Set the minimum loan amount\n" +
                "`autocollect` — Toggle automatic loan repayment from balance";
            case "gamble" ->
                "`blackjack` — Play blackjack\n" +
                "`cointoss` — Flip a coin\n" +
                "`poker` — Play poker\n" +
                "`rockpaperscissors` — Play rock paper scissors";
            case "antispam" ->
                "`enable` — Enable antispam\n" +
                "`disable` — Disable antispam\n" +
                "`status` — View current antispam settings\n" +
                "`set <setting> <value>` — Change a specific antispam setting";
            case "settings" ->
                "`view [setting]` — View one or all settings\n" +
                "`set <setting> <value>` — Update a setting\n" +
                "`reset [setting]` — Reset to default";
            case "permissions" ->
                "`set <target> <node> <true|false>` — Grant or deny a permission\n" +
                "`remove <target> <node>` — Remove a permission override\n" +
                "`view [target]` — View effective permissions";
            case "welcome" ->
                "`enable` / `disable` — Toggle welcome messages\n" +
                "`message <text>` — Set the welcome message\n" +
                "`channel <#channel>` — Set the welcome channel\n" +
                "`dm enable|disable` — Toggle welcome DMs\n" +
                "`auto-role <@role>` — Set the auto-role on join\n" +
                "`test` — Send a test welcome message";
            case "logging" ->
                "`enable` / `disable` — Toggle logging\n" +
                "`channel <#channel>` — Set the log channel\n" +
                "`view` — Show current logging settings";
            case "ticket" ->
                "`create` — Open a new support ticket\n" +
                "`close` — Close the current ticket\n" +
                "`add <@user>` — Add a user to the ticket\n" +
                "`remove <@user>` — Remove a user from the ticket";
            case "playlist" ->
                "`list` — Show all your playlists\n" +
                "`create <name>` — Create a new playlist\n" +
                "`delete <name>` — Delete a playlist\n" +
                "`play <name>` — Queue all tracks in a playlist\n" +
                "`add <name> <url>` — Add a track to a playlist";
            case "proxy" ->
                "`member list` — List all proxy members\n" +
                "`member create <name>` — Create a new proxy member\n" +
                "`member delete <name>` — Delete a proxy member\n" +
                "`settings` — View proxy settings\n" +
                "`autoproxy <member|off>` — Set auto-proxy member";
            case "flags" ->
                "`list` — Show all available pride flags\n" +
                "`display <flag>` — Show what a flag looks like";
            case "suspiciouslist" ->
                "`view [page]` — Paginated list of all suspicious accounts\n" +
                "`add <userId> [reason]` — Flag a user as suspicious\n" +
                "`remove <userId>` — Remove a user from the list\n" +
                "`validate <userId>` — Mark a user as reviewed and legitimate\n" +
                "`ban <userId> [reason]` — Ban a suspicious user immediately\n" +
                "`review` — Step through all pending entries one by one";
            case "reports" ->
                "`error [query] [sort] [page]` — View submitted error reports";
            case "preference" ->
                "`ephemeral [enabled:true|false]` — Toggle ephemeral (private) responses\n" +
                "`view` — Show all your current preferences";
            case "reactionrole" ->
                "`add <messageId> <emoji> <@role>` — Add a reaction-role binding\n" +
                "`remove <messageId> <emoji>` — Remove a binding\n" +
                "`list` — List all bindings in this server";
            case "globalchat" ->
                "`enable` / `disable` — Toggle global chat for this channel\n" +
                "`status` — Show global chat status";
            default -> null;
        };
    }

    private static String getCommandUsage(String commandName) {
        return switch (commandName.toLowerCase()) {
            // ── Moderation ──────────────────────────────────────────────────────────
            case "ban" ->
                "`/ban <user> <duration> [reason]`\n" +
                "Duration format: `7d`, `2h`, `30m`, or `0` for permanent\n" +
                "Example: `/ban @user 7d Repeated spamming`";
            case "kick" ->
                "`/kick <user> [reason]`\n" +
                "Example: `/kick @user Breaking community rules`";
            case "warn" ->
                "`/warn <user> [reason]`\n" +
                "Example: `/warn @user Please follow the rules`";
            case "mute" ->
                "`/mute <user> <duration> [reason]`\n" +
                "Example: `/mute @user 1h Inappropriate language`";
            case "unmute" ->
                "`/unmute <user>`\n" +
                "Example: `/unmute @user`";
            case "unban" ->
                "`/unban <userId> [reason]`\n" +
                "Example: `/unban 123456789012345678`";
            case "timeout" ->
                "`/timeout <user> <duration> [reason]`\n" +
                "Example: `/timeout @user 10m Please calm down`";
            case "purge" ->
                "`/purge <amount> [user]`\n" +
                "Deletes up to 100 messages. Optional: filter by user.\n" +
                "Example: `/purge 50` or `/purge 20 @user`";
            case "softban" ->
                "`/softban <user> [reason]`\n" +
                "Bans then immediately unbans the user (removes messages, keeps server).\n" +
                "Example: `/softban @user Spam cleanup`";
            case "hist" ->
                "`/hist <user> [page]`\n" +
                "Show full moderation history for a user.\n" +
                "Example: `/hist @user`";
            case "warns" ->
                "`/warns <user>`\n" +
                "Show all active warnings for a user.\n" +
                "Example: `/warns @user`";
            case "unwarn" ->
                "`/unwarn <warnId>`\n" +
                "Remove a specific warning by its ID.\n" +
                "Example: `/unwarn abc123`";
            case "check" ->
                "`/check <user>`\n" +
                "Show a quick summary of a user's moderation history.\n" +
                "Example: `/check @user`";
            case "lockdown" ->
                "`/lockdown [channel]`\n" +
                "Lock or unlock a channel (toggle). Defaults to current channel.\n" +
                "Example: `/lockdown #general`";
            // ── Economy ─────────────────────────────────────────────────────────────
            case "balance" ->
                "`/balance [@user]`\n" +
                "View your balance or another user's balance.\n" +
                "Example: `/balance` or `/balance @user`";
            case "pay" ->
                "`/pay <user> <amount>`\n" +
                "Transfer currency to another user.\n" +
                "Example: `/pay @user 500`";
            case "baltop" ->
                "`/baltop [page]`\n" +
                "Show the server leaderboard for balance.\n" +
                "Example: `/baltop` or `/baltop 2`";
            case "daily" ->
                "`/daily`\n" +
                "Claim your daily currency reward. Resets every 24 hours.\n" +
                "Streak bonuses increase your reward each consecutive day!";
            case "work" ->
                "`/work`\n" +
                "Work to earn currency. Has a configurable cooldown.\n" +
                "The reward and job description are randomised each time.";
            case "rob" ->
                "`/rob <user>`\n" +
                "Attempt to rob another user. Risk losing your own balance if caught!\n" +
                "Example: `/rob @user`";
            case "dice" ->
                "`/dice <bet>`\n" +
                "Roll dice and bet currency.\n" +
                "Example: `/dice 100`";
            case "flip" ->
                "`/flip <bet> <heads|tails>`\n" +
                "Bet on a coin flip.\n" +
                "Example: `/flip 200 heads`";
            case "slots" ->
                "`/slots <bet>`\n" +
                "Play the slot machine.\n" +
                "Example: `/slots 50`";
            case "gamble" ->
                "`/gamble <game> <bet>`\n" +
                "Available games: `blackjack`, `cointoss`, `poker`, `rockpaperscissors`\n" +
                "Example: `/gamble blackjack 100`";
            case "blackjack" ->
                "`/blackjack <bet>`\n" +
                "Play a hand of blackjack against the dealer.\n" +
                "Example: `/blackjack 250`";
            case "bank" ->
                "`/bank <subcommand> [options]`\n" +
                "Example: `/bank balance` • `/bank maxloan set amount:5000`";
            case "chargeback" ->
                "`/chargeback <transactionId> [reason]`\n" +
                "Reverse a payment transaction. Requires economy admin permission.\n" +
                "Example: `/chargeback tx_abc123 Wrong amount`";
            case "setbalance" ->
                "`/setbalance <user> <amount>`\n" +
                "Set a user's balance to a specific value. Requires economy.admin.\n" +
                "Example: `/setbalance @user 1000`";
            case "addbalance" ->
                "`/addbalance <user> <amount>`\n" +
                "Add currency to a user's balance. Requires economy.admin.\n" +
                "Example: `/addbalance @user 500`";
            case "subtractbalance" ->
                "`/subtractbalance <user> <amount>`\n" +
                "Remove currency from a user's balance. Requires economy.admin.\n" +
                "Example: `/subtractbalance @user 200`";
            case "currency" ->
                "`/currency name:<name> icon:<emoji>`\n" +
                "Customise the server currency name and icon.\n" +
                "Example: `/currency name:coins icon:🪙`";
            // ── Leveling ────────────────────────────────────────────────────────────
            case "rank" ->
                "`/rank [@user]`\n" +
                "View your rank card or another user's rank card.\n" +
                "Example: `/rank` or `/rank @user`";
            case "leaderboard", "lb" ->
                "`/leaderboard [page]`\n" +
                "Show the XP/level leaderboard for this server.\n" +
                "Example: `/leaderboard` or `/leaderboard 2`";
            case "xp" ->
                "`/xp <user> <add|remove|set> <amount>`\n" +
                "Manually adjust a user's XP. Requires leveling.admin.\n" +
                "Example: `/xp @user add 500`";
            case "level" ->
                "`/level <user> <set> <level>`\n" +
                "Manually set a user's level. Requires leveling.admin.\n" +
                "Example: `/level @user set 10`";
            // ── Utility ─────────────────────────────────────────────────────────────
            case "help" ->
                "`/help` — Show all commands by category\n" +
                "`/help command:<name>` — Get detailed help for a specific command\n" +
                "Example: `/help command:ban`";
            case "ping" ->
                "`/ping`\n" +
                "Check the bot's latency and uptime.";
            case "info" ->
                "`/info`\n" +
                "Show bot information, version, and statistics.";
            case "avatar" ->
                "`/avatar [@user]`\n" +
                "Show a user's avatar. Defaults to your own.\n" +
                "Example: `/avatar @user`";
            case "echo" ->
                "`/echo <message> [channel]`\n" +
                "Send a message as the bot. Requires permission.\n" +
                "Example: `/echo Hello World #general`";
            case "embed" ->
                "`/embed type:<simple|advanced> title:<title> description:<desc> [color:<hex>]`\n" +
                "Example: `/embed type:simple title:Welcome description:Hello! color:#ff0000`";
            case "embedgui" ->
                "`/embedgui`\n" +
                "Launch the interactive embed builder GUI.";
            case "pride" ->
                "`/pride flag:<flag> [user:@user] [style:border|overlay]`\n" +
                "Apply a pride flag overlay to your (or another user's) avatar.\n" +
                "Example: `/pride flag:trans` or `/pride flag:bi user:@user style:overlay`";
            case "flags" ->
                "`/flags list` — Show all available pride flags\n" +
                "`/flags display <flag>` — Preview a specific flag\n" +
                "Example: `/flags display rainbow`";
            case "pronouns" ->
                "`/pronouns set <pronouns>`\n" +
                "Set your preferred pronouns visible to the bot.\n" +
                "Example: `/pronouns set they/them`";
            case "queue" ->
                "`/queue [page]`\n" +
                "Show what's currently playing and the upcoming queue (10 per page).\n" +
                "Example: `/queue` or `/queue page:2`";
            case "preference" ->
                "`/preference ephemeral [enabled:true|false]` — Toggle private responses\n" +
                "`/preference view` — View all your preferences\n" +
                "Example: `/preference ephemeral enabled:false` (make responses public)";
            case "suspiciouslist" ->
                "`/suspiciouslist view [page]` — Browse the suspicious account list\n" +
                "`/suspiciouslist add <userId> [reason]` — Flag a user\n" +
                "`/suspiciouslist remove <userId>` — Remove a user from the list\n" +
                "`/suspiciouslist validate <userId>` — Mark as reviewed + legitimate\n" +
                "`/suspiciouslist ban <userId> [reason]` — Ban immediately\n" +
                "`/suspiciouslist review` — Interactive review workflow (owner only)";
            // ── Configuration ───────────────────────────────────────────────────────
            case "settings" ->
                "`/settings view [setting]` — View current settings\n" +
                "`/settings set <setting> <value>` — Change a setting\n" +
                "`/settings reset [setting]` — Reset to defaults\n" +
                "Example: `/settings set daily-reward 100`";
            case "antispam" ->
                "`/antispam enable|disable` — Toggle antispam\n" +
                "`/antispam status` — View current antispam settings\n" +
                "`/antispam set <setting> <value>` — Update a setting\n" +
                "Settings: `message-limit`, `time-window`, `mention-limit`, `dup-limit`,\n" +
                "`punishment` (warn/mute/timeout/kick/ban), `mute-duration`, `timeout-duration`, `ban-duration`";
            case "welcome" ->
                "`/welcome enable|disable` — Toggle welcome messages\n" +
                "`/welcome message <text>` — Set welcome text (use {user}, {server}, {membercount})\n" +
                "`/welcome channel <#channel>` — Set the welcome channel\n" +
                "`/welcome dm enable|disable` — Toggle welcome DMs\n" +
                "`/welcome auto-role <@role>` — Assign a role on join\n" +
                "`/welcome test` — Preview the welcome message";
            case "permissions" ->
                "`/permissions set <target> <node> <true|false>` — Set a permission\n" +
                "`/permissions remove <target> <node>` — Remove a permission override\n" +
                "`/permissions view [target]` — View permissions\n" +
                "Target: `user:@user`, `role:@role`, or `everyone`\n" +
                "Example: `/permissions set user:@mod moderation.ban true`";
            case "reactionrole" ->
                "`/reactionrole add <messageId> <emoji> <@role>` — Add a binding\n" +
                "`/reactionrole remove <messageId> <emoji>` — Remove a binding\n" +
                "`/reactionrole list` — List all bindings\n" +
                "Example: `/reactionrole add 123456 ✅ @Members`";
            case "log", "logging" ->
                "`/log enable|disable` — Toggle server logging\n" +
                "`/log channel <#channel>` — Set the log channel\n" +
                "`/log view` — Show current logging settings";
            case "autoconfig" ->
                "`/autoconfig`\n" +
                "Launch the interactive setup wizard to configure the bot for your server.";
            // ── Music ───────────────────────────────────────────────────────────────
            case "play" ->
                "`/play <query or url>`\n" +
                "Search YouTube/Spotify or play a direct URL.\n" +
                "Example: `/play never gonna give you up` or `/play https://open.spotify.com/...`";
            case "skip" ->
                "`/skip`\n" +
                "Skip the currently playing track.";
            case "stop" ->
                "`/stop`\n" +
                "Stop playback and clear the queue.";
            case "pause" ->
                "`/pause`\n" +
                "Toggle pause/resume for the current track.";
            case "volume" ->
                "`/volume <0-100>`\n" +
                "Set the playback volume.\n" +
                "Example: `/volume 75`";
            case "repeat" ->
                "`/repeat`\n" +
                "Toggle repeat mode for the current track.";
            case "shuffle" ->
                "`/shuffle`\n" +
                "Toggle shuffle mode for the queue.";
            case "join" ->
                "`/join`\n" +
                "Join your current voice channel.";
            case "leave" ->
                "`/leave`\n" +
                "Leave the voice channel and stop music.";
            case "playlist" ->
                "`/playlist list` — Show your saved playlists\n" +
                "`/playlist create <name>` — Create a new playlist\n" +
                "`/playlist delete <name>` — Delete a playlist\n" +
                "`/playlist play <name>` — Queue all tracks in a playlist\n" +
                "`/playlist add <name> <url>` — Add a track to a playlist\n" +
                "Example: `/playlist create lofi` or `/playlist play chill vibes`";
            // ── Owner ───────────────────────────────────────────────────────────────
            case "status" ->
                "`/status action:<set|clear|online> [type:<playing|watching|listening>] [text:<status>]`\n" +
                "Example: `/status action:set type:playing text:with fire`";
            case "announce" ->
                "`/announce title:<title> message:<message> [color:<hex>]`\n" +
                "Sends an announcement to all servers with an announcement channel set.\n" +
                "Example: `/announce title:Update message:New features! color:#FF69B4`";
            case "error" ->
                "`/error [category:<A-W>]`\n" +
                "View the error code reference documentation.\n" +
                "Example: `/error category:S` — Show all Settings errors";
            case "reports" ->
                "`/reports error [query] [sort] [page]`\n" +
                "Browse submitted error reports. Sort options: `most-reported`, `least-reported`, `newest`, `oldest`.\n" +
                "Example: `/reports error query:economy sort:most-reported page:1`";
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
