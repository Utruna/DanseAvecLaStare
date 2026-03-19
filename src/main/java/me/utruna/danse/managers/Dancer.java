package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface Dancer {
    void spawn(Location location, Player player);
    void tick(int tick, DanceStyle style);
    void stop();
}
