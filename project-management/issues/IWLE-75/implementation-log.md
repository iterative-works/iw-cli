# Implementation Log: iw start fails when running inside tmux

**Issue:** IWLE-75

This log tracks the evolution of implementation across phases.

---

## Phase 1: Core switch functionality for start.scala (2025-12-18)

**What was built:**
- Adapter: `.iw/core/Tmux.scala` - Added `switchSession(name: String): Either[String, Unit]` method using `tmux switch-client -t <session>`
- Command: `.iw/commands/start.scala` - Added conditional logic to detect tmux environment and choose switch vs attach

**Decisions made:**
- Follow exact pattern of existing `attachSession` method for consistency
- Keep error handling simple: show actionable manual command on failure, leave session running (per analysis decision)
- Use existing `isInsideTmux` detection which checks TMUX env var

**Patterns applied:**
- Functional error handling: `Either[String, Unit]` return type consistent with all TmuxAdapter methods
- Environment detection: Leverage existing `Constants.EnvVars.Tmux` and `isInsideTmux` check

**Testing:**
- Unit tests: 2 tests added for `switchSession` failure scenarios
- Total tests: 157 passing

**Code review:**
- Iterations: 1
- Review file: review-packet-phase-01.md
- Major findings: No critical issues. Minor suggestions for message consolidation.

**For next phases:**
- Available utilities: `TmuxAdapter.switchSession` can be used by `open.scala` in Phase 2
- Extension points: Same conditional pattern (check `isInsideTmux`, then switch or attach)
- Notes: Manual testing recommended to verify actual tmux switching behavior

**Files changed:**
```
M  .iw/commands/start.scala (+19/-9)
M  .iw/core/Tmux.scala (+6)
M  .iw/core/test/TmuxAdapterTest.scala (+21)
```

---
