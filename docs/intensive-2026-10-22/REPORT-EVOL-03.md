# EVOL-03 — politiques implémentées, exécution bloquée

**Statut : PARTIAL / BLOCKED. EVOL-03 non terminée ; EVOL-04 non éligible.**

Module EVOLUTION, dépôt `bubth720/JARVIS-VOLUTION-`, branche `feat/evol-03-atelier-de-correctifs-isoles-e`.
Base EVOL-02 publiée `0abd5f96caa27ee1e73a623d538e48555a02ce76`.
Référence UNI-01 revérifiée par `git ls-remote` : `feat/uni-01-tooling` à `bd283dad3a57effa276f7f4441901839d2834d5c`. Aucun contrat UNIFIED v1 modifié.

## Code effectif, sans application du patch

- Nouveau module JVM `evolution-sandbox` : `DiffPolicyValidator`, `DiffSummary`, `PatchProposal`, `ProposalVerifier`, `VerifiedProposal` et `RestrictedTrialRunner`.
- Validation réelle de syntaxe/hunks/compteurs de diff, UTF-8 strict,64KiB/4096lignes/8fichiers maximum, chemins réguliers Kotlin/Java autorisés explicitement, pas de création/suppression/renommage/binaire/exécutable/symlink. Les hunks de remplacement uniquement sont pris en charge ; les autres formats sont refusés.
- Vérification cryptographique Ed25519 réelle sur l'origine épinglée par l'hôte, empreinte du contenu, base attendue, référence UNIFIED exacte, mission/version et expiration. Cela n'atteste pas encore la correspondance du diff à un vrai checkout ni l'autorisation d'un humain.
- `HumanReviewQueue` : métadonnées seules, journal append-only avec chaîne SHA-256 et fsync,128propositions/4096décisions/1MiB, confirmation du digest exact, approbation/rejet/révocation, reprise et expiration sans suppression des traces. Aucune clef privée ni contenu de diff stocké. Journal corrompu/tronqué : refus, aucun reset.
- CLI `proposal-policy-demo` : fixture strictement synthétique, diff vérifié et signé avec clef éphémère, revue persistée et relue, source vérifiée inchangée. Sa base Git de patch est volontairement fictive et n'est jamais annoncée qualifiée.
- Le runner retourne toujours `NOT_RUN / SANDBOX_NOT_QUALIFIED`, même après signature et approbation de laboratoire. Aucune option booléenne/variante de transport ne le débloque.

## Blocage OS observé

Essai unique, programme fixe de confiance `/bin/true`, aucun code de patch exécuté :

```text
bubblewrap 0.12.0
bwrap: setting up uid map: Read-only file system
exit status: 1
```

Commande : `bwrap --unshare-all --die-with-parent --new-session --ro-bind /usr /usr --ro-bind /bin /bin --ro-bind /lib /lib --ro-bind /lib64 /lib64 --proc /proc --dev /dev --tmpfs /tmp --clearenv --setenv PATH /usr/bin:/bin -- /bin/true`.

Namespaces/FS minimal non établis : sandbox non qualifiée. Aucun contournement, élévation, répétition inchangée ou fallback à une exécution hors sandbox. Ni compilation du patch, checkout Git éphémère, métrique de gain, rollback d'essai ou preuve d'isolation n'est inventé.

## Validations réellement exécutées

- `./gradlew test :evolution-cli:installDist --no-daemon --console=plain` : build réussi ; résultats **38tests,0échec,0erreur,0saut**, dont25régressions EVOL-01/02 et13nouveaux tests de politique/revue. La dernière passe a réexécuté les13tests sandbox ; les25régressions inchangées ont été réutilisées UP-TO-DATE après leur exécution réussie précédente.
- `python3 scripts/check_demo.py` :100incidents SQLite persistés/relecture/export/rétention PASS.
- `python3 scripts/check_diagnostic_demo.py` :4cas persistés, preuves/digests/contrôle négatif/acquittement/idempotence PASS.
- `python3 scripts/check_proposal_policy_demo.py` : validation/signature/revue PASS ; runner NOT_RUN, source inchangée, sandbox_qualified=false et mission_complete=false.
- Tests nouveaux : chemins et modes interdits, hunks incomplets, taille/Unicode, lecture seule, symlink vers une autre fixture, vraie signature/contrefaçon/expiration/mauvaises références, refus du runner même approuvé, revue durable/révocation, rejeu refusé, journal tronqué préservé.
- `git diff --check` : PASS.

Toutes les fixtures sont neuves et synthétiques. Aucun snapshot privé ou fichier applicatif JARVIS n'a été traité ; aucun code non fiable lancé. Aucun essai Android, installation, changement de signature APK, migration ou merge main. La présence d'un module nommé sandbox n'est pas une preuve qu'une sandbox OS fonctionne.

## Reste à réaliser et prochaine action

Un environnement mini-PC/laboratoire dont les namespaces, restrictions filesystem/réseau/processus et limites CPU/RAM/temps/stockage sont effectivement qualifiés est indispensable. Le futur runner devra disposer de tests d'isolation et d'une allowlist de commandes fixes, d'un vrai checkout synthétique, d'un build et d'un retour arrière démontrés. Une nouvelle revue distincte est nécessaire avant d'autoriser l'application de correctifs ou une intégration sensible ; cette livraison n'en accorde aucun droit.

Conserver EVOL-04 bloquée jusqu'à livraison des capacités d'essai manquantes. Les validateurs/propositions de cette tranche peuvent être examinés et testés dès maintenant ; ils ne satisfont pas les critères complets d'EVOL-03.
