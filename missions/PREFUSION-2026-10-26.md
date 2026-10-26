# MISSION CODEX EVOL-PF-01 — Qualification diagnostique sans auto-réparation non autorisée

Dépôt : bubth720/JARVIS-VOLUTION-
Chemin : missions/PREFUSION-2026-10-26.md
Branche de distribution : mission/prefusion-gates-20261026
Base Git vérifiée lors de la préparation : c0180181804338d21f3bf9d4b537b709595902f7

## Règles obligatoires
Codex lit d'abord AGENTS.md si présent, la CI au SHA exact, les PR ouvertes et le code réel. Créer sa propre branche de développement à partir du HEAD de cette branche mission; préserver contrats, historique, données et tests. 100 % local hors ligne au runtime, sobriété Android, minSdk stable. Aucun merge main, changement architecture majeur, installation utilisateur, activation sensible, suppression de preuve ni prise de permission implicite. Chaque phase est un commit testable et réversible.

## Phases

### 1. État

Lire README.md et docs/intensive-2026-10-22/REPORT-EVOL-03.md, vérifier commit/tests JVM. La sandbox bubblewrap OS était bloquée; ne pas l'annoncer disponible sur Android.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 2. Diagnostics

Auditer déduplication, files bornées, quotas, redaction, rétention et preuves expurgées sans contenu privé; tests crash/reprise et absence d'événements hors consentement. Les recommandations restent hypothèses, jamais faits matériels.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 3. Sandbox indépendante

Tester uniquement exécution de fixtures synthétiques dans un environnement hôte disponible; absence de namespaces/seccomp => BLOCKED sans aucun fallback non isolé. Aucun accès données CORE, réseau, clés, et aucune écriture dans les autres dépôts.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 4. Application des corrections

Préparer un contrat proposal/review/reject et rollback, sans action automatique: diff signé, vérification SHA parent, tests négatifs, décision humaine obligatoire et échec fermé. Aucun patch automatique installé, aucun merge.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

### 5. Qualification

Exécuter ./gradlew test :evolution-cli:installDist et scripts de démonstration documentés, séparer simulation et isolation OS réellement attestée; PR brouillon et manuel de désactivation.

Critère : code/diff ciblé, tests positifs et négatifs réellement exécutés, rapport SHA et rollback. Si besoin d'accord => livrer la proposition et s'arrêter.

## Livrables et blocages
PR brouillon sur base correspondante, changelog technique, fichiers concernés, matrice dépendances, commandes et résultats tests (succès/échecs/skips/non exécutés), artefacts et SHA du commit, risques, retour arrière, prochaine action. Une réussite JVM ne constitue pas une preuve physique ni un GO Android. Aucun merge dans main.
