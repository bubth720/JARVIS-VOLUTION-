# EVOL-03 — Atelier de correctifs isolés et simulation de régressions sur mini-PC

## 1. Identité
**Module** EVOLUTION · **Dépôt** `bubth720/JARVIS-VOLUTION-` · **Mission** EVOL-03 · **Priorité** P2.
**Référence GitHub vérifiée** : `main` au commit `a67d3b6d39aa7d30ad38e9b1c9230b4e07180eb0`, uniquement README au point de départ.
**Branche documentaire** `plan/intensive-2026-10-22` ; **branche code proposée** `feat/evol-03-atelier-de-correctifs-isoles-e`. **Dépendances** EVOL-02,UNI-01.
**Statut** PRÊTE POUR ATTRIBUTION, NON AUTORISÉE À EXÉCUTION.

## 2. État initial et distinction entre fait et cible
Le dépôt `JARVIS-VOLUTION-` est initialement dépourvu de runtime ; toutes les arborescences `evolution-*` ci-dessous sont des **propositions à créer**. Les protections CORE et les prototypes isolés discutés ailleurs ne constituent pas du code EVOLUTION déjà livré. Règle centrale : aucun patch ne peut s'appliquer à JARVIS sans approbation explicite.

## 3. Objectif d'implémentation
Construire une vraie chaîne locale de propositions de patch et vérification dans des répertoires Git isolés, pour futur matériel adapté ; sur Android aucune écriture à l'application installée.

## 4. Fonctions exactes à programmer
1. Définir PatchProposal signée par son origine, digest diff et chemins autorisés ; toute proposition est inerte sans approbation.
2. Construire GitWorkspaceManager créant un checkout éphémère non privilégié sur mini-PC dédié ; jamais copier secrets, documents personnels ni clés.
3. Contrôler chemins, taille du diff, fichiers bannis, non-suivi de liens et permissions des commandes ; refus d'un patch hors périmètre.
4. Appliquer le patch dans la copie temporaire seulement, compiler/tester par commandes en allowlist et limites temps/RAM/CPU.
5. Capturer logs filtrés, codes de retour et manifestes SHA, générer PatchTrial avec comparaison aux mesures de référence.
6. Ajouter une action d'approbation humaine qui **émet une proposition d'application** sans fusionner ni muter main ; rollback seulement dans le sandbox.

## 5. Architecture et classes
1. PatchProposal/ApprovalRecord, DiffPolicyValidator
2. GitWorkspaceManager et WorkspaceLease
3. RestrictedTrialRunner, ResourceLimits et TrialArtifacts
4. PatchTrialComparator et HumanReviewQueue
Séparer logique de domaine, stockage, CLI et adaptateurs. Injecter horloges/stockage pour tests reproductibles, mais exercer les vrais composants persistance et traitement dans la démo.

## 6. Fichiers à créer/modifier
1. À créer : evolution-sandbox/src/main/kotlin/.../{PatchProposal,GitWorkspaceManager,RestrictedTrialRunner}.kt
2. À créer : evolution-core/src/main/kotlin/.../review/{ApprovalRecord,HumanReviewQueue}.kt
3. À créer : evolution-sandbox/src/test/.../SandboxBoundaryTest.kt ; outils CLI de démo sur dépôt jetable
Lire `README.md` et établir un plan Gradle réel avant écriture. Ne pas modifier un autre dépôt pour contourner une dépendance.

## 7. Implémentation séquencée
1. Construire un petit dépôt Git jetable synthétique et une politique refusant chemins absolus et traversées.
2. Développer création/fermeture isolées, garde de commandes autorisées et journal d'essais.
3. Ajouter application de patch en sandbox et collecte d'une vraie compilation/test d'exemple sur mini-projet.
4. Créer comparateur métrique déterministe et revue humaine à état durable.
5. Tester patch permis, patch interdit, échec build, timeout et nettoyage ; conserver preuve que main est inchangé.

## 8. Technologies, licences et dépendances
JVM Kotlin, processus Git local avec arguments fixes, sandbox OS impérative avant exécuter code non fiable ; pas d'installation Android.
Ajouter peu de dépendances ; déclarer licences, mode hors ligne, limites et usage de ressources. Les extensions pour matériel haute performance restent optionnelles et refusées sur Android non qualifié.

## 9. Intégrations futures et contrats
UNIFIED fournit identifiants de missions/digests ; future IA peut proposer des patches mais jamais les approuver. Ne pas activer exécution de code non fiable hors sandbox OS.
Ports produits par cette mission = internes ou opt-in ; aucune modification automatique des contrats UNIFIED ni activation d'une liaison inter-APK.

## 10. Contraintes impératives
- Collecte locale uniquement ; données personnelles, contenus des conversations, audio, poids, clés et jetons interdits dans les logs.
- Pas d'auto-installation, écriture dans main, auto-merge, privilèges OS/root ou exécution de code non fiable hors sandbox autorisée.
- Batterie, RAM et thermique Android prioritaires ; planifier opérations lourdes uniquement sur mini-PC approprié.
- Désactivation par défaut des tâches cycliques et opération de correction ; toute action sensible nécessite une nouvelle autorisation.

## 11. Livrables code
Modules compilables réels, API versionnée au sein du dépôt, démonstration CLI qui exerce le comportement promis, données synthétiques traçables, tests élémentaires et rapport bref listant commits/fichiers. Une classe vide ou des simulations non raccordées ne comptent pas.

## 12. Acceptation minimum mesurable
1. Un patch innocent sur dépôt Git jetable est compilé et comparé avec journal exact.
2. Tentative de sortie de sandbox ou commande non autorisée est refusée avant exécution.
3. Aucune capacité de fusion ni d'écriture sur repos de production exposée par l'API.
Compiler et rejouer la démo ; documenter précisément les fonctionnalités non encore branchées aux autres modules.

## 13. Dépendances et parallélisme
EVOL-02,UNI-01. Les missions EVOLUTION doivent suivre ces gates, mais peuvent progresser indépendamment des missions NEXUS, VOICE, INTERFACE, LLM tant qu'aucun contrat partagé n'est muté.

## 14. Git, approbations et propriété
Ne jamais fusionner dans `main`. Branche code distincte de `plan/intensive-2026-10-22` ; ouvrir une PR brouillon seulement après revue si autorisée, ne pas lancer Codex par dépôt de ce document. Aucune migration ni effacement d'une donnée utilisateur.

## 15. Compte rendu exigé
Module / mission / branche / base / commit / code et commandes exécutées / tests vrais / problèmes / régressions possibles / blocages / étape suivante. Une proposition de réparation n'est jamais qualifiée de réparation appliquée.
