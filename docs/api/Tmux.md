# Tmux

> Tmux adapter for session management operations.

## Import

```scala
import iw.core.adapters.*
```

## API

### TmuxAdapter.sessionExists(name: String): Boolean

Check if a tmux session with the given name exists.

### TmuxAdapter.createSession(name: String, workDir: os.Path): Either[String, Unit]

Create a new detached tmux session in the given directory.

### TmuxAdapter.attachSession(name: String): Either[String, Unit]

Attach to an existing tmux session (when outside tmux).

### TmuxAdapter.switchSession(name: String): Either[String, Unit]

Switch to an existing tmux session (when already inside tmux).

### TmuxAdapter.killSession(name: String): Either[String, Unit]

Kill an existing tmux session.

### TmuxAdapter.isInsideTmux: Boolean

Check if currently running inside a tmux session.

### TmuxAdapter.currentSessionName: Option[String]

Get current tmux session name if inside tmux.

### TmuxAdapter.isCurrentSession(sessionName: String): Boolean

Check if the given session name matches the current session.

## Examples

```scala
// From start.scala - creating and joining tmux session
val sessionName = worktreePath.sessionName

// Check for existing session
if TmuxAdapter.sessionExists(sessionName) then
  Output.error(s"Tmux session '$sessionName' already exists")
  Output.info(s"Use './iw open ${issueId.value}' to attach")
  sys.exit(1)

// Create session
Output.info(s"Creating tmux session '$sessionName'...")
TmuxAdapter.createSession(sessionName, targetPath) match
  case Left(error) => Output.error(error)
  case Right(_) => Output.success("Tmux session created")

// Join session (switch if inside tmux, attach if outside)
if TmuxAdapter.isInsideTmux then
  TmuxAdapter.switchSession(sessionName)
else
  TmuxAdapter.attachSession(sessionName)

// Check if targeting current session (for safety)
if TmuxAdapter.isCurrentSession(sessionName) then
  Output.error("Cannot delete current session")
```
