package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

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

    @SuppressWarnings("deprecation")
    public ModelEngineDancer(DanseAvecLaStare plugin, String modelId, String animationName, PlayerProfile skinProfile) {
        this.plugin = plugin;
        this.modelId = (modelId == null || modelId.isBlank()) ? "danseur" : modelId.trim();
        this.animationName = (animationName == null || animationName.isBlank()) ? "dance" : animationName.trim();
        this.skinProfile = skinProfile;
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;

        // 1) Création du Dummy avec le profil (contient la texture du joueur)
        this.dummy = new Dummy<>(skinProfile);
        this.dummy.setLocation(location);
        this.dummy.setRenderRadius(64);

        // 2) Création de l'entité modelée
        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        if (this.modeledEntity == null) {
            throw new IllegalStateException("ModelEngine n'a pas pu créer l'entité modelée.");
        }
        this.modeledEntity.registerSelf();

        // 3) Chargement du modèle actif
        this.activeModel = ModelEngineAPI.createActiveModel(modelId);
        if (this.activeModel == null) {
            throw new IllegalStateException("Blueprint introuvable dans ModelEngine : " + modelId);
        }

        plugin.getLogger().info("[DEBUG] Skin appliqué via Dummy: " + (skinProfile != null ? skinProfile.getName() : "null"));

        this.modeledEntity.addModel(activeModel, true);

        // Appliquer la texture aux player limbs.
        if (skinProfile != null) {
            applySkinToModel();
        }
    }

    @SuppressWarnings("deprecation")
    private void applySkinToModel() {
        try {
            plugin.getLogger().info("=== APPLYING SKIN ===");
            
            // getBones() retourne une Map<String, ModelBone>
            Object bonesObj = invokeMethod(activeModel, "getBones");
            if (bonesObj == null) {
                plugin.getLogger().warning("Could not get bones from ActiveModel");
                return;
            }

            // Convertir la Map en itérable
            java.util.Map<?, ?> bonesMap = (java.util.Map<?, ?>) bonesObj;
            plugin.getLogger().info("Bones found: " + bonesMap.size());

            // Découvrir les méthodes sur les bones
            if (!bonesMap.isEmpty()) {
                Object firstBone = bonesMap.values().iterator().next();
                plugin.getLogger().info("=== BONE METHODS ===");
                java.lang.reflect.Method[] boneMethods = firstBone.getClass().getMethods();
                for (java.lang.reflect.Method m : boneMethods) {
                    String methodName = m.getName();
                    if (methodName.contains("Behavior") || methodName.contains("behavior")) {
                        StringBuilder sig = new StringBuilder(methodName + "(");
                        java.lang.Class<?>[] params = m.getParameterTypes();
                        for (int i = 0; i < params.length; i++) {
                            sig.append(params[i].getSimpleName());
                            if (i < params.length - 1) sig.append(", ");
                        }
                        sig.append(") -> ").append(m.getReturnType().getSimpleName());
                        plugin.getLogger().info("  " + sig);
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
                String boneName = (String) entry.getKey();
                Object bone = entry.getValue();

                boolean isPlayerBone = false;
                for (String playerBone : playerBones) {
                    if (boneName != null && boneName.equalsIgnoreCase(playerBone)) {
                        isPlayerBone = true;
                        break;
                    }
                }
                if (!isPlayerBone) {
                    continue;
                }

                try {
                    plugin.getLogger().info("Processing bone: " + boneName);

                    Object boneBehaviorsObj = invokeMethodWithException(bone, "getImmutableBoneBehaviors");
                    if (!(boneBehaviorsObj instanceof java.util.Map)) {
                        plugin.getLogger().info("  getImmutableBoneBehaviors() returned: " + (boneBehaviorsObj == null ? "null" : boneBehaviorsObj.getClass().getSimpleName()));
                        continue;
                    }

                    java.util.Map<?, ?> boneBehaviors = (java.util.Map<?, ?>) boneBehaviorsObj;
                    plugin.getLogger().info("  Behaviors on " + boneName + ": " + boneBehaviors.size());

                    boolean hasPlayerLimb = false;
                    for (java.util.Map.Entry<?, ?> behaviorEntry : boneBehaviors.entrySet()) {
                        Object behaviorValue = behaviorEntry.getValue();
                        if (behaviorValue == null) {
                            continue;
                        }

                        String behaviorName = behaviorValue.getClass().getSimpleName();
                        plugin.getLogger().info("    Behavior type: " + behaviorName);

                        if (!behaviorName.contains("PlayerLimb")) {
                            continue;
                        }

                        hasPlayerLimb = true;
                        Object limbType = invokeMethod(behaviorValue, "getLimbType");
                        plugin.getLogger().info("    Limb type: " + String.valueOf(limbType));

                        try {
                            invokeMethodWithException(behaviorValue, "setTexture", new Class<?>[]{org.bukkit.profile.PlayerProfile.class}, owner.getPlayerProfile());
                            plugin.getLogger().info("    ✓ Skin applied via PlayerProfile");
                        } catch (Exception e1) {
                            try {
                                invokeMethodWithException(behaviorValue, "setTexture", new Class<?>[]{org.bukkit.entity.Player.class}, owner);
                                plugin.getLogger().info("    ✓ Skin applied via Player");
                            } catch (Exception e2) {
                                plugin.getLogger().warning("    ✗ setTexture failed on " + boneName + ": " + e2.getClass().getSimpleName() + " - " + e2.getMessage());
                            }
                        }
                    }

                    if (!hasPlayerLimb) {
                        plugin.getLogger().warning("  No PlayerLimb behavior found on " + boneName);
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Error on bone " + boneName + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                }
            }
            plugin.getLogger().info("=== SKIN APPLICATION COMPLETE ===");
        } catch (Exception e) {
            plugin.getLogger().warning("Error in applySkinToModel: " + e.getMessage());
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

            if (!activeModel.getAnimationHandler().isPlayingAnimation(animationName)) {
                activeModel.getAnimationHandler().playAnimation(animationName, 0.1d, 0.1d, 1.0d, true);
            }
        }
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