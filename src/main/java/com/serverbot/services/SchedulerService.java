package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorHandler;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.io.*;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Service for handling scheduled tasks like temporary bans, mutes, and other
 * time-based punishments
 */
public class SchedulerService {
    private static SchedulerService instance;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private JDA jda;

    private static final String DATA_DIR = "data";
    private static final String SCHEDULED_TASKS_FILE = "scheduled_tasks.json";

    private SchedulerService() {
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.gson = new Gson();

        // Create data directory if it doesn't exist
        new File(DATA_DIR).mkdirs();

        // Start the task checker that runs every minute
        scheduler.scheduleAtFixedRate(this::processScheduledTasks, 1, 1, TimeUnit.MINUTES);
    }

    public static SchedulerService getInstance() {
        if (instance == null) {
            instance = new SchedulerService();
        }
        return instance;
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    /**
     * Schedule a temporary ban that will be automatically lifted
     */
    public void scheduleUnban(String guildId, String userId, String reason, long unbanTimestamp) {
        ScheduledTask task = new ScheduledTask();
        task.id = generateTaskId();
        task.type = TaskType.UNBAN;
        task.guildId = guildId;
        task.userId = userId;
        task.reason = reason;
        task.executeAt = unbanTimestamp;
        task.createdAt = System.currentTimeMillis();

        addScheduledTask(task);
    }

    /**
     * Schedule a temporary mute that will be automatically lifted
     */
    public void scheduleUnmute(String guildId, String userId, String reason, long unmuteTimestamp) {
        ScheduledTask task = new ScheduledTask();
        task.id = generateTaskId();
        task.type = TaskType.UNMUTE;
        task.guildId = guildId;
        task.userId = userId;
        task.reason = reason;
        task.executeAt = unmuteTimestamp;
        task.createdAt = System.currentTimeMillis();

        addScheduledTask(task);
    }

    /**
     * Cancel all scheduled tasks for a specific user (useful when manually
     * unbanning/unmuting)
     */
    public boolean cancelUserTasks(String guildId, String userId, TaskType taskType) {
        List<ScheduledTask> tasks = getScheduledTasks();
        boolean removed = false;

        Iterator<ScheduledTask> iterator = tasks.iterator();
        while (iterator.hasNext()) {
            ScheduledTask task = iterator.next();
            if (task.guildId.equals(guildId) && task.userId.equals(userId) && task.type == taskType) {
                iterator.remove();
                removed = true;
            }
        }

        if (removed) {
            saveScheduledTasks(tasks);
        }

        return removed;
    }

    /**
     * Get remaining time for a user's punishment
     */
    public long getRemainingTime(String guildId, String userId, TaskType taskType) {
        List<ScheduledTask> tasks = getScheduledTasks();

        for (ScheduledTask task : tasks) {
            if (task.guildId.equals(guildId) && task.userId.equals(userId) && task.type == taskType) {
                return Math.max(0, task.executeAt - System.currentTimeMillis());
            }
        }

        return 0;
    }

    private void processScheduledTasks() {
        if (jda == null)
            return;

        List<ScheduledTask> tasks = getScheduledTasks();
        List<ScheduledTask> completedTasks = new ArrayList<>();
        long currentTime = System.currentTimeMillis();

        for (ScheduledTask task : tasks) {
            if (task.executeAt <= currentTime) {
                executeTask(task);
                completedTasks.add(task);
            }
        }

        if (!completedTasks.isEmpty()) {
            tasks.removeAll(completedTasks);
            saveScheduledTasks(tasks);
        }
    }

    private void executeTask(ScheduledTask task) {
        try {
            Guild guild = jda.getGuildById(task.guildId);
            if (guild == null)
                return;

            switch (task.type) {
                case UNBAN:
                    executeUnban(guild, task);
                    break;
                case UNMUTE:
                    executeUnmute(guild, task);
                    break;
            }
        } catch (Exception e) {
            System.err.println("Failed to execute scheduled task: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeUnban(Guild guild, ScheduledTask task) {
        guild.unban(User.fromId(task.userId))
                .reason("Temporary ban expired - " + task.reason)
                .queue(
                        success -> System.out.println("Auto-unbanned user " + task.userId + " from " + guild.getName()),
                        new ErrorHandler()
                                .ignore(ErrorResponse.UNKNOWN_BAN)
                                .handle(ErrorResponse.MISSING_PERMISSIONS,
                                        (response) -> System.err.println("Missing permissions to unban user "
                                                + task.userId + " in " + guild.getName())));
    }

    private void executeUnmute(Guild guild, ScheduledTask task) {
        // Implementation for unmuting (removing timeout)
        guild.retrieveMemberById(task.userId).queue(
                member -> {
                    if (member.isTimedOut()) {
                        member.removeTimeout()
                                .reason("Temporary mute expired - " + task.reason)
                                .queue(
                                        success -> System.out.println(
                                                "Auto-unmuted user " + task.userId + " from " + guild.getName()),
                                        new ErrorHandler()
                                                .ignore(ErrorResponse.UNKNOWN_MEMBER)
                                                .handle(ErrorResponse.MISSING_PERMISSIONS,
                                                        (response) -> System.err
                                                                .println("Missing permissions to unmute user "
                                                                        + task.userId + " in " + guild.getName())));
                    }
                },
                new ErrorHandler().ignore(ErrorResponse.UNKNOWN_MEMBER));
    }

    private void addScheduledTask(ScheduledTask task) {
        List<ScheduledTask> tasks = getScheduledTasks();
        tasks.add(task);
        saveScheduledTasks(tasks);
    }

    private List<ScheduledTask> getScheduledTasks() {
        try {
            File file = new File(DATA_DIR, SCHEDULED_TASKS_FILE);
            if (!file.exists()) {
                return new ArrayList<>();
            }

            Type listType = new TypeToken<List<ScheduledTask>>() {
            }.getType();
            List<ScheduledTask> tasks = gson.fromJson(new FileReader(file), listType);
            return tasks != null ? tasks : new ArrayList<>();
        } catch (IOException | JsonSyntaxException e) {
            System.err.println("Failed to load scheduled tasks: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveScheduledTasks(List<ScheduledTask> tasks) {
        try {
            File file = new File(DATA_DIR, SCHEDULED_TASKS_FILE);
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(tasks, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save scheduled tasks: " + e.getMessage());
        }
    }

    private String generateTaskId() {
        return "task_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    // Inner classes
    public static class ScheduledTask {
        public String id;
        public TaskType type;
        public String guildId;
        public String userId;
        public String reason;
        public long executeAt;
        public long createdAt;
    }

    public enum TaskType {
        UNBAN,
        UNMUTE
    }
}