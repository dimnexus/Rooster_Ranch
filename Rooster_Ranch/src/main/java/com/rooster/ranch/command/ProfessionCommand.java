package com.rooster.ranch.command;

import com.rooster.ranch.manager.ProfessionManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles the /profession command which allows players to pick their farming
 * profession. The command opens a GUI through the {@link ProfessionManager}.
 */
public class ProfessionCommand implements CommandExecutor {
    private final ProfessionManager professionManager;

    public ProfessionCommand(ProfessionManager professionManager) {
        this.professionManager = professionManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can choose a profession.");
            return true;
        }
        professionManager.openProfessionGUI(player);
        return true;
    }
}