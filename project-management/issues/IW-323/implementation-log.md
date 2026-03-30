# Implementation Log: Support Plugin Command Directories

Issue: IW-323

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Constants (2026-03-30)

**Layer:** Domain (model/)

**What was built:**
- `Constants.EnvVars.IwPluginDirs` — env var name for plugin directory override
- `Constants.Paths.PluginsDir` — XDG path segment for plugin discovery
- `Constants.CommandHeaders` — new object with `Requires` field for version gating header

**Dependencies on other layers:**
- None — first phase, pure constants

**Testing:**
- Unit tests: 3 tests added (one per new constant)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260330-233458.md
- Result: Pass (no critical issues)

**Files changed:**
```
M	.iw/core/model/Constants.scala
M	.iw/core/test/ConstantsTest.scala
```

---
