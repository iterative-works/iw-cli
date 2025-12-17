# Approach 4: Combined Best Practices

This prototype combines the best aspects of Approach 1 (script discovery with `//> using file`) and Approach 3 (structured headers and discovery commands).

## Key Features

### From Approach 1: Explicit Dependencies
Commands declare their dependencies using `//> using file` directives:
```scala
//> using scala 3.3.1
//> using file "../core/Output.scala"
```

This means:
- No need to pass core files on command line
- Each command is self-contained
- scala-cli handles the compilation dependencies
- No warnings about duplicate sources

### From Approach 3: Structured Headers
Commands start with structured metadata that can be parsed by the bootstrap:
```scala
// PURPOSE: Display version information
// USAGE: iw version [--verbose]
// ARGS:
//   --verbose: Show detailed version info
// EXAMPLE: iw version
```

This enables:
- `./iw --list` - list all commands with purpose and usage
- `./iw --describe <cmd>` - show full documentation
- LLM-friendly metadata format

### Simple Bootstrap Invocation
Like Approach 1, the bootstrap simply runs:
```bash
scala-cli run "$COMMAND_FILE" -- "$@"
```

The command file itself declares what it needs via `//> using file`.

## Directory Structure

```
approach-4-combined/
├── iw                      # Bootstrap script
├── core/
│   └── Output.scala        # Shared utilities
└── commands/
    ├── version.scala       # Version command
    └── hello.scala         # Hello command
```

## Usage

### Run Commands
```bash
./iw version
./iw hello World
./iw version --verbose
```

### Discovery
```bash
./iw --list              # List all commands
./iw --describe version  # Show full documentation
```

## Command Template

To add a new command, create `commands/mycommand.scala`:

```scala
// PURPOSE: Brief description
// USAGE: iw mycommand [args]
// ARGS:
//   --flag: Description
// EXAMPLE: iw mycommand example

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

object MyCommand:
  def main(args: Array[String]): Unit =
    // Your command logic here
    Output.info("Hello from my command!")
```

The command is immediately available without any registration or bootstrap changes.

## Goals

This approach aims to achieve:
1. Clean command execution without compilation warnings
2. Fast cached performance (~0.5-0.6s)
3. Simple command discovery
4. LLM-friendly metadata
5. Easy command development
