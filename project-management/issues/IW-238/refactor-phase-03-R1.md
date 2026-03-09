# Refactoring R1: Extract shared helpers and fix forge detection

**Phase:** 3
**Created:** 2026-03-08
**Status:** Complete

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

- [x] [impl] Create `model/ForgeType.scala` — enum GitHub/GitLab with `fromHost(host)` and `fromRemote(remote)`
- [x] [test] Create `test/ForgeTypeTest.scala` — github.com→GitHub, gitlab.com→GitLab, gitlab.e-bs.cz→GitLab, unknown→GitLab
- [x] [impl] Create `model/PhaseArgs.scala` — `namedArg`, `hasFlag`, `resolveIssueId`, `resolvePhaseNumber`
- [x] [test] Create `test/PhaseArgsTest.scala` — test each function with present/absent/fallback cases
- [x] [impl] Create `output/CommandHelpers.scala` — `exitOnError[A]`, `exitOnNone[A]`
- [x] [impl] Add `GitAdapter.fetchAndReset(featureBranch, dir)` to `adapters/Git.scala`
- [x] [test] Add test for `fetchAndReset` in `test/GitTest.scala`
- [x] [verify] Run `./iw test unit` — all unit tests pass including new ones

### Command rewrites

- [x] [impl] Rewrite `phase-start.scala` using PhaseArgs, CommandHelpers
- [x] [verify] Run `bats .iw/test/phase-start.bats` — all 7 tests pass
- [x] [impl] Rewrite `phase-commit.scala` using PhaseArgs, CommandHelpers
- [x] [verify] Run `bats .iw/test/phase-commit.bats` — all 7 tests pass
- [x] [impl] Rewrite `phase-pr.scala` using PhaseArgs, CommandHelpers, ForgeType, GitAdapter.fetchAndReset
- [x] [verify] Run `bats .iw/test/phase-pr.bats` — all 3 tests pass
- [x] [impl] Rewrite `phase-advance.scala` using PhaseArgs, CommandHelpers, ForgeType, GitAdapter.fetchAndReset
- [x] [verify] Run `bats .iw/test/phase-advance.bats` — all 2 tests pass

### Final verification

- [x] [verify] Run `./iw test` — full test suite passes, no regressions
- [x] [verify] Verify all new files have PURPOSE comments
- [x] [cleanup] Remove any dead code from command files

## Verification

- [x] All 19 phase command E2E tests pass unchanged
- [x] All existing unit tests pass
- [x] New unit tests for ForgeType and PhaseArgs pass
- [x] `phase-pr` and `phase-advance` use forge detection from git remote, not tracker type
- [x] No regressions in any existing functionality
