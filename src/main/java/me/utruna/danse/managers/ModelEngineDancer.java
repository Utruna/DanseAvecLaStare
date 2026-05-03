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
    private final PlayerProfile skinProfile;
    private Dummy<PlayerProfile> dummy; // API officielle

    public ModelEngineDancer(DanseAvecLaStare plugin, String modelId, PlayerProfile skinProfile) {
        this.plugin = plugin;
        this.modelId = modelId;
        this.skinProfile = skinProfile;
    }

    @Override
    public void spawn(Location location, Player player) {
        // C'est ici que ModelEngine se connecte. Si c'est rouge, attends 30s que Maven charge.
        this.dummy = new Dummy<>(skinProfile);
        this.dummy.setLocation(location);
        this.dummy.setVisible(true);
        this.dummy.setRenderRadius(64);

        this.modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
        this.modeledEntity.registerSelf();
        this.activeModel = ModelEngineAPI.createActiveModel(modelId);
        if (this.activeModel != null) {
            this.modeledEntity.addModel(activeModel, true);
        }
    }

    @Override
    public void tick(int tick, DanceStyle style) {
        if (dummy != null && activeModel != null) {
            dummy.setLocation(style.computeLocation(dummy.getLocation(), tick));
            // Ajoute ici ta logique de rotation si nécessaire
        }
    }

    @Override
    public void stop() {
        if (modeledEntity != null) modeledEntity.destroy();
    }
}