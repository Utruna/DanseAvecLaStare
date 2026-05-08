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
    private final PlayerProfile skinProfile;
    
    private Player owner;
    private Dummy<PlayerProfile> dummy;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;

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