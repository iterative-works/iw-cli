# Phase 3: GitHub CI polling and auto-merge

**Issue:** IW-289
**Phase:** 3 of 7
**Story:** CI polling and auto-merge on pass — GitHub (Story 1 from analysis)

## Goals

Create the `iw phase-merge` command that polls GitHub CI check statuses for a PR, waits until all checks complete, and auto-merges on success. This is the core happy-path command that connects the pure decision logic (Phase 1) and PR number extraction (Phase 2) with real I/O.

## Scope

### In Scope

- **Adapter function** `GitHubClient.fetchCheckStatuses(prNumber, repository)` — calls `gh pr checks <number> --repo <repo> --json name,state,link,bucket` and parses JSON into `List[CICheckResult]`
- **Pure JSON parsing function** `PhaseMerge.parseGhChecksJson(json: String): Either[String, List[CICheckResult]]` — maps `gh pr checks` JSON output to domain types
- **Command script** `phase-merge.scala` — orchestrates: read review-state → extract PR number → poll CI → merge on AllPassed/NoChecksFound → update review-state → advance feature branch
- **Polling loop** in `phase-merge.scala` — calls fetchCheckStatuses at intervals, evaluates verdict via `PhaseMerge.evaluateChecks`, exits on terminal verdict
- **Merge via** `gh pr merge --merge --delete-branch <prUrl>` (merge commit, not squash, per analysis decision)
- **Feature branch advance** after merge — checkout feature branch, fetch & reset
- **Review-state updates** — status transitions: `awaiting_review` → `ci_pending` → `phase_merged`
- **Output** — progress messages during polling, final merge status

### Out of Scope

- GitLab support (Phase 6)
- CI failure recovery / agent re-invocation (Phase 5)
- Timeout and configurable polling (Phase 4 — use hardcoded `PhaseMergeConfig` defaults for now)
- Command-line argument parsing for timeout/interval (Phase 4)
- Retry logic on CI failure (Phase 5 — for now, exit with error on SomeFailed)

## Dependencies

- **Phase 1** — `PhaseMerge.evaluateChecks`, `CICheckResult`, `CICheckStatus`, `CIVerdict`, `PhaseMergeConfig`
- **Phase 2** — `PhaseMerge.extractPrNumber`
- **Existing code:**
  - `GitHubClient` pattern (injected `execCommand`, `validateGhPrerequisites`)
  - `ProcessAdapter.run` for shell execution
  - `ReviewStateAdapter.update` for review-state transitions
  - `GitAdapter.checkoutBranch`, `GitAdapter.fetchAndReset` for branch advancement
  - `PhaseArgs`, `CommandHelpers`, `ForgeType`, `PhaseBranch` for argument handling
  - `Output` for user-facing messages

## Approach

### Layer 1: Pure JSON parsing in model (TDD first)

Add `parseGhChecksJson` to `PhaseMerge` object. This parses the JSON output of `gh pr checks --json name,state,link,bucket` into `List[CICheckResult]`.

**`gh pr checks` JSON format** (observed from real output):
```json
[
  {"bucket":"pass","link":"https://...","name":"test","state":"SUCCESS"},
  {"bucket":"fail","link":"https://...","name":"lint","state":"FAILURE"},
  {"bucket":"pending","link":"","name":"build","state":"PENDING"}
]
```

**State mapping:**
- `"SUCCESS"` → `CICheckStatus.Passed`
- `"FAILURE"` → `CICheckStatus.Failed`
- `"PENDING"` → `CICheckStatus.Pending`
- `"CANCELLED"` → `CICheckStatus.Cancelled`
- anything else → `CICheckStatus.Unknown`

Use the project's existing JSON library (`upickle`) for parsing, or simple string matching if the structure is trivial enough. Follow whichever pattern `GitHubClient` already uses for JSON.

### Layer 2: Adapter function for fetching checks

Add `fetchCheckStatuses` to `GitHubClient` following the existing pattern:
- Takes `prNumber: Int`, `repository: String`
- Takes injected `execCommand` for testability (same pattern as `createPullRequest`)
- Calls `gh pr checks <number> --repo <repo> --json name,state,link,bucket`
- Returns `Either[String, List[CICheckResult]]`
- Uses `PhaseMerge.parseGhChecksJson` for the pure parsing step

### Layer 3: Command script `phase-merge.scala`

New command at `.iw/commands/phase-merge.scala` that:

