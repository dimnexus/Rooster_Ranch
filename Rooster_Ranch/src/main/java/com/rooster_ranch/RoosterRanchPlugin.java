package com.rooster_ranch;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;

public final class RoosterRanchPlugin extends JavaPlugin {

    public static final String WORLD_NAME = "rooster_farms";
    private static final int ISLAND_Y = 100;

    @Override
    public void onEnable() {
        // Create or load the void world and generate a starter floating farm.
        World world = ensureVoidWorld();
        if (world != null) {
            generateStarterIslandIfMissing(world);
        } else {
            getLogger().severe("Failed to create/load world '" + WORLD_NAME + "'.");
        }
    }

    private World ensureVoidWorld() {
        World existing = Bukkit.getWorld(WORLD_NAME);
        if (existing != null) return existing;

        getLogger().info("Creating/loading void world: " + WORLD_NAME);
        WorldCreator creator = new WorldCreator(WORLD_NAME);
        creator.environment(World.Environment.NORMAL);
        creator.type(WorldType.NORMAL);
        creator.generateStructures(false);
        creator.generator(new VoidChunkGenerator());

        World world = Bukkit.createWorld(creator);
        if (world == null) return null;

        // Basic world rules for a skyblock-style world
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, true);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);

        // Keep spawn area loaded so the island doesn't unload during testing
        world.setKeepSpawnInMemory(true);

        return world;
    }

    private void generateStarterIslandIfMissing(World world) {
        // Define island center near spawn.
        Location center = new Location(world, 0.5, ISLAND_Y, 0.5);
        world.setSpawnLocation(center);

        // Check a single sentinel block: if it's already non-air, assume island exists.
        Block sentinel = world.getBlockAt(0, ISLAND_Y, 0);
        if (sentinel.getType() != Material.AIR) {
            getLogger().info("Starter island already exists (sentinel block is " + sentinel.getType() + ").");
            return;
        }

        getLogger().info("Generating starter floating farm island at " + center.getBlockX() + "," + ISLAND_Y + "," + center.getBlockZ());

        // Island footprint: 11x11 dirt base with a small farmland patch and water.
        int half = 5;
        int baseY = ISLAND_Y - 1;

        // Dirt base
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                world.getBlockAt(x, baseY, z).setType(Material.DIRT, false);
            }
        }

        // Add a little grass border on top
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                boolean edge = (x == -half || x == half || z == -half || z == half);
                if (edge) {
                    world.getBlockAt(x, ISLAND_Y, z).setType(Material.GRASS_BLOCK, false);
                } else {
                    world.getBlockAt(x, ISLAND_Y, z).setType(Material.DIRT, false);
                }
            }
        }

        // Farmland 5x5 in the middle with water in the center
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                world.getBlockAt(x, ISLAND_Y, z).setType(Material.FARMLAND, false);
            }
        }
        world.getBlockAt(0, ISLAND_Y, 0).setType(Material.WATER, false);

        // Chest + starter items
        Location chestLoc = new Location(world, 3, ISLAND_Y + 1, 0);
        world.getBlockAt(chestLoc).setType(Material.CHEST, false);
        if (world.getBlockAt(chestLoc).getState() instanceof Chest chest) {
            chest.getBlockInventory().addItem(new ItemStack(Material.WHEAT_SEEDS, 16));
            chest.getBlockInventory().addItem(new ItemStack(Material.CARROT, 8));
            chest.getBlockInventory().addItem(new ItemStack(Material.POTATO, 8));
            chest.getBlockInventory().addItem(new ItemStack(Material.BONE_MEAL, 8));
            ItemStack info = new ItemStack(Material.PAPER, 1);
            ItemMeta meta = info.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.GOLD + "Welcome to Rooster_Ranch");
                meta.setLore(List.of(
                        ChatColor.YELLOW + "World: " + ChatColor.WHITE + WORLD_NAME,
                        ChatColor.YELLOW + "This is a test island for the farming skyblock.",
                        ChatColor.YELLOW + "Next: contracts, shipping bin, and RC currency."
                ));
                info.setItemMeta(meta);
            }
            chest.getBlockInventory().addItem(info);
            chest.update();
        }

        // A small tree (sapling) + a crafting table
        world.getBlockAt(-3, ISLAND_Y + 1, 0).setType(Material.OAK_SAPLING, false);
        world.getBlockAt(0, ISLAND_Y + 1, 3).setType(Material.CRAFTING_TABLE, false);

        // Safety: a barrier ring below to reduce falling during testing? (kept off by default)
        getLogger().info("Starter island generated.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ranch")) return false;

        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "Rooster_Ranch " + ChatColor.WHITE + "v" + getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "World: " + ChatColor.WHITE + WORLD_NAME);
            sender.sendMessage(ChatColor.YELLOW + "This test build only generates the void world + starter island.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("regen")) {
            if (!sender.hasPermission("rooster_ranch.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            World world = Bukkit.getWorld(WORLD_NAME);
            if (world == null) {
                sender.sendMessage(ChatColor.RED + "World not loaded.");
                return true;
            }
            // Clear a small area and regenerate island.
            int radius = 10;
            for (int x = -radius; x <= radius; x++) {
                for (int y = ISLAND_Y - 5; y <= ISLAND_Y + 10; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
            generateStarterIslandIfMissing(world);
            sender.sendMessage(ChatColor.GREEN + "Regenerated starter island in " + WORLD_NAME + ".");
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Unknown subcommand. Try /ranch or /ranch regen");
        return true;
    }

    /**
     * A simple void generator: generates empty chunks (all air).
     */
    public static final class VoidChunkGenerator extends ChunkGenerator {

        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
            ChunkData chunkData = createChunkData(world);
            // We intentionally leave it empty (air).
            return chunkData;
        }

        @Override
        public boolean shouldGenerateNoise() { return false; }

        @Override
        public boolean shouldGenerateSurface() { return false; }

        @Override
        public boolean shouldGenerateBedrock() { return false; }

        @Override
        public boolean shouldGenerateCaves() { return false; }

        @Override
        public boolean shouldGenerateDecorations() { return false; }

        @Override
        public boolean shouldGenerateMobs() { return false; }

        @Override
        public boolean shouldGenerateStructures() { return false; }
    }
}
