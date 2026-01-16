package com.rooster.ranch.manager;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the Rooster Coins (RC) economy for each player. Balances are stored
 * persistently in an economy.yml file inside the plugin's data folder. Simple
 * methods are provided for querying, depositing and withdrawing currency.
 */
public class EconomyManager {

    private final JavaPlugin plugin;
    private final Map<UUID, Double> balances = new HashMap<>();
    private File economyFile;
    private FileConfiguration econConfig;

    public EconomyManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadEconomy();
    }

    /**
     * Load balances from economy.yml. If the file does not exist, it will be
     * created with an empty configuration. Any errors encountered during
     * loading are logged to the console.
     */
    private void loadEconomy() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            economyFile = new File(plugin.getDataFolder(), "economy.yml");
            if (!economyFile.exists()) {
                economyFile.createNewFile();
            }
            econConfig = YamlConfiguration.loadConfiguration(economyFile);
            for (String key : econConfig.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    double balance = econConfig.getDouble(key);
                    balances.put(uuid, balance);
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid UUID entries
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load economy data: " + e.getMessage());
        }
    }

    /**
     * Save balances to economy.yml. If saving fails, a severe log message is
     * printed but the plugin will continue running.
     */
    public void saveEconomy() {
        try {
            if (econConfig == null || economyFile == null) return;
            for (Map.Entry<UUID, Double> entry : balances.entrySet()) {
                econConfig.set(entry.getKey().toString(), entry.getValue());
            }
            econConfig.save(economyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save economy data: " + e.getMessage());
        }
    }

    /**
     * Get the balance of a player. If the player does not yet have an
     * associated balance, a new entry is created with zero RC.
     *
     * @param uuid the player's UUID
     * @return the player's balance
     */
    public double getBalance(@NotNull UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    public double getBalance(@NotNull Player player) {
        return getBalance(player.getUniqueId());
    }

    /**
     * Set a player's balance.
     *
     * @param uuid   the player's UUID
     * @param amount the new balance
     */
    public void setBalance(@NotNull UUID uuid, double amount) {
        balances.put(uuid, Math.max(0.0, amount));
    }

    /**
     * Deposit RC into a player's account.
     *
     * @param uuid   the player's UUID
     * @param amount the amount to deposit
     */
    public void deposit(@NotNull UUID uuid, double amount) {
        if (amount <= 0) return;
        balances.put(uuid, getBalance(uuid) + amount);
    }

    /**
     * Withdraw RC from a player's account.
     *
     * @param uuid   the player's UUID
     * @param amount the amount to withdraw
     * @return true if the withdrawal succeeded
     */
    public boolean withdraw(@NotNull UUID uuid, double amount) {
        double balance = getBalance(uuid);
        if (amount <= 0 || amount > balance) {
            return false;
        }
        balances.put(uuid, balance - amount);
        return true;
    }
}