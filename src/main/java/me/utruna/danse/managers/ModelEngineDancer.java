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
import java.lang.reflect.Method;
import java.util.List;

/**
 * Implémentation {@link Dancer} basée sur ModelEngine 4.0.9.
 * Crée un {@code Dummy<PlayerProfile>} comme support du skin, charge et attache un {@code ActiveModel},
 * puis applique la texture sur les bones {@code PlayerLimb} via réflexion pour rester compatible
 * avec plusieurs builds de ModelEngine sans recompilation.
 */
public class ModelEngineDancer implements Dancer {

    private final DanseAvecLaStare plugin;
    private final String modelId;
    private final String animationName;
    @SuppressWarnings("deprecation")
    private final PlayerProfile skinProfile;
    private final boolean useFallbackMode;
    private final String fallbackModelId;

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
        this.useFallbackMode = plugin.getConfig().getBoolean("modelEngine.useFallbackMode", false);
        String configuredFallbackModelId = plugin.getConfig().getString("modelEngine.fallbackModelId", "joueur_fallback");
        this.fallbackModelId = configuredFallbackModelId == null || configuredFallbackModelId.isBlank()
            ? "joueur_fallback"
            : configuredFallbackModelId.trim();
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

        String blueprintId = getEffectiveModelId();
        if (blueprintId == null || blueprintId.isBlank()) {
            throw new IllegalStateException("Aucun blueprint ModelEngine valide n'est disponible.");
        }

        this.dummy = new Dummy<>(skinProfile);
        this.dummy.setLocation(location);
        this.dummy.setRenderRadius(64);

        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        if (this.modeledEntity == null) {
            throw new IllegalStateException("ModelEngine n'a pas pu créer l'entité modelée.");
        }
        this.modeledEntity.registerSelf();

        this.activeModel = ModelEngineAPI.createActiveModel(blueprintId);
        if (this.activeModel == null) {
            throw new IllegalStateException("Blueprint introuvable dans ModelEngine : " + blueprintId);
        }

        loadAvailableAnimations();
        this.resolvedAnimationName = resolveAnimationName();

        debugInfo("[DEBUG] Mode de rendu=" + (useFallbackMode ? "fallback" : "standard") + ", blueprint=" + blueprintId);
        debugInfo("[DEBUG] Skin appliqué via Dummy: " + (skinProfile != null ? skinProfile.getName() : "null"));

        this.modeledEntity.addModel(activeModel, true);

