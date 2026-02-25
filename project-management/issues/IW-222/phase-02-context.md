# Phase 02: Infrastructure Layer — adapter moves, StateReader, TmuxAdapter.sendKeys

## Goals

Move I/O adapter code (`ServerClient`, `ServerConfigRepository`, `ProcessManager`) from `dashboard/` to `adapters/` so that CLI commands no longer violate the architecture rule against importing from `dashboard/`. Create a new read-only `StateReader` adapter for the new CLI commands (Phase 03). Add `TmuxAdapter.sendKeys` for the `--prompt` feature (Phase 04). Clean up Phase 1 re-exports.

## Scope

### In Scope

1. **Move `ServerClient`** — from `dashboard/ServerClient.scala` to `adapters/ServerClient.scala`, change package to `iw.core.adapters`
2. **Move `ServerConfigRepository`** — from `dashboard/ServerConfigRepository.scala` to `adapters/ServerConfigRepository.scala`, change package to `iw.core.adapters`
3. **Move `ProcessManager`** — from `dashboard/ProcessManager.scala` to `adapters/ProcessManager.scala`, change package to `iw.core.adapters`
4. **Create `StateReader`** — new adapter in `adapters/StateReader.scala` that reads `state.json` using `ServerStateCodec` from `model/`, returns `ServerState`
5. **Add `TmuxAdapter.sendKeys`** — new method in `adapters/Tmux.scala` to send keystrokes to a tmux session pane
6. **Update all imports** — commands, tests, adapters, and dashboard-internal code referencing moved files
7. **Clean up re-exports** — remove `dashboard/FeedbackParser.scala` and `dashboard/ServerLifecycleService.scala` re-export files (created in Phase 1), update all callers to import directly from `model/`

### Out of Scope

- New CLI commands (`projects`, `worktrees`, `status`) — Phase 03
- `--prompt` flag support in `start`/`open` — Phase 04
- Changes to `StateRepository` read/write logic (only import changes)
- Changes to `CaskServer` logic (only import additions)
- Moving any other dashboard-internal code not listed above

## Dependencies

### Required Before This Phase

- **Phase 01 complete**: `ServerStateCodec` in `model/` (for `StateReader`), `FeedbackParser` and `ServerLifecycleService` already in `model/` (for re-export cleanup)

### Provides for Later Phases

- **Phase 03** depends on `StateReader` for reading worktree/issue/PR state
- **Phase 03** depends on clean imports (commands import from `adapters/` not `dashboard/`)
- **Phase 04** depends on `TmuxAdapter.sendKeys` for agent launch

## Approach

### Step-by-step plan

> **Note:** Steps 1-3 each create temporary re-export files in `dashboard/` to keep the build green while moving one file at a time. All re-exports are removed together in Step 4 when all imports are updated.

**Step 1: Move `ProcessManager` to `adapters/`**

Move `.iw/core/dashboard/ProcessManager.scala` to `.iw/core/adapters/ProcessManager.scala`. Change package from `iw.core.dashboard` to `iw.core.adapters`. `ProcessManager` has no `iw.core` imports — it's pure infrastructure using `scala.util`, `java.nio.file`, and `os`. Leave the original file as a re-export so `ServerClient` (still in `dashboard/` at this point) doesn't break.

**Step 2: Move `ServerConfigRepository` to `adapters/`**

Move `.iw/core/dashboard/ServerConfigRepository.scala` to `.iw/core/adapters/ServerConfigRepository.scala`. Change package from `iw.core.dashboard` to `iw.core.adapters`. It imports `iw.core.model.ServerConfig` which is already in `model/`. Leave the original as a re-export.

**Step 3: Move `ServerClient` to `adapters/`**

Move `.iw/core/dashboard/ServerClient.scala` to `.iw/core/adapters/ServerClient.scala`. Change package from `iw.core.dashboard` to `iw.core.adapters`. Remove the import of `iw.core.dashboard.{ServerConfigRepository, ProcessManager}` — both are now in the same `adapters` package. Leave the original as a re-export (for `CaskServer` reference at line 652).

**Step 4: Update all imports and remove re-exports**

Update imports in all files that reference the moved components, then remove the dashboard re-export files (for all 5 moved/extracted components):

