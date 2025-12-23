# Refactoring R1: Hardcoded feedback target

**Phase:** 3
**Created:** 2025-12-22
**Status:** Complete

## Decision Summary

The feedback command is for reporting issues **about iw-cli itself**, not for the user's current project. It should always target the iw-cli repository (`iterative-works/iw-cli`) regardless of what project the user is working in or whether they're in an iw-cli project at all.

The previous implementation incorrectly:
1. Read local `.iw/config.conf` to detect tracker type
2. Routed to different clients (GitHub vs Linear) based on local config
3. Used repository from local config

## Target State

The feedback command now:
```
feedback.scala
  → Use hardcoded Constants.Feedback.Repository constant
  → Always use GitHubClient
  → No dependency on local config
  → Work from any directory
```

**New behavior:**
- `iw feedback "Bug report"` → Creates issue in `iterative-works/iw-cli` on GitHub
- Works whether or not `.iw/config.conf` exists
- Works from any directory (doesn't need to be in an iw project)

## Constraints

- PRESERVE: GitHubClient.scala implementation (it's correct and reusable) ✓
- PRESERVE: GitHubClientTest.scala tests (they test the client, not the command) ✓
- PRESERVE: Label mapping logic (Bug → "bug", Feature → "feedback") ✓
- PRESERVE: Graceful label fallback ✓
- DO NOT TOUCH: LinearClient.scala (may be used elsewhere) ✓
- DO NOT TOUCH: init.scala, issue.scala (they correctly use config) ✓

## Tasks

- [x] [impl] [Analysis] Review current feedback.scala to understand config dependencies
- [x] [impl] [Constants] Add FEEDBACK_REPOSITORY constant to Constants.scala
- [x] [impl] [Refactor] Remove config loading from feedback.scala
- [x] [impl] [Refactor] Remove tracker type routing - always use GitHubClient
- [x] [impl] [Refactor] Use FEEDBACK_REPOSITORY constant instead of config.repository
- [x] [impl] [Test] Update E2E tests to not require local config
- [x] [impl] [Test] Add test: feedback works without .iw/config.conf
- [x] [impl] [Test] Remove tests for Linear feedback routing (no longer applicable)
- [x] [impl] [Verify] Run all tests, ensure nothing broke

## Verification

- [x] All unit tests pass (GitHubClientTest unchanged)
- [x] All E2E tests pass (updated for new behavior)
- [x] `iw feedback "Test"` works without .iw/config.conf
- [x] `iw feedback "Test"` creates issue in iterative-works/iw-cli
- [x] No regressions in other commands (init, issue, etc.)
