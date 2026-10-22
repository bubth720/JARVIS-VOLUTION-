# EVOL-04 — Tableau de propositions et rollback de laboratoire

## 1. Identité
**Module** EVOLUTION · **Dépôt** `bubth720/JARVIS-VOLUTION-` · **Mission** EVOL-04 · **Priorité** P2.
**Référence GitHub vérifiée** : `main` au commit `a67d3b6d39aa7d30ad38e9b1c9230b4e07180eb0`, uniquement README au point de départ.
**Branche documentaire** `plan/intensive-2026-10-22` ; **branche code proposée** `feat/evol-04-tableau-de-propositions-et-rol`. **Dépendances** EVOL-03.
**Statut** PRÊTE POUR ATTRIBUTION, NON AUTORISÉE À EXÉCUTION.

## 2. État initial et distinction entre fait et cible
Le dépôt `JARVIS-VOLUTION-` est initialement dépourvu de runtime ; toutes les arborescences `evolution-*` ci-dessous sont des **propositions à créer**. Les protections CORE et les prototypes isolés discutés ailleurs ne constituent pas du code EVOLUTION déjà livré. Règle centrale : aucun patch ne peut s'appliquer à JARVIS sans approbation explicite.

## 3. Objectif d'implémentation
Créer l'expérience de validation humaine et le cycle des propositions d'amélioration, sans déploiement automatique.

## 4. Fonctions exactes à programmer
1. Implémenter liste de PatchTrial avec diff résumés, risques, provenance et fichiers cibles.
2. État durable PROPOSED/REVIEWED/APPROVED_FOR_LAB/REJECTED/EXPIRED ; l'approbation ne déclenche aucune installation.
3. Ajouter comparaison avant/après sur critères fonctionnels, temps, RAM et échecs ; refuser métriques absentes comme des gains.
4. Permettre sauvegarde/fermeture d'un espace sandbox et recréation d'un snapshot précédent ; laisser les branches source intactes.
5. Ajouter export d'un dossier de PR brouillon prêt à être transmis, sans l'envoyer automatiquement.

## 5. Architecture et classes
1. ReviewDashboardModel et DecisionJournal
2. PerformanceDeltaEvaluator et MissingEvidence
3. SandboxRollbackManager, ProposalExporter
Séparer logique de domaine, stockage, CLI et adaptateurs. Injecter horloges/stockage pour tests reproductibles, mais exercer les vrais composants persistance et traitement dans la démo.

## 6. Fichiers à créer/modifier
1. À créer : evolution-core/src/main/kotlin/.../review/{DecisionJournal,PerformanceDeltaEvaluator}.kt
2. À créer : evolution-cli/src/main/kotlin/.../ReviewCli.kt, evolution-sandbox/src/main/kotlin/.../SandboxRollbackManager.kt
Lire `README.md` et établir un plan Gradle réel avant écriture. Ne pas modifier un autre dépôt pour contourner une dépendance.

## 7. Implémentation séquencée
1. Définir la machine à états avec transitions interdites.
2. Coder le journal append-only et l'outil interactif local.
3. Implémenter analyse avant/après avec incertitude et données manquantes explicites.
4. Ajouter restauration d'une copie sandbox à une révision précédente.
5. Tester approbation/rejet/reprise et inexistence de toute écriture sur cible réelle.

## 8. Technologies, licences et dépendances
Kotlin CLI portable Android (lecture seule) et mini-PC (sandbox), stockage local.
Ajouter peu de dépendances ; déclarer licences, mode hors ligne, limites et usage de ressources. Les extensions pour matériel haute performance restent optionnelles et refusées sur Android non qualifié.

## 9. Intégrations futures et contrats
Éventuelle UI d'INTERFACE en lecture seule ; signature/approbation finale par humain et systèmes Git externes après accord séparé.
Ports produits par cette mission = internes ou opt-in ; aucune modification automatique des contrats UNIFIED ni activation d'une liaison inter-APK.

## 10. Contraintes impératives
- Collecte locale uniquement ; données personnelles, contenus des conversations, audio, poids, clés et jetons interdits dans les logs.
- Pas d'auto-installation, écriture dans main, auto-merge, privilèges OS/root ou exécution de code non fiable hors sandbox autorisée.
- Batterie, RAM et thermique Android prioritaires ; planifier opérations lourdes uniquement sur mini-PC approprié.
- Désactivation par défaut des tâches cycliques et opération de correction ; toute action sensible nécessite une nouvelle autorisation.

## 11. Livrables code
Modules compilables réels, API versionnée au sein du dépôt, démonstration CLI qui exerce le comportement promis, données synthétiques traçables, tests élémentaires et rapport bref listant commits/fichiers. Une classe vide ou des simulations non raccordées ne comptent pas.

## 12. Acceptation minimum mesurable
1. Une proposition peut être revue et annulée dans le sandbox sans perdre l'historique.
2. Un échantillon à métrique absente n'est jamais déclaré amélioré.
3. Aucun bouton, commande ou API n'effectue une fusion main.
Compiler et rejouer la démo ; documenter précisément les fonctionnalités non encore branchées aux autres modules.

## 13. Dépendances et parallélisme
EVOL-03. Les missions EVOLUTION doivent suivre ces gates, mais peuvent progresser indépendamment des missions NEXUS, VOICE, INTERFACE, LLM tant qu'aucun contrat partagé n'est muté.

## 14. Git, approbations et propriété
Ne jamais fusionner dans `main`. Branche code distincte de `plan/intensive-2026-10-22` ; ouvrir une PR brouillon seulement après revue si autorisée, ne pas lancer Codex par dépôt de ce document. Aucune migration ni effacement d'une donnée utilisateur.

## 15. Compte rendu exigé
Module / mission / branche / base / commit / code et commandes exécutées / tests vrais / problèmes / régressions possibles / blocages / étape suivante. Une proposition de réparation n'est jamais qualifiée de réparation appliquée.
