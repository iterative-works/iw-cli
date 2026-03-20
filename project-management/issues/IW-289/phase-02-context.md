# Phase 2: PR number extraction from review-state

**Issue:** IW-289
**Phase:** 2 of 7
**Story:** PR number extraction from review-state (Story 7 from analysis)

## Goals

Add a pure function to extract a PR/MR number from a URL string stored in `review-state.json`. This is prerequisite plumbing for Phase 3 (GitHub CI polling), which needs the PR number to query check statuses via `gh pr checks <number>`.

## Scope

### In Scope

- Pure function `extractPrNumber(url: String): Either[String, Int]` in `PhaseMerge` object
- Handles GitHub PR URLs: `https://github.com/{owner}/{repo}/pull/{number}`
- Handles GitLab MR URLs: `https://{host}/{path}/-/merge_requests/{number}`
- Clear error messages for unparseable URLs
- Comprehensive unit tests

### Out of Scope

- Reading review-state.json from disk (that's adapter-level, done in Phase 3)
- Any I/O or file access
- Modifying the `ReviewState` model class (pr_url is already handled as raw JSON)
- GitHub/GitLab CI status querying (Phase 3/6)

## Dependencies

- Phase 1 complete (PhaseMerge object exists in `.iw/core/model/PhaseMerge.scala`)
- Existing URL patterns in `GitHubClient.parseCreatePrResponse` and `GitLabClient.parseCreateMrResponse` serve as reference

## Approach

1. **TDD:** Add failing tests to `PhaseMergeTest.scala` first, then implement
2. **Add to existing `PhaseMerge` object** — this is a small pure function, not worth a separate file
3. **Use regex patterns** matching the established patterns in the codebase:
   - GitHub: `https://github.com/.+/pull/(\d+)` (from `GitHubClient.scala:533`)
   - GitLab: `https://.+/-/merge_requests/(\d+)` (from `GitLabClient.scala:414`)
4. **Return `Either[String, Int]`** — Left for error message, Right for extracted number

**Design decisions:**
- Return `Int` not `String` for the PR number — it's always numeric and typed is safer
- Single function handles both GitHub and GitLab — try GitHub pattern first, then GitLab
- Empty/blank input → Left with clear error
- URL that matches neither pattern → Left with the URL included for diagnostics

## Files to Create/Modify

### Modify

- `.iw/core/model/PhaseMerge.scala` — Add `extractPrNumber` function to `PhaseMerge` object
- `.iw/core/test/PhaseMergeTest.scala` — Add test cases for PR number extraction

### No new files

## Testing Strategy

Pure unit tests only, extending existing `PhaseMergeTest.scala`.

**GitHub URL scenarios:**
- Standard URL `https://github.com/owner/repo/pull/42` → Right(42)
- URL with org/repo path `https://github.com/iterative-works/iw-cli/pull/290` → Right(290)
- URL with trailing slash or whitespace → Right(number)

**GitLab URL scenarios:**
- Standard URL `https://gitlab.com/group/project/-/merge_requests/15` → Right(15)
- Self-hosted GitLab `https://git.company.com/team/project/-/merge_requests/7` → Right(7)
- Nested group `https://gitlab.com/group/subgroup/project/-/merge_requests/99` → Right(99)

**Error scenarios:**
- Empty string → Left with error
- Blank/whitespace-only string → Left with error
- Random non-URL string → Left with error containing the input
- URL to different service (e.g., Bitbucket) → Left with error
- URL with non-numeric PR number → Left with error
- `null`-like edge cases (Scala handles this via type system)

**Verification commands:**
- `scala-cli compile --scalac-option -Werror .iw/core/` — must pass with no warnings
- `./iw test unit` — all tests pass

## Acceptance Criteria

- [ ] `PhaseMerge.extractPrNumber(url: String): Either[String, Int]` exists
- [ ] Correctly extracts PR number from GitHub URLs
- [ ] Correctly extracts MR number from GitLab URLs
- [ ] Returns Left with descriptive error for invalid/empty inputs
- [ ] All new tests pass
- [ ] Existing Phase 1 tests still pass
- [ ] Code compiles with `-Werror`
