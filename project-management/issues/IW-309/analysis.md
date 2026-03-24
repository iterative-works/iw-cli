# Technical Analysis: Add `iw implement` and `iw analyze` commands

**Issue:** IW-309
**Created:** 2026-03-24
**Status:** Draft

## Problem Statement

Users currently need to remember which workflow was used during triage (`agile`, `waterfall`, or `diagnostic`) and manually invoke the corresponding slash command (`ag-implement`, `wf-implement`, or `dx-fix`). This is a purely deterministic routing decision that should not require human recall. Picking the wrong command wastes time or fails outright.

Similarly, starting analysis on an issue requires the verbose incantation `iw start <id> --prompt "/iterative-works:triage-issue"` when `iw analyze <id>` would suffice.

Both commands reduce cognitive load by hiding mechanical routing behind a single entry point.

## Proposed Solution

### High-Level Approach

Add two new commands to `.iw/commands/`:

**`implement.scala`** -- A workflow-aware dispatcher that reads `workflow_type` from `review-state.json`, maps it to the correct slash command prefix (`ag`, `wf`, `dx`) using the existing `BatchImplement.resolveWorkflowCode()`, and spawns `claude` with the appropriate prompt. When `--batch` is passed, it delegates to the same batch-implement orchestration loop. When no `review-state.json` exists, a new pure inference function attempts to detect workflow type from file patterns in the issue directory. If inference also fails, the user is prompted to choose.

**`analyze.scala`** -- A thin wrapper that resolves an issue ID and spawns `claude` with the `/iterative-works:triage-issue` slash command. This is intentionally minimal: it resolves the issue ID (from argument or branch), ensures `claude` is available, and invokes it.

### Why This Approach

The routing logic is deterministic and already exists in `BatchImplement.resolveWorkflowCode()`. Wrapping it in a dispatcher command is the simplest change that delivers the UX improvement. No new abstractions or framework changes are needed -- just two new command scripts that compose existing building blocks.

The fallback inference for missing `workflow_type` is new pure logic, but it belongs in the model layer alongside `BatchImplement` since it is a stateless decision function.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer (model/)

**Components:**
- `WorkflowInference` -- New object in `model/` with a pure function that takes a list of filenames in the issue directory and returns `Option[String]` (the inferred workflow type)
- Extend or co-locate with `BatchImplement` since `resolveWorkflowCode` is already there

**Responsibilities:**
- Infer workflow type from file pattern heuristics (Gherkin files suggest agile, hypothesis/defect files suggest diagnostic, analysis.md/tasks.md suggest waterfall)
- Keep the mapping rules pure and testable: input is a list of filenames, output is an optional workflow type string

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### Infrastructure Layer (adapters/)

**Components:**
- No new adapters needed. The commands will use:
  - `ReviewStateAdapter.read()` -- already exists
  - `ProcessAdapter.runStreaming()` / `ProcessAdapter.commandExists()` -- already exists
  - `GitAdapter.getCurrentBranch()` -- already exists
  - `Prompt.ask()` -- already exists (for user fallback selection)

**Responsibilities:**
- All I/O needs are covered by existing adapters

**Estimated Effort:** 0 hours
**Complexity:** N/A (no new work)

---

### Presentation Layer (commands/)

**Components:**
- `implement.scala` -- New command file
  - Parses args: optional issue ID, `--batch`, `--phase N`, `--model`, passthrough flags
  - Resolves issue ID (from arg or branch, reusing existing patterns)
  - Reads `review-state.json` and extracts `workflow_type`
  - Falls back to `WorkflowInference` if `workflow_type` is absent
  - Falls back to interactive `Prompt` selection if inference fails
  - When `--batch`: delegates to batch-implement logic (either via subprocess `iw batch-implement` or inline reuse)
  - When interactive: spawns `claude` with `/iterative-works:<code>-implement <issueId> [--phase N]`
- `analyze.scala` -- New command file
  - Parses args: required issue ID
  - Validates `claude` is available
  - Spawns `claude --dangerously-skip-permissions "/iterative-works:triage-issue <issueId>"`

**Responsibilities:**
- CLI argument parsing and validation
- Orchestrating the dispatch decision
- Spawning external processes (`claude` or `iw batch-implement`)
- Error messages and usage help

