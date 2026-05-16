# DanseAvecLaStare

Plugin Paper (1.21.x) qui affiche des danseurs 3D animés via ModelEngine 4.0.9.

Résumé : récupération du `PlayerProfile`, création d'un `Dummy<PlayerProfile>`, attachement d'un `ActiveModel` et application des textures sur les bones `PlayerLimb`.

Principes clés
- Intégration : ModelEngine 4.0.9
- Pipeline de skin : `Dummy<PlayerProfile>` → `ModeledEntity` → `ActiveModel` → `applySkinToModel()`
- Configurable via `config.yml` (ajout de danses sans recompiler)

---

Installation

Prérequis : Paper 1.21.x, Java 21+, ModelEngine 4.0.9

Build local :

```bash
mvn clean package -DskipTests
```

Déploiement : copier le JAR `target/DanseAvecLaStare-*.jar` dans `plugins/` puis redémarrer le serveur.

---

Configuration minimale

```yaml
useModelEngine: true

modelEngine:
  defaultModelId: danseur
  # Assurez-vous que defaultAnimationName et animationName correspondent
  defaultAnimationName: dance

dances:
  twist:
    displayName: "Twist"
    modelId: danseur
    animationName: dance
    movementType: STATIC
```

Note importante : l'`animationName` doit correspondre exactement au nom présent dans le `.bbmodel` (par ex. `dj_animation01`).

---

Commandes

- `/danse <style>` : lance une danse
- `/danse list` : liste les styles
- `/danse stop` : arrête la danse
- `/danse debug` : affiche des logs de diagnostic (bones, application de skin)

---

Pipeline technique (court)

1. `SkinService` récupère le `PlayerProfile` (ou `player.getPlayerProfile()`)
2. `ModelEngineDancer.spawn()` crée un `Dummy<PlayerProfile>` et l'enregistre
3. `ActiveModel` chargé avec `createActiveModel(modelId)` et ajouté via `addModel(...)`
4. `applySkinToModel()` parcourt `activeModel.getBones()` et appelle `setTexture(...)` sur chaque `PlayerLimb`
5. `tick()` met à jour la position et déclenche l'animation via `activeModel.getAnimationHandler()`

Si les logs indiquent `✓ Skin applied` pour tous les limbs mais que seul la tête est visible, investiguez le `.bbmodel` ou le resource pack côté client.

---

Documentation détaillée : `docs/pipeline_skin.md`, `docs/BBMODEL_INTEGRATION.md`, `docs/SKIN_RENDERING_FIX.md`.

Contributions : PR/Issues bienvenues (joindre le `.bbmodel` si un modèle pose problème).
