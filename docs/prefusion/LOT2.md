# EVOL-PF-01 — deuxième lot pré-fusion / 2026-10-27

Base mission vérifiée Git/GitHub : `4af477877c3ceb32e5805948ca42798970512d44`, branche `mission/prefusion-gates-20261026`.
Développement : `codex/prefusion-lot2-20261027`. PR brouillon vers la branche mission, jamais main. SHA publié et résultats CI exacts dans la description de PR ; aucune auto-référence inventée.

## Changement borné

Drain sérialisé avec activation/désactivation du collecteur. Après premier ACK d’écriture incertain, les événements restants ne sont plus drainés ; basculer le consentement ne réinitialise pas l’incertitude. Deux tests nouveaux : quarantaine avec queue restante et désactivation concurrente pendant écriture. Aucun nouveau journal, thread, collecte ou traitement automatique.

## Validation et limites

Tentative locale ./gradlew test :evolution-cli:installDist bloquée pendant téléchargement wrapper : proxy shell inaccessible, zéro test JVM local compté. Bubblewrap0.12.0 : fixture fixe /bin/true sous unshare-all refusée (NETLINK_ROUTE socket Operation not permitted), aucun code de patch exécuté ni fallback. CI existante exécute toutes les suites et les trois démos.

Les nouveaux tests Kotlin sont soumis à la CI après publication ; aucun résultat anticipé. Tests JVM/host != instrumentation != preuve physique. SDK, ABI, moteurs, modèles, budgets, contrats et anciennes preuves conservés. Aucun dépôt du premier lot modifié. Les gardes historiques CORE refusent volontairement le nouveau périmètre au HEAD ; leur garde dédié historique n’est ni assoupli ni présenté comme preuve actuelle. Les résultats nouveaux sont distincts.

## Blocages et décision

Sandbox OS toujours BLOCKED, aucune qualification Android ; RestrictedTrialRunner reste NOT_RUN/SANDBOX_NOT_QUALIFIED. Aucun correctif appliqué, aucun changement du protocole signé ou des données CORE.

Les essais sur appareil personnel, toute migration ou activation sensible attendent validation. Aucun merge main, déploiement, permission réseau/runtime ou nouvel import de dépendance. Les missions complètes restent PARTIAL/BLOCKED, seul ce lot ciblé est livré.

## Retour arrière et suite

Revert des commits de cette branche ; aucune migration ni donnée produit à restaurer. Lire la CI au SHA publié, corriger ses échecs sans skip ; ensuite traiter une tranche OFF indépendante. L’architecture d’ensemble, ADR-002 et les raccordements actifs ne sont pas présumés approuvés.
