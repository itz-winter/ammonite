package com.serverbot.services;

import com.serverbot.commands.SlashCommand;
import com.serverbot.ServerBot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Manages all slash commands for the bot
 */
public class CommandManager {
    
    private static final Logger logger = LoggerFactory.getLogger(CommandManager.class);
    private final Map<String, SlashCommand> commands = new HashMap<>();
    
    // Rate limiting for command updates
    private long lastCommandUpdate = 0;
    private static final long COMMAND_UPDATE_COOLDOWN = TimeUnit.HOURS.toMillis(1); // 1 hour cooldown
    
    public CommandManager() {
        registerCommands();
    }
    
    private void registerCommands() {
        logger.info("Registering commands...");
        
        // Utility commands
        registerCommand(new com.serverbot.commands.utility.HelpCommand());
        registerCommand(new com.serverbot.commands.utility.EchoCommand());
        registerCommand(new com.serverbot.commands.utility.StatusCommand());
        registerCommand(new com.serverbot.commands.utility.InfoCommand());
        registerCommand(new com.serverbot.commands.utility.PingCommand());
        registerCommand(new com.serverbot.commands.utility.PresenceCommand());
        registerCommand(new com.serverbot.commands.utility.RestartCommand());
        registerCommand(new com.serverbot.commands.utility.AppearanceCommand());
        registerCommand(new com.serverbot.commands.utility.ServerStatsCommand());
        registerCommand(new com.serverbot.commands.utility.EmbedCommand());
        registerCommand(new com.serverbot.commands.utility.DadJokeCommand());
        registerCommand(new com.serverbot.commands.utility.JokeCommand());
        registerCommand(new com.serverbot.commands.utility.PrideCommand());
        registerCommand(new com.serverbot.commands.utility.FlagsCommand());
        registerCommand(new com.serverbot.commands.utility.PronounsCommand());
        registerCommand(new com.serverbot.commands.utility.ErrorCommand());
        registerCommand(new com.serverbot.commands.utility.RulesCommand());
        registerCommand(new com.serverbot.commands.utility.TalkAsCommand());
        
        // Game commands
        registerCommand(new com.serverbot.commands.games.PokerCommand());
        registerCommand(new com.serverbot.commands.games.ChessCommand());
        
        // Economy commands that work with file storage
        registerCommand(new com.serverbot.commands.economy.BalanceCommand());
        registerCommand(new com.serverbot.commands.economy.PayCommand());
        registerCommand(new com.serverbot.commands.economy.BaltopCommand());
        registerCommand(new com.serverbot.commands.economy.DiceCommand());
        registerCommand(new com.serverbot.commands.economy.FlipCommand());
        registerCommand(new com.serverbot.commands.economy.SlotsCommand());
        registerCommand(new com.serverbot.commands.economy.DailyCommand());
        registerCommand(new com.serverbot.commands.economy.WorkCommand());
        registerCommand(new com.serverbot.commands.economy.RobCommand());
        registerCommand(new com.serverbot.commands.economy.GambleCommand());
        registerCommand(new com.serverbot.commands.economy.BlackjackCommand());
        registerCommand(new com.serverbot.commands.economy.BankCommand());
        registerCommand(new com.serverbot.commands.economy.ChargebackCommand());
        registerCommand(new com.serverbot.commands.economy.SetBalanceCommand());
        registerCommand(new com.serverbot.commands.economy.AddBalanceCommand());
        registerCommand(new com.serverbot.commands.economy.SubtractBalanceCommand());
        
        // Moderation commands that work with file storage  
        registerCommand(new com.serverbot.commands.moderation.WarnCommand());
        registerCommand(new com.serverbot.commands.moderation.BanCommand());
        registerCommand(new com.serverbot.commands.moderation.MuteCommand());
        registerCommand(new com.serverbot.commands.moderation.KickCommand());
        registerCommand(new com.serverbot.commands.moderation.UnbanCommand());
        registerCommand(new com.serverbot.commands.moderation.UnmuteCommand());
        registerCommand(new com.serverbot.commands.moderation.SoftbanCommand());
        registerCommand(new com.serverbot.commands.moderation.UnwarnCommand());
        registerCommand(new com.serverbot.commands.moderation.HistCommand());
        registerCommand(new com.serverbot.commands.moderation.WarnsCommand());
        registerCommand(new com.serverbot.commands.moderation.LockdownCommand());
        registerCommand(new com.serverbot.commands.moderation.PurgeCommand());
        registerCommand(new com.serverbot.commands.moderation.TimeoutCommand());
        
        // Leveling commands that work with file storage
        registerCommand(new com.serverbot.commands.leveling.XpCommand());
        registerCommand(new com.serverbot.commands.leveling.LevelCommand());
        registerCommand(new com.serverbot.commands.leveling.RankCommand());
        registerCommand(new com.serverbot.commands.leveling.LeaderboardCommand());
        registerCommand(new com.serverbot.commands.leveling.LbCommand());
        
        // Configuration commands that work with file storage
        registerCommand(new com.serverbot.commands.configuration.LevelsCommand());
        registerCommand(new com.serverbot.commands.configuration.PointsCommand());
        registerCommand(new com.serverbot.commands.configuration.ConfigCommand());
        
        // Config commands - new comprehensive system
        registerCommand(new com.serverbot.commands.config.SettingsCommand());
        registerCommand(new com.serverbot.commands.config.PermissionsCommand());
        registerCommand(new com.serverbot.commands.config.AntiSpamCommand());
        registerCommand(new com.serverbot.commands.config.WelcomeCommand());
        registerCommand(new com.serverbot.commands.config.BackupCommand());
        registerCommand(new com.serverbot.commands.config.LoggingCommand());
        registerCommand(new com.serverbot.commands.config.AutomodCommand());
    registerCommand(new com.serverbot.commands.config.LogCommand());
        registerCommand(new com.serverbot.commands.config.RolePersistenceCommand());
        registerCommand(new com.serverbot.commands.config.ReactionRoleCommand());
        registerCommand(new com.serverbot.commands.config.PunishmentDMCommand());
        registerCommand(new com.serverbot.commands.config.SuspiciousNotifyCommand());
        registerCommand(new com.serverbot.commands.config.PrefixCommand());
        registerCommand(new com.serverbot.commands.utility.SuspiciousListCommand());
        
        // Support commands
        registerCommand(new com.serverbot.commands.support.TicketCommand());
        
        // Ticket commands
        registerCommand(new com.serverbot.commands.tickets.TicketCommand());
        
        // Proxy commands (PluralKit-style)
        registerCommand(new com.serverbot.commands.proxy.ProxyMemberCommand());
        registerCommand(new com.serverbot.commands.proxy.ProxySettingsCommand());
        
        // Global chat commands
        registerCommand(new com.serverbot.commands.utility.GlobalChatCommand());
        
        // Auto config command
        registerCommand(new com.serverbot.commands.config.AutoConfigCommand());
        
        // Privacy & data management commands (Discord ToS compliance)
        registerCommand(new com.serverbot.commands.utility.PrivacyCommand());
        registerCommand(new com.serverbot.commands.utility.DeleteDataCommand());
        
        logger.info("Registered {} commands", commands.size());
    }
    
