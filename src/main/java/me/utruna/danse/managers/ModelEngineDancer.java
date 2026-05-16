package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.util.ArrayList;
import java.util.List;

public class ModelEngineDancer implements Dancer {

    private final DanseAvecLaStare plugin;
    private final String modelId;
    private final String animationName;
    @SuppressWarnings("deprecation")
    private final PlayerProfile skinProfile;

    private Player owner;
    @SuppressWarnings("deprecation")
    private Dummy<PlayerProfile> dummy;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;
    private String resolvedAnimationName;
    private final List<String> availableAnimationNames = new ArrayList<>();

    public ModelEngineDancer(DanseAvecLaStare plugin, String modelId, String animationName, PlayerProfile skinProfile) {
        this.plugin = plugin;
        this.modelId = modelId == null ? null : modelId.trim();
        this.animationName = animationName == null ? null : animationName.trim();
        this.skinProfile = skinProfile;
    }

    private boolean isDebugEnabled() {
        return owner != null && plugin.isPlayerDebug(owner.getUniqueId());
    }

    private void debugInfo(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().info(message);
        }
    }

    private void debugWarn(String message) {
        if (isDebugEnabled()) {
            plugin.getLogger().warning(message);
        }
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;

        this.dummy = new Dummy<>(skinProfile);
        this.dummy.setLocation(location);
        this.dummy.setRenderRadius(64);

        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        if (this.modeledEntity == null) {
            throw new IllegalStateException("ModelEngine n'a pas pu créer l'entité modelée.");
        }
        this.modeledEntity.registerSelf();

        this.activeModel = ModelEngineAPI.createActiveModel(modelId);
        if (this.activeModel == null) {
            throw new IllegalStateException("Blueprint introuvable dans ModelEngine : " + modelId);
        }

        loadAvailableAnimations();
        this.resolvedAnimationName = resolveAnimationName();

        debugInfo("[DEBUG] Skin appliqué via Dummy: " + (skinProfile != null ? skinProfile.getName() : "null"));

        this.modeledEntity.addModel(activeModel, true);

        if (skinProfile != null) {
            applySkinToModel();
        }
    }

    @SuppressWarnings("deprecation")
    private void applySkinToModel() {
        try {
            debugInfo("=== APPLYING SKIN ===");

            Object bonesObj = invokeMethod(activeModel, "getBones");
            if (bonesObj == null) {
                debugWarn("Could not get bones from ActiveModel");
                return;
            }

            java.util.Map<?, ?> bonesMap = (java.util.Map<?, ?>) bonesObj;
            debugInfo("Bones found: " + bonesMap.size());

            if (!bonesMap.isEmpty()) {
                Object firstBone = bonesMap.values().iterator().next();
                debugInfo("=== BONE METHODS ===");
                java.lang.reflect.Method[] boneMethods = firstBone.getClass().getMethods();
                for (java.lang.reflect.Method method : boneMethods) {
                    String methodName = method.getName();
                    if (methodName.contains("Behavior") || methodName.contains("behavior")) {
                        StringBuilder signature = new StringBuilder(methodName + "(");
                        Class<?>[] params = method.getParameterTypes();
                        for (int i = 0; i < params.length; i++) {
                            signature.append(params[i].getSimpleName());
                            if (i < params.length - 1) {
                                signature.append(", ");
                            }
                        }
                        signature.append(") -> ").append(method.getReturnType().getSimpleName());
                        debugInfo("  " + signature);
                    }
                }
            }

            String[] playerBones = {
                "head", "phead_head", "phead",
                "body", "pbody_body", "pbody",
                "left_arm", "plarm_left_arm", "plarm",
                "right_arm", "prarm_right_arm", "prarm",
                "left_leg", "plleg_left_leg", "plleg",
                "right_leg", "prleg_right_leg", "prleg"
            };

            for (java.util.Map.Entry<?, ?> entry : bonesMap.entrySet()) {
                String boneName = String.valueOf(entry.getKey());
                Object bone = entry.getValue();

                boolean isPlayerBone = false;
                for (String playerBone : playerBones) {
                    if (boneName.equalsIgnoreCase(playerBone)) {
                        isPlayerBone = true;
                        break;
                    }
                }
                if (!isPlayerBone) {
                    continue;
                }

                try {
                    debugInfo("Processing bone: " + boneName);

                    Object boneBehaviorsObj = invokeMethodWithException(bone, "getImmutableBoneBehaviors");
                    if (!(boneBehaviorsObj instanceof java.util.Map)) {
                        debugInfo("  getImmutableBoneBehaviors() returned: " + (boneBehaviorsObj == null ? "null" : boneBehaviorsObj.getClass().getSimpleName()));
                        continue;
                    }

                    java.util.Map<?, ?> boneBehaviors = (java.util.Map<?, ?>) boneBehaviorsObj;
                    debugInfo("  Behaviors on " + boneName + ": " + boneBehaviors.size());

                    boolean hasPlayerLimb = false;
                    for (java.util.Map.Entry<?, ?> behaviorEntry : boneBehaviors.entrySet()) {
                        Object behaviorValue = behaviorEntry.getValue();
                        if (behaviorValue == null) {
                            continue;
                        }

                        String behaviorName = behaviorValue.getClass().getSimpleName();
                        debugInfo("    Behavior type: " + behaviorName);

                        if (!behaviorName.contains("PlayerLimb")) {
                            continue;
                        }

                        hasPlayerLimb = true;
                        Object limbType = invokeMethod(behaviorValue, "getLimbType");
                        debugInfo("    Limb type: " + String.valueOf(limbType));

                        try {
                            invokeMethodWithException(behaviorValue, "setTexture", new Class<?>[]{PlayerProfile.class}, owner.getPlayerProfile());
                            debugInfo("    ✓ Skin applied via PlayerProfile");
                        } catch (Exception e1) {
                            try {
                                invokeMethodWithException(behaviorValue, "setTexture", new Class<?>[]{Player.class}, owner);
                                debugInfo("    ✓ Skin applied via Player");
                            } catch (Exception e2) {
                                debugWarn("    ✗ setTexture failed on " + boneName + ": " + e2.getClass().getSimpleName() + " - " + e2.getMessage());
                            }
                        }
                    }

                    if (!hasPlayerLimb) {
                        debugWarn("  No PlayerLimb behavior found on " + boneName);
                    }
                } catch (Exception e) {
                    debugWarn("Error on bone " + boneName + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }

            debugInfo("=== SKIN APPLICATION COMPLETE ===");
        } catch (Exception e) {
            debugWarn("Error in applySkinToModel: " + e.getMessage());
        }
    }

    private Object invokeMethod(Object obj, String methodName, Class<?>[] paramTypes, Object... args) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName, paramTypes);
            return method.invoke(obj, args);
        } catch (Exception e) {
            return null;
        }
    }

    private Object invokeMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }

    private Object invokeMethodWithException(Object obj, String methodName, Class<?>[] paramTypes, Object... args) throws Exception {
        java.lang.reflect.Method method = obj.getClass().getMethod(methodName, paramTypes);
        return method.invoke(obj, args);
    }

    private Object invokeMethodWithException(Object obj, String methodName) throws Exception {
        java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
        return method.invoke(obj);
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (dummy != null && activeModel != null) {
            Location playerLoc = owner.getLocation().clone();
            Location danceLocation = style.computeLocation(playerLoc, tick);

            dummy.setLocation(danceLocation);
            dummy.setYBodyRot(danceLocation.getYaw());
            dummy.setYHeadRot(danceLocation.getYaw());

            if (resolvedAnimationName != null && !activeModel.getAnimationHandler().isPlayingAnimation(resolvedAnimationName)) {
                activeModel.getAnimationHandler().playAnimation(resolvedAnimationName, 0.1d, 0.1d, 1.0d, true);
            }
        }
    }

    private void loadAvailableAnimations() {
        try {
            Object modelObj = invokeMethod(activeModel, "getModel");
            Object animationsObj = null;
            if (modelObj != null) {
                animationsObj = invokeMethod(modelObj, "getAnimations");
            }
            if (animationsObj == null) {
                animationsObj = invokeMethod(activeModel, "getAnimations");
            }

            availableAnimationNames.clear();
            if (animationsObj instanceof java.util.Map) {
                java.util.Map<?, ?> animations = (java.util.Map<?, ?>) animationsObj;
                for (Object key : animations.keySet()) {
                    if (key != null) {
                        availableAnimationNames.add(key.toString());
                    }
                }
            } else if (animationsObj instanceof java.util.Collection) {
                java.util.Collection<?> col = (java.util.Collection<?>) animationsObj;
                for (Object item : col) {
                    if (item != null) {
                        availableAnimationNames.add(item.toString());
                    }
                }
            }
        } catch (Exception e) {
            debugWarn("Could not inspect animations from blueprint: " + e.getMessage());
        }
    }

    private String resolveAnimationName() {
        if (animationName != null && !animationName.isBlank()) {
            if (!availableAnimationNames.isEmpty()) {
                for (String candidate : availableAnimationNames) {
                    if (candidate.equals(animationName) || candidate.equalsIgnoreCase(animationName)) {
                        return candidate;
                    }
                }

                for (String candidate : availableAnimationNames) {
                    if (candidate.equals(modelId) || candidate.equalsIgnoreCase(modelId)) {
                        debugWarn("AnimationName '" + animationName + "' introuvable pour le modèle '" + modelId + "'. Utilisation de '" + candidate + "' à la place.");
                        return candidate;
                    }
                }

                String fallback = availableAnimationNames.get(0);
                debugWarn("AnimationName '" + animationName + "' introuvable pour le modèle '" + modelId + "'. Utilisation de '" + fallback + "' à la place.");
                return fallback;
            }

            debugWarn("AnimationName '" + animationName + "' demandé, mais aucune animation n'a pu être inspectée sur le blueprint '" + modelId + "'.");
            return animationName;
        }

        if (!availableAnimationNames.isEmpty()) {
            String fallback = availableAnimationNames.get(0);
            debugInfo("No animationName provided — using first animation from blueprint: " + fallback);
            return fallback;
        }

        debugWarn("No animationName provided and no animations found on blueprint: " + modelId);
        return null;
    }

    @Override
    public void stop() {
        if (modeledEntity != null) {
            modeledEntity.destroy();
            modeledEntity = null;
            activeModel = null;
            dummy = null;
        }
    }
}