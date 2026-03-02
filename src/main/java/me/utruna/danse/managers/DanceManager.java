package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DanceManager {

    private final DanseAvecLaStare plugin;
    private final Map<UUID, BukkitTask> runningDances = new ConcurrentHashMap<>();

    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    public String getStylesLabel() {
        return Arrays.stream(DanceStyle.values())
                .map(style -> style.name().toLowerCase())
                .collect(Collectors.joining(", "));
    }

    public DanceStyle parseStyle(String value) {
        for (DanceStyle style : DanceStyle.values()) {
            if (style.name().equalsIgnoreCase(value)) {
                return style;
            }
        }
        return null;
    }

    public void startDance(Player player, DanceStyle style) {
        stopDance(player, false);

        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                Player onlinePlayer = Bukkit.getPlayer(uuid);
                if (onlinePlayer == null || !onlinePlayer.isOnline() || onlinePlayer.isDead()) {
                    stopDance(uuid);
                    return;
                }

                Location danceLocation = style.computeLocation(origin, tick);
                onlinePlayer.teleport(danceLocation);

                if (tick % 6 == 0) {
                    onlinePlayer.swingMainHand();
                }

                tick += 2;
            }
        }, 0L, 2L);

        runningDances.put(uuid, task);
    }

    public void stopDance(Player player, boolean notifyPlayer) {
        stopDance(player.getUniqueId());
        if (notifyPlayer) {
            player.sendMessage("§cTu arrêtes de danser.");
        }
    }

    public void stopDance(UUID uuid) {
        BukkitTask task = runningDances.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        runningDances.values().forEach(BukkitTask::cancel);
        runningDances.clear();
    }

    public enum DanceStyle {
        TWIST {
            @Override
            public Location computeLocation(Location origin, int tick) {
                double phase = tick * 0.20;
                float yaw = normalizeYaw(origin.getYaw() + (float) (Math.sin(phase) * 45.0));
                double side = Math.sin(phase) * 0.30;

                Location location = origin.clone();
                location.setX(origin.getX() + side);
                location.setYaw(yaw);
                location.setPitch(0.0f);
                return location;
            }
        },
        SPIN {
            @Override
            public Location computeLocation(Location origin, int tick) {
                Location location = origin.clone();
                location.setYaw(normalizeYaw(origin.getYaw() + (tick * 22.0f)));
                location.setPitch(0.0f);
                return location;
            }
        },
        BOUNCE {
            @Override
            public Location computeLocation(Location origin, int tick) {
                double phase = tick * 0.35;
                double yOffset = Math.max(0.0, Math.sin(phase)) * 0.35;
                float yaw = normalizeYaw(origin.getYaw() + (float) (Math.sin(phase) * 20.0));

                Location location = origin.clone();
                location.setY(origin.getY() + yOffset);
                location.setYaw(yaw);
                location.setPitch(0.0f);
                return location;
            }
        };

        public abstract Location computeLocation(Location origin, int tick);

        private static float normalizeYaw(float yaw) {
            while (yaw >= 180.0f) {
                yaw -= 360.0f;
            }
            while (yaw < -180.0f) {
                yaw += 360.0f;
            }
            return yaw;
        }
    }
}