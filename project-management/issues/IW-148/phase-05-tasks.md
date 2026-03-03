# Phase 5: CLI Integration — Tasks

## Helper Function

- [ ] [impl] [ ] [reviewed] Extract `TrackerUrlBuilder.buildTrackerUrl(config)` pure function into `model/` (reusable by CLI commands; mirrors `MainProjectService.buildTrackerUrl` logic)
- [ ] [impl] [ ] [reviewed] Test: `TrackerUrlBuilder` returns correct URL for each tracker type

## register.scala

- [ ] [impl] [ ] [reviewed] Test: `register` from main branch (no issue ID) calls `ServerClient.registerProject`
- [ ] [impl] [ ] [reviewed] Update `register.scala` to detect context: if `IssueId.fromBranch` fails, register project instead of worktree
- [ ] [impl] [ ] [reviewed] Test: `register` from issue worktree also registers parent project
- [ ] [impl] [ ] [reviewed] Update `register.scala` to also register parent project when on issue branch

## start.scala

- [ ] [impl] [ ] [reviewed] Test: `start` auto-registers parent project alongside worktree
- [ ] [impl] [ ] [reviewed] Update `start.scala` to call `ServerClient.registerProject` after worktree registration

## projects.scala

- [ ] [impl] [ ] [reviewed] Test: `projects` includes registered projects with zero worktrees
- [ ] [impl] [ ] [reviewed] Update `projects.scala` to merge `state.projects` into project listing

## Verification

- [ ] [impl] [ ] [reviewed] Run `./iw test unit` — all tests pass
- [ ] [impl] [ ] [reviewed] No compilation warnings
- [ ] [impl] [ ] [reviewed] Existing tests show no regression

**Phase Status:** Not Started
