# Phase 3 Tasks: GitHub CI polling and auto-merge

**Issue:** IW-289
**Phase:** 3 of 7

## Setup

- [x] [setup] Read existing `PhaseMerge.scala`, `PhaseMergeTest.scala`, and `GitHubClient.scala` to understand current structure and patterns
- [x] [setup] Read an existing command script (e.g., `phase-pr.scala`) to understand the command script pattern, argument handling, and review-state interaction
- [x] [setup] Verify Phase 1 and Phase 2 code compiles and tests pass: `scala-cli compile --scalac-option -Werror .iw/core/` and `./iw test unit`

## Tests First (TDD)

### Layer 1: Pure JSON parsing — `parseGhChecksJson`

- [x] [test] Add test: valid JSON with all-passing checks → `Right(List[CICheckResult])` with all `Passed`
- [x] [test] Add test: valid JSON with mixed states (SUCCESS, FAILURE, PENDING) → correctly maps each to `CICheckStatus`
- [x] [test] Add test: valid JSON with CANCELLED state → maps to `CICheckStatus.Cancelled`
- [x] [test] Add test: valid JSON with unknown state string → maps to `CICheckStatus.Unknown`
- [x] [test] Add test: empty JSON array `[]` → `Right(Nil)`
- [x] [test] Add test: invalid JSON (not valid JSON at all) → `Left(error)`
- [x] [test] Add test: valid JSON but not an array (e.g., `{}`) → `Left(error)`
- [x] [test] Add test: JSON array with missing required field (e.g., no `name`) → `Left(error)`
- [x] [test] Add test: check with empty `link` field → `url` is `None` or `Some("")` (decide based on convention)
- [x] [test] Add test: check with non-empty `link` field → `url` is `Some(link)`
- [x] [test] Run tests to confirm they fail (function doesn't exist yet)

### Layer 2: Adapter — `fetchCheckStatuses`

- [x] [test] Add test: successful `gh pr checks` execution → returns parsed `Right(List[CICheckResult])`
- [x] [test] Add test: `gh` command returns error → returns `Left` with error message
- [x] [test] Add test: `gh` returns empty array JSON → returns `Right(Nil)`
- [x] [test] Run tests to confirm they fail

### Layer 3: Command helpers (pure parts testable in unit tests)

- [x] [test] Add test: `buildCheckStatusesCommand(42, "owner/repo")` → correct `Array[String]` with `--json name,state,link,bucket`
- [x] [test] Add test: `buildMergePrWithDeleteCommand("https://github.com/owner/repo/pull/42")` → correct `Array[String]` with `--merge --delete-branch`
- [x] [test] Run tests to confirm they fail

## Implementation

### Layer 1: Pure JSON parsing

- [x] [impl] Add `parseGhChecksJson(json: String): Either[String, List[CICheckResult]]` to `PhaseMerge` object in `model/PhaseMerge.scala`
- [x] [impl] Map `state` field: `"SUCCESS"` → Passed, `"FAILURE"` → Failed, `"PENDING"` → Pending, `"CANCELLED"` → Cancelled, anything else → Unknown
- [x] [impl] Map empty `link` to `None`, non-empty `link` to `Some(link)`
- [x] [impl] Use `ujson` for parsing (same pattern as `GitHubClient.parseFetchIssueResponse`)
- [x] [impl] Run Layer 1 tests to confirm they pass
- [x] [impl] Compile with `-Werror`

### Layer 2: Adapter function

- [x] [impl] Add `buildCheckStatusesCommand(prNumber: Int, repository: String): Array[String]` to `GitHubClient`
- [x] [impl] Add `buildMergePrWithDeleteCommand(prUrl: String): Array[String]` to `GitHubClient` (returns `--merge --delete-branch`)
- [x] [impl] Add `fetchCheckStatuses(prNumber: Int, repository: String, execCommand: ...)` to `GitHubClient` — calls `gh pr checks`, uses `PhaseMerge.parseGhChecksJson` for parsing, returns `Either[String, List[CICheckResult]]`
- [x] [impl] Run Layer 2 tests to confirm they pass
- [x] [impl] Compile with `-Werror`

### Layer 3: Command script

- [x] [impl] Create `.iw/commands/phase-merge.scala` with file header and required imports
- [x] [impl] Read context: issue ID (from branch), forge type, repository, review-state path (follow `phase-pr.scala` patterns)
- [x] [impl] Detect ForgeType — if GitLab, exit with error "GitLab support coming in a future phase"
- [x] [impl] Read PR URL from review-state.json, extract PR number via `PhaseMerge.extractPrNumber`
- [x] [impl] Update review-state to `ci_pending`
- [x] [impl] Implement polling loop: call `fetchCheckStatuses` → `evaluateChecks` → on `AllPassed`/`NoChecksFound` break to merge, on `StillRunning` sleep `PhaseMergeConfig.pollIntervalMs` and continue, on `SomeFailed` print failed checks and exit non-zero
- [x] [impl] Add wall-clock timeout check in polling loop: if elapsed > `PhaseMergeConfig.timeoutMs` → print timeout message and exit non-zero
- [x] [impl] On merge: call `gh pr merge --merge --delete-branch <prUrl>` via `GitHubClient`
- [x] [impl] After merge: checkout feature branch and fetch & reset (advance feature branch)
- [x] [impl] Update review-state to `phase_merged`
- [x] [impl] Output JSON result (follow `phase-pr.scala` pattern)

## Integration

- [x] [integrate] Verify `phase-merge.scala` compiles: `scala-cli compile .iw/commands/phase-merge.scala`
- [x] [integrate] Run full unit test suite: `./iw test unit`
- [x] [integrate] Compile core with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [x] [integrate] Verify all existing tests still pass: `./iw test`

## E2E Tests (BATS)

- [x] [e2e] Create `.iw/test/e2e/phase-merge.bats` with setup exporting `IW_SERVER_DISABLED=1`
- [x] [e2e] Happy path test: mock `gh` script returns all-passing checks JSON, then mock merge success → verify `iw phase-merge` succeeds, review-state updated to `phase_merged`
- [x] [e2e] CI failure test: mock `gh` returns failing checks → verify `iw phase-merge` exits non-zero, prints failed check names
- [x] [e2e] Not on phase branch test: run from a non-phase branch → verify error message
- [x] [e2e] Missing PR URL test: review-state with no `pr_url` → verify error message
- [x] [e2e] GitLab forge type test: config with GitLab forge → verify "not yet supported" error
- [x] [e2e] Run E2E tests: `./iw test e2e`

## Verification

- [x] [verify] All new unit tests pass: `./iw test unit`
- [x] [verify] All E2E tests pass: `./iw test e2e`
- [x] [verify] Core compiles clean with `-Werror`
- [x] [verify] No I/O imports in `PhaseMerge.scala` model (pure parsing uses `ujson` which is data-only)
- [x] [verify] Existing phase-pr and batch-implement tests still pass
**Phase Status:** Complete