**Estimated Effort:** 3-5 hours
**Complexity:** Moderate (implement.scala has several code paths; analyze.scala is straightforward)

---

## Technical Decisions

### Patterns

- Reuse `BatchImplement.resolveWorkflowCode()` directly -- no wrapper or new abstraction
- Follow the existing command pattern: standalone `@main` function, imports from `iw.core.{model,adapters,output}.*`
- `implement --batch` should delegate to the `iw batch-implement` command as a subprocess rather than duplicating its orchestration loop inline. This keeps the commands independent and avoids top-level name collisions (commands cannot be compiled together)

### Technology Choices

- **Frameworks/Libraries**: scala-cli, os-lib (already used throughout), ujson (for JSON parsing, already used)
- **Data Storage**: Reads existing `review-state.json` -- no new storage
- **External Systems**: `claude` CLI (spawned as subprocess)

### Integration Points

- `implement.scala` reads `review-state.json` via `ReviewStateAdapter.read()` and parses `workflow_type` with `ujson` (same pattern as `batch-implement.scala` lines 92-104)
- `implement.scala --batch` invokes `iw batch-implement` via `ProcessAdapter.runStreaming()` or `ProcessAdapter.runInteractive()`, passing through all relevant flags
- `analyze.scala` spawns `claude` via `ProcessAdapter.runInteractive()` (interactive, not streaming, since user may want to interact)
- `WorkflowInference` is called from `implement.scala` when `workflow_type` field is missing from `review-state.json`

## Technical Risks & Uncertainties

### CLARIFY: `--batch` delegation strategy

The `implement --batch` mode needs to invoke the same logic as `batch-implement`. There are two options.

**Questions to answer:**
1. Should `implement --batch` invoke `iw batch-implement` as a subprocess, or should both commands share an extracted function?
2. What is the acceptable latency overhead of subprocess spawning (scala-cli JVM startup)?

**Options:**
- **Option A: Subprocess delegation** -- `implement --batch` runs `iw batch-implement <issueId> <workflowCode> [flags]` via `ProcessAdapter.runInteractive()`. Simple, no code sharing issues, but adds JVM startup latency.
- **Option B: Extract shared function** -- Pull the batch loop from `batch-implement.scala` into a model/adapter and call it from both commands. Cleaner but larger refactor, and commands cannot be compiled together so sharing is limited to core modules.

**Recommendation:** Option A (subprocess) is simpler and fits the existing architecture where commands are standalone scripts. The JVM startup cost is acceptable since batch-implement is a long-running operation anyway.

**Impact:** Affects implement.scala implementation complexity and batch-implement.scala refactoring scope.

---

### CLARIFY: Interactive claude invocation for `implement`

When `implement` is run without `--batch`, it needs to spawn `claude` interactively (user stays in the conversation). The existing `start.scala` uses `TmuxAdapter.sendKeys()` to launch claude in a tmux session, while `batch-implement.scala` uses `ProcessAdapter.runStreaming()` with `closeStdin = true`.

**Questions to answer:**
1. Should `iw implement` (non-batch) spawn `claude` as an interactive foreground process or send it to a tmux pane?
2. If foreground, should it use `ProcessAdapter.runInteractive()` or `os.proc().call()`?

**Options:**
- **Option A: Foreground interactive** -- Run `claude` in the current terminal via `ProcessAdapter.runInteractive()`. User types in the same terminal. Simplest approach.
- **Option B: Tmux sendKeys** -- Like `start.scala`, send the command to the current tmux pane. Only works if already in tmux.

**Recommendation:** Option A. The user runs `iw implement` from their terminal and expects to interact with claude there. No tmux dependency needed.

**Impact:** Affects how the claude process is spawned in implement.scala.

---

### CLARIFY: `analyze` scope -- worktree creation or just claude invocation?

The issue description says `iw analyze <issueID>` is equivalent to `iw start <issueID> --prompt "/iterative-works:triage-issue"`. But `start` does worktree creation, tmux session setup, and dashboard registration. Should `analyze` do all of that, or just spawn claude with the triage prompt?

**Questions to answer:**
1. Does `analyze` always create a new worktree (full `start` equivalent)?
2. Or can `analyze` be run from an existing worktree (just spawn claude)?

