# Files in This Prototype

## Core Infrastructure

| File | Lines | Purpose |
|------|-------|---------|
| `iw` | 42 | Bootstrap script that discovers and runs commands |
| `core/Command.scala` | 26 | Shared library with Command trait and helpers |
| `core/project.scala` | 4 | scala-cli configuration for core library |

## Example Commands

### iw-version (12 lines implementation)
- `iw-version/main.scala` - Displays version information
- `iw-version/project.scala` - scala-cli configuration

### iw-hello (13 lines implementation)
- `iw-hello/main.scala` - Greets user with optional name argument
- `iw-hello/project.scala` - scala-cli configuration

### iw-status (15 lines implementation)
- `iw-status/main.scala` - Shows mock worktree status
- `iw-status/project.scala` - scala-cli configuration

## Documentation

| File | Lines | Purpose |
|------|-------|---------|
| `OVERVIEW.md` | 107 | High-level overview and quick start |
| `README.md` | 153 | Technical details, architecture, caching behavior |
| `USAGE.md` | 105 | Usage examples and development workflow |
| `FILES.md` | (this file) | File inventory |

## Testing

| File | Lines | Purpose |
|------|-------|---------|
| `test.sh` | 28 | Basic test suite |
| `final-test.sh` | 56 | Comprehensive test suite |

## Configuration

| File | Purpose |
|------|---------|
| `.gitignore` | Excludes `.scala-build/` cache directories |

## Total Code

- **Bootstrap script**: 42 lines
- **Core library**: 30 lines (26 + 4)
- **Commands**: 40 lines total (3 commands × ~13 lines avg)
- **Tests**: 84 lines
- **Documentation**: 365 lines

**Total implementation code**: ~112 lines (bootstrap + core + commands)

## How to Read This Prototype

1. Start with `OVERVIEW.md` for quick understanding
2. Look at `iw` bootstrap script to see command discovery
3. Examine `core/Command.scala` to see shared code
4. Pick any `iw-*/main.scala` to see a command implementation
5. Read `README.md` for technical deep dive
6. Check `USAGE.md` for practical examples
7. Run `./final-test.sh` to verify everything works

## Key Insight

Each command is just ~13 lines of code because:
- Bootstrap script handles discovery and execution
- Core library provides common functionality
- scala-cli handles all build/dependency management
- No boilerplate needed
