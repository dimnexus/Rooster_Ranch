package com.rooster.ranch.farm;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a player's farm island. Each farm tracks its owner, central
 * location, health statistics and trusted visitors. Farms are persisted by
 * {@link com.rooster.ranch.manager.FarmManager} and updated every day to
 * simulate upkeep and crop/animal health.
 */
public class Farm {
    private final UUID owner;
    private final Location center;
    private int weedCount;
    private double upkeep;
    private double cropHealth;
    private double animalHealth;
    private final Set<UUID> trusted;

    public Farm(@NotNull UUID owner, @NotNull Location center) {
        this.owner = owner;
        this.center = center;
        this.weedCount = 0;
        this.upkeep = 100.0;
        this.cropHealth = 100.0;
        this.animalHealth = 100.0;
        this.trusted = new HashSet<>();
    }

    public UUID getOwner() {
        return owner;
    }

    public Location getCenter() {
        return center;
    }

    public int getWeedCount() {
        return weedCount;
    }

    public void setWeedCount(int weedCount) {
        this.weedCount = Math.max(0, weedCount);
    }

    public void addWeeds(int amount) {
        weedCount += amount;
    }

    public void clearWeeds() {
        weedCount = 0;
    }

    public double getUpkeep() {
        return upkeep;
    }

    public void setUpkeep(double upkeep) {
        this.upkeep = Math.max(0.0, Math.min(100.0, upkeep));
    }

    public double getCropHealth() {
        return cropHealth;
    }

    public void setCropHealth(double cropHealth) {
        this.cropHealth = Math.max(0.0, Math.min(100.0, cropHealth));
    }

    public double getAnimalHealth() {
        return animalHealth;
    }

    public void setAnimalHealth(double animalHealth) {
        this.animalHealth = Math.max(0.0, Math.min(100.0, animalHealth));
    }

    /**
     * Degrade farm statistics to simulate wear and tear. Called once per in-game
     * day by the {@link com.rooster.ranch.manager.FarmManager}. Weeds decrease
     * upkeep and crop/animal health over time.
     */
    public void tickDay() {
        // Increase weeds randomly between 1 and 3
        this.weedCount += 1 + (int) (Math.random() * 3);
        // Decrease upkeep based on weeds; more weeds means higher degradation
        this.upkeep = Math.max(0.0, upkeep - 1.0 - weedCount * 0.05);
        // Crop and animal health degrade slightly each day
        this.cropHealth = Math.max(0.0, cropHealth - 0.5 - weedCount * 0.02);
        this.animalHealth = Math.max(0.0, animalHealth - 0.5 - weedCount * 0.02);
    }

    public Set<UUID> getTrusted() {
        return trusted;
    }

    /**
     * Check whether a particular player is trusted to interact with this farm.
     *
     * @param uuid player UUID
     * @return true if the player is the owner or in the trust list
     */
    public boolean isTrusted(@NotNull UUID uuid) {
        return owner.equals(uuid) || trusted.contains(uuid);
    }

    public void trust(@NotNull UUID uuid) {
        if (!owner.equals(uuid)) {
            trusted.add(uuid);
        }
    }

    public void untrust(@NotNull UUID uuid) {
        trusted.remove(uuid);
    }
}