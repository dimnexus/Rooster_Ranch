package com.rooster.ranch.command;

import com.rooster.ranch.farm.Farm;
import com.rooster.ranch.manager.FarmManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Handles the /farm command which lets players manage their island. Supported
 * subcommands:
 *   create  - creates a new farm for the player if one does not exist.
 *   home    - teleports the player to their farm.
 *   info    - displays farm statistics in chat.
 *   trust   - trust a player to build/interact on your farm.
 *   untrust - revoke trust from a player.
 *   visit   - teleport to another player's farm.
 */
public class FarmCommand implements CommandExecutor {

    private final FarmManager farmManager;
    private final com.rooster.ranch.manager.ProfessionManager professionManager;

    public FarmCommand(FarmManager farmManager, com.rooster.ranch.manager.ProfessionManager professionManager) {
        this.farmManager = farmManager;
        this.professionManager = professionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§eUsage: /farm <create|home|info|trust|untrust|visit|market|help>");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "create" -> {
                Farm farm = farmManager.getFarm(player.getUniqueId());
                if (farm != null) {
                    player.sendMessage("§cYou already have a farm! Use /farm home to go to it.");
                    return true;
                }
                farmManager.createFarm(player);
                // After creating a farm, prompt the player to choose a profession
                professionManager.openProfessionGUI(player);
                return true;
            }
            case "home" -> {
                Farm farm = farmManager.getFarm(player.getUniqueId());
                if (farm == null) {
                    player.sendMessage("§cYou don't have a farm yet. Use /farm create.");
                    return true;
                }
                player.teleport(farm.getCenter().clone().add(14.5, -9.0, -14.5));
                player.sendMessage("§aTeleported to your farm.");
                return true;
            }
            case "info" -> {
                Farm farm = farmManager.getFarm(player.getUniqueId());
                if (farm == null) {
                    player.sendMessage("§cYou don't have a farm yet. Use /farm create.");
                    return true;
                }
                player.sendMessage("§6--- Farm Info ---");
                player.sendMessage("§eLocation: §f" + farm.getCenter().getBlockX() + ", " + farm.getCenter().getBlockY() + ", " + farm.getCenter().getBlockZ());
                player.sendMessage("§eUpkeep: §f" + String.format("%.0f%%", farm.getUpkeep()));
                player.sendMessage("§eCrop Health: §f" + String.format("%.0f%%", farm.getCropHealth()));
                player.sendMessage("§eAnimal Health: §f" + String.format("%.0f%%", farm.getAnimalHealth()));
                player.sendMessage("§eWeeds: §f" + farm.getWeedCount());
                return true;
            }
            case "trust" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /farm trust <player>");
                    return true;
                }
                Farm farm = farmManager.getFarm(player.getUniqueId());
                if (farm == null) {
                    player.sendMessage("§cYou don't have a farm yet. Use /farm create.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage("§cThat player is not online.");
                    return true;
                }
                farm.trust(target.getUniqueId());
                player.sendMessage("§aYou have trusted " + target.getName() + " on your farm.");
                return true;
            }
            case "untrust" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /farm untrust <player>");
                    return true;
                }
                Farm farm = farmManager.getFarm(player.getUniqueId());
                if (farm == null) {
                    player.sendMessage("§cYou don't have a farm yet. Use /farm create.");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cThat player is not online.");
                    return true;
                }
                farm.untrust(target.getUniqueId());
                player.sendMessage("§eYou have removed trust for " + target.getName() + ".");
                return true;
            }
            case "visit" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /farm visit <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cThat player is not online.");
                    return true;
                }
                Farm targetFarm = farmManager.getFarm(target.getUniqueId());
                if (targetFarm == null) {
                    player.sendMessage("§cThat player does not have a farm.");
                    return true;
                }
                player.teleport(targetFarm.getCenter().clone().add(14.5, -9.0, -14.5));
                player.sendMessage("§aTeleported to " + target.getName() + "'s farm.");
                return true;
            }
            case "market" -> {
                // Teleport the player to the market walkway if it exists
                org.bukkit.World market = org.bukkit.Bukkit.getWorld("rooster_market");
                if (market == null) {
                    player.sendMessage("§cThe market world is not available.");
                    return true;
                }
                // Spawn point on the walkway near the market vendor. The height is one block
                // above the path to avoid suffocation. Adjust if you change the schematic.
                player.teleport(new org.bukkit.Location(market, 16.5, 94.0, -5.5));
                player.sendMessage("§aTeleported to the market island.");
                return true;
            }
            case "help" -> {
                player.sendMessage("§6--- Rooster Ranch Commands ---");
                player.sendMessage("§e/farm create§f - Create your own farm island.");
                player.sendMessage("§e/farm home§f - Teleport to your farm.");
                player.sendMessage("§e/farm info§f - View your farm's stats.");
                player.sendMessage("§e/farm trust <player>§f - Allow someone to build on your farm.");
                player.sendMessage("§e/farm untrust <player>§f - Revoke someone's access.");
                player.sendMessage("§e/farm visit <player>§f - Visit another player's farm.");
                player.sendMessage("§e/farm market§f - Visit the market island.");
                player.sendMessage("§e/profession§f - Choose your profession.");
                return true;
            }
            default -> {
                player.sendMessage("§cUnknown subcommand. Use /farm help for a list of commands.");
                return true;
            }
        }
    }
}