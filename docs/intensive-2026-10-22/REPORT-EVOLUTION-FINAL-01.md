# EVOLUTION-FINAL-01 — livraison des composants non sensibles

Base : `c0180181804338d21f3bf9d4b537b709595902f7`, branche de travail `feat/evolution-final-01`.
La mission fournie dans la conversation sert de spécification : aucun document EVOLUTION-FINAL-01 distinct n'a été récupéré.
Les rapports EVOL-01/02/03 et le code publié ont été lus et réutilisés. Aucun changement à main.

## Fonctionnalités réellement programmées

- Diagnostic hôte opt-in `DiagnosticMonitor` : polling borné, sans thread permanent ; conservation des décisions humaines, UNKNOWN en cas d'indisponibilité, INSUFFICIENT_DATA si la fenêtre est tronquée. Les règles v2 ajoutent crash/interruption et file saturée ; dates incohérentes et métriques invalides deviennent UNKNOWN.
- Mesures : 5..256 latences indépendantes fournies par l'hôte, copies immuables, empreintes de contexte et provenance déclarée, médiane et p95 reproductibles.
- Comparateur : contexte et nombre d'échantillons identiques requis ; seuils descriptifs 10 % ET 5 ms ; une régression médiane/p95 prévaut. Les occurrences d'incidents dédupliqués ne sont pas transformées en échantillons.
- Historique SQLite dédié, versionné, transactions FULL, 128 paires / 4 MiB ; identités idempotentes, conflits et capacité refusés, réouverture avec contrôle des snapshots/digests. Une corruption est refusée avant toute écriture, sans reset ni effacement.
- Recommandations argumentées à partir des mesures expurgées et SHA-256 des preuves ; aucun statut humain changé automatiquement.
- Tableau local HTML hors ligne : diagnostics, performances, historique, recommandations et revues ; contenu échappé, CSP sans script, aucune ressource distante, export neuf obligatoire.
- CLI : `compare`, `history`, `supervise`, `review-list`, `review-decide`, `evolution-demo`.
- Préparation de la boucle expérimentale : `TrialPreparation.inspect` relit la revue persistée et vérifie la correspondance avec la proposition déjà vérifiée par `ProposalVerifier`. L'identité, le digest, l'origine, la base, UNIFIED et l'expiration doivent correspondre. Rejet/révocation/expiration ne donnent aucun accès à un essai.
- Validateur de diff, signatures Ed25519, journal de revue et runner EVOL-03 réutilisés. Le runner reste inchangé et bloqué même après approbation pour le laboratoire.

## Fichiers ajoutés ou modifiés

- `.github/workflows/jvm.yml`, `README.md`
- `evolution-cli/src/main/kotlin/fr/jarvis/evolution/cli/{Main,EvolutionCommands}.kt`
- `evolution-core/src/main/kotlin/fr/jarvis/evolution/diagnostic/{RuleEngine,DiagnosticCaseRepository,DiagnosticReportRenderer,DiagnosticMonitor}.kt`
- `evolution-core/src/main/kotlin/fr/jarvis/evolution/performance/{Performance,ExperimentRepository}.kt`
- `evolution-core/src/main/kotlin/fr/jarvis/evolution/supervision/Supervision.kt`
- `evolution-core/src/test/kotlin/fr/jarvis/evolution/{DiagnosticTest,DiagnosticMonitorTest,PerformanceTest,SupervisionTest}.kt`
- `evolution-sandbox/src/main/kotlin/fr/jarvis/evolution/sandbox/TrialPreparation.kt`
- `evolution-sandbox/src/test/kotlin/fr/jarvis/evolution/sandbox/TrialPreparationTest.kt`
- `scripts/check_evolution_demo.py`
- Ce rapport et `VALIDATION-EVOLUTION-FINAL-01.json`.

## Validation

Code validé : `68e095660975cca9004d474d35ad3daad3eebd80`.
CI : https://github.com/bubth720/JARVIS-VOLUTION-/actions/runs/40415562523 — SUCCESS.

