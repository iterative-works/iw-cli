---
name: iw-command-creation
description: Create new iw-cli commands following project conventions. Use when adding a new command to .iw/commands/, implementing CLI functionality, or when asked to create an iw subcommand.
---

# Creating iw-cli Commands

## File Location

Commands are Scala files in `.iw/commands/`:
```
.iw/commands/<command-name>.scala
```

## File Structure

Every command file follows this structure:

### 1. Header Comments (PURPOSE lines)

```scala
// PURPOSE: Brief description of what the command does
// PURPOSE: Additional detail about behavior (optional)
```

### 2. scala-cli Directives

```scala
//> using scala 3.3.1
//> using file "../core/Output.scala"
//> using file "../core/Config.scala"
// ... other core dependencies
```

Only include the core modules actually needed by the command.

### 3. Imports

```scala
import iw.core.*
import java.nio.file.{Files, Paths}  // if needed
```

### 4. Top-level Helper Functions (if needed)

Define helper functions at top-level, before the `@main` entry point:

```scala
def parseArgs(args: List[String]): (Option[String], Boolean) =
  val forceFlag = args.contains("--force")
  val issueIdArg = args.find(arg => !arg.startsWith("--"))
  (issueIdArg, forceFlag)
```

### 5. Entry Point with @main Annotation

```scala
@main def commandName(args: String*): Unit =
  // command implementation
```

**Important conventions:**
- Use `@main` annotation (NOT `object` with `def main`)
- Function name matches the command name (e.g., `rm`, `start`, `open`)
- Parameter is `args: String*` (varargs), not `Array[String]`
- Use `sys.exit(1)` for errors, `sys.exit(0)` for early success

## Available Core Modules

Located in `.iw/core/`:

| Module | Purpose |
|--------|---------|
| `Output.scala` | Console output (info, error, success, warning, section, keyValue) |
| `Config.scala` | Configuration types (ProjectConfiguration, IssueTrackerType) |
| `ConfigRepository.scala` | Read/write config files |
| `IssueId.scala` | Parse and validate issue IDs |
| `WorktreePath.scala` | Calculate worktree paths and session names |
| `Git.scala` | Git operations (branch, remote, uncommitted changes) |
| `GitWorktree.scala` | Git worktree operations |
| `Tmux.scala` | Tmux session management |
| `Process.scala` | Shell command execution |
| `Prompt.scala` | Interactive prompts (confirm, ask) |
| `DeletionSafety.scala` | Safety checks for destructive operations |
| `DoctorChecks.scala` | Health check registration |
| `LinearClient.scala` | Linear API client |

## Example: Simple Command

```scala
// PURPOSE: Display version information

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

val iwVersion = "0.1.0"

@main def version(args: String*): Unit =
  val verbose = args.contains("--verbose")

  if verbose then
    Output.section("iw-cli Version Information")
    Output.keyValue("Version", iwVersion)
  else
    Output.info(s"iw-cli version $iwVersion")
```

## Example: Command with Helpers

```scala
// PURPOSE: Remove a worktree for a completed issue
// PURPOSE: Kills tmux session and removes worktree with safety checks

//> using scala 3.3.1
//> using file "../core/Output.scala"
//> using file "../core/Config.scala"
//> using file "../core/ConfigRepository.scala"
//> using file "../core/IssueId.scala"
// ... other dependencies

import iw.core.*
import java.nio.file.Paths

def parseArgs(args: List[String]): (Option[String], Boolean) =
  val forceFlag = args.contains("--force")
  val issueIdArg = args.find(arg => !arg.startsWith("--"))
  (issueIdArg, forceFlag)

def removeWorktree(issueId: IssueId, force: Boolean): Unit =
  // implementation...

@main def rm(args: String*): Unit =
  val (issueIdArg, forceFlag) = parseArgs(args.toList)

  issueIdArg match
    case None =>
      Output.error("Missing issue ID")
      Output.info("Usage: ./iw rm <issue-id> [--force]")
      sys.exit(1)
    case Some(rawId) =>
      IssueId.parse(rawId) match
        case Left(error) =>
          Output.error(error)
          sys.exit(1)
        case Right(issueId) =>
          removeWorktree(issueId, forceFlag)
```

## Doctor Hook Pattern

For commands requiring external dependencies, create a companion hook file:

```
.iw/commands/<command>.hook-doctor.scala
```

Example `start.hook-doctor.scala`:
```scala
// PURPOSE: Doctor check for start command - validates tmux installation

//> using file "../core/DoctorChecks.scala"
//> using file "../core/Config.scala"
//> using file "../core/Process.scala"

import iw.core.*

object StartHookDoctor:
  def checkTmux(config: ProjectConfiguration): CheckResult =
    if ProcessAdapter.commandExists("tmux") then
      CheckResult.Success("Installed")
    else
      CheckResult.Error("Not found", Some("Install: sudo apt install tmux"))

  // Registration executes when object is initialized
  DoctorChecks.register("tmux")(checkTmux)
```

Note: Hook-doctor files use `object` pattern (not `@main`) because they register checks on initialization, not as entry points.

## Testing

1. **Unit tests** for core logic go in `.iw/core/test/`
2. **Integration tests** for commands go in `.iw/test/` using bats

Run tests:
```bash
scala-cli test .iw/core/test/*.scala  # unit tests
bats .iw/test/                         # integration tests
```

## Output Conventions

Use `Output` methods consistently:
- `Output.info(msg)` - General information
- `Output.error(msg)` - Error messages (prints to stderr)
- `Output.success(msg)` - Success with checkmark
- `Output.warning(msg)` - Warning messages
- `Output.section(title)` - Section headers with formatting
- `Output.keyValue(key, value)` - Formatted key-value pairs
