---
generated_from: 42929eacb5145139f871a5cb1a929b3a85514052
generated_at: 2026-03-24T14:11:49Z
branch: IW-309
issue_id: IW-309
phase: "1-2 (complete)"
files_analyzed:
  - .iw/commands/analyze.scala
  - .iw/commands/implement.scala
  - .iw/test/analyze.bats
  - .iw/test/implement.bats
  - project-management/issues/IW-309/analysis.md
  - project-management/issues/IW-309/phase-01-context.md
  - project-management/issues/IW-309/phase-02-context.md
  - project-management/issues/IW-309/tasks.md
---

# Review Packet: IW-309 — `iw analyze` and `iw implement` commands

## Goals

This feature adds two CLI commands that reduce cognitive load for the common development loop of triaging an issue and then implementing it.

Key objectives:

- Replace the verbose `iw start <id> --prompt "/iterative-works:triage-issue"` incantation with `iw analyze <id>`, giving analysis a discoverable entry point.
- Replace the manual recall of which workflow slash command to use (`ag-implement`, `wf-implement`, `dx-fix`) with `iw implement`, which reads `workflow_type` from `review-state.json` and routes automatically.
- Support both interactive mode (spawns `claude` directly in the terminal) and batch mode (`--batch` delegates to `iw batch-implement`).
- No new domain or adapter code — both commands compose existing building blocks.

## Scenarios

- [ ] `iw analyze <issueId>` creates a worktree and tmux session with the triage agent launched using `/iterative-works:triage-issue`
- [ ] `iw analyze` with no arguments exits with code 1 and shows a clear usage message
- [ ] `iw implement <issueId>` with `workflow_type: agile` in review-state.json spawns claude with `/iterative-works:ag-implement`
- [ ] `iw implement <issueId>` with `workflow_type: waterfall` spawns claude with `/iterative-works:wf-implement`
- [ ] `iw implement <issueId>` with `workflow_type: diagnostic` spawns claude with `/iterative-works:dx-implement`
- [ ] `iw implement` (no explicit issue ID, on an issue branch) resolves the issue ID from the branch name
- [ ] `iw implement` (no explicit issue ID, not on an issue branch) exits with code 1 and shows guidance
- [ ] Missing `review-state.json` exits with code 1 and shows the expected path
- [ ] `review-state.json` without a `workflow_type` field exits with code 1 and shows guidance
- [ ] Unrecognized `workflow_type` value exits with code 1 and names the bad value
- [ ] `--phase N` is appended to the slash command prompt string
- [ ] `--model MODEL` is forwarded to `claude` as `--model MODEL`
- [ ] `--batch` delegates to `iw batch-implement` with the issue ID and all relevant flags

## Entry Points

| File | Method/Class | Why Start Here |
|------|--------------|----------------|
| `.iw/commands/analyze.scala` | `analyze()` | Top-level entry point for the `iw analyze` command; all logic is here |
| `.iw/commands/implement.scala` | `implement()` | Top-level entry point for the `iw implement` command; contains all branching logic |
| `.iw/commands/implement.scala` | `buildClaudeCmd()` | Constructs the claude invocation; verify prompt format here |
| `.iw/commands/implement.scala` | `buildBatchArgs()` | Constructs the `batch-implement` argument list; verify flag passthrough here |
| `.iw/test/analyze.bats` | test 2 ("delegates to iw start") | Proves the core delegation works end-to-end |
| `.iw/test/implement.bats` | tests 5-7 (workflow dispatch) | Prove that each workflow type routes to the correct slash command |

## Diagrams

### Command Flow: `iw analyze <issueId>`

```
iw analyze <issueId>
    │
    ▼
findIwRun()         ← locates iw-run via IW_COMMANDS_DIR
    │
    ▼
ProcessAdapter.runInteractive(
  iw-run start --prompt /iterative-works:triage-issue <issueId>
)
    │
    ▼
iw start            ← creates worktree + tmux session + launches claude
```

### Command Flow: `iw implement [issueId]` (interactive mode)

