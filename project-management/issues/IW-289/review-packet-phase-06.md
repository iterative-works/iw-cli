---
generated_from: 80050035d2914593394e4089024b599aeb59a3dd
generated_at: 2026-03-21T12:26:16Z
branch: IW-289
issue_id: IW-289
phase: 6
files_analyzed:
  - .iw/commands/phase-merge.scala
  - .iw/core/adapters/GitLabClient.scala
  - .iw/core/test/GitLabClientTest.scala
  - .iw/test/phase-merge.bats
---

# Review Packet: Phase 6 — GitLab CI Status Support

## Goals

This phase extends `iw phase-merge` to work with GitLab CI pipelines, removing the GitHub-only restriction. After this phase, `phase-merge` fully supports both GitHub and GitLab forge types: it polls GitLab CI pipeline job statuses via the `glab` CLI, validates GitLab MR URLs, merges MRs with source branch deletion on CI pass, and recovers from CI failures using the same retry logic already proven for GitHub.

Key objectives:
- Add `GitLabClient.fetchCheckStatuses` — fetches pipeline jobs via two-step `glab api` calls (first the MR pipelines list, then the latest pipeline's jobs) and maps them into `List[CICheckResult]`
- Add `GitLabClient.buildMergeMrWithDeleteCommand` — the GitLab equivalent of `gh pr merge --merge --delete-branch`
- Update `phase-merge.scala` to dispatch on `ForgeType` for CI fetching, URL validation, and merge — removing the hard "GitLab not supported" early exit
- Cover all status mappings and edge cases with unit tests; add E2E BATS tests for the GitLab happy path and CI failure scenario

## Scenarios

- [ ] `iw phase-merge` works on a GitLab project (`tracker.type = gitlab`) with `glab` CLI installed and authenticated
- [ ] CI pipeline job statuses are fetched via `glab api` and parsed into `CICheckResult` list
- [ ] GitLab status strings (`success`, `failed`, `running`, `pending`, `created`, `waiting_for_resource`, `preparing`, `manual`, `canceled`/`cancelled`, unknown) map to the correct `CICheckStatus` values
- [ ] When no pipelines exist yet for an MR, the command treats it as no checks found and proceeds to merge
- [ ] MR is merged with `glab mr merge --remove-source-branch` on CI pass
- [ ] MR URL is validated against the configured repository path for GitLab URLs
- [ ] Feature branch is advanced after merge (same as GitHub path)
- [ ] `review-state.json` is updated to `phase_merged` on success
- [ ] CI failure recovery (agent re-invocation) works for GitLab pipelines
- [ ] Existing GitHub tests pass without regression
- [ ] The "GitLab not supported" guard and its E2E test are removed

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/commands/phase-merge.scala` | `poll()` inner function | ForgeType dispatch added here — the core change reviewers need to trace |
| `.iw/commands/phase-merge.scala` | `forgeType match` (URL validation, lines 78–87) | New GitLab MR URL validation replacing the hardcoded GitHub prefix check |
| `.iw/commands/phase-merge.scala` | `mergeCmd` val (line 186) | New forge-dispatch for the merge command itself |
| `.iw/core/adapters/GitLabClient.scala` | `fetchCheckStatuses` | Orchestrates two-step API fetch: pipelines list then jobs |
| `.iw/core/adapters/GitLabClient.scala` | `parseGlabJobsJson` | Parses job status JSON; most of the status-mapping logic lives here |
| `.iw/core/adapters/GitLabClient.scala` | `buildCheckStatusesCommand` | Builds the `glab api projects/:repo/merge_requests/:mr/pipelines` args |
| `.iw/core/adapters/GitLabClient.scala` | `buildMergeMrWithDeleteCommand` | Pure builder for the merge-with-delete command |

## Diagrams

### Component Relationships

```
phase-merge.scala
  │
  ├── ForgeType.GitHub ──► GitHubClient.fetchCheckStatuses
  │                         └── gh pr checks --json
  │
  └── ForgeType.GitLab ──► GitLabClient.fetchCheckStatuses
                            ├── glab api .../merge_requests/:mr/pipelines  (buildCheckStatusesCommand)
                            │   └── parseGlabPipelinesJson  →  pipelineId
                            └── glab api .../pipelines/:id/jobs            (buildPipelineJobsCommand)
                                └── parseGlabJobsJson  →  List[CICheckResult]
```

### GitLab CI Fetch Flow

```
fetchCheckStatuses(mrNumber, repository)
  │
  ├─ validateGlabPrerequisites()
  │    ├─ isCommandAvailable("glab")  →  GlabNotInstalled on miss
  │    └─ glab auth status            →  GlabNotAuthenticated on fail
  │
  ├─ glab api projects/:repo/merge_requests/:mr/pipelines
  │    ├─ Left(error)  →  Left("Failed to fetch pipelines: ...")
  │    └─ Right(json)
  │         └─ parseGlabPipelinesJson(json)
  │              ├─ Left(_)  →  Right(Nil)  [no pipelines yet — treat as no checks]
  │              └─ Right(pipelineId)
  │
  └─ glab api projects/:repo/pipelines/:id/jobs
       ├─ Left(error)  →  Left("Failed to fetch pipeline jobs: ...")
       └─ Right(json)
            └─ parseGlabJobsJson(json)  →  Right(List[CICheckResult])
```

### Status Mapping

```
GitLab status       →  CICheckStatus
─────────────────────────────────────
"success" / "passed" / "skipped"  →  Passed
"failed"                          →  Failed
"canceled" / "cancelled"          →  Cancelled
"pending" / "running" / "created" /
  "waiting_for_resource" /
  "preparing" / "manual"          →  Pending
(anything else)                   →  Unknown
```

## Test Summary

### Unit Tests (`GitLabClientTest.scala`) — new tests added in this phase

| Test | Type | Covers |
|------|------|--------|
| `parseGlabJobsJson -- all jobs passed` | Unit | `success` status → `Passed` |
| `parseGlabJobsJson -- some jobs failed` | Unit | `failed` → `Failed` |
| `parseGlabJobsJson -- running and pending statuses map to Pending` | Unit | `running`/`pending` → `Pending` |
| `parseGlabJobsJson -- canceled (American) maps to Cancelled` | Unit | American spelling |
| `parseGlabJobsJson -- cancelled (British) also maps to Cancelled` | Unit | British spelling |
| `parseGlabJobsJson -- additional pending states: created, waiting_for_resource, preparing, manual` | Unit | All pending variants |
| `parseGlabJobsJson -- skipped maps to Passed (should not block)` | Unit | `skipped` → `Passed` |
| `parseGlabJobsJson -- unknown status string maps to Unknown` | Unit | Unknown fallback |
| `parseGlabJobsJson -- empty array returns Right(Nil)` | Unit | Empty checks list |
| `parseGlabJobsJson -- invalid JSON returns Left` | Unit | Malformed input |
| `parseGlabJobsJson -- JSON object (not array) returns Left` | Unit | Wrong JSON structure |
| `parseGlabJobsJson -- entry missing name field returns Left` | Unit | Missing required field |
| `parseGlabJobsJson -- job URL present returns Some(url)` | Unit | URL extraction |
| `parseGlabJobsJson -- empty web_url returns None` | Unit | Optional URL absent |
| `parseGlabPipelinesJson -- returns first pipeline ID from array` | Unit | Pipeline ID extraction |
| `parseGlabPipelinesJson -- empty array returns Left` | Unit | No pipelines case |
| `parseGlabPipelinesJson -- invalid JSON returns Left` | Unit | Malformed input |
| `buildCheckStatusesCommand produces correct glab CLI args for pipelines API` | Unit | Command builder |
| `buildCheckStatusesCommand URL-encodes slash in repository path` | Unit | `/` → `%2F` encoding |
| `buildPipelineJobsCommand produces correct glab CLI args for jobs API` | Unit | Jobs command builder |
| `buildMergeMrWithDeleteCommand includes --remove-source-branch and MR URL` | Unit | Merge command builder |
| `fetchCheckStatuses -- returns parsed checks on success` | Integration | Full fetch with injected execCommand |
| `fetchCheckStatuses -- returns Left when pipelines command fails` | Integration | Error propagation |
| `fetchCheckStatuses -- returns Right(Nil) when no pipelines found` | Integration | Empty pipeline case |

### E2E Tests (`phase-merge.bats`) — new tests added in this phase

| Test | Type | Covers |
|------|------|--------|
| `phase-merge with GitLab forge type happy path merges MR and updates review-state to phase_merged` | E2E | Full GitLab happy path with mock `glab` |
| `phase-merge with GitLab forge type CI failure exits non-zero and reports failed job name` | E2E | GitLab CI failure scenario with `--max-retries 0` |

Note: the previous test "phase-merge with GitLab forge type exits with not-supported error" has been removed as GitLab is now fully supported.

### Pre-existing tests retained

All GitHub-path tests in `phase-merge.bats` (happy path, CI failure, timeout, poll-interval, max-retries, agent recovery, retries exhausted) remain in place and continue to exercise the GitHub code path.

## Files Changed

| File | Change |
|------|--------|
| `.iw/core/adapters/GitLabClient.scala` | Added `buildCheckStatusesCommand`, `buildPipelineJobsCommand`, `buildMergeMrWithDeleteCommand`, `parseGlabPipelinesJson`, `parseGlabJobsJson`, `mapGitLabJobStatus` (private), and `fetchCheckStatuses` |
| `.iw/commands/phase-merge.scala` | Removed GitLab early-exit guard; replaced hardcoded GitHub URL prefix check with `forgeType match`; replaced hardcoded GitHub CI fetch with `forgeType match`; replaced hardcoded `gh pr merge` with `forgeType match` for merge command |
| `.iw/core/test/GitLabClientTest.scala` | Added 23 unit/integration tests covering all new `GitLabClient` methods |
| `.iw/test/phase-merge.bats` | Replaced "GitLab not supported" test with two GitLab scenario tests (happy path + CI failure) |
| `project-management/issues/IW-289/phase-06-tasks.md` | All tasks marked complete |
| `project-management/issues/IW-289/review-state.json` | Phase 6 state updated |

<details>
<summary>GitLabClient.scala additions summary</summary>

The two-step fetch design (`buildCheckStatusesCommand` for the pipelines list, then `buildPipelineJobsCommand` for the jobs of the latest pipeline) reflects the GitLab API structure: the MR pipelines endpoint returns pipeline metadata but not job details, so a second call is needed. When the pipelines list is empty (MR not yet triggered a pipeline), `fetchCheckStatuses` returns `Right(Nil)` — the caller treats this as `NoChecksFound` and proceeds to merge immediately, consistent with the no-CI-configured behaviour already in the GitHub path.

The `skipped` status is mapped to `Passed` (rather than a separate value) because skipped jobs do not block a pipeline from passing and the existing `CICheckStatus` enum has no `Skipped` variant.

</details>