**Commands (`.iw/commands/`):**
- `open.scala`: `iw.core.dashboard.ServerClient` → `iw.core.adapters.ServerClient`
- `register.scala`: same
- `rm.scala`: same
- `start.scala`: same
- `issue.scala`: same
- `server.scala`: `iw.core.dashboard.{ProcessManager, ServerConfigRepository, ServerLifecycleService}` → `iw.core.adapters.{ProcessManager, ServerConfigRepository}` + `iw.core.model.ServerLifecycleService`
- `feedback.scala`: `iw.core.dashboard.FeedbackParser` → `iw.core.model.FeedbackParser`
- `dashboard.scala`: `iw.core.dashboard.{CaskServer, StateRepository, ServerConfigRepository}` → `iw.core.dashboard.{CaskServer, StateRepository}` + `iw.core.adapters.ServerConfigRepository`

**Adapters (`.iw/core/adapters/`):**
- `GitHubClient.scala`: `iw.core.dashboard.FeedbackParser` → `iw.core.model.FeedbackParser`
- `GitLabClient.scala`: `iw.core.dashboard.FeedbackParser` → `iw.core.model.FeedbackParser`

**Dashboard internal (`.iw/core/dashboard/`):**
- `CaskServer.scala`: add `import iw.core.adapters.ServerClient` (was same-package reference, now cross-package)

**Tests (`.iw/core/test/`):**
- `ServerClientTest.scala`: `iw.core.dashboard.ServerClient` → `iw.core.adapters.ServerClient`
- `ServerConfigRepositoryTest.scala`: `iw.core.dashboard.ServerConfigRepository` → `iw.core.adapters.ServerConfigRepository`
- `ProcessManagerTest.scala`: `iw.core.dashboard.ProcessManager` → `iw.core.adapters.ProcessManager`
- `FeedbackParserTest.scala`: `iw.core.dashboard.FeedbackParser` → `iw.core.model.FeedbackParser`
- `ServerLifecycleServiceTest.scala`: `iw.core.dashboard.ServerLifecycleService` → `iw.core.model.ServerLifecycleService`
- `GitHubClientTest.scala`: `iw.core.dashboard.FeedbackParser` → `iw.core.model.FeedbackParser`
- `GitLabClientTest.scala`: `iw.core.dashboard.FeedbackParser` → `iw.core.model.FeedbackParser`

**Remove re-export files:**
- `dashboard/ServerClient.scala` (re-export)
- `dashboard/ServerConfigRepository.scala` (re-export)
- `dashboard/ProcessManager.scala` (re-export)
- `dashboard/FeedbackParser.scala` (Phase 1 re-export)
- `dashboard/ServerLifecycleService.scala` (Phase 1 re-export)

**Step 5: Create `StateReader` adapter**

New file `.iw/core/adapters/StateReader.scala`. Read-only access to `state.json`, using `ServerStateCodec` from `model/`. Pattern follows `StateRepository.read()` but without write capability and without creating missing files.

```scala
package iw.core.adapters

import iw.core.model.{ServerState, ServerStateCodec}
import iw.core.model.ServerStateCodec.{given, *}
import java.nio.file.{Files, Paths}
import scala.util.{Try, Success, Failure}

object StateReader:
  /** Default state file location */
  val DefaultStatePath: String =
    s"${sys.env.getOrElse("HOME", "/tmp")}/.local/share/iw/server/state.json"

  /** Read server state from a JSON file. Returns empty state if file is missing. */
  def read(statePath: String = DefaultStatePath): Either[String, ServerState] =
    // ... same pattern as StateRepository.read() but read-only
```

**Step 6: Add `TmuxAdapter.sendKeys`**

Add a `sendKeys` method to `TmuxAdapter` in `.iw/core/adapters/Tmux.scala`:

```scala
/** Send keystrokes to a tmux session pane */
def sendKeys(sessionName: String, keys: String): Either[String, Unit] =
  val result = ProcessAdapter.run(tmuxCmd("send-keys", "-t", sessionName, keys, "Enter"))
  if result.exitCode == 0 then Right(())
  else Left(s"Failed to send keys to session '$sessionName': ${result.stderr}")
```

**Step 7: Write tests**

- `StateReaderTest` — read valid state, handle missing file (empty state), handle malformed JSON
- `TmuxAdapter.sendKeys` test — verify correct command construction
- Run all existing tests to verify no regressions from moves