**Options:**
- **Option A: Full `start` equivalent** -- `analyze` delegates to the `start` command as a subprocess with `--prompt` flag. Handles worktree creation, tmux, everything.
- **Option B: Just spawn claude** -- `analyze` only resolves the issue ID and spawns `claude` with the triage prompt. Assumes the user is already in the right context.
- **Option C: Smart detection** -- If already in a worktree for the issue, just spawn claude. Otherwise, delegate to `start`.

**Recommendation:** Option A for initial implementation -- it matches the stated equivalence in the issue description. The user explicitly said it should be equivalent to `iw start <issueID> --prompt "/iterative-works:triage-issue"`.

**Impact:** Determines whether analyze.scala is a 5-line subprocess call or has its own logic.

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer (model/): 1-2 hours
- Infrastructure Layer (adapters/): 0 hours
- Presentation Layer (commands/): 3-5 hours

**Total Range:** 4-7 hours

**Confidence:** High

**Reasoning:**
- The workflow resolution logic already exists and is well-tested
- Both commands follow established patterns with clear precedent in the codebase
- The only genuinely new logic is `WorkflowInference` (file pattern heuristics), which is small and pure
- `analyze` is trivially simple regardless of which option is chosen

## Testing Strategy

### Per-Layer Testing

**Domain Layer (model/):**
- Unit: `WorkflowInferenceTest` -- test each file pattern heuristic
  - Gherkin files (.feature) -> agile
  - Hypothesis/defect files -> diagnostic
  - analysis.md + tasks.md -> waterfall
  - Empty directory -> None
  - Ambiguous patterns -> None (forces user prompt)

**Presentation Layer (commands/):**
- E2E (BATS): `implement.bats`
  - Test with explicit workflow code argument (bypass detection)
  - Test with review-state.json containing workflow_type
  - Test error message when no detection succeeds and no interactive input
  - Test `--batch` flag delegates correctly
  - Test argument passthrough (`--phase`, `--model`)
- E2E (BATS): `analyze.bats`
  - Test error when no issue ID provided
  - Test that it invokes the right command (mock claude or check constructed command)

**Test Data Strategy:**
- Unit tests: inline fixture data (list of filenames)
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
Remove the two command files. No other changes to revert (assuming subprocess delegation for `--batch`).

## Dependencies

### Prerequisites
- Existing `BatchImplement.resolveWorkflowCode()` (already exists)
- Existing `ReviewStateAdapter.read()` (already exists)
- Existing `ProcessAdapter` methods (already exist)

### Layer Dependencies
- `WorkflowInference` (model) must exist before `implement.scala` (commands) can use it
- `analyze.scala` has no new dependencies -- can be implemented immediately

### External Blockers
None.

## Risks & Mitigations

### Risk 1: File pattern heuristics produce wrong inference
**Likelihood:** Medium
**Impact:** Low (user can always pass explicit workflow code, and the fallback prompts the user)
**Mitigation:** Keep heuristics conservative -- return None when uncertain rather than guessing. The interactive fallback is the safety net.

### Risk 2: Argument passthrough to batch-implement misses edge cases
**Likelihood:** Low
**Impact:** Medium (batch run fails or uses wrong flags)
**Mitigation:** Explicitly enumerate and forward known flags. Test each passthrough path in E2E tests.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** -- Add `WorkflowInference` object with pure file-pattern heuristics + unit tests
2. **Presentation Layer (analyze)** -- Add `analyze.scala` command + E2E tests (no dependency on new model code)
3. **Presentation Layer (implement)** -- Add `implement.scala` command + E2E tests (depends on WorkflowInference)

**Ordering Rationale:**
- `analyze.scala` and `WorkflowInference` are independent and can be built in parallel
- `implement.scala` depends on `WorkflowInference` for the fallback path
- No infrastructure layer work needed -- all adapters already exist
- `analyze` is so simple it can serve as a confidence-building first deliverable

## Documentation Requirements

- [x] Code documentation (PURPOSE headers on new files, doc comments on WorkflowInference)
- [ ] API documentation -- N/A (CLI commands, not APIs)
- [ ] Architecture decision record -- not needed (follows existing patterns)
- [ ] User-facing documentation -- update README or help text with new commands
- [ ] Migration guide -- N/A (purely additive)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders
2. Run **wf-create-tasks** with the issue ID
3. Run **wf-implement** for layer-by-layer implementation
