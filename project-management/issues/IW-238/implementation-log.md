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
