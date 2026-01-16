package com.rooster.ranch.manager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles player profession selection and persistence. Players choose a
 * profession via a simple GUI; each profession grants a unique starter kit.
 * Professions are saved to a YAML file and loaded when the plugin starts.
 */
public class ProfessionManager implements Listener {
    private final JavaPlugin plugin;
    private final Map<UUID, Profession> professions = new HashMap<>();
    private File professionFile;
    private FileConfiguration professionConfig;

    public ProfessionManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadProfessions();
        // Register this manager as an event listener for inventory clicks
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Opens the profession selection GUI for a player. Each profession is
     * represented by a unique item with a display name. Clicking an item will
     * assign that profession to the player and close the inventory.
     *
     * @param player the player to present with the GUI
     */
    public void openProfessionGUI(@NotNull Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, "Select Profession");
        int slot = 1;
        for (Profession profession : Profession.values()) {
            Material mat;
            // Choose a representative item for each profession
            switch (profession) {
                case FARMER -> mat = Material.WHEAT;
                case RANCHER -> mat = Material.COW_SPAWN_EGG;
                case FISHER -> mat = Material.FISHING_ROD;
                case MERCHANT -> mat = Material.EMERALD;
                default -> mat = Material.BOOK;
            }
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(profession.getDisplayName());
            item.setItemMeta(meta);
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Use the InventoryView and title component; Inventory#getTitle() was removed in newer Paper versions.
        InventoryView view = event.getView();
        Component title = view.title();
        if (!Component.text("Select Profession").equals(title)) {
            return;
        }
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String name = clicked.getItemMeta().getDisplayName();
        for (Profession profession : Profession.values()) {
            if (profession.getDisplayName().equals(name)) {
                setProfession(player.getUniqueId(), profession);
                // Give starter kit
                for (ItemStack kitItem : profession.getStarterKit()) {
                    if (kitItem != null) {
                        player.getInventory().addItem(kitItem.clone());
                    }
                }
                player.sendMessage("§aYou are now a " + profession.getDisplayName() + "!");
                player.closeInventory();
                return;
            }
        }
    }

    /**
     * Retrieves a player's profession.
     *
     * @param uuid the player's UUID
     * @return the profession or null if none chosen
     */
    public Profession getProfession(@NotNull UUID uuid) {
        return professions.get(uuid);
    }

    /**
     * Assigns a profession to a player.
     *
     * @param uuid       the player's UUID
     * @param profession the profession to assign
     */
    public void setProfession(@NotNull UUID uuid, @NotNull Profession profession) {
        professions.put(uuid, profession);
    }

    private void loadProfessions() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            professionFile = new File(plugin.getDataFolder(), "professions.yml");
            if (!professionFile.exists()) {
                professionFile.createNewFile();
            }
            professionConfig = YamlConfiguration.loadConfiguration(professionFile);
            for (String key : professionConfig.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String profName = professionConfig.getString(key);
                    Profession profession = Profession.valueOf(profName);
                    professions.put(uuid, profession);
                } catch (IllegalArgumentException ignored) {
                    // Skip invalid entries
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load professions: " + e.getMessage());
        }
    }

    public void saveProfessions() {
        try {
            if (professionConfig == null || professionFile == null) return;
            for (Map.Entry<UUID, Profession> entry : professions.entrySet()) {
                professionConfig.set(entry.getKey().toString(), entry.getValue().name());
            }
            professionConfig.save(professionFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save professions: " + e.getMessage());
        }
    }
}