## Files to Modify

### New Files

- `.iw/core/adapters/ServerClient.scala` — moved from dashboard/, package `iw.core.adapters`
- `.iw/core/adapters/ServerConfigRepository.scala` — moved from dashboard/, package `iw.core.adapters`
- `.iw/core/adapters/ProcessManager.scala` — moved from dashboard/, package `iw.core.adapters`
- `.iw/core/adapters/StateReader.scala` — new read-only state file reader
- `.iw/core/test/StateReaderTest.scala` — unit tests for StateReader
- `.iw/core/test/TmuxAdapterSendKeysTest.scala` — unit tests for sendKeys

### Modified Files

- `.iw/core/adapters/Tmux.scala` — add `sendKeys` method
- `.iw/core/dashboard/CaskServer.scala` — add import for `iw.core.adapters.ServerClient`
- `.iw/commands/open.scala` — import update
- `.iw/commands/register.scala` — import update
- `.iw/commands/rm.scala` — import update
- `.iw/commands/start.scala` — import update
- `.iw/commands/issue.scala` — import update
- `.iw/commands/server.scala` — import update
- `.iw/commands/feedback.scala` — import update
- `.iw/commands/dashboard.scala` — import update
- `.iw/core/adapters/GitHubClient.scala` — import update
- `.iw/core/adapters/GitLabClient.scala` — import update
- `.iw/core/test/ServerClientTest.scala` — import update
- `.iw/core/test/ServerConfigRepositoryTest.scala` — import update
- `.iw/core/test/ProcessManagerTest.scala` — import update
- `.iw/core/test/FeedbackParserTest.scala` — import update
- `.iw/core/test/ServerLifecycleServiceTest.scala` — import update
- `.iw/core/test/GitHubClientTest.scala` — import update
- `.iw/core/test/GitLabClientTest.scala` — import update

### Deleted Files

- `.iw/core/dashboard/ServerClient.scala` — replaced by adapters/ version
- `.iw/core/dashboard/ServerConfigRepository.scala` — replaced by adapters/ version
- `.iw/core/dashboard/ProcessManager.scala` — replaced by adapters/ version
- `.iw/core/dashboard/FeedbackParser.scala` — re-export no longer needed
- `.iw/core/dashboard/ServerLifecycleService.scala` — re-export no longer needed

## Component Specifications

### `adapters/ServerClient` (moved)

```scala
package iw.core.adapters

object ServerClient:
  def isHealthy(port: Int = -1): Boolean
  def registerWorktree(issueId: String, path: String, trackerType: String, team: String,
    statePath: String = "~/.local/share/iw/server/state.json"): Either[String, Unit]
  def updateLastSeen(issueId: String, path: String, trackerType: String, team: String,
    statePath: String = "~/.local/share/iw/server/state.json"): Either[String, Unit]
  def unregisterWorktree(issueId: String): Either[String, Unit]
```

**Package:** `iw.core.adapters` (was `iw.core.dashboard`)
**Imports:** `ServerConfigRepository` and `ProcessManager` (now same package), `iw.core.model.ServerConfig`, `sttp.client4`
**Logic:** Identical to current — only package declaration changes and dashboard-package imports removed
**Note:** `ServerClientTest` also imports `iw.core.dashboard.CaskServer` for integration testing — that import stays since `CaskServer` remains in `dashboard/`

### `adapters/ServerConfigRepository` (moved)

```scala
package iw.core.adapters

object ServerConfigRepository:
  def read(configPath: String): Either[String, ServerConfig]
  def write(config: ServerConfig, configPath: String): Either[String, Unit]
  def getOrCreateDefault(configPath: String): Either[String, ServerConfig]
```

**Package:** `iw.core.adapters` (was `iw.core.dashboard`)
**Imports:** `iw.core.model.ServerConfig`, `upickle.default`, `java.nio.file`, `os`
**Logic:** Identical — only package declaration changes

### `adapters/ProcessManager` (moved)

```scala
package iw.core.adapters

object ProcessManager:
  def writePidFile(pid: Long, pidPath: String): Either[String, Unit]
  def readPidFile(pidPath: String): Either[String, Option[Long]]
  def isProcessAlive(pid: Long): Boolean
  def removePidFile(pidPath: String): Either[String, Unit]
  def stopProcess(pid: Long, timeoutSeconds: Int = 10): Either[String, Unit]
  def spawnServerProcess(statePath: String, port: Int, hosts: Seq[String]): Either[String, Long]
```

