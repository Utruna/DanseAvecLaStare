package me.utruna.danse.managers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import com.ticxo.modelengine.api.model.ModeledType; // Ajouté
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ModelEngineDancer implements Dancer {

    private Player owner;
    private Dummy<?> dummy;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;
    private final DanseAvecLaStare plugin;
    private final String modelId;

    private static record SkinData(String value, String signature, long expiry) {}
    private static final Map<UUID, SkinData> skinCache = new ConcurrentHashMap<>();
    private static final long SKIN_CACHE_TTL_MS = 1000L * 60 * 60 * 6;
    private static final Gson GSON = new Gson();

    public ModelEngineDancer(DanseAvecLaStare plugin, String modelId) {
        this.plugin = plugin;
        this.modelId = (modelId == null || modelId.isBlank()) ? "danseur" : modelId.trim();
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;
        this.dummy = new Dummy<>(player); // Utilisation directe
        this.dummy.setLocation(location);
        this.dummy.setVisible(true);

        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        this.modeledEntity.registerSelf();

        this.activeModel = ModelEngineAPI.createActiveModel(modelId);
        
        // CORRECTION MAJEURE : On force le type PLAYER ici
        this.activeModel.setModeledType(ModeledType.PLAYER);
        this.activeModel.setAutoRendererInitialization(true);

        // Application du skin
        applyPlayerSkin(player);

        if (!modeledEntity.addModel(activeModel, true).isPresent()) {
            modeledEntity.addModel(activeModel, false);
        }
        
        activeModel.generateModel();
        activeModel.initializeRenderer();
    }

    private void applyPlayerSkin(Player player) {
        UUID uuid = player.getUniqueId();
        
        // 1) Essayer de récupérer du cache
        SkinData cached = skinCache.get(uuid);
        if (cached != null && cached.expiry > Instant.now().toEpochMilli()) {
            applySkinToModel(cached.value, cached.signature);
            return;
        }

        // 2) Fetch asynchrone
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            SkinData fetched = fetchSkinFromSessionServerGson(uuid);
            if (fetched != null) {
                skinCache.put(uuid, fetched);
                Bukkit.getScheduler().runTask(plugin, () -> applySkinToModel(fetched.value, fetched.signature));
            }
        });
    }

    private void applySkinToModel(String value, String signature) {
        if (this.activeModel != null && value != null) {
            // Méthode API Native
            this.activeModel.setSkin(value, signature);
            plugin.getLogger().info("Skin injecté avec succès dans le modèle.");
        }
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (dummy != null && activeModel != null) {
            Location danceLocation = style.computeLocation(owner.getLocation(), tick);
            dummy.setLocation(danceLocation);
            
            // Rotation native sans réflexion
            dummy.setBodyYaw(danceLocation.getYaw());
            dummy.setHeadYaw(danceLocation.getYaw());

            String animToPlay = "dance";
            if (!activeModel.getBlueprint().getAnimations().containsKey("dance")) {
                Optional<String> firstAnim = activeModel.getBlueprint().getAnimations().keySet().stream().findFirst();
                firstAnim.ifPresent(s -> animToPlay = s);
            }

            if (!activeModel.getAnimationHandler().isPlayingAnimation(animToPlay)) {
                activeModel.getAnimationHandler().playAnimation(animToPlay, 0.1d, 0.1d, 1.0d, false);
            }
        }
    }

    @Override
    public void stop() {
        if (modeledEntity != null) modeledEntity.destroy();
    }

    private SkinData fetchSkinFromSessionServerGson(UUID uuid) {
        // ... (ton code fetch reste inchangé, il est fonctionnel) ...
        try {
            String id = uuid.toString().replace("-", "");
            String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + id + "?unsigned=false";
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestProperty("User-Agent", "DanseAvecLaStare/1.0");
            conn.setConnectTimeout(3000);
            try (InputStreamReader r = new InputStreamReader(conn.getInputStream())) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                JsonArray props = root.getAsJsonArray("properties");
                for (JsonElement el : props) {
                    JsonObject obj = el.getAsJsonObject();
                    if (obj.get("name").getAsString().equals("textures")) {
                        return new SkinData(obj.get("value").getAsString(), obj.get("signature").getAsString(), Instant.now().toEpochMilli() + SKIN_CACHE_TTL_MS);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}