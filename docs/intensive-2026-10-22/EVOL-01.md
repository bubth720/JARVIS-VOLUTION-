# EVOL-01 — Télémétrie locale et observateur de santé modulaire

## 1. Identité
**Module** EVOLUTION · **Dépôt** `bubth720/JARVIS-VOLUTION-` · **Mission** EVOL-01 · **Priorité** P0.
**Référence GitHub vérifiée** : `main` au commit `a67d3b6d39aa7d30ad38e9b1c9230b4e07180eb0`, uniquement README au point de départ.
**Branche documentaire** `plan/intensive-2026-10-22` ; **branche code proposée** `feat/evol-01-telemetrie-locale-et-observate`. **Dépendances** Aucune.
**Statut** PRÊTE POUR ATTRIBUTION, NON AUTORISÉE À EXÉCUTION.

## 2. État initial et distinction entre fait et cible
Le dépôt `JARVIS-VOLUTION-` est initialement dépourvu de runtime ; toutes les arborescences `evolution-*` ci-dessous sont des **propositions à créer**. Les protections CORE et les prototypes isolés discutés ailleurs ne constituent pas du code EVOLUTION déjà livré. Règle centrale : aucun patch ne peut s'appliquer à JARVIS sans approbation explicite.

## 3. Objectif d'implémentation
Construire le premier moteur EVOLUTION autonome ; il doit collecter, persister et analyser des événements techniques produits localement sans dépendre d'un LLM, sans scanner les conversations privées et sans toucher au code JARVIS.

## 4. Fonctions exactes à programmer
1. Créer librairie Kotlin/JVM avec collecteur EventSink, event schema stable, catégorie/severity/occurrence et redaction avant stockage.
2. Implémenter stockage SQLite roulant (taille et rétention configurables), déduplication d'incidents et audit des erreurs de collecteur ; aucune dépendance réseau.
3. Ajouter sondes injectables sur délais, mémoire, crash non personnel, files saturées et états UNAVAILABLE ; pas de lecture de PID externe ni capteurs sans permission.
4. Implémenter flux StateFlow d'état lisible, API snapshot local et export compact anonymisé sur action volontaire.
5. Créer outil CLI qui injecte 100 incidents synthétiques et produit un vrai historique filtrable.

## 5. Architecture et classes
1. EvolutionEvent, SanitizedPayload et BoundedEventStore
2. HealthProbe et ProbeScheduler, scheduler désactivé par défaut sur Android
3. LocalTelemetryService et DiagnosticsSnapshot
4. CliDiagnosticFixture
Séparer logique de domaine, stockage, CLI et adaptateurs. Injecter horloges/stockage pour tests reproductibles, mais exercer les vrais composants persistance et traitement dans la démo.

## 6. Fichiers à créer/modifier
1. À créer : settings.gradle.kts, build.gradle.kts, evolution-core/src/main/kotlin/.../events/{EvolutionEvent,BoundedEventStore,Sanitizer}.kt
2. À créer : evolution-core/src/main/kotlin/.../probes/{HealthProbe,LocalTelemetryService}.kt
3. À créer : evolution-cli/src/main/kotlin/.../Main.kt et tests d'intégrité
Lire `README.md` et établir un plan Gradle réel avant écriture. Ne pas modifier un autre dépôt pour contourner une dépendance.

## 7. Implémentation séquencée
1. Créer runtime Kotlin/JVM et schéma d'événements versionné ; aucun sondage permanent tant qu'aucun hôte n'a opt-in.
2. Coder une file bornée non bloquante, stratégie de saturation explicitée, suppression/redaction des chaînes sensibles.
3. Coder stockage et requêtes temporelles, rotations et reprise après interruption.
4. Raccorder sondes synthétiques réelles par injection et une sortie CLI exploitable.
5. Exécuter tests de rétention, saturation, non-fuite, relecture et génération du snapshot.

## 8. Technologies, licences et dépendances
Kotlin/JVM, SQLite, Coroutines Flow si déjà compatibles ; adapter Android facultatif sans foreground service.
Ajouter peu de dépendances ; déclarer licences, mode hors ligne, limites et usage de ressources. Les extensions pour matériel haute performance restent optionnelles et refusées sur Android non qualifié.

## 9. Intégrations futures et contrats
CORE/LLM/VOICE publieront des événements techniques via adaptateur explicite futur ; aucun IPC ni extraction directe de leur stockage.
Ports produits par cette mission = internes ou opt-in ; aucune modification automatique des contrats UNIFIED ni activation d'une liaison inter-APK.

## 10. Contraintes impératives
- Collecte locale uniquement ; données personnelles, contenus des conversations, audio, poids, clés et jetons interdits dans les logs.
- Pas d'auto-installation, écriture dans main, auto-merge, privilèges OS/root ou exécution de code non fiable hors sandbox autorisée.
- Batterie, RAM et thermique Android prioritaires ; planifier opérations lourdes uniquement sur mini-PC approprié.
- Désactivation par défaut des tâches cycliques et opération de correction ; toute action sensible nécessite une nouvelle autorisation.

## 11. Livrables code
Modules compilables réels, API versionnée au sein du dépôt, démonstration CLI qui exerce le comportement promis, données synthétiques traçables, tests élémentaires et rapport bref listant commits/fichiers. Une classe vide ou des simulations non raccordées ne comptent pas.

## 12. Acceptation minimum mesurable
1. Une CLI locale collecte, consulte et purge selon rétention des événements persistés réels.
2. Les messages confidentiels et identifiants personnels ne peuvent pas entrer dans la base par le port standard.
3. Saturation et redémarrage donnent des états explicites sans crash ni boucle infinie.
Compiler et rejouer la démo ; documenter précisément les fonctionnalités non encore branchées aux autres modules.

## 13. Dépendances et parallélisme
Aucune. Les missions EVOLUTION doivent suivre ces gates, mais peuvent progresser indépendamment des missions NEXUS, VOICE, INTERFACE, LLM tant qu'aucun contrat partagé n'est muté.

## 14. Git, approbations et propriété
Ne jamais fusionner dans `main`. Branche code distincte de `plan/intensive-2026-10-22` ; ouvrir une PR brouillon seulement après revue si autorisée, ne pas lancer Codex par dépôt de ce document. Aucune migration ni effacement d'une donnée utilisateur.

## 15. Compte rendu exigé
Module / mission / branche / base / commit / code et commandes exécutées / tests vrais / problèmes / régressions possibles / blocages / étape suivante. Une proposition de réparation n'est jamais qualifiée de réparation appliquée.
