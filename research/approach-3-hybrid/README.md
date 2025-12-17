# Approach 3: Hybrid with LLM-friendly Metadata

## Overview

This prototype demonstrates a hybrid approach that combines scala-cli scripts with structured metadata designed for LLM consumption. The key innovation is making commands easily discoverable and understandable by AI agents through multiple discovery mechanisms.

## Structure

```
research/approach-3-hybrid/
├── iw                      # Bootstrap shell script
├── core/                   # Shared library
│   ├── CommandMetadata.scala
│   └── Output.scala
├── commands/               # Command scripts
│   ├── version.scala
│   └── hello.scala
├── commands.index.md       # LLM-readable index of commands
└── README.md               # This file
```

## Key Features

### 1. Structured Command Headers

Each command file has a standardized header with machine-parseable metadata:

```scala
// PURPOSE: Display version information
// USAGE: iw version [--verbose]
// ARGS:
//   --verbose: Show detailed version info including dependencies
// EXAMPLE: iw version
// EXAMPLE: iw version --verbose

//> using scala 3.3.1
//> using dep com.lihaoyi::os-lib:0.9.1

import iw.core.Output

object VersionCommand:
  def main(args: Array[String]): Unit =
    // Implementation
```

### 2. Discovery Mechanisms

The system provides three ways to discover commands:

**a) `./iw --list`** - Command inventory
```bash
$ ./iw --list
Available commands:

Command: hello
Purpose: Display a friendly greeting message
Usage:   iw hello [name]

Command: version
Purpose: Display version information
Usage:   iw version [--verbose]
```

**b) `./iw --describe <command>`** - Detailed documentation
```bash
$ ./iw --describe version
=== Command: version ===

Purpose:
Display version information

Usage:
iw version [--verbose]

Arguments:
--verbose: Show detailed version info including dependencies

Examples:
iw version
iw version --verbose
```

**c) `commands.index.md`** - Comprehensive reference
A markdown file containing all commands with complete documentation, optimized for LLM reading.

### 3. Shared Core Library

Commands can import and use shared utilities from the `core/` directory:

- `CommandMetadata` - Data structures for command metadata parsing
- `Output` - Consistent output formatting utilities

### 4. Zero-Install Bootstrap

The `iw` bootstrap script requires only:
- bash
- scala-cli (automatically downloads dependencies)

No compilation step needed - commands run directly via scala-cli.

## Usage

### Running Commands

```bash
# Show help
./iw

# List all commands
./iw --list

# Describe a command
./iw --describe version

# Run a command
./iw version
./iw version --verbose
./iw hello Michal
```

### Adding New Commands

1. Create `commands/newcmd.scala` with structured header:

```scala
// PURPOSE: Brief description
// USAGE: iw newcmd [args]
// ARGS:
//   arg1: Description
// EXAMPLE: iw newcmd example

//> using scala 3.3.1
//> using dep com.lihaoyi::os-lib:0.9.1

import iw.core.Output

object NewCommand:
  def main(args: Array[String]): Unit =
    Output.success("Command executed")
```

2. Update `commands.index.md` (or regenerate it)

3. The command is immediately available via `./iw newcmd`

## Test Results

All tests passed:

- ✓ Bootstrap script works without arguments
- ✓ `--list` produces structured output
- ✓ `--describe` shows detailed command documentation
- ✓ Commands execute successfully with scala-cli
- ✓ Command arguments are parsed correctly
- ✓ Error handling for unknown commands works
- ✓ Core library utilities are accessible from commands
- ✓ LLM-friendly metadata is consistent and parseable

## Advantages

### For Human Developers

1. **Simple Discovery**: Multiple ways to explore available commands
2. **Self-Documenting**: Each command file contains its own documentation
3. **Fast Development**: No build step, just create a file and run
4. **Consistent Patterns**: Clear conventions make it easy to add commands
5. **IDE Support**: Scala files work with standard IDEs

### For AI Agents

1. **Structured Metadata**: Consistent format makes parsing reliable
2. **Multiple Discovery Paths**: Can use --list, --describe, or read commands.index.md
3. **Clear Examples**: Each command includes usage examples
4. **Pattern Recognition**: Easy to understand the structure and create new commands
5. **Self-Contained Documentation**: Everything needed is in the command file

### For the System

1. **No Compilation**: Commands run directly via scala-cli
2. **Dependency Management**: scala-cli handles dependencies automatically
3. **Shared Code**: Core library promotes code reuse
4. **Extensibility**: Easy to add new commands without modifying bootstrap
5. **Portability**: Works anywhere bash and scala-cli are available

## Limitations and Issues