    public void registerCommand(SlashCommand command) {
        commands.put(command.getName().toLowerCase(), command);
        logger.debug("Registered command: {}", command.getName());
    }
    
    public SlashCommand getCommand(String name) {
        return commands.get(name.toLowerCase());
    }
    
    public Map<String, SlashCommand> getAllCommands() {
        return new HashMap<>(commands);
    }
    
    public void updateGlobalCommands(JDA jda) {
        // Rate limit command updates to prevent Discord API abuse
        long currentTime = System.currentTimeMillis();
        if (lastCommandUpdate > 0 && (currentTime - lastCommandUpdate) < COMMAND_UPDATE_COOLDOWN) {
            logger.info("Skipping command update due to rate limiting. Last update was {} minutes ago.", 
                (currentTime - lastCommandUpdate) / TimeUnit.MINUTES.toMillis(1));
            return;
        }
        
        // Only update commands if not already updated recently to avoid rate limits
        logger.info("Updating global slash commands...");
        lastCommandUpdate = currentTime;
        
        boolean hideOwner = false;
        try {
            if (ServerBot.getConfigManager() != null && ServerBot.getConfigManager().getConfig() != null) {
                hideOwner = ServerBot.getConfigManager().getConfig().isHideOwnerCommands();
            }
        } catch (Exception e) {
            logger.warn("Could not read hide_owner_commands config, defaulting to false");
        }
        
        List<CommandData> commandDataList = new ArrayList<>();
        
        // Utility commands
        commandDataList.add(com.serverbot.commands.utility.HelpCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.EchoCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.InfoCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.PingCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.ServerStatsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.EmbedCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.DadJokeCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.JokeCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.PrideCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.FlagsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.PronounsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.ErrorCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.RulesCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.TalkAsCommand.getCommandData());
        
        // Owner-only utility commands (hidden from slash command list when hide_owner_commands is true)
        // These remain accessible via prefix commands (e.g., !statusmsg, !rpc, !appearance, !restart)
        if (!hideOwner) {
            commandDataList.add(com.serverbot.commands.utility.StatusCommand.getCommandData());
            commandDataList.add(com.serverbot.commands.utility.PresenceCommand.getCommandData());
            commandDataList.add(com.serverbot.commands.utility.RestartCommand.getCommandData());
            commandDataList.add(com.serverbot.commands.utility.AppearanceCommand.getCommandData());
        }
        
        // Game commands
        commandDataList.add(com.serverbot.commands.games.PokerCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.games.ChessCommand.getCommandData());
        
        // Economy commands
        commandDataList.add(com.serverbot.commands.economy.BalanceCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.PayCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.BaltopCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.DiceCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.FlipCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.SlotsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.DailyCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.WorkCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.RobCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.GambleCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.BlackjackCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.BankCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.ChargebackCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.SetBalanceCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.AddBalanceCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.economy.SubtractBalanceCommand.getCommandData());
        
        // Moderation commands
        commandDataList.add(com.serverbot.commands.moderation.WarnCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.BanCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.MuteCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.KickCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.UnbanCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.UnmuteCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.SoftbanCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.UnwarnCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.HistCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.WarnsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.LockdownCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.PurgeCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.moderation.TimeoutCommand.getCommandData());
        
        // Leveling commands
        commandDataList.add(com.serverbot.commands.leveling.XpCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.leveling.LevelCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.leveling.RankCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.leveling.LeaderboardCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.leveling.LbCommand.getCommandData());
        
        // Configuration commands
        commandDataList.add(com.serverbot.commands.configuration.LevelsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.configuration.PointsCommand.getCommandData());
        
        // Owner-only config commands (hidden when hide_owner_commands is true)
        if (!hideOwner) {
            commandDataList.add(com.serverbot.commands.configuration.ConfigCommand.getCommandData());
            commandDataList.add(com.serverbot.commands.config.BackupCommand.getCommandData());
        }
        
        // New comprehensive config commands
        commandDataList.add(com.serverbot.commands.config.SettingsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.config.PermissionsCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.config.AntiSpamCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.config.WelcomeCommand.getCommandData());
    commandDataList.add(com.serverbot.commands.config.LoggingCommand.getCommandData());
    commandDataList.add(com.serverbot.commands.config.AutomodCommand.getCommandData());
    commandDataList.add(com.serverbot.commands.config.LogCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.config.RolePersistenceCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.config.PunishmentDMCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.config.SuspiciousNotifyCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.SuspiciousListCommand.getCommandData());
        
        // Ticket commands
        commandDataList.add(com.serverbot.commands.tickets.TicketCommand.getCommandData());
        
        // Proxy commands (PluralKit-style)
        commandDataList.add(com.serverbot.commands.proxy.ProxyMemberCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.proxy.ProxySettingsCommand.getCommandData());
        
        // Global chat commands
        commandDataList.add(com.serverbot.commands.utility.GlobalChatCommand.getCommandData());
        
        // Auto config command
        commandDataList.add(com.serverbot.commands.config.AutoConfigCommand.getCommandData());
        
        // Privacy & data management commands (Discord ToS compliance)
        commandDataList.add(com.serverbot.commands.utility.PrivacyCommand.getCommandData());
        commandDataList.add(com.serverbot.commands.utility.DeleteDataCommand.getCommandData());
        
        if (hideOwner) {
            logger.info("Owner-only commands hidden from slash commands (hide_owner_commands=true). Use prefix commands instead.");
        }
        
        jda.updateCommands().addCommands(commandDataList).queue(
            success -> {
                logger.info("Successfully updated {} global commands", success.size());
                logger.info("Commands may take up to 1 hour to appear globally");
            },
            error -> {
                logger.error("Failed to update global commands: {}", error.getMessage());
                if (error.getMessage().contains("rate limit")) {
                    logger.warn("Hit Discord rate limit. Commands will be registered later.");
                }
            }
        );
    }
}
