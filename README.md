# interopPlatformeEchangeCI — version simplifiée

API Spring Boot 2.7.18 / Java 8 pour recevoir une déclaration EPIC, contrôler son existence dans `EPIC_FM`, transférer le fichier par SCP et appeler la procédure Oracle `traitement_fichier_epic`.

Cette version est dérivée du projet Gesparck. Elle conserve les contrôles fonctionnels et de sécurité utiles au prompt, mais simplifie le build Maven : il n'y a plus de profil Maven supplémentaire ni d'enforcer imposant une version exacte de Maven. Le pilote Oracle est déclaré directement dans le POM.

## Flux métier

```text
Réception multipart
  → validation des champs et du nom de fichier
  → SELECT EPIC_FM
      ├─ clé existante : HTTP 409, aucun SCP, aucune procédure
      └─ clé absente : SCP vers /oracle/traitement
           ├─ échec : HTTP 502, procédure non appelée
           └─ succès : traitement_fichier_epic
                ├─ retour normal : HTTP 200
                └─ erreur : HTTP 500, fichier distant conservé
```

Le service orchestre le flux. Le repository contient uniquement les accès Oracle via `JdbcTemplate`. `FileTransferUtility` isole SSH/SCP du métier.

## Prérequis

- JDK 8 ;
- Maven 3.9.12 ;
- Oracle Database 12c ;
- pilote `com.oracle:ojdbc7:12.1.0.2` disponible dans le dépôt Maven local/interne ;
- serveur Linux accessible en SCP ;
- accès à `EPIC_FM` et `traitement_fichier_epic`.

Le projet contient les dépendances Spring Data JPA et Spring JDBC comme demandé. Le flux métier utilise explicitement Spring JDBC ; aucune entité JPA ni création de table n'est nécessaire.

## Configuration

Les secrets doivent être injectés par l'environnement d'exécution, jamais versionnés :

```bash
export ORACLE_DB_PASSWORD='mot-de-passe-oracle'
export SCP_HOST='192.168.3.13'
export SCP_PASSWORD='mot-de-passe-scp'
export SCP_KNOWN_HOSTS='/chemin/securise/known_hosts'
```

Les propriétés principales sont dans `src/main/resources/application.properties` :

```properties
spring.datasource.url=${ORACLE_DB_URL:jdbc:oracle:thin:@192.168.3.13:1521:dbcnr}
spring.datasource.username=${ORACLE_DB_USERNAME:affnouv}
spring.datasource.password=${ORACLE_DB_PASSWORD}
scp.host=${SCP_HOST}
scp.port=${SCP_PORT:22}
scp.username=${SCP_USERNAME:oracle}
scp.password=${SCP_PASSWORD}
scp.remote-directory=${SCP_REMOTE_DIRECTORY:/oracle/traitement}
scp.known-hosts=${SCP_KNOWN_HOSTS}
```

Le fichier `.env.example` liste toutes les variables. Spring Boot ne charge pas automatiquement un fichier `.env`.

### Oracle DIRECTORY

La procédure utilise :

```plsql
UTL_FILE.FOPEN('TEST_DIR', xnomFichier, 'r', '3000')
```

La configuration Oracle fournie confirme :

```sql
create or replace directory TEST_DIR
  as '/oracle/traitement';
```

La correspondance entre le dépôt SCP et le répertoire Oracle est donc cohérente. En recette, confirmer les droits Oracle `READ` sur `TEST_DIR` et les droits OS de lecture.

## Compilation et exécution

Depuis le répertoire contenant `pom.xml` :

```bash
mvn clean test
mvn clean package
java -jar target/interopPlatformeEchangeCI-1.0.0.jar
```

Le POM utilise directement `com.oracle:ojdbc7:12.1.0.2`. Si le dépôt Maven local n'est pas le dépôt par défaut, utiliser le `settings.xml` de l'entreprise ou installer le pilote dans le dépôt local autorisé.

Le JAR de production exclut DevTools et Lombok.

## API REST

```text
POST /api/epic-ci/chargerFichierEpic_CI
Content-Type: multipart/form-data
```

Champs obligatoires :

| Champ | Type | Règle |
|---|---|---|
| `fichier` | MultipartFile | non vide, nom `.txt` sûr |
| `periode` | Integer | 0 à 99 ; aucune limite artificielle à 12 |
| `annee` | Integer | quatre chiffres |
| `typeSalaire` | Integer | 0 à 99 |
| `codeEtab` | Integer | 0 à 9999 |
| `matriculeUser` | Long | 0 à 9999999999 |
| `direction` | Integer | 0 à 9999 |

