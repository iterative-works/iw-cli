# Implementation Log: Add deterministic phase lifecycle commands

Issue: IW-238

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Layer (2026-03-07)

**Layer:** Domain

**What was built:**
- `.iw/core/model/PhaseBranch.scala` - PhaseNumber opaque type (validates 1-99, zero-pads) and PhaseBranch case class for sub-branch name derivation
- `.iw/core/model/CommitMessage.scala` - Pure commit message construction with title and optional bulleted items
- `.iw/core/model/PhaseTaskFile.scala` - Markdown parsing/rewriting: markComplete (Phase Status line) and markReviewed (checkbox updates)
- `.iw/core/model/PhaseOutput.scala` - StartOutput, CommitOutput, PrOutput case classes with shared toJson helper

**Dependencies on other layers:**
- None (pure domain layer, no I/O)

**Testing:**
- Unit tests: 39 tests added (13 PhaseBranch, 6 CommitMessage, 13 PhaseTaskFile, 7 PhaseOutput)
- Integration tests: 0 (not applicable for pure domain)

**Code review:**
- Iterations: 2
- Review file: review-phase-01-20260307-224247.md
- Fixed: test package mismatch (iw.core.domain -> iw.tests), idempotency test assertion, unused import, extracted shared toJson helper, single-pass markComplete

**Notable decisions:**
- `Integer.parseInt(pn)` kept in PhaseNumber.toInt instead of `pn.toInt` — Scala 3 compiler detects the latter as infinite recursion within opaque type extension
- PhaseOutput uses raw String fields (not PhaseNumber/IssueId types) for simplicity as output DTOs
- toJson embedded in model case classes as pragmatic trade-off (mild FCIS deviation acknowledged)

**Files changed:**
```
A	.iw/core/model/CommitMessage.scala
A	.iw/core/model/PhaseBranch.scala
A	.iw/core/model/PhaseOutput.scala
A	.iw/core/model/PhaseTaskFile.scala
A	.iw/core/test/CommitMessageTest.scala
A	.iw/core/test/PhaseBranchTest.scala
A	.iw/core/test/PhaseOutputTest.scala
A	.iw/core/test/PhaseTaskFileTest.scala
```

---

## Phase 2: Infrastructure Layer (2026-03-08)

**Layer:** Infrastructure

**What was built:**
- `.iw/core/adapters/Git.scala` - 8 new GitAdapter methods: createAndCheckoutBranch, checkoutBranch, stageAll, commit, push, diffNameOnly, pull, getFullHeadSha
- `.iw/core/adapters/GitHubClient.scala` - PR lifecycle: buildCreatePrCommand, parseCreatePrResponse, createPullRequest, buildMergePrCommand, mergePullRequest
- `.iw/core/adapters/GitLabClient.scala` - MR lifecycle: buildCreateMrCommand, parseCreateMrResponse, createMergeRequest, buildMergeMrCommand, mergeMergeRequest
- `.iw/core/model/FileUrlBuilder.scala` - Pure URL derivation from GitRemote + branch to GitHub/GitLab blob URLs
- `.iw/core/adapters/ReviewStateAdapter.scala` - Read/merge/validate/write cycle for review-state.json

**Dependencies on other layers:**
- Domain (Phase 1): PhaseBranch (branch naming), CommitMessage (message formatting), PhaseOutput (output DTOs)
- Existing adapters: ProcessAdapter.run() for git operations, CommandRunner for gh/glab CLI

**Testing:**
- Unit tests: 55 tests added (16 Git, 10 GitHub, 11 GitLab, 8 FileUrlBuilder, 6 ReviewStateAdapter, 4 misc)
- Integration tests: 0 (adapter tests use real git repos via fixtures)

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260308-090927.md
- Fixed: unused `dir` parameter on create/merge PR/MR methods, dead push call in test, missing auth check in merge methods

**Notable decisions:**
- `dir` parameter removed from createPullRequest/mergePullRequest/createMergeRequest/mergeMergeRequest — these commands operate on the repository identified by `--repo` flag, not the local working directory
- FileUrlBuilder placed in `model/` (pure, no I/O) — derives URLs using GitRemote host detection
- Non-github.com hosts default to GitLab URL pattern (`/-/blob/`) as pragmatic default
- `buildXxxCommand` helpers kept public to match existing codebase pattern for testability

**Pre-existing issues tracked:**
- ReviewStateUpdater.merge captures Instant.now() in model layer (mild FCIS deviation)
- extractRepoPath in FileUrlBuilder duplicates URL parsing from GitRemote

**Files changed:**
```
M	.iw/core/adapters/Git.scala
M	.iw/core/adapters/GitHubClient.scala
M	.iw/core/adapters/GitLabClient.scala
A	.iw/core/adapters/ReviewStateAdapter.scala
A	.iw/core/model/FileUrlBuilder.scala
A	.iw/core/test/FileUrlBuilderTest.scala
M	.iw/core/test/GitHubClientTest.scala
M	.iw/core/test/GitLabClientTest.scala
M	.iw/core/test/GitTest.scala
A	.iw/core/test/ReviewStateAdapterTest.scala
```

---

## Phase 3: Presentation Layer (2026-03-08)

**Layer:** Presentation

