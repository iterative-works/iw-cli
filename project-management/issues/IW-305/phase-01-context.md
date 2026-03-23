# Phase 1: Command fix — push feature branch in phase-start

## Goals

Add a `GitAdapter.push` call in `phase-start.scala` before `createAndCheckoutBranch` to ensure the remote feature branch has all local commits before the phase sub-branch diverges.

## Scope

- **In scope:** Single-line change to `.iw/commands/phase-start.scala`
- **Out of scope:** Changes to `GitAdapter`, `phase-pr`, or any other command

## Dependencies

- `GitAdapter.push(branch, dir, setUpstream)` already exists in `.iw/core/adapters/Git.scala:81`
- No new dependencies introduced

## Approach

1. After resolving the feature branch (line 20) and before creating the phase branch (line 34), call `GitAdapter.push(featureBranch, os.pwd, setUpstream = true)`
2. Use `CommandHelpers.exitOnError` to fail fast if push fails (TD-2 from analysis)

## Files to Modify

- `.iw/commands/phase-start.scala` — add push call before `createAndCheckoutBranch`

## Testing Strategy

- No unit tests needed (no pure domain logic changes)
- E2E tests deferred to Phase 2

## Acceptance Criteria

- [ ] `phase-start` pushes the feature branch to origin before creating the sub-branch
- [ ] If the push fails, `phase-start` exits with an error (does not create the sub-branch)