```
iw implement [issueId]
    │
    ├── issueId from arg, or IssueId.fromBranch(currentBranch)
    │
    ├── --batch? ──────────────────────────────────────────────┐
    │                                                           │
    ▼                                                           ▼
ReviewStateAdapter.read(                           ProcessAdapter.runInteractive(
  project-management/issues/{id}/review-state.json   iw batch-implement <issueId> [...flags]
)                                                  )
    │
    ▼
ujson.read → workflow_type field
    │
    ▼
BatchImplement.resolveWorkflowCode(workflow_type)
  "agile"      → "ag"
  "waterfall"  → "wf"
  "diagnostic" → "dx"
    │
    ▼
buildClaudeCmd(
  prompt = "/iterative-works:{code}-implement {issueId} [--phase N]"
  model  = [--model MODEL]
)
    │
    ▼
ProcessAdapter.runInteractive(
  claude --dangerously-skip-permissions -p <prompt> [--model MODEL]
)
```

### Argument Parsing: `implement`

```
raw args
    │
    ├── positionalArgs (no -- prefix, not a named-arg value)
    │       └── issueIdArg  [A-Z]+-[0-9]+
    │
    └── named args via PhaseArgs
            --batch         → Boolean flag
            --phase N       → Option[String]
            --model M       → Option[String]
            --max-turns N   → Option[String]  (batch only)
            --max-budget-usd N → Option[String]  (batch only)
            unknown --flags → passthrough to batch-implement
```

## Test Summary

### E2E Tests (BATS)

| File | Test | Type |
|------|------|------|
| `analyze.bats` | analyze without issue ID exits with code 1 and shows usage | E2E |
| `analyze.bats` | analyze delegates to iw start and creates worktree and session | E2E |
| `analyze.bats` | analyze passes triage prompt to claude command | E2E (skipped in Docker) |
| `implement.bats` | implement without issue ID and not on issue branch exits with code 1 | E2E |
| `implement.bats` | implement with valid issue ID but missing review-state.json exits with code 1 | E2E |
| `implement.bats` | implement with review-state.json missing workflow_type exits with code 1 | E2E |
| `implement.bats` | implement with unrecognized workflow type exits with code 1 | E2E |
| `implement.bats` | implement with agile workflow spawns claude with ag-implement prompt | E2E |
| `implement.bats` | implement with waterfall workflow spawns claude with wf-implement prompt | E2E |
| `implement.bats` | implement with diagnostic workflow spawns claude with dx-implement prompt | E2E |
| `implement.bats` | implement --phase N includes phase number in the prompt string | E2E |
| `implement.bats` | implement --model MODEL passes model flag to claude | E2E |
| `implement.bats` | implement --batch delegates to iw batch-implement with issue ID | E2E |
| `implement.bats` | implement resolves issue ID from branch name when no explicit ID given | E2E |

**Total: 14 E2E tests across 2 files.**

No unit tests were added — all new logic is in the command layer and is covered by E2E tests. The underlying routing logic (`BatchImplement.resolveWorkflowCode`) already has unit test coverage from prior work.

Test isolation pattern used throughout:
- `IW_SERVER_DISABLED=1` prevents dashboard server interaction
- `IW_TMUX_SOCKET` scoped per test run for tmux isolation
- Temporary git repos created in `mktemp -d` and torn down in `teardown()`
- Mock `claude` script in `$STUB_DIR` records received arguments for assertion

## Files Changed

| File | Change | Description |
|------|--------|-------------|
| `.iw/commands/analyze.scala` | Added (33 lines) | `iw analyze` command — thin wrapper delegating to `iw start --prompt` |
| `.iw/commands/implement.scala` | Added (121 lines) | `iw implement` command — workflow-aware dispatcher to claude or batch-implement |
| `.iw/test/analyze.bats` | Added (101 lines) | 3 E2E tests for the analyze command |
| `.iw/test/implement.bats` | Added (221 lines) | 11 E2E tests for the implement command |
| `project-management/issues/IW-309/analysis.md` | Added | Technical analysis document |
| `project-management/issues/IW-309/implementation-log.md` | Added | Phase-by-phase implementation log |
| `project-management/issues/IW-309/phase-01-context.md` | Added | Phase 1 acceptance criteria and approach |
| `project-management/issues/IW-309/phase-01-tasks.md` | Added | Phase 1 task breakdown |
| `project-management/issues/IW-309/phase-02-context.md` | Added | Phase 2 acceptance criteria and approach |
| `project-management/issues/IW-309/phase-02-tasks.md` | Added | Phase 2 task breakdown |
| `project-management/issues/IW-309/review-phase-02-20260324-150754.md` | Added | Phase 2 code review record |
| `project-management/issues/IW-309/review-state.json` | Added | Issue workflow state (status: phase_merged) |
| `project-management/issues/IW-309/tasks.md` | Added | Top-level phase index (2/2 complete) |

**Production code changes: 2 new files, 0 existing files modified.**
