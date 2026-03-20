---
generated_from: 087d128a369761451e430db90cd8b37cc05b341d
generated_at: 2026-03-20T20:35:13Z
branch: IW-289-phase-02
issue_id: IW-289
phase: 2
files_analyzed:
  - .iw/core/model/PhaseMerge.scala
  - .iw/core/test/PhaseMergeTest.scala
---

# Review Packet: Phase 2 - PR Number Extraction from Review-State

## Goals

This phase adds a pure function to extract a PR or MR number from a URL string. It is
prerequisite plumbing for Phase 3 (GitHub CI polling), which needs the PR number to query
check statuses via `gh pr checks <number>`.

Key objectives:

- Add `PhaseMerge.extractPrNumber(url: String): Either[String, Int]` to the existing
  `PhaseMerge` object in `.iw/core/model/`
- Support GitHub PR URLs (`/pull/N`) and GitLab MR URLs (`/-/merge_requests/N`),
  including self-hosted GitLab instances and nested groups
- Return a typed `Int` on success and a descriptive error message on failure
- Provide comprehensive unit tests for all URL shapes and error paths

No new files are introduced. This phase is a targeted addition to the Phase 1 foundation.

## Scenarios

- [ ] GitHub PR URL extracts the PR number as `Right(N)`
- [ ] GitLab MR URL on gitlab.com extracts the MR number as `Right(N)`
- [ ] GitLab MR URL on a self-hosted instance extracts the MR number as `Right(N)`
- [ ] GitLab MR URL with nested groups extracts the MR number as `Right(N)`
- [ ] URL with leading or trailing whitespace is handled correctly
- [ ] Empty string returns `Left` with a descriptive error
- [ ] Whitespace-only string returns `Left` with a descriptive error
- [ ] Non-URL string returns `Left` with the input included in the error message
- [ ] URL from an unsupported forge (e.g., Bitbucket) returns `Left` with the URL included
- [ ] All 29 Phase 1 tests continue to pass

## Entry Points

| File | Symbol | Why Start Here |
|------|--------|----------------|
| `.iw/core/model/PhaseMerge.scala` | `PhaseMerge.extractPrNumber` | The entire Phase 2 addition; 8 lines including two private regex patterns |
| `.iw/core/test/PhaseMergeTest.scala` | Lines 212–274 | The 10 new tests grouped under `// extractPrNumber` comments; directly map to acceptance criteria |

## Diagrams

### Where `extractPrNumber` Sits in the Object

```
object PhaseMerge
  ├── evaluateChecks(...)   ← Phase 1
  ├── shouldRetry(...)      ← Phase 1
  ├── buildRecoveryPrompt(...)  ← Phase 1
  │
  ├── private githubPrPattern   ← Phase 2
  ├── private gitlabMrPattern   ← Phase 2
  └── extractPrNumber(url)      ← Phase 2
```

### Decision Flow — `extractPrNumber`

```
url: String
     │
     ▼
  trim
     │
     ▼
  isEmpty? ──yes──→ Left("URL must not be blank")
     │
    no
     │
     ▼
  matches github pattern?
  https://github.com/.+/pull/(\d+)
     │ yes
     ▼
  Right(n.toInt)

     │ no
     ▼
  matches gitlab pattern?
  https://.+/-/merge_requests/(\d+)
     │ yes
     ▼
  Right(n.toInt)

     │ no
     ▼
  Left(s"Unrecognised PR/MR URL: $trimmed")
```

### URL Pattern Coverage

```
GitHub:   https://github.com/{owner}/{repo}/pull/{N}
GitLab:   https://{any-host}/{any/path}/-/merge_requests/{N}
           └─ covers gitlab.com, self-hosted, and nested groups
Not matched: Bitbucket, Azure DevOps, or any other forge
```

## Test Summary

All tests are pure unit tests. No mocking, no I/O, no fixtures.

### Phase 2 Tests (new)

| Test | Type | Covers |
|------|------|--------|
| `extractPrNumber extracts number from standard GitHub PR URL` | Unit | GitHub happy path |
| `extractPrNumber extracts number from real GitHub org PR URL` | Unit | Real org/repo path (`iterative-works/iw-cli`) |
| `extractPrNumber extracts number from standard GitLab MR URL` | Unit | GitLab happy path |
| `extractPrNumber extracts number from self-hosted GitLab MR URL` | Unit | Custom hostname |
| `extractPrNumber extracts number from nested GitLab group MR URL` | Unit | Deep path (`group/sub/project`) |
| `extractPrNumber handles URL with trailing whitespace` | Unit | Whitespace trimming |
| `extractPrNumber returns Left for empty string` | Unit | Blank input guard |
| `extractPrNumber returns Left for whitespace-only string` | Unit | Blank input guard |
| `extractPrNumber returns Left with input in message for non-URL string` | Unit | Error message includes input for diagnostics |
| `extractPrNumber returns Left for Bitbucket URL` | Unit | Unsupported forge rejected |

**Phase 2 total: 10 unit tests.**

### Cumulative Test Count

Phase 1 contributed 29 tests (see review-packet-phase-01.md for full listing). All 39 tests
pass after Phase 2 changes. Integration and E2E tests are not applicable — this is a pure
function with no I/O.

## Files Changed

| File | Status | Description |
|------|--------|-------------|
| `.iw/core/model/PhaseMerge.scala` | Modified | Added two private regex vals (`githubPrPattern`, `gitlabMrPattern`) and the `extractPrNumber` function to the existing `PhaseMerge` object |
| `.iw/core/test/PhaseMergeTest.scala` | Modified | Added 10 unit tests for `extractPrNumber` covering GitHub URLs, GitLab URLs (standard, self-hosted, nested groups), whitespace handling, and error cases |

No new files were created. No existing behaviour was changed.
