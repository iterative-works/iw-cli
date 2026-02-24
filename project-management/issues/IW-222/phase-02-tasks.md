# Phase 02 Tasks: Infrastructure Layer — adapter moves, StateReader, TmuxAdapter.sendKeys

## Setup
- [ ] Verify all existing tests pass before starting (`./iw test unit`) to establish a clean baseline
- [ ] Verify E2E tests pass before starting (`./iw test e2e`) to confirm Phase 1 re-exports are working

## File Moves with Package Changes

### Step 1: Move ProcessManager
- [ ] Copy `.iw/core/dashboard/ProcessManager.scala` to `.iw/core/adapters/ProcessManager.scala`, change package from `iw.core.dashboard` to `iw.core.adapters`. Add PURPOSE header
- [ ] Replace `.iw/core/dashboard/ProcessManager.scala` with a re-export: `package iw.core.dashboard` + `export iw.core.adapters.ProcessManager`
- [ ] Run unit tests — `ProcessManagerTest` and any tests that depend on `ProcessManager` must pass

### Step 2: Move ServerConfigRepository
- [ ] Copy `.iw/core/dashboard/ServerConfigRepository.scala` to `.iw/core/adapters/ServerConfigRepository.scala`, change package from `iw.core.dashboard` to `iw.core.adapters`. Add PURPOSE header
- [ ] Replace `.iw/core/dashboard/ServerConfigRepository.scala` with a re-export: `package iw.core.dashboard` + `export iw.core.adapters.ServerConfigRepository`
- [ ] Run unit tests — `ServerConfigRepositoryTest` must pass

### Step 3: Move ServerClient
- [ ] Copy `.iw/core/dashboard/ServerClient.scala` to `.iw/core/adapters/ServerClient.scala`, change package from `iw.core.dashboard` to `iw.core.adapters`. Remove `iw.core.dashboard.{ServerConfigRepository, ProcessManager}` import (now same package). Add PURPOSE header
- [ ] Replace `.iw/core/dashboard/ServerClient.scala` with a re-export: `package iw.core.dashboard` + `export iw.core.adapters.ServerClient`
- [ ] Run unit tests — `ServerClientTest` must pass

## Import Updates

### Commands (`.iw/commands/`)
- [ ] `open.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [ ] `register.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [ ] `rm.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [ ] `start.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [ ] `issue.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [ ] `server.scala`: change `iw.core.dashboard.{ProcessManager, ServerConfigRepository, ServerLifecycleService}` to `iw.core.adapters.{ProcessManager, ServerConfigRepository}` + `iw.core.model.ServerLifecycleService`
- [ ] `feedback.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [ ] `dashboard.scala`: change `iw.core.dashboard.{CaskServer, StateRepository, ServerConfigRepository}` to `iw.core.dashboard.{CaskServer, StateRepository}` + `iw.core.adapters.ServerConfigRepository`

### Adapters (`.iw/core/adapters/`)
- [ ] `GitHubClient.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [ ] `GitLabClient.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`

### Dashboard internal (`.iw/core/dashboard/`)
- [ ] `CaskServer.scala`: add `import iw.core.adapters.ServerClient` (was same-package reference, now cross-package)

### Tests (`.iw/core/test/`)
- [ ] `ServerClientTest.scala`: change `iw.core.dashboard.ServerClient` to `iw.core.adapters.ServerClient`
- [ ] `ServerConfigRepositoryTest.scala`: change `iw.core.dashboard.ServerConfigRepository` to `iw.core.adapters.ServerConfigRepository`
- [ ] `ProcessManagerTest.scala`: change `iw.core.dashboard.ProcessManager` to `iw.core.adapters.ProcessManager`
- [ ] `FeedbackParserTest.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [ ] `ServerLifecycleServiceTest.scala`: change `iw.core.dashboard.ServerLifecycleService` to `iw.core.model.ServerLifecycleService`
- [ ] `GitHubClientTest.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`
- [ ] `GitLabClientTest.scala`: change `iw.core.dashboard.FeedbackParser` to `iw.core.model.FeedbackParser`

### Import update checkpoint
- [ ] Run full unit test suite (`./iw test unit`) — all tests must pass with updated imports

## Cleanup — Remove Re-export Files
- [ ] Delete `dashboard/ProcessManager.scala` (re-export from Step 1)
- [ ] Delete `dashboard/ServerConfigRepository.scala` (re-export from Step 2)
- [ ] Delete `dashboard/ServerClient.scala` (re-export from Step 3)
- [ ] Delete `dashboard/FeedbackParser.scala` (Phase 1 re-export)
- [ ] Delete `dashboard/ServerLifecycleService.scala` (Phase 1 re-export)
- [ ] Run full unit test suite (`./iw test unit`) — all tests must pass without re-exports
- [ ] Run E2E tests (`./iw test e2e`) — no command regressions after re-export removal

## New Components (Tests First)

### StateReader
- [ ] Create `.iw/core/test/StateReaderTest.scala` with tests: (1) read valid `state.json` with all 5 cache types populated returns populated `ServerState`, (2) read from non-existent file returns empty `ServerState`, (3) read malformed JSON returns Left with error message, (4) read empty JSON object returns `ServerState` with empty maps
- [ ] Run `StateReaderTest` — confirm all tests fail (no implementation yet)
- [ ] Create `.iw/core/adapters/StateReader.scala` — package `iw.core.adapters`, object `StateReader` with `read(statePath: String): Either[String, ServerState]`. Uses `ServerStateCodec` from `model/`. Returns empty state on missing file, Left on malformed JSON. Add PURPOSE header
- [ ] Run `StateReaderTest` — all 4 tests must pass

### TmuxAdapter.sendKeys
- [ ] Create `.iw/core/test/TmuxAdapterSendKeysTest.scala` with tests: (1) verify correct tmux command construction (session name, keys, Enter), (2) return Left on non-zero exit code
- [ ] Run `TmuxAdapterSendKeysTest` — confirm tests fail (no implementation yet)
- [ ] Add `sendKeys(sessionName: String, keys: String): Either[String, Unit]` method to `TmuxAdapter` in `.iw/core/adapters/Tmux.scala`. Uses `ProcessAdapter.run(tmuxCmd("send-keys", "-t", sessionName, keys, "Enter"))`. Add doc comment
- [ ] Run `TmuxAdapterSendKeysTest` — all tests must pass

## Verification
- [ ] Run full unit test suite (`./iw test unit`) — all existing and new tests pass
- [ ] Run full E2E test suite (`./iw test e2e`) — no regressions from moves or re-export removal
- [ ] Verify no `dashboard/` imports remain in `commands/` scripts (except `dashboard.scala` which imports `CaskServer` and `StateRepository`)
- [ ] Verify no code duplication: moved files are real implementations, no re-export files remain in `dashboard/` for the 5 moved/extracted components
- [ ] Verify all new and modified files have PURPOSE headers
