package com.serverbot.commands.proxy;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.models.ProxyMember;
import com.serverbot.services.ProxyService;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;

import java.awt.*;
import java.util.List;

/**
 * Main proxy command for managing proxy members
 * Similar to PluralKit's pk;member command
 */
public class ProxyMemberCommand implements SlashCommand {
    
    private final ProxyService proxyService;
    
    public ProxyMemberCommand() {
        this.proxyService = ServerBot.getProxyService();
    }
    
    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Error",
                "Please specify a subcommand."
            )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        
        switch (subcommand) {
            case "create":
                handleCreate(event);
                break;
            case "edit":
                handleEdit(event);
                break;
            case "delete":
                handleDelete(event);
                break;
            case "info":
                handleInfo(event);
                break;
            case "list":
                handleList(event);
                break;
            case "addtag":
                handleAddTag(event);
                break;
            case "removetag":
                handleRemoveTag(event);
                break;
            default:
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Error",
                    "Unknown subcommand."
                )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }
    
    private void handleCreate(SlashCommandInteractionEvent event) {
        String name = event.getOption("name").getAsString();
        String displayName = event.getOption("displayname") != null ? 
                             event.getOption("displayname").getAsString() : null;
        String avatarUrl = event.getOption("avatar") != null ? 
                          event.getOption("avatar").getAsString() : null;
        String prefix = event.getOption("prefix") != null ? 
                       event.getOption("prefix").getAsString() : null;
        String suffix = event.getOption("suffix") != null ? 
                       event.getOption("suffix").getAsString() : null;
        
        event.deferReply().queue();
        
        // All proxies are global (guildId = null) - work in all servers and DMs
        String guildId = null;
        
        proxyService.createMember(
            event.getUser().getId(),
            guildId,
            name,
            displayName,
            avatarUrl,
            prefix,
            suffix
        ).thenAccept(result -> {
            if (result.startsWith("7")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to create proxy member. Use `/error category:7` for full proxy system documentation."
                )).queue();
            } else {
                ProxyMember member = proxyService.getMember(result);
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(CustomEmojis.SUCCESS + " Proxy Member Created")
                    .setDescription("Successfully created proxy member!")
                    .addField("Name", member.getName(), true)
                    .addField("Display Name", member.getDisplayName(), true)
                    .addField("Scope", CustomEmojis.INFO + " Global (works in all servers and DMs)", true)
                    .addField("ID", member.getMemberId(), false)
                    .setColor(Color.GREEN);
                
                if (member.getAvatarUrl() != null) {
                    embed.setThumbnail(member.getAvatarUrl());
                }
                
                if (!member.getProxyTags().isEmpty()) {
                    StringBuilder tags = new StringBuilder();
                    for (ProxyMember.ProxyTag tag : member.getProxyTags()) {
                        tags.append(tag.toString()).append("\n");
                    }
                    embed.addField("Proxy Tags", tags.toString(), false);
                }
                
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            }
        });
    }
    
    private void handleEdit(SlashCommandInteractionEvent event) {
        String memberName = event.getOption("member").getAsString();
        String field = event.getOption("field").getAsString();
        String value = event.getOption("value").getAsString();
        
        // Use null guildId to search both global and guild-specific
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        
        // Find member
        ProxyMember member = proxyService.getMemberByName(
            event.getUser().getId(),
            guildId,
            memberName
        );
        
        if (member == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Error 710",
                "Proxy member not found. Use `/proxy list` to see your members."
            )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        
        event.deferReply().queue();
        
        proxyService.editMember(member.getMemberId(), field, value).thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Member Updated",
                    "Successfully updated " + field + " for **" + member.getName() + "**"
                )).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to edit proxy member. Use `/error category:7` for full proxy system documentation."
                )).queue();
            }
        });
    }
    
    private void handleDelete(SlashCommandInteractionEvent event) {
        String memberName = event.getOption("member").getAsString();
        
        // Use null guildId to search both global and guild-specific
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        
        // Find member
        ProxyMember member = proxyService.getMemberByName(
            event.getUser().getId(),
            guildId,
            memberName
        );
        
        if (member == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Error 710",
                "Proxy member not found. Use `/proxy list` to see your members."
            )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        
        event.deferReply().queue();
        
        proxyService.deleteMember(member.getMemberId()).thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Member Deleted",
                    "Successfully deleted proxy member **" + member.getName() + "**"
                )).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to delete proxy member. Use `/error category:7` for full proxy system documentation."
                )).queue();
            }
        });
    }
    
    private void handleInfo(SlashCommandInteractionEvent event) {
        String memberName = event.getOption("member").getAsString();
        
        // Use null guildId to search both global and guild-specific
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        
        // Find member
        ProxyMember member = proxyService.getMemberByName(
            event.getUser().getId(),
            guildId,
            memberName
        );
        
        if (member == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Error 710",
                "Proxy member not found. Use `/proxy list` to see your members."
            )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(CustomEmojis.INFO + " " + member.getName())
            .setColor(member.getColor() != null ? Color.decode(member.getColor()) : Color.BLUE)
            .addField("Display Name", member.getDisplayName(), true)
            .addField("ID", member.getMemberId(), true)
            .addField("Scope", CustomEmojis.INFO + " Global (works in all servers and DMs)", true);
        
        if (member.getPronouns() != null) {
            embed.addField("Pronouns", member.getPronouns(), true);
        }
        
        if (member.getDescription() != null) {
            embed.setDescription(member.getDescription());
        }
        
        if (member.getAvatarUrl() != null) {
            embed.setThumbnail(member.getAvatarUrl());
        }
        
        if (!member.getProxyTags().isEmpty()) {
            StringBuilder tags = new StringBuilder();
            int i = 0;
            for (ProxyMember.ProxyTag tag : member.getProxyTags()) {
                tags.append(i++).append(". ").append(tag.toString()).append("\n");
            }
            embed.addField("Proxy Tags", tags.toString(), false);
        }
        
        embed.addField("Keep Proxy Tags", member.isKeepProxy() ? "Yes" : "No", true);
        embed.addField("Created", "<t:" + member.getCreatedAt().getEpochSecond() + ":R>", true);
        
        event.replyEmbeds(embed.build()).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }
    
    private void handleList(SlashCommandInteractionEvent event) {
        // Defer reply immediately to avoid timeout
        event.deferReply(true).queue();
        
        // Use null guildId to get both global and guild-specific proxies
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        
        List<ProxyMember> members = proxyService.getUserMembers(
            event.getUser().getId(),
            guildId
        );
        
        if (members.isEmpty()) {
            event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                "No Members",
                "You don't have any proxy members. Create one with `/proxy create`"
            )).queue();
            return;
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(CustomEmojis.INFO + " Your Proxy Members")
            .setColor(Color.BLUE)
            .setDescription("Total: " + members.size());
        
        StringBuilder list = new StringBuilder();
        for (ProxyMember member : members) {
            list.append("🌐 **").append(member.getName()).append("**");
            if (!member.getProxyTags().isEmpty()) {
                list.append(" - `").append(member.getProxyTags().get(0).toString()).append("`");
            }
            list.append("\n");
        }
        
        embed.addField("Members (all global)", list.toString(), false);
        
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }
    
    private void handleAddTag(SlashCommandInteractionEvent event) {
        String memberName = event.getOption("member").getAsString();
        String prefix = event.getOption("prefix") != null ? 
                       event.getOption("prefix").getAsString() : null;
        String suffix = event.getOption("suffix") != null ? 
                       event.getOption("suffix").getAsString() : null;
        
        // Use null guildId to search both global and guild-specific
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        
        // Find member
        ProxyMember member = proxyService.getMemberByName(
            event.getUser().getId(),
            guildId,
            memberName
        );
        
        if (member == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Error 710",
                "Proxy member not found. Use `/proxy list` to see your members."
            )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        
        event.deferReply().queue();
        
        proxyService.addProxyTag(member.getMemberId(), prefix, suffix).thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                String tagStr = (prefix != null ? prefix : "") + "text" + (suffix != null ? suffix : "");
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Proxy Tag Added",
                    "Added proxy tag `" + tagStr + "` to **" + member.getName() + "**"
                )).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to add proxy tag. Use `/error category:7` for full proxy system documentation."
                )).queue();
            }
        });
    }
    
    private void handleRemoveTag(SlashCommandInteractionEvent event) {
        String memberName = event.getOption("member").getAsString();
        int tagIndex = event.getOption("index").getAsInt();
        
        // Use null guildId to search both global and guild-specific
        String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
        
        // Find member
        ProxyMember member = proxyService.getMemberByName(
            event.getUser().getId(),
            guildId,
            memberName
        );
        
        if (member == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Error 710",
                "Proxy member not found. Use `/proxy list` to see your members."
            )).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }
        
        event.deferReply().queue();
        
        proxyService.removeProxyTag(member.getMemberId(), tagIndex).thenAccept(result -> {
            if (result.equals("SUCCESS")) {
                event.getHook().editOriginalEmbeds(EmbedUtils.createSuccessEmbed(
                    "Proxy Tag Removed",
                    "Removed proxy tag from **" + member.getName() + "**"
                )).queue();
            } else {
                event.getHook().editOriginalEmbeds(EmbedUtils.createErrorEmbed(
                    "Error " + result,
                    "Failed to remove proxy tag. Use `/error category:7` for full proxy system documentation."
                )).queue();
            }
        });
    }
    
    public static CommandData getCommandData() {
        return Commands.slash("proxy", "Manage proxy members (PluralKit-style)")
            .addSubcommands(
                new SubcommandData("create", "Create a new proxy member")
                    .addOption(OptionType.STRING, "name", "Name of the member", true)
                    .addOption(OptionType.STRING, "displayname", "Display name (defaults to name)", false)
                    .addOption(OptionType.STRING, "avatar", "Avatar URL for the member", false)
                    .addOption(OptionType.STRING, "prefix", "Proxy tag prefix (e.g., 'Alice:')", false)
                    .addOption(OptionType.STRING, "suffix", "Proxy tag suffix (e.g., '~alice')", false),
                    
                new SubcommandData("edit", "Edit a proxy member")
                    .addOption(OptionType.STRING, "member", "Name of the member to edit", true)
                    .addOption(OptionType.STRING, "field", "Field to edit (name, displayname, avatar, pronouns, description, color, keepproxy)", true)
                    .addOption(OptionType.STRING, "value", "New value for the field", true),
                    
                new SubcommandData("delete", "Delete a proxy member")
                    .addOption(OptionType.STRING, "member", "Name of the member to delete", true),
                    
                new SubcommandData("info", "View information about a proxy member")
                    .addOption(OptionType.STRING, "member", "Name of the member", true),
                    
                new SubcommandData("list", "List all your proxy members"),
                    
                new SubcommandData("addtag", "Add a proxy tag to a member")
                    .addOption(OptionType.STRING, "member", "Name of the member", true)
                    .addOption(OptionType.STRING, "prefix", "Proxy tag prefix", false)
                    .addOption(OptionType.STRING, "suffix", "Proxy tag suffix", false),
                    
                new SubcommandData("removetag", "Remove a proxy tag from a member")
                    .addOption(OptionType.STRING, "member", "Name of the member", true)
                    .addOption(OptionType.INTEGER, "index", "Index of the tag to remove (use /proxy info to see)", true)
            );
    }
    
    @Override
    public String getName() {
        return "proxy";
    }
    
    @Override
    public String getDescription() {
        return "Manage proxy members (PluralKit-style)";
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
        return false; // Allow in DMs for global proxy management
    }
    
    @Override
    public void handleAutoComplete(CommandAutoCompleteInteractionEvent event) {
        String focusedOption = event.getFocusedOption().getName();
        
        switch (focusedOption) {
            case "member":
                // Get user's proxy members for autocomplete
                String guildId = event.getGuild() != null ? event.getGuild().getId() : null;
                List<ProxyMember> members = proxyService.getUserMembers(event.getUser().getId(), guildId);
                
                // Filter and limit to 25 choices (Discord limit)
                List<Command.Choice> choices = members.stream()
                    .filter(member -> member.getName().toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                    .limit(25)
                    .map(member -> new Command.Choice(member.getName(), member.getName()))
                    .toList();
                
                event.replyChoices(choices).queue();
                break;
        }
    }
}
