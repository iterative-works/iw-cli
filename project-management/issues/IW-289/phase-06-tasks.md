# Phase 6 Tasks: GitLab CI status support

**Issue:** IW-289
**Phase:** 6 of 7

## Setup

- [x] [setup] Verify Phase 5 code compiles and tests pass: `scala-cli compile --scalac-option -Werror .iw/core/` and `./iw test unit` and `./iw test e2e`
- [x] [setup] Spike: determine exact `glab` CLI command and JSON format for fetching pipeline/job statuses (candidates: `glab ci get --branch <branch> --output json`, `glab mr view <mr> --repo <repo> --json pipeline`, `glab api projects/:id/merge_requests/:mr_iid/pipelines`). Document the chosen command and a sample JSON response in a scratch file or journal entry before proceeding.
- [x] [setup] Review `GitHubClient.buildCheckStatusesCommand`, `parseGhChecksJson`, `fetchCheckStatuses`, `buildMergePrWithDeleteCommand` signatures to establish the pattern to mirror
- [x] [setup] Review `GitLabClient.scala` existing methods (`validateGlabPrerequisites`, `buildMergeMrCommand`) to plan where new methods slot in
- [x] [setup] Review `phase-merge.scala` to identify the three hardcoded GitHub references to replace: CI fetching (line 111), PR URL validation (lines 82-85), merge command (line 181)

## Tests (Write First -- TDD)

### Unit tests: `GitLabClientTest.scala`

- [x] [test] `parseGlabCiJson` -- all jobs passed: JSON fixture with `"success"` statuses returns `Right(List[CICheckResult])` all `CICheckStatus.Passed`
- [x] [test] `parseGlabCiJson` -- some jobs failed: JSON fixture with `"failed"` status returns check with `CICheckStatus.Failed`
- [x] [test] `parseGlabCiJson` -- jobs still running: JSON with `"running"` and `"pending"` statuses return `CICheckStatus.Pending`
- [x] [test] `parseGlabCiJson` -- cancelled job: JSON with `"canceled"` returns `CICheckStatus.Cancelled`
- [x] [test] `parseGlabCiJson` -- `"cancelled"` (British spelling) also maps to `CICheckStatus.Cancelled`
- [x] [test] `parseGlabCiJson` -- additional pending states: `"created"`, `"waiting_for_resource"`, `"preparing"`, `"manual"` all map to `CICheckStatus.Pending`
- [x] [test] `parseGlabCiJson` -- unknown status string maps to `CICheckStatus.Unknown`
- [x] [test] `parseGlabCiJson` -- empty array: `"[]"` returns `Right(Nil)`
- [x] [test] `parseGlabCiJson` -- invalid JSON: malformed input returns `Left(error)`
- [x] [test] `parseGlabCiJson` -- JSON object (not array): returns `Left(error)`
- [x] [test] `parseGlabCiJson` -- entry missing name field: returns `Left(error)`
- [x] [test] `parseGlabCiJson` -- job URL present returns `Some(url)`, empty/absent returns `None`
- [x] [test] `buildCheckStatusesCommand` -- verify correct glab CLI args array for given MR number and repository
- [x] [test] `buildMergeMrWithDeleteCommand` -- verify includes `--remove-source-branch` and MR URL
- [x] [test] `fetchCheckStatuses` -- with injected `execCommand` returning fixture JSON: returns parsed `Right(List[CICheckResult])`
- [x] [test] `fetchCheckStatuses` -- with injected `execCommand` returning error: returns `Left`
- [x] [test] `fetchCheckStatuses` -- with injected `execCommand` returning empty array: returns `Right(Nil)`
- [x] [test] Compile and run unit tests: `./iw test unit`

## Implementation

### Layer 1: `parseGlabCiJson` pure parser

- [x] [impl] Add import for `CICheckResult` and `CICheckStatus` to `GitLabClient.scala`
- [x] [impl] Implement `parseGlabCiJson(json: String): Either[String, List[CICheckResult]]` -- parse glab JSON array into `CICheckResult` list, mapping statuses per the context doc: `"success"`/`"passed"` -> Passed, `"failed"` -> Failed, `"pending"`/`"running"`/`"created"`/`"waiting_for_resource"`/`"preparing"`/`"manual"` -> Pending, `"canceled"`/`"cancelled"` -> Cancelled, anything else -> Unknown. Use the JSON field names determined during the spike.
- [x] [impl] Compile with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [x] [impl] Run unit tests to confirm `parseGlabCiJson` tests pass: `./iw test unit`

### Layer 2: `buildCheckStatusesCommand` and `buildMergeMrWithDeleteCommand`

