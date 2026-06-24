package com.serverbot.services;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.PermissionUtils;
import com.serverbot.utils.AutoLogUtils;
import com.serverbot.utils.TimeUtils;
import com.serverbot.utils.CooldownManager;
import com.serverbot.utils.DismissibleMessage;
import com.serverbot.commands.SlashCommand;
import com.serverbot.ServerBot;
import com.serverbot.models.ProxySettings;
import com.serverbot.models.ProxyMember;
import com.serverbot.services.PunishmentNotificationService;
import com.serverbot.services.PunishmentNotificationService.PunishmentType;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;
import java.util.Random;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Service for handling prefix commands by translating them to slash commands
 * This approach ensures consistency between prefix and slash command functionality
 */
public class PrefixCommandService {
    private static final String DEFAULT_PREFIX = "!";
    private static final Pattern USER_MENTION = Pattern.compile("<@!?(\\d+)>");
    
    private final CommandManager commandManager;
    
    // Command aliases mapping
    private static final Map<String, String> COMMAND_ALIASES = Map.ofEntries(
        Map.entry("h", "help"),
        Map.entry("p", "permissions"),
        Map.entry("perms", "permissions"),
        Map.entry("a", "automod"),
        Map.entry("amod", "automod"),
        Map.entry("w", "work"),
        Map.entry("bal", "balance"),
        Map.entry("points", "balance"),
        Map.entry("lb", "leaderboard"),
        Map.entry("t", "ticket"),
        Map.entry("new", "ticket"),
        Map.entry("close", "ticket"),
        Map.entry("ping", "ping"),
        Map.entry("info", "info"),
        Map.entry("botinfo", "info"),
        Map.entry("serverinfo", "serverinfo"),
        Map.entry("sinfo", "serverinfo"),
        Map.entry("server", "serverinfo"),
        Map.entry("stats", "serverstats"),
        Map.entry("r", "rank"),
        Map.entry("level", "rank"),
        Map.entry("xp", "xp"),
        Map.entry("pay", "pay"),
        Map.entry("baltop", "baltop"),
        Map.entry("daily", "daily"),
        Map.entry("work", "work"),
        Map.entry("gamble", "gamble"),
        Map.entry("slots", "slots"),
        Map.entry("flip", "flip"),
        Map.entry("dice", "dice"),
        Map.entry("ban", "ban"),
        Map.entry("kick", "kick"),
        Map.entry("warn", "warn"),
        Map.entry("mute", "mute"),
        Map.entry("timeout", "timeout"),
        Map.entry("purge", "purge"),
        Map.entry("echo", "echo"),
        Map.entry("write", "echo"),
        Map.entry("say", "echo"),
        Map.entry("writehost","echo" ),
        Map.entry("rules", "rules"),
        Map.entry("talkas", "talkas"),
        // Utility aliases
        Map.entry("pride", "pride"),
        Map.entry("flag", "pride"),
        Map.entry("gay", "pride"),
        Map.entry("lgbt", "pride"),
        Map.entry("avatar", "avatar"),
        Map.entry("pfp", "avatar"),
        // Proxy command aliases
        Map.entry("autoproxy", "autoproxy"),
        Map.entry("ap", "autoproxy"),
        Map.entry("proxy", "proxy"),
        Map.entry("px", "proxy"),
        // Moderation aliases
        Map.entry("softban", "softban"),
        Map.entry("hist", "hist"),
        Map.entry("history", "hist"),
        Map.entry("warns", "warns"),
        Map.entry("lockdown", "lockdown"),
        Map.entry("lock", "lockdown"),
        Map.entry("ldown", "lockdown"),
        // Economy aliases
        Map.entry("rob", "rob"),
        Map.entry("steal", "rob"),
        Map.entry("crime", "rob"),
        Map.entry("blackjack", "blackjack"),
        Map.entry("bj", "blackjack"),
        Map.entry("bank", "bank"),
        // Utility aliases
        Map.entry("embed", "embed"),
        Map.entry("dadjoke", "dadjoke"),
        Map.entry("joke", "joke"),
        // Owner-only command aliases
        Map.entry("statusmsg", "statusmsg"),
        Map.entry("status", "statusmsg"),
        Map.entry("restart", "restart"),
        Map.entry("rpc", "rpc"),
        Map.entry("presence", "rpc"),
        Map.entry("appearance", "appearance"),
        Map.entry("backup", "backup"),
        Map.entry("config", "config"),
        Map.entry("announce", "announce"),
        // Music command aliases
        Map.entry("play", "play"),
        Map.entry("skip", "skip"),
        Map.entry("s", "skip"),
        Map.entry("join", "join"),
        Map.entry("leave", "leave"),
        Map.entry("disconnect", "leave"),
        Map.entry("dc", "leave"),
        Map.entry("queue", "queue"),
        Map.entry("q", "queue"),
        Map.entry("pause", "pause"),
        Map.entry("resume", "pause"),
        Map.entry("stop", "stop"),
        Map.entry("volume", "volume"),
        Map.entry("vol", "volume"),
        Map.entry("repeat", "repeat"),
        Map.entry("loop", "repeat"),
        Map.entry("shuffle", "shuffle"),
        // Global chat aliases
        Map.entry("globalchat", "globalchat"),
        Map.entry("gc", "globalchat")
    );

    public PrefixCommandService(CommandManager commandManager) {
        this.commandManager = commandManager;
    }
    
