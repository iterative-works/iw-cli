# Approach 2: Git-Style External Commands

## Overview

This prototype demonstrates a git-style command architecture where each command is a separate scala-cli project that can be independently compiled and cached.

## Structure

```
research/approach-2-git-style/
├── iw                      # Bootstrap shell script
├── core/                   # Shared library code
│   ├── Command.scala       # Common interfaces and utilities
│   └── project.scala       # scala-cli configuration
├── iw-version/             # Version command (separate project)
│   ├── main.scala          # Command implementation
│   └── project.scala       # scala-cli configuration + core import
└── iw-hello/               # Hello command (separate project)
    ├── main.scala          # Command implementation
    └── project.scala       # scala-cli configuration + core import
```

## How It Works

1. **Bootstrap Script (`iw`)**:
   - Takes command name as first argument (e.g., `iw version`)
   - Looks for directory named `iw-{command}` (e.g., `iw-version/`)
   - Runs `scala-cli run iw-{command}/` with remaining arguments
   - Falls back to listing available commands if no command provided

2. **Core Library**:
   - Contains shared code (traits, utilities, helpers)
   - Each command imports it via `//> using file ../core/Command.scala`
   - No separate build step needed - scala-cli handles it

3. **Individual Commands**:
   - Each command is a self-contained scala-cli project
   - Has its own `@main` entry point
   - Imports shared core via using directive in `project.scala`
   - Can be compiled and run independently

## Compilation & Caching Behavior

### Independent Compilation
Each command can be compiled independently:
```bash
cd iw-version
scala-cli compile .
```

### Automatic Caching
- scala-cli creates `.scala-build/` directory in each command folder
- First run compiles and caches (takes ~2-3 seconds)
- Subsequent runs use cache (takes ~0.6 seconds)
- Cache is per-command, so changes to one command don't affect others
- Cache is tied to source file contents - changes trigger recompilation
- **Important**: Changes to core library trigger recompilation of all commands that use it (scala-cli tracks file dependencies)

### Cache Location
```
iw-version/.scala-build/
├── .bloop/                          # Bloop build server files
├── ide-*.json                       # IDE integration files
└── iw-version_{hash}/               # Compiled artifacts
```

### Pre-compilation
Commands can be packaged as standalone binaries:
```bash
cd iw-version
scala-cli package . -o iw-version-binary
./iw-version-binary  # Runs instantly, no compilation
```

## Test Results

All requirements verified:

1. **Bootstrap script works**: ✓
   ```bash
   $ ./iw version
   iw version 0.1.0 (prototype)
   ```

2. **Arguments passed correctly**: ✓
   ```bash
   $ ./iw hello "Michal Příhoda"
   Hello, Michal Příhoda!
   ```

3. **Lists commands when no args**: ✓
   ```bash
   $ ./iw
   Available commands:
     hello
     version
   ```

4. **Each command compiles independently**: ✓
   ```bash
   $ cd iw-version && scala-cli compile .
   # Compiles successfully

   # Commands can also be compiled in parallel
   $ (cd iw-version && scala-cli compile .) & (cd iw-hello && scala-cli compile .) & wait
   # Both compile simultaneously
   ```

5. **Error handling for unknown commands**: ✓
   ```bash
   $ ./iw nonexistent
   Error: Unknown command 'nonexistent'

   Available commands:
     hello
     version
   ```

## Performance

- **First run**: ~2-3 seconds (includes compilation)
- **Cached run**: ~0.6 seconds
- **Packaged binary**: Instant (no JVM startup or compilation)

## Advantages

1. **True Independence**: Each command is fully isolated
2. **Efficient Caching**: Only changed commands recompile
3. **Discoverability**: Easy to see available commands (`ls iw-*/`)
4. **Simple Distribution**: Can package individual commands as binaries
5. **No Build Tool**: Uses only scala-cli features
6. **Parallel Development**: Multiple developers can work on different commands without conflicts

## Limitations & Issues Discovered

1. **Startup Time**: Even with caching, ~600ms overhead per invocation
2. **Disk Space**: Each command has its own cache directory (~1-2 MB)
3. **Code Sharing**: Using `//> using file` works but duplicates compilation of core code for each command
4. **No Dependency Management**: If core library grows, might want proper artifact publishing

## Recommendations

1. For frequently used commands, consider pre-packaging as binaries
2. Keep core library minimal to reduce duplication
3. Use `.gitignore` to exclude `.scala-build/` directories
4. Consider setting up a build script to pre-compile all commands for distribution

## Next Steps to Explore

1. Can we publish core as a local Maven artifact to avoid recompilation?
2. How does this scale to 10-20 commands?
3. Can we use scala-cli's workspace feature to share compilation?
4. What's the best way to distribute pre-compiled commands?
