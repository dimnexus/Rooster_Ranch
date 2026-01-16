package com.rooster.ranch.manager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the community market functionality. A single market vendor is spawned
 * in the market world and offers a range of goods to buy or sell. Players can
 * interact with the vendor to open a custom GUI. Prices are defined in this
 * class and transactions use the {@link EconomyManager} for RC balance
 * adjustments.
 */
public class MarketManager implements Listener {
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final Map<Material, Double> buyPrices = new HashMap<>();
    private final Map<Material, Double> sellPrices = new HashMap<>();
    private Inventory buyInventory;
    private Inventory sellInventory;
    private NamespacedKey priceKey;

    public MarketManager(JavaPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        // Define buy prices (players pay RC to obtain the item)
        buyPrices.put(Material.WHEAT_SEEDS, 2.0);
        buyPrices.put(Material.CARROT, 3.0);
        buyPrices.put(Material.POTATO, 3.0);
        buyPrices.put(Material.COW_SPAWN_EGG, 50.0);
        buyPrices.put(Material.CHICKEN_SPAWN_EGG, 20.0);
        buyPrices.put(Material.SHEEP_SPAWN_EGG, 30.0);
        // Milk bucket constant changed name in newer versions (Paper 1.21)
        buyPrices.put(Material.MILK_BUCKET, 5.0);
        buyPrices.put(Material.BREAD, 4.0);
        // Define sell prices (players get RC when selling)
        sellPrices.put(Material.WHEAT, 1.0);
        sellPrices.put(Material.CARROT, 1.5);
        sellPrices.put(Material.POTATO, 1.5);
        sellPrices.put(Material.EGG, 0.5);
        sellPrices.put(Material.BEEF, 2.0);
        // Build GUI inventories
        buildInventories();
        this.priceKey = new NamespacedKey(plugin, "price");
        // Register listeners
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Spawns the market vendor NPC in the market world. The NPC is a villager
     * without AI and with a custom name so players can easily find and
     * interact with the market. If the vendor already exists, this method
     * returns without creating a duplicate.
     */
    public void spawnVendor(@NotNull org.bukkit.World marketWorld) {
        // Check if a vendor already exists
        for (var entity : marketWorld.getEntities()) {
            if (entity instanceof Villager villager && villager.customName() != null
                    && villager.customName().equals(Component.text("Market Vendor", NamedTextColor.GOLD))) {
                return; // Vendor already present
            }
        }
        // Spawn new vendor on the market walkway. Determine the highest solid block at
        // the desired X/Z coordinate (approx. x=16, z=-5) and place the villager one
        // block above. This ensures the vendor stands on the path rather than
        // floating in mid‑air.
        int spawnX = 16;
        int spawnZ = -5;
        // Drop the vendor 9 blocks lower than the default highest‑block spawn. This
        // prevents the villager from standing in the tree canopy.
        int highestY = marketWorld.getHighestBlockYAt(spawnX, spawnZ);
        int spawnY = highestY - 8; // highestY + 1 (original) - 9 = highestY - 8
        var loc = new org.bukkit.Location(marketWorld, spawnX + 0.5, spawnY, spawnZ + 0.5);
        Villager villager = (Villager) marketWorld.spawnEntity(loc, EntityType.VILLAGER);
        villager.setAI(false);
        villager.setInvulnerable(true);
        villager.setCustomNameVisible(true);
        villager.customName(Component.text("Market Vendor", NamedTextColor.GOLD));
        villager.setVillagerLevel(1);
        villager.setVillagerType(Villager.Type.PLAINS);
        // Rotate the vendor to face towards the main path (yaw 180 degrees). Adjust
        // this value if you change the vendor's position.
        villager.setRotation(180f, 0f);
    }

    /**
     * Build the buy and sell inventories. Each slot contains an item with a
     * display name and lore containing its price. We attach a PersistentData
     * key to store the price on the item, allowing us to determine cost when
     * clicked without referencing maps.
     */
    private void buildInventories() {
        buyInventory = Bukkit.createInventory(null, 27, "Market: Buy Items");
        sellInventory = Bukkit.createInventory(null, 27, "Market: Sell Items");
        // Populate buy inventory
        int buySlot = 0;
        for (Map.Entry<Material, Double> entry : buyPrices.entrySet()) {
            ItemStack item = new ItemStack(entry.getKey());
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.displayName(Component.text(entry.getKey().name(), NamedTextColor.GREEN));
            java.util.List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.text("Price: " + entry.getValue() + " RC", NamedTextColor.YELLOW));
            meta.lore(lore);
            // Store price in persistent data
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "price"), PersistentDataType.DOUBLE, entry.getValue());
            // Mark as buy item
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "type"), PersistentDataType.STRING, "buy");
            item.setItemMeta(meta);
            buyInventory.setItem(buySlot++, item);
        }
        // Populate sell inventory
        int sellSlot = 0;
        for (Map.Entry<Material, Double> entry : sellPrices.entrySet()) {
            ItemStack item = new ItemStack(entry.getKey());
            ItemMeta meta = item.getItemMeta();
            assert meta != null;
            meta.displayName(Component.text(entry.getKey().name(), NamedTextColor.RED));
            java.util.List<Component> lore = new java.util.ArrayList<>();
            lore.add(Component.text("Sell: " + entry.getValue() + " RC per item", NamedTextColor.YELLOW));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "price"), PersistentDataType.DOUBLE, entry.getValue());
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "type"), PersistentDataType.STRING, "sell");
            item.setItemMeta(meta);
            sellInventory.setItem(sellSlot++, item);
        }
    }

    /**
     * Open the market GUI (buy or sell) for a player. Players right-click
     * the vendor to open the buy inventory. A button in the corner toggles
     * between buying and selling.
     */
    private void openMarketGUI(Player player, boolean selling) {
        if (selling) {
            player.openInventory(sellInventory);
        } else {
            player.openInventory(buyInventory);
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        if (villager.customName() == null) return;
        if (!villager.customName().equals(Component.text("Market Vendor", NamedTextColor.GOLD))) return;
        event.setCancelled(true);
        openMarketGUI(event.getPlayer(), false);
    }

    /**
     * Handle clicks in the market inventories. If the player clicks on a buy
     * item, we attempt to withdraw RC equal to the price and give the item.
     * If the player clicks on a sell item, we remove one item from the
     * player's inventory and deposit RC.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (view == null) return;
        String title = view.getTitle();
        if (!title.startsWith("Market: ")) return;
        event.setCancelled(true);
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Double price = data.get(new NamespacedKey(plugin, "price"), PersistentDataType.DOUBLE);
        String type = data.get(new NamespacedKey(plugin, "type"), PersistentDataType.STRING);
        if (price == null || type == null) return;
        UUID uuid = player.getUniqueId();
        switch (type) {
            case "buy" -> {
                double balance = economyManager.getBalance(uuid);
                if (balance < price) {
                    player.sendMessage(ChatColor.RED + "You do not have enough RC to buy this.");
                    return;
                }
                // Deduct and give
                economyManager.withdraw(uuid, price);
                ItemStack toGive = new ItemStack(clicked.getType());
                player.getInventory().addItem(toGive);
                player.sendMessage(ChatColor.GREEN + "Purchased " + clicked.getType().name() + " for " + price + " RC.");
            }
            case "sell" -> {
                Material sellMat = clicked.getType();
                // Check if player has at least one item
                int count = 0;
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == sellMat) {
                        count += item.getAmount();
                    }
                }
                if (count <= 0) {
                    player.sendMessage(ChatColor.RED + "You have none of that item to sell.");
                    return;
                }
                // Remove one item and deposit price
                player.getInventory().removeItem(new ItemStack(sellMat, 1));
                economyManager.deposit(uuid, price);
                player.sendMessage(ChatColor.GREEN + "Sold 1 " + sellMat.name() + " for " + price + " RC.");
            }
            default -> {
                // nothing
            }
        }
    }
}