**Package:** `iw.core.adapters` (was `iw.core.dashboard`)
**Imports:** `scala.util`, `java.nio.file`, `os`
**Logic:** Identical — only package declaration changes
**Note:** `spawnServerProcess` contains the string literal `"iw.core.dashboard.ServerDaemon"` as a `--main-class` argument. This stays correct after the move because `ServerDaemon` remains in `dashboard/`.

### `adapters/StateReader` (new)

```scala
package iw.core.adapters

object StateReader:
  val DefaultStatePath: String

  /** Read server state from JSON file. Returns empty state if file missing. */
  def read(statePath: String = DefaultStatePath): Either[String, ServerState]
```

**Package:** `iw.core.adapters`
**Imports:** `iw.core.model.{ServerState, ServerStateCodec}`, `ServerStateCodec.{given, *}`, `java.nio.file`, `upickle.default`
**Logic:** Reads file, deserializes via `StateJson`, converts to `ServerState`. Missing file returns empty state. Malformed JSON returns Left with error message.

### `TmuxAdapter.sendKeys` (new method)

```scala
/** Send keystrokes to a tmux session pane and press Enter to execute.
  * Designed for the --prompt use case (typing a command and executing it).
  */
def sendKeys(sessionName: String, keys: String): Either[String, Unit]
```

**Added to:** `iw.core.adapters.TmuxAdapter` in `Tmux.scala`
**Pattern:** Same as `killSession` — runs `tmux send-keys -t <session> <keys> Enter` via `ProcessAdapter.run`
**Note:** Always appends `Enter` to execute the typed command. This is intentional for the `--prompt` use case.

## Testing Strategy

### New Tests

1. **`StateReaderTest`**:
   - Read valid `state.json` with all cache types → returns populated `ServerState`
   - Read from non-existent file → returns empty `ServerState`
   - Read malformed JSON → returns Left with error message
   - Read empty JSON object → returns `ServerState` with empty maps

2. **`TmuxAdapterSendKeysTest`**:
   - Verify correct tmux command construction (session name, keys, Enter)
   - Return Left on non-zero exit code

### Existing Tests That Must Pass After Moves

- `ServerClientTest` — all tests pass with import change
- `ServerConfigRepositoryTest` — all tests pass with import change
- `ProcessManagerTest` — all tests pass with import change
- `FeedbackParserTest` — all tests pass with import change to `iw.core.model`
- `ServerLifecycleServiceTest` — all tests pass with import change to `iw.core.model`
- `GitHubClientTest` — all tests pass with import change
- `GitLabClientTest` — all tests pass with import change
- `StateRepositoryTest` — unchanged, must still pass
- All E2E tests (`./iw test e2e`) — verify no command regressions

### Test Execution

```bash
./iw test unit   # All unit tests
./iw test e2e    # All E2E tests
```

## Acceptance Criteria

- [ ] `ServerClient`, `ServerConfigRepository`, `ProcessManager` exist in `adapters/` with package `iw.core.adapters`
- [ ] Original files in `dashboard/` are deleted (no re-exports remain)
- [ ] `dashboard/FeedbackParser.scala` re-export is deleted
- [ ] `dashboard/ServerLifecycleService.scala` re-export is deleted
- [ ] All command imports updated to `iw.core.adapters.*` or `iw.core.model.*`
- [ ] `CaskServer.scala` has explicit import for `iw.core.adapters.ServerClient`
- [ ] `GitHubClient.scala` and `GitLabClient.scala` import `FeedbackParser` from `model/`
- [ ] `StateReader` exists in `adapters/` and reads `state.json` using `ServerStateCodec`
- [ ] `TmuxAdapter.sendKeys` method exists and follows existing command pattern
- [ ] `StateReaderTest` passes (valid read, missing file, malformed JSON)
- [ ] All existing unit tests pass with updated imports
- [ ] All E2E tests pass (no command regressions)
- [ ] No code duplication between original and moved files
- [ ] All new/modified files have PURPOSE headers
- [ ] No `dashboard/` imports remain in `commands/` scripts (except `dashboard.scala` which imports `CaskServer` and `StateRepository`)
