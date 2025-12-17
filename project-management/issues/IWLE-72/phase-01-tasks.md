# Phase 1 Tasks: Bootstrap script runs tool via scala-cli

**Issue:** IWLE-72
**Phase:** 1 of 7
**Status:** Ready for Implementation

## Setup Tasks

- [x] [impl] Create project directory structure (`.iw/core/`, `.iw/commands/`)
- [x] [impl] Create `.gitignore` with `.iw/cache/` and scala-cli build artifacts

## Core Output Utilities

- [x] [test] Write tests for Output utility formatting functions (covered by E2E tests - simple wrappers)
- [x] [impl] Create `.iw/core/Output.scala` with info, error, success, section, keyValue helpers

## Bootstrap Shell Script

- [x] [test] Write shell script test: exits with error if scala-cli not found
- [x] [test] Write shell script test: `--help` shows usage information
- [x] [test] Write shell script test: `--list` lists available commands
- [x] [test] Write shell script test: `--describe <cmd>` shows command documentation
- [x] [test] Write shell script test: unknown command returns non-zero exit code
- [x] [test] Write shell script test: executes valid command with arguments
- [x] [impl] Create `iw` bootstrap shell script with scala-cli detection
- [x] [impl] Add `--help` handling with usage information
- [x] [impl] Add `--list` handling with command discovery
- [x] [impl] Add `--describe <cmd>` handling with header parsing
- [x] [impl] Add command execution via scala-cli run
- [x] [impl] Make `iw` script executable (chmod +x)

## Version Command

- [x] [test] Write test: version command outputs version string
- [x] [test] Write test: version command with `--verbose` shows extended info
- [x] [impl] Create `.iw/commands/version.scala` with structured headers
- [x] [impl] Implement version output (basic and verbose modes)

## Command Stubs

- [x] [impl] Create `.iw/commands/init.scala` stub
- [x] [impl] Create `.iw/commands/doctor.scala` stub
- [x] [impl] Create `.iw/commands/start.scala` stub
- [x] [impl] Create `.iw/commands/open.scala` stub
- [x] [impl] Create `.iw/commands/rm.scala` stub
- [x] [impl] Create `.iw/commands/issue.scala` stub

## Integration Verification

- [x] [test] E2E: `./iw version` outputs "iw-cli version 0.1.0"
- [x] [test] E2E: `./iw --help` shows usage information
- [x] [test] E2E: `./iw --list` shows all commands with PURPOSE/USAGE
- [x] [test] E2E: `./iw --describe version` shows full command documentation
- [x] [test] E2E: `./iw unknown` exits with non-zero code and suggests --list
- [x] [test] E2E: Command stubs return exit code 0 with "Not implemented" message

## Notes

- Reference implementation in `research/approach-4-combined/`
- Commands declare dependencies via `//> using file`
- Structured headers enable LLM discoverability:
  ```scala
  // PURPOSE: Brief description
  // USAGE: iw command [args]
  // ARGS:
  //   --flag: Description
  // EXAMPLE: iw command example
  ```
