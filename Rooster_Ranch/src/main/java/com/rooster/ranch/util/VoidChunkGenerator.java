package com.rooster.ranch.util;

import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;
import java.util.Random;

/**
 * A simple {@link ChunkGenerator} that generates empty chunks.
 *
 * This generator is used to create void worlds for farms and the market. Every
 * chunk produced by this generator contains only air blocks, resulting in a
 * completely empty world. A void world ensures that player islands and the
 * market schematic are the only structures present.
 */
public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public @NotNull ChunkData generateChunkData(@NotNull World world, @NotNull Random random,
                                                int chunkX, int chunkZ, @NotNull BiomeGrid biome) {
        // Create an empty chunk. Paper/Bukkit will automatically fill it with air.
        return createChunkData(world);
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }
}