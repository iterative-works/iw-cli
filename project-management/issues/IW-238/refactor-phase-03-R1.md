# Refactoring R1: Extract shared helpers and fix forge detection

**Phase:** 3
**Created:** 2026-03-08
**Status:** Planned

## Decision Summary

Four phase command files have significant code duplication (arg parsing, issue ID resolution, phase number resolution, branch detection, config reading, fetch+reset sequences). Additionally, forge detection (choosing between `gh` and `glab` CLI) incorrectly uses the issue tracker type from config instead of the git remote URL host, breaking YouTrack+GitLab configurations.

## Current State

- All 4 commands duplicate: argument parsing (~6 lines each), issue ID resolution (~12 lines each), phase number resolution (~12 lines each), getCurrentBranch error handling (~5 lines each)
- `phase-pr` and `phase-advance` duplicate: config reading (~6 lines), git fetch+reset (~10 lines)
- `phase-pr` and `phase-advance` use `config.trackerType match` to dispatch between GitHub/GitLab — fails for YouTrack+GitLab projects
- `exitOnError` pattern (match Left → Output.error + sys.exit, Right → value) appears ~20 times across all files

## Target State

- Shared pure parsing in `model/PhaseArgs.scala` (namedArg, hasFlag, resolveIssueId, resolvePhaseNumber)
- Forge detection via `model/ForgeType.scala` enum (fromRemote using GitRemote.host, not trackerType)
- Exit helpers in `output/CommandHelpers.scala` (exitOnError, exitOnNone)
- Git fetch+reset in `adapters/Git.scala` (fetchAndReset method)
- All 4 commands rewritten to use these helpers, ~45% line reduction

## Constraints

- PRESERVE: all existing E2E test behavior (19 tests across 4 BATS files must pass unchanged)
- PRESERVE: existing model/adapter APIs (IssueId, PhaseNumber, PhaseBranch, ReviewStateAdapter)
- DO NOT TOUCH: BATS test files (unless a test has wrong assertions)
- DO NOT TOUCH: existing adapter methods (only add new ones)
- Follow FCIS: pure logic in model/, presentation in output/, I/O in adapters/

## Tasks

### New shared code

- [ ] [impl] Create `model/ForgeType.scala` — enum GitHub/GitLab with `fromHost(host)` and `fromRemote(remote)`
- [ ] [test] Create `test/ForgeTypeTest.scala` — github.com→GitHub, gitlab.com→GitLab, gitlab.e-bs.cz→GitLab, unknown→GitLab
- [ ] [impl] Create `model/PhaseArgs.scala` — `namedArg`, `hasFlag`, `resolveIssueId`, `resolvePhaseNumber`
- [ ] [test] Create `test/PhaseArgsTest.scala` — test each function with present/absent/fallback cases
- [ ] [impl] Create `output/CommandHelpers.scala` — `exitOnError[A]`, `exitOnNone[A]`
- [ ] [impl] Add `GitAdapter.fetchAndReset(featureBranch, dir)` to `adapters/Git.scala`
- [ ] [test] Add test for `fetchAndReset` in `test/GitTest.scala`
- [ ] [verify] Run `./iw test unit` — all unit tests pass including new ones

### Command rewrites

- [ ] [impl] Rewrite `phase-start.scala` using PhaseArgs, CommandHelpers
- [ ] [verify] Run `bats .iw/test/phase-start.bats` — all 7 tests pass
- [ ] [impl] Rewrite `phase-commit.scala` using PhaseArgs, CommandHelpers
- [ ] [verify] Run `bats .iw/test/phase-commit.bats` — all 7 tests pass
- [ ] [impl] Rewrite `phase-pr.scala` using PhaseArgs, CommandHelpers, ForgeType, GitAdapter.fetchAndReset
- [ ] [verify] Run `bats .iw/test/phase-pr.bats` — all 3 tests pass
- [ ] [impl] Rewrite `phase-advance.scala` using PhaseArgs, CommandHelpers, ForgeType, GitAdapter.fetchAndReset
- [ ] [verify] Run `bats .iw/test/phase-advance.bats` — all 2 tests pass

### Final verification

- [ ] [verify] Run `./iw test` — full test suite passes, no regressions
- [ ] [verify] Verify all new files have PURPOSE comments
- [ ] [cleanup] Remove any dead code from command files

## Verification

- [ ] All 19 phase command E2E tests pass unchanged
- [ ] All existing unit tests pass
- [ ] New unit tests for ForgeType and PhaseArgs pass
- [ ] `phase-pr` and `phase-advance` use forge detection from git remote, not tracker type
- [ ] No regressions in any existing functionality
