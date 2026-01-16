package com.rooster.ranch;

import com.rooster.ranch.command.FarmCommand;
import com.rooster.ranch.command.ProfessionCommand;
import com.rooster.ranch.manager.EconomyManager;
import com.rooster.ranch.manager.FarmManager;
import com.rooster.ranch.manager.MarketManager;
import com.rooster.ranch.manager.ProfessionManager;
import com.rooster.ranch.listener.PlayerListener;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Entry point for the Rooster_Ranch plugin. This class is responsible for
 * initializing worlds, managers, commands and tasks. Upon disabling, the
 * plugin persists data for farms, economy balances and professions.
 */
public class RoosterRanchPlugin extends JavaPlugin {

    private static RoosterRanchPlugin instance;
    private EconomyManager economyManager;
    private ProfessionManager professionManager;
    private FarmManager farmManager;
    private MarketManager marketManager;

    public static RoosterRanchPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        // Load configs and ensure data folder exists
        saveDefaultConfig();
        // Initialize managers
        economyManager = new EconomyManager(this);
        professionManager = new ProfessionManager(this);
        farmManager = new FarmManager(this, economyManager);
        // Initialize market manager after economy manager
        marketManager = new MarketManager(this, economyManager);
        // Create void worlds and copy schematics
        farmManager.createWorlds();
        farmManager.ensureSchematics();
        // Paste market island
        farmManager.pasteMarketIsland();
        // Spawn market vendor in the market world
        if (Bukkit.getWorld("rooster_market") != null) {
            marketManager.spawnVendor(Bukkit.getWorld("rooster_market"));
        }
        // Start repeating tasks
        farmManager.startTasks();
        // Register commands
        PluginCommand farmCmd = getCommand("farm");
        if (farmCmd != null) {
            farmCmd.setExecutor(new FarmCommand(farmManager, professionManager));
        }
        PluginCommand profCmd = getCommand("profession");
        if (profCmd != null) {
            profCmd.setExecutor(new ProfessionCommand(professionManager));
        }
        // Register additional listeners
        new PlayerListener(this, professionManager);
        getLogger().info("Rooster_Ranch enabled successfully.");
    }

    @Override
    public void onDisable() {
        // Save persistent data
        if (farmManager != null) {
            farmManager.stopTasks();
            farmManager.saveFarms();
        }
        if (economyManager != null) {
            economyManager.saveEconomy();
        }
        if (professionManager != null) {
            professionManager.saveProfessions();
        }
        getLogger().info("Rooster_Ranch has been disabled.");
    }
}