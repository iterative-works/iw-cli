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
