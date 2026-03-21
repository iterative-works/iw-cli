# Phase 3 Tasks: phase-advance does not commit review-state.json update

## Investigation & Fix Tasks

- [ ] [impl] [ ] [reviewed] Write failing E2E test reproducing the defect (BATS test: run phase-advance with mocked gh, verify git status is dirty)
- [ ] [impl] [ ] [reviewed] Implement fix: add git stage + commit after ReviewStateAdapter.update() in phase-advance.scala (reuse pattern from Phase 1/2)
- [ ] [impl] [ ] [reviewed] Verify fix passes and no regressions (run failing test, then full test suite)
