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

### TD-3: No safety warning in `phase-pr`

The `phase-start` push fix is sufficient to prevent the scenario. No additional preflight check in `phase-pr` is needed — it would add complexity for a case that can no longer occur.

## Estimates

| Layer | Component | Effort |
|-------|-----------|--------|
| Commands | `phase-start.scala` — add push before branch creation | 0.5h |
| E2E Tests | `phase-start.bats` — test push behavior | 1h |
| **Total** | | **1.5h** |

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
