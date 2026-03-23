# IW-305: phase-start should push feature branch before phase work

## Problem Statement

When `phase-start` creates a phase sub-branch, it does not push the feature branch to origin first. If unpushed commits exist on the feature branch (e.g., phase context and task files), the remote feature branch is behind. This causes conflicts or data loss when the phase MR is squash-merged back into the remote feature branch and `fetchAndReset` runs afterward.

### Observed Failure (CPH-7)

1. Agent pushed feature branch early, then committed context + tasks locally
2. `phase-start` created phase branch (local-only commits carried over)
3. Phase branch squash-merged into remote feature branch — included context + tasks + implementation
4. `git pull --rebase` on local feature branch caused conflict in `phase-01-tasks.md`
5. Agent resolved conflict with wrong `--theirs`/`--ours` semantics

## Architecture Design

### Layer Analysis

This change is entirely in the **command/adapter layer** — no domain model changes needed.

#### Commands Layer (`.iw/commands/`)

**Component: `phase-start.scala`** (primary fix)
- Add `GitAdapter.push(featureBranch, os.pwd, setUpstream = true)` before `createAndCheckoutBranch`
- This ensures the remote has all local commits before the phase branch diverges
- Must handle push failure gracefully (exit with error)

**Component: `phase-pr.scala`** (safety net, optional)
- In batch mode, before `fetchAndReset`, consider warning if the feature branch target has unpushed commits
- Currently `phase-pr` already pushes the *phase* branch (line 67), but doesn't verify the *feature* branch (its merge target) is up to date on remote

#### Adapters Layer (`.iw/core/adapters/`)

**Component: `Git.scala`**
- `push()` (line 81) already exists with `setUpstream` parameter — no changes needed
- `fetchAndReset()` (line 142) — no changes needed, it will work correctly once `phase-start` pushes first

### Dependency Direction

```
phase-start.scala → GitAdapter.push() → ProcessAdapter.run()
                   → GitAdapter.createAndCheckoutBranch()
```

No new dependencies introduced. Using existing `GitAdapter.push` which is already used by `phase-pr.scala`.

## Technical Decisions

### TD-1: Push with `setUpstream = true`

Use `setUpstream = true` to ensure tracking is established. The feature branch may not have been pushed before (e.g., created locally for a new issue).

### TD-2: Fail on push error

If the push fails, `phase-start` should exit with an error rather than proceeding. A failed push means the precondition (remote parity) is not met, and continuing would reproduce the original bug.

### TD-3: Safety warning in `phase-pr` (optional enhancement)

### CLARIFY: phase-pr safety warning scope

Should `phase-pr` add a preflight check that warns/errors if the feature branch (merge target) has unpushed commits? The issue mentions this as a "safety net" but the primary fix in `phase-start` should prevent the scenario. Options:
1. Skip for now — the `phase-start` fix is sufficient
2. Add a warning only (non-blocking)
3. Add a blocking check that prevents PR creation

## Estimates

| Layer | Component | Effort |
|-------|-----------|--------|
| Commands | `phase-start.scala` — add push before branch creation | 0.5h |
| E2E Tests | `phase-start.bats` — test push behavior | 1h |
| Commands | `phase-pr.scala` — optional safety warning | 0.5h |
| **Total** | | **1.5–2h** |

## Testing Strategy

### Unit Tests
- No pure domain logic changes — no new unit tests needed

### E2E Tests (`phase-start.bats`)
- **New test**: phase-start pushes feature branch to origin before creating sub-branch
  - Setup: create a bare remote, add commits to feature branch without pushing
  - Run `phase-start`
  - Verify: remote feature branch has the local commits (check with `git log origin/<branch>`)
  - Verify: phase sub-branch is created after push
- **New test**: phase-start fails gracefully when push fails (e.g., no remote configured)
- **Existing tests**: must be updated to work with push (need bare remote in setup, or handle push failure)

### Integration Scenario
The full scenario from the issue (push → phase-start → implement → squash merge → fetchAndReset) is too complex for an automated test but the individual precondition (push before branch) is testable.

## Implementation Sequence

1. **Phase 1**: Update `phase-start.scala` to push feature branch before creating sub-branch
2. **Phase 2**: Update E2E tests — add bare remote to setup, add new test cases for push behavior
3. **Phase 3** (optional): Add safety warning in `phase-pr.scala` for unpushed feature branch commits