    /**
     * Get the primary prefix for a guild, or default if not in a guild.
     */
    private String getGuildPrefix(MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            return ServerBot.getStorageManager().getPrefix(event.getGuild().getId());
        }
        return DEFAULT_PREFIX;
    }

    /**
     * Get all active prefixes for a guild (supports multiple per guild).
     */
    private java.util.List<String> getGuildPrefixes(MessageReceivedEvent event) {
        if (event.isFromGuild()) {
            return ServerBot.getStorageManager().getPrefixes(event.getGuild().getId());
        }
        java.util.List<String> defaults = new java.util.ArrayList<>();
        defaults.add(DEFAULT_PREFIX);
        return defaults;
    }
    
    /**
     * Process a prefix command by translating it to a slash command
     */
    public void handlePrefixCommand(MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw().trim();
        
        // Check if it's a proxy command (px; prefix)
        if (content.startsWith("px;")) {
            handleProxyCommand(event, content.substring(3).trim());
            return;
        }
        
        // Find which prefix (if any) this message starts with.
        // Check all guild prefixes and the hardcoded default for backwards compatibility.
        java.util.List<String> guildPrefixes = getGuildPrefixes(event);
        String matchedPrefix = null;
        for (String p : guildPrefixes) {
            if (content.startsWith(p)) {
                matchedPrefix = p;
                break;
            }
        }
        // Also accept the hardcoded default prefix as a fallback so existing users
        // aren't broken if a guild changes their prefix.
        if (matchedPrefix == null && content.startsWith(DEFAULT_PREFIX)) {
            matchedPrefix = DEFAULT_PREFIX;
        }
        if (matchedPrefix == null) {
            return;
        }
        final String prefix = matchedPrefix;
        
        // Parse the command and arguments — using quote-aware tokenizer so
        // e.g. !echo -m "hello world" preserves "hello world" as a single argument
        List<String> allTokens = parseQuotedTokens(content.substring(prefix.length()));
        if (allTokens.isEmpty() || allTokens.get(0).isEmpty()) {
            return;
        }
        
        String commandName = allTokens.get(0).toLowerCase();
        String[] args = allTokens.subList(1, allTokens.size()).toArray(new String[0]);
        
        // Resolve aliases
        commandName = COMMAND_ALIASES.getOrDefault(commandName, commandName);
        
        // Check if prefix commands are enabled for this guild
        if (event.isFromGuild()) {
            String guildId = event.getGuild().getId();
            
            // Check if prefix commands are globally disabled
            if (!ServerBot.getStorageManager().arePrefixCommandsEnabled(guildId)) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Prefix Commands Disabled",
                    "Prefix commands are disabled for this server. Please use slash commands instead."
                )).queue();
                return;
            }
        }
        
        // Get the corresponding slash command
        SlashCommand slashCommand = commandManager.getCommand(commandName);
        if (slashCommand == null) {
            // Only respond if the typed word resembles a real command (avoids flagging e.g. "!!!")
            if (isSimilarToKnownCommand(commandName)) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Command",
                    "The command `" + prefix + commandName + "` was not found. Use `" + prefix + "help` to see available commands."
                )).queue();
            }
            return;
        }
        
        try {
            // Check if economy/leveling is disabled for this guild
            if (event.isFromGuild()) {
                com.serverbot.commands.CommandCategory category = slashCommand.getCategory();
                if (category == com.serverbot.commands.CommandCategory.ECONOMY ||
                    category == com.serverbot.commands.CommandCategory.GAMBLING ||
                    category == com.serverbot.commands.CommandCategory.BANKING) {
                    String gId = event.getGuild().getId();
                    Map<String, Object> gs = com.serverbot.ServerBot.getStorageManager().getGuildSettings(gId);
                    boolean economyEnabled = Boolean.TRUE.equals(gs.get("enableEconomy"));
                    if (!economyEnabled) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Economy Disabled",
                            "The economy system is disabled in this server. An admin can enable it using /settings."
                        )).queue();
                        return;
                    }
                }
                if (category == com.serverbot.commands.CommandCategory.LEVELING) {
                    String gId = event.getGuild().getId();
                    Map<String, Object> gs = com.serverbot.ServerBot.getStorageManager().getGuildSettings(gId);
                    boolean levelingEnabled = Boolean.TRUE.equals(gs.get("enableLeveling"));
                    if (!levelingEnabled) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Leveling Disabled",
                            "The leveling system is disabled in this server. An admin can enable it using /settings."
                        )).queue();
                        return;
                    }
                }
            }
            // Handle the command based on its type
            handleSpecificCommand(event, commandName, args, slashCommand);
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Command Error",
                "An error occurred while executing the command: " + e.getMessage()
            )).queue();
        }
    }
    
    /**
     * Handle specific commands by calling their logic directly
     */
    private void handleSpecificCommand(MessageReceivedEvent event, String commandName, String[] args, SlashCommand slashCommand) {
        // Parse arguments for this command
        Map<String, String> options = parseArguments(commandName, args);
        
        switch (commandName.toLowerCase()) {
            case "work":
                handleWorkCommand(event);
                break;
            case "ping":
                handlePingCommand(event);
                break;
            case "balance":
                handleBalanceCommand(event, options);
                break;
            case "pay":
                handlePayCommand(event, options);
                break;
            case "baltop":
                handleBaltopCommand(event);
                break;
            case "help":
                handleHelpCommand(event, options);
                break;
            case "rank":
                handleRankCommand(event, options);
                break;
            case "lb":
            case "leaderboard":
                handleLeaderboardCommand(event);
                break;
            case "echo":
                handleEchoCommand(event, options);
                break;
            case "talkas":
                handleTalkAsCommand(event, options);
                break;
            case "permissions":
                handlePermissionsCommand(event, options);
                break;
            case "warn":
                handleWarnCommand(event, options);
                break;
            case "ban":
                handleBanCommand(event, options);
                break;
            case "kick":
                handleKickCommand(event, options);
                break;
            case "mute":
                handleMuteCommand(event, options);
                break;
            case "timeout":
                handleTimeoutCommand(event, options);
                break;
            case "info":
                handleInfoCommand(event, options);
                break;
            case "serverinfo":
                handleServerInfoCommand(event, options);
                break;
            case "xp":
                handleXpCommand(event, options);
                break;
            case "daily":
                handleDailyCommand(event, options);
                break;
            case "error":
                handleErrorCommand(event, options);
                break;
            // Unpunishment commands
            case "unban":
                handleUnbanCommand(event, options);
                break;
            case "unmute":
                handleUnmuteCommand(event, options);
                break;
            case "unwarn":
                handleUnwarnCommand(event, options);
                break;
            // Gambling/Games commands
            case "gamble":
                handleGambleCommand(event, options);
                break;
            case "slots":
                handleSlotsCommand(event, options);
                break;
            case "flip":
                handleFlipCommand(event, options);
                break;
            case "dice":
                handleDiceCommand(event, options);
                break;
            // Utility commands
            case "purge":
                handlePurgeCommand(event, options);
                break;
            case "automod":
                handleAutomodCommand(event, options);
                break;
            case "serverstats":
                handleServerstatsCommand(event);
                break;
            case "rules":
                handleRulesCommand(event, options);
                break;
            case "ticket":
                handleTicketCommand(event, options);
                break;
            // New moderation commands
            case "softban":
                handleSoftbanCommand(event, options);
                break;
            case "hist":
                handleHistCommand(event, options);
                break;
            case "warns":
                handleWarnsCommand(event, options);
                break;
            case "lockdown":
                handleLockdownCommand(event, options);
                break;
            // New economy commands
            case "rob":
                handleRobCommand(event, options);
                break;
            case "blackjack":
                handleBlackjackCommand(event, options);
                break;
            case "bank":
                handleBankCommand(event, options);
                break;
            // New utility commands
            case "embed":
                handleEmbedCommand(event, options);
                break;
            case "dadjoke":
                handleDadJokeCommand(event);
                break;
            case "joke":
                handleJokeCommand(event);
                break;
            // Owner-only commands
            case "statusmsg":
                handleStatusMsgCommand(event, options);
                break;
            case "restart":
                handleRestartCommand(event);
                break;
            case "rpc":
                handleRpcCommand(event, options);
                break;
            case "appearance":
                handleAppearanceCommand(event, options);
                break;
            case "backup":
                handleBackupCommand(event, options);
                break;
            case "config":
                handleConfigCommand(event, options);
                break;
            // Music commands
            case "play":
                handlePlayCommand(event, options, args);
                break;
            case "skip":
                handleSkipCommand(event, options);
                break;
            case "join":
                handleJoinCommand(event);
                break;
            case "leave":
                handleLeaveCommand(event);
                break;
            case "queue":
                handleQueueCommand(event);
                break;
            case "pause":
                handlePauseCommand(event);
                break;
            case "stop":
                handleStopCommand(event);
                break;
            case "volume":
                handleVolumeCommand(event, options);
                break;
            case "repeat":
                handleRepeatCommand(event);
                break;
            case "shuffle":
                handleShuffleCommand(event);
                break;
            case "pride":
                handlePrideCommand(event, options);
                break;
            case "avatar":
                handleAvatarCommand(event, options);
                break;
            case "proxy": {
                String gId = event.isFromGuild() ? event.getGuild().getId() : null;
                String uId = event.getAuthor().getId();
                String joinedArgs = String.join(" ", args);
                handleProxyMemberCommand(event, joinedArgs, gId, uId);
                break;
            }
            case "autoproxy": {
                String gId = event.isFromGuild() ? event.getGuild().getId() : null;
                String uId = event.getAuthor().getId();
                String joinedArgs = String.join(" ", args);
                handleAutoproxyCommand(event, joinedArgs, gId, uId);
                break;
            }
            case "announce":
                handleAnnounceCommand(event, options);
                break;
            case "globalchat":
                handleGlobalChatCommand(event, args);
                break;
            case "prefix":
                handlePrefixConfigCommand(event, options);
                break;
            case "settings":
                handleSettingsCommand(event, options);
                break;
            case "welcome":
                handleWelcomeCommand(event, options);
                break;
            case "logging":
                handleLoggingCommand(event, options);
                break;
            case "levels":
                handleLevelsToggleCommand(event, options);
                break;
            case "points":
                handlePointsToggleCommand(event, options);
                break;
            default:
                // Command is registered but has no prefix implementation
                String fullInput = String.join(" ", args);
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Slash Command Required",
                    "The `" + commandName + "` command is not available via prefix.\n" +
                    "Use the slash command instead: ` /" + commandName + " " + fullInput + "`"
                )).queue();
                break;
        }
    }

    /**
     * Returns true if the typed command name is similar (Levenshtein distance ≤ 2)
     * to any known command name or alias, so we only show "Unknown Command" when it
     * looks like a genuine typo rather than just any message starting with the prefix
     * (e.g. "!!!" would give commandName "!!" which has no close match and is ignored).
     */
    private boolean isSimilarToKnownCommand(String input) {
        if (input == null || input.isBlank()) return false;
        // Reject inputs that contain no letters or digits (e.g. "!!", "!?", "!!!")
        // Single-char aliases like "h", "p", "s" are within Levenshtein ≤2 of any 1-3 char
        // punctuation string, so we must guard against purely symbolic inputs first.
        if (!input.chars().anyMatch(Character::isLetterOrDigit)) return false;
        // Build set of all known command names + aliases
        Set<String> known = new java.util.HashSet<>(COMMAND_ALIASES.keySet());
        known.addAll(COMMAND_ALIASES.values());
        for (String cmd : known) {
            if (levenshtein(input, cmd) <= 2) return true;
        }
        return false;
    }

    /** Standard iterative Levenshtein distance between two strings. */
    private static int levenshtein(String a, String b) {
        int la = a.length(), lb = b.length();
        int[] prev = new int[lb + 1], curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[lb];
    }

    /**
     * Parse prefix command arguments into a map
     * Supports:
     *   - short flags: -m value
     *   - long flags: --message value
     *   - quoted values: -m "multi word value"
     *   - positional arguments (inferred by order for each command)
     */
    private Map<String, String> parseArguments(String commandName, String[] args) {
        Map<String, String> options = new HashMap<>();
        List<String> positionalArgs = new ArrayList<>();
        
        // args are already properly tokenized (quotes handled upstream in handlePrefixCommand),
        // so we iterate directly without re-joining and re-parsing.
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            
            if (arg.startsWith("--") && arg.length() > 2) {
                // Long flag: --flag value
                String flag = arg.substring(2);
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    String value = args[i + 1];
                    String optionName = mapFlagToOption(flag);
                    options.put(optionName, value);
                    i++;
                } else {
                    // Flag with no value — treat as boolean flag
                    String optionName = mapFlagToOption(flag);
                    options.put(optionName, "true");
                }
            } else if (arg.startsWith("-") && arg.length() > 1 && !arg.startsWith("--")) {
                // Short flag: -f value
                String flag = arg.substring(1);
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    String value = args[i + 1];
                    String optionName = mapFlagToOption(flag);
                    options.put(optionName, value);
                    i++;
                } else {
                    String optionName = mapFlagToOption(flag);
                    options.put(optionName, "true");
                }
            } else if (!arg.startsWith("-")) {
                // Positional argument
                positionalArgs.add(arg);
            }
        }
        
        // Handle positional arguments based on command
        handlePositionalArguments(commandName, options, positionalArgs);
        
        return options;
    }
    
    /**
     * Parse a command string into tokens, respecting double-quoted strings.
     * E.g. -m "hello world" -> ["-m", "hello world"]
     */
    private List<String> parseQuotedTokens(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            
            if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        
        return tokens;
    }
    
    /**
     * Map prefix command flags to slash command option names
     */
    private String mapFlagToOption(String flag) {
        return switch (flag.toLowerCase()) {
            case "u", "user" -> "user";
            case "r", "role" -> "role";
            case "c", "channel" -> "channel";
            case "m", "message", "msg" -> "message";
            case "a", "amount" -> "amount";
            case "time" -> "time";
            case "reason" -> "reason";
            case "p", "permission" -> "permission";
            case "v", "value" -> "value";
            case "action" -> "action";
            case "type" -> "type";
            case "category" -> "category";
            case "id" -> "id";
            // Permissions command specific flags
            case "t", "te", "target-entity" -> "target-entity";
            case "n", "node" -> "node";
            case "target" -> "target";
            default -> flag;
        };
    }
    
    /**
     * Handle positional arguments based on the command type
     */
    private void handlePositionalArguments(String commandName, Map<String, String> options, List<String> positionalArgs) {
        if (positionalArgs.isEmpty()) return;
        
        switch (commandName.toLowerCase()) {
            case "pay":
                if (positionalArgs.size() >= 2) {
                    options.put("user", positionalArgs.get(0));
                    options.put("amount", positionalArgs.get(1));
                }
                break;
            case "ban":
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        // Check if second arg looks like duration (contains d, h, m)
                        String secondArg = positionalArgs.get(1);
                        if (secondArg.matches(".*[dhm].*")) {
                            options.put("duration", secondArg);
                            if (positionalArgs.size() >= 3) {
                                options.put("reason", String.join(" ", positionalArgs.subList(2, positionalArgs.size())));
                            }
                        } else {
                            // Treat everything as reason
                            options.put("reason", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                        }
                    }
                }
                break;
            case "kick", "warn":
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("reason", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                    }
                }
                break;
            case "mute", "timeout":
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        // Check if second arg looks like duration (contains d, h, m, s, w)
                        String secondArg = positionalArgs.get(1);
                        if (secondArg.matches(".*[dhmsw].*")) {
                            options.put("duration", secondArg);
                            if (positionalArgs.size() >= 3) {
                                options.put("reason", String.join(" ", positionalArgs.subList(2, positionalArgs.size())));
                            }
                        } else {
                            // Treat everything as reason
                            options.put("reason", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                        }
                    }
                }
                break;
            case "echo", "talkas":
                if (positionalArgs.size() >= 1) {
                    options.put("message", String.join(" ", positionalArgs));
                }
                break;
            case "rank", "balance", "xp":
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                }
                break;
            case "rules":
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("id", positionalArgs.get(1));
                    }
                }
                break;
            case "gamble", "slots":
                if (positionalArgs.size() >= 1) {
                    options.put("amount", positionalArgs.get(0));
                }
                break;
            case "flip":
                if (positionalArgs.size() >= 1) {
                    options.put("amount", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("choice", positionalArgs.get(1));
                    }
                }
                break;
            case "dice":
                if (positionalArgs.size() >= 1) {
                    options.put("amount", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("guess", positionalArgs.get(1));
                    }
                }
                break;
            case "purge":
                if (positionalArgs.size() >= 1) {
                    options.put("amount", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("user", positionalArgs.get(1));
                    }
                }
                break;
            case "automod":
                // !automod <action> <feature> [threshold]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("feature", positionalArgs.get(1));
                    }
                    if (positionalArgs.size() >= 3) {
                        options.put("threshold", positionalArgs.get(2));
                    }
                }
                break;
            case "ticket":
                // !ticket <action> [reason/user]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("reason", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                    }
                }
                break;
            case "softban":
                // !softban @user [reason]
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("reason", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                    }
                }
                break;
            case "hist":
                // !hist @user
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                }
                break;
            case "warns":
                // !warns [@user]
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                }
                break;
            case "lockdown":
                // !lockdown [#channel]
                if (positionalArgs.size() >= 1) {
                    options.put("channel", positionalArgs.get(0));
                }
                break;
            case "rob":
                // !rob @user
                if (positionalArgs.size() >= 1) {
                    options.put("user", positionalArgs.get(0));
                }
                break;
            case "blackjack":
                // !blackjack <bet>
                if (positionalArgs.size() >= 1) {
                    options.put("bet", positionalArgs.get(0));
                }
                break;
            case "bank":
                // !bank <setting> [action] [user] [amount]
                if (positionalArgs.size() >= 1) {
                    options.put("setting", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("action", positionalArgs.get(1));
                    }
                    if (positionalArgs.size() >= 3) {
                        options.put("user", positionalArgs.get(2));
                    }
                    if (positionalArgs.size() >= 4) {
                        options.put("amount", positionalArgs.get(3));
                    }
                }
                break;
            case "embed":
                // !embed <title> | <description> [-color #hex]
                if (positionalArgs.size() >= 1) {
                    String fullText = String.join(" ", positionalArgs);
                    // Support pipe separator: !embed Title | Description
                    if (fullText.contains("|")) {
                        String[] splitParts = fullText.split("\\|", 2);
                        options.put("title", splitParts[0].trim());
                        if (splitParts.length > 1) {
                            options.put("description", splitParts[1].trim());
                        }
                    } else {
                        options.put("title", fullText);
                    }
                }
                break;
            // Owner-only command positional arguments
            case "statusmsg":
                // !statusmsg [message...] (all args are the status message)
                if (positionalArgs.size() >= 1) {
                    options.put("message", String.join(" ", positionalArgs));
                }
                break;
            case "rpc":
                // !rpc <action> [type] [text...]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        // Check if second arg is a known type
                        String secondArg = positionalArgs.get(1).toLowerCase();
                        if (secondArg.equals("playing") || secondArg.equals("watching") || 
                            secondArg.equals("listening") || secondArg.equals("streaming") || 
                            secondArg.equals("competing")) {
                            options.put("type", secondArg);
                            if (positionalArgs.size() >= 3) {
                                options.put("text", String.join(" ", positionalArgs.subList(2, positionalArgs.size())));
                            }
                        } else {
                            // No type specified, everything after action is text
                            options.put("text", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                        }
                    }
                }
                break;
            case "appearance":
                // !appearance <status>
                if (positionalArgs.size() >= 1) {
                    options.put("status", positionalArgs.get(0));
                }
                break;
            case "backup":
                // !backup <action> [timestamp]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("timestamp", positionalArgs.get(1));
                    }
                }
                break;
            case "config":
                // !config [action]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                }
                break;
            // Music command positional arguments
            case "play":
                // !play <query...> [index] — handled specially in handler via raw args
                if (positionalArgs.size() >= 1) {
                    options.put("query", String.join(" ", positionalArgs));
                }
                break;
            case "skip":
                // !skip [count]
                if (positionalArgs.size() >= 1) {
                    options.put("count", positionalArgs.get(0));
                }
                break;
            case "volume":
                // !volume [level]
                if (positionalArgs.size() >= 1) {
                    options.put("level", positionalArgs.get(0));
                }
                break;
            case "prefix":
                // !prefix <action> [prefix]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("prefix", positionalArgs.get(1));
                    }
                }
                break;
            case "flags":
                // !flags [action] [flag]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("flag", positionalArgs.get(1));
                    }
                }
                break;
            case "pronouns":
                // !pronouns <action>
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                }
                break;
            case "welcome":
                // !welcome <action> [value]
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("value", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                    }
                }
                break;
            case "logging":
                // !logging <type> [channel]
                if (positionalArgs.size() >= 1) {
                    options.put("type", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("channel", positionalArgs.get(1));
                    }
                }
                break;
            case "log":
                // !log <type> <message...>
                if (positionalArgs.size() >= 2) {
                    options.put("type", positionalArgs.get(0));
                    options.put("message", String.join(" ", positionalArgs.subList(1, positionalArgs.size())));
                }
                break;
            case "antispam":
                // !antispam <setting> [threshold]
                if (positionalArgs.size() >= 1) {
                    options.put("setting", positionalArgs.get(0));
                    if (positionalArgs.size() >= 2) {
                        options.put("threshold", positionalArgs.get(1));
                    }
                }
                break;
            case "levels":
            case "points":
                // !levels <enable/disable>
                if (positionalArgs.size() >= 1) {
                    options.put("action", positionalArgs.get(0));
                }
                break;
            case "support":
                // !support
                break;
            case "chess":
                // !chess [opponent]
                if (positionalArgs.size() >= 1) {
                    options.put("opponent", positionalArgs.get(0));
                }
                break;
            case "poker":
                // !poker <bet>
                if (positionalArgs.size() >= 1) {
                    options.put("bet", positionalArgs.get(0));
                }
                break;
            case "announce":
                // !announce [message...] — all remaining positional args = the message
                // Also works with -m "message" or --message "message"
                if (positionalArgs.size() >= 1) {
                    options.put("message", String.join(" ", positionalArgs));
                }
                break;
            default:
                // For other commands, put the first positional arg as "target"
                if (positionalArgs.size() >= 1) {
                    options.put("target", positionalArgs.get(0));
                }
                break;
        }
    }
    
    // Command implementations that replicate slash command logic
    
    // Short helper: forward to slash command by sending instruction to user
    private void slashOnly(MessageReceivedEvent event, String commandName, String args) {
        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
            "Slash Command Required",
            "The `" + commandName + "` command requires slash commands.\n" +
            "Use ` /" + commandName + " " + args + "` instead."
        )).queue();
    }

    private void handlePrefixConfigCommand(MessageReceivedEvent event, Map<String, String> options) {
        slashOnly(event, "prefix", options.getOrDefault("action", "") + " " + options.getOrDefault("prefix", ""));
    }

    private void handleSettingsCommand(MessageReceivedEvent event, Map<String, String> options) {
        slashOnly(event, "settings", options.getOrDefault("action", ""));
    }

    private void handleWelcomeCommand(MessageReceivedEvent event, Map<String, String> options) {
        slashOnly(event, "welcome", options.getOrDefault("action", "") + " " + options.getOrDefault("value", ""));
    }

    private void handleLoggingCommand(MessageReceivedEvent event, Map<String, String> options) {
        slashOnly(event, "logging", options.getOrDefault("type", "") + " " + options.getOrDefault("channel", ""));
    }

    private void handleLevelsToggleCommand(MessageReceivedEvent event, Map<String, String> options) {
        slashOnly(event, "levels", options.getOrDefault("action", ""));
    }

    private void handlePointsToggleCommand(MessageReceivedEvent event, Map<String, String> options) {
        slashOnly(event, "points", options.getOrDefault("action", ""));
    }

    private void handleWorkCommand(MessageReceivedEvent event) {
        try {
            Guild guild = event.getGuild();
            User user = event.getAuthor();
            String guildId = guild.getId();
            String userId = user.getId();
            String userKey = guildId + ":" + userId;

            // Get guild settings to determine work rewards
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Respect the economy enabled/disabled toggle
            if (!ServerBot.getStorageManager().isEconomyEnabled(guildId)) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Economy Disabled",
                    "The economy system is disabled on this server."
                )).queue();
                return;
            }

            // Check cooldown via shared CooldownManager (same as slash command)
            Number workCooldownNum = (Number) guildSettings.get("workCooldown");
            // workCooldown is stored in seconds directly (via /settings duration input)
            int cooldownSeconds = workCooldownNum != null ? workCooldownNum.intValue() : 300;

            if (CooldownManager.isOnCooldown(userId, "work", cooldownSeconds)) {
                long remaining = CooldownManager.getRemainingCooldown(userId, "work", cooldownSeconds);
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Work Cooldown",
                    "You need to rest before working again!\nTime remaining: **" + com.serverbot.utils.TimeUtils.formatDuration(java.time.Duration.ofSeconds(remaining)) + "**"
                )).queue();
                return;
            }

            // Set cooldown via shared CooldownManager
            CooldownManager.setCooldown(userId, "work");
            
            // Get work reward amount
            Number workRewardNum = (Number) guildSettings.get("workReward");
            int reward = workRewardNum != null ? workRewardNum.intValue() : 50;
            
            // Add random variation
            int variation = reward / 4;
            reward = reward + new Random().nextInt(variation * 2 + 1) - variation;
            
            // Add money to user's balance
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
            ServerBot.getStorageManager().setBalance(guildId, userId, currentBalance + reward);
            
            // Send success message
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            String cooldownText = com.serverbot.utils.TimeUtils.formatDuration(java.time.Duration.ofSeconds(cooldownSeconds));
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                currencyIcon + " Work Complete!",
                String.format("You worked hard and earned **%d** %s!\n**New Balance:** %d %s\n\n*You can work again in %s.*",
                    reward, currencyName, currentBalance + reward, currencyName, cooldownText)
            )).queue();
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Work Error",
                "Failed to process work command: " + e.getMessage()
            )).queue();
        }
    }
    
    private void handlePingCommand(MessageReceivedEvent event) {
        long startTime = System.currentTimeMillis();
        
        event.getChannel().sendMessage("🏓 Pinging...").queue(message -> {
            long endTime = System.currentTimeMillis();
            long messageLatency = endTime - startTime;
            long apiLatency = event.getJDA().getGatewayPing();
            
            message.editMessageEmbeds(EmbedUtils.createInfoEmbed(
                "🏓 Pong!",
                String.format("**Message Latency:** %dms\n**API Latency:** %dms", 
                    messageLatency, apiLatency)
            )).queue();
        });
    }
    
    private void handleBalanceCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            Guild guild = event.getGuild();
            User targetUser = event.getAuthor();
            
            // Check if a user was specified
            String userArg = options.get("user");
            if (userArg != null) {
                Matcher matcher = USER_MENTION.matcher(userArg);
                if (matcher.matches()) {
                    String userId = matcher.group(1);
                    User user = event.getJDA().getUserById(userId);
                    if (user != null) {
                        targetUser = user;
                    }
                }
            }
            
            String guildId = guild.getId();
            String userId = targetUser.getId();
            
            long balance = ServerBot.getStorageManager().getBalance(guildId, userId);
            
            // Get guild settings for currency name and icon
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                currencyIcon + " Balance",
                String.format("**%s** has **%d** %s", targetUser.getName(), balance, currencyName)
            )).queue();
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Balance Error",
                "Failed to retrieve balance: " + e.getMessage()
            )).queue();
        }
    }
    
    private void handlePayCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            Guild guild = event.getGuild();
            User sender = event.getAuthor();
            
            String userArg = options.get("user");
            String amountArg = options.get("amount");
            
            if (userArg == null || amountArg == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Arguments",
                    "Please provide both user and amount: `!pay @user amount`"
                )).queue();
                return;
            }
            
            // Parse target user
            User targetUser = null;
            Matcher matcher = USER_MENTION.matcher(userArg);
            if (matcher.matches()) {
                String userId = matcher.group(1);
                targetUser = event.getJDA().getUserById(userId);
            }
            
            if (targetUser == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User",
                    "Please mention a valid user to pay."
                )).queue();
                return;
            }
            
            // Parse amount
            long amount;
            try {
                amount = Long.parseLong(amountArg);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount",
                    "Please provide a valid number for the amount."
                )).queue();
                return;
            }
            
            if (amount <= 0) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount",
                    "Amount must be greater than 0."
                )).queue();
                return;
            }
            
            String guildId = guild.getId();
            String senderId = sender.getId();
            String targetId = targetUser.getId();
            
            // Check sender's balance
            long senderBalance = ServerBot.getStorageManager().getBalance(guildId, senderId);
            if (senderBalance < amount) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You don't have enough. You have %d, but need %d.", 
                        senderBalance, amount)
                )).queue();
                return;
            }
            
            // Perform the transaction
            ServerBot.getStorageManager().setBalance(guildId, senderId, senderBalance - amount);
            long targetBalance = ServerBot.getStorageManager().getBalance(guildId, targetId);
            ServerBot.getStorageManager().setBalance(guildId, targetId, targetBalance + amount);
            
            // Get currency info
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);
            
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                currencyIcon + " Payment Successful",
                String.format("**%s** paid **%s** %d %s\n\n**Your new balance:** %d %s", 
                    sender.getName(), targetUser.getName(), amount, currencyName,
                    senderBalance - amount, currencyName)
            )).queue();
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Payment Error",
                "Failed to process payment: " + e.getMessage()
            )).queue();
        }
    }
    
    private void handleBaltopCommand(MessageReceivedEvent event) {
        try {
            Guild guild = event.getGuild();
            String guildId = guild.getId();
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);

            List<Map.Entry<String, Long>> topBalances = ServerBot.getStorageManager().getTopBalances(guildId, 10);

            if (topBalances.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "💰 Balance Leaderboard",
                    "No economy data yet! Use `!work` or `!daily` to start earning."
                )).queue();
                return;
            }

            StringBuilder leaderboard = new StringBuilder();
            for (int i = 0; i < topBalances.size(); i++) {
                Map.Entry<String, Long> entry = topBalances.get(i);
                String userId = entry.getKey();
                long balance = entry.getValue();

                // Try to resolve user name
                String displayName;
                try {
                    User user = event.getJDA().getUserById(userId);
                    displayName = user != null ? user.getName() : "Unknown User";
                } catch (Exception e) {
                    displayName = "Unknown User";
                }

                if (i < 3) {
                    String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                    leaderboard.append(medal).append(" ").append(displayName)
                            .append("\n└ ").append(currencyIcon).append(" **").append(String.format("%,d", balance))
                            .append("** ").append(currencyName).append("\n\n");
                } else {
                    leaderboard.append("**").append(i + 1).append(".** ").append(displayName)
                            .append("\n└ ").append(currencyIcon).append(" **").append(String.format("%,d", balance))
                            .append("** ").append(currencyName).append("\n\n");
                }
            }

            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                currencyIcon + " Points Leaderboard", leaderboard.toString()
            )).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Leaderboard Error",
                "Failed to retrieve balance leaderboard: " + e.getMessage()
            )).queue();
        }
    }
    
    // Simple implementations for other commands
    private void handleHelpCommand(MessageReceivedEvent event, Map<String, String> options) {
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("📚 Prefix Command Help")
                .setDescription("All prefix commands use the same logic as their slash command equivalents.\n" +
                    "Use `!command` or your server's configured prefix.");

        embed.addField("💰 Economy",
            "`!work` — Earn money\n" +
            "`!daily` — Claim daily reward\n" +
            "`!balance [user]` — Check balance\n" +
            "`!pay @user amount` — Send money\n" +
            "`!baltop` — Balance leaderboard\n" +
            "`!gamble <amount>` — Gamble coins\n" +
            "`!slots <amount>` — Play slots\n" +
            "`!flip <amount> [heads/tails]` — Coin flip\n" +
            "`!dice <amount> [guess]` — Dice roll\n" +
            "`!rob @user` — Attempt to rob a user\n" +
            "`!blackjack <bet>` — Play blackjack\n" +
            "`!bank [setting]` — Bank management", false);

        embed.addField("📊 Leveling",
            "`!rank [user]` — View rank & XP\n" +
            "`!leaderboard` — XP leaderboard\n" +
            "`!xp [user]` — View XP info", false);

        embed.addField("🔨 Moderation",
            "`!warn @user [reason]` — Warn a user\n" +
            "`!mute @user [duration] [reason]` — Mute a user\n" +
            "`!timeout @user <duration> [reason]` — Timeout a user\n" +
            "`!kick @user [reason]` — Kick a user\n" +
            "`!ban @user [duration] [reason]` — Ban a user\n" +
            "`!softban @user [reason]` — Softban (ban+unban to delete msgs)\n" +
            "`!purge <amount> [user]` — Delete messages\n" +
            "`!lockdown [#channel]` — Lock/unlock a channel\n" +
            "`!warns [@user]` — View warnings\n" +
            "`!hist @user` — View moderation history\n" +
            "`!unban @user` — Unban a user\n" +
            "`!unmute @user` — Unmute a user\n" +
            "`!unwarn @user <id>` — Remove a warning", false);

        embed.addField("🛠️ Utility",
            "`!ping` — Bot latency\n" +
            "`!info` — Bot info\n" +
            "`!serverinfo` — Server info\n" +
            "`!echo <message>` — Echo a message\n" +
            "`!embed Title | Description` — Create a custom embed\n" +
            "`!dadjoke` — Random dad joke\n" +
            "`!joke` — Random joke\n" +
            "`!help` — This help message", false);

        embed.addField("🎵 Music",
            "`!play <url/query> [index]` — Play a track or playlist\n" +
            "`!skip [count]` — Skip the current track\n" +
            "`!queue` — View the music queue\n" +
            "`!pause` — Pause/resume playback\n" +
            "`!stop` — Stop playing & clear queue\n" +
            "`!volume [0-150]` — View/set volume\n" +
            "`!repeat` — Toggle repeat mode\n" +
            "`!shuffle` — Toggle shuffle mode\n" +
            "`!join` — Join your voice channel\n" +
            "`!leave` — Leave voice channel", false);

        // Only show owner commands to the bot owner
        if (PermissionUtils.isBotOwner(event.getAuthor())) {
            embed.addField("👑 Bot Owner",
                "`!statusmsg [message]` — Set/clear bot status\n" +
                "`!rpc <set|remove> [type] [text]` — Set bot presence\n" +
                "`!appearance <status>` — Set bot online status\n" +
                "`!restart` — Restart the bot\n" +
                "`!backup <create|list|info|restore>` — Manage backups\n" +
                "`!config [show|reload]` — View/reload server config", false);
        }

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void handleRankCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            Guild guild = event.getGuild();
            String guildId = guild.getId();
            User targetUser = event.getAuthor();

            // Check if a user was specified
            String userArg = options.get("user");
            if (userArg != null) {
                Matcher matcher = USER_MENTION.matcher(userArg);
                if (matcher.matches()) {
                    String userId = matcher.group(1);
                    User user = event.getJDA().getUserById(userId);
                    if (user != null) {
                        targetUser = user;
                    }
                }
            }

            String userId = targetUser.getId();
            int level = ServerBot.getStorageManager().getLevel(guildId, userId);
            long experience = ServerBot.getStorageManager().getExperience(guildId, userId);

            // Calculate XP for current and next level (same as RankCommand)
            long currentLevelExp = ServerBot.getStorageManager().calculateXpForLevel(level);
            long nextLevelExp = ServerBot.getStorageManager().calculateXpForLevel(level + 1);
            long expInCurrentLevel = experience - currentLevelExp;
            long expRequiredThisLevel = nextLevelExp - currentLevelExp;
            double progressPercent = expRequiredThisLevel > 0
                    ? (double) expInCurrentLevel / expRequiredThisLevel * 100
                    : 100.0;

            // Build progress bar (same style as RankCommand)
            int filledBars = (int) (progressPercent / 10);
            int emptyBars = 10 - filledBars;
            String progressBar = "▰".repeat(Math.max(0, filledBars)) + "▱".repeat(Math.max(0, emptyBars));

            // Calculate rank position
            List<Map.Entry<String, Integer>> topLevels = ServerBot.getStorageManager().getTopLevels(guildId, 1000);
            int rank = 1;
            for (Map.Entry<String, Integer> entry : topLevels) {
                if (entry.getKey().equals(userId)) break;
                rank++;
            }
            if (topLevels.stream().noneMatch(e -> e.getKey().equals(userId))) {
                rank = topLevels.size() + 1;
            }

            String description = "**Level:** " + level + "\n" +
                    "**Total XP:** " + String.format("%,d", experience) + "\n" +
                    "**Rank:** #" + rank + "\n" +
                    "**Progress:** " + Math.max(0, expInCurrentLevel) + "/" + expRequiredThisLevel + " XP\n" +
                    "**XP to Next Level:** " + (nextLevelExp - experience) + "\n" +
                    progressBar + " " + String.format("%.1f", progressPercent) + "%";

            event.getChannel().sendMessageEmbeds(
                EmbedUtils.createDefaultEmbed(targetUser.getName() + "'s Rank", description)
            ).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Rank Error",
                "Failed to retrieve rank information: " + e.getMessage()
            )).queue();
        }
    }
    
    private void handleLeaderboardCommand(MessageReceivedEvent event) {
        try {
            Guild guild = event.getGuild();
            String guildId = guild.getId();

            List<Map.Entry<String, Integer>> topLevels = ServerBot.getStorageManager().getTopLevels(guildId, 10);

            if (topLevels.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "🏆 XP Leaderboard",
                    "No leveling data yet! Start chatting to earn XP."
                )).queue();
                return;
            }

            StringBuilder leaderboard = new StringBuilder();
            for (int i = 0; i < topLevels.size(); i++) {
                Map.Entry<String, Integer> entry = topLevels.get(i);
                String userId = entry.getKey();
                int level = entry.getValue();
                long xp = ServerBot.getStorageManager().getExperience(guildId, userId);

                // Try to resolve user name
                String displayName;
                try {
                    User user = event.getJDA().getUserById(userId);
                    displayName = user != null ? user.getName() : "Unknown User";
                } catch (Exception e) {
                    displayName = "Unknown User";
                }

                if (i < 3) {
                    String medal = i == 0 ? "🥇" : i == 1 ? "🥈" : "🥉";
                    leaderboard.append(medal).append(" ").append(displayName)
                            .append("\n└ Level **").append(level).append("** (")
                            .append(String.format("%,d", xp)).append(" XP)\n\n");
                } else {
                    leaderboard.append("**").append(i + 1).append(".** ").append(displayName)
                            .append("\n└ Level **").append(level).append("** (")
                            .append(String.format("%,d", xp)).append(" XP)\n\n");
                }
            }

            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "📊 XP Leaderboard", leaderboard.toString()
            )).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Leaderboard Error",
                "Failed to retrieve XP leaderboard: " + e.getMessage()
            )).queue();
        }
    }
    
    private void handleEchoCommand(MessageReceivedEvent event, Map<String, String> options) {
        String message = options.get("message");
        if (message == null || message.trim().isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Message",
                "Please provide a message to echo: `!echo your message here`"
            )).queue();
            return;
        }
        
        // Check message length (Discord's limit is 2000 characters)
        if (message.length() > 2000) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Message Too Long", 
                CustomEmojis.ERROR + " Message too long! The maximum length is 2000 characters. Your message is " + message.length() + " characters."
            )).queue();
            return;
        }
        
        event.getChannel().sendMessage(message).queue();
    }

    private void handleAnnounceCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!PermissionUtils.isBotOwner(event.getAuthor())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        String message = options.get("message");
        if (message == null || message.trim().isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Message",
                "Provide a message: `!announce your message here`"
            )).queue();
            return;
        }
        if (message.length() > 2000) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Message Too Long",
                "Max 2000 characters. Your message is " + message.length() + " characters."
            )).queue();
            return;
        }

        int sentCount = 0;
        for (Guild guild : event.getJDA().getGuilds()) {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());
            Object val = settings.get("announcementChannel");
            String channelId = val instanceof String ? (String) val : null;
            if (channelId == null) continue;

            TextChannel target = guild.getTextChannelById(channelId);
            if (target == null) continue;
            if (!target.canTalk()) continue;

            target.sendMessage(message).queue(null, err -> {});
            sentCount++;
        }

        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Announcement Sent",
            "Sent to **" + sentCount + "** server(s)."
        )).queue();
    }

    private void handleTalkAsCommand(MessageReceivedEvent event, Map<String, String> options) {
        String message = options.get("message");
        if (message == null || message.trim().isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Message",
                "Please provide a message to send: `!talkas your message here`"
            )).queue();
            return;
        }
        
        // Check message length (Discord's limit is 2000 characters)
        if (message.length() > 2000) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Message Too Long", 
                CustomEmojis.ERROR + " Message too long! The maximum length is 2000 characters. Your message is " + message.length() + " characters."
            )).queue();
            return;
        }
        
        // Delete the original command message
        event.getMessage().delete().queue();
        
        // Send the message as if the bot said it
        event.getChannel().sendMessage(message).queue();
    }

    /**
     * Handle avatar command — shows the user's own avatar or a mentioned user's.
     */
    private void handleAvatarCommand(MessageReceivedEvent event, Map<String, String> options) {
        User user = event.getAuthor();
        // Check if a target user was passed (via mention or ID)
        String targetStr = options.get("target");
        if (targetStr != null && !targetStr.isEmpty()) {
            String id = targetStr.replaceAll("<@!?(\\d+)>", "$1");
            if (id.matches("\\d+")) {
                try {
                    User mentioned = event.getJDA().getUserById(id);
                    if (mentioned == null) {
                        // Try retrieving from Discord if not cached
                        mentioned = event.getJDA().retrieveUserById(id).complete();
                    }
                    if (mentioned != null) user = mentioned;
                } catch (Exception ignored) { }
            }
        }
        String avatarUrl = user.getEffectiveAvatarUrl() + "?size=4096";
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(EmbedUtils.INFO_COLOR)
            .setAuthor(user.getName(), null, user.getEffectiveAvatarUrl())
            .setTitle("🖼️ " + user.getName() + "'s Avatar")
            .setImage(avatarUrl)
            .setFooter("User ID: " + user.getId());
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Handle pride command — redirects to slash command since this uses image processing.
     */
    private void handlePrideCommand(MessageReceivedEvent event, Map<String, String> options) {
        event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
            "Pride Command",
            "The pride command is only available as a slash command. Please use `/pride` instead.\n\n" +
            "Example: `/pride flag:transgender`"
        )).queue();
    }

    /**
     * Handle permissions command with prefix syntax
     */
    private void handlePermissionsCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.permissions")) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions", "You need the `admin.permissions` permission to use this command."
            )).queue();
            return;
        }

        // Check for special commands first
        String target = options.get("target");
        if ("list-nodes".equals(target)) {
            handleListNodesPrefix(event);
            return;
        } else if ("check".equals(target)) {
            handleCheckPermissionsPrefix(event, options);
            return;
        }

        // Check if no arguments provided - show help
        String targetEntity = options.get("target-entity");
        String action = options.get("action");
        
        if (targetEntity == null && action == null) {
            showPermissionsHelpPrefix(event);
            return;
        }

        if (targetEntity == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Target [100]", 
                "Please specify a target entity.\n" +
                "Usage: `!permissions -t @user -a view` or `!permissions help`\n" +
                "Error Code: **100** - Missing Target Parameter"
            )).queue();
            return;
        }

        action = action != null ? action : "view";

        // Try to parse target entity
        if (targetEntity.startsWith("<@") && targetEntity.endsWith(">")) {
            if (targetEntity.startsWith("<@&")) {
                // It's a role mention
                String roleId = targetEntity.replaceAll("[<@&>]", "");
                try {
                    Role targetRole = event.getGuild().getRoleById(roleId);
                    if (targetRole == null) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Role Not Found", "The specified role was not found."
                        )).queue();
                        return;
                    }
                    handleRolePermissionsPrefix(event, targetRole, action, options);
                } catch (NumberFormatException e) {
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Role", "Please mention a valid role."
                    )).queue();
                }
            } else {
                // It's a user mention
                String userId = targetEntity.replaceAll("[<@!>]", "");
                try {
                    Member targetMember = event.getGuild().getMemberById(userId);
                    if (targetMember == null) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "User Not Found [301]", 
                            "The specified user is not a member of this server.\n" +
                            "Error Code: **301** - Target Not Found"
                        )).queue();
                        return;
                    }
                    handleUserPermissionsPrefix(event, targetMember, action, options);
                } catch (NumberFormatException e) {
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid User", "Please mention a valid user."
                    )).queue();
                }
            }
        } else if ("@everyone".equals(targetEntity) || "everyone".equalsIgnoreCase(targetEntity) || "e".equalsIgnoreCase(targetEntity)) {
            // Handle @everyone role (including "everyone" and "e" as alternatives to avoid pinging)
            Role everyoneRole = event.getGuild().getPublicRole();
            handleRolePermissionsPrefix(event, everyoneRole, action, options);
        } else {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Target", 
                "Please specify a valid target:\n" +
                "• User mention: `@username` (e.g., `<@123456789>`)\n" +
                "• Role mention: `@rolename` (e.g., `<@&987654321>`)\n" +
                "• Everyone: `@everyone`, `everyone`, or `e`"
            )).queue();
        }
    }

    private void showPermissionsHelpPrefix(MessageReceivedEvent event) {
        // Delete the original command message
        event.getMessage().delete().queue();
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle(CustomEmojis.INFO + "Permissions Command Help (Prefix)")
                .setDescription("Permissions management via prefix commands\n\n**Original command:** `" + event.getMessage().getContentRaw() + "`")
                .addField("**Set Permissions**", 
                    "```!permissions -t @user -a set -n mod.ban -v true```\n" +
                    "```!permissions -t @role -a set -n economy.admin -v false```\n" +
                    "```!permissions -t everyone -a set -n levels.use -v true```", false)
                .addField("**View Permissions**", 
                    "```!permissions -t @user -a view```\n" +
                    "```!permissions -t @role -a view```\n" +
                    "```!permissions -a view``` - View your own permissions", false)
                .addField("**Remove Permissions**", 
                    "```!permissions -t @user -a remove -n mod.ban```\n" +
                    "```!permissions -t @role -a remove -n economy.admin```", false)
                .addField("**Utility Commands**", 
                    "```!permissions -target list-nodes``` - List all available permission nodes\n" +
                    "```!permissions -target check -t @user -n mod.ban``` - Check if user has permission", false)
                .addField("**Flag Reference**", 
                    "`-t` = target (user/role/everyone), `-a` = action, `-n` = node, `-v` = value", false)
                .addField("**Target Options**", 
                    "• `@user` - Discord mention (e.g., `<@123456789>`)\n• `@role` - Role mention (e.g., `<@&987654321>`)\n• `everyone` or `e` - @everyone (no ping)", false);

        Button dismissButton = Button.secondary("dismiss_help", "Dismiss");
        
        event.getChannel().sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(dismissButton))
                .queue();
    }

    private void handleListNodesPrefix(MessageReceivedEvent event) {
        // Delete the original command message
        event.getMessage().delete().queue();
        
        // This would call the actual list nodes logic from PermissionsCommand
        // For now, show a basic list
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("📋 Available Permission Nodes")
                .setDescription("**Original command:** `" + event.getMessage().getContentRaw() + "`")
                .addField("**Moderation**", "mod.ban, mod.kick, mod.mute, mod.warn, mod.timeout", false)
                .addField("**Economy**", "economy.use, economy.admin, economy.pay, economy.work", false)
                .addField("**Levels**", "levels.use, levels.admin", false)
                .addField("**Admin**", "admin.permissions, admin.config, admin.server", false)
                .addField("**Utility**", "utility.ping, utility.help, utility.echo", false)
                .setFooter("Use wildcards like 'mod.*' for all moderation permissions");

        Button dismissButton = Button.secondary("dismiss_help", "Dismiss");
        
        event.getChannel().sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(dismissButton))
                .queue();
    }

    private void handleCheckPermissionsPrefix(MessageReceivedEvent event, Map<String, String> options) {
        String targetEntity = options.get("target-entity");
        String node = options.get("node");
        
        if (targetEntity == null || node == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameters", 
                "Usage: `!permissions -target check -t @user -n permission.node`"
            )).queue();
            return;
        }

        // Parse target entity and check permission
        if (targetEntity.startsWith("<@") && targetEntity.endsWith(">")) {
            String userId = targetEntity.replaceAll("[<@!>]", "");
            try {
                Member targetMember = event.getGuild().getMemberById(userId);
                if (targetMember == null) {
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "User Not Found", "The specified user is not a member of this server."
                    )).queue();
                    return;
                }
                
                boolean hasPermission = PermissionManager.hasPermission(targetMember, node);
                EmbedBuilder embed = EmbedUtils.createEmbedBuilder(hasPermission ? EmbedUtils.SUCCESS_COLOR : EmbedUtils.ERROR_COLOR)
                        .setTitle(CustomEmojis.SEARCH + " Permission Check")
                        .addField("User", targetMember.getAsMention(), true)
                        .addField("Permission", "`" + node + "`", true)
                        .addField("Has Permission", hasPermission ? CustomEmojis.SUCCESS + " Yes" : CustomEmojis.ERROR + " No", true);
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User", "Please mention a valid user."
                )).queue();
            }
        } else {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Target", "Please mention a valid user for permission check."
            )).queue();
        }
    }

    private void handleUserPermissionsPrefix(MessageReceivedEvent event, Member targetMember, String action, Map<String, String> options) {
        switch (action) {
            case "view" -> viewUserPermissionsPrefix(event, targetMember);
            case "set" -> setUserPermissionPrefix(event, targetMember, options);
            case "remove" -> removeUserPermissionPrefix(event, targetMember, options);
            default -> event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Action", "Valid actions: view, set, remove"
            )).queue();
        }
    }

    private void handleRolePermissionsPrefix(MessageReceivedEvent event, Role targetRole, String action, Map<String, String> options) {
        switch (action) {
            case "view" -> viewRolePermissionsPrefix(event, targetRole);
            case "set" -> setRolePermissionPrefix(event, targetRole, options);
            case "remove" -> removeRolePermissionPrefix(event, targetRole, options);
            default -> event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Action", "Valid actions: view, set, remove"
            )).queue();
        }
    }

    private void viewUserPermissionsPrefix(MessageReceivedEvent event, Member targetMember) {
        Map<String, Boolean> userPermissions = PermissionManager.getUserPermissions(event.getGuild().getId(), targetMember.getId());
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("👤 User Permissions: " + targetMember.getEffectiveName())
                .setThumbnail(targetMember.getEffectiveAvatarUrl());
        
        if (userPermissions.isEmpty()) {
            embed.addField("Direct Permissions", "No direct permissions set", false);
        } else {
            StringBuilder directPerms = new StringBuilder();
            for (Map.Entry<String, Boolean> entry : userPermissions.entrySet()) {
                String status = entry.getValue() ? CustomEmojis.SUCCESS : CustomEmojis.ERROR;
                directPerms.append(status).append(" `").append(entry.getKey()).append("`\n");
            }
            embed.addField("Direct Permissions", directPerms.toString(), false);
        }
        
        // Show permission count
        int allowCount = (int) userPermissions.values().stream().filter(v -> v).count();
        int denyCount = userPermissions.size() - allowCount;
        if (userPermissions.size() > 0) {
            embed.addField("Summary", 
                String.format(CustomEmojis.SUCCESS + " %d allowed • " + CustomEmojis.ERROR + " %d denied", allowCount, denyCount), false);
        }
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void viewRolePermissionsPrefix(MessageReceivedEvent event, Role targetRole) {
        Map<String, Boolean> permissions = PermissionManager.getRolePermissions(event.getGuild().getId(), targetRole.getId());
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🎭 Role Permissions: " + targetRole.getName())
                .setColor(targetRole.getColor());
        
        if (permissions.isEmpty()) {
            embed.addField("Permissions", "No permissions set for this role", false);
        } else {
            StringBuilder rolePerms = new StringBuilder();
            for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
                String status = entry.getValue() ? CustomEmojis.SUCCESS : CustomEmojis.ERROR;
                rolePerms.append(status).append(" `").append(entry.getKey()).append("`\n");
            }
            embed.addField("Permissions", rolePerms.toString(), false);
        }
        
        embed.addField("Members", String.valueOf(targetRole.getGuild().getMembersWithRoles(targetRole).size()), true);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void setUserPermissionPrefix(MessageReceivedEvent event, Member targetMember, Map<String, String> options) {
        String node = options.get("node");
        String value = options.get("value");
        
        if (node == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Permission Node", 
                "Usage: `!permissions -t @user -a set -n permission.node -v true`"
            )).queue();
            return;
        }
        
        boolean granted = value == null || "true".equalsIgnoreCase(value) || "allow".equalsIgnoreCase(value);
        
        PermissionManager.setUserPermission(event.getGuild().getId(), targetMember.getId(), node, granted);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " Permission Updated")
                .addField("User", targetMember.getAsMention(), true)
                .addField("Permission", "`" + node + "`", true)
                .addField("Action", granted ? CustomEmojis.SUCCESS + " Granted" : CustomEmojis.ERROR + " Denied", true);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void setRolePermissionPrefix(MessageReceivedEvent event, Role targetRole, Map<String, String> options) {
        String node = options.get("node");
        String value = options.get("value");
        
        if (node == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Permission Node", 
                "Usage: `!permissions -t @role -a set -n permission.node -v true`"
            )).queue();
            return;
        }
        
        boolean granted = value == null || "true".equalsIgnoreCase(value) || "allow".equalsIgnoreCase(value);
        
        PermissionManager.setRolePermission(event.getGuild().getId(), targetRole.getId(), node, granted);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + "Permission Updated")
                .addField("Role", targetRole.getAsMention(), true)
                .addField("Permission", "`" + node + "`", true)
                .addField("Action", granted ? CustomEmojis.SUCCESS + "Granted" : CustomEmojis.ERROR + "Denied", true);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void removeUserPermissionPrefix(MessageReceivedEvent event, Member targetMember, Map<String, String> options) {
        String node = options.get("node");
        
        if (node == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Permission Node", 
                "Usage: `!permissions -t @user -a remove -n permission.node`"
            )).queue();
            return;
        }
        
        PermissionManager.removeUserPermission(event.getGuild().getId(), targetMember.getId(), node);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.TRASH + " Permission Removed")
                .addField("User", targetMember.getAsMention(), true)
                .addField("Permission", "`" + node + "`", true)
                .addField("Action", "Removed", true);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void removeRolePermissionPrefix(MessageReceivedEvent event, Role targetRole, Map<String, String> options) {
        String node = options.get("node");
        
        if (node == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Permission Node", 
                "Usage: `!permissions -t @role -a remove -n permission.node`"
            )).queue();
            return;
        }
        
        PermissionManager.removeRolePermission(event.getGuild().getId(), targetRole.getId(), node);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                .setTitle(CustomEmojis.TRASH + " Permission Removed")
                .addField("Role", targetRole.getAsMention(), true)
                .addField("Permission", "`" + node + "`", true)
                .addField("Action", "Removed", true);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Handle warn command with prefix syntax
     */
    private void handleWarnCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.warn")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.warn` permission to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        // Parse user mention from arguments
        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to warn: `!warn @user [reason]`",
                event.getAuthor().getId()
            );
            return;
        }

        // Parse user mention
        User targetUser = null;
        if (userArg.startsWith("<@") && userArg.endsWith(">")) {
            String userId = userArg.replaceAll("[<@!>]", "");
            try {
                targetUser = event.getJDA().getUserById(userId);
            } catch (NumberFormatException e) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Invalid User", "Please mention a valid user.",
                    event.getAuthor().getId()
                );
                return;
            }
        }

        if (targetUser == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "User Not Found", "Could not find the specified user.",
                event.getAuthor().getId()
            );
            return;
        }

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "User Not Found", "This user is not in the server!",
                event.getAuthor().getId()
            );
            return;
        }

        if (targetUser.isBot()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Target", "You cannot warn bots!",
                event.getAuthor().getId()
            );
            return;
        }

        // Check if the moderator can interact with the target
        if (!canInteractWith(moderator, targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Warn", "You cannot warn this user due to role hierarchy!",
                event.getAuthor().getId()
            );
            return;
        }

        String reason = options.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            reason = "No reason provided";
        }

        // Add the warning
        String guildId = event.getGuild().getId();
        String userId = targetUser.getId();
        String moderatorId = event.getAuthor().getId();

        ServerBot.getStorageManager().addWarning(guildId, userId, reason, moderatorId);
        int warningCount = ServerBot.getStorageManager().getWarningCount(guildId, userId);

        String description = String.format(
            "**User:** %s (%s)\n" +
            "**Reason:** %s\n" +
            "**Warning Count:** %d\n" +
            "**Moderator:** %s",
            targetUser.getName(), targetUser.getId(),
            reason, warningCount, event.getAuthor().getName()
        );

        DismissibleMessage.sendSuccess(event.getChannel(), "User Warned", description, event.getAuthor().getId());

        // Send DM notification if configured
        PunishmentNotificationService.getInstance().sendPunishmentNotification(
            event.getGuild().getId(),
            targetUser.getId(),
            PunishmentType.WARN,
            reason,
            null, // No duration for warnings
            event.getMember().getEffectiveName()
        );

        // Log to AutoLog channel
        AutoLogUtils.logWarn(event.getGuild(), targetUser, event.getAuthor(), reason);
    }

    /**
     * Handle ban command with prefix syntax
     */
    private void handleBanCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.ban")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need ban permissions to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        // Parse user mention
        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to ban: `!ban @user [duration] [reason]`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        Member targetMember = event.getGuild().getMember(targetUser);

        // Check permissions if member exists
        if (targetMember != null) {
            if (!canInteractWith(moderator, targetMember)) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Cannot Ban User", "You cannot ban this user due to role hierarchy.",
                    event.getAuthor().getId()
                );
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Cannot Ban User", "I cannot ban this user due to role hierarchy.",
                    event.getAuthor().getId()
                );
                return;
            }
        }

        String durationStr = options.get("duration");
        final String finalReason;
        String tempReason = options.get("reason");
        if (tempReason == null || tempReason.trim().isEmpty()) {
            finalReason = "No reason provided";
        } else {
            finalReason = tempReason;
        }

        final Duration banDuration;
        if (durationStr != null && !durationStr.trim().isEmpty()) {
            banDuration = TimeUtils.parseDuration(durationStr);
            if (banDuration == null) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Invalid Duration", "Please provide a valid duration (e.g., 1d, 2h, 30m)",
                    event.getAuthor().getId()
                );
                return;
            }
        } else {
            banDuration = null;
        }

        // Execute ban
        event.getGuild().ban(targetUser, 7, TimeUnit.DAYS).reason(finalReason)
                .queue(success -> {
                    String durationText = banDuration != null ? TimeUtils.formatDuration(banDuration) : "Permanent";
                    DismissibleMessage.send(event.getChannel(),
                        EmbedUtils.createModerationEmbed(
                            "User Banned", targetUser, moderator.getUser(), finalReason + "\n**Duration:** " + durationText
                        ),
                        moderator.getId()
                    );

                    // Send DM notification if configured
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                        event.getGuild().getId(),
                        targetUser.getId(),
                        PunishmentType.BAN,
                        finalReason,
                        banDuration,
                        moderator.getEffectiveName()
                    );
                });
    }

    /**
     * Handle kick command with prefix syntax
     */
    private void handleKickCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.kick")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need kick permissions to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to kick: `!kick @user [reason]`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "User Not Found", "This user is not in the server!",
                event.getAuthor().getId()
            );
            return;
        }

        if (!canInteractWith(moderator, targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Kick User", "You cannot kick this user due to role hierarchy.",
                event.getAuthor().getId()
            );
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Kick User", "I cannot kick this user due to role hierarchy.",
                event.getAuthor().getId()
            );
            return;
        }

        final String finalReason;
        String tempReason = options.get("reason");
        if (tempReason == null || tempReason.trim().isEmpty()) {
            finalReason = "No reason provided";
        } else {
            finalReason = tempReason;
        }

        // Execute kick
        event.getGuild().kick(targetMember).reason(finalReason)
                .queue(success -> {
                    DismissibleMessage.send(event.getChannel(),
                        EmbedUtils.createModerationEmbed(
                            "User Kicked", targetUser, moderator.getUser(), finalReason
                        ),
                        moderator.getId()
                    );

                    // Send DM notification if configured
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                        event.getGuild().getId(),
                        targetUser.getId(),
                        PunishmentType.KICK,
                        finalReason,
                        null,
                        moderator.getEffectiveName()
                    );
                });
    }

    /**
     * Handle mute command with prefix syntax
     */
    private void handleMuteCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.mute")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.mute` permission to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to mute: `!mute @user [duration] [reason]`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "User Not Found", "This user is not in the server.",
                event.getAuthor().getId()
            );
            return;
        }

        if (!canInteractWith(moderator, targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Mute User", "You cannot mute this user due to role hierarchy.",
                event.getAuthor().getId()
            );
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Mute User", "I cannot mute this user due to role hierarchy.",
                event.getAuthor().getId()
            );
            return;
        }

        // Parse duration (default 1h)
        String durationStr = options.get("duration");
        if (durationStr == null || durationStr.trim().isEmpty()) {
            durationStr = "1h";
        }
        final Duration muteDuration = TimeUtils.parseDuration(durationStr);
        if (muteDuration == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Duration",
                "Please provide a valid duration format.\n" +
                "**Valid formats:** `1d`, `2h`, `30m`, `1w`, `12h30m`\n" +
                "**Units:** `s`=seconds, `m`=minutes, `h`=hours, `d`=days, `w`=weeks",
                event.getAuthor().getId()
            );
            return;
        }

        String reason = options.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            reason = "No reason provided";
        }
        final String finalReason = reason;

        // Find or create mute role
        Role muteRole = event.getGuild().getRolesByName("Muted", true).stream()
                .findFirst().orElse(null);
        if (muteRole == null) {
            try {
                muteRole = event.getGuild().createRole()
                        .setName("Muted")
                        .setColor(0x808080)
                        .setMentionable(false)
                        .setHoisted(false)
                        .reason("Automatically created by ServerBot for muting users")
                        .complete();

                Role finalMuteRole = muteRole;
                event.getGuild().getTextChannels().forEach(channel -> {
                    channel.getManager().putRolePermissionOverride(finalMuteRole.getIdLong(), null,
                        java.util.EnumSet.of(
                            net.dv8tion.jda.api.Permission.MESSAGE_SEND,
                            net.dv8tion.jda.api.Permission.MESSAGE_ADD_REACTION,
                            net.dv8tion.jda.api.Permission.MESSAGE_SEND_IN_THREADS,
                            net.dv8tion.jda.api.Permission.CREATE_PUBLIC_THREADS,
                            net.dv8tion.jda.api.Permission.CREATE_PRIVATE_THREADS
                        )).queue(null, throwable -> {});
                });
                event.getGuild().getVoiceChannels().forEach(channel -> {
                    channel.getManager().putRolePermissionOverride(finalMuteRole.getIdLong(), null,
                        java.util.EnumSet.of(net.dv8tion.jda.api.Permission.VOICE_SPEAK)
                    ).queue(null, throwable -> {});
                });
            } catch (Exception e) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Mute Role Missing",
                    "Could not find or create a mute role. Please create a role named 'Muted' and configure its permissions.",
                    event.getAuthor().getId()
                );
                return;
            }
        }

        // Apply mute
        event.getGuild().addRoleToMember(targetMember, muteRole).reason(finalReason)
                .queue(success -> {
                    String durationText = TimeUtils.formatDuration(muteDuration);
                    DismissibleMessage.send(event.getChannel(),
                        EmbedUtils.createModerationEmbed(
                            "User Muted", targetUser, moderator.getUser(),
                            finalReason + "\n**Duration:** " + durationText
                        ),
                        moderator.getId()
                    );

                    // Schedule unmute
                    try {
                        String uniqueKey = event.getGuild().getId() + ":" + targetUser.getId() + ":MUTE:" + System.currentTimeMillis();
                        Map<String, Object> tempPunishment = new HashMap<>();
                        tempPunishment.put("guildId", event.getGuild().getId());
                        tempPunishment.put("userId", targetUser.getId());
                        tempPunishment.put("punishmentType", "MUTE");
                        tempPunishment.put("expiresAt", System.currentTimeMillis() + muteDuration.toMillis());
                        tempPunishment.put("moderatorId", moderator.getId());
                        tempPunishment.put("reason", finalReason);
                        tempPunishment.put("createdAt", System.currentTimeMillis());
                        ServerBot.getStorageManager().storeTempPunishment(uniqueKey, tempPunishment);
                    } catch (Exception e) {
                        System.err.println("Failed to schedule unmute: " + e.getMessage());
                    }

                    // Log mute
                    try {
                        Map<String, Object> logEntry = new HashMap<>();
                        logEntry.put("type", "MUTE");
                        logEntry.put("userId", targetUser.getId());
                        logEntry.put("moderatorId", moderator.getId());
                        logEntry.put("reason", finalReason);
                        logEntry.put("duration", muteDuration.toString());
                        logEntry.put("timestamp", System.currentTimeMillis());
                        ServerBot.getStorageManager().addModerationLog(event.getGuild().getId(), logEntry);
                    } catch (Exception e) {
                        System.err.println("Failed to log mute: " + e.getMessage());
                    }

                    // Log to AutoLog channel
                    AutoLogUtils.logMute(event.getGuild(), targetUser, moderator.getUser(), finalReason, muteDuration);

                    // Send DM notification
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                        event.getGuild().getId(),
                        targetUser.getId(),
                        PunishmentType.MUTE,
                        finalReason,
                        muteDuration,
                        durationText
                    );
                }, error -> {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Mute Failed", "Failed to mute user: " + error.getMessage(),
                        event.getAuthor().getId()
                    );
                });
    }

    /**
     * Handle timeout command with prefix syntax
     */
    private void handleTimeoutCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.timeout")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.timeout` permission to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to timeout: `!timeout @user <duration> [reason]`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "User Not Found", "This user is not in the server.",
                event.getAuthor().getId()
            );
            return;
        }

        // Check bot permissions
        if (!event.getGuild().getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MODERATE_MEMBERS)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing Bot Permissions", "I need the 'Moderate Members' permission to timeout users.",
                event.getAuthor().getId()
            );
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Timeout", "I cannot timeout this user. They may have higher permissions than me.",
                event.getAuthor().getId()
            );
            return;
        }

        if (targetUser.getId().equals(moderator.getUser().getId())) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Target", "You cannot timeout yourself.",
                event.getAuthor().getId()
            );
            return;
        }

        if (!moderator.canInteract(targetMember)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Cannot Timeout", "You cannot timeout this user. They have higher or equal permissions.",
                event.getAuthor().getId()
            );
            return;
        }

        // Parse duration (required)
        String durationStr = options.get("duration");
        if (durationStr == null || durationStr.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing Duration", "Please provide a duration: `!timeout @user <duration> [reason]`\n" +
                "**Example:** `!timeout @user 1h Spamming`",
                event.getAuthor().getId()
            );
            return;
        }

        Duration timeoutDuration = TimeUtils.parseDuration(durationStr);
        if (timeoutDuration == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Duration",
                "Please provide a valid duration (e.g., 1h, 30m, 2d, 1h30m).\n" +
                "**Units:** `s`=seconds, `m`=minutes, `h`=hours, `d`=days",
                event.getAuthor().getId()
            );
            return;
        }

        // Discord timeout limit: max 28 days
        if (timeoutDuration.toDays() > 28) {
            DismissibleMessage.sendError(event.getChannel(),
                "Duration Too Long", "Timeout duration cannot exceed 28 days.",
                event.getAuthor().getId()
            );
            return;
        }

        String reason = options.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            reason = "No reason provided";
        }
        final String finalReason = reason;
        final String durationText = TimeUtils.formatDuration(timeoutDuration);

        // Apply timeout
        targetMember.timeoutFor(timeoutDuration).reason(finalReason).queue(
            success -> {
                DismissibleMessage.send(event.getChannel(),
                    EmbedUtils.createSuccessEmbed(
                        "User Timed Out",
                        "**User:** " + targetUser.getAsMention() + "\n" +
                        "**Duration:** " + durationText + "\n" +
                        "**Reason:** " + finalReason + "\n" +
                        "**Moderator:** " + moderator.getUser().getAsMention()
                    ),
                    moderator.getId()
                );

                // Log the timeout
                try {
                    ServerBot.getStorageManager().logModerationAction(
                        event.getGuild().getId(),
                        targetUser.getId(),
                        moderator.getUser().getId(),
                        "TIMEOUT",
                        finalReason,
                        durationText
                    );
                } catch (Exception e) {
                    System.err.println("Failed to log timeout action: " + e.getMessage());
                }

                // Log to AutoLog channel
                AutoLogUtils.logTimeout(event.getGuild(), targetUser, moderator.getUser(), finalReason, timeoutDuration);

                // Send DM notification
                PunishmentNotificationService.getInstance().sendPunishmentNotification(
                    event.getGuild().getId(),
                    targetUser.getId(),
                    PunishmentType.TIMEOUT,
                    finalReason,
                    timeoutDuration,
                    durationText
                );
            },
            error -> {
                DismissibleMessage.sendError(event.getChannel(),
                    "Timeout Failed", "Failed to timeout user: " + error.getMessage(),
                    event.getAuthor().getId()
                );
            }
        );
    }

    /**
     * Handle info command with prefix syntax
     */
    private void handleInfoCommand(MessageReceivedEvent event, Map<String, String> options) {
        net.dv8tion.jda.api.JDA jda = event.getJDA();
        
        // Get runtime information
        long uptimeMillis = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeSeconds = uptimeMillis / 1000;
        String uptime = formatUptime(uptimeSeconds);
        
        // Get memory usage
        Runtime runtime = Runtime.getRuntime();
        long memoryUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long memoryTotal = runtime.totalMemory() / (1024 * 1024);
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🤖 Bot Information")
                .setThumbnail(jda.getSelfUser().getEffectiveAvatarUrl());

        // Bot Stats
        embed.addField("📊 Bot Statistics",
                "**Servers:** " + jda.getGuilds().size() + "\n" +
                "**Users:** " + jda.getUsers().size() + "\n" +
                "**Commands:** " + commandManager.getAllCommands().size(),
                true);

        // Technical Info
        embed.addField(CustomEmojis.SETTING + " Technical Info",
                "**Uptime:** " + uptime + "\n" +
                "**Memory:** " + memoryUsed + "/" + memoryTotal + " MB\n" +
                "**Java:** " + System.getProperty("java.version"),
                true);

        // Additional Info
        embed.addField("🔗 Links",
                "**Ping:** " + jda.getGatewayPing() + "ms\n" +
                "**Shards:** " + (jda.getShardInfo() != null ? 
                    (jda.getShardInfo().getShardId() + 1) + "/" + jda.getShardInfo().getShardTotal() : "1/1"),
                true);

        embed.setFooter("Bot created with JDA");

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Format uptime into human readable string
     */
    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        sb.append(secs).append("s");

        return sb.toString();
    }

    /**
     * Handle serverinfo command with prefix syntax
     */
    private void handleServerInfoCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).queue();
            return;
        }

        Guild guild = event.getGuild();
        
        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🏠 Server Information")
                .setThumbnail(guild.getIconUrl());

        // Basic Server Info
        embed.addField("📊 Server Stats",
                "**Name:** " + guild.getName() + "\n" +
                "**ID:** " + guild.getId() + "\n" +
                "**Owner:** " + guild.getOwner().getAsMention() + "\n" +
                "**Created:** <t:" + guild.getTimeCreated().toEpochSecond() + ":R>",
                true);

        // Member Info
        long totalMembers = guild.getMemberCount();
        long bots = guild.getMembers().stream().mapToLong(m -> m.getUser().isBot() ? 1 : 0).sum();
        long humans = totalMembers - bots;

        embed.addField("👥 Members",
                "**Total:** " + totalMembers + "\n" +
                "**Humans:** " + humans + "\n" +
                "**Bots:** " + bots,
                true);

        // Channel Info
        embed.addField(CustomEmojis.NOTE + " Channels",
                "**Text:** " + guild.getTextChannels().size() + "\n" +
                "**Voice:** " + guild.getVoiceChannels().size() + "\n" +
                "**Categories:** " + guild.getCategories().size() + "\n" +
                "**Total:** " + guild.getChannels().size(),
                true);

        // Role Info
        embed.addField("🎭 Other",
                "**Roles:** " + guild.getRoles().size() + "\n" +
                "**Emojis:** " + guild.getEmojis().size() + "\n" +
                "**Boosts:** " + guild.getBoostCount() + "\n" +
                "**Boost Tier:** " + guild.getBoostTier(),
                true);

        if (guild.getDescription() != null) {
            embed.addField("📄 Description", guild.getDescription(), false);
        }

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Handle xp command with prefix syntax
     */
    private void handleXpCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).queue();
            return;
        }

        // Check if target user is specified
        String userArg = options.get("user");
        User targetUser;
        
        if (userArg != null && !userArg.trim().isEmpty()) {
            targetUser = parseUserMention(event, userArg);
            if (targetUser == null) return;
        } else {
            targetUser = event.getAuthor();
        }

        String guildId = event.getGuild().getId();
        String userId = targetUser.getId();
        
        // Get XP data from storage
        long currentXp = ServerBot.getStorageManager().getExperience(guildId, userId);
        int currentLevel = calculateLevel(currentXp);
        long xpForCurrentLevel = calculateXpForLevel(currentLevel);
        long xpForNextLevel = calculateXpForLevel(currentLevel + 1);
        long xpProgress = currentXp - xpForCurrentLevel;
        long xpNeeded = xpForNextLevel - xpForCurrentLevel;

        // Create progress bar
        String progressBar = createProgressBar(xpProgress, xpNeeded);

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("📈 XP Information")
                .setThumbnail(targetUser.getEffectiveAvatarUrl());

        embed.addField("User", targetUser.getAsMention(), true);
        embed.addField("Level", String.valueOf(currentLevel), true);
        embed.addField("Total XP", String.valueOf(currentXp), true);

        embed.addField("Progress to Level " + (currentLevel + 1),
                progressBar + "\n" + xpProgress + " / " + xpNeeded + " XP", false);

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Calculate level from XP
     */
    private int calculateLevel(long xp) {
        return com.serverbot.ServerBot.getStorageManager().calculateLevel(xp);
    }

    /**
     * Calculate XP needed for a specific level
     */
    private long calculateXpForLevel(int level) {
        return com.serverbot.ServerBot.getStorageManager().calculateXpForLevel(level);
    }

    /**
     * Create a progress bar string
     */
    private String createProgressBar(long current, long max) {
        int barLength = 20;
        int progress = (int) ((double) current / max * barLength);
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < progress) {
                bar.append("█");
            } else {
                bar.append("░");
            }
        }
        bar.append("]");
        
        int percentage = (int) ((double) current / max * 100);
        bar.append(" ").append(percentage).append("%");
        
        return bar.toString();
    }

    /**
     * Parse user mention from string
     */
    private User parseUserMention(MessageReceivedEvent event, String userArg) {
        if (userArg.startsWith("<@") && userArg.endsWith(">")) {
            String userId = userArg.replaceAll("[<@!>]", "");
            try {
                return event.getJDA().getUserById(userId);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User", "Please mention a valid user."
                )).queue();
                return null;
            }
        } else {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid User", "Please mention a valid user."
            )).queue();
            return null;
        }
    }

    /**
     * Check if moderator can interact with target member
     */
    private boolean canInteractWith(Member moderator, Member target) {
        return moderator.canInteract(target);
    }

    /**
     * Handle daily command with prefix syntax
     */
    private void handleDailyCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            // Check basic requirements that would be in the slash command
            if (!event.isFromGuild()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers."
                )).queue();
                return;
            }

            // Since we can't easily create a SlashCommandInteractionEvent, we'll implement the logic directly
            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            
            // Get guild settings
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
            
            // Check if economy is enabled
            Boolean economyEnabled = (Boolean) guildSettings.get("enableEconomy");
            if (economyEnabled == null || !economyEnabled) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Economy Disabled", "The economy system is disabled in this server."
                )).queue();
                return;
            }

            // Get daily reward amount from settings
            Object dailyRewardSetting = guildSettings.get("economy.dailyReward");
            int dailyReward = 100; // Default value
            if (dailyRewardSetting != null) {
                if (dailyRewardSetting instanceof Number) {
                    dailyReward = ((Number) dailyRewardSetting).intValue();
                }
            }

            // Check if user has already claimed today
            String lastClaimKey = "daily_last_claim_" + userId;
            Object lastClaimObj = guildSettings.get(lastClaimKey);
            
            LocalDate today = LocalDate.now(ZoneId.systemDefault());
            LocalDate lastClaim = null;
            
            if (lastClaimObj != null) {
                try {
                    lastClaim = LocalDate.parse(lastClaimObj.toString());
                } catch (Exception e) {
                    // Ignore parsing errors, treat as no previous claim
                }
            }
            
            if (lastClaim != null && lastClaim.equals(today)) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Already Claimed", "You have already claimed your daily reward today! Come back tomorrow."
                )).queue();
                return;
            }

            // Calculate streak and bonus
            String streakKey = "daily_streak_" + userId;
            Object streakObj = guildSettings.get(streakKey);
            int currentStreak = 0;
            
            if (streakObj != null) {
                try {
                    currentStreak = Integer.parseInt(streakObj.toString());
                } catch (NumberFormatException e) {
                    currentStreak = 0;
                }
            }
            
            // Check if streak continues (yesterday was last claim)
            if (lastClaim != null && lastClaim.equals(today.minusDays(1))) {
                currentStreak++;
            } else if (lastClaim == null || !lastClaim.equals(today.minusDays(1))) {
                currentStreak = 1; // Start new streak
            }

            // Calculate bonus (10% per day, max 100% at 10 days)
            double bonusMultiplier = 1.0 + Math.min(currentStreak * 0.1, 1.0);
            int totalReward = (int) (dailyReward * bonusMultiplier);
            
            // Add random bonus (±20%)
            Random random = new Random();
            double randomMultiplier = 0.8 + (random.nextDouble() * 0.4); // 0.8 to 1.2
            totalReward = (int) (totalReward * randomMultiplier);

            // Update user data
            ServerBot.getStorageManager().updateGuildSettings(guildId, lastClaimKey, today.toString());
            ServerBot.getStorageManager().updateGuildSettings(guildId, streakKey, String.valueOf(currentStreak));
            
            // Add to balance
            long currentBalance = ServerBot.getStorageManager().getBalance(guildId, userId);
            ServerBot.getStorageManager().setBalance(guildId, userId, currentBalance + totalReward);

            // Get currency name
            String currencyName = ServerBot.getStorageManager().getCurrencyName(guildId);
            String currencyIcon = ServerBot.getStorageManager().getCurrencyIcon(guildId);

            // Send success message
            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(currencyIcon + " Daily Reward Claimed!")
                    .setDescription(String.format("You received **%d** %s!", totalReward, currencyName))
                    .addField("🔥 Streak", String.valueOf(currentStreak), true)
                    .addField("💎 Bonus", String.format("%.0f%%", (bonusMultiplier - 1) * 100), true)
                    .addField("Balance", String.format("%d %s", currentBalance + totalReward, currencyName), true);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Daily Error",
                "Failed to process daily command: " + e.getMessage()
            )).queue();
        }
    }

    /**
     * Handle error command with prefix syntax
     */
    private void handleErrorCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String category = options.get("category");
            
            if (category == null) {
                // Show error code overview
                EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                        .setTitle("🚨 Error Code Documentation")
                        .setDescription("Use `!error <category>` to see specific error codes.")
                        .addField("📚 Available Categories", 
                                "**A** - Authentication & Access\n" +
                                "**B** - Bot Configuration\n" +
                                "**C** - Command & Communication\n" +
                                "**D** - Database & Data\n" +
                                "**E** - Economy & Exchange\n" +
                                "**F** - File & Form\n" +
                                "**G** - Guild & General\n" +
                                "**H** - Help & Handler\n" +
                                "**I** - Integration & Input\n" +
                                "**J** - JSON & Joining\n" +
                                "**K** - Kernel & Key\n" +
                                "**L** - Logging & Limit\n" +
                                "**M** - Moderation & Member\n" +
                                "**N** - Network & Notification\n" +
                                "**O** - Operation & Output\n" +
                                "**P** - Permission & Process", false);
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
            } else {
                // Show specific category - redirect to new /error command
                EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR);
                
                switch (category.toUpperCase()) {
                    case "A" -> {
                        embed.setTitle("🚨 Error Code System Updated")
                                .setDescription("The error code system has been updated to a new 3-digit numeric format.\n\n" +
                                    "Please use `/error category:1` for Input/Validation errors (1XX)\n" +
                                    "Or `/error category:2` for Permission/Access errors (2XX)")
                                .addField("New System", "All error codes now use 3-digit numbers organized by category.", false);
                    }
                    case "B" -> {
                        embed.setTitle("🚨 Error Code System Updated")
                                .setDescription("The error code system has been updated to a new 3-digit numeric format.\n\n" +
                                    "Please use `/error category:6` for Configuration errors (6XX)")
                                .addField("New System", "All error codes now use 3-digit numbers organized by category.", false);
                    }
                    case "C" -> {
                        embed.setTitle("🚨 Error Code System Updated")
                                .setDescription("The error code system has been updated to a new 3-digit numeric format.\n\n" +
                                    "Please use `/error category:4` for Operation errors (4XX)")
                                .addField("New System", "All error codes now use 3-digit numbers organized by category.", false);
                    }
                    case "D" -> {
                        embed.setTitle("🚨 Error Code System Updated")
                                .setDescription("The error code system has been updated to a new 3-digit numeric format.\n\n" +
                                    "Please use `/error category:5` for System/Database errors (5XX)")
                                .addField("New System", "All error codes now use 3-digit numbers organized by category.", false);
                    }
                    case "E" -> {
                        embed.setTitle("🚨 Error Code System Updated")
                                .setDescription("The error code system has been updated to a new 3-digit numeric format.\n\n" +
                                    "Please use `/error` to see all available error categories.")
                                .addField("New System", "All error codes now use 3-digit numbers organized by category.", false);
                    }
                    default -> {
                        embed.setTitle(CustomEmojis.ERROR + " Unknown Category")
                                .setDescription("Error category '" + category + "' not found.\n\n" +
                                    "Use `/error` to see all available categories in the new 3-digit system.");
                    }
                }
                
                event.getChannel().sendMessageEmbeds(embed.build()).queue();
            }
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Error Command Error",
                "Failed to process error command: " + e.getMessage()
            )).queue();
        }
    }

    /**
     * Handle unban command with prefix syntax
     */
    private void handleUnbanCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            if (!event.isFromGuild()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers."
                )).queue();
                return;
            }

            // Check permissions
            Member member = event.getMember();
            if (!PermissionManager.hasPermission(member, "moderation.unban")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You don't have permission to use the unban command!"
                )).setComponents().queue();
                return;
            }

            String userArg = options.get("user");
            if (userArg == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Arguments", "Please specify a user to unban."
                )).queue();
                return;
            }

            // Parse user ID from mention or direct ID
            String userId = userArg.replaceAll("[<@!>]", "");
            
            try {
                Guild guild = event.getGuild();
                User targetUser = event.getJDA().getUserById(userId);
                
                if (targetUser == null) {
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "User Not Found", "Could not find the specified user."
                    )).queue();
                    return;
                }

                // Check if user is actually banned
                guild.retrieveBanList().queue(bans -> {
                    boolean isBanned = bans.stream().anyMatch(ban -> ban.getUser().getId().equals(userId));
                    
                    if (!isBanned) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Not Banned", "This user is not banned from the server."
                        )).queue();
                        return;
                    }

                    // Unban the user
                    guild.unban(targetUser).queue(
                        success -> {
                            // Log the action
                            AutoLogUtils.logUnban(guild, targetUser, event.getAuthor(), "Unbanned via prefix command");

                            // Remove from storage - we'll use temp punishments instead
                            ServerBot.getStorageManager().removeTempPunishment("ban_" + guild.getId() + "_" + userId);

                            // Send success message
                            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                "User Unbanned", 
                                String.format("Successfully unbanned **%s**", targetUser.getName())
                            )).queue();
                        },
                        error -> {
                            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                "Unban Failed", "Failed to unban user: " + error.getMessage()
                            )).queue();
                        }
                    );
                });
                
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid User", "Please provide a valid user mention or ID."
                )).queue();
            }
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unban Error",
                "Failed to process unban command: " + e.getMessage()
            )).queue();
        }
    }

    /**
     * Handle unmute command with prefix syntax
     */
    private void handleUnmuteCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            if (!event.isFromGuild()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers."
                )).queue();
                return;
            }

            // Check permissions
            Member member = event.getMember();
            if (!PermissionManager.hasPermission(member, "moderation.unmute")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You don't have permission to use the unmute command!"
                )).queue();
                return;
            }

            String userArg = options.get("user");
            if (userArg == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Arguments", "Please specify a user to unmute."
                )).queue();
                return;
            }

            // Parse user mention
            User targetUser = parseUserMention(event, userArg);
            if (targetUser == null) return;

            Guild guild = event.getGuild();
            Member targetMember = guild.getMember(targetUser);
            
            if (targetMember == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Member Not Found", "This user is not a member of this server."
                )).queue();
                return;
            }

            // Get mute role
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guild.getId());
            String muteRoleId = (String) guildSettings.get("muteRoleId");
            
            if (muteRoleId == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "No Mute Role", "No mute role has been configured for this server."
                )).queue();
                return;
            }

            Role muteRole = guild.getRoleById(muteRoleId);
            if (muteRole == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Mute Role Not Found", "The configured mute role no longer exists."
                )).queue();
                return;
            }

            // Check if user is actually muted
            if (!targetMember.getRoles().contains(muteRole)) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Muted", "This user is not currently muted."
                )).queue();
                return;
            }

            // Remove mute role
            guild.removeRoleFromMember(targetMember, muteRole).queue(
                success -> {
                    // Log the action
                    AutoLogUtils.logUnmute(guild, targetUser, event.getAuthor(), "Unmuted via prefix command");

                    // Remove from storage
                    String guildId = guild.getId();
                    String userId = targetUser.getId();
                    ServerBot.getStorageManager().removeTempPunishment("mute_" + guildId + "_" + userId);

                    // Send success message
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "User Unmuted", 
                        String.format("Successfully unmuted **%s**", targetUser.getName())
                    )).queue();
                },
                error -> {
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Unmute Failed", "Failed to unmute user: " + error.getMessage()
                    )).queue();
                }
            );
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unmute Error",
                "Failed to process unmute command: " + e.getMessage()
            )).queue();
        }
    }

    /**
     * Handle unwarn command with prefix syntax
     */
    private void handleUnwarnCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            if (!event.isFromGuild()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers."
                )).queue();
                return;
            }

            // Check permissions
            Member member = event.getMember();
            if (!PermissionManager.hasPermission(member, "moderation.unwarn")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You don't have permission to use the unwarn command!"
                )).queue();
                return;
            }

            String userArg = options.get("user");
            if (userArg == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Arguments", "Please specify a user to remove warnings from."
                )).queue();
                return;
            }

            // Parse user mention
            User targetUser = parseUserMention(event, userArg);
            if (targetUser == null) return;

            String guildId = event.getGuild().getId();
            String userId = targetUser.getId();

            // Get current warnings
            List<Map<String, Object>> warnings = ServerBot.getStorageManager().getWarnings(guildId, userId);
            
            if (warnings.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "No Warnings", "This user has no warnings to remove."
                )).queue();
                return;
            }

            // Remove all warnings
            ServerBot.getStorageManager().clearWarnings(guildId, userId);

            // Log the action
            AutoLogUtils.logUnwarn(event.getGuild(), targetUser, event.getAuthor(), 
                String.format("Removed %d warning(s) via prefix command", warnings.size()), 
                "Multiple warnings");

            // Send success message
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "Warnings Cleared", 
                String.format("Successfully removed **%d** warning(s) from **%s**", 
                    warnings.size(), targetUser.getName())
            )).queue();
            
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unwarn Error",
                "Failed to process unwarn command: " + e.getMessage()
            )).queue();
        }
    }

    // ============================================
    // Gambling/Games Commands
    // ============================================

    private void handleGambleCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String amountStr = options.get("amount");
            
            if (amountStr == null || amountStr.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Missing Amount",
                    "You need to specify an amount to gamble.",
                    "!gamble <amount> [game]",
                    "!gamble 100 coinflip"
                )).queue();
                return;
            }

            long amount;
            try {
                amount = Long.parseLong(amountStr);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "Please enter a valid number."
                )).queue();
                return;
            }

            if (amount <= 0) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "You must bet at least 1 coin."
                )).queue();
                return;
            }

            if (amount > 10000) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Bet Too High", "You cannot bet more than 10,000 coins at once."
                )).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            long balance = ServerBot.getStorageManager().getBalance(guildId, userId);

            if (balance < amount) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You only have %,d coins but tried to bet %,d coins.", balance, amount)
                )).queue();
                return;
            }

            // Simple coinflip gamble
            boolean won = new Random().nextBoolean();
            long newBalance;
            String result;

            if (won) {
                newBalance = balance + amount;
                result = String.format("🎉 **You won!**\nYou doubled your bet and earned **%,d** coins!\n**New Balance:** %,d coins", amount, newBalance);
            } else {
                newBalance = balance - amount;
                result = String.format("💸 **You lost!**\nYou lost **%,d** coins.\n**New Balance:** %,d coins", amount, newBalance);
            }

            ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);
            
            event.getChannel().sendMessageEmbeds(EmbedUtils.createDefaultEmbed(
                "🎰 Gamble Result", result
            )).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Gamble Error", "Failed to process gamble command: " + e.getMessage()
            )).queue();
        }
    }

    private void handleSlotsCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String amountStr = options.get("amount");
            
            if (amountStr == null || amountStr.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Missing Amount",
                    "You need to specify an amount to bet.",
                    "!slots <amount>",
                    "!slots 100"
                )).queue();
                return;
            }

            long amount;
            try {
                amount = Long.parseLong(amountStr);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "Please enter a valid number."
                )).queue();
                return;
            }

            if (amount <= 0 || amount > 10000) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "Bet must be between 1 and 10,000 coins."
                )).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            long balance = ServerBot.getStorageManager().getBalance(guildId, userId);

            if (balance < amount) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You only have %,d coins.", balance)
                )).queue();
                return;
            }

            // Slot symbols
            String[] symbols = {"🍒", "🍋", "🍊", "🍇", "💎", "7️⃣"};
            Random random = new Random();
            
            String s1 = symbols[random.nextInt(symbols.length)];
            String s2 = symbols[random.nextInt(symbols.length)];
            String s3 = symbols[random.nextInt(symbols.length)];

            long winnings = 0;
            String resultMsg;

            if (s1.equals(s2) && s2.equals(s3)) {
                // Jackpot - all three match (multiplier is total return, net = multiplier - 1)
                int multiplier = s1.equals("7️⃣") ? 9 : (s1.equals("💎") ? 4 : 2);
                winnings = amount * multiplier;
                resultMsg = "🎉 **JACKPOT!** All three match!";
            } else if (s1.equals(s2) || s2.equals(s3) || s1.equals(s3)) {
                // Two match - break even (return bet, no net change)
                winnings = 0;
                resultMsg = "✨ **Two match!** You got your bet back!";
            } else {
                // No match
                winnings = -amount;
                resultMsg = "💸 **No match.** Better luck next time!";
            }

            long newBalance = balance + winnings;
            ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);

            String slotsDisplay = String.format("╔═══════════╗\n║ %s │ %s │ %s ║\n╚═══════════╝", s1, s2, s3);
            
            event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("🎰 Slots")
                .setDescription(slotsDisplay + "\n\n" + resultMsg)
                .addField("Result", winnings >= 0 ? 
                    String.format("+%,d coins", winnings) : 
                    String.format("%,d coins", winnings), true)
                .addField("New Balance", String.format("%,d coins", newBalance), true)
                .setColor(winnings > 0 ? EmbedUtils.SUCCESS_COLOR : (winnings < 0 ? EmbedUtils.ERROR_COLOR : EmbedUtils.WARNING_COLOR))
                .build()
            ).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Slots Error", "Failed to process slots command: " + e.getMessage()
            )).queue();
        }
    }

    private void handleFlipCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String amountStr = options.get("amount");
            String choice = options.getOrDefault("choice", "heads").toLowerCase();
            
            if (amountStr == null || amountStr.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Missing Amount",
                    "You need to specify an amount to bet.",
                    "!flip <amount> [heads/tails]",
                    "!flip 100 heads"
                )).queue();
                return;
            }

            long amount;
            try {
                amount = Long.parseLong(amountStr);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "Please enter a valid number."
                )).queue();
                return;
            }

            if (amount <= 0 || amount > 10000) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "Bet must be between 1 and 10,000 coins."
                )).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            long balance = ServerBot.getStorageManager().getBalance(guildId, userId);

            if (balance < amount) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You only have %,d coins.", balance)
                )).queue();
                return;
            }

            // Normalize choice
            if (choice.startsWith("h")) choice = "heads";
            else if (choice.startsWith("t")) choice = "tails";
            else choice = "heads";

            boolean isHeads = new Random().nextBoolean();
            String result = isHeads ? "heads" : "tails";
            boolean won = choice.equals(result);

            long newBalance = won ? balance + amount : balance - amount;
            ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);

            String emoji = isHeads ? "🪙" : "🔵";
            String outcomeMsg = won ? 
                String.format("🎉 **You won!** +%,d coins", amount) :
                String.format("💸 **You lost!** -%,d coins", amount);

            event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle(emoji + " Coin Flip")
                .setDescription(String.format("The coin landed on **%s**!\n\nYou chose **%s**.\n\n%s", result, choice, outcomeMsg))
                .addField("New Balance", String.format("%,d coins", newBalance), true)
                .setColor(won ? EmbedUtils.SUCCESS_COLOR : EmbedUtils.ERROR_COLOR)
                .build()
            ).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Flip Error", "Failed to process flip command: " + e.getMessage()
            )).queue();
        }
    }

    private void handleDiceCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String amountStr = options.get("amount");
            String guessStr = options.get("guess");
            
            if (amountStr == null || amountStr.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Missing Amount",
                    "You need to specify an amount to bet and a number to guess.",
                    "!dice <amount> <guess 1-6>",
                    "!dice 100 4"
                )).queue();
                return;
            }

            long amount;
            int guess;
            try {
                amount = Long.parseLong(amountStr);
                guess = guessStr != null ? Integer.parseInt(guessStr) : -1;
            } catch (NumberFormatException e) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Input", "Please enter valid numbers."
                )).queue();
                return;
            }

            if (guess < 1 || guess > 6) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Guess", "Please guess a number between 1 and 6."
                )).queue();
                return;
            }

            if (amount <= 0 || amount > 10000) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Amount", "Bet must be between 1 and 10,000 coins."
                )).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            long balance = ServerBot.getStorageManager().getBalance(guildId, userId);

            if (balance < amount) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Funds",
                    String.format("You only have %,d coins.", balance)
                )).queue();
                return;
            }

            int roll = new Random().nextInt(6) + 1;
            boolean won = roll == guess;
            long winnings = won ? amount * 4 : -amount; // 5x return - 1x bet = 4x net profit
            long newBalance = balance + winnings;

            ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);

            String[] diceEmoji = {"⚀", "⚁", "⚂", "⚃", "⚄", "⚅"};
            String outcomeMsg = won ?
                String.format("🎉 **You won!** +%,d coins (5x payout!)", amount * 4) :
                String.format("💸 **You lost!** -%,d coins", amount);

            event.getChannel().sendMessageEmbeds(new EmbedBuilder()
                .setTitle("🎲 Dice Roll")
                .setDescription(String.format("You guessed **%d**\n\n%s The dice rolled **%d**!\n\n%s", 
                    guess, diceEmoji[roll - 1], roll, outcomeMsg))
                .addField("New Balance", String.format("%,d coins", newBalance), true)
                .setColor(won ? EmbedUtils.SUCCESS_COLOR : EmbedUtils.ERROR_COLOR)
                .build()
            ).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Dice Error", "Failed to process dice command: " + e.getMessage()
            )).queue();
        }
    }

    // ============================================
    // Utility Commands
    // ============================================

    private void handlePurgeCommand(MessageReceivedEvent event, Map<String, String> options) {
        try {
            // Check permissions
            Member member = event.getMember();
            if (member == null || !PermissionManager.hasPermission(member, "moderation.purge")) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Permission Denied", "You don't have permission to use this command.",
                    event.getAuthor().getId()
                );
                return;
            }

            String amountStr = options.get("amount");
            if (amountStr == null || amountStr.isEmpty()) {
                DismissibleMessage.send(event.getChannel(),
                    EmbedUtils.createErrorEmbedWithUsage(
                        "Missing Amount",
                        "You need to specify how many messages to delete.",
                        "!purge <amount> [@user]",
                        "!purge 50"
                    ),
                    event.getAuthor().getId()
                );
                return;
            }

            int amount;
            try {
                amount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Invalid Amount", "Please enter a valid number.",
                    event.getAuthor().getId()
                );
                return;
            }

            if (amount < 1 || amount > 100) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Invalid Amount", "Amount must be between 1 and 100.",
                    event.getAuthor().getId()
                );
                return;
            }

            // Delete the command message first
            event.getMessage().delete().queue();

            // Get and delete messages
            event.getChannel().getHistory().retrievePast(amount).queue(messages -> {
                if (messages.isEmpty()) {
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "No Messages", "No messages found to delete."
                    )).queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
                    return;
                }

                // Filter by user if specified
                String userArg = options.get("user");
                if (userArg != null && !userArg.isEmpty()) {
                    User targetUser = parseUserMention(event, userArg);
                    if (targetUser != null) {
                        messages.removeIf(msg -> !msg.getAuthor().getId().equals(targetUser.getId()));
                    }
                }

                if (messages.size() == 1) {
                    messages.get(0).delete().queue();
                } else {
                    event.getGuildChannel().asTextChannel().deleteMessages(messages).queue();
                }

                DismissibleMessage.sendSuccess(event.getChannel(),
                    "Messages Purged",
                    String.format("Successfully deleted **%d** message(s).", messages.size()),
                    event.getAuthor().getId()
                );
            });

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Purge Error", "Failed to process purge command: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    /**
     * Handle proxy commands with px; prefix
     */
    private void handleProxyCommand(MessageReceivedEvent event, String commandContent) {
        try {
            if (!event.isFromGuild()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "Proxy commands can only be used in servers."
                )).queue();
                return;
            }

            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            
            // Parse command and arguments
            String[] parts = commandContent.trim().split("\\s+", 2);
            String command = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";

            switch (command) {
                case "ap", "autoproxy" -> handleAutoproxyCommand(event, args, guildId, userId);
                case "new", "add", "create" -> handleProxyNewCommand(event, args, guildId, userId);
                case "member", "m" -> handleProxyMemberCommand(event, args, guildId, userId);
                case "system", "sys", "s" -> handleProxySystemCommand(event, guildId, userId);
                case "list", "ls" -> handleProxyListCommand(event, guildId, userId);
                case "switch", "sw" -> handleProxySwitchCommand(event, args, guildId, userId);
                case "help", "h" -> handleProxyHelpCommand(event);
                default -> event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Unknown Command",
                    "Unknown proxy command: `" + command + "`\n" +
                    "Use `px;help` for a list of available commands."
                )).queue();
            }
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Proxy Error",
                "Failed to process proxy command: " + e.getMessage()
            )).queue();
        }
    }

    private void handleAutoproxyCommand(MessageReceivedEvent event, String args, String guildId, String userId) {
        ProxyService proxyService = ServerBot.getProxyService();
        
        if (args.isEmpty()) {
            // Show current status
            ProxySettings settings = proxyService.getSettings(userId, guildId);
            String mode = settings.getAutoproxyMode().toString();
            boolean enabled = !settings.getAutoproxyMode().equals(ProxySettings.AutoproxyMode.OFF);
            
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                "Autoproxy Status",
                String.format("**Current Mode:** %s\n**Status:** %s",
                    mode, enabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled")
            )).queue();
            return;
        }

        // Parse the mode
        String mode = args.toLowerCase();
        ProxySettings.AutoproxyMode newMode;

        switch (mode) {
            case "off", "false", "disable", "disabled" -> newMode = ProxySettings.AutoproxyMode.OFF;
            case "on", "true", "enable", "enabled", "member" -> newMode = ProxySettings.AutoproxyMode.MEMBER;
            case "latch" -> newMode = ProxySettings.AutoproxyMode.LATCH;
            case "front" -> newMode = ProxySettings.AutoproxyMode.FRONT;
            case "sticky" -> newMode = ProxySettings.AutoproxyMode.STICKY;
            default -> {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Mode",
                    "Valid autoproxy modes: `off`, `member`, `latch`, `front`, `sticky`"
                )).queue();
                return;
            }
        }

        ProxySettings settings = proxyService.getSettings(userId, guildId);
        settings.setAutoproxyMode(newMode);
        
        proxyService.updateSettings(userId, guildId, settings).thenAccept(result -> {
            if (result.startsWith("7")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Autoproxy Error [" + result + "]",
                    "Failed to update autoproxy settings.\nUse `/error " + result + "` for more info."
                )).queue();
            } else {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "Autoproxy Updated",
                    String.format("Autoproxy mode set to: **%s**", newMode)
                )).queue();
            }
        });
    }

    private void handleProxyNewCommand(MessageReceivedEvent event, String args, String guildId, String userId) {
        if (args.isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Name",
                "Please provide a name for the proxy member.\nExample: `px;new John`"
            )).queue();
            return;
        }

        ProxyService proxyService = ServerBot.getProxyService();
        proxyService.createMember(userId, guildId, args, null, null, null, null).thenAccept(result -> {
            if (result.startsWith("7")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Creation Failed [" + result + "]",
                    "Failed to create proxy member.\nUse `/error " + result + "` for more info."
                )).queue();
            } else {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "Proxy Member Created",
                    String.format("Created proxy member: **%s**\nID: `%s`\n\n" +
                        "Set up tags with: `/proxy member %s tags:prefix suffix:suffix`",
                        args, result, args)
                )).queue();
            }
        });
    }

    private void handleProxyMemberCommand(MessageReceivedEvent event, String args, String guildId, String userId) {
        if (args.isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Name",
                "Please provide the proxy member name.\nExample: `px;member John`"
            )).queue();
            return;
        }

        ProxyService proxyService = ServerBot.getProxyService();
        com.serverbot.models.ProxyMember member = proxyService.getMemberByName(userId, guildId, args);

        if (member == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Member Not Found",
                "Could not find proxy member: **" + args + "**"
            )).queue();
        } else {
            StringBuilder info = new StringBuilder();
            info.append("**Member Information**\n\n");
            info.append("**Name:** ").append(member.getName()).append("\n");
            info.append("**Display Name:** ").append(member.getDisplayName()).append("\n");
            if (member.getPronouns() != null) {
                info.append("**Pronouns:** ").append(member.getPronouns()).append("\n");
            }
            if (member.getDescription() != null) {
                info.append("**Description:** ").append(member.getDescription()).append("\n");
            }
            info.append("**ID:** `").append(member.getMemberId()).append("`\n");
            
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                "Proxy Member", info.toString()
            )).queue();
        }
    }

    private void handleProxySystemCommand(MessageReceivedEvent event, String guildId, String userId) {
        ProxyService proxyService = ServerBot.getProxyService();
        ProxySettings settings = proxyService.getSettings(userId, guildId);
        
        StringBuilder info = new StringBuilder();
        info.append("**System Information**\n\n");
        info.append("**User:** ").append(event.getAuthor().getAsMention()).append("\n");
        info.append("**Autoproxy Mode:** ").append(settings.getAutoproxyMode()).append("\n");
        
        if (settings.getAutoproxyMemberId() != null) {
            com.serverbot.models.ProxyMember member = proxyService.getMember(settings.getAutoproxyMemberId());
            if (member != null) {
                info.append("**Autoproxy Member:** ").append(member.getName()).append("\n");
            }
        }
        
        info.append("\n**Commands:**\n");
        info.append("`px;list` - View all proxy members\n");
        info.append("`px;ap [mode]` - Set autoproxy mode\n");
        info.append("`px;new [name]` - Create new member\n");
        info.append("`px;help` - Show all commands\n");

        event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
            "Proxy System", info.toString()
        )).queue();
    }

    private void handleProxyListCommand(MessageReceivedEvent event, String guildId, String userId) {
        ProxyService proxyService = ServerBot.getProxyService();
        List<com.serverbot.models.ProxyMember> members = proxyService.getUserMembers(userId, guildId);

        if (members.isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                "No Members",
                "You don't have any proxy members yet.\nCreate one with: `px;new [name]`"
            )).queue();
        } else {
            StringBuilder list = new StringBuilder();
            list.append("**Your Proxy Members**\n\n");
            
            for (com.serverbot.models.ProxyMember member : members) {
                list.append("• **").append(member.getName()).append("**");
                if (member.getGuildId() == null) {
                    list.append(" *(global)*");
                }
                list.append(" - `").append(member.getMemberId()).append("`\n");
            }
            
            list.append("\nUse `px;member [name]` to view details.");
            
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                "Proxy Members", list.toString()
            )).queue();
        }
    }

    private void handleProxySwitchCommand(MessageReceivedEvent event, String args, String guildId, String userId) {
        if (args.isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Name",
                "Please provide the proxy member name to switch to.\nExample: `px;switch John`"
            )).queue();
            return;
        }

        ProxyService proxyService = ServerBot.getProxyService();
        com.serverbot.models.ProxyMember member = proxyService.getMemberByName(userId, guildId, args);
        
        if (member == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Member Not Found",
                "Could not find proxy member: **" + args + "**"
            )).queue();
            return;
        }

        proxyService.switchMember(userId, guildId, member.getMemberId()).thenAccept(result -> {
            if (result.startsWith("7")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Switch Failed [" + result + "]",
                    "Failed to switch to member: **" + args + "**\nUse `/error " + result + "` for more info."
                )).queue();
            } else {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                    "Switched Member",
                    String.format("Now proxying as: **%s**", args)
                )).queue();
            }
        });
    }

    private void handleProxyHelpCommand(MessageReceivedEvent event) {
        StringBuilder help = new StringBuilder();
        help.append("**Proxy Commands (prefix: `px;`)**\n\n");
        help.append("**Basic Commands:**\n");
        help.append("`px;new [name]` - Create a new proxy member\n");
        help.append("`px;member [name]` - View member info\n");
        help.append("`px;list` - List all your proxy members\n");
        help.append("`px;switch [name]` - Switch to a proxy member\n");
        help.append("`px;system` - View your system info\n\n");
        help.append("**Autoproxy:**\n");
        help.append("`px;ap [mode]` - Set autoproxy mode\n");
        help.append("`px;autoproxy [mode]` - Same as above\n\n");
        help.append("**Modes:** `off`, `member`, `latch`, `front`, `sticky`\n\n");
        help.append("**Advanced:**\n");
        help.append("Use slash commands `/proxy` for full functionality including:\n");
        help.append("• Setting tags (prefix/suffix)\n");
        help.append("• Managing groups\n");
        help.append("• Configuring display names and avatars\n");

        event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
            "Proxy Help", help.toString()
        )).queue();
    }

    // ========== AUTOMOD COMMAND ==========
    private void handleAutomodCommand(MessageReceivedEvent event, Map<String, String> options) {
        Member executor = event.getMember();
        if (executor == null) return;

        // Check permissions
        if (!PermissionManager.hasPermission(executor, "admin.automod")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions",
                "You need administrator permissions to configure automod.",
                event.getAuthor().getId()
            );
            return;
        }

        String action = options.get("action");
        String feature = options.get("feature");

        if (action == null) {
            // Show usage
            DismissibleMessage.sendInfo(event.getChannel(),
                "Automod Usage",
                "**Usage:** `!automod <action> <feature> [threshold]`\n\n" +
                "**Actions:**\n" +
                "• `enable` - Enable an automod feature\n" +
                "• `disable` - Disable an automod feature\n" +
                "• `view` - View current settings for a feature\n\n" +
                "**Features:**\n" +
                "• `anti_spam` - Prevent message spam\n" +
                "• `bad_words` - Filter inappropriate words\n" +
                "• `caps_lock` - Limit excessive caps\n" +
                "• `repeated_text` - Prevent repeated messages\n" +
                "• `mass_mentions` - Limit mass pinging\n" +
                "• `link_filter` - Filter unwanted links\n\n" +
                "**Example:** `!automod enable anti_spam 5`",
                event.getAuthor().getId()
            );
            return;
        }

        if (feature == null) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing Feature",
                "Please specify which feature to configure.\n" +
                "**Available:** `anti_spam`, `bad_words`, `caps_lock`, `repeated_text`, `mass_mentions`, `link_filter`",
                event.getAuthor().getId()
            );
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            switch (action.toLowerCase()) {
                case "enable" -> {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "automod_" + feature + "_enabled", true);
                    String thresholdStr = options.get("threshold");
                    if (thresholdStr != null) {
                        try {
                            int threshold = Integer.parseInt(thresholdStr);
                            ServerBot.getStorageManager().updateGuildSettings(guildId, "automod_" + feature + "_threshold", threshold);
                        } catch (NumberFormatException ignored) {}
                    }

                    DismissibleMessage.sendSuccess(event.getChannel(),
                        "Automod Feature Enabled",
                        "**Feature:** " + feature.toUpperCase().replace("_", " ") + "\n" +
                        "**Status:** Enabled\n" +
                        "**Configured by:** " + executor.getAsMention(),
                        event.getAuthor().getId()
                    );
                }
                case "disable" -> {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "automod_" + feature + "_enabled", false);

                    DismissibleMessage.sendSuccess(event.getChannel(),
                        "Automod Feature Disabled",
                        "**Feature:** " + feature.toUpperCase().replace("_", " ") + "\n" +
                        "**Status:** Disabled\n" +
                        "**Configured by:** " + executor.getAsMention(),
                        event.getAuthor().getId()
                    );
                }
                case "view" -> {
                    boolean enabled = guildSettings.getOrDefault("automod_" + feature + "_enabled", false).equals(true);
                    Object threshold = guildSettings.get("automod_" + feature + "_threshold");

                    String description = "**Feature:** " + feature.toUpperCase().replace("_", " ") + "\n" +
                        "**Status:** " + (enabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled");

                    if (threshold != null) {
                        description += "\n**Threshold:** " + threshold;
                    }

                    DismissibleMessage.sendInfo(event.getChannel(),
                        "Automod Configuration",
                        description,
                        event.getAuthor().getId()
                    );
                }
                default -> {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Invalid Action",
                        "Valid actions are: `enable`, `disable`, `view`",
                        event.getAuthor().getId()
                    );
                }
            }
        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Automod Config Failed",
                "Failed to configure automod: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    // ========== SERVERSTATS COMMAND ==========
    private void handleServerstatsCommand(MessageReceivedEvent event) {
        Guild guild = event.getGuild();
        if (guild == null) return;

        try {
            String guildId = guild.getId();
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Calculate member statistics
            java.util.List<Member> members = guild.getMembers();
            int totalMembers = members.size();
            int humanMembers = (int) members.stream().filter(m -> !m.getUser().isBot()).count();
            int botMembers = totalMembers - humanMembers;

            // Calculate online status
            int onlineMembers = (int) members.stream().filter(m -> m.getOnlineStatus() == net.dv8tion.jda.api.OnlineStatus.ONLINE).count();
            int idleMembers = (int) members.stream().filter(m -> m.getOnlineStatus() == net.dv8tion.jda.api.OnlineStatus.IDLE).count();
            int dndMembers = (int) members.stream().filter(m -> m.getOnlineStatus() == net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB).count();
            int offlineMembers = totalMembers - onlineMembers - idleMembers - dndMembers;

            // Bot configuration status
            Boolean economyEnabled = (Boolean) guildSettings.get("enableEconomy");
            Boolean levelingEnabled = (Boolean) guildSettings.get("enableLeveling");
            Boolean automodEnabled = (Boolean) guildSettings.get("enableAutomod");
            Boolean welcomeEnabled = (Boolean) guildSettings.get("welcomeEnabled");

            net.dv8tion.jda.api.EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(CustomEmojis.INFO + " Server Statistics")
                    .setDescription("Comprehensive statistics for **" + guild.getName() + "**")
                    .setThumbnail(guild.getIconUrl());

            // Server information
            embed.addField(CustomEmojis.INFO + " Server Information",
                          "**Name:** " + guild.getName() + "\n" +
                          "**Owner:** " + guild.getOwner().getAsMention() + "\n" +
                          "**Created:** " + guild.getTimeCreated().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")) + "\n" +
                          "**Verification:** " + guild.getVerificationLevel() + "\n" +
                          "**Boost Level:** " + guild.getBoostTier() + " (" + guild.getBoostCount() + " boosts)",
                          false);

            // Member statistics
            embed.addField(CustomEmojis.INFO + " Members (" + totalMembers + ")",
                          "**Humans:** " + humanMembers + "\n" +
                          "**Bots:** " + botMembers + "\n" +
                          CustomEmojis.ONLINE + " **Online:** " + onlineMembers + "\n" +
                          CustomEmojis.IDLE + " **Idle:** " + idleMembers + "\n" +
                          CustomEmojis.DND + " **DND:** " + dndMembers + "\n" +
                          CustomEmojis.OFFLINE + " **Offline:** " + offlineMembers,
                          true);

            // Channel statistics
            int textChannels = guild.getTextChannels().size();
            int voiceChannels = guild.getVoiceChannels().size();
            int categories = guild.getCategories().size();
            int totalChannels = textChannels + voiceChannels;

            embed.addField(CustomEmojis.INFO + " Channels (" + totalChannels + ")",
                          "**Text:** " + textChannels + "\n" +
                          "**Voice:** " + voiceChannels + "\n" +
                          "**Categories:** " + categories,
                          true);

            // Role statistics
            int totalRoles = guild.getRoles().size();
            embed.addField(CustomEmojis.INFO + " Roles",
                          "**Total Roles:** " + totalRoles,
                          true);

            // Bot feature status
            embed.addField(CustomEmojis.INFO + " Bot Features",
                          (economyEnabled != null && economyEnabled ? CustomEmojis.ON : CustomEmojis.OFF) + " **Economy**\n" +
                          (levelingEnabled != null && levelingEnabled ? CustomEmojis.ON : CustomEmojis.OFF) + " **Leveling**\n" +
                          (automodEnabled != null && automodEnabled ? CustomEmojis.ON : CustomEmojis.OFF) + " **Automod**\n" +
                          (welcomeEnabled != null && welcomeEnabled ? CustomEmojis.ON : CustomEmojis.OFF) + " **Welcome**",
                          true);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Error",
                "Failed to retrieve server statistics: " + e.getMessage()
            )).queue();
        }
    }

    // ========== RULES COMMAND ==========
    @SuppressWarnings("unchecked")
    private void handleRulesCommand(MessageReceivedEvent event, Map<String, String> options) {
        Member member = event.getMember();
        if (member == null) return;

        String action = options.getOrDefault("action", "display");

        // Check permissions based on action
        if (!hasRulesPermission(member, action)) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Insufficient Permissions",
                "You don't have permission to perform this action.\n" +
                "Required permission: `rules." + (action.equals("display") ? "use" : action) + "` or `rules.*`"
            )).queue();
            return;
        }

        switch (action.toLowerCase()) {
            case "display", "show", "view" -> handleRulesDisplay(event);
            case "list" -> handleRulesList(event);
            default -> {
                // For create/edit/delete, tell them to use slash command
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "Rules Management",
                    "For rule management (create/edit/delete), please use the slash command:\n" +
                    "`/rules action:" + action + "`\n\n" +
                    "**Available prefix commands:**\n" +
                    "• `!rules` or `!rules display` - Show server rules\n" +
                    "• `!rules list` - List all rules"
                )).queue();
            }
        }
    }

    private boolean hasRulesPermission(Member member, String action) {
        if (PermissionManager.hasPermission(member, "rules.*")) {
            return true;
        }

        return switch (action.toLowerCase()) {
            case "display", "show", "view" -> PermissionManager.hasPermission(member, "rules.use");
            case "list" -> PermissionManager.hasPermission(member, "rules.use") ||
                          PermissionManager.hasPermission(member, "rules.create") ||
                          PermissionManager.hasPermission(member, "rules.edit") ||
                          PermissionManager.hasPermission(member, "rules.delete");
            default -> false;
        };
    }

    @SuppressWarnings("unchecked")
    private void handleRulesDisplay(MessageReceivedEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            java.util.List<Map<String, Object>> rules = (java.util.List<Map<String, Object>>) settings.get("serverRules");

            if (rules == null || rules.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "📋 Server Rules",
                    "No rules have been set up for this server yet.\n" +
                    "Administrators can add rules using `/rules action:create`."
                )).queue();
                return;
            }

            StringBuilder rulesText = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> rule = rules.get(i);
                String title = (String) rule.get("title");
                String description = (String) rule.get("description");

                rulesText.append("**").append(i + 1).append(". ").append(title).append("**\n");
                if (description != null && !description.isEmpty()) {
                    rulesText.append(description).append("\n");
                }
                rulesText.append("\n");
            }

            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                    .setTitle("📋 " + event.getGuild().getName() + " Rules")
                    .setColor(0x3498DB)
                    .setDescription(rulesText.toString())
                    .setFooter("Please follow these rules to keep the server safe!")
                    .setThumbnail(event.getGuild().getIconUrl());

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Error",
                "Failed to retrieve rules: " + e.getMessage()
            )).queue();
        }
    }

    @SuppressWarnings("unchecked")
    private void handleRulesList(MessageReceivedEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            java.util.List<Map<String, Object>> rules = (java.util.List<Map<String, Object>>) settings.get("serverRules");

            if (rules == null || rules.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "Rules List",
                    "No rules have been set up for this server."
                )).queue();
                return;
            }

            StringBuilder listText = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                Map<String, Object> rule = rules.get(i);
                String title = (String) rule.get("title");
                listText.append("`").append(i + 1).append("` ").append(title).append("\n");
            }

            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                "Rules List (" + rules.size() + " rules)",
                listText.toString()
            )).queue();

        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Error",
                "Failed to retrieve rules list: " + e.getMessage()
            )).queue();
        }
    }

    // ========== TICKET COMMAND ==========
    private void handleTicketCommand(MessageReceivedEvent event, Map<String, String> options) {
        Member member = event.getMember();
        if (member == null) return;

        String action = options.getOrDefault("action", "create");

        switch (action.toLowerCase()) {
            case "create", "new", "open" -> handleTicketCreate(event, options);
            case "close" -> handleTicketClose(event, options);
            default -> {
                // For other ticket actions, refer to slash command
                DismissibleMessage.sendInfo(event.getChannel(),
                    "Ticket Commands",
                    "**Prefix commands available:**\n" +
                    "• `!ticket` or `!ticket create` - Create a new ticket\n" +
                    "• `!ticket close [reason]` - Close current ticket\n\n" +
                    "**For advanced ticket management, use slash commands:**\n" +
                    "• `/ticket create` - Create ticket with options\n" +
                    "• `/ticket add <user>` - Add user to ticket\n" +
                    "• `/ticket remove <user>` - Remove user from ticket\n" +
                    "• `/ticket category-create` - Create ticket category\n" +
                    "• `/ticket settings` - Configure ticket system",
                    event.getAuthor().getId()
                );
            }
        }
    }

    private void handleTicketCreate(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String reason = options.getOrDefault("reason", "Ticket created via prefix command");

            TicketService ticketService = ServerBot.getTicketService();
            if (ticketService == null) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Error",
                    "Ticket system is not available.",
                    event.getAuthor().getId()
                );
                return;
            }

            ticketService.createTicket(event.getGuild(), event.getAuthor(), reason)
                .thenAccept(result -> {
                    if (result.startsWith("T")) {
                        // Error code
                        DismissibleMessage.sendError(event.getChannel(),
                            "Error " + result,
                            "Failed to create ticket.\nUse `/error category:8` for full 8XX-series documentation.",
                            event.getAuthor().getId()
                        );
                    } else {
                        // Success - ticket ID returned
                        DismissibleMessage.sendSuccess(event.getChannel(),
                            "Ticket Created",
                            "Your ticket #" + result + " has been created successfully!",
                            event.getAuthor().getId()
                        );
                    }
                });

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Error",
                "Failed to create ticket: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    private void handleTicketClose(MessageReceivedEvent event, Map<String, String> options) {
        try {
            String reason = options.getOrDefault("reason", "Closed via prefix command");

            TicketService ticketService = ServerBot.getTicketService();
            if (ticketService == null) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Error",
                    "Ticket system is not available.",
                    event.getAuthor().getId()
                );
                return;
            }

            // Check if current channel is a ticket
            String channelId = event.getChannel().getId();
            TicketService.TicketData ticket = ticketService.getTicketByChannel(channelId);

            if (ticket == null) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Not a Ticket",
                    "This channel is not a ticket channel.\n" +
                    "Use this command inside a ticket to close it.",
                    event.getAuthor().getId()
                );
                return;
            }

            ticketService.closeTicket(event.getGuild(), ticket.getTicketId(), event.getAuthor(), reason)
                .thenAccept(result -> {
                    if (result.startsWith("T")) {
                        DismissibleMessage.sendError(event.getChannel(),
                            "Error " + result,
                            "Failed to close ticket.\nUse `/error category:8` for full 8XX-series documentation.",
                            event.getAuthor().getId()
                        );
                    }
                    // Success message is handled by TicketService
                });

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Error",
                "Failed to close ticket: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    // ========================= NEW MODERATION PREFIX COMMANDS =========================

    /**
     * Handle softban command - ban and immediately unban to delete messages
     */
    private void handleSoftbanCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.softban")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.softban` permission to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to softban: `!softban @user [reason]`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        if (targetUser.equals(event.getAuthor())) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Target", "You cannot softban yourself.",
                event.getAuthor().getId()
            );
            return;
        }

        if (targetUser.equals(event.getJDA().getSelfUser())) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Target", "I cannot softban myself.",
                event.getAuthor().getId()
            );
            return;
        }

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember != null) {
            if (!canInteractWith(moderator, targetMember)) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Cannot Softban", "You cannot softban this user due to role hierarchy.",
                    event.getAuthor().getId()
                );
                return;
            }
            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Cannot Softban", "I cannot softban this user due to role hierarchy.",
                    event.getAuthor().getId()
                );
                return;
            }
        }

        final String reason = options.getOrDefault("reason", "No reason provided");

        event.getGuild().ban(targetUser, 7, TimeUnit.DAYS)
                .reason(reason + " (Softban by " + moderator.getEffectiveName() + ")")
                .flatMap(v -> event.getGuild().unban(targetUser))
                .queue(
                    success -> {
                        try {
                            Map<String, Object> logEntry = new HashMap<>();
                            logEntry.put("type", "SOFTBAN");
                            logEntry.put("userId", targetUser.getId());
                            logEntry.put("moderatorId", moderator.getId());
                            logEntry.put("reason", reason);
                            logEntry.put("timestamp", System.currentTimeMillis());
                            ServerBot.getStorageManager().addModerationLog(event.getGuild().getId(), logEntry);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        DismissibleMessage.sendSuccess(event.getChannel(),
                            "User Softbanned",
                            "Successfully softbanned " + targetUser.getAsMention() +
                            "\n**Reason:** " + reason +
                            "\n**Moderator:** " + moderator.getAsMention(),
                            moderator.getId()
                        );
                    },
                    error -> {
                        DismissibleMessage.sendError(event.getChannel(),
                            "Softban Failed",
                            "Failed to softban user: " + error.getMessage(),
                            event.getAuthor().getId()
                        );
                    }
                );
    }

    /**
     * Handle hist command - view user moderation history
     */
    private void handleHistCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.history")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.history` permission to view user history.",
                event.getAuthor().getId()
            );
            return;
        }

        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user: `!hist @user`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        try {
            List<Map<String, Object>> moderationLogs = ServerBot.getStorageManager().getModerationLogs(event.getGuild().getId());

            List<Map<String, Object>> userHistory = moderationLogs.stream()
                .filter(log -> targetUser.getId().equals(log.get("userId")))
                .sorted((a, b) -> {
                    long timestampA = getLongValue(a.get("timestamp"));
                    long timestampB = getLongValue(b.get("timestamp"));
                    return Long.compare(timestampB, timestampA);
                })
                .limit(20)
                .toList();

            if (userHistory.isEmpty()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "No Moderation History",
                    targetUser.getAsMention() + " has no recorded moderation actions."
                )).queue();
                return;
            }

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.WARNING_COLOR)
                    .setTitle("📋 Moderation History")
                    .setDescription("Recent moderation actions for " + targetUser.getAsMention())
                    .setThumbnail(targetUser.getAvatarUrl());

            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

            int displayCount = Math.min(userHistory.size(), 10);

            for (int i = 0; i < displayCount; i++) {
                Map<String, Object> log = userHistory.get(i);
                long timestamp = getLongValue(log.get("timestamp"));
                String type = (String) log.get("type");
                String reason = (String) log.get("reason");
                String moderatorId = (String) log.get("moderatorId");

                String moderatorName = "Unknown";
                try {
                    User mod = event.getJDA().getUserById(moderatorId);
                    if (mod != null) moderatorName = mod.getName();
                } catch (Exception ignored) {}

                String dateStr = formatter.format(java.time.Instant.ofEpochMilli(timestamp));

                String fieldTitle = getHistoryActionEmoji(type) + " " + type + " (#" + (i + 1) + ")";
                String fieldValue = "**Reason:** " + reason + "\n" +
                                   "**Moderator:** " + moderatorName + "\n" +
                                   "**Date:** " + dateStr;

                embed.addField(fieldTitle, fieldValue, false);
            }

            embed.addField("📊 Statistics",
                          "**Total Records:** " + userHistory.size() + "\n" +
                          "**User:** " + targetUser.getName(), true);

            if (userHistory.size() > displayCount) {
                embed.setFooter("Showing " + displayCount + " most recent entries out of " + userHistory.size());
            }

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "History Error",
                "Failed to retrieve moderation history: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    /**
     * Helper to get emoji for moderation action type
     */
    private String getHistoryActionEmoji(String actionType) {
        if (actionType == null) return "📋";
        return switch (actionType.toUpperCase()) {
            case "WARN", "WARNING" -> "⚠️";
            case "MUTE", "TIMEOUT" -> "🔇";
            case "KICK" -> "👢";
            case "BAN" -> "🔨";
            case "SOFTBAN" -> "🔨";
            case "UNBAN" -> "🔓";
            case "UNMUTE" -> "🔊";
            default -> "📋";
        };
    }

    /**
     * Helper to safely convert timestamp values
     */
    private long getLongValue(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Double) {
            return ((Double) value).longValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else {
            return 0L;
        }
    }

    /**
     * Handle warns command - view user warnings
     */
    private void handleWarnsCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member member = event.getMember();
        String userArg = options.get("user");
        User targetUser = event.getAuthor();

        // If a user was specified, resolve them
        if (userArg != null && !userArg.trim().isEmpty()) {
            User parsed = parseUserMention(event, userArg);
            if (parsed == null) return;
            targetUser = parsed;
        }

        // Check permissions - can view own warnings or need mod perms for others
        if (!targetUser.equals(event.getAuthor()) && !PermissionManager.hasPermission(member, "mod.warns")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.warns` permission to view other users' warnings.",
                event.getAuthor().getId()
            );
            return;
        }

        try {
            List<Map<String, Object>> warnings = ServerBot.getStorageManager().getWarnings(event.getGuild().getId(), targetUser.getId());

            if (warnings.isEmpty()) {
                String target = targetUser.equals(event.getAuthor()) ? "You have" : targetUser.getName() + " has";
                event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                    "No Warnings", target + " no active warnings."
                )).queue();
                return;
            }

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.WARNING_COLOR)
                    .setTitle("⚠️ User Warnings")
                    .setDescription("Warnings for " + targetUser.getAsMention())
                    .setThumbnail(targetUser.getAvatarUrl());

            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

            int displayCount = Math.min(warnings.size(), 10);

            for (int i = 0; i < displayCount; i++) {
                Map<String, Object> warning = warnings.get(i);
                String reason = (String) warning.get("reason");
                String moderatorId = (String) warning.get("moderatorId");
                long timestamp = getLongValue(warning.get("timestamp"));

                String moderatorName = "*unknown*";
                try {
                    User mod = event.getJDA().getUserById(moderatorId);
                    if (mod != null) moderatorName = mod.getName();
                } catch (Exception ignored) {}

                String dateStr = formatter.format(java.time.Instant.ofEpochMilli(timestamp));

                String fieldTitle = "Warn #" + (i + 1);
                String fieldValue = "**Reason:** " + reason + "\n" +
                                   "**Moderator:** " + moderatorName + "\n" +
                                   "**Date:** " + dateStr;

                embed.addField(fieldTitle, fieldValue, false);
            }

            embed.addField("Stats",
                          "**Total Active Warnings:** " + warnings.size(), true);

            if (warnings.size() > displayCount) {
                embed.setFooter("Showing " + displayCount + " most recent warnings out of " + warnings.size());
            }

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Storage Error",
                "Failed to retrieve warnings: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    /**
     * Handle lockdown command - lock/unlock a channel
     */
    private void handleLockdownCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "mod.lockdown")) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Permissions", "You need the `mod.lockdown` permission to use this command.",
                event.getAuthor().getId()
            );
            return;
        }

        // Determine target channel
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel targetChannel;
        String channelArg = options.get("channel");

        if (channelArg != null && !channelArg.trim().isEmpty()) {
            // Parse channel mention: <#123456>
            String channelId = channelArg.replaceAll("[<#>]", "");
            try {
                targetChannel = event.getGuild().getTextChannelById(channelId);
                if (targetChannel == null) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Invalid Channel", "Could not find that channel.",
                        event.getAuthor().getId()
                    );
                    return;
                }
            } catch (Exception e) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Invalid Channel", "Please mention a valid channel: `!lockdown [#channel]`",
                    event.getAuthor().getId()
                );
                return;
            }
        } else {
            // Default to current channel
            try {
                targetChannel = event.getChannel().asTextChannel();
            } catch (Exception e) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Invalid Channel", "This command can only be used in text channels.",
                    event.getAuthor().getId()
                );
                return;
            }
        }

        // Check if bot has permissions
        if (!event.getGuild().getSelfMember().hasPermission(targetChannel, net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing Permissions", "I don't have permission to manage the channel " + targetChannel.getAsMention(),
                event.getAuthor().getId()
            );
            return;
        }

        Role everyoneRole = event.getGuild().getPublicRole();
        net.dv8tion.jda.api.entities.PermissionOverride everyoneOverride = targetChannel.getPermissionOverride(everyoneRole);

        boolean isLocked = everyoneOverride != null &&
                everyoneOverride.getDenied().contains(net.dv8tion.jda.api.Permission.MESSAGE_SEND);

        if (isLocked) {
            // Unlock the channel
            unlockChannelPrefix(event, targetChannel, everyoneRole, everyoneOverride);
        } else {
            // Lock the channel
            lockChannelPrefix(event, targetChannel, everyoneRole);
        }
    }

    private void lockChannelPrefix(MessageReceivedEvent event, net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel,
                                   Role everyoneRole) {
        try {
            channel.getManager()
                    .putRolePermissionOverride(everyoneRole.getIdLong(),
                            java.util.EnumSet.noneOf(net.dv8tion.jda.api.Permission.class),
                            java.util.EnumSet.of(net.dv8tion.jda.api.Permission.MESSAGE_SEND,
                                                 net.dv8tion.jda.api.Permission.MESSAGE_ADD_REACTION))
                    .queue(
                        success -> {
                            channel.sendMessageEmbeds(EmbedUtils.createWarningEmbed(
                                "🔒 Channel Locked",
                                "This channel has been locked by " + event.getAuthor().getAsMention() + ".\n" +
                                "Only moderators can send messages during lockdown."
                            )).queue();

                            if (!channel.getId().equals(event.getChannel().getId())) {
                                event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                    "Channel Locked", channel.getAsMention() + " has been locked."
                                )).queue();
                            }
                        },
                        error -> DismissibleMessage.sendError(event.getChannel(),
                            "Lock Failed", "Failed to lock channel: " + error.getMessage(),
                            event.getAuthor().getId()
                        )
                    );
        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Lock Error", "An error occurred while locking the channel: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    private void unlockChannelPrefix(MessageReceivedEvent event, net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel,
                                     Role everyoneRole, net.dv8tion.jda.api.entities.PermissionOverride override) {
        try {
            if (override != null) {
                java.util.EnumSet<net.dv8tion.jda.api.Permission> newDenied = java.util.EnumSet.copyOf(override.getDenied());
                newDenied.remove(net.dv8tion.jda.api.Permission.MESSAGE_SEND);
                newDenied.remove(net.dv8tion.jda.api.Permission.MESSAGE_ADD_REACTION);

                if (newDenied.isEmpty() && override.getAllowed().isEmpty()) {
                    override.delete().queue(
                        success -> handleUnlockSuccessPrefix(event, channel),
                        error -> DismissibleMessage.sendError(event.getChannel(),
                            "Unlock Failed", "Failed to unlock channel: " + error.getMessage(),
                            event.getAuthor().getId()
                        )
                    );
                } else {
                    channel.getManager()
                            .putRolePermissionOverride(everyoneRole.getIdLong(),
                                    override.getAllowed(), newDenied)
                            .queue(
                                success -> handleUnlockSuccessPrefix(event, channel),
                                error -> DismissibleMessage.sendError(event.getChannel(),
                                    "Unlock Failed", "Failed to unlock channel: " + error.getMessage(),
                                    event.getAuthor().getId()
                                )
                            );
                }
            } else {
                handleUnlockSuccessPrefix(event, channel);
            }
        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Unlock Error", "An error occurred while unlocking the channel: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    private void handleUnlockSuccessPrefix(MessageReceivedEvent event, net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel) {
        channel.sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "🔓 Channel Unlocked",
            "This channel has been unlocked by " + event.getAuthor().getAsMention() + ".\n" +
            "Normal conversation can now resume."
        )).queue();

        if (!channel.getId().equals(event.getChannel().getId())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "Channel Unlocked", channel.getAsMention() + " has been unlocked."
            )).queue();
        }
    }

    // ========================= NEW ECONOMY PREFIX COMMANDS =========================

    /**
     * Handle rob command - attempt to steal points from another user
     */
    private void handleRobCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        // Check if economy is enabled
        try {
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(event.getGuild().getId());
            Boolean economyEnabled = (Boolean) guildSettings.get("enableEconomy");
            if (economyEnabled != null && !economyEnabled) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Economy Disabled", "The economy system is disabled in this server.",
                    event.getAuthor().getId()
                );
                return;
            }
        } catch (Exception ignored) {}

        String userArg = options.get("user");
        if (userArg == null || userArg.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing User", "Please mention a user to rob: `!rob @user`",
                event.getAuthor().getId()
            );
            return;
        }

        User targetUser = parseUserMention(event, userArg);
        if (targetUser == null) return;

        User robber = event.getAuthor();

        if (targetUser.isBot()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Target", "You cannot rob bots.",
                event.getAuthor().getId()
            );
            return;
        }

        if (robber.equals(targetUser)) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Target", "You cannot rob yourself.",
                event.getAuthor().getId()
            );
            return;
        }

        try {
            String guildId = event.getGuild().getId();
            long robberBalance = ServerBot.getStorageManager().getBalance(guildId, robber.getId());

            if (robberBalance < 100) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Not Enough Money", "You need at least 100 points to attempt a robbery.",
                    event.getAuthor().getId()
                );
                return;
            }

            long targetBalance = ServerBot.getStorageManager().getBalance(guildId, targetUser.getId());

            if (targetBalance <= 0) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "No Money", targetUser.getName() + " has no points to rob!"
                )).queue();
                return;
            }

            Random random = new Random();
            boolean success = random.nextDouble() < 0.35;

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder();

            if (success) {
                long maxSteal = Math.min(targetBalance / 4, 500);
                long minSteal = Math.max(1, targetBalance / 10);
                long stolenAmount = minSteal + (maxSteal > minSteal ? random.nextLong(maxSteal - minSteal + 1) : 0);

                ServerBot.getStorageManager().setBalance(guildId, robber.getId(), robberBalance + stolenAmount);
                ServerBot.getStorageManager().setBalance(guildId, targetUser.getId(), targetBalance - stolenAmount);

                embed.setColor(EmbedUtils.SUCCESS_COLOR)
                        .setTitle("🎯 Robbery Successful!")
                        .setDescription("**" + robber.getName() + "** successfully robbed **" + targetUser.getName() + "**!")
                        .addField("💰 Stolen", stolenAmount + " points", true)
                        .addField("🏃 Your Balance", (robberBalance + stolenAmount) + " points", true)
                        .addField("😢 Victim's Balance", (targetBalance - stolenAmount) + " points", true);
            } else {
                long penalty = Math.min(robberBalance / 10, 100);
                if (penalty > 0) {
                    ServerBot.getStorageManager().setBalance(guildId, robber.getId(), robberBalance - penalty);
                }

                embed.setColor(EmbedUtils.ERROR_COLOR)
                        .setTitle("🚨 Robbery Failed!")
                        .setDescription("**" + robber.getName() + "** tried to rob **" + targetUser.getName() + "** but got caught!")
                        .addField("💸 Fine", penalty + " points", true)
                        .addField("💰 Your Balance", (robberBalance - penalty) + " points", true)
                        .addField("😤 Victim", targetUser.getName() + " kept their money safe!", true);
            }

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Robbery Failed", "An error occurred during the robbery attempt.",
                event.getAuthor().getId()
            );
        }
    }

    /**
     * Handle blackjack command - play a game of blackjack
     * Note: The interactive button-based blackjack only works with slash commands.
     * This prefix version provides a simplified auto-play experience.
     */
    private void handleBlackjackCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        String betStr = options.get("bet");
        if (betStr == null || betStr.trim().isEmpty()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Missing Bet", "Please specify a bet amount: `!blackjack <amount>`\n" +
                "**Tip:** For the full interactive experience with hit/stand buttons, use `/blackjack`.",
                event.getAuthor().getId()
            );
            return;
        }

        long bet;
        try {
            bet = Long.parseLong(betStr);
        } catch (NumberFormatException e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Amount", "Please enter a valid number.",
                event.getAuthor().getId()
            );
            return;
        }

        if (bet <= 0) {
            DismissibleMessage.sendError(event.getChannel(),
                "Invalid Amount", "You must bet at least 1 coin.",
                event.getAuthor().getId()
            );
            return;
        }

        if (bet > 10000) {
            DismissibleMessage.sendError(event.getChannel(),
                "Bet Too High", "You cannot bet more than 10,000 coins at once.",
                event.getAuthor().getId()
            );
            return;
        }

        String guildId = event.getGuild().getId();
        String userId = event.getAuthor().getId();
        long balance = ServerBot.getStorageManager().getBalance(guildId, userId);

        if (balance < bet) {
            DismissibleMessage.sendError(event.getChannel(),
                "Insufficient Funds",
                String.format("You only have %,d coins but tried to bet %,d coins.", balance, bet),
                event.getAuthor().getId()
            );
            return;
        }

        // Simple auto-play blackjack for prefix
        Random random = new Random();
        int playerTotal = random.nextInt(11) + 12; // 12-22
        int dealerTotal = random.nextInt(11) + 12; // 12-22

        boolean playerBust = playerTotal > 21;
        boolean dealerBust = dealerTotal > 21;

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder();
        long winnings = 0;

        if (playerBust) {
            // Player busts
            embed.setColor(EmbedUtils.ERROR_COLOR)
                 .setTitle("🃏 Blackjack - Bust!")
                 .setDescription("You went over 21!")
                 .addField("Your Hand", String.valueOf(playerTotal), true)
                 .addField("Dealer Hand", String.valueOf(dealerTotal), true);
            // Loss - bet already deducted below
        } else if (dealerBust || playerTotal > dealerTotal) {
            // Player wins
            winnings = bet * 2;
            embed.setColor(EmbedUtils.SUCCESS_COLOR)
                 .setTitle("🃏 Blackjack - You Win! 🎉")
                 .setDescription(dealerBust ? "Dealer busted!" : "You beat the dealer!")
                 .addField("Your Hand", String.valueOf(playerTotal), true)
                 .addField("Dealer Hand", String.valueOf(dealerTotal), true)
                 .addField("Winnings", String.format("+%,d coins", winnings - bet), true);
        } else if (playerTotal == dealerTotal) {
            // Push
            winnings = bet; // Return bet
            embed.setColor(EmbedUtils.WARNING_COLOR)
                 .setTitle("🃏 Blackjack - Push!")
                 .setDescription("It's a tie! Your bet has been returned.")
                 .addField("Your Hand", String.valueOf(playerTotal), true)
                 .addField("Dealer Hand", String.valueOf(dealerTotal), true);
        } else {
            // Dealer wins
            embed.setColor(EmbedUtils.ERROR_COLOR)
                 .setTitle("🃏 Blackjack - Dealer Wins!")
                 .setDescription("The dealer beat you!")
                 .addField("Your Hand", String.valueOf(playerTotal), true)
                 .addField("Dealer Hand", String.valueOf(dealerTotal), true);
        }

        long newBalance = balance - bet + winnings;
        ServerBot.getStorageManager().setBalance(guildId, userId, newBalance);
        embed.addField("💰 Balance", String.format("%,d coins", newBalance), false);
        embed.setFooter("For the full interactive experience, use /blackjack");

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Handle bank command - manage banking system
     */
    private void handleBankCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        String setting = options.get("setting");
        if (setting == null || setting.trim().isEmpty()) {
            // Show bank help
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🏦 Bank Command Help")
                    .setDescription("Manage banking system, user balances, and loan settings")
                    .setColor(0x00AA00)
                    .addField("**Usage**",
                        "`!bank balance [view/set/add/subtract] [@user] [amount]`\n" +
                        "`!bank maxloan <amount>` — Set max loan amount\n" +
                        "`!bank minloan <amount>` — Set min loan amount\n" +
                        "`!bank autocollect [enable/disable]` — Toggle auto-collect", false)
                    .addField("**Examples**",
                        "`!bank balance` — View your balance\n" +
                        "`!bank balance view @user` — View user's balance\n" +
                        "`!bank balance add @user 100` — Give 100 points\n" +
                        "`!bank maxloan 1000` — Set max loan to 1000", false);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
            return;
        }

        String guildId = event.getGuild().getId();
        Member member = event.getMember();

        switch (setting.toLowerCase()) {
            case "balance" -> {
                String action = options.getOrDefault("action", "view");
                User targetUser = event.getAuthor();

                String userArg = options.get("user");
                if (userArg != null && !userArg.trim().isEmpty()) {
                    User parsed = parseUserMention(event, userArg);
                    if (parsed == null) return;
                    targetUser = parsed;
                }

                // Check permissions for viewing other users
                if (!targetUser.getId().equals(event.getAuthor().getId()) &&
                    !PermissionManager.hasPermission(member, "economy.admin.view")) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Insufficient Permissions",
                        "You need the `economy.admin.view` permission to view other users' balances.",
                        event.getAuthor().getId()
                    );
                    return;
                }

                try {
                    long currentBalance = ServerBot.getStorageManager().getBalance(guildId, targetUser.getId());
                    String amountStr = options.get("amount");
                    Long amount = null;
                    if (amountStr != null) {
                        try { amount = Long.parseLong(amountStr); } catch (NumberFormatException ignored) {}
                    }

                    switch (action.toLowerCase()) {
                        case "view" -> {
                            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                "💰 Bank Balance",
                                "**User:** " + targetUser.getAsMention() + "\n" +
                                "**Balance:** " + currentBalance + " points"
                            )).queue();
                        }
                        case "set" -> {
                            if (amount == null) {
                                DismissibleMessage.sendError(event.getChannel(),
                                    "Missing Amount", "Please specify the amount: `!bank balance set @user <amount>`",
                                    event.getAuthor().getId()
                                );
                                return;
                            }
                            ServerBot.getStorageManager().setBalance(guildId, targetUser.getId(), amount);
                            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                "💰 Balance Updated",
                                "**User:** " + targetUser.getAsMention() + "\n" +
                                "**New Balance:** " + amount + " points\n" +
                                "**Previous Balance:** " + currentBalance + " points"
                            )).queue();
                        }
                        case "add" -> {
                            if (amount == null) {
                                DismissibleMessage.sendError(event.getChannel(),
                                    "Missing Amount", "Please specify the amount: `!bank balance add @user <amount>`",
                                    event.getAuthor().getId()
                                );
                                return;
                            }
                            ServerBot.getStorageManager().addBalance(guildId, targetUser.getId(), amount);
                            long newBal = ServerBot.getStorageManager().getBalance(guildId, targetUser.getId());
                            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                "💰 Balance Updated",
                                "**User:** " + targetUser.getAsMention() + "\n" +
                                "**Added:** " + amount + " points\n" +
                                "**New Balance:** " + newBal + " points"
                            )).queue();
                        }
                        case "subtract" -> {
                            if (amount == null) {
                                DismissibleMessage.sendError(event.getChannel(),
                                    "Missing Amount", "Please specify the amount: `!bank balance subtract @user <amount>`",
                                    event.getAuthor().getId()
                                );
                                return;
                            }
                            long newBalance = Math.max(0, currentBalance - amount);
                            ServerBot.getStorageManager().setBalance(guildId, targetUser.getId(), newBalance);
                            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                                "💰 Balance Updated",
                                "**User:** " + targetUser.getAsMention() + "\n" +
                                "**Subtracted:** " + amount + " points\n" +
                                "**New Balance:** " + newBalance + " points"
                            )).queue();
                        }
                        default -> DismissibleMessage.sendError(event.getChannel(),
                            "Invalid Action", "Valid actions: `view`, `set`, `add`, `subtract`",
                            event.getAuthor().getId()
                        );
                    }
                } catch (Exception e) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Balance Error", "Failed to handle balance operation: " + e.getMessage(),
                        event.getAuthor().getId()
                    );
                }
            }
            case "maxloan" -> {
                if (!PermissionManager.hasPermission(member, "economy.admin.config")) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Insufficient Permissions", "You need the `economy.admin.config` permission.",
                        event.getAuthor().getId()
                    );
                    return;
                }
                String amountStr = options.get("amount");
                if (amountStr == null) {
                    // Also check action field as it might hold the amount in positional args
                    amountStr = options.get("action");
                }
                if (amountStr == null) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Missing Amount", "Usage: `!bank maxloan <amount>`",
                        event.getAuthor().getId()
                    );
                    return;
                }
                try {
                    long amount = Long.parseLong(amountStr);
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "bankMaxLoan", amount);
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "🏦 Max Loan Updated",
                        "**Maximum loan amount set to:** " + amount + " points"
                    )).queue();
                } catch (NumberFormatException e) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Invalid Amount", "Please enter a valid number.",
                        event.getAuthor().getId()
                    );
                } catch (Exception e) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Update Failed", "Failed to update max loan: " + e.getMessage(),
                        event.getAuthor().getId()
                    );
                }
            }
            case "minloan" -> {
                if (!PermissionManager.hasPermission(member, "economy.admin.config")) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Insufficient Permissions", "You need the `economy.admin.config` permission.",
                        event.getAuthor().getId()
                    );
                    return;
                }
                String amountStr = options.get("amount");
                if (amountStr == null) amountStr = options.get("action");
                if (amountStr == null) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Missing Amount", "Usage: `!bank minloan <amount>`",
                        event.getAuthor().getId()
                    );
                    return;
                }
                try {
                    long amount = Long.parseLong(amountStr);
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "bankMinLoan", amount);
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "🏦 Min Loan Updated",
                        "**Minimum loan amount set to:** " + amount + " points"
                    )).queue();
                } catch (NumberFormatException e) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Invalid Amount", "Please enter a valid number.",
                        event.getAuthor().getId()
                    );
                } catch (Exception e) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Update Failed", "Failed to update min loan: " + e.getMessage(),
                        event.getAuthor().getId()
                    );
                }
            }
            case "autocollect" -> {
                if (!PermissionManager.hasPermission(member, "economy.admin.config")) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Insufficient Permissions", "You need the `economy.admin.config` permission.",
                        event.getAuthor().getId()
                    );
                    return;
                }
                String action = options.get("action");
                if (action == null || action.trim().isEmpty()) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Missing Action", "Usage: `!bank autocollect <enable/disable>`",
                        event.getAuthor().getId()
                    );
                    return;
                }
                boolean enabled = action.equalsIgnoreCase("enable");
                try {
                    ServerBot.getStorageManager().updateGuildSettings(guildId, "bankAutoCollect", enabled);
                    event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                        "🏦 Auto-Collect " + (enabled ? "Enabled" : "Disabled"),
                        "Auto-collect is now **" + (enabled ? "enabled" : "disabled") + "**."
                    )).queue();
                } catch (Exception e) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Update Failed", "Failed to update auto-collect: " + e.getMessage(),
                        event.getAuthor().getId()
                    );
                }
            }
            default -> DismissibleMessage.sendError(event.getChannel(),
                "Invalid Setting",
                "Valid settings: `balance`, `maxloan`, `minloan`, `autocollect`\nUse `!bank` for help.",
                event.getAuthor().getId()
            );
        }
    }

    // ========================= NEW UTILITY PREFIX COMMANDS =========================

    /**
     * Handle embed command - create custom embeds
     */
    private void handleEmbedCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            DismissibleMessage.sendError(event.getChannel(),
                "Guild Only", "This command can only be used in servers.",
                event.getAuthor().getId()
            );
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "mod.embed")) {
            // Fall back to checking mod perms
            try {
                if (!member.hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                    DismissibleMessage.sendError(event.getChannel(),
                        "Insufficient Permissions", "You need moderation permissions to create embeds.",
                        event.getAuthor().getId()
                    );
                    return;
                }
            } catch (Exception ignored) {
                DismissibleMessage.sendError(event.getChannel(),
                    "Insufficient Permissions", "You need moderation permissions to create embeds.",
                    event.getAuthor().getId()
                );
                return;
            }
        }

        String title = options.get("title");
        String description = options.get("description");
        String colorHex = options.getOrDefault("color", "#0099FF");

        if ((title == null || title.trim().isEmpty()) && (description == null || description.trim().isEmpty())) {
            EmbedBuilder help = new EmbedBuilder()
                    .setTitle("📝 Embed Command Help")
                    .setDescription("Create custom embeds")
                    .setColor(0x5865F2)
                    .addField("Usage",
                        "`!embed Title | Description` — Simple embed\n" +
                        "`!embed Title | Description -color #FF0000` — Colored embed\n" +
                        "`!embed -title MyTitle -description MyDesc -color #00FF00` — Flag syntax", false)
                    .addField("Examples",
                        "`!embed Welcome | Hello everyone!`\n" +
                        "`!embed Announcement | Check this out -color #FF0000`", false);
            event.getChannel().sendMessageEmbeds(help.build()).queue();
            return;
        }

        try {
            java.awt.Color embedColor = parseHexColor(colorHex);
            EmbedBuilder embed = new EmbedBuilder();

            if (title != null && !title.trim().isEmpty()) {
                embed.setTitle(title.trim());
            }
            if (description != null && !description.trim().isEmpty()) {
                embed.setDescription(description.replace("\\n", "\n").trim());
            }

            embed.setColor(embedColor);
            embed.setFooter("Created by " + event.getAuthor().getEffectiveName(),
                           event.getAuthor().getAvatarUrl());

            event.getChannel().sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            DismissibleMessage.sendError(event.getChannel(),
                "Embed Creation Failed", "Failed to create embed: " + e.getMessage(),
                event.getAuthor().getId()
            );
        }
    }

    /**
     * Parse a hex color string to a Color object
     */
    private java.awt.Color parseHexColor(String hex) {
        if (hex == null || hex.isEmpty()) return new java.awt.Color(0x0099FF);
        hex = hex.replace("#", "");
        try {
            return new java.awt.Color(Integer.parseInt(hex, 16));
        } catch (NumberFormatException e) {
            // Try named colors
            return switch (hex.toLowerCase()) {
                case "red" -> java.awt.Color.RED;
                case "green" -> java.awt.Color.GREEN;
                case "blue" -> java.awt.Color.BLUE;
                case "yellow" -> java.awt.Color.YELLOW;
                case "orange" -> java.awt.Color.ORANGE;
                case "pink" -> java.awt.Color.PINK;
                case "cyan" -> java.awt.Color.CYAN;
                case "purple" -> new java.awt.Color(0x9B59B6);
                default -> new java.awt.Color(0x0099FF);
            };
        }
    }

    /**
     * Handle dadjoke command - get a random dad joke
     */
    private void handleDadJokeCommand(MessageReceivedEvent event) {
        String[] dadJokes = {
            "Why don't scientists trust atoms? Because they make up everything!",
            "I told my wife she was drawing her eyebrows too high. She looked surprised.",
            "What do you call a fake noodle? An impasta!",
            "Why did the scarecrow win an award? He was outstanding in his field!",
            "I used to hate facial hair, but then it grew on me.",
            "What do you call a bear with no teeth? A gummy bear!",
            "Why don't eggs tell jokes? They'd crack each other up!",
            "What's the best thing about Switzerland? I don't know, but the flag is a big plus.",
            "I invented a new word: Plagiarism!",
            "Did you hear about the mathematician who's afraid of negative numbers? He'll stop at nothing to avoid them!",
            "Why did the coffee file a police report? It got mugged!",
            "What did the ocean say to the beach? Nothing, it just waved.",
            "Why don't skeletons fight each other? They don't have the guts.",
            "What do you call a sleeping bull? A bulldozer!",
            "I'm reading a book about anti-gravity. It's impossible to put down!",
            "Why did the bicycle fall over? Because it was two tired!",
            "What do you call a dinosaur that crashes his car? Tyrannosaurus Wrecks!",
            "Want to hear a joke about construction? I'm still working on it.",
            "What do you call a factory that makes okay products? A satisfactory!",
            "What did the janitor say when he jumped out of the closet? Supplies!",
            "I don't trust stairs. They're always up to something.",
            "What do you call a pig that does karate? A pork chop!",
            "Why did the golfer wear two pairs of pants? In case he got a hole in one!",
            "How does a penguin build its house? Igloos it together!",
            "What did one wall say to the other wall? I'll meet you at the corner!"
        };

        String joke = dadJokes[new Random().nextInt(dadJokes.length)];
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Dad Joke 🔥", joke
        )).queue();
    }

    /**
     * Handle joke command - get a random joke
     */
    private void handleJokeCommand(MessageReceivedEvent event) {
        String[] jokes = {
            "Why did the programmer quit his job? He didn't get arrays!",
            "A SQL query goes into a bar, walks up to two tables and asks: 'Can I join you?'",
            "Why do Java developers wear glasses? Because they can't C#!",
            "How many programmers does it take to change a light bulb? None, that's a hardware problem!",
            "Why did the developer go broke? Because he used up all his cache!",
            "What's a computer's favorite snack? Microchips!",
            "Why do programmers prefer dark mode? Because light attracts bugs!",
            "What do you call 8 hobbits? A hobbyte!",
            "Why did the smartphone need glasses? It lost all its contacts!",
            "What do you call a fish wearing a bowtie? Sofishticated!",
            "What do you call a sleeping bull? A bulldozer!",
            "Why did the coffee file a police report? It got mugged!",
            "What's the best way to communicate with a fish? Drop it a line!",
            "Why did the bicycle fall over? Because it was two tired!",
            "What do you call a belt made of watches? A waist of time!",
            "What's orange and sounds like a parrot? A carrot!",
            "Why did the stadium get hot after the game? All of the fans left!",
            "What do you call a dog magician? A labracadabrador!",
            "Why did the picture go to jail? Because it was framed!",
            "What did the big flower say to the little flower? Hi, bud!",
            "Why don't skeletons fight each other? They don't have the guts!",
            "What did the ocean say to the beach? Nothing, it just waved!",
            "I told my wife she was drawing her eyebrows too high. She looked surprised!",
            "I'm reading a book about anti-gravity. It's impossible to put down!",
            "Want to hear a joke about construction? I'm still working on it!"
        };

        String joke = jokes[new Random().nextInt(jokes.length)];
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "😄 Random Joke", joke
        )).queue();
    }

    // ==================== Owner-Only Commands ====================

    /**
     * Handle !statusmsg [message] - Set or clear bot custom status message
     * Owner only - non-owners get "Unknown Command" from the main flow since these
     * commands are hidden. But if they somehow reach here, we block them.
     */
    private void handleStatusMsgCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!PermissionUtils.isBotOwner(event.getAuthor())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        String message = options.get("message");

        if (message == null || message.isBlank()) {
            try {
                event.getJDA().getPresence().setActivity(null);
                event.getChannel().sendMessageEmbeds(
                    EmbedUtils.createSuccessEmbed("Status Cleared", "Bot status has been cleared.")
                ).queue();
            } catch (Exception e) {
                event.getChannel().sendMessageEmbeds(
                    EmbedUtils.createErrorEmbed("Status Clear Failed", "Failed to clear bot status: " + e.getMessage())
                ).queue();
            }
        } else {
            try {
                net.dv8tion.jda.api.entities.Activity activity = net.dv8tion.jda.api.entities.Activity.customStatus(message);
                event.getJDA().getPresence().setActivity(activity);
                event.getChannel().sendMessageEmbeds(
                    EmbedUtils.createSuccessEmbed("Status Updated", "Bot status has been set to:\n**" + message + "**")
                ).queue();
            } catch (Exception e) {
                event.getChannel().sendMessageEmbeds(
                    EmbedUtils.createErrorEmbed("Status Update Failed", "Failed to update bot status: " + e.getMessage())
                ).queue();
            }
        }
    }

    /**
     * Handle !restart - Restart the bot
     * Owner only
     */
    private void handleRestartCommand(MessageReceivedEvent event) {
        if (!PermissionUtils.isBotOwner(event.getAuthor())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
            "\uD83D\uDD04 Restarting Bot",
            "The bot is restarting... This may take a few moments."
        )).queue(message -> {
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    System.out.println("Bot restart initiated by owner: " + event.getAuthor().getName());
                    ServerBot.getStorageManager().saveAllData();
                    event.getJDA().shutdown();
                    System.exit(0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    System.err.println("Restart interrupted: " + e.getMessage());
                }
            }).start();
        });
    }

    /**
     * Handle !rpc <action> [type] [text] - Set bot rich presence (activity)
     * Owner only
     */
    private void handleRpcCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!PermissionUtils.isBotOwner(event.getAuthor())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        String action = options.get("action");
        if (action == null) {
            event.getChannel().sendMessageEmbeds(
                EmbedUtils.createErrorEmbed("Missing Action",
                    "Usage: `!rpc <set|remove> [type] [text]`\n" +
                    "Types: `playing`, `watching`, `listening`, `streaming`, `competing`")
            ).queue();
            return;
        }

        switch (action.toLowerCase()) {
            case "set":
                String presenceText = options.get("text");
                String activityType = options.getOrDefault("type", "playing");

                if (presenceText == null || presenceText.trim().isEmpty()) {
                    event.getChannel().sendMessageEmbeds(
                        EmbedUtils.createErrorEmbed("Missing Text", "Please provide presence text when using `set`.\n" +
                        "Usage: `!rpc set [type] <text>`")
                    ).queue();
                    return;
                }

                try {
                    net.dv8tion.jda.api.entities.Activity activity = switch (activityType.toLowerCase()) {
                        case "playing" -> net.dv8tion.jda.api.entities.Activity.playing(presenceText);
                        case "watching" -> net.dv8tion.jda.api.entities.Activity.watching(presenceText);
                        case "listening" -> net.dv8tion.jda.api.entities.Activity.listening(presenceText);
                        case "streaming" -> net.dv8tion.jda.api.entities.Activity.streaming(presenceText, "https://twitch.tv/placeholder");
                        case "competing" -> net.dv8tion.jda.api.entities.Activity.competing(presenceText);
                        default -> net.dv8tion.jda.api.entities.Activity.playing(presenceText);
                    };

                    event.getJDA().getPresence().setActivity(activity);
                    event.getChannel().sendMessageEmbeds(
                        EmbedUtils.createSuccessEmbed("Presence Updated",
                            "Bot presence has been set to: **" + activityType.substring(0, 1).toUpperCase() + activityType.substring(1) + " " + presenceText + "**")
                    ).queue();
                } catch (Exception e) {
                    event.getChannel().sendMessageEmbeds(
                        EmbedUtils.createErrorEmbed("Presence Update Failed", "Failed to update bot presence: " + e.getMessage())
                    ).queue();
                }
                break;

            case "remove":
            case "clear":
                try {
                    event.getJDA().getPresence().setActivity(null);
                    event.getChannel().sendMessageEmbeds(
                        EmbedUtils.createSuccessEmbed("Presence Cleared", "Bot presence has been cleared.")
                    ).queue();
                } catch (Exception e) {
                    event.getChannel().sendMessageEmbeds(
                        EmbedUtils.createErrorEmbed("Presence Clear Failed", "Failed to clear bot presence: " + e.getMessage())
                    ).queue();
                }
                break;

            default:
                event.getChannel().sendMessageEmbeds(
                    EmbedUtils.createErrorEmbed("Invalid Action", "Available actions: `set`, `remove`")
                ).queue();
                break;
        }
    }

    /**
     * Handle !appearance <status> - Set bot online status
     * Owner only
     */
    private void handleAppearanceCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!PermissionUtils.isBotOwner(event.getAuthor())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        String statusType = options.get("status");
        if (statusType == null) {
            event.getChannel().sendMessageEmbeds(
                EmbedUtils.createErrorEmbed("Missing Status", "Usage: `!appearance <online|dnd|idle|offline>`")
            ).queue();
            return;
        }

        net.dv8tion.jda.api.OnlineStatus status;
        String statusText;

        switch (statusType.toLowerCase()) {
            case "online":
                status = net.dv8tion.jda.api.OnlineStatus.ONLINE;
                statusText = "Online " + CustomEmojis.ONLINE;
                break;
            case "dnd":
            case "do-not-disturb":
                status = net.dv8tion.jda.api.OnlineStatus.DO_NOT_DISTURB;
                statusText = "Do Not Disturb " + CustomEmojis.DND;
                break;
            case "idle":
            case "away":
                status = net.dv8tion.jda.api.OnlineStatus.IDLE;
                statusText = "Idle " + CustomEmojis.IDLE;
                break;
            case "offline":
                status = net.dv8tion.jda.api.OnlineStatus.OFFLINE;
                statusText = "Offline " + CustomEmojis.OFFLINE;
                break;
            default:
                event.getChannel().sendMessageEmbeds(
                    EmbedUtils.createErrorEmbed("Invalid Status", "Valid statuses are: `online`, `dnd`, `idle`, `offline`")
                ).queue();
                return;
        }

        try {
            event.getJDA().getPresence().setStatus(status);
            event.getChannel().sendMessageEmbeds(
                EmbedUtils.createSuccessEmbed("Appearance Updated", "Bot appearance has been set to: **" + statusText + "**")
            ).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(
                EmbedUtils.createErrorEmbed("Appearance Update Failed", "Failed to update bot appearance: " + e.getMessage())
            ).queue();
        }
    }

    /**
     * Handle !backup <action> [timestamp] - Manage server configuration backups
     * Requires admin.backup permission
     */
    private void handleBackupCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionManager.hasPermission(member, "admin.backup")) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        String action = options.getOrDefault("action", "list");
        String guildId = event.getGuild().getId();

        switch (action.toLowerCase()) {
            case "create":
                handleBackupCreate(event, guildId);
                break;
            case "list":
                handleBackupList(event, guildId);
                break;
            case "info":
                handleBackupInfo(event, guildId, options.get("timestamp"));
                break;
            case "restore":
                handleBackupRestore(event, guildId, options.get("timestamp"));
                break;
            default:
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Action",
                    "Usage: `!backup <create|list|info|restore> [timestamp]`"
                )).queue();
                break;
        }
    }

    private void handleBackupCreate(MessageReceivedEvent event, String guildId) {
        try {
            String guildName = event.getGuild().getName();
            String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String backupName = "backup_" + guildName.replaceAll("[^a-zA-Z0-9]", "_") + "_" + timestamp;

            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            StringBuilder backupData = new StringBuilder();
            backupData.append("# Server Bot Configuration Backup\n");
            backupData.append("# Server: ").append(guildName).append(" (").append(guildId).append(")\n");
            backupData.append("# Created: ").append(java.time.LocalDateTime.now()).append("\n");
            backupData.append("# DO NOT EDIT THIS FILE MANUALLY\n\n");

            int settingsCount = 0;
            for (Map.Entry<String, Object> entry : guildSettings.entrySet()) {
                backupData.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                settingsCount++;
            }

            ServerBot.getStorageManager().updateGuildSettings(guildId, "backup_" + timestamp, backupData.toString());

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle("Backup Created Successfully")
                    .setDescription("Server configuration has been backed up!")
                    .addField("Backup Details",
                              "**Name:** " + backupName + "\n" +
                              "**Settings Saved:** " + settingsCount + "\n" +
                              "**Created:** " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")),
                              false)
                    .addField("Usage",
                              "Use `!backup list` to view all backups\n" +
                              "Use `!backup restore <timestamp>` to restore from this backup",
                              false);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Backup Failed", "Failed to create backup: " + e.getMessage()
            )).queue();
        }
    }

    private void handleBackupList(MessageReceivedEvent event, String guildId) {
        try {
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);

            StringBuilder backupList = new StringBuilder();
            int backupCount = 0;

            for (Map.Entry<String, Object> entry : guildSettings.entrySet()) {
                if (entry.getKey().startsWith("backup_")) {
                    String timestamp = entry.getKey().substring(7);
                    try {
                        java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timestamp,
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                        String formattedDate = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
                        backupList.append("**").append(timestamp).append("**\n")
                                 .append("Created: ").append(formattedDate).append("\n\n");
                        backupCount++;
                    } catch (Exception ignored) {
                    }
                }
            }

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(CustomEmojis.SAVE + " Server Backups")
                    .setDescription("Available configuration backups for **" + event.getGuild().getName() + "**");

            if (backupCount == 0) {
                embed.addField(CustomEmojis.NOTE + " No Backups Found",
                              "Use `!backup create` to create your first backup.", false);
            } else {
                embed.addField(CustomEmojis.NOTE + " Available Backups (" + backupCount + ")",
                              backupList.toString(), false);
                embed.addField(CustomEmojis.INFO + " Usage",
                              "Use `!backup info <timestamp>` to view backup details\n" +
                              "Use `!backup restore <timestamp>` to restore a backup", false);
            }

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "List Failed", "Failed to list backups: " + e.getMessage()
            )).queue();
        }
    }

    private void handleBackupInfo(MessageReceivedEvent event, String guildId, String timestamp) {
        if (timestamp == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the backup timestamp.\nUsage: `!backup info <timestamp>`"
            )).queue();
            return;
        }

        try {
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String backupData = (String) guildSettings.get("backup_" + timestamp);

            if (backupData == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Backup Not Found", "No backup found with timestamp: " + timestamp
                )).queue();
                return;
            }

            String[] lines = backupData.split("\n");
            int settingsCount = 0;
            for (String line : lines) {
                if (line.contains("=") && !line.startsWith("#")) {
                    settingsCount++;
                }
            }

            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timestamp,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String formattedDate = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(CustomEmojis.SAVE + " Backup Information")
                    .setDescription("Details for backup: **" + timestamp + "**")
                    .addField(CustomEmojis.NOTE + " Created", formattedDate, true)
                    .addField(CustomEmojis.SETTING + " Settings Count", String.valueOf(settingsCount), true)
                    .addField(CustomEmojis.INFO + " Size", backupData.length() + " characters", true)
                    .addField(CustomEmojis.WARN + " Restore Warning",
                              "Restoring this backup will **overwrite all current settings**!\n" +
                              "Make sure to create a current backup first.", false);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Info Failed", "Failed to get backup info: " + e.getMessage()
            )).queue();
        }
    }

    private void handleBackupRestore(MessageReceivedEvent event, String guildId, String timestamp) {
        if (timestamp == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Parameter", "Please specify the backup timestamp to restore.\nUsage: `!backup restore <timestamp>`"
            )).queue();
            return;
        }

        try {
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String backupData = (String) guildSettings.get("backup_" + timestamp);

            if (backupData == null) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Backup Not Found", "No backup found with timestamp: " + timestamp
                )).queue();
                return;
            }

            String[] lines = backupData.split("\n");
            int restoredCount = 0;

            for (String line : lines) {
                if (line.contains("=") && !line.startsWith("#") && !line.startsWith("backup_")) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0];
                        String value = parts[1];

                        Object convertedValue;
                        if (value.equals("true") || value.equals("false")) {
                            convertedValue = Boolean.parseBoolean(value);
                        } else if (value.equals("null")) {
                            convertedValue = null;
                        } else {
                            try {
                                convertedValue = Long.parseLong(value);
                            } catch (NumberFormatException e) {
                                convertedValue = value;
                            }
                        }

                        ServerBot.getStorageManager().updateGuildSettings(guildId, key, convertedValue);
                        restoredCount++;
                    }
                }
            }

            java.time.LocalDateTime dateTime = java.time.LocalDateTime.parse(timestamp,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            String formattedDate = dateTime.format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                    .setTitle(CustomEmojis.SUCCESS + " Backup Restored Successfully")
                    .setDescription("Server configuration has been restored from backup!")
                    .addField(CustomEmojis.NOTE + " Backup From", formattedDate, true)
                    .addField(CustomEmojis.SETTING + " Settings Restored", String.valueOf(restoredCount), true)
                    .addField(CustomEmojis.INFO + " Status", "All settings have been applied", false)
                    .addField(CustomEmojis.INFO + " Next Steps",
                              "Your server configuration has been restored to the backed up state.\n" +
                              "You may need to restart the bot for some changes to take effect.", false);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Restore Failed", "Failed to restore backup: " + e.getMessage()
            )).queue();
        }
    }

    /**
     * Handle !config [action] - View or reload server configuration
     * Requires Manage Server permissions
     */
    private void handleConfigCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Guild Only", "This command can only be used in servers."
            )).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionUtils.hasManageServerPermissions(member)) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Command",
                "The command was not found. Use `" + getGuildPrefix(event) + "help` to see available commands."
            )).queue();
            return;
        }

        String action = options.getOrDefault("action", "show");

        switch (action.toLowerCase()) {
            case "show":
                handleConfigShow(event);
                break;
            case "reload":
                handleConfigReload(event);
                break;
            default:
                handleConfigShow(event);
                break;
        }
    }

    private void handleConfigShow(MessageReceivedEvent event) {
        String guildId = event.getGuild().getId();

        try {
            Map<String, Object> config = ServerBot.getStorageManager().getGuildSettings(guildId);

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                    .setTitle("Server Config")
                    .setDescription("Current bot settings for this server");

            String prefix = (String) config.getOrDefault("prefix", "/");
            boolean levelsEnabled = (Boolean) config.getOrDefault("enableLeveling", false);
            boolean pointsEnabled = (Boolean) config.getOrDefault("enableEconomy", false);
            boolean automodEnabled = (Boolean) config.getOrDefault("enableAutoMod", false);
            boolean autoroleEnabled = (Boolean) config.getOrDefault("enableAutoRole", false);

            embed.addField("Basic",
                    "**Prefix:** " + prefix + "\n" +
                    "**Levels:** " + (levelsEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                    "**Economy:** " + (pointsEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                    "**AutoMod:** " + (automodEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled") + "\n" +
                    "**AutoRole:** " + (autoroleEnabled ? CustomEmojis.ON + " Enabled" : CustomEmojis.OFF + " Disabled"),
                    false);

            String modLogChannel = (String) config.get("modLogChannel");
            String punishmentLogChannel = (String) config.get("punishmentLogChannel");
            String allLogChannel = (String) config.get("allLogChannel");

            String logChannels = "";
            if (modLogChannel != null) {
                logChannels += "**Mod Log:** <#" + modLogChannel + ">\n";
            }
            if (punishmentLogChannel != null) {
                logChannels += "**Punishment Log:** <#" + punishmentLogChannel + ">\n";
            }
            if (allLogChannel != null) {
                logChannels += "**All Events:** <#" + allLogChannel + ">\n";
            }
            if (logChannels.isEmpty()) {
                logChannels = "No logging channels configured";
            }

            embed.addField("Logchannels", logChannels, false);

            embed.addField("Config Commands",
                    "`/levels enable|disable` - Toggle leveling system\n" +
                    "`/points enable|disable` - Toggle economy system\n" +
                    "`!config reload` - Reload configuration",
                    false);

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Configuration Error", "Failed to load server configuration: " + e.getMessage()
            )).queue();
        }
    }

    private void handleConfigReload(MessageReceivedEvent event) {
        try {
            ServerBot.getStorageManager().saveAllData();
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "Configuration Reloaded", "Bot configuration has been reloaded successfully."
            )).queue();
        } catch (Exception e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Reload Failed", "Failed to reload configuration: " + e.getMessage()
            )).queue();
        }
    }

    // ==================== Music Commands ====================

    /**
     * Handle !play <query> [index] — Play a track or playlist from URL/search.
     * The raw args are used to parse query and optional index range.
     * Last arg is treated as index if it matches a range pattern (e.g. "4:10", "5:", ":9", or a number).
     */
    private void handlePlayCommand(MessageReceivedEvent event, Map<String, String> options, String[] args) {
        if (!event.isFromGuild()) return;

        Member member = event.getMember();
        if (member == null) return;

        net.dv8tion.jda.api.entities.GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not in Voice Channel",
                "You need to be in a voice channel to use this command."
            )).queue();
            return;
        }

        if (args.length == 0) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Query",
                "Usage: `!play <url or search> [index]`\nIndex examples: `4:10`, `5:`, `:9`"
            )).queue();
            return;
        }

        // Determine if last arg is an index range
        String lastArg = args[args.length - 1];
        String indexStr = "";
        String query;

        if (args.length > 1 && lastArg.matches("^\\d*:\\d*$|^\\d+$")) {
            // Last arg looks like an index range or single number
            indexStr = lastArg;
            query = String.join(" ", java.util.Arrays.copyOfRange(args, 0, args.length - 1));
        } else {
            query = String.join(" ", args);
        }

        if (query.isBlank()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Query", "Please provide a URL or search query."
            )).queue();
            return;
        }

        final net.dv8tion.jda.api.entities.channel.middleman.AudioChannel channel = voiceState.getChannel();
        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();

        // Join voice INSIDE the load callback (after we know a track exists) to avoid
        // the bot sitting idle in the channel when nothing loads — mirrors the slash /play
        // behaviour.  We still check the channel early so we can fail fast if the user
        // is not in a voice channel, but we do NOT open the connection yet.

        final String finalQuery = query;

        if (!indexStr.isBlank()) {
            int[] range = com.serverbot.music.MusicUtils.parseIndexRange(indexStr);
            musicManager.loadPlaylistWithRange(query, event.getGuild(), range[0], range[1],
                new com.serverbot.music.MusicManager.MusicLoadCallback() {
                    @Override
                    public void onTrackLoaded(com.sedmelluq.discord.lavaplayer.track.AudioTrack track) {
                        if (!joinBeforePlay(musicManager, channel, event)) return;
                        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        boolean startedPlaying = gmm.getScheduler().queue(track);
                        int position = gmm.getScheduler().getQueueSize();
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 " + (startedPlaying ? "Now Playing" : "Added to Queue"))
                                .setDescription(com.serverbot.music.MusicUtils.formatTrack(track));
                        if (!startedPlaying) embed.setFooter("Position #" + position + " in queue");
                        event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    }
                    @Override
                    public void onPlaylistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                        if (!joinBeforePlay(musicManager, channel, event)) return;
                        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        for (com.sedmelluq.discord.lavaplayer.track.AudioTrack track : playlist.getTracks()) {
                            gmm.getScheduler().queue(track);
                        }
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playlist Added")
                                .setDescription("**" + playlist.getName() + "** — " + playlist.getTracks().size() + " tracks");
                        event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    }
                    @Override
                    public void onPlaylistRangeLoaded(String name, java.util.List<com.sedmelluq.discord.lavaplayer.track.AudioTrack> tracks, int start, int end, int total) {
                        if (!joinBeforePlay(musicManager, channel, event)) return;
                        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        for (com.sedmelluq.discord.lavaplayer.track.AudioTrack track : tracks) {
                            gmm.getScheduler().queue(track);
                        }
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playlist Added (Range)")
                                .setDescription("**" + name + "**")
                                .addField("Selected", "Tracks " + start + "-" + end + " of " + total, true)
                                .addField("Added", tracks.size() + " tracks", true);
                        event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    }
                    @Override
                    public void onNoMatches() {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "No Results", "No matches found for: `" + finalQuery + "`"
                        )).queue();
                    }
                    @Override
                    public void onLoadFailed(String message) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Load Failed", "Failed to load track: " + message
                        )).queue();
                    }
                });
        } else {
            musicManager.loadAndPlay(query, event.getGuild(),
                new com.serverbot.music.MusicManager.MusicLoadCallback() {
                    @Override
                    public void onTrackLoaded(com.sedmelluq.discord.lavaplayer.track.AudioTrack track) {
                        if (!joinBeforePlay(musicManager, channel, event)) return;
                        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        boolean startedPlaying = gmm.getScheduler().queue(track);
                        int position = gmm.getScheduler().getQueueSize();
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Added to Queue")
                                .setDescription(com.serverbot.music.MusicUtils.formatTrack(track));
                        if (startedPlaying) {
                            embed.addField("Status", "Now playing", true);
                        } else {
                            embed.addField("Position", "#" + position + " in queue", true);
                        }
                        event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    }
                    @Override
                    public void onPlaylistLoaded(com.sedmelluq.discord.lavaplayer.track.AudioPlaylist playlist) {
                        if (!joinBeforePlay(musicManager, channel, event)) return;
                        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        for (com.sedmelluq.discord.lavaplayer.track.AudioTrack track : playlist.getTracks()) {
                            gmm.getScheduler().queue(track);
                        }
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playlist Added")
                                .setDescription("**" + playlist.getName() + "** — " + playlist.getTracks().size() + " tracks");
                        if (!playlist.getTracks().isEmpty()) {
                            embed.addField("First Track", com.serverbot.music.MusicUtils.formatTrack(playlist.getTracks().get(0)), false);
                        }
                        event.getChannel().sendMessageEmbeds(embed.build()).queue();
                    }
                    @Override
                    public void onNoMatches() {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "No Results", "No matches found for: `" + finalQuery + "`"
                        )).queue();
                    }
                    @Override
                    public void onLoadFailed(String message) {
                        event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Load Failed", "Failed to load track: " + message
                        )).queue();
                    }
                });
        }
    }

    /**
     * Join the voice channel only if not already connected.
     * Called from inside a successful load callback so we only join when we have audio.
     * @return true if connected (or already was), false if join failed (error reply already sent)
     */
    private boolean joinBeforePlay(com.serverbot.music.MusicManager musicManager,
                                   net.dv8tion.jda.api.entities.channel.middleman.AudioChannel channel,
                                   MessageReceivedEvent event) {
        if (!musicManager.isConnected(event.getGuild())) {
            if (!musicManager.joinChannel(channel)) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Connection Failed", "Failed to join your voice channel."
                )).queue();
                return false;
            }
        } else {
            // Already connected — move to the requester's channel if different
            net.dv8tion.jda.api.entities.channel.middleman.AudioChannel connected =
                    musicManager.getConnectedChannel(event.getGuild());
            if (connected != null && !connected.getId().equals(channel.getId())) {
                event.getGuild().getAudioManager().openAudioConnection(channel);
            }
        }
        return true;
    }

    private void handleSkipCommand(MessageReceivedEvent event, Map<String, String> options) {
        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        if (gmm.getScheduler().getCurrentTrack() == null) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Nothing Playing", "There is no track currently playing."
            )).queue();
            return;
        }

        int count = 1;
        String countStr = options.get("count");
        if (countStr != null) {
            try { count = Math.max(1, Integer.parseInt(countStr)); } catch (NumberFormatException ignored) {}
        }

        int skipped = gmm.getScheduler().skip(count);
        var currentTrack = gmm.getScheduler().getCurrentTrack();
        String nowPlaying = currentTrack != null
                ? "Now playing: **" + currentTrack.getInfo().title + "**"
                : "Queue is now empty.";

        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "⏭️ Skipped",
            "Skipped **" + skipped + "** track" + (skipped != 1 ? "s" : "") + ".\n" + nowPlaying
        )).queue();
    }

    private void handleJoinCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;
        Member member = event.getMember();
        if (member == null) return;

        net.dv8tion.jda.api.entities.GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not in Voice Channel", "You need to be in a voice channel for me to join."
            )).queue();
            return;
        }

        net.dv8tion.jda.api.entities.channel.middleman.AudioChannel channel = voiceState.getChannel();
        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();

        if (musicManager.isConnected(event.getGuild())) {
            var currentChannel = musicManager.getConnectedChannel(event.getGuild());
            if (currentChannel != null && currentChannel.getIdLong() == channel.getIdLong()) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                    "Already Connected", "I'm already in your voice channel!"
                )).queue();
                return;
            }
        }

        if (musicManager.joinChannel(channel)) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "🔊 Joined", "Connected to **" + channel.getName() + "**"
            )).queue();
        } else {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Connection Failed", "Failed to join **" + channel.getName() + "**. Check bot permissions."
            )).queue();
        }
    }

    private void handleLeaveCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Connected", "I'm not currently in a voice channel."
            )).queue();
            return;
        }

        musicManager.leaveChannel(event.getGuild());
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "👋 Disconnected", "Left the voice channel and cleared the queue."
        )).queue();
    }

    private void handleQueueCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        com.sedmelluq.discord.lavaplayer.track.AudioTrack currentTrack = gmm.getScheduler().getCurrentTrack();
        java.util.List<com.sedmelluq.discord.lavaplayer.track.AudioTrack> queue = gmm.getScheduler().getQueue();

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                .setTitle("🎶 Music Queue");

        if (currentTrack != null) {
            String progress = com.serverbot.music.MusicUtils.createProgressBar(currentTrack.getPosition(), currentTrack.getDuration());
            embed.addField("Now Playing",
                    com.serverbot.music.MusicUtils.formatTrack(currentTrack) + "\n" +
                    progress + " `" + com.serverbot.music.MusicUtils.formatDuration(currentTrack.getPosition()) +
                    "/" + com.serverbot.music.MusicUtils.formatDuration(currentTrack.getDuration()) + "`",
                    false);
        } else {
            embed.addField("Now Playing", "Nothing is currently playing.", false);
        }

        if (queue.isEmpty()) {
            embed.addField("Up Next", "Queue is empty. Use `!play` to add tracks!", false);
        } else {
            StringBuilder queueStr = new StringBuilder();
            int displayCount = Math.min(queue.size(), 10);
            for (int i = 0; i < displayCount; i++) {
                queueStr.append(com.serverbot.music.MusicUtils.formatTrackShort(queue.get(i), i + 1)).append("\n");
            }
            if (queue.size() > 10) {
                queueStr.append("*... and ").append(queue.size() - 10).append(" more*");
            }
            long totalDuration = queue.stream().mapToLong(t -> t.getDuration()).sum();
            if (currentTrack != null) {
                totalDuration += currentTrack.getDuration() - currentTrack.getPosition();
            }
            embed.addField("Up Next (" + queue.size() + " tracks)", queueStr.toString(), false);
            embed.setFooter("Total queue time: " + com.serverbot.music.MusicUtils.formatDuration(totalDuration));
        }

        String statusLine = "";
        if (gmm.getScheduler().isRepeating()) statusLine += "🔁 Repeat ON  ";
        if (gmm.getScheduler().isShuffling()) statusLine += "🔀 Shuffle ON";
        if (!statusLine.isEmpty()) {
            embed.addField("Mode", statusLine.trim(), false);
        }

        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }

    private void handlePauseCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean isPaused = gmm.getPlayer().isPaused();
        gmm.getPlayer().setPaused(!isPaused);

        if (isPaused) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "▶️ Resumed", "Music playback has been resumed."
            )).queue();
        } else {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "⏸️ Paused", "Music playback has been paused."
            )).queue();
        }
    }

    private void handleStopCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        var gmm = musicManager.getGuildMusicManager(event.getGuild());
        gmm.getScheduler().clearQueue();
        gmm.getPlayer().stopTrack();

        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "⏹️ Stopped", "Stopped playing and cleared the queue."
        )).queue();
    }

    private void handleVolumeCommand(MessageReceivedEvent event, Map<String, String> options) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        int currentVolume = gmm.getPlayer().getVolume();

        String levelStr = options.get("level");
        if (levelStr == null) {
            int barLen = 10;
            int filled = (int) Math.round((currentVolume / 100.0) * barLen);
            filled = Math.max(0, Math.min(barLen, filled));
            String volumeBar = "▓".repeat(filled) + "░".repeat(barLen - filled);
            event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
                "🔊 Volume", "Current volume: **" + currentVolume + "%**\n" + volumeBar
            )).queue();
            return;
        }

        int newVolume;
        try {
            newVolume = Math.max(0, Math.min(150, Integer.parseInt(levelStr)));
        } catch (NumberFormatException e) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Invalid Volume", "Please provide a number between 0 and 150."
            )).queue();
            return;
        }

        gmm.getPlayer().setVolume(newVolume);
        String emoji = newVolume == 0 ? "🔇" : newVolume <= 50 ? "🔉" : "🔊";
        int barLen = 10;
        int filled = (int) Math.round((newVolume / 100.0) * barLen);
        filled = Math.max(0, Math.min(barLen, filled));
        String volumeBar = "▓".repeat(filled) + "░".repeat(barLen - filled);
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            emoji + " Volume Set", "Volume set to **" + newVolume + "%**\n" + volumeBar
        )).queue();
    }

    private void handleRepeatCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean newState = !gmm.getScheduler().isRepeating();
        gmm.getScheduler().setRepeating(newState);

        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "🔁 Repeat " + (newState ? "Enabled" : "Disabled"),
            newState ? "The current track will now repeat." : "Repeat mode has been turned off."
        )).queue();
    }

    private void handleShuffleCommand(MessageReceivedEvent event) {
        if (!event.isFromGuild()) return;

        com.serverbot.music.MusicManager musicManager = com.serverbot.music.MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).queue();
            return;
        }

        com.serverbot.music.GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean newState = !gmm.getScheduler().isShuffling();
        gmm.getScheduler().setShuffling(newState);

        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "🔀 Shuffle " + (newState ? "Enabled" : "Disabled"),
            newState ? "The queue will now be shuffled." : "Shuffle mode has been turned off."
        )).queue();
    }

    // Global Chat prefix handlers
    // Works in both DMs and guilds. Only link/unlink are guild-only.

    /**
     * Handle !globalchat / !gc — works in DMs and guilds.
     * Usage: !globalchat <subcommand> [args...]
     */
    private void handleGlobalChatCommand(MessageReceivedEvent event, String[] args) {
        if (args.length == 0) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed(
                "Global Chat",
                "**Available subcommands:**\n" +
                "`create <name> <description>` — create a channel\n" +
                "`list` — list your channels\n" +
                "`info <channelId>` — view channel info\n" +
                "`manage <channelId>` — open management panel (sent to DMs)\n" +
                "`edit <channelId> [name:<n>] [desc:<d>] [vis:<v>] [key:<k>]` — edit a channel\n" +
                "`setrules <channelId> <rule1 | rule2 | ...>` — set rules\n" +
                "`delete <channelId>` — delete a channel\n" +
                "`link <channelId> <#channel> [key]` — link a channel *(guild only)*\n" +
                "`unlink <#channel>` — unlink a channel *(guild only)*\n" +
                "`kick/ban/unban/warn/unwarn <channelId> <serverId> [reason]`\n" +
                "`mute <channelId> <serverId> [duration] [reason]`\n" +
                "`unmute <channelId> <serverId>`\n" +
                "`addmod/removemod/addcoowner <channelId> <@user or userId>`\n\n" +
                "💡 Use `/globalchat` for the full interactive slash command."
            )).queue();
            return;
        }

        com.serverbot.services.GlobalChatService service = ServerBot.getGlobalChatService();
        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (sub) {
            case "create"     -> handleGcPrefixCreate(event, subArgs, service);
            case "edit"       -> handleGcPrefixEdit(event, subArgs, service);
            case "delete"     -> handleGcPrefixDelete(event, subArgs, service);
            case "link"       -> handleGcPrefixLink(event, subArgs, service);
            case "unlink"     -> handleGcPrefixUnlink(event, subArgs, service);
            case "info"       -> handleGcPrefixInfo(event, subArgs, service);
            case "list"       -> handleGcPrefixList(event, service);
            case "setrules"   -> handleGcPrefixSetRules(event, subArgs, service);
            case "manage"     -> handleGcPrefixManage(event, subArgs, service);
            case "kick"       -> handleGcPrefixKick(event, subArgs, service);
            case "ban"        -> handleGcPrefixBan(event, subArgs, service);
            case "unban"      -> handleGcPrefixUnban(event, subArgs, service);
            case "warn"       -> handleGcPrefixWarn(event, subArgs, service);
            case "unwarn"     -> handleGcPrefixUnwarn(event, subArgs, service);
            case "mute"       -> handleGcPrefixMute(event, subArgs, service);
            case "unmute"     -> handleGcPrefixUnmute(event, subArgs, service);
            case "addmod"     -> handleGcPrefixAddMod(event, subArgs, service);
            case "removemod"  -> handleGcPrefixRemoveMod(event, subArgs, service);
            case "addcoowner" -> handleGcPrefixAddCoOwner(event, subArgs, service);
            default -> event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Unknown Subcommand",
                "Unknown subcommand `" + sub + "`. Use `!globalchat` to see all subcommands."
            )).queue();
        }
    }

    private void handleGcPrefixCreate(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        // !globalchat create <name> <description...>
        // First token = name, rest = description
        if (args.length < 2) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Usage", "`!globalchat create <name> <description>`\n" +
                "The name is the first word; everything after is the description."
            )).queue();
            return;
        }
        String name = args[0];
        String desc = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        // Support kv args at end of description for nameformat and webhookmode
        String nameFormat = gcExtractKvArg(desc, "nameformat");
        String webhookModeStr = gcExtractKvArg(desc, "webhookmode");
        boolean webhookEnabled = webhookModeStr == null || !webhookModeStr.equalsIgnoreCase("text");
        // Remove kv args from the description string
        if (nameFormat != null) desc = desc.replaceAll("(?i)nameformat:[^\\s]+", "").trim();
        if (webhookModeStr != null) desc = desc.replaceAll("(?i)webhookmode:[^\\s]+", "").trim();
        com.serverbot.models.GlobalChatChannel ch = service.createChannel(
            name, desc, "public", false, null, event.getAuthor().getId(), null, null, nameFormat, webhookEnabled);
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Global Chat Channel Created",
            "**" + ch.getName() + "** created!\n" +
            "**ID:** `" + ch.getChannelId() + "`\n" +
            "Use `!globalchat manage " + ch.getChannelId() + "` to manage it."
        )).queue();
    }

    private void handleGcPrefixEdit(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        // !globalchat edit <channelId> [name:<name>] [desc:<desc>] [vis:<vis>] [key:<key>]
        if (args.length < 1) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                "Usage", "`!globalchat edit <channelId> [name:<name>] [desc:<desc>] [vis:public|private] [key:<key>]`"
            )).queue();
            return;
        }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasManageAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to edit this channel.")).queue(); return; }

        String remaining = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        String newName = gcExtractKvArg(remaining, "name");
        String newDesc = gcExtractKvArg(remaining, "desc");
        String newVis  = gcExtractKvArg(remaining, "vis");
        String newKey  = gcExtractKvArg(remaining, "key");
        String newNameFormat = gcExtractKvArg(remaining, "nameformat");
        String newWebhookMode = gcExtractKvArg(remaining, "webhookmode");

        if (newName != null) gc.setName(newName);
        if (newDesc != null) gc.setDescription(newDesc);
        if (newVis != null) {
            if (!newVis.equalsIgnoreCase("public") && !newVis.equalsIgnoreCase("private")) {
                event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Invalid Visibility", "Visibility must be `public` or `private`.")).queue();
                return;
            }
            gc.setVisibility(newVis.toLowerCase());
        }
        if (newKey != null) { gc.setKey(newKey); gc.setKeyRequired(true); }
        if (newNameFormat != null) {
            if (newNameFormat.equalsIgnoreCase("reset") || newNameFormat.equalsIgnoreCase("none") || newNameFormat.equalsIgnoreCase("default")) {
                gc.setNameFormat(null);
            } else if (newNameFormat.equals("{}")) {
                gc.setNameFormat("");
            } else {
                gc.setNameFormat(newNameFormat);
            }
        }
        if (newWebhookMode != null) {
            gc.setWebhookEnabled(!newWebhookMode.equalsIgnoreCase("text"));
        }

        service.saveChannels();
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Channel Updated", "Global chat channel **" + gc.getName() + "** has been updated."
        )).queue();
    }

    private void handleGcPrefixDelete(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 1) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat delete <channelId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.isOwner(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "Only the channel owner can delete it.")).queue(); return; }

        for (Map.Entry<String, String> entry : gc.getLinkedChannels().entrySet()) {
            try {
                net.dv8tion.jda.api.entities.Guild g = event.getJDA().getGuildById(entry.getKey());
                if (g != null) {
                    net.dv8tion.jda.api.entities.channel.concrete.TextChannel tc = g.getTextChannelById(entry.getValue());
                    if (tc != null) tc.sendMessageEmbeds(EmbedUtils.createInfoEmbed("Global Chat",
                        CustomEmojis.TRASH + " The global chat channel **" + gc.getName() + "** has been deleted.")).queue(s -> {}, e -> {});
                }
            } catch (Exception ignored) {}
        }

        service.deleteChannel(args[0]);
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Channel Deleted", "Global chat channel **" + gc.getName() + "** has been deleted."
        )).queue();
    }

    private void handleGcPrefixLink(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (!event.isFromGuild()) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "The `link` subcommand can only be used in a server.")).queue(); return; }
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat link <channelId> <#channel> [key]`")).queue(); return; }
        if (!PermissionManager.hasPermission(event.getMember(), "globalchat.link")) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Permission", "You need the `globalchat.link` permission.")).queue(); return; }

        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }

        String channelRef = args[1];
        Matcher cm = Pattern.compile("<#(\\d+)>").matcher(channelRef);
        String textChannelId = cm.matches() ? cm.group(1) : channelRef.replaceAll("\\D", "");
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel target = event.getGuild().getTextChannelById(textChannelId);
        if (target == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Invalid Channel", "Could not find that channel in this server.")).queue(); return; }

        String key = args.length > 2 ? args[2] : null;
        String error = service.linkChannel(args[0], event.getGuild().getId(), textChannelId, key);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Link Failed", error)).queue(); return; }

        service.sendRulesToChannel(args[0], event.getGuild().getId(), event.getJDA());
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Channel Linked", target.getAsMention() + " is now linked to global chat **" + gc.getName() + "**."
        )).queue();
    }

    private void handleGcPrefixUnlink(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (!event.isFromGuild()) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Guild Only", "The `unlink` subcommand can only be used in a server.")).queue(); return; }
        if (args.length < 1) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat unlink <#channel>`")).queue(); return; }

        String channelRef = args[0];
        Matcher cm = Pattern.compile("<#(\\d+)>").matcher(channelRef);
        String textChannelId = cm.matches() ? cm.group(1) : channelRef.replaceAll("\\D", "");
        String globalId = service.getGlobalChannelIdByTextChannel(textChannelId);
        if (globalId == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Linked", "That channel is not linked to any global chat.")).queue(); return; }

        com.serverbot.models.GlobalChatChannel gc = service.getChannel(globalId);
        boolean isChannelOwner = gc != null && gc.hasManageAccess(event.getAuthor().getId());
        if (!isChannelOwner && !PermissionManager.hasPermission(event.getMember(), "globalchat.unlink")) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Permission", "You need the `globalchat.unlink` permission.")).queue();
            return;
        }
        String error = service.unlinkChannel(event.getGuild().getId(), textChannelId);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Unlink Failed", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Channel Unlinked", "Channel has been unlinked" + (gc != null ? " from **" + gc.getName() + "**" : "") + "."
        )).queue();
    }

    private void handleGcPrefixInfo(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 1) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat info <channelId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if ("private".equals(gc.getVisibility()) && !gc.hasModerateAccess(event.getAuthor().getId())) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Private Channel", "This channel is private.")).queue();
            return;
        }
        EmbedBuilder eb = new EmbedBuilder()
            .setTitle(CustomEmojis.INFO + " " + gc.getName())
            .setDescription(gc.getDescription())
            .setColor(EmbedUtils.INFO_COLOR)
            .addField("Channel ID", "`" + gc.getChannelId() + "`", true)
            .addField("Visibility", gc.getVisibility(), true)
            .addField("Key Required", gc.isKeyRequired() ? "Yes" : "No", true)
            .addField("Owner", "<@" + gc.getOwnerId() + ">", true)
            .addField("Linked Servers", String.valueOf(gc.getLinkedChannels().size()), true);
        if (!gc.getRules().isEmpty()) eb.addField("Rules", service.formatRules(gc), false);
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleGcPrefixList(MessageReceivedEvent event, com.serverbot.services.GlobalChatService service) {
        java.util.List<com.serverbot.models.GlobalChatChannel> owned = service.getChannelsByOwner(event.getAuthor().getId());
        if (owned.isEmpty()) {
            event.getChannel().sendMessageEmbeds(EmbedUtils.createInfoEmbed("No Channels",
                "You don't own any global chat channels. Use `!globalchat create <name> <description>` to make one!")).queue();
            return;
        }
        EmbedBuilder eb = new EmbedBuilder()
            .setTitle(CustomEmojis.INFO + " Your Global Chat Channels")
            .setColor(EmbedUtils.INFO_COLOR);
        for (com.serverbot.models.GlobalChatChannel gc : owned) {
            String role = gc.isOwner(event.getAuthor().getId()) ? "Owner" : "Co-Owner";
            eb.addField(gc.getName() + " (`" + gc.getChannelId() + "`)",
                gc.getDescription() + "\nVisibility: " + gc.getVisibility() +
                " | Linked: " + gc.getLinkedChannels().size() + " | Role: " + role, false);
        }
        event.getChannel().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleGcPrefixSetRules(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat setrules <channelId> <rule1 | rule2 | ...>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasManageAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to set rules for this channel.")).queue(); return; }

        String rulesRaw = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        java.util.List<String> rules = Arrays.stream(rulesRaw.split("\\|"))
            .map(String::trim).filter(s -> !s.isEmpty())
            .collect(java.util.stream.Collectors.toList());
        service.setRules(args[0], rules, event.getJDA());
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Rules Updated", "Rules for **" + gc.getName() + "** have been updated.\n" + service.formatRules(gc)
        )).queue();
    }

    private void handleGcPrefixManage(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 1) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat manage <channelId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to manage this channel.")).queue(); return; }

        boolean isOwnerOrCoOwner = gc.hasManageAccess(event.getAuthor().getId());
        EmbedBuilder eb = new EmbedBuilder()
            .setTitle(CustomEmojis.SETTING + " Manage: " + gc.getName())
            .setDescription("Use the buttons below to manage this global chat channel.\n" +
                "**ID:** `" + gc.getChannelId() + "`\n**Linked Servers:** " + gc.getLinkedChannels().size())
            .setColor(EmbedUtils.INFO_COLOR);

        java.util.List<Button> row1 = new java.util.ArrayList<>();
        java.util.List<Button> row2 = new java.util.ArrayList<>();
        java.util.List<Button> row3 = new java.util.ArrayList<>();
        if (isOwnerOrCoOwner) {
            row1.add(Button.primary("gc_edit:" + gc.getChannelId(), "Edit Channel"));
            row1.add(Button.danger("gc_delete:" + gc.getChannelId(), "Delete Channel"));
            row1.add(Button.primary("gc_setrules:" + gc.getChannelId(), "Set Rules"));
            row1.add(Button.primary("gc_addmod:" + gc.getChannelId(), "Add Mod"));
        }
        row2.add(Button.danger("gc_kick:" + gc.getChannelId(), "Kick Server"));
        row2.add(Button.danger("gc_ban:" + gc.getChannelId(), "Ban Server"));
        row2.add(Button.secondary("gc_warn:" + gc.getChannelId(), "Warn Server"));
        row2.add(Button.secondary("gc_mute:" + gc.getChannelId(), "Mute Server"));
        row3.add(Button.secondary("gc_unmute:" + gc.getChannelId(), "Unmute Server"));
        row3.add(Button.secondary("gc_unwarn:" + gc.getChannelId(), "Unwarn Server"));
        row3.add(Button.primary("gc_linked:" + gc.getChannelId(), "View Linked"));

        event.getAuthor().openPrivateChannel().queue(dm -> {
            var msgAction = dm.sendMessageEmbeds(eb.build());
            if (!row1.isEmpty()) msgAction = msgAction.addComponents(ActionRow.of(row1));
            msgAction = msgAction.addComponents(ActionRow.of(row2)).addComponents(ActionRow.of(row3));
            msgAction.queue(s -> {}, e -> {});
        }, e -> {});
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed(
            "Management Panel Sent", "The management panel has been sent to your DMs."
        )).queue();
    }

    private void handleGcPrefixKick(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat kick <channelId> <serverId> [reason]`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to kick from this channel.")).queue(); return; }
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        String error = service.kickServer(args[0], args[1], reason, event.getJDA());
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Server Kicked", "Server `" + args[1] + "` has been kicked.")).queue();
    }

    private void handleGcPrefixBan(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat ban <channelId> <serverId> [reason]`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to ban from this channel.")).queue(); return; }
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        String error = service.banServer(args[0], args[1], reason, event.getJDA());
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Server Banned", "Server `" + args[1] + "` has been banned.")).queue();
    }

    private void handleGcPrefixUnban(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat unban <channelId> <serverId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasManageAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to unban from this channel.")).queue(); return; }
        String error = service.unbanServer(args[0], args[1]);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Server Unbanned", "Server `" + args[1] + "` has been unbanned.")).queue();
    }

    private void handleGcPrefixWarn(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat warn <channelId> <serverId> [reason]`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to warn in this channel.")).queue(); return; }
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
        String error = service.warnServer(args[0], args[1], reason, event.getJDA());
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Server Warned", "Server `" + args[1] + "` has been warned.")).queue();
    }

    private void handleGcPrefixUnwarn(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat unwarn <channelId> <serverId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to unwarn in this channel.")).queue(); return; }
        String error = service.unwarnServer(args[0], args[1]);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Warnings Cleared", "Warnings for server `" + args[1] + "` have been cleared.")).queue();
    }

    private void handleGcPrefixMute(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        // !globalchat mute <channelId> <serverId> [duration] [reason...]
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat mute <channelId> <serverId> [duration] [reason]`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to mute in this channel.")).queue(); return; }
        String durationStr = args.length > 2 ? args[2] : "0";
        String reason = args.length > 3 ? String.join(" ", Arrays.copyOfRange(args, 3, args.length)) : null;
        long durationMs = gcParseDuration(durationStr);
        String error = service.muteServer(args[0], args[1], durationMs, reason, event.getJDA());
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Server Muted",
            "Server `" + args[1] + "` has been muted" + (durationMs <= 0 ? " permanently" : " for " + durationStr) + ".")).queue();
    }

    private void handleGcPrefixUnmute(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat unmute <channelId> <serverId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasModerateAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to unmute in this channel.")).queue(); return; }
        String error = service.unmuteServer(args[0], args[1], event.getJDA());
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Server Unmuted", "Server `" + args[1] + "` has been unmuted.")).queue();
    }

    private void handleGcPrefixAddMod(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat addmod <channelId> <@user or userId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasManageAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to add moderators.")).queue(); return; }
        String userId = gcParseUserArg(args[1]);
        String error = service.addModerator(args[0], userId);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Moderator Added", "<@" + userId + "> has been added as a moderator.")).queue();
    }

    private void handleGcPrefixRemoveMod(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat removemod <channelId> <@user or userId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.hasManageAccess(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "You don't have permission to remove moderators.")).queue(); return; }
        String userId = gcParseUserArg(args[1]);
        String error = service.removeModerator(args[0], userId);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Moderator Removed", "<@" + userId + "> has been removed as a moderator.")).queue();
    }

    private void handleGcPrefixAddCoOwner(MessageReceivedEvent event, String[] args, com.serverbot.services.GlobalChatService service) {
        if (args.length < 2) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Usage", "`!globalchat addcoowner <channelId> <@user or userId>`")).queue(); return; }
        com.serverbot.models.GlobalChatChannel gc = service.getChannel(args[0]);
        if (gc == null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Not Found", "Global chat channel not found.")).queue(); return; }
        if (!gc.isOwner(event.getAuthor().getId())) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("No Access", "Only the channel owner can add co-owners.")).queue(); return; }
        String userId = gcParseUserArg(args[1]);
        String error = service.addCoOwner(args[0], userId);
        if (error != null) { event.getChannel().sendMessageEmbeds(EmbedUtils.createErrorEmbed("Error", error)).queue(); return; }
        event.getChannel().sendMessageEmbeds(EmbedUtils.createSuccessEmbed("Co-Owner Added", "<@" + userId + "> has been added as a co-owner.")).queue();
    }

    /** Parse a user mention (&lt;@123&gt; or &lt;@!123&gt;) or bare numeric ID. */
    private String gcParseUserArg(String input) {
        Matcher m = USER_MENTION.matcher(input);
        return m.matches() ? m.group(1) : input.replaceAll("\\D", "");
    }

    /** Extract value from "key:value" (or key:"quoted value") pairs in a string. */
    private String gcExtractKvArg(String input, String key) {
        Matcher m = Pattern.compile("\\b" + key + ":(?:\"([^\"]+)\"|([^\\s]+))").matcher(input);
        if (m.find()) return m.group(1) != null ? m.group(1) : m.group(2);
        return null;
    }

    /** Parse duration string (e.g. "1h", "30m", "7d") to milliseconds; returns 0 for permanent/invalid. */
    private long gcParseDuration(String input) {
        if (input == null || input.equals("0")) return 0;
        try {
            input = input.trim().toLowerCase();
            long value = Long.parseLong(input.substring(0, input.length() - 1));
            char unit = input.charAt(input.length() - 1);
            return switch (unit) {
                case 's' -> TimeUnit.SECONDS.toMillis(value);
                case 'm' -> TimeUnit.MINUTES.toMillis(value);
                case 'h' -> TimeUnit.HOURS.toMillis(value);
                case 'd' -> TimeUnit.DAYS.toMillis(value);
                default  -> 0;
            };
        } catch (Exception e) { return 0; }
    }
}