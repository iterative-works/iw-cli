# Phase 3 Tasks: GitHub CI polling and auto-merge

**Issue:** IW-289
**Phase:** 3 of 7

## Setup

- [ ] [setup] Read existing `PhaseMerge.scala`, `PhaseMergeTest.scala`, and `GitHubClient.scala` to understand current structure and patterns
- [ ] [setup] Read an existing command script (e.g., `phase-pr.scala`) to understand the command script pattern, argument handling, and review-state interaction
- [ ] [setup] Verify Phase 1 and Phase 2 code compiles and tests pass: `scala-cli compile --scalac-option -Werror .iw/core/` and `./iw test unit`

## Tests First (TDD)

### Layer 1: Pure JSON parsing — `parseGhChecksJson`

- [ ] [test] Add test: valid JSON with all-passing checks → `Right(List[CICheckResult])` with all `Passed`
- [ ] [test] Add test: valid JSON with mixed states (SUCCESS, FAILURE, PENDING) → correctly maps each to `CICheckStatus`
- [ ] [test] Add test: valid JSON with CANCELLED state → maps to `CICheckStatus.Cancelled`
- [ ] [test] Add test: valid JSON with unknown state string → maps to `CICheckStatus.Unknown`
- [ ] [test] Add test: empty JSON array `[]` → `Right(Nil)`
- [ ] [test] Add test: invalid JSON (not valid JSON at all) → `Left(error)`
- [ ] [test] Add test: valid JSON but not an array (e.g., `{}`) → `Left(error)`
- [ ] [test] Add test: JSON array with missing required field (e.g., no `name`) → `Left(error)`
- [ ] [test] Add test: check with empty `link` field → `url` is `None` or `Some("")` (decide based on convention)
- [ ] [test] Add test: check with non-empty `link` field → `url` is `Some(link)`
- [ ] [test] Run tests to confirm they fail (function doesn't exist yet)

### Layer 2: Adapter — `fetchCheckStatuses`

- [ ] [test] Add test: successful `gh pr checks` execution → returns parsed `Right(List[CICheckResult])`
- [ ] [test] Add test: `gh` command returns error → returns `Left` with error message
- [ ] [test] Add test: `gh` returns empty array JSON → returns `Right(Nil)`
- [ ] [test] Run tests to confirm they fail

### Layer 3: Command helpers (pure parts testable in unit tests)

- [ ] [test] Add test: `buildCheckStatusesCommand(42, "owner/repo")` → correct `Array[String]` with `--json name,state,link,bucket`
- [ ] [test] Add test: `buildMergePrWithDeleteCommand("https://github.com/owner/repo/pull/42")` → correct `Array[String]` with `--merge --delete-branch`
- [ ] [test] Run tests to confirm they fail

## Implementation

### Layer 1: Pure JSON parsing

- [ ] [impl] Add `parseGhChecksJson(json: String): Either[String, List[CICheckResult]]` to `PhaseMerge` object in `model/PhaseMerge.scala`
- [ ] [impl] Map `state` field: `"SUCCESS"` → Passed, `"FAILURE"` → Failed, `"PENDING"` → Pending, `"CANCELLED"` → Cancelled, anything else → Unknown
- [ ] [impl] Map empty `link` to `None`, non-empty `link` to `Some(link)`
- [ ] [impl] Use `ujson` for parsing (same pattern as `GitHubClient.parseFetchIssueResponse`)
- [ ] [impl] Run Layer 1 tests to confirm they pass
- [ ] [impl] Compile with `-Werror`

### Layer 2: Adapter function

- [ ] [impl] Add `buildCheckStatusesCommand(prNumber: Int, repository: String): Array[String]` to `GitHubClient`
- [ ] [impl] Add `buildMergePrWithDeleteCommand(prUrl: String): Array[String]` to `GitHubClient` (returns `--merge --delete-branch`)
- [ ] [impl] Add `fetchCheckStatuses(prNumber: Int, repository: String, execCommand: ...)` to `GitHubClient` — calls `gh pr checks`, uses `PhaseMerge.parseGhChecksJson` for parsing, returns `Either[String, List[CICheckResult]]`
- [ ] [impl] Run Layer 2 tests to confirm they pass
- [ ] [impl] Compile with `-Werror`

### Layer 3: Command script

- [ ] [impl] Create `.iw/commands/phase-merge.scala` with file header and required imports
- [ ] [impl] Read context: issue ID (from branch), forge type, repository, review-state path (follow `phase-pr.scala` patterns)
- [ ] [impl] Detect ForgeType — if GitLab, exit with error "GitLab support coming in a future phase"
- [ ] [impl] Read PR URL from review-state.json, extract PR number via `PhaseMerge.extractPrNumber`
- [ ] [impl] Update review-state to `ci_pending`
- [ ] [impl] Implement polling loop: call `fetchCheckStatuses` → `evaluateChecks` → on `AllPassed`/`NoChecksFound` break to merge, on `StillRunning` sleep `PhaseMergeConfig.pollIntervalMs` and continue, on `SomeFailed` print failed checks and exit non-zero
- [ ] [impl] Add wall-clock timeout check in polling loop: if elapsed > `PhaseMergeConfig.timeoutMs` → print timeout message and exit non-zero
- [ ] [impl] On merge: call `gh pr merge --merge --delete-branch <prUrl>` via `GitHubClient`
- [ ] [impl] After merge: checkout feature branch and fetch & reset (advance feature branch)
- [ ] [impl] Update review-state to `phase_merged`
- [ ] [impl] Output JSON result (follow `phase-pr.scala` pattern)

## Integration

- [ ] [integrate] Verify `phase-merge.scala` compiles: `scala-cli compile .iw/commands/phase-merge.scala`
- [ ] [integrate] Run full unit test suite: `./iw test unit`
- [ ] [integrate] Compile core with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [ ] [integrate] Verify all existing tests still pass: `./iw test`

## E2E Tests (BATS)

- [ ] [e2e] Create `.iw/test/e2e/phase-merge.bats` with setup exporting `IW_SERVER_DISABLED=1`
- [ ] [e2e] Happy path test: mock `gh` script returns all-passing checks JSON, then mock merge success → verify `iw phase-merge` succeeds, review-state updated to `phase_merged`
- [ ] [e2e] CI failure test: mock `gh` returns failing checks → verify `iw phase-merge` exits non-zero, prints failed check names
- [ ] [e2e] Not on phase branch test: run from a non-phase branch → verify error message
- [ ] [e2e] Missing PR URL test: review-state with no `pr_url` → verify error message
- [ ] [e2e] GitLab forge type test: config with GitLab forge → verify "not yet supported" error
- [ ] [e2e] Run E2E tests: `./iw test e2e`

## Verification

- [ ] [verify] All new unit tests pass: `./iw test unit`
- [ ] [verify] All E2E tests pass: `./iw test e2e`
- [ ] [verify] Core compiles clean with `-Werror`
- [ ] [verify] No I/O imports in `PhaseMerge.scala` model (pure parsing uses `ujson` which is data-only)
- [ ] [verify] Existing phase-pr and batch-implement tests still pass
