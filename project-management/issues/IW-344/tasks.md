# Implementation Tasks: Layer 0 — Pre-compile core into jar and update iw-run launcher

**Issue:** IW-344
**Created:** 2026-04-17
**Status:** 2/3 phases complete (67%)

## Phase Index

- [x] Phase 1: Core jar build and mtime check (Est: 2-3h) → `phase-01-context.md`
- [x] Phase 2: Update command execution to use jar (Est: 2-3h) → `phase-02-context.md`
- [ ] Phase 3: E2E test adaptation (Est: 3-5h) → `phase-03-context.md`

## Progress Tracker

**Completed:** 2/3 phases
**Estimated Total:** 7-11 hours
**Time Spent:** 0 hours

## Phase Details

### Phase 1: Core jar build and mtime check
- Add `build_core_jar()` function to `iw-run` using `scala-cli --power package --library`
- Add `core_jar_stale()` mtime check using `find -newer`
- Auto-rebuild with "Rebuilding core jar..." message to stderr when stale or missing
- Add `IW_CORE_JAR` env var support (check first, default to `$INSTALL_DIR/build/iw-core.jar`)
- Update `bootstrap()` to call `build_core_jar()` instead of compiling version.scala with core files

### Phase 2: Update command execution to use jar
- Update shared command invocation (line ~673-683) to use `--jar "$CORE_JAR" "$CORE_DIR/project.scala"` instead of `$core_files`
- Update plugin command invocation (line ~571-599) same pattern
- Update project command invocation (line ~509-516) same pattern
- Remove `core_files` find-and-collect logic from all three paths
- Ensure `build_core_jar()` is called before execution when jar is stale/missing

### Phase 3: E2E test adaptation
- Update `test.scala` to pre-build jar once and export `IW_CORE_JAR`
- Update BATS test setup functions that copy core sources into temp dirs
- Add new BATS tests for jar build/rebuild behavior (missing jar, stale jar, bootstrap)
- Verify all existing BATS tests pass with jar-based execution
- Key regression indicator: `project-commands-execute.bats` (imports core types)

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Spike confirmed: `scala-cli run <cmd> core/project.scala --jar build/iw-core.jar` is the recipe
- Cold-start speedup: ~27x (29.6s → 1.1s)
