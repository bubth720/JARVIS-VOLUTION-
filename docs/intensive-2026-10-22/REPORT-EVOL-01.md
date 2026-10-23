# EVOL-01 — observateur technique local livré

- Module EVOLUTION, dépôt `bubth720/JARVIS-VOLUTION-`.
- Branche `feat/evol-01-telemetrie-locale-et-observate`.
- Base technique/main vérifiée `a67d3b6d39aa7d30ad38e9b1c9230b4e07180eb0` ; départ planning `36d09339aa93869c36c0981800918cde818637ac`. Le seul commit intermédiaire ajoute les fiches, sans runtime préexistant.

## Code et fonctionnement

Deux modules réels Kotlin/JVM17 : `evolution-core` (événements enum/nombres, expurgation avant file/stockage, SQLite versionné, rétention/déduplication, StateFlow, sondes injectables et polling hôte) et `evolution-cli` (démo100incidents persistés, consultation filtrée, export volontaire non écrasant, purge technique). Wrapper Gradle8.13 épinglé et CI JVM avec contrôle de tests effectivement exécutés.

Aucun timer/thread permanent n'est lancé. Collecte et scheduler inactifs par défaut. Pas de lecture de conversation, audio, autre application, PID externe ou capteur. Les textes, noms de fichiers, jetons et exceptions sont jetés, sans hachage. Les seules métriques permises sont latence, octets mémoire et taille de file, dans des bornes fixes. Les erreurs de sonde peuvent produire un incident technique SERVICE_UNAVAILABLE ; les erreurs SQLite restent UNKNOWN dans l'état du processus, sans rejeu automatique ni message privé.

Base dédiée : schéma interne1, transactions `synchronous=FULL`, pages/lignes/rétention bornées, schéma inconnu/corruption refusés sans reset, chemin sans symlink. La file volatile ne constitue pas une sauvegarde après mort de processus ; seuls les incidents commités sont annoncés persistés. Désactiver la collecte vide les éléments encore en file ; un drainage déjà commencé peut terminer son écriture acceptée auparavant. Il appartient au futur hôte de coordonner l'arrêt de son worker IO. Aucun adaptateur Android livré, SQLite JDBC qualifié ici uniquement sous Linux/JVM.

## Vérifications exécutées

Environnement : JDK17, Gradle8.13, Linux, véritables appels SQLite via sqlite-jdbc ; aucun LLM/JNI de génération.

1. `./gradlew test :evolution-cli:installDist --no-daemon --console=plain` : **14 tests,0échec,0erreur,0saut**. Le premier build avant ajout des tests indiquait NO-SOURCE ; il n'est pas compté comme validation. La seconde exécution a réellement exécuté la suite.
2. `python3 scripts/check_demo.py` : PASS,100incidents/100occurrences réellement persistés, relecture après fermeture,20incidents filtrés CORE, export JSON volontaire et cohérent, refus d'écrasement base/export, absence du texte synthétique privé, purge de rétention supprimant100incidents.
3. Tests : opt-in false, sondes non invoquées sans activation, expurgation avant persistance, NaN/valeurs invalides, saturation, arrêt, déduplication/relecture, rotation/rétention, filtres, corruption et version future préservées, base historique étrangère refusée intacte, commit inconnu sans rejeu, polling intervalle explicite, sondes injectées, limites et schéma.
4. `git diff --check` : PASS. Aucun fichier de données, modèle, secret ou archive personnelle suivi ; les bases de démonstration sont temporaires et synthétiques.

Les contrôles Android, intégrations CORE/LLM/VOICE et mesures physiques **ne sont pas exécutés ni revendiqués**. CI distante distincte des résultats locaux, à consulter après publication.

## Fichiers et limites

Créés : builds/wrapper, classes `events/` et `probes/`, CLI `Main.kt`,14tests, `scripts/check_demo.py`, workflow JVM, `.gitignore`, documentation et résultat machine. README initial minimal complété. Aucune autre application modifiée, aucune migration ou fusion main, aucun exécuteur de patch ni privilège système.

Prochaine mission : EVOL-02 sur cette livraison réellement compilée et testée. EVOL-03 exige également UNI-01 et une sandbox OS qualifiée avant toute exécution de code non fiable ; aucun succès de sandbox n'est impliqué par EVOL-01.