- `./gradlew test :evolution-cli:installDist --no-daemon` : compilation et installation CLI réussies ; **51 tests, 0 échec, 0 erreur, 0 saut** (36 core, 15 sandbox ; 38 tests antérieurs conservés, 13 nouveaux).
- `python3 scripts/check_demo.py` : PASS — 100 incidents SQLite persistés, relecture, filtrage, export, rétention.
- `python3 scripts/check_diagnostic_demo.py` : PASS — quatre cas, preuves/digests, contrôle négatif, décision humaine conservée et idempotence.
- `python3 scripts/check_proposal_policy_demo.py` : PASS — validation/signature/revue, runner NOT_RUN, sandbox non qualifiée.
- `python3 scripts/check_evolution_demo.py` : PASS — comparaisons synthétiques avant/après réellement calculées et persistées, diagnostics, HTML local, relecture de revue, refus d'écrasement, NaN/infini, nombre d'échantillons invalide et symlink.
- `git diff --check` : PASS dans la CI. La vérification JUnit exige au moins 51 tests et aucun échec/erreur/saut.

Les 13 nouveaux tests couvrent aussi les statistiques exactes, la priorité aux régressions p95, les contextes incompatibles, l'idempotence/conflit/capacité, les octets corrompus préservés, l'opt-in, les erreurs de source, l'échappement HTML et le refus d'exécution après approbation/révocation.

Les tests de comparaison et les scénarios CLI utilisent des données explicitement synthétiques.
SQLite, la persistance, les signatures, le refus du runner et les calculs sont réellement exécutés.
Aucun benchmark d'un correctif, aucune mesure sur un appareil JARVIS, aucun gain de production ni qualification OS n'est revendiqué.

Deux échecs intermédiaires sont conservés dans l'historique CI :
1. Historique corrompu refusé après une écriture de métadonnées SQLite : corrigé en validant les snapshots avant toute écriture. Le test exige toujours la préservation exacte des octets.
2. Deux fixtures de test non adaptées : UUID manquant et attente de toutes les valeurs d'une enum nouvellement étendue. Les fixtures ont été corrigées ; les trois anciens motifs UNKNOWN restent exigés et le nouveau motif invalide possède son propre test.

L'environnement local n'a pas pu télécharger Gradle (proxy indisponible puis environnement indisponible).
La validation finale est distante sur GitHub Actions avec JDK17 et Gradle8.13, pas une exécution locale annoncée à tort.

## Fonctions encore bloquées

`SANDBOX_NOT_QUALIFIED` reste le verrou inconditionnel. Aucune tentative de contournement, élévation, application ou exécution de patch.
Aucun checkout de code candidat, build de correctif non fiable, rollback d'essai, déploiement ou merge.
`mayExecute=false` et `productionAuthorized=false`, même si une proposition est approuvée pour le laboratoire.

La qualification OS n'a pas été retentée dans cette mission. Le constat EVOL-03 reste une absence de qualification, pas une preuve nouvelle de l'état actuel de l'OS.
La mission complète d'amélioration expérimentale reste PARTIAL / LAB_BLOCKED ; ses composants indépendants sont livrés.

## Prochaines améliorations possibles

1. Qualifier réellement namespaces, filesystem, réseau, processus et budgets CPU/RAM/temps/stockage sur un hôte de laboratoire.
2. Développer un runner à commandes fixes, checkout éphémère, preuves avant/après et rollback démontré, puis revue distincte avant activation.
3. Intégrer les sondes aux vrais modules JARVIS via un hôte de confiance et produire des mesures répétées sur appareil, sans données privées.
4. Ajouter des intervalles d'incertitude et une stratégie de répétition ; le comparateur actuel reste descriptif.
5. Renforcer la liaison cryptographique entre les mesures attestées par l'hôte, la proposition et la qualification OS ; les digests actuels ne sont pas une authentification.

L'approbation humaine du laboratoire ne vaut jamais autorisation de production.
