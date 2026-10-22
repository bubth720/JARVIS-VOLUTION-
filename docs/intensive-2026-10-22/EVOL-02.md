# EVOL-02 — Moteur de diagnostic et recommandations traçables

## 1. Identité
**Module** EVOLUTION · **Dépôt** `bubth720/JARVIS-VOLUTION-` · **Mission** EVOL-02 · **Priorité** P1.
**Référence GitHub vérifiée** : `main` au commit `a67d3b6d39aa7d30ad38e9b1c9230b4e07180eb0`, uniquement README au point de départ.
**Branche documentaire** `plan/intensive-2026-10-22` ; **branche code proposée** `feat/evol-02-moteur-de-diagnostic-et-recomm`. **Dépendances** EVOL-01.
**Statut** PRÊTE POUR ATTRIBUTION, NON AUTORISÉE À EXÉCUTION.

## 2. État initial et distinction entre fait et cible
Le dépôt `JARVIS-VOLUTION-` est initialement dépourvu de runtime ; toutes les arborescences `evolution-*` ci-dessous sont des **propositions à créer**. Les protections CORE et les prototypes isolés discutés ailleurs ne constituent pas du code EVOLUTION déjà livré. Règle centrale : aucun patch ne peut s'appliquer à JARVIS sans approbation explicite.

## 3. Objectif d'implémentation
Transformer les événements en diagnostics déterministes, liés à des preuves et à des propositions de correction concrètes, sans annoncer de réparation automatique fictive.

## 4. Fonctions exactes à programmer
1. Créer catalogue de règles versionnées : lenteur répétée, mémoire excessive, fichier inaccessible, permission refusée, échecs de libération et panne de service.
2. Construire un interpréteur local de règles et fenêtres glissantes sur événements stockés ; gestion des inconnues et de la confiance, pas de diagnostics magiques.
3. Produire DiagnosticCase avec cause possible, événement preuve, niveau d'impact, composant propriétaire et recommandation bornée.
4. Ajouter regroupement, acquittement humain et suivi OPEN/ACKNOWLEDGED/RESOLVED/REJECTED sans masquer l'historique.
5. Créer API JSON et tableau CLI consultable, filtre par module et chronologie, liens vers événements attestés.

## 5. Architecture et classes
1. RuleEngine et RuleRepository immuable
2. IncidentCorrelator et DiagnosticCaseRepository
3. EvidenceLink et RecommendationCatalog
4. DiagnosticReportRenderer
Séparer logique de domaine, stockage, CLI et adaptateurs. Injecter horloges/stockage pour tests reproductibles, mais exercer les vrais composants persistance et traitement dans la démo.

## 6. Fichiers à créer/modifier
1. À créer : evolution-core/src/main/kotlin/.../diagnostic/{RuleEngine,IncidentCorrelator,DiagnosticCase}.kt
2. À créer : evolution-core/src/main/kotlin/.../diagnostic/DefaultRules.kt et tests sur flux synthétiques
3. À créer : evolution-cli/src/main/kotlin/.../DiagnoseCommand.kt
Lire `README.md` et établir un plan Gradle réel avant écriture. Ne pas modifier un autre dépôt pour contourner une dépendance.

## 7. Implémentation séquencée
1. Spécifier erreurs/états/identifiants en cohérence avec EVOL-01.
2. Coder moteur de conditions et corrélation temporelle des signaux, pas de second moteur IA.
3. Créer règles réelles codées et tables des causes ; garder toutes les règles désactivables.
4. Ajouter persistance des cas et statut humain, avec journal immuable.
5. Réaliser démo de 4 incidents contrastés et tests de non-détection sur données absentes.

## 8. Technologies, licences et dépendances
Kotlin standard, SQLite, JSON borné ; pas de dépendance LLM ni de commande shell.
Ajouter peu de dépendances ; déclarer licences, mode hors ligne, limites et usage de ressources. Les extensions pour matériel haute performance restent optionnelles et refusées sur Android non qualifié.

## 9. Intégrations futures et contrats
Consommer les snapshots EVENT explicites sans données personnelles ; préparer port vers UI, mais aucune exécution de remédiation.
Ports produits par cette mission = internes ou opt-in ; aucune modification automatique des contrats UNIFIED ni activation d'une liaison inter-APK.

## 10. Contraintes impératives
- Collecte locale uniquement ; données personnelles, contenus des conversations, audio, poids, clés et jetons interdits dans les logs.
- Pas d'auto-installation, écriture dans main, auto-merge, privilèges OS/root ou exécution de code non fiable hors sandbox autorisée.
- Batterie, RAM et thermique Android prioritaires ; planifier opérations lourdes uniquement sur mini-PC approprié.
- Désactivation par défaut des tâches cycliques et opération de correction ; toute action sensible nécessite une nouvelle autorisation.

## 11. Livrables code
Modules compilables réels, API versionnée au sein du dépôt, démonstration CLI qui exerce le comportement promis, données synthétiques traçables, tests élémentaires et rapport bref listant commits/fichiers. Une classe vide ou des simulations non raccordées ne comptent pas.

## 12. Acceptation minimum mesurable
1. Un flux d'erreurs synthétiques conduit à des DiagnosticCase persistés et attribués au bon module.
2. Chaque recommandation renvoie à des identifiants de preuve valides ; pas de prétention d'action réussie.
3. L'acquittement humain et la reprise sont observables après réouverture.
Compiler et rejouer la démo ; documenter précisément les fonctionnalités non encore branchées aux autres modules.

## 13. Dépendances et parallélisme
EVOL-01. Les missions EVOLUTION doivent suivre ces gates, mais peuvent progresser indépendamment des missions NEXUS, VOICE, INTERFACE, LLM tant qu'aucun contrat partagé n'est muté.

## 14. Git, approbations et propriété
Ne jamais fusionner dans `main`. Branche code distincte de `plan/intensive-2026-10-22` ; ouvrir une PR brouillon seulement après revue si autorisée, ne pas lancer Codex par dépôt de ce document. Aucune migration ni effacement d'une donnée utilisateur.

## 15. Compte rendu exigé
Module / mission / branche / base / commit / code et commandes exécutées / tests vrais / problèmes / régressions possibles / blocages / étape suivante. Une proposition de réparation n'est jamais qualifiée de réparation appliquée.
