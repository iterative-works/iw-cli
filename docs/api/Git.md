# Git

> Git infrastructure adapter for reading repository information.

## Import

```scala
import iw.core.adapters.*
```

## API

### GitAdapter.getRemoteUrl(dir: os.Path): Option[GitRemote]

Get the remote origin URL for a git repository.

### GitAdapter.isGitRepository(dir: os.Path): Boolean

Check if a directory is a git repository.

### GitAdapter.getCurrentBranch(dir: os.Path): Either[String, String]

Get the current branch name.

### GitAdapter.hasUncommittedChanges(path: os.Path): Either[String, Boolean]

Check if the repository has uncommitted changes.

## Examples

```scala
// From doctor.scala - checking git repository
val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
if GitAdapter.isGitRepository(currentDir) then
  CheckResult.Success("Found")
else
  CheckResult.Error("Not found", "Initialize git repository: git init")

// From issue.scala - getting current branch for issue ID inference
val currentDir = os.Path(System.getProperty(Constants.SystemProps.UserDir))
for {
  branch <- GitAdapter.getCurrentBranch(currentDir)
  issueId <- IssueId.fromBranch(branch)
} yield issueId

// Getting remote URL for tracker detection
GitAdapter.getRemoteUrl(os.pwd) match
  case Some(remote) =>
    TrackerDetector.suggestTracker(remote)
  case None =>
    None
```
