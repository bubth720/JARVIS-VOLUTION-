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

## EVOL-02 — diagnostics sur preuves

```sh
./gradlew test :evolution-cli:installDist
python3 scripts/check_diagnostic_demo.py
# Nouveau répertoire synthétique, quatre cas contrastés et un contrôle négatif :
evolution-cli/build/install/evolution-cli/bin/evolution-cli diagnostic-demo /tmp/mon-nouveau-diagnostic
evolution-cli/build/install/evolution-cli/bin/evolution-cli cases /tmp/mon-nouveau-diagnostic/cases.db
# Évaluation explicite de données existantes, aucun traitement périodique :
evolution-cli/build/install/evolution-cli/bin/evolution-cli diagnose /tmp/mon-nouveau-diagnostic/events.db /tmp/mon-nouveau-diagnostic/cases.db 1000000
```

Six règles déterministes désactivables : signal de latence répété, mémoire élevée, fichier indisponible, permission refusée, libération UNKNOWN et service indisponible. Ce sont des **causes possibles**, pas des mesures natives ou réparations. Les budgets/protections CORE/LLM/VOICE ne sont jamais modifiés. Les occurrences dédupliquées n'ont pas de chronologie individuelle : si la première occurrence précède la fenêtre, la fréquence récente reste UNKNOWN. Une métrique obligatoire manquante est également UNKNOWN, même sur un échantillon ancien ; elle ne devient pas un résultat sain. La latence du snapshot est le dernier échantillon, pas une moyenne ni la preuve que chaque occurrence dépasse le seuil.

Les cas utilisent une base diagnostique séparée, bornée4MiB : au maximum128cas,32snapshots immuables par cas,4096preuves et4096décisions. Une capacité pleine provoque un refus transactionnel, jamais la suppression silencieuse de preuves. Chaque snapshot expurgé conserve l'ID de sa source et le SHA-256 du JSON canonique produit par `SnapshotJson`; il reste consultable lorsque la rétention technique purge l'incident original. Cela atteste une observation logicielle, pas l'état physique absolu d'Android.

`case-state <cases-db> <id> <OPEN|ACKNOWLEDGED|RESOLVED|REJECTED>` est une décision explicite de l'hôte/utilisateur. Aucun acquittement, réouverture ou résolution automatique n'est effectué par le moteur. Le journal conserve les transitions après reprise. Ni règle, CLI ni recommandation n'exécute de réparation.

Voir [rapport EVOL-02](docs/intensive-2026-10-22/REPORT-EVOL-02.md).

## EVOL-03 — tranche de politiques, mission bloquée

**PARTIAL / BLOCKED** : la sandbox OS n'est pas qualifiée dans cet environnement. L'essai unique de bubblewrap0.12.0, avec `/bin/true` et un filesystem minimal, a échoué : `bwrap: setting up uid map: Read-only file system` (exit1). Aucune nouvelle tentative inchangée ni contournement. Aucun code non fiable, patch proposé ou correctif JARVIS n'est exécuté.

```sh
./gradlew test :evolution-cli:installDist
python3 scripts/check_proposal_policy_demo.py
# Crée uniquement une fixture neuve, vérifie la politique et constate NOT_RUN :
evolution-cli/build/install/evolution-cli/bin/evolution-cli proposal-policy-demo /tmp/mon-nouveau-lab-politique
```

Code réellement disponible : validation stricte d'un diff textuel borné (fichiers Kotlin/Java explicitement autorisés sous `src/`, hunks de remplacement, mode100644), refus des chemins ambigus/symlinks/renommages/binaires, SHA-256, signatures Ed25519 liées à une origine épinglée par l'hôte, mission/base/référence UNIFIED/expiration, file de revue durable contenant seulement des métadonnées hachées. Les clefs privées de démo sont éphémères en RAM, jamais exportées. Une signature d'origine n'est pas une autorisation utilisateur. Une approbation de laboratoire n'est pas un droit d'exécution.

`RestrictedTrialRunner` refuse **toujours** avec `NOT_RUN/SANDBOX_NOT_QUALIFIED`, sans option de déblocage, appel système, Git apply ou lancement de processus. Aucun checkout/snapshot privé n'est créé/traité. Le contrôle des fichiers ne visite que les chemins déclarés d'une fixture explicitement marquée synthétique, en lecture seule.

Cette tranche ne qualifie ni la correspondance d'un diff avec un checkout Git réel, ni les commandes de build, quotas CPU/RAM/stockage ou restaurations de sandbox. La base du patch de démo est fictive et annoncée telle quelle. L'intégrité par chaîne de hachage détecte une corruption du journal, mais n'authentifie pas à elle seule un humain ou un attaquant capable de réécrire les métadonnées. Le futur hôte de confiance doit afficher le contenu exact et appliquer les permissions CORE ; aucune liaison à CORE n'est présente.

EVOL-04 reste **non éligible** tant qu'EVOL-03 ne dispose pas d'un véritable essai isolé qualifié. Voir [rapport EVOL-03](docs/intensive-2026-10-22/REPORT-EVOL-03.md).
