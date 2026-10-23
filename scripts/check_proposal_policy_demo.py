#!/usr/bin/env python3
"""Exercises policy/signature/metadata persistence; asserts that execution stays blocked."""
import json
from pathlib import Path
import subprocess
import tempfile
root = Path(__file__).resolve().parents[1]
cli = root / "evolution-cli/build/install/evolution-cli/bin/evolution-cli"
with tempfile.TemporaryDirectory(prefix="evol-policy-synthetic-") as directory:
    result = subprocess.run([str(cli), "proposal-policy-demo", str(Path(directory) / "new-lab")],
                            text=True, capture_output=True, timeout=30)
    assert result.returncode == 0
    report = json.loads(result.stdout)
    assert report["policyValidation"] == "PASS" and report["reviewPersisted"]
    assert report["runner"] == "NOT_RUN" and report["reason"] == "SANDBOX_NOT_QUALIFIED"
    assert report["sourceUnchanged"] and not report["gitBaseQualified"]
print(json.dumps({"result": "PASS", "policy_demo": True, "untrusted_execution": False,
                  "sandbox_qualified": False, "mission_complete": False}))
