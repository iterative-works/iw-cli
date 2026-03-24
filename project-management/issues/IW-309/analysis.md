# Technical Analysis: Add `iw implement` and `iw analyze` commands

**Issue:** IW-309
**Created:** 2026-03-24
**Status:** Ready for Implementation

## Problem Statement

Users currently need to remember which workflow was used during triage (`agile`, `waterfall`, or `diagnostic`) and manually invoke the corresponding slash command (`ag-implement`, `wf-implement`, or `dx-fix`). This is a purely deterministic routing decision that should not require human recall. Picking the wrong command wastes time or fails outright.

Similarly, starting analysis on an issue requires the verbose incantation `iw start <id> --prompt "/iterative-works:triage-issue"` when `iw analyze <id>` would suffice.

Both commands reduce cognitive load by hiding mechanical routing behind a single entry point.

## Proposed Solution

### High-Level Approach

Add two new commands to `.iw/commands/`:

**`implement.scala`** -- A workflow-aware dispatcher that reads `workflow_type` from `review-state.json`, maps it to the correct slash command prefix (`ag`, `wf`, `dx`) using the existing `BatchImplement.resolveWorkflowCode()`, and spawns `claude` as an interactive foreground process with the appropriate prompt. When `--batch` is passed, it delegates to `iw batch-implement` as a subprocess. Requires `review-state.json` with `workflow_type` — errors if missing.

**`analyze.scala`** -- A thin wrapper that delegates to `iw start <issueId> --prompt "/iterative-works:triage-issue"` as a subprocess. Provides a discoverable entry point for starting analysis on an issue.

### Why This Approach

The routing logic is deterministic and already exists in `BatchImplement.resolveWorkflowCode()`. Wrapping it in a dispatcher command is the simplest change that delivers the UX improvement. No new abstractions or framework changes are needed -- just two new command scripts that compose existing building blocks.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer (model/)

**Components:**
- No new model code needed. `BatchImplement.resolveWorkflowCode()` already handles the workflow type → code mapping.

**Estimated Effort:** 0 hours
**Complexity:** N/A

---

### Infrastructure Layer (adapters/)

**Components:**
- No new adapters needed. The commands will use:
  - `ReviewStateAdapter.read()` -- already exists
  - `ProcessAdapter.commandExists()` -- already exists
  - `GitAdapter.getCurrentBranch()` -- already exists

**Estimated Effort:** 0 hours
**Complexity:** N/A

---

### Presentation Layer (commands/)

**Components:**
- `implement.scala` -- New command file
  - Parses args: optional issue ID, `--batch`, `--phase N`, `--model`, passthrough flags
  - Resolves issue ID (from arg or branch, reusing existing patterns)
  - Reads `review-state.json` and extracts `workflow_type`
  - Maps workflow type to code via `BatchImplement.resolveWorkflowCode()`
  - Errors if `review-state.json` is missing or has no `workflow_type`
  - When `--batch`: delegates to `iw batch-implement` as a subprocess, passing through all flags
  - When interactive: spawns `claude` as a foreground interactive process with inherited stdin/stdout/stderr, using the resolved slash command
- `analyze.scala` -- New command file
  - Parses args: required issue ID
  - Delegates to `iw start <issueId> --prompt "/iterative-works:triage-issue"` as a subprocess

**Responsibilities:**
- CLI argument parsing and validation
- Orchestrating the dispatch decision
- Spawning external processes (`claude` or `iw batch-implement` or `iw start`)
- Error messages and usage help

**Estimated Effort:** 2-4 hours
**Complexity:** Moderate (implement.scala has several code paths; analyze.scala is straightforward)

---

## Technical Decisions

### Resolved Decisions

1. **No workflow inference from file patterns.** `implement` requires `review-state.json` with a `workflow_type` field. If missing, it errors with a clear message. This avoids fuzzy heuristics and keeps the command deterministic.

2. **`--batch` delegates to `iw batch-implement` as a subprocess.** No shared code extraction needed. The JVM startup cost is negligible for a long-running batch operation. Keeps commands independent.

3. **Interactive `implement` spawns `claude` as a foreground process.** Uses `os.proc().call()` with inherited stdin/stdout/stderr so claude takes over the terminal. User is already in the issue worktree — no tmux or worktree setup needed.

