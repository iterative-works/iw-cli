# Implementation Tasks: Support Plugin Command Directories

**Issue:** IW-323
**Created:** 2026-03-30
**Status:** 2/5 phases complete (40%)

## Phase Index

- [x] Phase 1: Domain constants (Est: 1h) → `phase-01-context.md`
- [x] Phase 2: Plugin discovery and listing (Est: 3-4h) → `phase-02-context.md`
- [ ] Phase 3: Plugin command execution (Est: 3-5h) → `phase-03-context.md`
- [ ] Phase 4: Hooks and version checking (Est: 2-3h) → `phase-04-context.md`
- [ ] Phase 5: E2E tests (Est: 5-8h) → `phase-05-context.md`

## Progress Tracker

**Completed:** 2/5 phases
**Estimated Total:** 14-21 hours
**Time Spent:** 0 hours

## Phase Details

### Phase 1: Domain constants
Add new constants to `Constants.scala` for plugin env vars, paths, and command headers.
- `Constants.EnvVars.IwPluginDirs`
- `Constants.Paths.PluginsDir`
- `Constants.CommandHeaders.Requires`

### Phase 2: Plugin discovery and listing
Extend `iw-run` with plugin directory scanning and `--list` output.
- `discover_plugins()` — scan XDG + env var for plugin dirs
- Extend `list_commands()` — show `Plugin commands (kanon):` section with `kanon/<cmd>` entries
- Extend `describe_command()` — resolve `<plugin>/<cmd>` syntax, show source

### Phase 3: Plugin command execution
Extend `iw-run` to execute plugin commands with lib support.
- Parse `<plugin>/<command>` in `execute_command()`
- Include plugin `lib/*.scala` + `$IW_CORE_DIR` on classpath
- Error handling for unknown plugin/command

### Phase 4: Hooks and version checking
Add hook discovery from plugin dirs and `// REQUIRES:` version gating.
- Scan plugin `hooks/` dirs when running core commands
- Scan project dir for hooks when running plugin commands
- `check_version_requirement()` — parse and compare semver
- Version mismatch error message with upgrade hint

### Phase 5: E2E tests
Comprehensive BATS test suite for all plugin functionality.
- `plugin-discovery.bats`
- `plugin-commands-list.bats`
- `plugin-commands-execute.bats`
- `plugin-commands-describe.bats`
- `plugin-version-check.bats`
- `plugin-hooks.bats`
- Regression: existing tests still pass

## Notes

- Phase context files generated just-in-time during implementation
- Use wf-implement to start next phase automatically
- Estimates are rough and will be refined during implementation
- Phases 2-4 split the iw-run work into logical increments for reviewability
- Phase 5 tests are separate but some tests may be written alongside phases 2-4 via TDD
