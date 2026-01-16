package com.rooster.ranch.manager;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Enum representing the available professions in Rooster Ranch. Each profession
 * has a display name and a basic starter kit to help players begin their
 * farming adventure. Additional professions can be added by extending this
 * enumeration and updating the GUI in {@link ProfessionManager} accordingly.
 */
public enum Profession {
    FARMER("Farmer", new ItemStack[] {
            new ItemStack(Material.WHEAT_SEEDS, 32),
            new ItemStack(Material.IRON_HOE),
            new ItemStack(Material.BUCKET)
    }),
    RANCHER("Rancher", new ItemStack[] {
            new ItemStack(Material.WHEAT, 16),
            new ItemStack(Material.LEAD, 2),
            new ItemStack(Material.IRON_SWORD)
    }),
    FISHER("Fisher", new ItemStack[] {
            new ItemStack(Material.FISHING_ROD),
            new ItemStack(Material.SALMON, 8),
            new ItemStack(Material.COOKED_COD, 8)
    }),
    MERCHANT("Merchant", new ItemStack[] {
            new ItemStack(Material.EMERALD, 8),
            new ItemStack(Material.GOLD_NUGGET, 32),
            new ItemStack(Material.WRITABLE_BOOK)
    });

    private final String displayName;
    private final ItemStack[] starterKit;

    Profession(String displayName, ItemStack[] starterKit) {
        this.displayName = displayName;
        this.starterKit = starterKit;
    }

    public String getDisplayName() {
        return displayName;
    }

    public ItemStack[] getStarterKit() {
        return starterKit;
    }
}