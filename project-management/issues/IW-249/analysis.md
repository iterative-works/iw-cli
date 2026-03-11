# Analysis: `iw finish` — merge final PR and remove worktree

**Issue:** IW-249
**Created:** 2026-03-11

## Problem Statement

The issue lifecycle has a clean start (`iw start`) but no clean end. After all phases are done, the developer manually merges the final PR, removes the worktree, and cleans up. This is 3-4 steps across two worktrees that should be one command.

## Command Design

```
iw finish <issue-id> [--no-merge] [--force]
```

**Run from:** The main worktree (not the issue worktree).

### Flow

```
iw finish PROJ-123
  │
  ├─ Resolve issue branch name (PROJ-123)
  ├─ Find open PR: branch PROJ-123 → main
  │   └─ No PR found? → error, suggest creating one
  │
  ├─ Check PR is mergeable
  │   ├─ CI passing?
  │   ├─ No conflicts?
  │   └─ Fail → report status, stop
  │
  ├─ Merge PR (squash, delete remote branch)
  ├─ git pull (update local main)
  │
  ├─ Remove worktree (reuse rm logic)
  │   ├─ Check uncommitted changes → warn / --force
  │   ├─ Kill tmux session
  │   ├─ git worktree remove
  │   └─ Unregister from dashboard
  │
  └─ Update review-state → finished
```

### `--no-merge` variant

For when the PR was already merged (manually, or by GitHub auto-merge):

```
iw finish PROJ-123 --no-merge
  │
  ├─ Verify PR is merged (not just closed)
  │   └─ Not merged? → error
  ├─ git pull
  ├─ Remove worktree
  └─ Update review-state → finished
```

## Reusable Components

Most of the logic already exists:

| Component | Source | What to reuse |
|-----------|--------|---------------|
| PR lookup | `GitHubClient.scala` / `GitLabClient.scala` | `listPullRequests`, `mergePullRequest` |
| PR merge | `phase-pr.scala` batch mode | Squash merge + delete branch logic |
| Worktree removal | `rm.scala` | Uncommitted check, tmux kill, worktree remove, dashboard unregister |
| Review-state update | `ReviewStateManager.scala` | `update` with new terminal status |
| Branch/issue resolution | `PhaseBranch.scala`, `IssueId.scala` | Issue ID parsing, branch name construction |
| Worktree path | `WorktreePath.scala` | Sibling directory resolution |

## New Code Needed

1. **PR-to-main lookup:** `phase-pr` always targets the feature branch. Need a query for PR with `base=main` and `head=<issue-branch>`.

2. **Mergeability check:** Query PR status (CI, conflicts, reviews). `phase-pr --batch` just tries to merge and fails — `finish` should check first and report clearly.

3. **Orchestration:** Wire together: find PR → check → merge → pull → rm.

4. **`finished` status in review-state:** New terminal status. Currently the pipeline uses `all_complete` as the final state. `finished` means "PR merged, worktree gone, issue closed."

## Safety Considerations

- **Must not run from issue worktree:** The command removes that worktree. Detect via `git worktree list` — if cwd is inside the target worktree, error out.
- **Uncommitted changes:** Same check as `rm` — warn and require `--force`.
- **Review-state not `all_complete`:** Warn that the issue may not be fully implemented. Don't block (the user may have valid reasons), but make them confirm or use `--force`.
- **Local branch cleanup:** After worktree removal, the local branch may still exist. `git worktree remove` doesn't delete the branch. Consider deleting the local branch too (it's been squash-merged to main, so it's safe).

## Open Questions

1. Should `finish` also close the issue in the tracker (GitHub/YouTrack/GitLab)? Probably not in v1 — merging the PR often auto-closes the issue via commit message.

2. Should `finish` work without a worktree? (Just merge PR + clean up branch, for cases where the worktree was already removed manually.) Probably yes — `rm` step should be best-effort.

3. Should there be an `iw finish --dry-run` that reports what would happen without doing it?
