# Implementation Log: Agent-usable CLI: projects, worktrees, status commands + Claude-in-tmux

Issue: IW-222

This log tracks the evolution of implementation across phases.

---

## Phase 1: Domain Layer — model/ extractions and value objects (2026-02-24)

**Layer:** Domain

**What was built:**
- `.iw/core/model/ProjectPath.scala` — Pure function for deriving main project paths from worktree paths
- `.iw/core/model/ServerStateCodec.scala` — JSON serialization codecs for all server state domain models (16 ReadWriter instances + StateJson wire format)
- `.iw/core/model/ServerLifecycleService.scala` — Server lifecycle pure business logic (moved from dashboard)
- `.iw/core/model/FeedbackParser.scala` — Feedback command argument parser (moved from dashboard)
- `.iw/core/model/ProjectSummary.scala` — Value object for `iw projects` CLI output
- `.iw/core/model/WorktreeSummary.scala` — Value object for `iw worktrees` CLI output
- `.iw/core/model/WorktreeStatus.scala` — Value object for `iw status` CLI output

**Dependencies on other layers:**
- None — this is the domain layer, all code is pure

**Decisions made:**
- Used re-export pattern (`export iw.core.model.X`) in dashboard files for backward compatibility during Phase 1; callers will be updated in Phase 2
- `MainProject.deriveMainProjectPath` delegates to `ProjectPath` rather than being deleted, preserving existing callers
- Value objects use `derives ReadWriter` for automatic JSON codec generation
- `ServerStateCodec` centralizes all codec instances in one object for discoverability

**Testing:**
- Unit tests: 20 new tests (8 ProjectPathTest + 12 ServerStateCodecTest)
- All existing tests pass: MainProjectTest (12), StateRepositoryTest (23), ServerLifecycleServiceTest (17), FeedbackParserTest (18)

**Code review:**
- Iterations: 1
- Review file: review-phase-01-20260224.md
- Findings: 0 critical, 5 warnings (pre-existing/intentional), 14 suggestions

**Files changed:**
```
A  .iw/core/model/ProjectPath.scala
A  .iw/core/model/ServerStateCodec.scala
A  .iw/core/model/ServerLifecycleService.scala
A  .iw/core/model/FeedbackParser.scala
A  .iw/core/model/ProjectSummary.scala
A  .iw/core/model/WorktreeSummary.scala
A  .iw/core/model/WorktreeStatus.scala
A  .iw/core/test/ProjectPathTest.scala
A  .iw/core/test/ServerStateCodecTest.scala
M  .iw/core/dashboard/FeedbackParser.scala (re-export)
M  .iw/core/dashboard/ServerLifecycleService.scala (re-export)
M  .iw/core/dashboard/StateRepository.scala (imports from ServerStateCodec)
M  .iw/core/dashboard/domain/MainProject.scala (delegates to ProjectPath)
```

---

## Phase 2: Infrastructure Layer — adapter moves, StateReader, TmuxAdapter.sendKeys (2026-02-24)

**Layer:** Infrastructure

**What was built:**
- `.iw/core/adapters/ProcessManager.scala` — Process spawning and PID file management (moved from dashboard/)
- `.iw/core/adapters/ServerConfigRepository.scala` — Server configuration file persistence (moved from dashboard/)
- `.iw/core/adapters/ServerClient.scala` — HTTP client for CLI-to-server communication (moved from dashboard/)
- `.iw/core/adapters/StateReader.scala` — Read-only adapter for querying server state from state.json
- `.iw/core/adapters/Tmux.scala` — Added `sendKeys` method for tmux keystroke injection

**Dependencies on other layers:**
- `model/ServerStateCodec` — Used by `StateReader` for JSON deserialization (from Phase 1)
- `model/ServerConfig` — Used by `ServerConfigRepository` (pre-existing)

**Decisions made:**
- Used incremental move strategy: move files one at a time with temporary re-exports, then update all imports, then remove re-exports
- `StateReader` follows `StateRepository.read()` pattern but is read-only (no write, no file creation)
- `sendKeys` always appends Enter keystroke — intentional for the `--prompt` command execution use case
- All Phase 1 re-export files (`dashboard/FeedbackParser.scala`, `dashboard/ServerLifecycleService.scala`) cleaned up in this phase along with the 3 new re-exports
- `CaskServer.scala` got an explicit import for `ServerClient` since it's no longer same-package

**Testing:**
- Unit tests: 6 new tests (4 StateReaderTest + 2 TmuxAdapterSendKeysTest)
- All existing tests pass with updated imports
- E2E: 269/273 pass (4 pre-existing dev-mode failures unrelated to Phase 2)

**Code review:**
- Iterations: 1
- Review file: review-phase-02-20260224.md
- Findings: 0 critical, 5 warnings (1 fixed, 4 pre-existing), 8 suggestions (2 fixed)

