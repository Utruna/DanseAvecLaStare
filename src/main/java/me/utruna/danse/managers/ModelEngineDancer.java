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

    /**
     * Constructeur pour ModelEngine.
     * @param plugin Instance du plugin.
     * @param modelId ID du blueprint (ex: "danseur").
     * @param animationName Nom de l'animation à jouer (ex: "dance").
     * @param skinProfile Le profil du skin à appliquer (peut être null).
     */
    public ModelEngineDancer(DanseAvecLaStare plugin, String modelId, String animationName, PlayerProfile skinProfile) {
        this.plugin = plugin;
        this.modelId = (modelId == null || modelId.isBlank()) ? "danseur" : modelId.trim();
        this.animationName = (animationName == null || animationName.isBlank()) ? "dance" : animationName.trim();
        this.skinProfile = skinProfile;
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;

        // 1) Création du Dummy avec le skinProfile
        this.dummy = new Dummy<>(skinProfile);
        this.dummy.setLocation(location);
        this.dummy.setVisible(true);
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

        // 4) Attachement du modèle
        this.modeledEntity.addModel(activeModel, true);
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (dummy != null && activeModel != null) {
            Location playerLoc = owner.getLocation().clone();
            Location danceLocation = style.computeLocation(playerLoc, tick);
            
            // Mise à jour de la position et de la rotation
            dummy.setLocation(danceLocation);
            dummy.setYBodyRot(danceLocation.getYaw());
            dummy.setYHeadRot(danceLocation.getYaw());

            // Lancement de l'animation dynamique si elle n'est pas déjà en cours
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