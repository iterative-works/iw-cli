# Approach 1: Script Discovery - Findings

## Overview

Successfully created a working prototype that demonstrates script discovery with a shared library. The approach uses scala-cli to run individual command scripts that share common code through `//> using file` directives.

## What Works

### 1. Command Discovery
- Bootstrap script (`iw`) automatically discovers all `.scala` files in `commands/` directory
- No registration needed - just add a new file and it appears
- Command listing extracts descriptions by parsing the source files with grep

### 2. Shared Code
- Commands import shared code via `//> using file ../core/Command.scala`
- Works without needing to publish or install packages
- All commands share the same `Command` trait interface

### 3. Argument Passing
- Arguments pass through cleanly from shell script to scala-cli to command
- Exit codes propagate correctly
- Error handling works as expected

### 4. Adding New Commands
- Demonstrated by adding `status.scala` command
- No changes to bootstrap script or other commands needed
- Immediately available after file creation

## Issues and Limitations Discovered

### 1. Compilation Overhead
**Severity: High**

Every command invocation triggers a full compilation:
```
$ time ./iw version
iw-cli version 0.1.0-SNAPSHOT
Compiling project (Scala 3.3.1, JVM (21))
Compiled project (Scala 3.3.1, JVM (21))

real    0m4.521s
```

This is a 4+ second delay for even the simplest command. For a CLI tool, this is unacceptable.

**Potential Solutions:**
- scala-cli does cache compiled artifacts, but still takes 2-3 seconds on cache hits
- Could pre-compile all commands to JARs (loses simplicity of script approach)
- Could use native-image compilation (complex setup, longer build times)

### 2. Command Metadata Extraction
**Severity: Medium**

Currently using grep to extract descriptions from source files:
```bash
grep -E "def description.*=.*\"" "$cmd_file" | sed -E 's/.*"(.*)".*/\1/'
```

**Issues:**
- Fragile - breaks if description spans multiple lines
- Can't extract other metadata (usage, arguments, etc.)
- No type safety

**Better Approaches:**
- Could run command with `--help` flag and parse output
- Could generate a metadata file at build time
- Could use reflection (but adds complexity)

### 3. Dependency Management
**Severity: Low**

Each command file lists its own dependencies:
```scala
//> using scala "3.3.1"
//> using file "../core/Command.scala"
//> using file "../core/Config.scala"
```

**Issues:**
- Repetitive - every command needs same boilerplate
- Easy to forget dependencies
- Changes to core require updating all commands

**Potential Solutions:**
- Could use a shared project file
- Could have commands import from a published artifact

### 4. No Shared State or Context
**Severity: Medium**

Commands are completely independent scripts. There's no way to:
- Share parsed configuration between commands
- Build up a context object
- Have middleware/interceptors
- Implement global flags (like --verbose)

The bootstrap script would need significant enhancement to support these features.

### 5. Error Messages
**Severity: Low**

The compilation output goes to stderr and appears after the actual command output:
```
Hello, World!
Compiling project (Scala 3.3.1, JVM (21))
Compiled project (Scala 3.3.1, JVM (21))
```

This is confusing UX. Could be suppressed but then users won't know why there's a delay.

## Performance Comparison

### Cold Start (no cache)
```
$ rm -rf .scala-build
$ time ./iw version
real    0m4.521s
```

### Warm Start (with cache)
```
$ time ./iw version
real    0m2.134s
```

Even with caching, 2+ seconds is too slow for a CLI tool. Most CLI tools respond in <100ms.

## Architecture Assessment

### Strengths
1. **Simplicity**: Adding commands is trivial - just add a file
2. **Flexibility**: Each command is independent, can have different dependencies
3. **No Build Step**: Commands run directly from source
4. **Easy to Understand**: Bootstrap script is ~70 lines of bash

### Weaknesses
1. **Performance**: 2-4 second startup time is unacceptable
2. **Limited Sharing**: Hard to share runtime state/context
3. **Fragile Metadata**: grep-based description extraction
4. **No Composition**: Can't easily compose commands or add middleware

## Recommendation

This approach works for the basic use case but has significant limitations:

**Use this approach if:**
- Startup time doesn't matter (e.g., long-running commands)
- Commands are truly independent
- Simplicity is the top priority

**Don't use this approach if:**
- Need sub-second command execution
- Need shared context/state between commands
- Need sophisticated argument parsing
- Need command composition

For a developer tool that will be run frequently (like git), the performance overhead makes this approach impractical despite its simplicity.

## Suggested Improvements for This Approach

If we wanted to continue with this approach, here are ways to improve it:

1. **Pre-compilation**: Add a `compile-all` command that pre-compiles all commands to JARs
2. **Metadata File**: Generate a `commands.json` at compile time with all metadata
3. **Shared Project**: Use a `project.scala` file to reduce boilerplate in each command
4. **Native Image**: Compile to native binaries for instant startup (but complex)

## Test Results

All tests passed:
- ✓ `./iw version` - shows version
- ✓ `./iw hello World` - greets with argument
- ✓ `./iw hello` - shows error for missing argument
- ✓ `./iw` - lists all commands with descriptions
- ✓ `./iw nonexistent` - shows error for unknown command
- ✓ Adding `status.scala` - immediately available without changes