**Files changed:**
```
A  .iw/core/adapters/ProcessManager.scala
A  .iw/core/adapters/ServerClient.scala
A  .iw/core/adapters/ServerConfigRepository.scala
A  .iw/core/adapters/StateReader.scala
A  .iw/core/test/StateReaderTest.scala
A  .iw/core/test/TmuxAdapterSendKeysTest.scala
M  .iw/core/adapters/Tmux.scala (added sendKeys)
M  .iw/core/dashboard/CaskServer.scala (import update)
M  .iw/commands/dashboard.scala (import update)
M  .iw/commands/feedback.scala (import update)
M  .iw/commands/issue.scala (import update)
M  .iw/commands/open.scala (import update)
M  .iw/commands/register.scala (import update)
M  .iw/commands/rm.scala (import update)
M  .iw/commands/server.scala (import update)
M  .iw/commands/start.scala (import update)
M  .iw/core/adapters/GitHubClient.scala (import update)
M  .iw/core/adapters/GitLabClient.scala (import update)
M  .iw/core/test/FeedbackParserTest.scala (import update)
M  .iw/core/test/GitHubClientTest.scala (import update)
M  .iw/core/test/GitLabClientTest.scala (import update)
M  .iw/core/test/ProcessManagerTest.scala (import update)
M  .iw/core/test/ServerClientTest.scala (import update)
M  .iw/core/test/ServerConfigRepositoryTest.scala (import update)
M  .iw/core/test/ServerLifecycleServiceTest.scala (import update)
D  .iw/core/dashboard/FeedbackParser.scala (re-export removed)
D  .iw/core/dashboard/ProcessManager.scala (moved to adapters/)
D  .iw/core/dashboard/ServerClient.scala (moved to adapters/)
D  .iw/core/dashboard/ServerConfigRepository.scala (moved to adapters/)
D  .iw/core/dashboard/ServerLifecycleService.scala (re-export removed)
```

---

## Phase 3: Presentation Layer — new commands (projects, worktrees, status) with --json (2026-02-25)

**Layer:** Presentation

**What was built:**
- `.iw/core/output/ProjectsFormatter.scala` — Human-readable formatter for projects list (column-aligned table)
- `.iw/core/output/WorktreesFormatter.scala` — Human-readable formatter for worktrees list (issue/PR/review summary)
- `.iw/core/output/StatusFormatter.scala` — Human-readable formatter for detailed worktree status (conditional multi-section display)
- `.iw/commands/projects.scala` — `iw projects [--json]` command: lists registered projects from state.json
- `.iw/commands/worktrees.scala` — `iw worktrees [--all] [--json]` command: lists worktrees for current project or all
- `.iw/commands/status.scala` — `iw status [issue-id] [--json]` command: detailed worktree status with live git + cached data

**Dependencies on other layers:**
- `model/ProjectSummary`, `model/WorktreeSummary`, `model/WorktreeStatus` — value objects from Phase 1
- `model/ProjectPath`, `model/ServerState`, `model/ServerStateCodec` — domain types from Phase 1
- `adapters/StateReader` — read-only state access from Phase 2
- `adapters/ConfigFileRepository`, `adapters/GitAdapter` — pre-existing adapters

**Decisions made:**
- Commands follow existing script-per-command pattern (consistent with `start.scala`, `open.scala`)
- `--json` outputs the value objects directly via `upickle.default.write` (automatic serialization via `derives ReadWriter`)
- `status` command issue ID resolution follows same pattern as `open.scala` (branch inference + team prefix)
- `worktrees` default filters to current project using `ProjectPath.deriveMainProjectPath` matching

**Testing:**
- Unit tests: 24 new tests (6 ProjectsFormatterTest + 8 WorktreesFormatterTest + 10 StatusFormatterTest)
- E2E tests: 15 new tests (5 projects.bats + 5 worktrees.bats + 5 status.bats)
- All existing tests pass: 269+ E2E tests, all unit tests

**Code review:**
- Iterations: 1
- Review file: review-phase-03-20260225.md
- Findings: 0 critical, 5 warnings (2 pre-existing patterns, 3 minor), 12 suggestions

**Files changed:**
```
A  .iw/commands/projects.scala
A  .iw/commands/worktrees.scala
A  .iw/commands/status.scala
A  .iw/core/output/ProjectsFormatter.scala
A  .iw/core/output/WorktreesFormatter.scala
A  .iw/core/output/StatusFormatter.scala
A  .iw/core/test/ProjectsFormatterTest.scala
A  .iw/core/test/WorktreesFormatterTest.scala
A  .iw/core/test/StatusFormatterTest.scala
A  .iw/test/projects.bats
A  .iw/test/worktrees.bats
A  .iw/test/status.bats
```

---
