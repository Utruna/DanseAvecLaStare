# DanseAvecLaStare

Plugin Minecraft (Spigot 1.21.x) permettant aux joueurs de lancer des danses visibles par les autres.

Cette branche améliore la mise en place initiale en remplaçant l'ArmorStand simple par un NPC (via Citizens) afin d'afficher
le skin complet du joueur et d'avoir un rendu plus naturel pour les observateurs.

## Ce qui a été fait

- Refactorisation des styles : chaque style est une classe (`AbstractDanceStyle` + `*Style`).
- Remplacement de l'ArmorStand par un NPC player (Citizens) pour afficher le skin complet.
- Le NPC suit la position/rotation du joueur (mise à jour chaque tick) pour un rendu organique.
- Option pour cacher le NPC au joueur qui l'a activé (par défaut activée).
- Commande unique `/danse` avec autocomplétion (styles + `list`/`stop`).
- Gestion d'erreurs améliorée (logs + stack traces lors d'exception pour faciliter le debug).

## Fonctionnalités

- Danse visible en temps réel par les autres joueurs (NPC).
- Styles fournis : `twist`, `spin`, `disco`, `moonwalk`, `wave` (facilement extensible).
- Contrôle de la visibilité du NPC pour le lanceur (argument optionnel).

## Commandes

- `/danse` : lance/arrête la danse par défaut (`twist` si non précisé).
- `/danse list` : affiche la liste des styles disponibles.
- `/danse stop` : arrête la danse en cours.
- `/danse <style> [visible|off|false]` : lance le style donné ; le second argument optionnel contrôle la
  visibilité du PNJ pour le lanceur. Par défaut le PNJ est caché au lanceur.

Exemples :

- `/danse twist` → lance `twist` et cache le PNJ au lanceur.
- `/danse twist visible` → lance `twist` et laisse le PNJ visible au lanceur.

## Installation

Prérequis serveur :

- Java 21
- Paper/Spigot 1.21.x
- Citizens plugin (installé sur le serveur)

Étapes :

1. Compiler :

```bash
mvn clean package
```

2. Copier `target/DanseAvecLaStare-1.0.0-SNAPSHOT.jar` dans `plugins/`.
3. Installer `Citizens` et redémarrer le serveur.

## Développement

Prérequis locaux :

- Maven 3.9+
- JDK 21

Exécuter les tests unitaires :

```bash
mvn test
```

Les tests couvrent la logique des styles et du parsing. Le comportement runtime (NPC/skin) nécessite un serveur avec `Citizens`.

## Structure du code

- `DanseAvecLaStare.java` — point d'entrée, commande et tab completion.
- `managers/DanceManager.java` — gestion des danses, création/destruction de NPC, boucle d'animation.
- `managers/DanceStyle.java`, `AbstractDanceStyle.java`, `*Style.java` — implémentations des styles.
- `listeners/PlayerListener.java` — nettoyage des danses si le joueur quitte.

## Debug & Troubleshooting

- Si la commande plante : regarde les logs du serveur, le plugin logge les stack traces pour faciliter le debug.
- Si le skin ne s'applique pas, le plugin essaie plusieurs approches (réflexion sur différentes versions de `SkinTrait`).
  Si rien ne fonctionne le NPC sera spawn sans skin.
- Assure-toi de la compatibilité de `Citizens` avec ta version de serveur.

## Prochaines améliorations possibles

- Ajouter un fallback ProtocolLib (implémentation de NPC via packets) pour éviter la dépendance Citizens.
- Ajouter `config.yml` pour des préférences par défaut (ex. `hideByDefault`).
- Persistance des préférences par joueur.

## Auteur

Utruna
