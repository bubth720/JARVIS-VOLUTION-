#!/usr/bin/env python3
"""Actual CLI + Linux SQLite exercise using only a newly created synthetic temporary database."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
cli = root / "evolution-cli/build/install/evolution-cli/bin/evolution-cli"

def invoke(*args, expected=0):
    result = subprocess.run([str(cli), *map(str, args)], text=True, capture_output=True, timeout=30)
    assert result.returncode == expected, (args[0], result.returncode)
    return json.loads(result.stdout) if result.stdout.strip() else None

with tempfile.TemporaryDirectory(prefix="jarvis-evolution-synthetic-") as temp:
    database = Path(temp) / "events.db"
    summary = invoke("demo", database)
    assert summary["persistedIncidents"] == 100 and summary["occurrences"] == 100
    original = hashlib.sha256(database.read_bytes()).hexdigest()
    invoke("demo", database, expected=2)
    assert hashlib.sha256(database.read_bytes()).hexdigest() == original
    rows = invoke("list", database)["incidents"]
    assert len(rows) == 100 and sum(r["occurrences"] for r in rows) == 100
    assert len(invoke("list", database, "CORE")["incidents"]) == 20
    target = Path(temp) / "sanitized.json"
    invoke("export", database, target)
    assert json.loads(target.read_text())["incidents"] == rows
    original_export = target.read_bytes()
    invoke("export", database, target, expected=2)
    assert target.read_bytes() == original_export
    assert b"SYNTHETIC_PRIVATE_TEXT_MUST_NOT_PERSIST" not in database.read_bytes()
    assert "message" not in target.read_text() and "payload" not in target.read_text()
    assert invoke("purge", database, 1_000_000 + 8 * 86_400_000)["expiredRemoved"] == 100
    assert not invoke("list", database)["incidents"]
print(json.dumps({"result": "PASS", "persisted_incidents": 100, "sqlite_platform": "JVM Linux",
                  "checks": ["reopen", "module_filter", "explicit_export", "no_overwrite", "no_private_text", "retention"],
                  "android_qualified": False}))
