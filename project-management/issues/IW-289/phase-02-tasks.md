# Phase 2 Tasks: PR number extraction from review-state

**Issue:** IW-289
**Phase:** 2 of 7

## Setup

- [x] [setup] Read existing `PhaseMerge.scala` and `PhaseMergeTest.scala` to understand current structure

## Tests First (TDD)

- [x] [test] Add test: GitHub PR URL `https://github.com/owner/repo/pull/42` → Right(42)
- [x] [test] Add test: GitHub PR URL with real org `https://github.com/iterative-works/iw-cli/pull/290` → Right(290)
- [x] [test] Add test: GitLab MR URL `https://gitlab.com/group/project/-/merge_requests/15` → Right(15)
- [x] [test] Add test: Self-hosted GitLab URL `https://git.company.com/team/project/-/merge_requests/7` → Right(7)
- [x] [test] Add test: Nested GitLab group `https://gitlab.com/group/sub/project/-/merge_requests/99` → Right(99)
- [x] [test] Add test: Empty string → Left with error
- [x] [test] Add test: Whitespace-only string → Left with error
- [x] [test] Add test: Non-URL string → Left with error containing input
- [x] [test] Add test: URL with trailing whitespace → Right(number)
- [x] [test] Run tests to confirm they fail (function doesn't exist yet)

## Implementation

- [x] [impl] Add `extractPrNumber(url: String): Either[String, Int]` to `PhaseMerge` object
- [x] [impl] Run tests to confirm they pass
- [x] [impl] Compile with `-Werror` to verify no warnings
- [x] [impl] Run full unit test suite (`./iw test unit`)

## Verification

- [x] [verify] All new tests pass
- [x] [verify] All existing Phase 1 tests still pass
- [x] [verify] Code compiles clean with `-Werror`
**Phase Status:** Complete
