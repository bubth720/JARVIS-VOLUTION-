# EVOL-02 — diagnostics et recommandations traçables

Module EVOLUTION ; dépôt `bubth720/JARVIS-VOLUTION-` ; branche `feat/evol-02-moteur-de-diagnostic-et-recomm`.
Départ : EVOL-01 réellement publiée `9cc6288b537d71743fbb634a8d098ce304441464`. Aucun merge/main ni modification d'une autre application.

## Fonctionnalités programmées

- `RuleEngine` / `RuleRepository` : six règles versionnées/désactivables, fenêtre bornée, données insuffisantes et dates futures explicites.
- `IncidentCorrelator`, `EvidenceLink`, `DiagnosticCase` : regroupement par propriétaire/règle et empreinte des snapshots expurgés.
- `DiagnosticCaseRepository` : vrais cas SQLite, snapshots de preuve immuables, refus des références inventées, acquittement/rejet/résolution/réouverture humains et journal append-only. Répéter l'analyse ne duplique pas les cas et conserve le statut humain.
- `DiagnosticReportRenderer` / `RecommendationCatalog` : JSON de causes possibles et recommandations bornées. Une libération UNKNOWN recommande de conserver le blocage, jamais de réinitialiser ou prétendre le modèle libéré.
- CLI : `diagnostic-demo`, `diagnose`, `cases [MODULE]`, `case-state`. CI étendue à la démonstration réelle.

Les bases sont propres à EVOLUTION, aucun accès aux SQLite historiques CORE ou NEXUS. Aucune seconde IA, aucun IPC, transport, patch appliqué, commande shell de remédiation ou donnée personnelle. Les seuils diagnostiques ne changent aucun budget matériel.

## Démonstrations et résultats réellement obtenus

JDK17/Gradle8.13, Linux/JVM et vrai SQLite :

- `./gradlew test :evolution-cli:installDist --no-daemon --console=plain` : **25tests,0échec,0erreur,0saut**, dont14régressions EVOL-01 et11nouveaux tests diagnostics.
- `python3 scripts/check_demo.py` : ancien scénario100incidents, relecture/export/rétention PASS.
- `python3 scripts/check_diagnostic_demo.py` : **quatre cas persistés** (LLM latence, CORE mémoire, VOICE libération inconnue, SERVICES permission), contrôle négatif INTERFACE, IDs source existants, SHA-256 des snapshots vérifiés par un second langage, acquittement après réouverture, répétition sans doublons ni perte de statut, cinq décisions persistées, refus d'écrasement du laboratoire : PASS.
- `git diff --check` : PASS.

Échec initial conservé dans le bilan :24/25tests passaient ; une mesure obligatoire absente était ignorée si l'échantillon était ancien. `RuleEngine` valide désormais la présence des mesures avant l'exclusion temporelle et produit UNKNOWN au lieu d'une fausse observation négative. **Aucune assertion, seuil ou protection n'a été diminué**, aucun test désactivé. La suite finale entière a ensuite été réexécutée.

## Limites, risques et prochaine étape

Les preuves sont des événements logiciels avec snapshots hachés, pas une preuve de fin JNI ou de libération physique. Les occurrences historiques dédupliquées ne permettent pas de reconstituer chaque timestamp : fenêtre incomplète = UNKNOWN. Le dernier échantillon de latence ne prouve pas que toutes les occurrences étaient lentes. Les cas restent POSSIBLE_CAUSE, jamais une certitude causale ni une réparation appliquée.

Base diagnostique4MiB,128cas,32snapshots/cas,4096preuves/décisions ; dépassement refusé sans effacer le journal. Les snapshots persistent les seules métriques expurgées déjà autorisées, pas les messages privés. Aucun essai Android, appareil physique ou intégration intermodule effectué. La CI distante doit être distinguée de ces résultats locaux.

Suite possible : EVOL-03 dépend d'EVOL-02 et UNI-01 ; seule une sandbox OS effectivement qualifiée autorise l'exécution de code non fiable. Sans cette preuve, livrer les politiques et propositions inertes, conserver le runner bloqué et ne pas annoncer EVOL-03 qualifiée. EVOL-04 ne sera pas lancée comme dépendance satisfaite sur la seule existence de ces politiques.
