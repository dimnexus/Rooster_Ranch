package com.rooster.ranch.manager;

import com.rooster.ranch.farm.Farm;
import com.rooster.ranch.util.VoidChunkGenerator;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Manages player farms, island creation, schematic pasting and scoreboard
 * updating. Farms are stored in a YAML file and loaded on plugin start. A
 * repeating task updates each player's sidebar while in farm or market worlds.
 */
public class FarmManager implements Listener {
    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final Map<UUID, Farm> farms = new HashMap<>();
    private World farmWorld;
    private World marketWorld;
    private File farmsFile;
    private FileConfiguration farmsConfig;
    private BukkitTask scoreboardTask;
    private BukkitTask weedTask;
    // Offsets for placing new islands so they don't overlap (each island spaced 200 blocks apart)
    private int nextIslandIndex = 0;
    // Scoreboards for players in farm and market worlds
    private final Map<UUID, Scoreboard> playerFarmBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> playerMarketBoards = new HashMap<>();

    public FarmManager(JavaPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadFarms();
    }

    /**
     * Removes unwanted vegetation and closes trapdoors on a pasted island. This method scans
     * a small area around the given center and removes plants such as grass and flowers
     * while leaving the main tree intact. Trapdoors are closed to ensure a consistent
     * starting state. This cleanup runs synchronously after the schematic is pasted.
     *
     * @param center the centre of the island
     */
    /**
     * Remove unwanted vegetation, crops and trapdoors and reinforce the island.
     *
     * This method cleans up the pasted island so that players start with a blank farm.
     * It removes flowers, tall grass and other decorative plants, converts any existing
     * farmland back into plain dirt, removes pre‑planted crops, and closes any
     * trapdoors that might be open. It also fills holes beneath the surface with
     * dirt to provide a second supporting layer under the island, preventing water
     * or players from falling into the void. The scan radius and height range are
     * deliberately generous to cover most of the schematic but can be adjusted
     * if you change the island size.
     *
     * @param center the centre location of the island paste
     */
    private void cleanupIsland(@NotNull Location center) {
        World world = center.getWorld();
        if (world == null) return;
        // Radii around the centre to scan. Increase if your schematic is larger.
        int radiusX = 20;
        int radiusZ = 20;
        int baseY = center.getBlockY();
        int minY = baseY - 3;
        int maxY = baseY + 8;
        // First pass: simply close any trapdoors found in the island area. We do not
        // remove plants or crops so that the original island decorations remain intact.
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = world.getBlockAt(center.getBlockX() + dx, y, center.getBlockZ() + dz);
                    var data = block.getBlockData();
                    if (data instanceof org.bukkit.block.data.type.TrapDoor trap) {
                        if (trap.isOpen()) {
                            trap.setOpen(false);
                            block.setBlockData(trap, false);
                        }
                    }
                }
            }
        }
        // Second pass: add a supporting layer of dirt only directly beneath the existing
        // island footprint. We locate positions on the surface (baseY) that are not
        // air or water and then fill one or two blocks below those positions with dirt.
        for (int dx = -radiusX; dx <= radiusX; dx++) {
            for (int dz = -radiusZ; dz <= radiusZ; dz++) {
                Block surface = world.getBlockAt(center.getBlockX() + dx, baseY, center.getBlockZ() + dz);
                Material surfaceType = surface.getType();
                if (surfaceType != Material.AIR && surfaceType != Material.WATER) {
                    // Fill one and two blocks below the surface if those spaces are empty or water
                    for (int depth = 1; depth <= 2; depth++) {
                        Block below = surface.getRelative(0, -depth, 0);
                        Material belowType = below.getType();
                        if (belowType == Material.AIR || belowType == Material.WATER) {
                            below.setType(Material.DIRT, false);
                        }
                    }
                }
            }
        }
    }

    /**
     * Load farm data from farms.yml. Each farm entry includes the owner, center
     * location coordinates, statistics and trust lists. Missing or corrupt
     * entries are skipped with a warning in the console.
     */
    private void loadFarms() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            farmsFile = new File(plugin.getDataFolder(), "farms.yml");
            if (!farmsFile.exists()) {
                farmsFile.createNewFile();
            }
            farmsConfig = YamlConfiguration.loadConfiguration(farmsFile);
            // Load next island index
            this.nextIslandIndex = farmsConfig.getInt("nextIslandIndex", 0);
            if (farmsConfig.isConfigurationSection("farms")) {
                for (String key : farmsConfig.getConfigurationSection("farms").getKeys(false)) {
                    try {
                        UUID owner = UUID.fromString(key);
                        String worldName = farmsConfig.getString("farms." + key + ".world", "rooster_farms");
                        double x = farmsConfig.getDouble("farms." + key + ".x");
                        double y = farmsConfig.getDouble("farms." + key + ".y");
                        double z = farmsConfig.getDouble("farms." + key + ".z");
                        Location loc = new Location(Bukkit.getWorld(worldName), x, y, z);
                        Farm farm = new Farm(owner, loc);
                        farm.setWeedCount(farmsConfig.getInt("farms." + key + ".weed", 0));
                        farm.setUpkeep(farmsConfig.getDouble("farms." + key + ".upkeep", 100.0));
                        farm.setCropHealth(farmsConfig.getDouble("farms." + key + ".crop", 100.0));
                        farm.setAnimalHealth(farmsConfig.getDouble("farms." + key + ".animal", 100.0));
                        // Load trusted list
                        List<String> trustedList = farmsConfig.getStringList("farms." + key + ".trusted");
                        for (String s : trustedList) {
                            try {
                                farm.getTrusted().add(UUID.fromString(s));
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                        farms.put(owner, farm);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to load farm for key " + key + ": " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load farms: " + e.getMessage());
        }
    }

    /**
     * Save farm data to farms.yml. The nextIslandIndex and each farm's fields
     * are written to disk. Any errors are logged but not propagated.
     */
    public void saveFarms() {
        try {
            if (farmsConfig == null || farmsFile == null) return;
            farmsConfig.set("nextIslandIndex", this.nextIslandIndex);
            farmsConfig.set("farms", null);
            for (Map.Entry<UUID, Farm> entry : farms.entrySet()) {
                String key = entry.getKey().toString();
                Farm farm = entry.getValue();
                farmsConfig.set("farms." + key + ".world", farm.getCenter().getWorld().getName());
                farmsConfig.set("farms." + key + ".x", farm.getCenter().getX());
                farmsConfig.set("farms." + key + ".y", farm.getCenter().getY());
                farmsConfig.set("farms." + key + ".z", farm.getCenter().getZ());
                farmsConfig.set("farms." + key + ".weed", farm.getWeedCount());
                farmsConfig.set("farms." + key + ".upkeep", farm.getUpkeep());
                farmsConfig.set("farms." + key + ".crop", farm.getCropHealth());
                farmsConfig.set("farms." + key + ".animal", farm.getAnimalHealth());
                List<String> trustedList = new ArrayList<>();
                for (UUID uid : farm.getTrusted()) {
                    trustedList.add(uid.toString());
                }
                farmsConfig.set("farms." + key + ".trusted", trustedList);
            }
            farmsConfig.save(farmsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save farms: " + e.getMessage());
        }
    }

    /**
     * Creates the farm and market worlds if they do not already exist. These
     * worlds are void worlds generated by {@link VoidChunkGenerator}. After
     * creation, gamerules are set to disable mob spawning and daylight cycles.
     */
    public void createWorlds() {
        if (Bukkit.getWorld("rooster_farms") == null) {
            WorldCreator creator = new WorldCreator("rooster_farms");
            creator.environment(World.Environment.NORMAL);
            creator.generator(new VoidChunkGenerator());
            farmWorld = creator.createWorld();
            if (farmWorld != null) {
                farmWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                farmWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                farmWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
                // Set spawn location for new players: 17 101 14 facing west (yaw 90)
                Location spawn = new Location(farmWorld, 17.5, 101.0, 14.5, 90f, 0f);
                farmWorld.setSpawnLocation(spawn);
            }
        } else {
            farmWorld = Bukkit.getWorld("rooster_farms");
            if (farmWorld != null) {
                // Ensure spawn location is correct when world already exists
                Location spawn = new Location(farmWorld, 17.5, 101.0, 14.5, 90f, 0f);
                farmWorld.setSpawnLocation(spawn);
                farmWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                farmWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                farmWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
            }
        }

        if (Bukkit.getWorld("rooster_market") == null) {
            WorldCreator creator = new WorldCreator("rooster_market");
            creator.environment(World.Environment.NORMAL);
            creator.generator(new VoidChunkGenerator());
            marketWorld = creator.createWorld();
            if (marketWorld != null) {
                marketWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                marketWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                marketWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
                // Set a safe spawn on the market walkway. We choose coordinates based on
                // the schematic: approximately x=16, z=-5 at y=93. Spawn one block
                // above to avoid suffocation.
                Location mSpawn = new Location(marketWorld, 16.5, 94.0, -5.5, 180f, 0f);
                marketWorld.setSpawnLocation(mSpawn);
            }
        } else {
            marketWorld = Bukkit.getWorld("rooster_market");
            if (marketWorld != null) {
                // Ensure spawn location remains on the walkway when reloading
                Location mSpawn = new Location(marketWorld, 16.5, 94.0, -5.5, 180f, 0f);
                marketWorld.setSpawnLocation(mSpawn);
                marketWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
                marketWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
                marketWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
            }
        }
    }

    /**
     * Ensures the schematic files packaged in the jar are copied to the data
     * folder. This method should be called before attempting to paste any
     * schematics.
     */
    public void ensureSchematics() {
        // Ensure the schematics folder exists
        File schemDir = new File(plugin.getDataFolder(), "schematics");
        if (!schemDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            schemDir.mkdirs();
        }

        // Clean up old/experimental schematics so we don't accidentally paste the wrong one.
        // Keep only the market and the authoritative farm schematic.
        String keepMarket = "market.schem";
        String keepFarm = "rooster_farm_good.schem";
        File[] files = schemDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                String name = f.getName();
                if (!name.equalsIgnoreCase(keepMarket) && !name.equalsIgnoreCase(keepFarm)) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }

        // Copy packaged schematics into the data folder.
        plugin.saveResource("schematics/market.schem", true);
        // Authoritative farm island schematic (same for all players) — overwrite=true.
        plugin.saveResource("schematics/rooster_farm_good.schem", true);
    }

    /**
     * Pastes the market schematic into the market world at the origin. This
     * method should be called once on plugin enable. The paste is synchronous
     * and uses WorldEdit's API to perform the operation.
     */
    public void pasteMarketIsland() {
        if (marketWorld == null) return;
        File schematic = new File(plugin.getDataFolder(), "schematics/market.schem");
        Location location = new Location(marketWorld, 0, 100, 0);
        pasteSchematic(schematic, location);
    }

    /**
     * Create a new farm for a player. Each farm island is spaced 200 blocks
     * apart along the X axis. If the player already owns a farm, the existing
     * farm is returned. Otherwise, a schematic is pasted at the next island
     * coordinate and the farm is recorded. Players are teleported to the new
     * island.
     *
     * @param player the player to create a farm for
     * @return the newly created farm or existing one
     */
    public Farm createFarm(@NotNull Player player) {
        Farm existing = farms.get(player.getUniqueId());
        if (existing != null) {
            return existing;
        }
        if (farmWorld == null) {
            createWorlds();
        }
        // Determine new island location based on nextIslandIndex
        int spacing = 200;
        double x = nextIslandIndex * spacing;
        double y = 100;
        double z = 0;
        nextIslandIndex++;
        Location center = new Location(farmWorld, x, y, z);
        // Paste the player's island schematic
        File schematic = new File(plugin.getDataFolder(), "schematics/rooster_farm_good.schem");
        pasteSchematic(schematic, center);
        // Remove vegetation and close trapdoors for a clean starting island
        cleanupIsland(center);
        Farm farm = new Farm(player.getUniqueId(), center);
        farms.put(player.getUniqueId(), farm);
        // Give player some starting RC
        economyManager.deposit(player.getUniqueId(), 50.0);
        // Give the player a Farming Handbook – a book containing helpful
        // information about how to use their island. The book explains core
        // mechanics like upkeep, weeds, seasons and the market. This is
        // purely informational and can be expanded by server owners.
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Create and give the handbook after the island is pasted to avoid
            // inventory conflicts during world creation.
            org.bukkit.inventory.ItemStack book = new org.bukkit.inventory.ItemStack(org.bukkit.Material.WRITTEN_BOOK);
            org.bukkit.inventory.meta.BookMeta meta = (org.bukkit.inventory.meta.BookMeta) book.getItemMeta();
            if (meta != null) {
                meta.setTitle("Farming Handbook");
                meta.setAuthor("Rooster Ranch");
                meta.addPage("Welcome to your farm!\n\nUse /farm trust to allow friends to help.\nWeeds spawn each day – keep them under control to maintain crop and animal health.");
                meta.addPage("Seasons & Days:\nThe day counter and season are shown on your scoreboard. Each season lasts 20 days and affects crop growth.");
                meta.addPage("Marketplace:\nVisit the market island to buy seeds, animals and tools with your RC balance. Sell extra produce to earn more coins!");
                meta.addPage("Professions:\nYour profession gives you a starter kit. Try different roles to diversify your farm.");
                book.setItemMeta(meta);
            }
            player.getInventory().addItem(book);
            // Teleport player to a safe starting location inside the barn. The small barn
            // schematic positions the barn at approximately (13,14) blocks east/z south of
            // the island origin. We add 13.5 on the X and 14.5 on the Z axes and 1 Y
            // block above the paste location so the player spawns inside on the floor.
            player.teleport(center.clone().add(14.5, -9.0, -14.5));
            player.sendMessage("§aYour farm has been created at " + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ() + ".");
        });
        return farm;
    }

    /**
     * Get a player's farm if it exists.
     *
     * @param uuid the player's UUID
     * @return the farm or null
     */
    public Farm getFarm(@NotNull UUID uuid) {
        return farms.get(uuid);
    }

    /**
     * Finds a farm at a given location. Used by protection logic to determine
     * whether a block interaction is allowed.
     *
     * @param loc the location to check
     * @return the farm if the location is within a farm's radius, otherwise null
     */
    public Farm findFarmAtLocation(@NotNull Location loc) {
        if (farmWorld == null || !loc.getWorld().equals(farmWorld)) return null;
        for (Farm farm : farms.values()) {
            Location center = farm.getCenter();
            double radius = 80.0; // Farm protection radius
            if (loc.getX() >= center.getX() - radius && loc.getX() <= center.getX() + radius
                    && loc.getZ() >= center.getZ() - radius && loc.getZ() <= center.getZ() + radius) {
                return farm;
            }
        }
        return null;
    }

    /**
     * Paste a schematic file at the specified location using WorldEdit's API.
     * This method is synchronous and should only be run from the server thread.
     * Errors are logged to the console.
     *
     * @param schematicFile the schematic file
     * @param location      the location to paste at
     */
    public void pasteSchematic(@NotNull File schematicFile, @NotNull Location location) {
        if (!schematicFile.exists()) {
            plugin.getLogger().severe("Schematic not found: " + schematicFile.getName());
            return;
        }
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(schematicFile);
            if (format == null) {
                plugin.getLogger().severe("Unknown schematic format for file: " + schematicFile.getName());
                return;
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(schematicFile))) {
                Clipboard clipboard = reader.read();
                com.sk89q.worldedit.world.World weWorld = BukkitAdapter.adapt(location.getWorld());
                try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(weWorld, -1)) {
                    Operation operation = new ClipboardHolder(clipboard)
                            .createPaste(editSession)
                            .to(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                            .ignoreAirBlocks(false)
                            .build();
                    Operations.complete(operation);
                    editSession.flushSession();
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to paste schematic " + schematicFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Starts repeating tasks for updating scoreboards and spawning weeds. Scoreboards
     * are updated every second (20 ticks) while weed tasks run every in-game
     * day (24000 ticks). These tasks are cancelled automatically on plugin
     * disable.
     */
    public void startTasks() {
        // Scoreboard task: update both farm and market scoreboards every second
        this.scoreboardTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            updateFarmScoreboards();
            updateMarketScoreboards();
        }, 20L, 20L);
        // Weed task: degrade farms each in-game day
        this.weedTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Farm farm : farms.values()) {
                farm.tickDay();
            }
        }, 24000L, 24000L);
    }

    /**
     * Stops repeating tasks. Should be called on plugin disable to avoid
     * scheduling orphaned runnables.
     */
    public void stopTasks() {
        if (scoreboardTask != null) scoreboardTask.cancel();
        if (weedTask != null) weedTask.cancel();
    }

    /**
     * Update scoreboard for all players in the farm world. Each player gets a
     * personalised scoreboard showing farm stats and RC balance. Scoreboard
     * entries are recreated each update to reflect changes without flickering.
     */
    private void updateFarmScoreboards() {
        if (farmWorld == null) return;
        // Update scoreboards for players currently in the farm world
        for (Player player : farmWorld.getPlayers()) {
            Farm farm = farms.get(player.getUniqueId());
            // Only show scoreboard if player owns a farm; otherwise revert
            if (farm == null) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                continue;
            }
            Scoreboard scoreboard = playerFarmBoards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
            Objective obj = scoreboard.getObjective("farm");
            if (obj == null) {
                obj = scoreboard.registerNewObjective("farm", org.bukkit.scoreboard.Criteria.DUMMY, Component.text("Rooster Farm", NamedTextColor.GOLD));
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            // Clear previous entries
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }
            // Compute day and season
            long time = farmWorld.getFullTime();
            int day = (int) (time / 24000L) + 1;
            String[] seasons = {"Spring", "Summer", "Autumn", "Winter"};
            int seasonIndex = ((day - 1) / 20) % seasons.length;
            String season = seasons[seasonIndex];
            int score = 7;
            obj.getScore("§fDay: " + day).setScore(score--);
            obj.getScore("§fSeason: " + season).setScore(score--);
            obj.getScore("§fUpkeep: " + String.format("%.0f%%", farm.getUpkeep())).setScore(score--);
            obj.getScore("§fCrops: " + String.format("%.0f%%", farm.getCropHealth())).setScore(score--);
            obj.getScore("§fAnimals: " + String.format("%.0f%%", farm.getAnimalHealth())).setScore(score--);
            obj.getScore("§fWeeds: " + farm.getWeedCount()).setScore(score--);
            double balance = economyManager.getBalance(player);
            obj.getScore("§fBalance: " + String.format("%.1f RC", balance)).setScore(score--);
            player.setScoreboard(scoreboard);
        }
        // Remove scoreboards for players who have left the farm world
        for (UUID uuid : new HashSet<>(playerFarmBoards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.getWorld().equals(farmWorld)) {
                playerFarmBoards.remove(uuid);
                if (player != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            }
        }
    }

    /**
     * Update scoreboard for all players in the market world. Scoreboard shows
     * player name, coordinates and RC balance. When leaving the market world,
     * players receive the main scoreboard back.
     */
    private void updateMarketScoreboards() {
        if (marketWorld == null) return;
        for (Player player : marketWorld.getPlayers()) {
            Scoreboard scoreboard = playerMarketBoards.computeIfAbsent(player.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
            Objective obj = scoreboard.getObjective("market");
            if (obj == null) {
                obj = scoreboard.registerNewObjective("market", org.bukkit.scoreboard.Criteria.DUMMY, Component.text("Rooster Market", NamedTextColor.GOLD));
                obj.setDisplaySlot(DisplaySlot.SIDEBAR);
            }
            // Clear previous entries
            for (String entry : scoreboard.getEntries()) {
                scoreboard.resetScores(entry);
            }
            Location loc = player.getLocation();
            int score = 4;
            obj.getScore("§fPlayer: " + player.getName()).setScore(score--);
            obj.getScore(String.format("§fPos: %d, %d, %d", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())).setScore(score--);
            double balance = economyManager.getBalance(player);
            obj.getScore("§fBalance: " + String.format("%.1f RC", balance)).setScore(score--);
            player.setScoreboard(scoreboard);
        }
        // Players not in market world should revert scoreboard
        for (UUID uuid : new HashSet<>(playerMarketBoards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.getWorld().equals(marketWorld)) {
                playerMarketBoards.remove(uuid);
                if (player != null) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            }
        }
    }

    /**
     * Event listener: prevent players from breaking blocks on farms they do not
     * own or have been trusted to interact with. This ensures that farms are
     * protected by default from griefing.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        Farm farm = findFarmAtLocation(loc);
        if (farm != null) {
            Player player = event.getPlayer();
            if (!farm.isTrusted(player.getUniqueId())) {
                player.sendMessage("§cYou are not trusted on this farm!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Location loc = event.getBlock().getLocation();
        Farm farm = findFarmAtLocation(loc);
        if (farm != null) {
            Player player = event.getPlayer();
            if (!farm.isTrusted(player.getUniqueId())) {
                player.sendMessage("§cYou are not trusted on this farm!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();
        Farm farm = findFarmAtLocation(loc);
        if (farm != null) {
            Player player = event.getPlayer();
            if (!farm.isTrusted(player.getUniqueId())) {
                player.sendMessage("§cYou are not trusted on this farm!");
                event.setCancelled(true);
            }
        }
    }

    /**
     * Event listener: when players join, if they have a farm assign scoreboard
     * tasks. Also if they don't have a profession, optionally prompt them to
     * choose one through a command. Profession prompting is handled outside
     * FarmManager to keep responsibilities separate.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Force scoreboard update on next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.getWorld().equals(farmWorld)) {
                updateFarmScoreboards();
            } else if (player.getWorld().equals(marketWorld)) {
                updateMarketScoreboards();
            } else {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
        });
    }
}