1. **Reads context:** issue ID (from branch), forge type, repository, review-state path
2. **Reads PR URL** from review-state.json via `ReviewStateAdapter` or raw file read
3. **Extracts PR number** via `PhaseMerge.extractPrNumber`
4. **Updates review-state** to `ci_pending`
5. **Polls in a loop:**
   - Calls `GitHubClient.fetchCheckStatuses`
   - Evaluates with `PhaseMerge.evaluateChecks`
   - On `AllPassed` or `NoChecksFound` → break, merge
   - On `StillRunning` → sleep `PhaseMergeConfig.pollIntervalMs`, continue
   - On `SomeFailed` → print failed checks, exit with error (recovery is Phase 5)
   - On timeout (wall clock > `PhaseMergeConfig.timeoutMs`) → set `TimedOut`, exit with error
6. **Merges PR** via `gh pr merge --merge --delete-branch <prUrl>`
7. **Advances feature branch** — checkout + fetch & reset
8. **Updates review-state** to `phase_merged`
9. **Outputs JSON** result (following `phase-pr.scala` pattern)

**Branch assumptions:**
- Must be run while on the phase sub-branch (same as `phase-pr`)
- After merge, switches to the feature branch

**Note on ForgeType:** This phase only handles GitHub. The command should detect ForgeType and error if GitLab (with message "GitLab support coming in a future phase").

## Files to Create/Modify

### Create

- `.iw/commands/phase-merge.scala` — Command script for CI polling and auto-merge

### Modify

- `.iw/core/model/PhaseMerge.scala` — Add `parseGhChecksJson` pure function
- `.iw/core/adapters/GitHubClient.scala` — Add `fetchCheckStatuses` function
- `.iw/core/test/PhaseMergeTest.scala` — Add tests for `parseGhChecksJson`

### Possibly create

- `.iw/core/test/fixtures/gh-checks-all-pass.json` — Test fixture for all-passing checks
- `.iw/core/test/fixtures/gh-checks-some-fail.json` — Test fixture for mixed results
- `.iw/core/test/fixtures/gh-checks-pending.json` — Test fixture for pending checks
- `.iw/core/test/fixtures/gh-checks-empty.json` — Test fixture for empty array

## Testing Strategy

### Unit tests (pure, in PhaseMergeTest.scala)

**`parseGhChecksJson` scenarios:**
- Valid JSON with all-passing checks → `Right(List[CICheckResult])` with all Passed
- Valid JSON with mixed states → correctly maps SUCCESS/FAILURE/PENDING/CANCELLED
- Valid JSON with unknown state → maps to Unknown
- Empty array `[]` → `Right(Nil)`
- Invalid JSON (not an array) → `Left(error)`
- Missing required fields → `Left(error)`
- Check with empty link → `url = None` or `url = Some("")`

### Integration tests (adapter level)

**`GitHubClient.fetchCheckStatuses` with injected execCommand:**
- Successful execution → parses and returns checks
- `gh` returns error → Left with error message
- `gh` returns "no checks reported" → handle as empty list or specific error

### E2E tests (BATS)

**`phase-merge` happy path:**
- Setup: create a mock `gh` script that returns all-passing checks JSON
- Run `iw phase-merge` on a phase sub-branch with valid review-state
- Verify: merge command called, review-state updated to `phase_merged`

**`phase-merge` CI failure:**
- Setup: mock `gh` returns failing checks
- Run `iw phase-merge`
- Verify: exits with error, review-state shows failure

**`phase-merge` not on phase branch:**
- Run from a non-phase branch
- Verify: error message

**All E2E tests must export `IW_SERVER_DISABLED=1` in setup().**

### Verification commands

- `scala-cli compile --scalac-option -Werror .iw/core/` — no warnings
- `./iw test unit` — all tests pass
- `./iw test e2e` — all E2E tests pass (when added)

## Acceptance Criteria

- [ ] `PhaseMerge.parseGhChecksJson` correctly parses `gh pr checks` JSON into `List[CICheckResult]`
- [ ] `GitHubClient.fetchCheckStatuses` calls `gh pr checks` and returns parsed results
- [ ] `phase-merge.scala` command exists and can be run via `iw phase-merge`
- [ ] Polls CI until terminal verdict (AllPassed, SomeFailed, or timeout)
- [ ] On AllPassed/NoChecksFound: merges PR with `--merge --delete-branch`, advances feature branch
- [ ] On SomeFailed: exits with non-zero and prints failed checks
- [ ] On timeout: exits with non-zero and prints timeout message
- [ ] Review-state updated: `ci_pending` during polling, `phase_merged` after merge
- [ ] GitLab detected → clear error message ("not yet supported")
- [ ] Works when run on phase sub-branch, errors otherwise
- [ ] No compilation warnings with `-Werror`
- [ ] All existing tests still pass
