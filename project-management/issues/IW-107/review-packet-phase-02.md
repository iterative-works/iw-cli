# Review Packet: Phase 2 - Improved Error Messaging

**Issue:** IW-107
**Phase:** 2
**Branch:** IW-107-phase-02
**Date:** 2026-01-12

---

## Goals

Improve error messaging in `claude-sync` when the template file is not found:
- Show the exact path that was checked
- Indicate this is an installation issue (not a project setup issue)
- Provide actionable suggestions (check IW_HOME, reinstall iw-cli)
- Show detected installation directory if IW_COMMANDS_DIR was set

---

## Scenarios (Acceptance Criteria)

From Story 2 in analysis.md:

- [x] Error message clearly indicates installation issue, not project setup issue
- [x] Error shows exact path where template was expected
- [x] Error suggests actionable next steps
- [x] Exit code remains non-zero (1)

---

## Entry Points

1. **Error handling block:** `.iw/commands/claude-sync.scala:24-33`
   - This is where the improved error messaging was implemented

2. **Test coverage:** `.iw/test/claude-sync.bats:114-142`
   - Three new tests verify the error messaging improvements

---

## Architecture Diagram

```
                 ┌─────────────────────────────────┐
                 │       claude-sync.scala         │
                 └─────────────────────────────────┘
                               │
                               ▼
                 ┌─────────────────────────────────┐
                 │     Resolve Template Path       │
                 │  (Phase 1: IW_COMMANDS_DIR)     │
                 └─────────────────────────────────┘
                               │
                 ┌─────────────┴─────────────┐
                 ▼                           ▼
         Template Found              Template Not Found
                 │                           │
                 ▼                           ▼
          Continue...            ┌─────────────────────────────┐
                                 │  PHASE 2: Improved Errors   │
                                 ├─────────────────────────────┤
                                 │ 1. Show exact path checked  │
                                 │ 2. "installation" context   │
                                 │ 3. Actionable suggestions   │
                                 │ 4. Show IW_COMMANDS_DIR     │
                                 └─────────────────────────────┘
                                               │
                                               ▼
                                          sys.exit(1)
```

---

## Code Changes

### `.iw/commands/claude-sync.scala` (lines 24-33)

**Before (Phase 1):**
```scala
if !os.exists(promptFile) then
  Output.error(s"Prompt file not found: $promptFile")
  sys.exit(1)
```

**After (Phase 2):**
```scala
if !os.exists(promptFile) then
  Output.error(s"Template file not found: $promptFile")
  Output.info("This template is part of the iw-cli installation.")
  Output.info("Suggestions:")
  Output.info("  - Check if IW_HOME is set correctly")
  Output.info("  - Try reinstalling iw-cli")
  sys.env.get(Constants.EnvVars.IwCommandsDir).foreach { dir =>
    Output.info(s"  - Installation directory detected: $dir")
  }
  sys.exit(1)
```

---

## Test Summary

### E2E Tests (BATS) - 6 tests total

| Test | Status | Description |
|------|--------|-------------|
| claude-sync finds template from IW_COMMANDS_DIR when set | PASS | Phase 1 test |
| claude-sync works from iw-cli repository (os.pwd fallback) | PASS | Phase 1 test |
| claude-sync fails gracefully when template not found | PASS | Phase 1 test |
| **error message shows exact path that was checked** | PASS | **Phase 2 NEW** |
| **error message indicates installation issue** | PASS | **Phase 2 NEW** |
| **error message provides actionable suggestions** | PASS | **Phase 2 NEW** |

All tests passing.

---

## Files Changed

```
 .iw/commands/claude-sync.scala                     |  9 ++++-
 .iw/test/claude-sync.bats                          | 38 +++++++++++++++++++---
 2 files changed (excluding workflow artifacts)
```

---

## Manual Verification

Error output when template is missing:

```
Error: Template file not found: /path/to/.iw/scripts/claude-skill-prompt.md
This template is part of the iw-cli installation.
Suggestions:
  - Check if IW_HOME is set correctly
  - Try reinstalling iw-cli
  - Installation directory detected: /path/to/.iw/commands
```

---

## Review Checklist

- [ ] Error message is clear and actionable
- [ ] No misleading information (doesn't suggest "create local template")
- [ ] Installation context is helpful for debugging
- [ ] Suggestions match actual recovery steps
- [ ] Code style matches existing patterns
- [ ] Test coverage is adequate
