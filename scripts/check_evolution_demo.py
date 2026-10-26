"""Synthetic end-to-end persistence and safety checks. Never executes a patch or benchmark."""
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import tempfile

cli = Path("evolution-cli/build/install/evolution-cli/bin/evolution-cli").resolve()

def run(*args, ok=True):
    result = subprocess.run([str(cli), *map(str, args)], capture_output=True, text=True)
    assert (result.returncode == 0) == ok, (result.returncode, result.stdout, result.stderr)
    return json.loads(result.stdout) if ok else None

with tempfile.TemporaryDirectory() as temporary:
    root = Path(temporary)
    demo = root / "demo"
    result = run("evolution-demo", demo)
    assert result == dict(synthetic=True, benchmarkExecuted=False, patchExecuted=False, sandboxQualified=False)
    history = run("history", demo / "history.db")["experiments"]
    assert len(history) == 1 and history[0]["origin"] == "SYNTHETIC"
    assert history[0]["status"] == "REGRESSION"
    assert history[0]["medianBeforeMs"] == 100 and history[0]["p95AfterMs"] == 120
    with sqlite3.connect(demo / "cases.db") as db:
        assert {row[0] for row in db.execute("SELECT rule FROM cases")} == {"REPEATED_LATENCY", "QUEUE_SATURATION"}
    report = (demo / "supervision.html").read_text()
    assert "SANDBOX_NOT_QUALIFIED" in report and "SYNTHETIC" in report
    assert "<script" not in report and "https://" not in report
    run("evolution-demo", demo, ok=False)
    run("supervise", demo / "events.db", demo / "cases.db", demo / "history.db", demo / "reviews.log", demo / "supervision.html", 10000, ok=False)
    run("supervise", demo / "events.db", demo / "cases.db", demo / "history.db", demo / "reviews.log", demo / "second.html", 10000)
    assert run("review-list", demo / "reviews.log", 10000)["reviews"] == []
    context = root / "context.properties"
    context.write_text("module=LLM\nworkloadSha256=" + "a"*64 + "\nenvironmentSha256=" + "b"*64 +
                       "\nconfigurationSha256=" + "c"*64 + "\nbeforeRevision=" + "d"*40 +
                       "\nafterRevision=" + "e"*40 + "\nwarmups=2\norigin=SYNTHETIC\n")
    before = root / "before.csv"
    after = root / "after.csv"
    before.write_text(",".join(["100"]*20))
    after.write_text(",".join(["80"]*20))
    result = run("compare", demo / "history.db", context, before, after, 10001)["experiments"][0]
    assert result["status"] == "IMPROVEMENT" and result["origin"] == "SYNTHETIC"
    assert len(run("history", demo / "history.db")["experiments"]) == 2
    for invalid in ("NaN,1,2,3,4", "1,2", "Infinity,1,2,3,4"):
        after.write_text(invalid)
        run("compare", demo / "history.db", context, before, after, 10002, ok=False)
    assert len(run("history", demo / "history.db")["experiments"]) == 2
    after.unlink()
    after.symlink_to(before)
    run("compare", demo / "history.db", context, before, after, 10002, ok=False)
print("PASS evolution: synthetic comparisons, durable history, diagnostics, offline report, refusals; no benchmark or patch executed")
