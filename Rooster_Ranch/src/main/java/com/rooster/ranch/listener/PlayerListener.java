package com.rooster.ranch.listener;

import com.rooster.ranch.manager.ProfessionManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Listens for player join events to prompt profession selection if none has been
 * chosen yet. This listener does not handle scoreboard updates; these are
 * managed by the FarmManager.
 */
public class PlayerListener implements Listener {
    private final JavaPlugin plugin;
    private final ProfessionManager professionManager;

    public PlayerListener(JavaPlugin plugin, ProfessionManager professionManager) {
        this.plugin = plugin;
        this.professionManager = professionManager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Delay sending a welcome message slightly to allow other login processes to complete
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (professionManager.getProfession(event.getPlayer().getUniqueId()) == null) {
                event.getPlayer().sendMessage("§6Welcome to Rooster Ranch!\nOnce your farm is created, use §e/profession§6 to choose a role.");
            }
        }, 40L);
    }
}