4. **`analyze` delegates to `iw start` as a subprocess.** Matches the stated equivalence in the issue. Implementation details may change later, but the command interface is stable.

### Patterns

- Reuse `BatchImplement.resolveWorkflowCode()` directly -- no wrapper or new abstraction
- Follow the existing command pattern: standalone `@main` function, imports from `iw.core.{model,adapters,output}.*`

### Technology Choices

- **Frameworks/Libraries**: scala-cli, os-lib (already used throughout), ujson (for JSON parsing, already used)
- **Data Storage**: Reads existing `review-state.json` -- no new storage
- **External Systems**: `claude` CLI (spawned as subprocess)

### Integration Points

- `implement.scala` reads `review-state.json` via `ReviewStateAdapter.read()` and parses `workflow_type` with `ujson` (same pattern as `batch-implement.scala` lines 92-104)
- `implement.scala --batch` invokes `iw batch-implement` via subprocess with inherited I/O, passing through all relevant flags
- `implement.scala` (interactive) spawns `claude` with inherited I/O and exits with its exit code
- `analyze.scala` invokes `iw start` via subprocess with inherited I/O

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer (model/): 0 hours
- Infrastructure Layer (adapters/): 0 hours
- Presentation Layer (commands/): 2-4 hours

**Total Range:** 2-4 hours

**Confidence:** High

**Reasoning:**
- The workflow resolution logic already exists and is well-tested
- Both commands follow established patterns with clear precedent in the codebase
- No new model or adapter code needed
- `analyze` is trivially simple
- `implement` is a thin dispatcher with well-defined code paths

## Testing Strategy

### Per-Layer Testing

**Presentation Layer (commands/):**
- E2E (BATS): `implement.bats`
  - Test with review-state.json containing `workflow_type: agile` → correct command constructed
  - Test with review-state.json containing `workflow_type: waterfall` → correct command constructed
  - Test with review-state.json containing `workflow_type: diagnostic` → correct command constructed
  - Test error message when review-state.json is missing
  - Test error message when `workflow_type` is absent from review-state.json
  - Test `--batch` flag delegates to batch-implement
  - Test argument passthrough (`--phase`, `--model`)
- E2E (BATS): `analyze.bats`
  - Test error when no issue ID provided
  - Test that it constructs the correct `iw start` command with `--prompt`

**Test Data Strategy:**
- E2E tests: temporary git repos with review-state.json fixtures, `IW_SERVER_DISABLED=1`

**Regression Coverage:**
- `batch-implement` behavior must remain unchanged
- `start --prompt` behavior must remain unchanged
- Existing `resolveWorkflowCode` tests already cover the mapping

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
None. Both commands use existing config infrastructure.

### Rollout Strategy
Ship both commands together. They are purely additive -- no existing behavior changes.

### Rollback Plan
Remove the two command files. No other changes to revert.

## Dependencies

### Prerequisites
- Existing `BatchImplement.resolveWorkflowCode()` (already exists)
- Existing `ReviewStateAdapter.read()` (already exists)
- Existing `ProcessAdapter` methods (already exist)

### Layer Dependencies
- `analyze.scala` has no new dependencies -- can be implemented immediately
- `implement.scala` has no new dependencies -- can be implemented immediately
- Both commands are independent of each other

### External Blockers
None.

## Risks & Mitigations

### Risk 1: Argument passthrough to batch-implement misses edge cases
**Likelihood:** Low
**Impact:** Medium (batch run fails or uses wrong flags)
**Mitigation:** Explicitly enumerate and forward known flags. Test each passthrough path in E2E tests.

---

## Implementation Sequence

**Recommended Order:**

1. **`analyze.scala`** -- Simpler command, no workflow detection. Good confidence builder.
2. **`implement.scala`** -- More complex, but all building blocks already exist.

**Ordering Rationale:**
- Both commands are independent and could be built in parallel
- No domain or infrastructure layer work needed
- `analyze` is so simple it can serve as a confidence-building first deliverable

## Documentation Requirements

- [x] Code documentation (PURPOSE headers on new files)
- [ ] API documentation -- N/A (CLI commands, not APIs)
- [ ] Architecture decision record -- not needed (follows existing patterns)
- [ ] User-facing documentation -- update README or help text with new commands
- [ ] Migration guide -- N/A (purely additive)

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run **wf-create-tasks** with the issue ID
2. Run **wf-implement** for layer-by-layer implementation
