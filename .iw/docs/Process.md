# Process

> Shell command execution adapter with output capture and timeout support.

## Import

```scala
import iw.core.adapters.*
```

## API

### ProcessResult

```scala
case class ProcessResult(
  exitCode: Int,
  stdout: String,
  stderr: String,
  truncated: Boolean = false,
  timedOut: Boolean = false
)
```

### ProcessAdapter.commandExists(command: String): Boolean

Check if a command is available in PATH.

### ProcessAdapter.run(command: Seq[String], maxOutputBytes: Int = 1024*1024, timeoutMs: Int = 300000): ProcessResult

Execute a command and capture output. Default timeout is 5 minutes.

### ProcessAdapter.runStreaming(command: Seq[String], timeoutMs: Int = 300000): Int

Execute a command with streaming output to console. Returns exit code.

## Examples

```scala
// Check if a command exists
if ProcessAdapter.commandExists("gh") then
  Output.success("GitHub CLI found")
else
  Output.error("GitHub CLI not found")

// Run a command and check result
val result = ProcessAdapter.run(Seq("git", "status", "--porcelain"))
if result.exitCode == 0 then
  val hasChanges = result.stdout.trim.nonEmpty
  if hasChanges then
    Output.warning("Uncommitted changes detected")
else
  Output.error(s"Git error: ${result.stderr}")

// Run with streaming output (for interactive commands)
val exitCode = ProcessAdapter.runStreaming(Seq("npm", "install"))
if exitCode != 0 then
  Output.error("npm install failed")
```
