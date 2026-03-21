# Phase 6: GitLab CI status support

## Goals

Enable `iw phase-merge` to work with GitLab CI pipelines, not just GitHub Actions. After this phase, the command polls GitLab CI pipeline status via `glab` CLI, merges MRs on success, and handles failures with the same retry/recovery logic already built for GitHub.

## Scope

### In scope

- `GitLabClient.fetchCheckStatuses` — adapter to fetch pipeline/job statuses via `glab` CLI and parse into `List[CICheckResult]`
- `GitLabClient.buildCheckStatusesCommand` — pure command builder for glab CI status query
- `GitLabClient.buildMergeMrWithDeleteCommand` — merge MR with branch deletion (analogous to `GitHubClient.buildMergePrWithDeleteCommand`)
- `phase-merge.scala` — remove the "GitLab support coming in a future phase" guard; add `ForgeType.GitLab` dispatch for CI fetching and merge
- PR URL validation for GitLab MR URLs (currently only validates `https://github.com/...` prefix)
- Unit tests for glab JSON parsing
- E2E BATS tests for GitLab scenario

### Out of scope

- Self-hosted GitLab URL detection changes to `ForgeType` (already handled by `ForgeType.fromHost`)
- Changes to `PhaseMerge` model (pure logic already handles both forges via `CICheckResult`)
- Changes to `PhaseOutput.MergeOutput` (forge-agnostic)
- GitLab-specific recovery prompt text (same `buildRecoveryPrompt` works for both)

## Dependencies

- **Phase 1** — `CICheckResult`, `CICheckStatus`, `CIVerdict`, `PhaseMerge.evaluateChecks` (done)
- **Phase 2** — `PhaseMerge.extractPrNumber` already handles GitLab MR URLs (done)
- **Phase 3** — `phase-merge.scala` command with polling loop, merge, branch advance (done)
- **Phase 4** — Timeout and configurable polling (done)
- **Phase 5** — Recovery loop with agent re-invocation (done)
- **Existing** — `GitLabClient` in `.iw/core/adapters/GitLabClient.scala` with `validateGlabPrerequisites`, `buildMergeMrCommand`, `createMergeRequest`
- **Existing** — `ForgeType` enum with `resolve()`, `cliTool` ("glab"), `installUrl`

## Approach

### 1. Add CI status fetching to GitLabClient

The `glab` CLI provides pipeline job statuses. The first implementation task must be a **spike to verify the exact glab CLI command and JSON format** for fetching CI pipeline/job statuses. Candidate commands:

```
glab ci get --branch <branch> --output json
glab mr view <mr-number> --repo <repository> --json pipeline
glab api projects/:id/merge_requests/:mr_iid/pipelines
```

The implementer must run the actual command against a GitLab project or check `glab` documentation to determine the correct approach and JSON structure before writing the parser.

Add to `GitLabClient`:
- `buildCheckStatusesCommand(mrNumber: Int, repository: String): Array[String]` — builds the glab CLI args
- `parseGlabCiJson(json: String): Either[String, List[CICheckResult]]` — parses glab JSON into the same `CICheckResult` model used by GitHub
- `fetchCheckStatuses(mrNumber: Int, repository: String, ...): Either[String, List[CICheckResult]]` — orchestrates prerequisite check + command + parsing

Map glab pipeline job statuses to `CICheckStatus`:
- `"success"` / `"passed"` → `CICheckStatus.Passed`
- `"failed"` → `CICheckStatus.Failed`
- `"pending"` / `"running"` / `"created"` / `"waiting_for_resource"` / `"preparing"` / `"manual"` → `CICheckStatus.Pending`
- `"canceled"` / `"cancelled"` → `CICheckStatus.Cancelled`
- anything else → `CICheckStatus.Unknown`

### 2. Add merge-with-delete command for GitLab

The existing `GitLabClient.buildMergeMrCommand` does `Array("mr", "merge", mrUrl)` without `--remove-source-branch`. Add:

```scala
def buildMergeMrWithDeleteCommand(mrUrl: String): Array[String] =
  Array("mr", "merge", "--remove-source-branch", mrUrl)
```

This is the GitLab equivalent of `gh pr merge --merge --delete-branch`.

Note: GitLab's `glab mr merge` uses merge commit by default (no `--merge` flag needed). The `--squash` flag was used in `phase-pr.scala` batch mode but the analysis decision says phase merges should use merge commits, not squash.

### 3. Update phase-merge.scala for ForgeType dispatch

Remove the early-exit guard:
```scala
// REMOVE:
if forgeType == ForgeType.GitLab then
  Output.error("GitLab support coming in a future phase.")
  sys.exit(1)
```

Add forge-type dispatch in two places:

**CI status fetching** (in `poll()`):
```scala
val checks = forgeType match
  case ForgeType.GitHub => GitHubClient.fetchCheckStatuses(prNumber, repository)
  case ForgeType.GitLab => GitLabClient.fetchCheckStatuses(prNumber, repository)
```

