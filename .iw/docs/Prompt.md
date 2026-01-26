# Prompt

> Interactive console prompt utilities for user input.

## Import

```scala
import iw.core.adapters.*
```

## API

### Prompt.ask(question: String, default: Option[String] = None): String

Ask for text input. Shows default value in brackets if provided, and uses it if user presses Enter.

### Prompt.confirm(question: String, default: Boolean = false): Boolean

Ask a yes/no question. Shows [Y/n] or [y/N] based on default. Repeats on invalid input.

## Examples

```scala
// Ask for project name with default
val projectName = Prompt.ask("Project name", Some("my-project"))
// Shows: "Project name [my-project]: "
// User presses Enter -> returns "my-project"
// User types "other" -> returns "other"

// Ask without default
val issueTitle = Prompt.ask("Issue title")
// Shows: "Issue title: "

// Confirm with default yes
if Prompt.confirm("Continue?", default = true) then
  // Shows: "Continue? [Y/n]: "
  Output.info("Proceeding...")
else
  Output.info("Aborted")

// Confirm with default no
if Prompt.confirm("Delete worktree?", default = false) then
  // Shows: "Delete worktree? [y/N]: "
  deleteWorktree()

// Valid inputs: y, yes, Y, YES, n, no, N, NO, or Enter for default
```
