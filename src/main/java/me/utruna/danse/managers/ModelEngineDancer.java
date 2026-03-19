package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.lang.reflect.Method;
import java.util.UUID;

public class ModelEngineDancer implements Dancer {

    private Player owner;
    private Dummy<?> dummy;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;

    // Must match the .bbmodel identifier loaded by ModelEngine
    private final String modelId = "danseur";

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;

        // 1) Build a Dummy using the ME service available in this server version.
        this.dummy = createDummyCompat();
        if (this.dummy == null) {
            throw new IllegalStateException("Impossible de creer un Dummy ModelEngine");
        }
        this.dummy.setLocation(location);

        // 2) Create modeled entity and load model
        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        this.activeModel = ModelEngineAPI.createActiveModel(modelId);

        if (activeModel != null) {
            // 3) Player skin mapping: try known APIs in descending order.
            applyPlayerSkinCompat(player);

            // 4) Attach model regardless of method naming differences.
            addModelCompat();
        }
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (modeledEntity != null && activeModel != null) {
            Location playerLoc = owner.getLocation().clone();
            Location danceLocation = style.computeLocation(playerLoc, tick);
            
            dummy.setLocation(danceLocation);
            setRotationCompat(danceLocation.getYaw());

            // Play "dance" animation with runtime-compatible signatures.
            playDanceAnimationCompat();
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

    private Dummy<?> createDummyCompat() {
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

    private void addModelCompat() {
        // modeledEntity.addModel(activeModel, true)
        if (invokeIfPresent(modeledEntity, "addModel", new Class<?>[]{activeModel.getClass(), boolean.class}, activeModel, true)) {
            return;
        }

        // modeledEntity.addActiveModel(activeModel, true)
        invokeIfPresent(modeledEntity, "addActiveModel", new Class<?>[]{activeModel.getClass(), boolean.class}, activeModel, true);
    }

    private void setRotationCompat(float yaw) {
        invokeIfPresent(dummy, "setYBodyRot", new Class<?>[]{float.class}, yaw);
        invokeIfPresent(dummy, "setYHeadRot", new Class<?>[]{float.class}, yaw);
    }

    private void playDanceAnimationCompat() {
        try {
            Method getAnimationHandler = activeModel.getClass().getMethod("getAnimationHandler");
            Object handler = getAnimationHandler.invoke(activeModel);
            if (handler == null) return;

            if (invokeIfPresent(handler, "playAnimation", new Class<?>[]{String.class, double.class, double.class, double.class, boolean.class}, "dance", 0.1d, 0.1d, 1.0d, false)) {
                return;
            }

            if (invokeIfPresent(handler, "playAnimation", new Class<?>[]{String.class, int.class, int.class, int.class, boolean.class}, "dance", 0, 0, 1, false)) {
                return;
            }

            invokeIfPresent(handler, "playAnimation", new Class<?>[]{String.class}, "dance");
        } catch (Exception ignored) {
        }
    }

    private void removeModeledEntityCompat() {
        try {
            Method entityHandlerGetter = ModelEngineAPI.class.getMethod("getEntityHandler");
            Object entityHandler = entityHandlerGetter.invoke(null);
            if (entityHandler != null) {
                UUID uuid = getDummyUuidCompat();
                if (uuid != null) {
                    if (!invokeIfPresent(entityHandler, "removeModeledEntity", new Class<?>[]{UUID.class}, uuid)) {
                        invokeIfPresent(entityHandler, "removeModeledEntity", new Class<?>[]{dummy.getClass()}, dummy);
                    }
                } else {
                    invokeIfPresent(entityHandler, "removeModeledEntity", new Class<?>[]{dummy.getClass()}, dummy);
                }
            }
        } catch (Exception ignored) {
        }

        invokeIfPresent(modeledEntity, "destroy", new Class<?>[]{});
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

    private UUID getDummyUuidCompat() {
        if (dummy == null) return null;
        for (String methodName : new String[]{"getUniqueId", "getUUID", "getUuid"}) {
            try {
                Method m = dummy.getClass().getMethod(methodName);
                Object v = m.invoke(dummy);
                if (v instanceof UUID id) {
                    return id;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}