**What was built:**
- `.iw/commands/phase-start.scala` - Creates phase sub-branch, captures baseline SHA, updates review-state, outputs JSON
- `.iw/commands/phase-commit.scala` - Stages all changes, commits with structured message, updates task file, outputs JSON
- `.iw/commands/phase-pr.scala` - Pushes branch, creates GitHub/GitLab PR/MR, supports `--batch` squash-merge mode, outputs JSON
- `.iw/commands/phase-advance.scala` - Verifies PR is merged, resets feature branch to remote, outputs JSON
- `.iw/core/model/PhaseOutput.scala` - Added AdvanceOutput case class
- `.iw/core/model/PhaseBranch.scala` - Added unapply extractor for branch name pattern matching

**Dependencies on other layers:**
- Domain (Phase 1): PhaseBranch, PhaseNumber, CommitMessage, PhaseTaskFile, PhaseOutput
- Infrastructure (Phase 2): GitAdapter, GitHubClient, GitLabClient, ReviewStateAdapter, FileUrlBuilder, ConfigFileRepository

**Testing:**
- Unit tests: 2 tests added (AdvanceOutput JSON serialization)
- E2E tests: 19 tests added (7 phase-start, 7 phase-commit, 3 phase-pr, 2 phase-advance)

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260308-202756.md
- Fixed: `var merged` → immutable val, dead code in phase-advance, stale PURPOSE comment, duplicated PhaseSubBranchPattern extracted to PhaseBranch.unapply, phase-advance.bats test assertions, phase-commit.bats missing output assertion

**Notable decisions:**
- Commands use `ProcessAdapter.run()` directly for `git fetch`/`git reset --hard` (no adapter methods, as designed in context)
- Squash-merge in `--batch` mode uses `gh pr merge --squash --delete-branch` instead of existing merge adapter methods
- `PhaseBranch.unapply` extracted during code review to eliminate duplicated regex across 4 command files
- Review-state updates are best-effort (missing review-state.json logged but not fatal)
- `phase-start` uses positional arg for phase number; other commands use `--phase-number` flag

**Files changed:**
```
A	.iw/commands/phase-advance.scala
A	.iw/commands/phase-commit.scala
A	.iw/commands/phase-pr.scala
A	.iw/commands/phase-start.scala
M	.iw/core/model/PhaseBranch.scala
M	.iw/core/model/PhaseOutput.scala
M	.iw/core/test/PhaseOutputTest.scala
A	.iw/test/phase-advance.bats
A	.iw/test/phase-commit.bats
A	.iw/test/phase-pr.bats
A	.iw/test/phase-start.bats
```

---

### Refactoring R1: Extract shared helpers and fix forge detection (2026-03-09)

**Trigger:** Code review feedback — significant duplication across 4 command files, and forge detection used issue tracker type instead of git remote URL host, breaking YouTrack+GitLab configurations.

**What changed:**
- `.iw/core/model/ForgeType.scala` — New enum GitHub/GitLab with `fromHost`, `fromRemote` (returns Either), `resolve` (with tracker fallback), `cliTool`, `installUrl`
- `.iw/core/model/PhaseArgs.scala` — Shared pure arg parsing: `namedArg`, `hasFlag`, `resolveIssueId`, `resolvePhaseNumber`
- `.iw/core/output/CommandHelpers.scala` — `exitOnError[A]`, `exitOnNone[A]` CLI exit helpers
- `.iw/core/adapters/Git.scala` — Added `fetchAndReset(branch, dir)` method
- `.iw/commands/phase-{start,commit,pr,advance}.scala` — Rewritten to use shared helpers

**Before → After:**
- Duplicated arg parsing (~6 lines × 4 files) → single `PhaseArgs` object
- Duplicated issue/phase resolution (~12 lines × 4 files) → `resolveIssueId`/`resolvePhaseNumber`
- `config.trackerType` match for forge detection → `ForgeType.fromRemote` via git remote URL host
- Inline `git fetch` + `git reset --hard` → `GitAdapter.fetchAndReset`
- Duplicated forge fallback logic → `ForgeType.resolve(remoteOpt, trackerType)`
- Hardcoded `"gh"`/`"glab"` strings → `forgeType.cliTool`

**Patterns applied:**
- Extract Method (shared helpers in proper FCIS layers)
- Replace Conditional with Polymorphism (ForgeType enum methods)
- Single Source of Truth (forge CLI tool mapping on enum)

**Testing:**
- New unit tests: ForgeTypeTest (14 tests), PhaseArgsTest (12 tests), CommandHelpersTest (2 tests), GitTest.fetchAndReset (2 tests)
- All 19 E2E tests pass unchanged
- All existing unit tests pass

**Code review:**
- Iterations: 2
- Review file: review-refactor-03-R1-20260309-104840.md
- Iteration 1 fixes: no-op `.left.map`, non-idiomatic `return`, `ForgeType.fromRemote` signature (→ Either), `cliTool`/`installUrl` on enum, error message content assertions
- Iteration 2 fixes: double-prefixed error messages, `ForgeType.resolve()` extraction, hardcoded CLI tool strings, side effect in `.left.map`

**Files changed:**
```
M	.iw/commands/phase-advance.scala
M	.iw/commands/phase-commit.scala
M	.iw/commands/phase-pr.scala
M	.iw/commands/phase-start.scala
M	.iw/core/adapters/Git.scala
A	.iw/core/model/ForgeType.scala
A	.iw/core/model/PhaseArgs.scala
A	.iw/core/output/CommandHelpers.scala
A	.iw/core/test/CommandHelpersTest.scala
A	.iw/core/test/ForgeTypeTest.scala
M	.iw/core/test/GitTest.scala
A	.iw/core/test/PhaseArgsTest.scala
```

---