Exemple :

```bash
curl -X POST http://localhost:8080/api/epic-ci/chargerFichierEpic_CI \
  -H 'X-Correlation-ID: epic-recette-001' \
  -F 'fichier=@fichier_EPIC.txt' \
  -F 'periode=9' \
  -F 'annee=2026' \
  -F 'typeSalaire=1' \
  -F 'codeEtab=3012' \
  -F 'matriculeUser=12345' \
  -F 'direction=5600'
```

Réponses principales :

- `200 FILE_LOADED` : SCP terminé et procédure revenue sans exception ;
- `400` : données, fichier ou nom invalides ;
- `409 FILE_ALREADY_EXISTS` : clé déjà présente ou conflit concurrent confirmé ;
- `413 FILE_TOO_LARGE` : limite multipart dépassée ;
- `415 UNSUPPORTED_MEDIA_TYPE` : requête non multipart ;
- `500` : erreur Oracle ;
- `502 SCP_TRANSFER_FAILED` : SCP échoué, procédure non appelée.

Swagger UI : `http://localhost:8080/swagger-ui.html`  
OpenAPI JSON : `http://localhost:8080/v3/api-docs`

## Procédure Oracle

L'appel est réalisé avec `JdbcTemplate` et un `CallableStatement` :

```text
{call traitement_fichier_epic(?, ?, ?, ?, ?, ?, ?, ?, ?)}
```

Correspondances :

| Paramètre Oracle | Valeur |
|---|---|
| `xnomFichier` | nom distant effectivement transféré |
| `xmatricule` | `codeEtab` |
| `xperiode` | `periode` |
| `xannee` | `annee` |
| `xcodeTypeSalaire` | `typeSalaire` |
| `xuser` | `matriculeUser` |
| `xtypeOperation` | `C` |
| `xdateOperation` | date formatée transmise à la procédure |
| `xcdirect` | `direction` |

La procédure fournie utilise actuellement `SYSDATE` et non `xdateOperation`. La valeur est tout de même transmise pour respecter la signature et permettre une évolution ultérieure.

Le fichier EPIC est un fichier à positions fixes. La procédure lit notamment des champs jusqu'à la position 2055 ; les essais d'intégration doivent utiliser un fichier EPIC réel et valide.

## Sécurité et concurrence

- Le nom de fichier entrant est refusé s'il contient un chemin, `..`, des caractères de contrôle, des espaces ou des caractères shell ; seuls les noms `.txt` ASCII sûrs sont acceptés.
- Le nom distant est généré avec un UUID et ne provient pas directement du client.
- La vérification `known_hosts` est obligatoire ; aucune acceptation aveugle de clé n'est utilisée.
- Les fichiers temporaires locaux sont supprimés et les permissions sont restrictives.
- Le fichier distant n'est jamais supprimé automatiquement après une erreur Oracle.
- Le `SELECT` préalable ne constitue pas un verrou. La contrainte Oracle reste l'arbitre en cas de concurrence.
- Une violation unique est convertie en 409 uniquement après vérification que la clé EPIC existe réellement.

L'API doit être placée derrière le frontal authentifié de l'entreprise. `matriculeUser` et `direction` sont des métadonnées reçues ; ils ne constituent pas une authentification.

## Tests et recette

Les tests unitaires utilisent Mockito et ne nécessitent ni Oracle ni serveur SSH réel. Ils couvrent la validation, le doublon, l'ordre SELECT → SCP → procédure, la période 13, l'échec SCP, l'échec Oracle, la concurrence, les erreurs Oracle, le nommage sûr, les permissions SCP et OpenAPI.

Exécuter :

```bash
mvn clean test
```

La recette réelle est décrite dans `docs/RECETTE_INTEGRATION.md`. Elle doit confirmer les droits Oracle/OS, la clé SSH, le fichier distant, les effets métier de la procédure, les transactions et les cas de reprise.

## Structure

```text
src/main/java/com/cnrps/InteropPlatformeEchangeCI/
  config/       Configuration datasource, JDBC, OpenAPI, SCP
  controller/   Endpoint REST et gestion des erreurs
  models/       Requêtes, réponses et exceptions métier
  services/     Orchestration et repository Oracle
  utilities/    Validation nom, corrélation et transfert SCP
src/test/java/  Tests unitaires et web
```
"# interopPlatformeEchangeCIMANUS" 
