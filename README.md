# JARVIS EVOLUTION — observation technique locale

Premier runtime **Kotlin/JVM17**, sans LLM, réseau, IPC ou exécution de correctifs. Le nom GitHub `JARVIS-VOLUTION-` est conservé. La collecte et le polling sont désactivés par défaut : seul un hôte de confiance peut les activer. Aucun adaptateur Android ni raccordement à une autre application n'est livré.

```sh
./gradlew test :evolution-cli:installDist
python3 scripts/check_demo.py
# Base technique neuve explicitement choisie ; aucune base existante n'est écrasée :
evolution-cli/build/install/evolution-cli/bin/evolution-cli demo /tmp/mon-nouveau-lab/events.db
evolution-cli/build/install/evolution-cli/bin/evolution-cli list /tmp/mon-nouveau-lab/events.db CORE
evolution-cli/build/install/evolution-cli/bin/evolution-cli export /tmp/mon-nouveau-lab/events.db /tmp/mon-nouveau-lab/rapport-neuf.json
```

La démo émet et persiste réellement100incidents synthétiques dans SQLite puis peut les relire. Elle ne mesure pas la santé réelle de CORE/LLM/VOICE. Le port standard refuse tout texte libre en stockage : seules des enums fermées et quatre métriques numériques bornées survivent à l'expurgation avant mise en file. Ni messages, noms, exceptions, chemins ni identifiants fournis par un appelant ne sont conservés ou hachés. Les sondes fournies observent uniquement le processus JVM courant ou des valeurs injectées explicitement.

`evolution-core` : EventSink, Sanitizer, SQLite roulant, StateFlow, sondes et scheduler **piloté par l'hôte**, sans thread/timer automatique. `evolution-cli` : démo, lecture filtrée, export explicite vers un nouveau fichier, purge de la rétention technique. API/schéma interne1 ; aucun contrat UNIFIED n'est modifié.

File64éléments par défaut (max256), saturation signalée sans blocage ; drainage synchrone à appeler sur un worker IO. Base4MiB/2048lignes/7jours par défaut, plafonds configurables stricts. Déduplication par module/code/sévérité, compteur et dates conservés ; aucune nouvelle entrée personnelle. SQLite `synchronous=FULL`, transactions et limites de pages ; le journal transactionnel temporaire peut porter la consommation totale jusqu'à environ deux fois la limite de base. Pas de WAL ni de reset d'un schéma inconnu. Une erreur de persistance reste UNKNOWN, sans rejeu automatique ni fausse confirmation. Le compteur d'incertitude et la file sont propres au processus, non une preuve durable après une coupure. Les incidents déjà commités sont durables ; une file RAM interrompue n'est pas annoncée persistée.

L'export est expurgé de contenu et identifiants personnels ; aucune garantie statistique universelle d'anonymat n'est revendiquée pour des métriques horodatées. Un hôte reste responsable de fournir uniquement des mesures techniques légitimes dans les champs numériques autorisés. Ne jamais lui transmettre de conversations ou lire le stockage d'une autre application.

Dépendances épinglées : Kotlin2.1.20 et coroutines1.10.2 (Apache2), sqlite-jdbc3.46.1.0 (Apache2, SQLite domaine public), JUnit4.13.2 pour tests (EPL1). Gradle8.13 avec SHA-256 de distribution vérifié par le wrapper. Le téléchargement d'outils/dépendances est requis au premier build ; le runtime et ses tests de démonstration fonctionnent ensuite hors ligne avec le cache. Aucun poids de modèle n'est téléchargé.

Voir [rapport EVOL-01](docs/intensive-2026-10-22/REPORT-EVOL-01.md). Pas de fusion main, installation ou auto-application de réparation.
