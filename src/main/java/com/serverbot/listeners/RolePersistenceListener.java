package com.serverbot.listeners;

import com.serverbot.services.RolePersistenceService;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener for handling role persistence when members join/leave
 */
public class RolePersistenceListener extends ListenerAdapter {

    private final RolePersistenceService rolePersistenceService;

    public RolePersistenceListener() {
        this.rolePersistenceService = RolePersistenceService.getInstance();
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        // Save user's roles when they leave
        if (event.getMember() != null) {
            rolePersistenceService.saveUserRoles(event.getMember());
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        // Restore user's roles when they rejoin (with a small delay to ensure member is
        // fully loaded)
        // Schedule the role restoration for 2 seconds later to avoid race conditions
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 2 second delay
                rolePersistenceService.restoreUserRoles(event.getMember());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
}