**PR/MR URL validation** — currently hardcoded to `https://github.com/...`:
```scala
// CURRENT:
val expectedPrefix = s"https://github.com/$repository/pull/"
if !prUrl.startsWith(expectedPrefix) then ...

// CHANGE TO: validate based on forge type
forgeType match
  case ForgeType.GitHub =>
    val expectedPrefix = s"https://github.com/$repository/pull/"
    if !prUrl.startsWith(expectedPrefix) then ...
  case ForgeType.GitLab =>
    // GitLab URLs: https://<host>/<path>/-/merge_requests/<number>
    // Validate that repository path appears in URL
    if !prUrl.contains(s"/$repository/-/merge_requests/") then ...
```

**Merge command** — currently hardcoded to `gh pr merge`:
```scala
// CURRENT:
val mergeResult = ProcessAdapter.run(Seq("gh") ++ GitHubClient.buildMergePrWithDeleteCommand(prUrl).toSeq)

// CHANGE TO:
val mergeResult = forgeType match
  case ForgeType.GitHub =>
    ProcessAdapter.run(Seq("gh") ++ GitHubClient.buildMergePrWithDeleteCommand(prUrl).toSeq)
  case ForgeType.GitLab =>
    ProcessAdapter.run(Seq("glab") ++ GitLabClient.buildMergeMrWithDeleteCommand(prUrl).toSeq)
```

## Files to modify

| File | Changes |
|------|---------|
| `.iw/core/adapters/GitLabClient.scala` | Add `buildCheckStatusesCommand`, `parseGlabCiJson`, `fetchCheckStatuses`, `buildMergeMrWithDeleteCommand` |
| `.iw/commands/phase-merge.scala` | Remove GitLab guard; add `ForgeType` dispatch for CI fetching, URL validation, and merge command |
| `.iw/core/test/GitLabClientTest.scala` | Add unit tests for glab CI JSON parsing and command builders |
| `.iw/test/phase-merge.bats` | Add E2E tests for GitLab scenario; update test 3 (GitLab guard test) |

## Testing strategy

### Unit tests (GitLabClientTest.scala)

1. **`parseGlabCiJson` — all jobs passed**: JSON with `"success"` statuses → `List[CICheckResult]` all `Passed`
2. **`parseGlabCiJson` — some jobs failed**: JSON with `"failed"` status → check has `Failed` status
3. **`parseGlabCiJson` — jobs still running**: JSON with `"running"` or `"pending"` → `Pending` status
4. **`parseGlabCiJson` — cancelled job**: JSON with `"canceled"` → `Cancelled` status
5. **`parseGlabCiJson` — empty array**: `[]` → empty list (NoChecksFound when fed to `evaluateChecks`)
6. **`parseGlabCiJson` — invalid JSON**: malformed input → `Left(error)`
7. **`buildCheckStatusesCommand`**: verify correct glab CLI args
8. **`buildMergeMrWithDeleteCommand`**: verify includes `--remove-source-branch`
9. **`fetchCheckStatuses` integration test**: with injected `execCommand` returning fixture JSON

### E2E BATS tests (phase-merge.bats)

1. **GitLab happy path**: GitLab config + mock `glab` returning passing pipeline → MR merged, review-state updated to `phase_merged`
2. **GitLab CI failure**: mock `glab` returning failed pipeline → exits non-zero with failed job name
3. **Remove test "phase-merge with GitLab forge type exits with not-supported error"**: This test must be **removed or replaced** since GitLab will now be supported

BATS tests need:
- `.iw/config.conf` with `tracker.type = gitlab` and `tracker.repository = "test-group/test-repo"`
- Mock `glab` script in `$TEST_DIR/mock-bin/` with dispatch on `$1 $2` (same pattern as mock `gh`)
- `review-state.json` with `pr_url` set to a GitLab MR URL (e.g., `https://gitlab.com/test-group/test-repo/-/merge_requests/42`)
- `IW_SERVER_DISABLED=1` in setup (already present)

### What does NOT need new tests

- `PhaseMerge.evaluateChecks` — already tested, forge-agnostic
- `PhaseMerge.extractPrNumber` — already tested with GitLab MR URLs
- Recovery/retry loop — same logic, already tested in Phase 5
- Timeout — same logic, already tested in Phase 4

## Acceptance criteria

- [ ] `iw phase-merge` works on a GitLab project (tracker.type = gitlab) with `glab` CLI
- [ ] CI pipeline statuses fetched via `glab` CLI and parsed into `CICheckResult` list
- [ ] Same `evaluateChecks` verdict logic applies (no forge-specific decision logic)
- [ ] MR merged with `glab mr merge --remove-source-branch` on CI pass
- [ ] MR URL validated against configured repository for GitLab URLs
- [ ] Feature branch advanced after merge (same as GitHub path)
- [ ] review-state.json updated to `phase_merged` on success
- [ ] CI failure recovery (agent re-invocation) works with GitLab
- [ ] Unit tests pass for glab JSON parsing (all status mappings)
- [ ] E2E BATS test passes for GitLab happy path with mock `glab`
- [ ] E2E BATS test passes for GitLab CI failure scenario
- [ ] Existing GitHub tests still pass (no regressions)
- [ ] The "GitLab not supported" guard and its E2E test are removed
- [ ] Core compiles with `-Werror`
