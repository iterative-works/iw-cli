# Output

> Console output utilities for consistent command output formatting.

## Import

```scala
import iw.core.output.*
```

## API

### Output.info(message: String): Unit

Print an informational message to stdout.

### Output.error(message: String): Unit

Print an error message to stderr with "Error: " prefix.

### Output.warning(message: String): Unit

Print a warning message to stdout with "Warning: " prefix.

### Output.success(message: String): Unit

Print a success message to stdout with checkmark prefix.

### Output.section(title: String): Unit

Print a section header with visual separators.

### Output.keyValue(key: String, value: String): Unit

Print a key-value pair with consistent alignment (20-char key width).

## Examples

```scala
// From issue.scala - error handling
result match
  case Right((issue, issueId, config)) =>
    val formatted = IssueFormatter.format(issue)
    println(formatted)
  case Left(error) =>
    Output.error(error)
    sys.exit(1)

// From start.scala - progress messages
Output.info(s"Creating worktree ${worktreePath.directoryName}...")
Output.info(s"Creating new branch '$branchName'")
Output.success(s"Worktree created at ${targetPath}")
Output.warning(s"Failed to register worktree with dashboard: $error")

// From doctor.scala - section output
Output.section("Environment Check")
Output.keyValue("Git repository", "Found")
Output.keyValue("Configuration", ".iw/config.conf valid")

// Typical error flow
if args.isEmpty then
  Output.error("Missing issue ID")
  Output.info("Usage: iw start <issue-id>")
  sys.exit(1)
```
