package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ModelEngineDancer implements Dancer {

    private Player owner;
    private Dummy<Player> dummy;
    private ModeledEntity modeledEntity;
    private ActiveModel activeModel;
    private final DanseAvecLaStare plugin;
    private final String modelId;

    public ModelEngineDancer(DanseAvecLaStare plugin, String modelId) {
        this.plugin = plugin;
        this.modelId = (modelId == null || modelId.isBlank()) ? "danseur" : modelId.trim();
    }

    @Override
    public void spawn(Location location, Player player) {
        this.owner = player;

        // Le fait de donner 'player' au Dummy permet à ModelEngine de charger le skin tout seul
        this.dummy = new Dummy<>(player);
        this.dummy.setLocation(location);
        this.dummy.setVisible(true);
        this.dummy.setRenderRadius(64);

        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        this.modeledEntity.registerSelf();

        this.activeModel = ModelEngineAPI.createActiveModel(modelId);
        
        if (this.activeModel != null) {
            this.activeModel.setAutoRendererInitialization(true);
            this.modeledEntity.addModel(activeModel, true);
            this.activeModel.generateModel();
            this.activeModel.initializeRenderer();
        } else {
            plugin.getLogger().warning("Le modèle '" + modelId + "' est introuvable.");
        }
    }

   @Override
    public void tick(int tick, DanceStyle style) {
        if (dummy != null && activeModel != null) {
            Location danceLocation = style.computeLocation(owner.getLocation(), tick);
            dummy.setLocation(danceLocation);
            
            dummy.setYBodyRot(danceLocation.getYaw());
            dummy.setYHeadRot(danceLocation.getYaw());

            String anim = "dance"; 
            if (activeModel.getBlueprint().getAnimations().containsKey(anim)) {
                if (!activeModel.getAnimationHandler().isPlayingAnimation(anim)) {
                    activeModel.getAnimationHandler().playAnimation(anim, 0.1d, 0.1d, 1.0d, true);
                }
            }
        }
    }

    @Override
    public void stop() {
        if (modeledEntity != null) {
            modeledEntity.destroy();
        }
    }
}