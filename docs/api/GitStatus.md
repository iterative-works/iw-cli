# GitStatus

> Git repository status with branch name and working directory state.

## Import

```scala
import iw.core.model.*
```

## API

### GitStatus

```scala
case class GitStatus(
  branchName: String,    // Current branch (e.g., "main", "IWLE-123")
  isClean: Boolean       // True if no uncommitted changes
):
  def statusIndicator: String   // "clean" or "uncommitted"
  def statusCssClass: String    // "git-clean" or "git-dirty"
```

## Examples

```scala
// Check repository status
val status = GitStatus("IWLE-123", isClean = true)
status.statusIndicator  // "clean"
status.statusCssClass   // "git-clean"

// Display status
if status.isClean then
  Output.success(s"Branch ${status.branchName} is clean")
else
  Output.warning(s"Branch ${status.branchName} has uncommitted changes")
```
