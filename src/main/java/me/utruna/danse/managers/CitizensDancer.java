package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class CitizensDancer implements Dancer {

    private final DanseAvecLaStare plugin;
    private NPC npc;
    private Player owner;
    private boolean hiddenFromOwner;

    public CitizensDancer(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    public void setHiddenFromOwner(boolean hiddenFromOwner) {
        this.hiddenFromOwner = hiddenFromOwner;
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;
        
        try {
            npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, player.getName());

            // Try to apply skin trait reflectively to support multiple Citizens versions
            try {
                Class<?> skinTraitClass = null;
                String[] candidates = new String[]{
                        "net.citizensnpcs.api.trait.trait.SkinTrait",
                        "net.citizensnpcs.trait.SkinTrait",
                        "net.citizensnpcs.api.trait.SkinTrait"
                };
                for (String fqcn : candidates) {
                    try {
                        skinTraitClass = Class.forName(fqcn);
                        break;
                    } catch (ClassNotFoundException ignored) {
                    }
                }

                if (skinTraitClass != null) {
                    Method getOrAdd = NPC.class.getMethod("getOrAddTrait", Class.class);
                    Object skinTrait = getOrAdd.invoke(npc, skinTraitClass);
                    try {
                        Method setSkin = skinTraitClass.getMethod("setSkinName", String.class, boolean.class);
                        setSkin.invoke(skinTrait, player.getName(), true);
                    } catch (NoSuchMethodException e) {
                        try {
                            Method setSkin = skinTraitClass.getMethod("setSkinName", String.class);
                            setSkin.invoke(skinTrait, player.getName());
                        } catch (NoSuchMethodException e2) {
                            plugin.getLogger().warning("SkinTrait found but no compatible setSkinName method.");
                        }
                    }
                } else {
                    plugin.getLogger().warning("Citizens SkinTrait class not found; NPC skin won't be applied.");
                }
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "Failed to apply SkinTrait via reflection", ex);
            }

            npc.setProtected(true);
            npc.spawn(location);

            // Hide NPC from the owning player only so they don't notice the mannequin
            if (hiddenFromOwner) {
                try {
                    Entity spawned = npc.getEntity();
                    if (spawned != null) {
                        player.hideEntity(plugin, spawned);
                    }
                } catch (NoSuchMethodError | Exception e) {
                    // ignore if server implementation doesn't support per-player hide
                    plugin.getLogger().fine("Per-player hideEntity not available: " + e.getMessage());
                }
            }

            if (npc.getEntity() instanceof Player npcPlayer) {
                npcPlayer.getInventory().setArmorContents(player.getInventory().getArmorContents());
                npcPlayer.getInventory().setItemInMainHand(player.getInventory().getItemInMainHand());
                npcPlayer.getInventory().setItemInOffHand(player.getInventory().getItemInOffHand());
            }

        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création du NPC pour la danse", ex);
            throw new RuntimeException("Erreur creation CitizensDancer", ex);
        }
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (npc != null && npc.isSpawned()) {
            Location playerLoc = owner.getLocation().clone();

            Location danceLocation = style.computeLocation(playerLoc, tick);

            npc.teleport(danceLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);

            if (npc.getEntity() instanceof Player npcPlayer) {
                if (tick % 20 == 0) {
                    npcPlayer.swingMainHand();
                } else if (tick % 20 == 10) {
                    npcPlayer.swingOffHand();
                }

                boolean isBeat = (tick % 15) < 5;
                npcPlayer.setSneaking(isBeat);

                if (tick % 5 == 0) {
                    npcPlayer.getWorld().spawnParticle(
                        Particle.NOTE,
                        npcPlayer.getLocation().add(0, 2.2, 0),
                        1, 0.5, 0.5, 0.5, 0.1
                    );
                }
            }
        }
    }

    @Override
    public void stop() {
        if (npc != null) {
            try {
                if (owner != null && owner.isOnline() && hiddenFromOwner) {
                    Entity spawned = npc.getEntity();
                    if (spawned != null) {
                        try {
                            owner.showEntity(plugin, spawned);
                        } catch (NoSuchMethodError | Exception ignore) {
                        }
                    }
                }
            } catch (Exception e) {
                // ignore
            }

            if (npc.isSpawned()) {
                npc.despawn();
            }
            npc.destroy();
        }
    }
}
