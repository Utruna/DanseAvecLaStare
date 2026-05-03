package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public class ModelEngineDancer implements Dancer {

    private Player owner;
    private Dummy<?> dummy;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;

    // Must match the blueprint id loaded by ModelEngine
    private final String modelId;

    public ModelEngineDancer() {
        this("danseur");
    }

    public ModelEngineDancer(String modelId) {
        if (modelId == null || modelId.isBlank()) {
            this.modelId = "danseur";
        } else {
            this.modelId = modelId.trim();
        }
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;

        // 1) Build a Dummy using constructors available in ModelEngine 4.0.9.
        this.dummy = createDummyCompat(player);
        if (this.dummy == null) {
            throw new IllegalStateException("Impossible de creer un Dummy ModelEngine");
        }
        this.dummy.setLocation(location);
        this.dummy.setVisible(true);
        this.dummy.setRenderRadius(64);

        // 2) Create modeled entity and load model
        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        if (this.modeledEntity == null) {
            throw new IllegalStateException("ModelEngine createModeledEntity a retourne null");
        }
        this.modeledEntity.registerSelf();
        this.activeModel = ModelEngineAPI.createActiveModel(modelId);
        if (this.activeModel == null) {
            throw new IllegalStateException("ModelEngine n'a pas trouve le blueprint: " + modelId);
        }
        this.activeModel.setAutoRendererInitialization(true);

        // 3) Player skin mapping: try known APIs in descending order.
        applyPlayerSkinCompat(player);

        // 4) Attach model and ensure renderer is initialized.
        if (!addModelCompat()) {
            boolean blueprintFound = ModelEngineAPI.getBlueprint(modelId) != null;
            throw new IllegalStateException("Echec lors de l'attachement du modele actif sur la ModeledEntity (modelId=" + modelId + ", blueprintFound=" + blueprintFound + ")");
        }
        activeModel.generateModel();
        activeModel.initializeRenderer();
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (modeledEntity != null && activeModel != null) {
            Location playerLoc = owner.getLocation().clone();
            Location danceLocation = style.computeLocation(playerLoc, tick);
            
            dummy.setLocation(danceLocation);
            setRotationCompat(danceLocation.getYaw());

            // Avoid restarting animation every tick
            if (!activeModel.getAnimationHandler().isPlayingAnimation("dance")) {
                activeModel.getAnimationHandler().playAnimation("dance", 0.1d, 0.1d, 1.0d, false);
            }
        }
    }

    @Override
    public void stop() {
        if (modeledEntity != null) {
            removeModeledEntityCompat();
            modeledEntity = null;
            activeModel = null;
            dummy = null;
        }
    }

    private Dummy<?> createDummyCompat(Player player) {
        // Preferred path for ME 4.0.9: constructors exist on Dummy.
        try {
            return new Dummy<>(player);
        } catch (Throwable ignored) {
        }

        try {
            return new Dummy<>();
        } catch (Throwable ignored) {
        }

        // Fallback for potential API variants.
        try {
            Method apiGetter = ModelEngineAPI.class.getMethod("getAPI");
            Object api = apiGetter.invoke(null);

            for (String serviceMethod : new String[]{"getBukkitEntityService", "getBoxEntityService", "getEntityService"}) {
                try {
                    Method m = api.getClass().getMethod(serviceMethod);
                    Object service = m.invoke(api);
                    if (service == null) continue;
                    Method createDummy = service.getClass().getMethod("createDummy");
                    Object result = createDummy.invoke(service);
                    if (result instanceof Dummy<?> d) {
                        return d;
                    }
                } catch (NoSuchMethodException ignored) {
                    // Try next service candidate
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void applyPlayerSkinCompat(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProfile profile = player.getPlayerProfile();

        // 1) activeModel.setPlayerProfile(PlayerProfile)
        if (invokeIfPresent(activeModel, "setPlayerProfile", new Class<?>[]{PlayerProfile.class}, profile)) {
            return;
        }

        // 2) activeModel.setSkin(UUID)
        if (invokeIfPresent(activeModel, "setSkin", new Class<?>[]{UUID.class}, uuid)) {
            return;
        }

        // 3) dummy.setPlayerProfile(PlayerProfile)
        invokeIfPresent(dummy, "setPlayerProfile", new Class<?>[]{PlayerProfile.class}, profile);
    }

    private boolean addModelCompat() {
        Optional<ActiveModel> added = modeledEntity.addModel(activeModel, true);
        if (added.isPresent()) return true;

        // Fallback: some blueprints/flags can fail with true
        added = modeledEntity.addModel(activeModel, false);
        return added.isPresent();
    }

    private void setRotationCompat(float yaw) {
        invokeIfPresent(dummy, "setYBodyRot", new Class<?>[]{float.class}, yaw);
        invokeIfPresent(dummy, "setYHeadRot", new Class<?>[]{float.class}, yaw);
    }

    private void removeModeledEntityCompat() {
        try {
            if (activeModel != null && !activeModel.isDestroyed()) {
                activeModel.destroy();
            }
            if (dummy != null) {
                UUID uuid = dummy.getUUID();
                if (uuid != null) {
                    ModelEngineAPI.removeModeledEntity(uuid);
                }
            }
            if (modeledEntity != null && !modeledEntity.isDestroyed()) {
                modeledEntity.destroy();
            }
        } catch (Exception ignored) {
        }
    }

    private boolean invokeIfPresent(Object target, String methodName, Class<?>[] paramTypes, Object... args) {
        if (target == null) return false;
        try {
            Method method = target.getClass().getMethod(methodName, paramTypes);
            method.invoke(target, args);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

}