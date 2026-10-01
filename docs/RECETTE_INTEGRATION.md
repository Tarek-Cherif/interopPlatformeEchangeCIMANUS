# Recette Oracle 12c / Linux SCP — à exécuter dans votre environnement

Aucune des opérations ci-dessous n'a été exécutée sur le système CNRPS. Utiliser un environnement de recette, des fichiers EPIC de test autorisés et des clés métier réservées. Ne pas modifier/supprimer des déclarations de production pour ces essais.

## Prérequis à valider avec le DBA et l'administrateur

| Contrôle | Validation attendue |
|---|---|
| Java/Maven | JDK 8, Maven 3.9.12 sur le poste/CI |
| Pilote | ojdbc7 de la distribution Oracle 12.1.0.2, installé au dépôt local/interne |
| SID ou service | URL JDBC confirmée pour dbcnr |
| Schéma/droits | SELECT EPIC_FM, EXECUTE traitement_fichier_epic, signature 9 IN confirmée |
| PK_EPIC_FM | colonnes MATRICULE, CODE_TYPE_SALAIRE, ANNEE, PERIODE et contrainte activée |
| Date | conversion VARCHAR2 explicite au masque validé, indépendante de NLS |
| Procédure | ne masque pas les erreurs attendues; règles COMMIT/ROLLBACK documentées |
| Répertoire Oracle | correspondance entre /oracle/traitement et accès de la procédure existante |
| SSH | hôte/port joignables, mot de passe injecté, empreinte vérifiée et known_hosts approuvé |
| SCP | binaire présent, permissions de dépôt/lecture appropriées, format de nom UUID accepté |
| API | frontal authentifié, établissement autorisé, valeurs utilisateur/direction fiables |
| Volumétrie | limites Servlet/proxy, espace temporaire et distant, timeout clients/proxy alignés |

## Scénarios

1. **Succès :** choisir une clé absente et un vrai fichier EPIC valide. Appeler le curl du README. Attendre 200. Vérifier le fichier distant, son nom identique à fileName/xnomFichier, ses permissions 0600, les données EPIC_FM et les autres effets métier de la procédure. Ne pas se limiter au code HTTP.
2. **Doublon :** réenvoyer la même clé après chargement. Attendre 409 avec details. Vérifier qu'aucun nouveau fichier n'a été déposé ni nouvel appel de procédure lancé, à l'aide des logs/observations autorisées.
3. **Période spécifique :** tester une période supérieure à 12 reconnue par les règles métier existantes. L'API doit accepter son domaine technique 0–99 ; une éventuelle erreur métier de la procédure doit être distinguée d'une erreur de binding.
4. **Invalides :** fichier absent/vide, codeEtab 10000 ou 00001, année mal formée, matriculeUser de 11 chiffres, nom ../paie.txt. Attendre 400, aucune requête métier Oracle ni transfert.
5. **SCP inaccessible / clé refusée :** dans une configuration de recette, rendre l'accès SSH indisponible ou utiliser un known_hosts volontairement non correspondant. Attendre 502 et aucun appel procédure. Ne jamais désactiver la vérification de clé pour contourner le test.
6. **Échec Oracle après transfert :** provoquer un cas d'erreur prévu par le DBA et non destructif. Attendre 500; fileName conservé dans la réponse, fichier distant conservé. Vérifier les transactions et tout effet partiel avant reprise.
7. **Concurrence réelle :** soumettre deux appels identiques parallèles avec une clé neuve réservée. Confirmer les effets de PK_EPIC_FM et de la procédure. Les deux transferts peuvent avoir lieu ; les noms doivent différer. Si ORA-00001 est propagée et la clé présente à la revérification, un appel retourne 409. Si la procédure absorbe l'erreur, ce scénario nécessite une évolution du contrat Oracle.
8. **Unicité autre table :** selon les possibilités de recette DBA, vérifier qu'une violation unique sans ligne EPIC correspondante n'est pas faussement qualifiée FILE_ALREADY_EXISTS.
9. **Format date/NLS :** confirmer le fuseau métier et le format exact reçu et utilisé. Changer le NLS de session dans un test DBA approprié ; la conversion explicite doit rester correcte.
10. **Taille/proxy :** envoyer un fichier trop volumineux via un vrai serveur HTTP. Attendre 413 au niveau application ou proxy selon la configuration; vérifier que rien n'a été traité.
11. **Documentation :** vérifier Swagger UI, multipart binaire et tous les champs; l'accès doit respecter la politique d'exploitation.
12. **Reprise/retention :** retrouver un fichier via correlationId, vérifier la base avant reprise et valider la procédure manuelle de rétention/purge.

Consigner pour chaque essai l'identifiant de corrélation, clé métier, nom distant, statut HTTP, observations Oracle/SCP et conclusion. La conformité des mocks ne remplace pas cette recette.
