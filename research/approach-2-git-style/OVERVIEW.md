# Approach 2: Git-Style External Commands - Overview

## What This Prototype Demonstrates

This is a working implementation of a git-style CLI where each subcommand is a separate scala-cli project that can be independently developed, compiled, and cached.

## Quick Start

```bash
# List available commands
./iw

# Run commands
./iw version
./iw hello "World"
./iw status

# Run tests
./final-test.sh
```

## File Structure

```
approach-2-git-style/
├── iw                    # Bootstrap script (discovers and runs commands)
├── core/                 # Shared library (imported by all commands)
│   ├── Command.scala
│   └── project.scala
├── iw-version/          # Independent command project
│   ├── main.scala
│   └── project.scala
├── iw-hello/            # Independent command project
│   ├── main.scala
│   └── project.scala
├── iw-status/           # Independent command project
│   ├── main.scala
│   └── project.scala
├── README.md            # Technical documentation
├── USAGE.md             # Usage examples
└── final-test.sh        # Comprehensive test suite
```

## Key Features Demonstrated

1. **Command Discovery**: Bootstrap script automatically finds all `iw-*` directories
2. **Independent Compilation**: Each command is a separate scala-cli project
3. **Shared Code**: Core library imported via `//> using file` directive
4. **Automatic Caching**: scala-cli caches compiled commands (~0.6s startup)
5. **Easy Extension**: Add new commands by creating new directory
6. **Parallel Compilation**: Commands can be compiled simultaneously
7. **Error Handling**: Unknown commands show helpful error messages

## Performance

- **First run**: ~2-3 seconds (compilation + execution)
- **Cached run**: ~0.6 seconds (from cache)
- **Packaged binary**: Instant (no compilation or JVM startup)

## Verification

All requirements from the task have been verified:

- [x] `./iw version` works
- [x] `./iw hello World` works
- [x] `./iw` lists commands
- [x] Each command compiles independently
- [x] Compilation behavior documented in README.md

## Documentation

- **README.md**: Technical details, architecture, caching behavior, performance
- **USAGE.md**: Examples of running, adding, and developing commands
- **This file**: High-level overview

## Testing

Run the comprehensive test suite:

```bash
./final-test.sh
```

Tests verify:
- Command listing
- Command execution
- Argument passing
- Error handling
- Independent compilation
- Caching behavior

## Limitations Found

1. **Startup Time**: Even cached, ~600ms overhead due to JVM startup
2. **Core Library Duplication**: Each command recompiles core library (could use published artifact)
3. **Disk Space**: Each command has ~1-2 MB cache directory

## Next Questions to Explore

1. Can we publish core as a local Maven artifact to avoid duplication?
2. How does this scale to 10-20 commands?
3. Should frequently-used commands be pre-packaged as binaries?
4. Can scala-cli workspace feature help with shared compilation?

## Recommendation

This approach works well and meets all requirements. The independent compilation and caching make it practical for a CLI with multiple commands. The main trade-off is startup time vs. development simplicity.
