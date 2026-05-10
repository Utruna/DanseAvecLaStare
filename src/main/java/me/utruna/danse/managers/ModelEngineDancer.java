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

        // Appliquer le skin aux bones du modèle
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

            // Récupérer tous les comportements de chaque bone
            String[] playerBones = {"head", "body", "left_arm", "right_arm", "left_leg", "right_leg"};

            // Itérer sur les entries (clé = boneName, valeur = bone object)
            for (java.util.Map.Entry<?, ?> entry : bonesMap.entrySet()) {
                String boneName = (String) entry.getKey();  // La clé est le nom du bone
                Object bone = entry.getValue();
                
                // Vérifier si c'est un bone de joueur
                for (String playerBone : playerBones) {
                    if (boneName != null && boneName.equalsIgnoreCase(playerBone)) {
                        try {
                            plugin.getLogger().info("Processing bone: " + boneName);
                            
                            // Récupérer les comportements du bone
                            Object boneBehaviorsObj = invokeMethodWithException(bone, "getImmutableBoneBehaviors");
                            if (boneBehaviorsObj instanceof java.util.Map) {
                                java.util.Map<?, ?> boneBehaviors = (java.util.Map<?, ?>) boneBehaviorsObj;
                                plugin.getLogger().info("  Behaviors on " + boneName + ": " + boneBehaviors.size());
                                
                                // Chercher PlayerLimb dans les comportements
                                boolean hasPlayerLimb = false;
                                for (java.util.Map.Entry<?, ?> behaviorEntry : boneBehaviors.entrySet()) {
                                    Object behaviorValue = behaviorEntry.getValue();
                                    plugin.getLogger().info("    Behavior type: " + behaviorValue.getClass().getSimpleName());
                                    
                                    // Vérifier si c'est un PlayerLimb
                                    if (behaviorValue.getClass().getSimpleName().contains("PlayerLimb")) {
                                        hasPlayerLimb = true;
                                        
                                        // Afficher les méthodes disponibles
                                        java.lang.reflect.Method[] playerLimbMethods = behaviorValue.getClass().getDeclaredMethods();
                                        plugin.getLogger().info("=== PlayerLimb Methods ===");
                                        for (java.lang.reflect.Method m : playerLimbMethods) {
                                            if (!m.getName().startsWith("lambda")) {
                                                StringBuilder sig = new StringBuilder(m.getName() + "(");
                                                java.lang.Class<?>[] params = m.getParameterTypes();
                                                for (int i = 0; i < params.length; i++) {
                                                    sig.append(params[i].getSimpleName());
                                                    if (i < params.length - 1) sig.append(", ");
                                                }
                                                sig.append(") -> ").append(m.getReturnType().getSimpleName());
                                                plugin.getLogger().info("  " + sig);
                                            }
                                        }
                                        
                                        // Essayer différentes approches
                                        try {
                                            // Approche: setTexture(Player)
                                            invokeMethodWithException(behaviorValue, "setTexture", 
                                                new Class<?>[]{org.bukkit.entity.Player.class}, owner);
                                            plugin.getLogger().info("✓ Skin applied to bone: " + boneName + " (via Player)");
                                        } catch (Exception e1) {
                                            plugin.getLogger().warning("Error applying skin to " + boneName + ": " + e1.getClass().getSimpleName() + " - " + e1.getMessage());
                                        }
                                    }
                                }
                                
                                // Si pas de PlayerLimb, essayer d'en ajouter un
                                if (!hasPlayerLimb) {
                                    plugin.getLogger().info("  No PlayerLimb on " + boneName + " - skipping for now");
                                }
                            } else {
                                plugin.getLogger().info("  getImmutableBoneBehaviors() returned: " + (boneBehaviorsObj == null ? "null" : boneBehaviorsObj.getClass().getSimpleName()));
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Error on bone " + boneName + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                        }
                    }
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