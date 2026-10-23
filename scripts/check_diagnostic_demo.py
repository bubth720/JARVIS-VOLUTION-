#!/usr/bin/env python3
"""Real persisted diagnostic CLI exercise, with synthetic sources only."""
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cli = root / "evolution-cli/build/install/evolution-cli/bin/evolution-cli"

def invoke(*args, expected=0):
    run = subprocess.run([str(cli), *map(str, args)], text=True, capture_output=True, timeout=30)
    assert run.returncode == expected, (args[0], run.returncode)
    return json.loads(run.stdout) if run.stdout.strip() else None

with tempfile.TemporaryDirectory(prefix="jarvis-diagnostic-synthetic-") as tmp:
    lab = Path(tmp) / "new-lab"
    initial = invoke("diagnostic-demo", lab)["cases"]
    assert len(initial) == 4
    events, cases = lab / "events.db", lab / "cases.db"
    with sqlite3.connect(events) as db:
        ids = {r[0] for r in db.execute("SELECT id FROM incidents")}
    for case in initial:
        assert case["confidence"] == "POSSIBLE_CAUSE" and case["status"] == "OPEN"
        assert case["owner"] != "INTERFACE"  # NO_DATA negative control, never a fabricated diagnosis.
        for evidence in case["evidence"]:
            assert evidence["snapshot"]["incidents"][0]["id"] in ids
            canonical = json.dumps(evidence["snapshot"], separators=(",", ":"), ensure_ascii=False)
            assert hashlib.sha256(canonical.encode()).hexdigest() == evidence["sha256"]
    chosen = initial[0]["id"]
    invoke("case-state", cases, chosen, "ACKNOWLEDGED")
    reread = invoke("cases", cases)["cases"]
    assert next(c for c in reread if c["id"] == chosen)["status"] == "ACKNOWLEDGED"
    invoke("diagnose", events, cases, 1000000)
    repeated = invoke("cases", cases)["cases"]
    assert {c["id"] for c in repeated} == {c["id"] for c in initial}
    assert next(c for c in repeated if c["id"] == chosen)["status"] == "ACKNOWLEDGED"
    with sqlite3.connect(cases) as db:
        assert db.execute("SELECT count(*) FROM journal").fetchone()[0] == 5
    invoke("diagnostic-demo", lab, expected=2)
print(json.dumps({"result": "PASS", "persisted_cases": 4,
                  "checks": ["owner", "source_ids", "evidence_sha256", "negative_control", "ack_reopen", "idempotency", "append_only_decisions"],
                  "repair_executed": False}))
