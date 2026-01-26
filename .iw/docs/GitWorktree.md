# GitWorktree

> Git worktree adapter for creating and managing worktrees.

## Import

```scala
import iw.core.adapters.*
```

## API

### GitWorktreeAdapter.worktreeExists(path: os.Path, workDir: os.Path): Boolean

Check if a worktree exists at the given path.

### GitWorktreeAdapter.branchExists(branchName: String, workDir: os.Path): Boolean

Check if a branch exists.

### GitWorktreeAdapter.createWorktree(path: os.Path, branchName: String, workDir: os.Path): Either[String, Unit]

Create a new worktree with a new branch.

### GitWorktreeAdapter.createWorktreeForBranch(path: os.Path, branchName: String, workDir: os.Path): Either[String, Unit]

Create a worktree for an existing branch.

### GitWorktreeAdapter.removeWorktree(path: os.Path, workDir: os.Path, force: Boolean): Either[String, Unit]

Remove an existing worktree.

## Examples

```scala
// From start.scala - creating a worktree
val targetPath = worktreePath.resolve(currentDir)
val branchName = issueId.toBranchName

// Check for existing worktree
if os.exists(targetPath) then
  Output.error(s"Directory already exists")
  if GitWorktreeAdapter.worktreeExists(targetPath, currentDir) then
    Output.info(s"Use './iw open ${issueId.value}' to open existing worktree")
  sys.exit(1)

// Create worktree (new branch or existing)
val worktreeResult =
  if GitWorktreeAdapter.branchExists(branchName, currentDir) then
    Output.info(s"Using existing branch '$branchName'")
    GitWorktreeAdapter.createWorktreeForBranch(targetPath, branchName, currentDir)
  else
    Output.info(s"Creating new branch '$branchName'")
    GitWorktreeAdapter.createWorktree(targetPath, branchName, currentDir)

worktreeResult match
  case Left(error) => Output.error(error)
  case Right(_) => Output.success(s"Worktree created at ${targetPath}")
```
