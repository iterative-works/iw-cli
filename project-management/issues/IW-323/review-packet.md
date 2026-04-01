# Review Packet: IW-323 — Support Plugin Command Directories

**Branch:** IW-323
**Commit:** 73fe8ac
**Phases:** 5/5 complete

## Goals

Enable iw-cli to discover and execute commands from external plugin directories, allowing platform-specific commands (e.g., kanon) to integrate without embedding in core.

Key deliverables:
- Plugin discovery via XDG auto-discovery (`~/.local/share/iw/plugins/*/`)
- Namespaced invocation: `iw kanon/implement`
- Plugin `lib/` classpath isolation
- Bidirectional hook discovery (plugin→core, project→plugin)
- `// REQUIRES: iw-cli >= X.Y.Z` version gating

## Scenarios

- [ ] Plugin discovered via `$XDG_DATA_HOME/iw/plugins/*/`
- [ ] Plugin discovered via `$IW_PLUGIN_DIRS` env var
- [ ] `iw --list` shows plugin commands in `Plugin commands (<name>):` sections
- [ ] `iw kanon/implement` executes plugin command with core + lib classpath
- [ ] `iw --describe kanon/implement` shows metadata with source attribution
- [ ] Plugin hooks run when executing core commands
- [ ] Project hooks run when executing plugin commands
- [ ] Version check gates execution when `// REQUIRES:` is unsatisfied
- [ ] Invalid plugin/command syntax gives clear errors
- [ ] Existing core and project commands unaffected (regression)

## Entry Points

Start reviewing from these files:

1. **`iw-run`** (lines 300-550) — All plugin logic lives here:
   - `discover_plugins()` — scans XDG + env var for plugin dirs
   - `list_commands()` extension — plugin sections in `--list`
   - `describe_command()` extension — `<plugin>/<command>` resolution
   - `execute_command()` extension — plugin command execution with classpath
   - `read_iw_version()`, `compare_versions()`, `check_version_requirement()` — version gating
   - `extract_hook_classes()` generalization — supports any object name

2. **`.iw/core/model/Constants.scala`** — New constants:
   - `EnvVars.IwPluginDirs`, `Paths.PluginsDir`, `CommandHeaders.Requires`

3. **`.iw/VERSION`** — Single source of truth for version string

4. **`.iw/commands/version.scala`** — Reads version from `.iw/VERSION` at runtime

## Test Summary

**53 plugin-specific BATS tests** across 6 files:

| Test File | Tests | Coverage |
|-----------|-------|----------|
| `plugin-discovery.bats` | 7 | XDG, env var, error resilience, precedence |
| `plugin-commands-list.bats` | 8 | Section headers, format, hook/lib exclusion, ordering |
| `plugin-commands-describe.bats` | 5 | Metadata, error cases |
| `plugin-commands-execute.bats` | 10 | Execution, args, classpath, errors |
| `plugin-hooks.bats` | 8 | Plugin→core hooks, project→plugin hooks |
| `version-check.bats` | 15 | read/compare/check version functions |

**Unit tests:** 3 munit tests for new Constants.

**Regression:** 28 existing project-commands tests pass, all other E2E tests pass.

## Diff Summary

- **27 files changed**, 3,081 insertions, 5 deletions
- Core production changes: `iw-run` (+371 lines), `Constants.scala` (+6), `version.scala` (+5), `VERSION` (new)
- Tests: 6 new BATS files (965 lines total), 1 updated unit test file
- Project management: analysis, tasks, phase contexts, reviews, state

## Architecture

```
Plugin Directory Layout:
~/.local/share/iw/plugins/
└── kanon/                    (symlink to plugin source)
    ├── commands/*.scala      (discovered by iw-run)
    ├── lib/*.scala           (compiled with plugin commands)
    └── hooks/*.hook-*.scala  (extend core commands)

Command Namespaces:
  (none)      → core shared commands     (iw start)
  <plugin>/   → plugin commands          (iw kanon/implement)
  ./          → project-local commands   (iw ./test)
```

## Phase History

| Phase | Layer | PR | Review |
|-------|-------|-----|--------|
| 1 | Domain constants | #325 | Pass (1 iter) |
| 2 | Plugin discovery & listing | #326 | Pass (1 iter) |
| 3 | Plugin command execution | #327 | Pass (1 iter) |
| 4 | Hooks & version checking | #328 | Pass (2 iter) |
| 5 | E2E tests | N/A (done in phases 2-4) | All pass |
