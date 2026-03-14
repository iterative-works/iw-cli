# Phase 2 Tasks: `write --help` fails with misleading error instead of showing help

- [ ] [impl] [ ] [reviewed] Write failing E2E test reproducing the defect (`--help` should exit 0 with usage, not error or write files)
- [ ] [impl] [ ] [reviewed] Investigate root cause (confirm no `--help` guard in write.scala)
- [ ] [impl] [ ] [reviewed] Implement fix (add `--help`/`-h` early guard and `showHelp()` function)
- [ ] [impl] [ ] [reviewed] Verify fix passes and no regressions (run full test suite)
