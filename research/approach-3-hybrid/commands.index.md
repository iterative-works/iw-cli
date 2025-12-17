# iw-cli Commands Index

This index provides a comprehensive reference of all available commands in iw-cli. It is designed to be easily parsed by both humans and AI agents.

## Available Commands

### hello

**Purpose:** Display a friendly greeting message

**Usage:** `iw hello [name]`

**Arguments:**
- `name`: Optional name to greet (defaults to "World")

**Examples:**
```bash
iw hello
iw hello Michal
```

**Implementation:** `/commands/hello.scala`

---

### version

**Purpose:** Display version information

**Usage:** `iw version [--verbose]`

**Arguments:**
- `--verbose`: Show detailed version info including dependencies

**Examples:**
```bash
iw version
iw version --verbose
```

**Implementation:** `/commands/version.scala`

---

## Command Structure

All commands follow this pattern:

1. **File Location:** `commands/<command-name>.scala`
2. **Header Format:** Structured comments at the top of the file:
   ```scala
   // PURPOSE: <one-line description>
   // USAGE: iw <command> [args...]
   // ARGS:
   //   <arg-name>: <description>
   // EXAMPLE: iw <command> <example-args>
   ```
3. **Dependencies:** Commands can use the shared core library via `//> using dep`
4. **Execution:** Commands are executed via scala-cli by the bootstrap script

## Creating New Commands

To add a new command:

1. Create a new file in `commands/<name>.scala`
2. Add the structured header with PURPOSE, USAGE, ARGS, and EXAMPLE
3. Include `//> using scala 3.3.1` and any dependencies
4. Implement the command logic in a main method
5. Update this index file (or regenerate it)

Example template:
```scala
// PURPOSE: Brief description of what this command does
// USAGE: iw <command> [options]
// ARGS:
//   --option: Description of option
// EXAMPLE: iw <command> --option value

//> using scala 3.3.1
//> using dep com.lihaoyi::os-lib:0.9.1

import iw.core.Output

object MyCommand:
  def main(args: Array[String]): Unit =
    // Implementation here
    Output.success("Command executed")
```

## Discovery Tools

- `./iw --list`: List all available commands with basic info
- `./iw --describe <command>`: Show detailed documentation for a specific command
- This file (`commands.index.md`): Complete reference in markdown format

## Design for AI Agents

This command system is optimized for AI agent interaction:

1. **Structured Headers:** Consistent format makes parsing reliable
2. **Self-Documenting:** Each command file contains its own documentation
3. **Discovery Mechanism:** Multiple ways to explore available commands
4. **Clear Patterns:** New commands follow established conventions
5. **LLM-Friendly Output:** Both the bootstrap script and this index use clear, parseable formats

An AI agent can:
- Read this index to understand all available commands
- Use `--list` to get current command inventory
- Use `--describe` to get detailed info on specific commands
- Create new commands by following the documented pattern
- Parse command headers directly from source files if needed
