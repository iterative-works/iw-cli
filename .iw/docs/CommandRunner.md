# CommandRunner

> Command execution utilities with stdout/stderr separation.

## Import

```scala
import iw.core.adapters.*
```

## API

### CommandRunner.execute(command: String, args: Array[String], workingDir: Option[String] = None): Either[String, String]

Execute a command and return stdout on success, error message with stderr on failure.

### CommandRunner.isCommandAvailable(command: String): Boolean

Check if a command is available in PATH.

## Examples

```scala
// Execute a git command
CommandRunner.execute("git", Array("status", "--porcelain")) match
  case Right(output) =>
    if output.isEmpty then
      Output.success("Working directory clean")
    else
      Output.info(s"Changes: $output")
  case Left(error) =>
    Output.error(error)

// Execute with working directory
CommandRunner.execute("npm", Array("install"), Some("/path/to/project"))

// Check command availability before use
if CommandRunner.isCommandAvailable("gh") then
  // Use gh CLI
else
  Output.error("GitHub CLI not installed")
```

## Notes

`CommandRunner` is used internally by API clients (GitHubClient, GitLabClient) with dependency injection for testability. For general shell execution, prefer `ProcessAdapter` which provides more control over output handling.
