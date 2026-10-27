# EVOL-PF-01 / Lot 2 — non-executable LAB review interpretation

Base: `mission/prefusion-gates-20261026` commit `4af477877c3ceb32e5805948ca42798970512d44`.

New `evolution-sandbox/src/main/kotlin/fr/jarvis/evolution/sandbox/ReviewOnlyAdmission.kt` and a dedicated JUnit class. This additive projection gives a bounded, human-readable reason for refusing execution with any review state, including `APPROVED_FOR_LAB`. No file is patched, no sandbox launched and no CLI dispatch enabled.

The real OS sandbox remains unqualified (original bubblewrap namespace failure), so `canExecute=false` and `canApply=false` are constants. The tests include approved/rejected/expired/pending and missing review. Existing JUnit and three demo scripts must pass on GitHub CI.

**Blockers:** OS-isolated runner and signed human handoff not qualified. No auto-remediation or deployment permission. **Rollback:** revert added class, tests and report; no database change.
