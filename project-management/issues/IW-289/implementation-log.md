# Implementation Log: Add `iw phase-merge` command

Issue: IW-289

This log tracks the evolution of implementation across phases.

---

## Phase 1: Pure decision logic for CI check results (2026-03-20)

**What was built:**
- Domain types: `CICheckStatus` enum (5 values with `isTerminalFailure` helper), `CICheckResult` case class, `CIVerdict` enum (5 cases), `PhaseMergeConfig` case class
- Decision functions in `PhaseMerge` object: `evaluateChecks`, `shouldRetry`, `buildRecoveryPrompt`
- All code is pure — no I/O imports

**Decisions made:**
- `Unknown` status treated as non-blocking (passes through to AllPassed) — rationale: unknown checks shouldn't block merge
- `Cancelled` treated as terminal failure alongside `Failed` — rationale: cancelled checks indicate problems
- `TimedOut` verdict exists in enum but is not produced by `evaluateChecks` — it will be set by the polling loop caller in Phase 4
- `evaluateChecks` uses `partition` for single-pass classification with clear precedence

**Patterns applied:**
- Follows `BatchImplement.scala` pattern: pure object with decision functions, no I/O
- Follows `DoctorChecks.scala` pattern: enum with associated data on cases
- `isTerminalFailure` method on `CICheckStatus` centralizes failure classification

**Testing:**
- Unit tests: 29 tests added (27 original + 2 from review feedback)
- Covers all verdict paths, retry boundaries, recovery prompt format, edge cases

**Code review:**
- Iterations: 1 (no critical issues)
- Review file: review-phase-01-20260320-205057.md
- Applied fixes: added `isTerminalFailure`, refactored to `partition`, added exact-output and empty-list tests, commented `TimedOut`

**For next phases:**
- `PhaseMerge.evaluateChecks` is ready for Phase 3 (GitHub CI polling) to call with parsed check data
- `PhaseMerge.shouldRetry` is ready for Phase 5 (failure recovery loop)
- `PhaseMerge.buildRecoveryPrompt` is ready for Phase 5 (agent re-invocation)
- `PhaseMergeConfig` defaults are ready for Phase 4 (timeout/polling configuration)

**Files changed:**
```
A	.iw/core/model/PhaseMerge.scala
A	.iw/core/test/PhaseMergeTest.scala
```

---

## Phase 2: PR number extraction from review-state (2026-03-20)

**What was built:**
- `PhaseMerge.extractPrNumber(url: String): Either[String, Int]` — pure function to extract PR/MR number from GitHub and GitLab URLs
- Two private regex patterns matching established codebase conventions (from `GitHubClient` and `GitLabClient`)

**Decisions made:**
- Single function handles both GitHub and GitLab URLs — try GitHub pattern first, then GitLab
- Return `Either[String, Int]` — Left for descriptive error, Right for numeric PR/MR number
- Whitespace trimming before matching — matches existing codebase URL handling behavior
- Scala regex `unapply` provides full-string matching, so no explicit end anchors needed (verified at runtime)

**Patterns applied:**
- Extends existing `PhaseMerge` object rather than creating a new file — keeps all phase-merge pure logic together
- Regex patterns follow `GitHubClient.parseCreatePrResponse` and `GitLabClient.parseCreateMrResponse` conventions

**Testing:**
- Unit tests: 11 new tests (total 40 in PhaseMergeTest)
- Covers: GitHub URLs (standard, real org), GitLab URLs (standard, self-hosted, nested groups), whitespace handling (leading, trailing), error cases (empty, whitespace-only, non-URL, unsupported forge)

**Code review:**
- Iterations: 1 (no critical issues)
- Review file: review-phase-02-20260320-214121.md
- Applied fixes: improved test assertion style (use `assertEquals`/`assert(result.isLeft)` instead of manual match), added leading whitespace test, verified error message content for blank inputs

**For next phases:**
- `PhaseMerge.extractPrNumber` is ready for Phase 3 (GitHub CI polling) to extract PR number from review-state.json `pr_url` field
- Same function handles GitLab MR URLs for Phase 6

**Files changed:**
```
M	.iw/core/model/PhaseMerge.scala
M	.iw/core/test/PhaseMergeTest.scala
```

---

## Phase 3: GitHub CI polling and auto-merge (2026-03-20)

**What was built:**
- `phase-merge.scala` command: polls CI checks for a PR, waits for completion, auto-merges on success, advances feature branch
- `GitHubClient.fetchCheckStatuses` adapter: calls `gh pr checks --json name,state,link`, parses JSON into `List[CICheckResult]`
- `GitHubClient.buildCheckStatusesCommand` and `buildMergePrWithDeleteCommand`: pure command builders
- `ReviewStateAdapter.readPrUrl`: typed read-side accessor for pr_url from review-state.json
- `PhaseOutput.MergeOutput`: JSON output type for merge results
- 7 BATS E2E tests covering happy path, CI failure, wrong branch, missing PR URL, GitLab detection, timeout

