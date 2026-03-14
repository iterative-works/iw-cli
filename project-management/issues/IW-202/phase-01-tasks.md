# Phase 1 Tasks: `update --help` mutates state instead of showing help

- [ ] [impl] [ ] [reviewed] Write failing E2E test reproducing the defect (`--help` should exit 0 with usage, not mutate state)
- [ ] [impl] [ ] [reviewed] Investigate root cause (confirm no `--help` guard in update.scala)
- [ ] [impl] [ ] [reviewed] Implement fix (add `--help`/`-h` early guard and `showHelp()` function)
- [ ] [impl] [ ] [reviewed] Verify fix passes and no regressions (run full test suite)
