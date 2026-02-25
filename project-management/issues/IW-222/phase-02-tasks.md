# Phase 02 Tasks: Infrastructure Layer — adapter moves, StateReader, TmuxAdapter.sendKeys

**Phase Status:** Complete

## Setup
- [x] Verify all existing tests pass before starting (`./iw test unit`) to establish a clean baseline
- [x] Verify E2E tests pass before starting (`./iw test e2e`) to confirm Phase 1 re-exports are working

## File Moves with Package Changes

### Step 1: Move ProcessManager
- [x] Copy `.iw/core/dashboard/ProcessManager.scala` to `.iw/core/adapters/ProcessManager.scala`, change package from `iw.core.dashboard` to `iw.core.adapters`. Add PURPOSE header
- [x] Replace `.iw/core/dashboard/ProcessManager.scala` with a re-export: `package iw.core.dashboard` + `export iw.core.adapters.ProcessManager`
- [x] Run unit tests — `ProcessManagerTest` and any tests that depend on `ProcessManager` must pass

### Step 2: Move ServerConfigRepository
- [x] Copy `.iw/core/dashboard/ServerConfigRepository.scala` to `.iw/core/adapters/ServerConfigRepository.scala`, change package from `iw.core.dashboard` to `iw.core.adapters`. Add PURPOSE header
- [x] Replace `.iw/core/dashboard/ServerConfigRepository.scala` with a re-export: `package iw.core.dashboard` + `export iw.core.adapters.ServerConfigRepository`
- [x] Run unit tests — `ServerConfigRepositoryTest` must pass

### Step 3: Move ServerClient
- [x] Copy `.iw/core/dashboard/ServerClient.scala` to `.iw/core/adapters/ServerClient.scala`, change package from `iw.core.dashboard` to `iw.core.adapters`. Remove `iw.core.dashboard.{ServerConfigRepository, ProcessManager}` import (now same package). Add PURPOSE header
- [x] Replace `.iw/core/dashboard/ServerClient.scala` with a re-export: `package iw.core.dashboard` + `export iw.core.adapters.ServerClient`
- [x] Run unit tests — `ServerClientTest` must pass

## Import Updates

### Commands (`.iw/commands/`)
- [x] `open.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [x] `register.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [x] `rm.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [x] `start.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [x] `issue.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [x] `server.scala`: change `iw.core.dashboard.{ProcessManager, ServerConfigRepository, ServerLifecycleService}` to `iw.core.adapters.{ProcessManager, ServerConfigRepository}` + `iw.core.model.ServerLifecycleService`
- [x] `feedback.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [x] `dashboard.scala`: change `iw.core.dashboard.{CaskServer, StateRepository, ServerConfigRepository}` to `iw.core.dashboard.{CaskServer, StateRepository}` + `iw.core.adapters.ServerConfigRepository`

### Adapters (`.iw/core/adapters/`)
- [x] `GitHubClient.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [x] `GitLabClient.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`

### Dashboard internal (`.iw/core/dashboard/`)
- [x] `CaskServer.scala`: add `import iw.core.adapters.ServerClient` (was same-package reference, now cross-package)

### Tests (`.iw/core/test/`)
- [x] `ServerClientTest.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [x] `ServerConfigRepositoryTest.scala`: change `iw.core.dashboard.ServerConfigRepository` to `iw.core.adapters.ServerConfigRepository`
- [x] `ProcessManagerTest.scala`: change `iw.core.dashboard.ProcessManager` to `iw.core.adapters.ProcessManager`
- [x] `FeedbackParserTest.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [x] `ServerLifecycleServiceTest.scala`: change `iw.core.dashboard.ServerLifecycleService` to `iw.core.model.ServerLifecycleService`
- [x] `GitHubClientTest.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [x] `GitLabClientTest.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`

### Import update checkpoint
- [x] Run full unit test suite (`./iw test unit`) — all tests must pass with updated imports

## Cleanup — Remove Re-export Files
- [x] Delete `dashboard/ProcessManager.scala` (re-export from Step 1)
- [x] Delete `dashboard/ServerConfigRepository.scala` (re-export from Step 2)
- [x] Delete `dashboard/ServerClient.scala` (re-export from Step 3)
- [x] Delete `dashboard/FeedbackParser.scala` (Phase 1 re-export)
- [x] Delete `dashboard/ServerLifecycleService.scala` (Phase 1 re-export)
- [x] Run full unit test suite (`./iw test unit`) — all tests must pass without re-exports
- [x] Run E2E tests (`./iw test e2e`) — no command regressions after re-export removal

## New Components (Tests First)

### StateReader
- [x] Create `.iw/core/test/StateReaderTest.scala` with tests: (1) read valid `state.json` with all 5 cache types populated returns populated `ServerState`, (2) read from non-existent file returns empty `ServerState`, (3) read malformed JSON returns Left with error message, (4) read empty JSON object returns `ServerState` with empty maps
- [x] Run `StateReaderTest` — confirm all tests fail (no implementation yet)
- [x] Create `.iw/core/adapters/StateReader.scala` — package `iw.core.adapters`, object `StateReader` with `read(statePath: String): Either[String, ServerState]`. Uses `ServerStateCodec` from `model/`. Returns empty state on missing file, Left on malformed JSON. Add PURPOSE header
- [x] Run `StateReaderTest` — all 4 tests must pass

### TmuxAdapter.sendKeys
- [x] Create `.iw/core/test/TmuxAdapterSendKeysTest.scala` with tests: (1) verify correct tmux command construction (session name, keys, Enter), (2) return Left on non-zero exit code
- [x] Run `TmuxAdapterSendKeysTest` — confirm tests fail (no implementation yet)
- [x] Add `sendKeys(sessionName: String, keys: String): Either[String, Unit]` method to `TmuxAdapter` in `.iw/core/adapters/Tmux.scala`. Uses `ProcessAdapter.run(tmuxCmd("send-keys", "-t", sessionName, keys, "Enter"))`. Add doc comment
- [x] Run `TmuxAdapterSendKeysTest` — all tests must pass

## Verification
- [x] Run full unit test suite (`./iw test unit`) — all existing and new tests pass
- [x] Run full E2E test suite (`./iw test e2e`) — no regressions from moves or re-export removal (269/273 tests pass, 4 pre-existing failures in dev mode tests unrelated to Phase 2 changes)
- [x] Verify no `dashboard/` imports remain in `commands/` scripts (except `dashboard.scala` which imports `CaskServer` and `StateRepository`)
- [x] Verify no code duplication: moved files are real implementations, no re-export files remain in `dashboard/` for the 5 moved/extracted components
- [x] Verify all new and modified files have PURPOSE headers
