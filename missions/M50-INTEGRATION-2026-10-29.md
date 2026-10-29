# EVOL-M50-01 — Diagnostic du JARVIS intégré en observation seulement

Dépôt : bubth720/JARVIS-VOLUTION-
Branche mission : mission/m50-integrated-beta-20261029
SHA base : 2e10d6f5c4d41458d0e55cd6724cd93973db450e

**DOCUMENT DE MISSION UNIQUEMENT — PAS DE CODE MODIFIÉ PAR CE FICHIER.** Objectif : préparer les modules optionnels pour JARVIS M50 tout en respectant indépendance et fonctionnement local. Codex crée une nouvelle branche de développement depuis la branche mission, tests et PR brouillon. Sans fusion main, activation sensible, installation utilisateur, migration ou changement de licence/ABI/minSdk sans validation humaine.

## Fichiers et contrats (vérifier présence)
- `README.md`
- `docs/intensive-2026-10-22/REPORT-EVOL-01.md`
- `docs/intensive-2026-10-22/REPORT-EVOL-02.md`
- `evolution-core`
- `evolution-cli`

## Phases et vérification

### 1 — A — Baseline

Relire les résultats JVM et démos EVOL-PF-01 lot2, incertitude du drain, absence sandbox OS; prévenir contre toute interprétation erronée de collecte automatique.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 2 — B — Contrat mesures expurgées

Définir schéma de compteurs limités CORE/LLM/VOICE/NEXUS/SERVICES sans payload, URI, conversation, nom, profil, exception, identifiant personnel ou chemin. Consommateur opt-in OFF par défaut; aucun accès direct à la base d'un autre APK.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 3 — C — Diagnostic démontré

Construire un jeu de 20 incidents synthétiques, démontrer alertes de panne et UNKNOWN sticky, déduplication, quotas et replay, export explicite vers nouveau fichier synthétique; pas d'auto-remédiation.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 4 — D — Sécurité et sandbox

Proposer sandbox locale OS réelle par hôte qualifié. Si Linux namespaces/seccomp/bubblewrap indisponibles ou Android incompatible, verdict BLOCKED; n'exécuter aucun correctif en repli non isolé.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

### 5 — E — Qualification

Exécuter ./gradlew test :evolution-cli:installDist, check_demo.py, check_diagnostic_demo.py, check_proposal_policy_demo.py si disponibles au HEAD exact. Rapport source/test/limites et rollback; module non bloquant pour l'APK M50.

Réception : commit unique/étape, tests positifs/négatifs, SHA exact + CI et statut PASS/BLOCKED/NOT_RUN, dépendances, rollback.

## Conditions communes M50
- Les modules indispensables de l'APK initiale sont CORE, UI et un service local de documents / CSV ; LLM natif devient indispensable pour dire « conversation IA réelle » et doit être qualifié séparément.
- VOICE et EVOLUTION ne doivent pas être des dépendances bloquantes : un échec d'audio ou de diagnostic n'affecte ni chat ni documents.
- Ne jamais présenter un simulateur, une compilation ou un refus correct comme une véritable intégration Android en production.
- Ne pas copier secrets ou modèles protégés dans le dépôt et n'utiliser aucune connexion réseau au runtime.

## Bilan requis
Module, mission, branche, commit 40 caractères, arbre, tests pass/fail/skip/not-run, ABI/API, CI URL, PR brouillon, blocages et prochaine action. Aucun merge main. Toute action sensible nécessite confirmation explicite.
