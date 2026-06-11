package com.fabian.xclearlag;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import com.fabian.xclearlag.commands.*;
import com.fabian.xclearlag.managers.*;
import com.fabian.xclearlag.managers.DependencyManager;
import com.fabian.xclearlag.services.*;
import com.fabian.xclearlag.utils.*;
import com.fabian.xclearlag.utils.scheduler.*;
import com.fabian.xclearlag.utils.DebugLogger;
import com.fabian.xclearlag.api.XClearlagAPI;

/**
 * Main plugin class for X-Clearlag.
 * Refactored for modularity, custom events, and elite-level API.
 */
public class XClearlag extends JavaPlugin {

    private static XClearlag instance;

    public static XClearlag getInstance() {
        return instance;
    }

    public void logInfo(String message) {
        getLogger().info(message);
    }

    public void logWarning(String message) {
        getLogger().warning(message);
    }

    public void logError(String message) {
        getLogger().severe(message);
    }

    private ConfigManager configManager;
    private LanguageManager languageManager;
    private TaskManager taskManager;
    private UpdateChecker updateChecker;
    private TPSMonitor tpsMonitor;
    private Object tpsMonitorTask;
    private TpsCleanupService tpsCleanupService;
    private SchedulerAdapter schedulerAdapter;
    
    private MetricsTracker metricsTracker;
    private CommandDispatcher commandDispatcher;
    private ClearExecutor clearExecutor;
    private BossBarManager bossBarManager;
    private CleanupNotifier cleanupNotifier;

