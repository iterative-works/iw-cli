# Refactoring R1: Hardcoded feedback target

**Phase:** 3
**Created:** 2025-12-22
**Status:** Planned

## Decision Summary

The feedback command is for reporting issues **about iw-cli itself**, not for the user's current project. It should always target the iw-cli repository (`iterative-works/iw-cli`) regardless of what project the user is working in or whether they're in an iw-cli project at all.

The current implementation incorrectly:
1. Reads local `.iw/config.conf` to detect tracker type
2. Routes to different clients (GitHub vs Linear) based on local config
3. Uses repository from local config

## Current State

- File: `.iw/commands/feedback.scala` - Loads config, routes based on tracker type
- File: `.iw/test/feedback.bats` - Tests depend on local config setup
- File: `.iw/core/Constants.scala` - Contains Linear-specific constants

The feedback command currently:
```
feedback.scala
  → Load .iw/config.conf (ERROR: should not depend on local config)
  → Detect tracker type from config (ERROR: should always be GitHub)
  → Route to GitHubClient or LinearClient (ERROR: should always be GitHub)
  → Use repository from config (ERROR: should be hardcoded)
```

## Target State

The feedback command should:
```
feedback.scala
  → Use hardcoded FEEDBACK_REPOSITORY constant
  → Always use GitHubClient
  → No dependency on local config
  → Work from any directory
```

**New behavior:**
- `iw feedback "Bug report"` → Creates issue in `iterative-works/iw-cli` on GitHub
- Works whether or not `.iw/config.conf` exists
- Works from any directory (doesn't need to be in an iw project)

## Constraints

- PRESERVE: GitHubClient.scala implementation (it's correct and reusable)
- PRESERVE: GitHubClientTest.scala tests (they test the client, not the command)
- PRESERVE: Label mapping logic (Bug → "bug", Feature → "feedback")
- PRESERVE: Graceful label fallback
- DO NOT TOUCH: LinearClient.scala (may be used elsewhere)
- DO NOT TOUCH: init.scala, issue.scala (they correctly use config)

## Tasks

- [ ] [impl] [Analysis] Review current feedback.scala to understand config dependencies
- [ ] [impl] [Constants] Add FEEDBACK_REPOSITORY constant to Constants.scala
- [ ] [impl] [Refactor] Remove config loading from feedback.scala
- [ ] [impl] [Refactor] Remove tracker type routing - always use GitHubClient
- [ ] [impl] [Refactor] Use FEEDBACK_REPOSITORY constant instead of config.repository
- [ ] [impl] [Test] Update E2E tests to not require local config
- [ ] [impl] [Test] Add test: feedback works without .iw/config.conf
- [ ] [impl] [Test] Remove tests for Linear feedback routing (no longer applicable)
- [ ] [impl] [Verify] Run all tests, ensure nothing broke

## Verification

- [ ] All unit tests pass (GitHubClientTest unchanged)
- [ ] All E2E tests pass (updated for new behavior)
- [ ] `iw feedback "Test"` works without .iw/config.conf
- [ ] `iw feedback "Test"` creates issue in iterative-works/iw-cli
- [ ] No regressions in other commands (init, issue, etc.)
