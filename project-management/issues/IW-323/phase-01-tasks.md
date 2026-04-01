# Phase 1 Tasks: Domain Constants

## Setup
- [x] [setup] Read existing Constants.scala and ConstantsTest.scala

## Tests (TDD - write first)
- [x] [test] Add test for `Constants.EnvVars.IwPluginDirs` equals `"IW_PLUGIN_DIRS"`
- [x] [test] Add test for `Constants.Paths.PluginsDir` equals `"plugins"`
- [x] [test] Add test for `Constants.CommandHeaders.Requires` equals `"REQUIRES"`

## Implementation
- [x] [impl] Add `IwPluginDirs` to `Constants.EnvVars` object
- [x] [impl] Add `PluginsDir` to `Constants.Paths` object
- [x] [impl] Add new `Constants.CommandHeaders` object with `Requires` field

## Verification
- [x] [verify] Compile core with `-Werror`
- [x] [verify] Run unit tests
**Phase Status:** Complete