    @Override
    public void onEnable() {
        try {
            DebugLogger.debug("Init", "Loading dependencies...");
            // Load libraries before anything else
            new DependencyManager(this).loadDependencies();

            instance = this;
            DebugLogger.debug("Init", "Instance set, initializing API...");
            XClearlagAPI.init(this);
            
            DebugLogger.debug("Init", "Initializing scheduler...");
            initScheduler();
            DebugLogger.debug("Init", "Initializing managers...");
            initManagers();
            DebugLogger.debug("Init", "Initializing services...");
            initServices();
            DebugLogger.debug("Init", "Initializing commands...");
            initCommands();
            
            // PlaceholderAPI Integration
            if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
                new XPlaceholderExpansion(this).register();
                logInfo("Found PlaceholderAPI! Custom placeholders registered.");
                DebugLogger.debug("Init", "PlaceholderAPI expansion registered.");
            }
            
            DebugLogger.debug("Init", "X-Clearlag v" + getDescription().getVersion() + " fully initialized.");
            logInfo("X-Clearlag v" + getDescription().getVersion() + " initialized successfully.");
        } catch (Exception e) {
            logError("CRITICAL FAILURE DURING STARTUP: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void initManagers() {
        logInfo("Loading managers...");
        DebugLogger.debug("Managers", "Creating ConfigManager...");
        configManager = new ConfigManager(this);
        try {
            configManager.load();
        } catch (Exception e) {
            logError("Failed to load config: " + e.getMessage());
        }

        DebugLogger.debug("Managers", "Creating LanguageManager...");
        languageManager = new LanguageManager(this);
        try {
            languageManager.load();
        } catch (Exception e) {
            logError("Failed to load messages: " + e.getMessage());
        }
        
        DebugLogger.debug("Managers", "Creating TaskManager...");
        taskManager = new TaskManager(this);
    }

    private void initServices() {
        logInfo("Initializing services...");
        
        // 1. Core Utilities
        DebugLogger.debug("Services", "Creating TPSMonitor...");
        tpsMonitor = new TPSMonitor();
        tpsMonitorTask = schedulerAdapter.runTaskTimer(tpsMonitor, 1L, 1L);
        DebugLogger.debug("Services", "TPSMonitor scheduled (every tick).");
        
        commandDispatcher = new CommandDispatcher(this);
        bossBarManager = new BossBarManager(configManager);
        cleanupNotifier = new CleanupNotifier(messageManager, bossBarManager, getLogger());
        
        // 2. Functional Services
        metricsTracker = new MetricsTracker(tpsMonitor);
        clearExecutor = new ClearExecutor(this, configManager);
        DebugLogger.debug("Services", "ClearExecutor and MetricsTracker created.");

        // 3. Lifecycle Managers
        DebugLogger.debug("Services", "Loading tasks...");
        taskManager.loadTasks();
        
        tpsCleanupService = new TpsCleanupService(this, configManager, tpsMonitor, taskManager, schedulerAdapter);
        tpsCleanupService.start();
        DebugLogger.debug("Services", "TpsCleanupService started.");

        updateChecker = new UpdateChecker(this);
        if (configManager.get().general.checkUpdates) {
            schedulerAdapter.runTaskLater(() -> updateChecker.checkForUpdates(), 100L);
            DebugLogger.debug("Services", "Update check scheduled (delayed 5s).");
        }
    }

    private void initCommands() {
        logInfo("Registering commands...");
        XClearlagCommand commandHandler = new XClearlagCommand(this);
        org.bukkit.command.PluginCommand xclCmd = getCommand("xcl");
        if (xclCmd != null) {
            xclCmd.setExecutor(commandHandler);
            xclCmd.setTabCompleter(commandHandler);
        }
    }

    @Override
    public void onDisable() {
        DebugLogger.debug("Shutdown", "Disabling X-Clearlag...");
        if (taskManager != null) { taskManager.stopAll(); DebugLogger.debug("Shutdown", "All tasks stopped."); }
        if (tpsCleanupService != null) { tpsCleanupService.stop(); DebugLogger.debug("Shutdown", "TpsCleanupService stopped."); }
        if (tpsMonitorTask != null) { schedulerAdapter.cancelTask(tpsMonitorTask); DebugLogger.debug("Shutdown", "TPSMonitor task cancelled."); }
        if (bossBarManager != null) { bossBarManager.hide(); DebugLogger.debug("Shutdown", "BossBar hidden."); }
        DebugLogger.debug("Shutdown", "X-Clearlag disabled.");
    }

    public void reload() {
        try {
            DebugLogger.debug("Reload", "Reloading X-Clearlag...");
            reloadConfig();
            configManager.load();
            messageManager.load();
            metricsTracker.reset();
            taskManager.loadTasks();
            if (tpsCleanupService != null) {
                tpsCleanupService.stop();
                tpsCleanupService.start();
            }
            DebugLogger.debug("Reload", "Reload complete.");
            logInfo("X-Clearlag reloaded successfully.");
        } catch (Exception e) {
            logError("Failed to reload plugin: " + e.getMessage());
        }
    }

    private void initScheduler() {
        DebugLogger.debug("Scheduler", "Detecting server type...");
        boolean isFolia = false;
        try {
            // Check for Folia by looking for the GlobalRegionScheduler method
            // This is more reliable than Class.forName across different versions
            Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            isFolia = true;
        } catch (Throwable ignored) {
            // Fallback to class check
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (Throwable ignored2) {}
        }

        if (isFolia) {
            schedulerAdapter = new FoliaSchedulerAdapter(this);
            logInfo("Folia detected! Using regional scheduler adapter.");
            DebugLogger.debug("Scheduler", "Folia detected, using FoliaSchedulerAdapter.");
        } else {
            schedulerAdapter = new BukkitSchedulerAdapter(this);
            logInfo("Standard Bukkit/Paper detected! Using standard scheduler adapter.");
            DebugLogger.debug("Scheduler", "Standard Bukkit/Paper detected, using BukkitSchedulerAdapter.");
        }
    }

    public SchedulerAdapter getSchedulerAdapter() { return schedulerAdapter; }
    public ConfigManager getConfigManager() { return configManager; }
    public LanguageManager getLanguageManager() { return languageManager; }
    public TaskManager getTaskManager() { return taskManager; }
    public UpdateChecker getUpdateChecker() { return updateChecker; }
    public TPSMonitor getTpsMonitor() { return tpsMonitor; }
    public MetricsTracker getMetricsTracker() { return metricsTracker; }
    public CommandDispatcher getCommandDispatcher() { return commandDispatcher; }
    public ClearExecutor getClearExecutor() { return clearExecutor; }
    public BossBarManager getBossBarManager() { return bossBarManager; }
    public CleanupNotifier getCleanupNotifier() { return cleanupNotifier; }
}