### 1. First-Run Compilation Delay

**Issue**: Each command compilation takes 2-3 seconds on first run.

**Impact**: Not instant like pure shell scripts.

**Mitigation**: scala-cli caches compiled code, so subsequent runs are fast.

### 2. Scala-cli Dependency

**Issue**: Requires scala-cli to be installed.

**Impact**: Not as universally available as bash/sh.

**Mitigation**: scala-cli is easy to install and handles all Scala/dependency management.

### 3. Verbose Output

**Issue**: scala-cli produces warnings and hints during execution.

**Example**:
```
Warning: setting /path/to/commands as the project root directory
[hint] "os-lib is outdated, update to 0.11.6"
```

**Impact**: Makes output less clean for parsing.

**Mitigation**: Can use `2>/dev/null` to suppress or filter warnings.

### 4. Manual Index Maintenance

**Issue**: `commands.index.md` must be manually updated when adding commands.

**Impact**: Can get out of sync with actual commands.

**Mitigation**: Could create a command like `iw index-generate` to auto-update it.

### 5. Working Directory Sensitivity

**Issue**: scala-cli sets a project root based on where it's run from.

**Impact**: Minor - just produces a warning message.

**Mitigation**: Can be suppressed or we could use a fixed project root.

## LLM Effectiveness

This approach is highly effective for LLM interaction:

### What LLMs Can Do

1. **Discover Commands**: Parse `--list` output or read `commands.index.md`
2. **Understand Usage**: Get complete info via `--describe`
3. **Create Commands**: Follow the clear pattern to add new commands
4. **Debug Issues**: Read source files with headers for troubleshooting
5. **Maintain System**: Update commands by following established patterns

### What Makes It Work

1. **Consistency**: Every command follows the same structure
2. **Self-Documentation**: Headers are in the same file as implementation
3. **Multiple Formats**: Both structured text and markdown available
4. **Clear Examples**: Each command shows actual usage
5. **Discoverable Patterns**: Easy to see what conventions to follow

### Sample LLM Workflow

```
LLM: Let me check what commands are available
→ Reads commands.index.md OR runs ./iw --list

LLM: I need more details on the 'version' command
→ Runs ./iw --describe version

LLM: I'll create a new 'status' command
→ Reads version.scala to see the pattern
→ Creates status.scala following the same structure
→ Tests with ./iw status

LLM: The user wants to know all Git-related commands
→ Parses commands.index.md for commands mentioning Git
→ Returns structured list with descriptions
```

## Comparison to Other Approaches

### vs. Pure Shell Scripts (Approach 1)

**Pros**:
- Type safety and better error messages
- Code reuse via shared library
- IDE support for development

**Cons**:
- Slower first run (compilation)
- Requires scala-cli dependency

### vs. Monolithic CLI (Approach 2)

**Pros**:
- Easier to add commands (no recompilation of main)
- More discoverable (--list, --describe)
- LLM-friendly metadata

**Cons**:
- Slightly slower execution
- More files to manage

## Recommendations

### This Approach is Best For:

1. **Complex Commands**: When type safety and IDE support matter
2. **AI Agent Interaction**: When LLMs need to discover/create commands
3. **Rapid Development**: When you want to add commands without builds
4. **Shared Libraries**: When commands need common utilities
5. **Self-Service**: When users (human or AI) need to understand/extend the system

### Consider Alternatives When:

1. **Performance Critical**: If milliseconds matter on every invocation
2. **Simple Scripts**: If commands are just wrapping shell commands
3. **Minimal Dependencies**: If you can't require scala-cli
4. **Large Scale**: If you have dozens of commands (might want proper build)

## Next Steps

To evolve this prototype into production:

1. **Silence Warnings**: Configure scala-cli to suppress non-critical output
2. **Auto-Generate Index**: Create `iw index-generate` command
3. **Add Tests**: Create test suite for commands
4. **Performance Optimization**: Consider pre-compilation for commonly used commands
5. **Enhanced Metadata**: Add categories, tags, or other classification
6. **Validation**: Add command to validate all headers are properly formatted

## Conclusion

This hybrid approach successfully demonstrates:

- Commands that are self-documenting and discoverable
- Multiple mechanisms for LLMs to understand the system
- Fast development cycle for adding new commands
- Clean separation between bootstrap logic and commands
- Extensible architecture for shared utilities

The prototype is functional and ready for further development. The main trade-offs are:
- Compilation delay vs. type safety/IDE support
- scala-cli dependency vs. better dependency management
- Verbose output vs. helpful hints

For a system where AI agents need to discover and create commands, these trade-offs are worthwhile.
