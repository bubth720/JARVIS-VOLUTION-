# FINAL-01 / F08 — EVOLUTION : diagnostic en lecture seule des campagnes V3

**Base :** `feat/evolution-final-01` @ `c4abfb035e3e99cc52840b04af06e3cea818c7a0`. Branche `ccr-e05fe51e-ycqubx`.

**Statut : PARTIAL.** Le diagnostic, la comparaison et les propositions sont faits et testés. L'essai de correctif reste **BLOCKED** (`SANDBOX_NOT_QUALIFIED`).

## Ce qui est ajouté

| Fichier | Rôle |
|---|---|
| `evolution-core/.../campaign/CampaignEvidence.kt` | Lecteur **en lecture seule** des fichiers de preuves V3 (`apiNN-results.txt`, `apiNN-audit-results.txt`, `apiNN-audit-p.json`). Il ne retient que des verdicts `PASS`/`FAIL` par identifiant et 4 métriques numériques bornées par phase de la sphère. Aucun texte : réponses, documents et traces d'erreur sont ignorés. Liens symboliques et fichiers > 1 Mio sont refusés. Deux verdicts contradictoires pour un même test donnent FAIL. |
| `evolution-core/.../campaign/CampaignComparator.kt` | Comparaison avant/après déterministe. Par test : FIXED, REGRESSED, STILL_FAILING, NEW_PASS, NEW_FAIL ou MISSING_AFTER. Budgets de la sphère selon la règle du propriétaire. Statut : REGRESSION, OVER_BUDGET, IMPROVEMENT, STABLE ou INCOMPLETE. Propositions `PROPOSED_FOR_HUMAN_REVIEW`, toujours `mayExecute=false`. |
| `evolution-cli/.../CampaignCommand.kt` | `campaign-compare <avant> <sha> <après> <sha> <nouveau-rapport.json>` : n'écrit qu'un **nouveau** fichier et refuse d'écraser. |
| `evolution-core/src/test/.../CampaignTest.kt` | 10 tests : lecture fermée, noms réels, contradiction, amélioration et régression, absence, sphère figée en réponse, refus, non-écriture, rapport sans exécution. |

## Tests exécutés

- `gradle test` (JDK 17) : **61/61**, soit 51 tests historiques inchangés et 10 nouveaux.
- **Essai sur données réelles** : `campaign-compare` entre la campagne `8f77a8d` (run 43658134254) et la campagne `b1224ae` (run 44391288619), sur les fichiers versionnés d'INTERFACE.
  - Résultat : `REGRESSION`, avec FIXED 2, STILL_FAILING 4, NEW_PASS 8, NEW_FAIL 2 et STABLE_PASS 58.
  - Détail conforme à l'historique : c8 réparé ; K toujours en échec ; P nouveau et en échec ; Q, R, S et T nouveaux et réussis ; c et e (API 35) toujours en échec.
  - 10 propositions. Fichier : `real-comparison-8f77a8d-to-b1224ae.json`.
- **Deux défauts de l'adaptateur trouvés par cet essai réel**, corrigés et couverts par des tests :
  1. Seuls les noms de tests en minuscules étaient lus (4 tests au lieu d'environ 37 par API).
  2. Un dépassement de budget sans mesure « avant » était classé STABLE ; il donne désormais OVER_BUDGET.

## Non fait (volontairement)

- Aucun essai de correctif ni exécution de code candidat : `SANDBOX_NOT_QUALIFIED`, `mayExecute=false`.
- Aucun raccordement automatique à la CI ni collecte périodique. La commande est lancée explicitement par l'hôte.
- Aucune lecture de conversation ou de document de l'utilisateur.