**Decisions made:**
- `parseGhChecksJson` placed in `GitHubClient` adapter (not model) — JSON wire format is infrastructure, not domain logic. Code review caught the initial misplacement
- PR URL validated against configured repository before merge — security review identified that a crafted review-state.json could redirect merge to any accessible repo
- Merge uses `--merge --delete-branch` (merge commit, not squash) — per analysis decision to avoid history rewriting on phase branches
- `NoChecksFound` → immediate merge — design decision for repos without CI
- `CIVerdict.TimedOut` kept in enum but not produced by polling loop — the loop handles timeout via wall-clock check; `TimedOut` is reserved for Phase 4
- Polling loop uses `@annotation.tailrec` to avoid stack overflow during long waits

**Patterns applied:**
- Follows `phase-pr.scala` pattern: branch detection, forge type resolution, review-state updates
- Follows `GitHubClient` injected `execCommand` pattern for testability
- FCIS layering: pure model types → adapter I/O → command orchestration
- Mock `gh` scripts in BATS with positional dispatch (`$1`/`$2`) and call log verification

**Testing:**
- Unit tests: 8 new tests in GitHubClientTest (JSON parsing via fetchCheckStatuses, command builders)
- E2E tests: 7 BATS tests covering all major scenarios
- Total tests passing: 2021

**Code review:**
- Iterations: 1 (3 critical issues found and fixed)
- Review file: review-phase-03-20260320-221933.md
- Critical fixes: moved parseGhChecksJson to adapter, added PR URL repository validation, extracted readPrUrl to ReviewStateAdapter
- Additional fixes: PURPOSE comment, PhaseOutput comment, removed unused bucket field, improved E2E assertions

**For next phases:**
- `phase-merge` command is ready for Phase 4 (configurable timeout/polling via CLI args)
- `phase-merge` command is ready for Phase 5 (CI failure recovery loop — currently exits on failure)
- `GitHubClient.fetchCheckStatuses` is ready for Phase 6 (GitLab equivalent via glab)
- `ReviewStateAdapter.readPrUrl` is reusable by any command needing PR URL from review-state

**Files changed:**
```
A	.iw/commands/phase-merge.scala
M	.iw/core/adapters/GitHubClient.scala
M	.iw/core/adapters/ReviewStateAdapter.scala
M	.iw/core/model/PhaseOutput.scala
M	.iw/core/test/GitHubClientTest.scala
M	.iw/core/test/PhaseMergeTest.scala
A	.iw/test/phase-merge.bats
```

---

## Phase 4: Timeout and configurable polling (2026-03-21)

**What was built:**
- `PhaseMerge.parseDuration(input: String): Either[String, Long]` — parses "30s", "5m", "2h" to milliseconds
- `PhaseMerge.formatDuration(ms: Long): String` — converts milliseconds to human-readable duration
- CLI flags `--timeout` and `--poll-interval` wired into `phase-merge.scala`
- Improved timeout handling: sets review-state `activity: "waiting"` before exit

**Decisions made:**
- Bare numbers without suffix (e.g., "30") are rejected — require explicit s/m/h suffix for clarity
- `formatDuration(0L)` returns "0s" — zero is valid but treated as seconds
- Duration parsing moved to the very start of `phaseMerge` main, before config/branch checks — invalid `--timeout abc` exits immediately with a clear parse error
- `formatDuration` picks the most natural unit: hours if divisible by 3,600,000, minutes if divisible by 60,000, else seconds

**Patterns applied:**
- Pure functions in model (`parseDuration`, `formatDuration`) — no I/O, trivially testable
- Existing `PhaseArgs.namedArg` pattern for CLI argument extraction
- `ReviewStateUpdater.UpdateInput(activity = Some("waiting"))` for timeout state transition

**Testing:**
- Unit tests: 15 new tests (9 for parseDuration, 6 for formatDuration) — total 2036
- E2E tests: 3 new BATS tests (timeout scenario, custom poll interval, invalid duration flag)

**Code review:**
- Iterations: 1 (no critical issues)
- Applied fixes: consistent `formatDuration` usage in polling message, stronger E2E assertions for poll-interval test, updated PURPOSE comments

**For next phases:**
- `phase-merge` command is ready for Phase 5 (CI failure recovery — currently exits on SomeFailed)
- `parseDuration` and `formatDuration` are reusable by any command needing duration CLI args
- `activity: "waiting"` state is set on timeout, ready for dashboard to display

**Files changed:**
```
M	.iw/commands/phase-merge.scala
M	.iw/core/model/PhaseMerge.scala
M	.iw/core/test/PhaseMergeTest.scala
M	.iw/test/phase-merge.bats
```

---