        // Délai 2 ticks : ME4 spawne ses display entities de bones de façon différée
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> hideDancerFromOwner(true), 2L);

        if (skinProfile != null) {
            applySkinToModel();
        }
    }

    private void applySkinToModel() {
        try {
            debugInfo("=== APPLYING SKIN (" + (useFallbackMode ? "FALLBACK" : "STANDARD") + ") ===");

            Object bonesObj = invokeMethod(activeModel, "getBones");
            if (bonesObj == null) {
                debugWarn("Could not get bones from ActiveModel");
                return;
            }

            java.util.Map<?, ?> bonesMap = (java.util.Map<?, ?>) bonesObj;
            debugInfo("Bones found: " + bonesMap.size());

            for (java.util.Map.Entry<?, ?> entry : bonesMap.entrySet()) {
                String boneName = String.valueOf(entry.getKey());
                Object bone = entry.getValue();

                try {
                    debugInfo("Processing bone: " + boneName);

                    Object boneBehaviorsObj = invokeMethodWithException(bone, "getImmutableBoneBehaviors");
                    if (!(boneBehaviorsObj instanceof java.util.Map)) {
                        debugInfo("  getImmutableBoneBehaviors() returned: " + (boneBehaviorsObj == null ? "null" : boneBehaviorsObj.getClass().getSimpleName()));
                        continue;
                    }

                    java.util.Map<?, ?> boneBehaviors = (java.util.Map<?, ?>) boneBehaviorsObj;
                    debugInfo("  Behaviors on " + boneName + ": " + boneBehaviors.size());

                    boolean hasTextureCapableBehavior = false;
                    for (java.util.Map.Entry<?, ?> behaviorEntry : boneBehaviors.entrySet()) {
                        Object behaviorValue = behaviorEntry.getValue();
                        if (behaviorValue == null) {
                            continue;
                        }

                        String behaviorName = behaviorValue.getClass().getSimpleName();
                        debugInfo("    Behavior type: " + behaviorName);

                        if (applyTextureToBehavior(behaviorValue)) {
                            hasTextureCapableBehavior = true;
                            debugInfo("    ✓ Skin appliqué sur " + boneName + " via " + behaviorName);
                        } else {
                            debugInfo("    - Aucun setTexture compatible sur " + behaviorName);
                        }
                    }

                    if (!hasTextureCapableBehavior) {
                        debugWarn("  No texture-capable behavior found on " + boneName);
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

    private boolean applyTextureToBehavior(Object behavior) {
        if (behavior == null) {
            return false;
        }

        Method[] methods = behavior.getClass().getMethods();
        for (Method method : methods) {
            if (!method.getName().equals("setTexture") || method.getParameterCount() != 1) {
                continue;
            }

            Class<?> parameterType = method.getParameterTypes()[0];
            try {
                // Priorité au skinProfile fourni (profil du joueur ciblé, pas forcément owner)
                if (skinProfile != null && parameterType.isInstance(skinProfile)) {
                    method.invoke(behavior, skinProfile);
                    return true;
                }

                // Fallback : passer le joueur directement si le behavior l'accepte
                if (owner != null && Player.class.isAssignableFrom(parameterType)) {
                    method.invoke(behavior, owner);
                    return true;
                }
            } catch (Exception ex) {
                debugWarn("    ✗ setTexture failed on " + behavior.getClass().getSimpleName() + " : " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
            }
        }

        return false;
    }

    private String getEffectiveModelId() {
        if (useFallbackMode) {
            return fallbackModelId != null && !fallbackModelId.isBlank() ? fallbackModelId : modelId;
        }
        return modelId;
    }

    private Object invokeMethod(Object obj, String methodName) {
        try {
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
            return method.invoke(obj);
        } catch (Exception e) {
            return null;
        }
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

    private void hideDancerFromOwner(boolean hide) {
        if (owner == null) return;

        // Cacher/montrer les entités visuelles de chaque bone (Display Entities en ME4 4.x)
        if (activeModel != null) {
            Object bonesObj = invokeMethod(activeModel, "getBones");
            if (bonesObj instanceof java.util.Map<?, ?> bonesMap) {
                for (Object bone : bonesMap.values()) {
                    hideBoneEntity(owner, bone, hide);
                }
            }
        }

        // Cacher/montrer aussi l'entité de base du Dummy si accessible
        org.bukkit.entity.Entity base = getBaseEntity();
        if (base != null) {
            if (hide) owner.hideEntity(plugin, base);
            else owner.showEntity(plugin, base);
            for (org.bukkit.entity.Entity passenger : base.getPassengers()) {
                if (hide) owner.hideEntity(plugin, passenger);
                else owner.showEntity(plugin, passenger);
            }
        }
    }

    private void hideBoneEntity(Player player, Object bone, boolean hide) {
        for (String method : new String[]{"getBukkitEntity", "getEntity", "getScaffold", "getVehicle", "getBase", "getLivingEntity"}) {
            Object e = invokeMethod(bone, method);
            if (e instanceof org.bukkit.entity.Entity entity) {
                if (hide) player.hideEntity(plugin, entity);
                else player.showEntity(plugin, entity);
                for (org.bukkit.entity.Entity passenger : entity.getPassengers()) {
                    if (hide) player.hideEntity(plugin, passenger);
                    else player.showEntity(plugin, passenger);
                }
                return;
            }
        }
    }

    private org.bukkit.entity.Entity getBaseEntity() {
        // Essai via modeledEntity (plus fiable en ME4 4.x)
        if (modeledEntity != null) {
            Object base = invokeMethod(modeledEntity, "getBase");
            if (base instanceof org.bukkit.entity.Entity ent) return ent;
            if (base != null) {
                Object e = invokeMethod(base, "getBukkitEntity");
                if (e == null) e = invokeMethod(base, "getEntity");
                if (e instanceof org.bukkit.entity.Entity ent) return ent;
            }
        }
        if (dummy == null) return null;
        Object e = invokeMethod(dummy, "getBukkitEntity");
        if (e == null) e = invokeMethod(dummy, "getEntity");
        return e instanceof org.bukkit.entity.Entity ent ? ent : null;
    }

    @Override
    public void stop() {
        if (modeledEntity != null) {
            if (owner != null) hideDancerFromOwner(false);
            modeledEntity.destroy();
            modeledEntity = null;
            activeModel = null;
            dummy = null;
        }
    }
}