package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation {@link Dancer} basée sur ModelEngine 4.0.9.
 * Crée un {@code Dummy<PlayerProfile>} comme support du skin, charge et attache un {@code ActiveModel},
 * puis applique la texture sur les bones {@code PlayerLimb} via réflexion pour rester compatible
 * avec plusieurs builds de ModelEngine sans recompilation.
 */
public class ModelEngineDancer implements Dancer {

    /**
     * Cache statique partagé entre toutes les instances : classe du behavior ME4 →
     * liste des méthodes {@code setTexture(X)} à 1 paramètre.
     * Peuplé au premier spawn de chaque type de behavior ; jamais invalidé en cours de
     * session (les classes ME4 ne changent pas sans redémarrage du serveur).
     */
    private static final ConcurrentHashMap<Class<?>, List<Method>> SET_TEXTURE_CACHE = new ConcurrentHashMap<>();

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
    private int renderRadius;
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
        this.renderRadius = plugin.getConfig().getInt("modelEngine.renderRadius", 256);
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
        this.dummy.setRenderRadius(renderRadius);

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

        this.dummy.getData().getTracked().setPlayerPredicate(p -> !p.getUniqueId().equals(owner.getUniqueId()));

        this.modeledEntity.addModel(activeModel, true);

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
        if (behavior == null) return false;

        List<Method> methods = SET_TEXTURE_CACHE.computeIfAbsent(behavior.getClass(), cls -> {
            List<Method> found = new ArrayList<>();
            for (Method m : cls.getMethods()) {
                if (m.getName().equals("setTexture") && m.getParameterCount() == 1) found.add(m);
            }
            return found;
        });

        for (Method method : methods) {
            Class<?> parameterType = method.getParameterTypes()[0];
            try {
                if (skinProfile != null && parameterType.isInstance(skinProfile)) {
                    method.invoke(behavior, skinProfile);
                    return true;
                }
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

    @Override
    public void stop() {
        if (modeledEntity != null) {
            modeledEntity.destroy();
            modeledEntity = null;
            activeModel = null;
            dummy = null;
        }
    }

    @Override
    public void setRenderRadius(int radius) {
        this.renderRadius = Math.max(1, radius);
        if (dummy != null) {
            dummy.setRenderRadius(this.renderRadius);
        }
    }

    @Override
    public void setOwnerCanSee(boolean canSee) {
        if (dummy == null || owner == null) return;
        if (canSee) {
            dummy.getData().getTracked().setPlayerPredicate(p -> p.getUniqueId().equals(owner.getUniqueId()));
        } else {
            dummy.getData().getTracked().setPlayerPredicate(p -> !p.getUniqueId().equals(owner.getUniqueId()));
        }
    }
}