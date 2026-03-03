package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.TraitFactory;
import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.EntityType;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.io.StringWriter;
import java.io.PrintWriter;

public class DanceManager {

    private final DanseAvecLaStare plugin;

    private static class RunningDance {
        BukkitTask task;
        NPC npc;
        boolean previousInvisible;
        boolean hiddenFromOwner;
    }

    private final Map<UUID, RunningDance> runningDances = new ConcurrentHashMap<>();

    private static final Map<String, DanceStyle> STYLES = new HashMap<>();
    static {
        STYLES.put("twist", new TwistStyle());
        STYLES.put("spin", new SpinStyle());
        STYLES.put("disco", new DiscoStyle());
        STYLES.put("moonwalk", new MoonwalkStyle());
        STYLES.put("wave", new WaveStyle());
    }

    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    public String getStylesLabel() {
        return STYLES.keySet().stream().sorted().collect(Collectors.joining(", "));
    }

    public List<String> getStyleNames() {
        List<String> list = new ArrayList<>(STYLES.keySet());
        list.sort(String::compareTo);
        return list;
    }

    public DanceStyle parseStyle(String value) {
        if (value == null) return null;
        return STYLES.get(value.toLowerCase(Locale.ROOT));
    }

    public void startDance(Player player, DanceStyle style) {
        startDance(player, style, true);
    }

    public void startDance(Player player, DanceStyle style, boolean hideFromOwner) {
        stopDance(player.getUniqueId());

        if (style == null) {
            player.sendMessage("§cStyle de danse invalide.");
            return;
        }

        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens") || !CitizensAPI.hasImplementation()) {
            player.sendMessage("§cCitizens est requis pour la danse avec skin complet.");
            return;
        }

        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        try {
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, player.getName());
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
            npc.spawn(origin);

            // Hide NPC from the owning player only so they don't notice the mannequin
            if (hideFromOwner) {
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

            RunningDance running = new RunningDance();
            running.npc = npc;
            running.previousInvisible = player.isInvisible();
            running.hiddenFromOwner = hideFromOwner;

            // On rend le vrai joueur invisible
            player.setInvisible(true);

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            private int tick = 0;

            @Override
            public void run() {
                // 1. Vérification de sécurité : Si le joueur quitte ou meurt, on arrête tout
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline() || online.isDead()) {
                        stopDance(uuid);
                        return;
                    }

                    // 2. Vérification du NPC : si présent, calcule la position à partir
                    //    de la position courante du joueur pour que le NPC colle au joueur.
                    if (running.npc != null && running.npc.isSpawned()) {
                        Location playerLoc = online.getLocation().clone();

                        // computeLocation now receives the player's current location
                        Location danceLocation = style.computeLocation(playerLoc, tick);

                        // Téléportation du NPC vers la position calculée (cause PLUGIN)
                        running.npc.teleport(danceLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);

                        // 3. ANIMATIONS RYTHMIQUES (Le secret de la danse)
                        if (running.npc.getEntity() instanceof Player npcPlayer) {

                            // --- Mouvement des bras (Alternance gauche/droite toutes les 10 ticks) ---
                            if (tick % 20 == 0) {
                                npcPlayer.swingMainHand(); // Bras droit
                            } else if (tick % 20 == 10) {
                                npcPlayer.swingOffHand();  // Bras gauche
                            }

                            // --- Mouvement de corps (Sneak/S'accroupir pour simuler le "Bounce") ---
                            boolean isBeat = (tick % 15) < 5;
                            npcPlayer.setSneaking(isBeat);

                            // --- Effet visuel optionnel : Particules de musique ---
                            if (tick % 5 == 0) {
                                npcPlayer.getWorld().spawnParticle(
                                    org.bukkit.Particle.NOTE,
                                    npcPlayer.getLocation().add(0, 2.2, 0), // Au dessus de la tête
                                    1, 0.5, 0.5, 0.5, 0.1
                                );
                            }
                        }
                    }

                    tick++;
                }
            }, 0L, 1L); // 1L pour une fluidité de 20 images par seconde

            running.task = task;
            runningDances.put(uuid, running);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création du NPC pour la danse", ex);
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            plugin.getLogger().severe(sw.toString());
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setInvisible(false);
            } catch (Exception ignore) {}
        }
    }

    public void stopDance(UUID uuid) {
        RunningDance running = runningDances.remove(uuid);
        if (running != null) {
            if (running.task != null) running.task.cancel();
            if (running.npc != null) {
                // If possible, make NPC visible again to the owner before destroying
                try {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline() && running.hiddenFromOwner) {
                        Entity spawned = running.npc.getEntity();
                        if (spawned != null) {
                            try {
                                p.showEntity(plugin, spawned);
                            } catch (NoSuchMethodError | Exception ignore) {
                            }
                        }
                    }
                } catch (Exception e) {
                    // ignore
                }

                if (running.npc.isSpawned()) {
                    running.npc.despawn();
                }
                running.npc.destroy();
            }

            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setInvisible(running.previousInvisible);
            }
        }
    }

    public void stopAll() {
        for (UUID uuid : runningDances.keySet()) {
            stopDance(uuid);
        }
    }

    // Styles are implemented in dedicated classes under the same package.
}