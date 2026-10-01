# Vérification du livrable simplifié

## Modifications réalisées

Cette version est dérivée de `interopPlatformeEchangeCI.zip` (Gesparck AI).

- Conservation de l'architecture `Controller → Service → Repository / Utility`.
- Conservation de la validation, de la gestion de concurrence et de la sécurité `known_hosts`.
- Conservation des tests unitaires existants.
- Suppression des profils Maven `oracle-driver` et `validation-without-oracle`.
- Suppression de Maven Enforcer qui imposait une version exacte de Maven.
- Déclaration directe de `com.oracle:ojdbc7:12.1.0.2` dans le POM, puisque le pilote est disponible dans le dépôt Maven local/interne.
- README réduit et aligné sur la signature réelle de `traitement_fichier_epic`.
- Documentation de la correspondance confirmée : `TEST_DIR` → `/oracle/traitement`.
- Suppression des rapports de validation générés par l'ancien projet afin de ne pas présenter de résultats associés à un POM différent.

## Commandes à exécuter dans l'environnement cible

```bash
mvn clean test
mvn clean package
```

Prérequis : JDK 8, Maven 3.9.12 et résolution Maven de :

```text
com.oracle:ojdbc7:12.1.0.2
```

## Limites de la validation dans cet environnement

Le sandbox courant ne dispose pas de Maven et utilise Java 21. La compilation Maven Java 8 n'a donc pas été rejouée ici. Les sources et tests sont ceux du projet Gesparck retenu, dont l'archive d'origine fournit un rapport de 58 tests réussis sous JDK 8/Maven 3.9.12.

Aucun accès au réseau Oracle ou SSH CNRPS n'a été exécuté ici. La configuration fournie par le demandeur confirme toutefois :

```sql
create or replace directory TEST_DIR
  as '/oracle/traitement';
```

Cette définition est cohérente avec `scp.remote-directory=/oracle/traitement`.

## Vérifications restantes

- exécuter les commandes Maven avec Java 8 et le dépôt Maven contenant le pilote Oracle ;
- vérifier les droits Oracle `READ` sur `TEST_DIR` ;
- vérifier les droits OS de lecture de `/oracle/traitement` ;
- vérifier les droits SCP d'écriture ;
- configurer et vérifier `SCP_KNOWN_HOSTS` ;
- exécuter `docs/RECETTE_INTEGRATION.md` sur un environnement de recette ;
- confirmer les effets transactionnels et métier de la procédure réelle.