- [x] [impl] Implement `buildCheckStatusesCommand(mrNumber: Int, repository: String): Array[String]` -- builds the glab CLI args determined during the spike (without leading `"glab"`)
- [x] [impl] Implement `buildMergeMrWithDeleteCommand(mrUrl: String): Array[String]` -- returns `Array("mr", "merge", "--remove-source-branch", mrUrl)`
- [x] [impl] Compile with `-Werror`
- [x] [impl] Run unit tests to confirm command builder tests pass: `./iw test unit`

### Layer 3: `fetchCheckStatuses`

- [x] [impl] Implement `fetchCheckStatuses(mrNumber: Int, repository: String, isCommandAvailable, execCommand): Either[String, List[CICheckResult]]` -- follows same pattern as `GitHubClient.fetchCheckStatuses`: validate prerequisites, build command, execute, parse with `parseGlabCiJson`
- [x] [impl] Compile with `-Werror`
- [x] [impl] Run unit tests to confirm `fetchCheckStatuses` tests pass: `./iw test unit`

### Layer 4: Update `phase-merge.scala` -- remove GitLab guard

- [x] [impl] Remove the `if forgeType == ForgeType.GitLab then ... sys.exit(1)` guard block (lines 61-63)
- [x] [impl] Compile command: `scala-cli compile .iw/commands/phase-merge.scala`

### Layer 5: Update `phase-merge.scala` -- PR URL validation dispatch

- [x] [impl] Replace hardcoded GitHub URL validation with `forgeType match`: `ForgeType.GitHub` validates `https://github.com/$repository/pull/` prefix; `ForgeType.GitLab` validates that URL contains `/$repository/-/merge_requests/`
- [x] [impl] Compile command: `scala-cli compile .iw/commands/phase-merge.scala`

### Layer 6: Update `phase-merge.scala` -- CI fetching dispatch

- [x] [impl] Replace `GitHubClient.fetchCheckStatuses(prNumber, repository)` in `poll()` with `forgeType match`: `ForgeType.GitHub` calls `GitHubClient.fetchCheckStatuses`, `ForgeType.GitLab` calls `GitLabClient.fetchCheckStatuses`
- [x] [impl] Compile command: `scala-cli compile .iw/commands/phase-merge.scala`

### Layer 7: Update `phase-merge.scala` -- merge command dispatch

- [x] [impl] Replace hardcoded `Seq("gh") ++ GitHubClient.buildMergePrWithDeleteCommand(prUrl)` with `forgeType match`: `ForgeType.GitHub` uses `gh` + `GitHubClient.buildMergePrWithDeleteCommand`, `ForgeType.GitLab` uses `glab` + `GitLabClient.buildMergeMrWithDeleteCommand`
- [x] [impl] Compile with `-Werror` (full compile including core): `scala-cli compile --scalac-option -Werror .iw/core/ && scala-cli compile .iw/commands/phase-merge.scala`

## E2E Tests (BATS)

- [x] [e2e] Remove or replace test "phase-merge with GitLab forge type exits with not-supported error" (test 3 in `phase-merge.bats`) -- GitLab is now supported
- [x] [e2e] GitLab happy path: GitLab config (`tracker.type = gitlab`, `tracker.repository = "test-group/test-repo"`), review-state with `pr_url` set to `https://gitlab.com/test-group/test-repo/-/merge_requests/42`, mock `glab` script dispatching on `$1 $2` (same pattern as mock `gh`): `auth status` -> success, CI status command -> all-passing JSON, `mr merge` -> success. Verify exit 0, review-state updated to `phase_merged`, mock `glab` called.
- [x] [e2e] GitLab CI failure: GitLab config, mock `glab` returning failed pipeline job. Run with `--max-retries 0`. Verify non-zero exit, output contains failed job name.
- [x] [e2e] All new BATS tests export `IW_SERVER_DISABLED=1` in setup (already present in shared setup)
- [x] [e2e] Run all E2E tests: `./iw test e2e`

## Integration & Verification

- [x] [verify] Compile core with `-Werror`: `scala-cli compile --scalac-option -Werror .iw/core/`
- [x] [verify] All unit tests pass: `./iw test unit`
- [x] [verify] All E2E tests pass: `./iw test e2e`
- [x] [verify] Existing GitHub-path phase-merge tests still pass (no regressions)
- [x] [verify] Existing phase-pr and batch-implement tests still pass
- [x] [verify] No new I/O imports in model/ -- all new adapter code is in `GitLabClient.scala` and `phase-merge.scala`

**Phase Status:** Complete
