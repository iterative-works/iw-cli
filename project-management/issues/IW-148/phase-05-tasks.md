# Phase 5: CLI Integration — Tasks

## Helper Function

- [x] [impl] [x] [reviewed] Extract `TrackerUrlBuilder.buildTrackerUrl(config)` pure function into `model/` (reusable by CLI commands; mirrors `MainProjectService.buildTrackerUrl` logic)
- [x] [impl] [x] [reviewed] Test: `TrackerUrlBuilder` returns correct URL for each tracker type

## register.scala

- [x] [impl] [x] [reviewed] Test: `register` from main branch (no issue ID) calls `ServerClient.registerProject`
- [x] [impl] [x] [reviewed] Update `register.scala` to detect context: if `IssueId.fromBranch` fails, register project instead of worktree
- [x] [impl] [x] [reviewed] Test: `register` from issue worktree also registers parent project
- [x] [impl] [x] [reviewed] Update `register.scala` to also register parent project when on issue branch

## start.scala

- [x] [impl] [x] [reviewed] Test: `start` auto-registers parent project alongside worktree
- [x] [impl] [x] [reviewed] Update `start.scala` to call `ServerClient.registerProject` after worktree registration

## projects.scala

- [x] [impl] [x] [reviewed] Test: `projects` includes registered projects with zero worktrees
- [x] [impl] [x] [reviewed] Update `projects.scala` to merge `state.projects` into project listing

## Verification

- [x] [impl] [x] [reviewed] Run `./iw test unit` — all tests pass
- [x] [impl] [x] [reviewed] No compilation warnings
- [x] [impl] [x] [reviewed] Existing tests show no regression

**Phase Status:** Complete
