# Phase 2 Tasks: PR number extraction from review-state

**Issue:** IW-289
**Phase:** 2 of 7

## Setup

- [ ] [setup] Read existing `PhaseMerge.scala` and `PhaseMergeTest.scala` to understand current structure

## Tests First (TDD)

- [ ] [test] Add test: GitHub PR URL `https://github.com/owner/repo/pull/42` → Right(42)
- [ ] [test] Add test: GitHub PR URL with real org `https://github.com/iterative-works/iw-cli/pull/290` → Right(290)
- [ ] [test] Add test: GitLab MR URL `https://gitlab.com/group/project/-/merge_requests/15` → Right(15)
- [ ] [test] Add test: Self-hosted GitLab URL `https://git.company.com/team/project/-/merge_requests/7` → Right(7)
- [ ] [test] Add test: Nested GitLab group `https://gitlab.com/group/sub/project/-/merge_requests/99` → Right(99)
- [ ] [test] Add test: Empty string → Left with error
- [ ] [test] Add test: Whitespace-only string → Left with error
- [ ] [test] Add test: Non-URL string → Left with error containing input
- [ ] [test] Add test: URL with trailing whitespace → Right(number)
- [ ] [test] Run tests to confirm they fail (function doesn't exist yet)

## Implementation

- [ ] [impl] Add `extractPrNumber(url: String): Either[String, Int]` to `PhaseMerge` object
- [ ] [impl] Run tests to confirm they pass
- [ ] [impl] Compile with `-Werror` to verify no warnings
- [ ] [impl] Run full unit test suite (`./iw test unit`)

## Verification

- [ ] [verify] All new tests pass
- [ ] [verify] All existing Phase 1 tests still pass
- [ ] [verify] Code compiles clean with `-Werror`
