# Phase 1 Context: Bootstrap script runs tool via scala-cli

**Issue:** IWLE-72
**Phase:** 1 of 7
**Status:** Ready for Implementation

## Goals

Establish the foundation for running iw-cli by creating a POSIX-compliant shell bootstrap script that discovers and runs commands via scala-cli. This architecture supports dynamic command creation by AI agents.

## Scope

### In Scope
- Bootstrap shell script (`iw`) with command discovery and execution
- Core output utilities for consistent formatting
- Version command (`version`) to verify the tool runs correctly
- Help command (`--help`) with usage information
- Discovery commands (`--list`, `--describe`)

### Out of Scope
- Actual command implementations (init, doctor, start, open, rm, issue) - just stubs
- Configuration file parsing
- Git integration
- Tmux integration
- Issue tracker integration

## Dependencies

- **External:** scala-cli must be installed on the system
- **None from previous phases** (this is Phase 1)

## Technical Approach

### Architecture Decision

Based on research (see `research/COMPARISON.md` and `research/approach-4-combined/`), we use a **command discovery pattern** with:

1. **Structured headers** - Each command has machine-parseable metadata
2. **`//> using file`** - Commands declare their own dependencies
3. **Simple invocation** - Bootstrap runs `scala-cli run "$COMMAND_FILE" -- "$@"`

### Shell Script (`iw`)

1. Check if scala-cli is available
2. If not, print error with installation instructions and exit 1
3. Handle special flags (`--list`, `--describe`, `--help`)
4. For commands: find command file and invoke via scala-cli

### Command File Structure

Each command is a self-contained `.scala` file with structured headers:

```scala
// PURPOSE: Brief description of what the command does
// USAGE: iw command [args]
// ARGS:
//   --flag: Description of flag
// EXAMPLE: iw command example

//> using scala 3.3.1
//> using file "../core/Output.scala"

object CommandName:
  def main(args: Array[String]): Unit =
    // Implementation
```

### Project Structure

```
project-root/
├── iw                           # POSIX shell bootstrap script (executable)
├── .iw/
│   ├── core/
│   │   └── Output.scala         # Shared output utilities
│   └── commands/
│       ├── version.scala        # Version command
│       ├── init.scala           # Init command (stub)
│       ├── doctor.scala         # Doctor command (stub)
│       ├── start.scala          # Start command (stub)
│       ├── open.scala           # Open command (stub)
│       ├── rm.scala             # Remove command (stub)
│       └── issue.scala          # Issue command (stub)
```

## Files to Create

| File | Purpose |
|------|---------|
| `iw` | Bootstrap shell script with discovery |
| `.iw/core/Output.scala` | Shared output formatting utilities |
| `.iw/commands/version.scala` | Version command (fully implemented) |
| `.iw/commands/init.scala` | Init command (stub) |
| `.iw/commands/doctor.scala` | Doctor command (stub) |
| `.iw/commands/start.scala` | Start command (stub) |
| `.iw/commands/open.scala` | Open command (stub) |
| `.iw/commands/rm.scala` | Remove command (stub) |
| `.iw/commands/issue.scala` | Issue command (stub) |

## Testing Strategy

### Unit Tests
- Output utility formatting
- Structured header parsing (in bootstrap tests)

### Integration Tests
- Shell script exits with error if scala-cli not found
- Shell script discovers commands correctly
- Commands execute with proper argument passing

### E2E Tests (manual verification scenarios)
- `./iw version` outputs version string
- `./iw --help` outputs help text
- `./iw --list` shows all available commands
- `./iw --describe version` shows command documentation
- `./iw unknown` outputs error and suggests --list
- Missing scala-cli shows clear installation instructions

## Acceptance Criteria

From analysis.md Story 7:
- [ ] Shell script is POSIX-compliant (works on Linux, macOS)
- [ ] Invokes scala-cli with correct source paths
- [ ] First run compiles (handled by scala-cli)
- [ ] Subsequent runs use scala-cli's built-in compilation cache (~0.5-0.6s)
- [ ] Clear error if scala-cli not installed

Additional criteria:
- [ ] `./iw version` outputs "iw-cli version 0.1.0"
- [ ] `./iw --help` shows usage information
- [ ] `./iw --list` shows all commands with PURPOSE/USAGE
- [ ] `./iw --describe <cmd>` shows full command documentation
- [ ] Unknown commands return non-zero exit code
- [ ] Script is executable (chmod +x)
- [ ] Command stubs return exit code 0 with "Not implemented" message

## Notes

- Keep the shell script focused on discovery and invocation
- scala-cli handles compilation caching automatically
- Commands declare their own dependencies via `//> using file`
- Structured headers enable LLM discoverability
- Reference implementation: `research/approach-4-combined/`
