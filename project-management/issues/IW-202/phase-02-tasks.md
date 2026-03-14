# Phase 2 Tasks: `write --help` fails with misleading error instead of showing help

- [x] [impl] [x] [reviewed] Write failing E2E test reproducing the defect (`--help` should exit 0 with usage, not error or write files)
- [x] [impl] [x] [reviewed] Investigate root cause (confirm no `--help` guard in write.scala)
- [x] [impl] [x] [reviewed] Implement fix (add `--help`/`-h` early guard and `showHelp()` function)
- [x] [impl] [x] [reviewed] Verify fix passes and no regressions (run full test suite)
**Phase Status:** Complete
