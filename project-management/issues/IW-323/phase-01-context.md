# Phase 1: Domain Constants

## Goals

Add new constants to `Constants.scala` for plugin-related environment variables, paths, and command headers needed by subsequent phases.

## Scope

### In Scope
- `Constants.EnvVars.IwPluginDirs` — env var name `IW_PLUGIN_DIRS` (override for plugin directory discovery)
- `Constants.Paths.PluginsDir` — XDG path segment `plugins` (for `$XDG_DATA_HOME/iw/plugins/`)
- `Constants.CommandHeaders.Requires` — header field name `REQUIRES` (for `// REQUIRES:` version gating)

### Out of Scope
- Shell script changes (Phase 2-4)
- E2E tests (Phase 5)

## Dependencies

- None — this is the first phase with no prior phase dependencies.

## Approach

1. Add constants to existing `Constants.scala` following established patterns
2. Add a new `CommandHeaders` object for the `REQUIRES` constant
3. Add unit tests to `ConstantsTest.scala` following existing test patterns
4. Verify compilation with `-Werror`

## Files to Modify

- `.iw/core/model/Constants.scala` — add new constants
- `.iw/core/test/ConstantsTest.scala` — add tests for new constants

## Testing Strategy

Unit tests verifying each new constant has the expected string value, following the existing pattern in `ConstantsTest.scala`.

## Acceptance Criteria

- [ ] `Constants.EnvVars.IwPluginDirs` equals `"IW_PLUGIN_DIRS"`
- [ ] `Constants.Paths.PluginsDir` equals `"plugins"`
- [ ] `Constants.CommandHeaders.Requires` equals `"REQUIRES"`
- [ ] All existing tests still pass
- [ ] Core compiles with `-Werror`
