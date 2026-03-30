# Phase 1 Tasks: Domain Constants

## Setup
- [ ] [setup] Read existing Constants.scala and ConstantsTest.scala

## Tests (TDD - write first)
- [ ] [test] Add test for `Constants.EnvVars.IwPluginDirs` equals `"IW_PLUGIN_DIRS"`
- [ ] [test] Add test for `Constants.Paths.PluginsDir` equals `"plugins"`
- [ ] [test] Add test for `Constants.CommandHeaders.Requires` equals `"REQUIRES"`

## Implementation
- [ ] [impl] Add `IwPluginDirs` to `Constants.EnvVars` object
- [ ] [impl] Add `PluginsDir` to `Constants.Paths` object
- [ ] [impl] Add new `Constants.CommandHeaders` object with `Requires` field

## Verification
- [ ] [verify] Compile core with `-Werror`
- [ ] [verify] Run unit tests
