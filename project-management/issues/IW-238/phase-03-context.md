# Phase 3: Presentation Layer

## Goals

Implement the three command scripts that compose domain model types (Phase 1) with infrastructure adapters (Phase 2) into user-facing CLI commands:

1. **`phase-start`** — Create phase sub-branch, capture baseline SHA, update review-state
2. **`phase-commit`** — Stage all changes, commit with structured message, update task file, update review-state
3. **`phase-pr`** — Push sub-branch, create PR/MR, optionally squash-merge + advance to feature branch, update review-state

Each command outputs structured JSON to stdout for machine consumption and human-readable messages to stderr via `Output.*`.

## Scope

### In Scope
- Three new command scripts in `.iw/commands/`
- E2E tests (BATS) for each command in `.iw/test/` (matching existing test directory convention)
- JSON output via `PhaseOutput.{StartOutput, CommitOutput, PrOutput}.toJson`
- Review-state updates via `ReviewStateAdapter.update()`
- Both GitHub (`gh`) and GitLab (`glab`) support in `phase-pr`

### Out of Scope
- Modifying domain model types (Phase 1) or existing adapter methods (Phase 2)
- Dashboard server integration
- Workflow skill updates (those are updated separately after the commands exist)

### Scope Note: Batch-Mode Git Operations
The `--batch` path in `phase-pr` requires `git fetch`, `git reset --hard`, and `gh pr merge --squash --delete-branch` — none of which have existing adapter methods. Rather than modifying Phase 2 adapters, **use `ProcessAdapter.run()` directly** in the command for these three batch-specific operations. This is appropriate because they are specific to the batch merge flow, not general-purpose adapter functionality.

## What Was Built in Previous Phases

### Phase 1: Domain Layer (`model/`)
- `PhaseBranch` — `PhaseBranch(featureBranch, phaseNumber).branchName` → `"{featureBranch}-phase-{NN}"`
- `PhaseNumber` — Opaque type in `PhaseBranch.scala`, `PhaseNumber.parse(raw): Either[String, PhaseNumber]`, `.value`, `.toInt`
- `CommitMessage` — `CommitMessage.build(title: String, items: List[String] = Nil): String`
- `PhaseTaskFile` — `markComplete(content): String`, `markReviewed(content): String`
- `PhaseOutput` — `StartOutput.toJson`, `CommitOutput.toJson`, `PrOutput.toJson`

### Phase 2: Infrastructure Layer
**In `adapters/`:**
- `GitAdapter` — `createAndCheckoutBranch`, `checkoutBranch`, `stageAll`, `commit`, `push`, `diffNameOnly`, `pull`, `getFullHeadSha`, `getCurrentBranch`, `getHeadSha`, `hasUncommittedChanges`, `getRemoteUrl`
- `GitHubClient` — `createPullRequest`, `mergePullRequest`, `validateGhPrerequisites`
- `GitLabClient` — `createMergeRequest`, `mergeMergeRequest`, `validateGlabPrerequisites`
- `ReviewStateAdapter` — `update(path, UpdateInput): Either[String, Unit]`, `read(path): Either[String, String]`
- `CommandRunner` — `execute(command: String, args: Array[String], workingDir: Option[String]): Either[String, String]`, `isCommandAvailable(command: String): Boolean`

**In `model/` (pure, no I/O):**
- `FileUrlBuilder` — `build(remote: GitRemote, branch: String): Either[String, String]`

## Important API Notes

### Return types that are NOT `Either`
- `ConfigFileRepository.read(path: os.Path): Option[ProjectConfiguration]` — returns `Option`, not `Either`. Use `match` with `case Some(c) => ... case None => ...`
- `GitAdapter.getRemoteUrl(dir: os.Path): Option[GitRemote]` — returns `Option`, not `Either`. Convert with `.toRight("error message")` when needed in an Either chain.

### Merge methods use `--merge`, not `--squash`
The existing `GitHubClient.mergePullRequest` builds: `Array("pr", "merge", "--merge", prUrl)`
The existing `GitLabClient.mergeMergeRequest` builds: `Array("mr", "merge", mrUrl)`

For `--batch` mode, we need **squash merge with branch deletion**. Do NOT use the existing merge methods. Instead, build the command directly via `ProcessAdapter.run`:
```scala
// GitHub squash merge with branch deletion
ProcessAdapter.run(Seq("gh", "pr", "merge", "--squash", "--delete-branch", prUrl))

// GitLab squash merge
ProcessAdapter.run(Seq("glab", "mr", "merge", "--squash", mrUrl))
```

### `createPullRequest` / `createMergeRequest` have default parameters
The last two parameters (`isCommandAvailable`, `execCommand`) have defaults pointing to `CommandRunner`. In command scripts, just pass the first 5 positional arguments:
```scala
GitHubClient.createPullRequest(repository, headBranch, baseBranch, title, body)
```

## Command Specifications

### `phase-start`

**Usage:** `iw phase-start <phase-number> [--issue-id ID]`

**Inputs:**
- `phase-number` (required) — raw phase number string (e.g., "2", "02")
- `--issue-id` (optional) — issue ID; if omitted, inferred from current branch via `IssueId.fromBranch()`

**Sequence:**
1. Parse and validate `PhaseNumber.parse(phaseNumberArg)`
2. Resolve issue ID (from arg or branch)
3. Get current branch: `GitAdapter.getCurrentBranch(os.pwd)` — this is the feature branch
4. Verify we're NOT already on a phase sub-branch (branch name should not match `-phase-\d+$`)
5. Build branch name: `PhaseBranch(featureBranch, phaseNumber).branchName`
6. Create and checkout sub-branch: `GitAdapter.createAndCheckoutBranch(branchName, os.pwd)`
7. Capture baseline SHA: `GitAdapter.getFullHeadSha(os.pwd)`
8. Update review-state (if review-state.json exists — not an error if missing)
9. Print `StartOutput(...).toJson` to stdout

**Output JSON:**
```json
{
  "issueId": "IW-238",
  "phaseNumber": "02",
  "branch": "IW-238-phase-02",
  "baselineSha": "a7695d0..."
}
```

**Errors:**
- Invalid phase number → exit 1
- Cannot determine issue ID → exit 1
- Already on a phase sub-branch → exit 1 with message suggesting `phase-commit`
- Branch already exists → exit 1 (GitAdapter returns Left)

### `phase-commit`

**Usage:** `iw phase-commit --title TITLE [--items ITEM1,ITEM2,...] [--issue-id ID] [--phase-number N]`

**Inputs:**
- `--title` (required) — commit message title
- `--items` (optional) — comma-separated list of bullet items for the commit body
- `--issue-id` (optional) — inferred from branch if omitted
- `--phase-number` (optional) — inferred from branch if omitted (extracts from `-phase-NN` suffix)

**Sequence:**
1. Resolve issue ID and phase number from branch name or arguments
2. Verify we ARE on a phase sub-branch (branch name matches `-phase-\d+$`)
3. Stage all changes: `GitAdapter.stageAll(os.pwd)`
4. Build commit message: `CommitMessage.build(title, items)`
5. Execute commit: `GitAdapter.commit(message, os.pwd)` — returns commit SHA
6. Count committed files: `GitAdapter.diffNameOnly(parentSha, os.pwd)` or parse commit output
7. Read phase task file, apply `PhaseTaskFile.markComplete()`, write back
8. Read phase task file again, apply `PhaseTaskFile.markReviewed()`, write back
9. Print `CommitOutput(...).toJson` to stdout

**Phase task file path:** `project-management/issues/{ISSUE-ID}/phase-{NN}-tasks.md`

**Note:** Steps 7-8 are best-effort — if the task file doesn't exist, skip silently. The task file updates are a convenience for the workflow, not a hard requirement.

**Output JSON:**
```json
{
  "issueId": "IW-238",
  "phaseNumber": "02",
  "commitSha": "9b2fab6...",
  "filesCommitted": 12,
  "message": "Phase 2: Infrastructure Layer\n\n- GitAdapter extensions\n- PR/MR lifecycle"
}
```

**Errors:**
- Not on a phase sub-branch → exit 1 with message suggesting `phase-start`
- Missing `--title` → exit 1
- Nothing to commit (stageAll succeeds but commit returns Left "nothing to commit") → exit 1

### `phase-pr`

**Usage:** `iw phase-pr --title TITLE [--body BODY] [--batch] [--issue-id ID] [--phase-number N]`

**Inputs:**
- `--title` (required) — PR/MR title
- `--body` (optional) — PR/MR body text; if omitted, generates a default body
- `--batch` (optional) — after creating PR, squash-merge it, checkout feature branch, advance
- `--issue-id` (optional) — inferred from branch if omitted
- `--phase-number` (optional) — inferred from branch if omitted

**Sequence:**
1. Resolve issue ID, phase number, feature branch from current branch
2. Verify we ARE on a phase sub-branch
3. Read config to determine tracker type: `ConfigFileRepository.read(configPath)` (returns `Option[ProjectConfiguration]`)
4. Get remote URL: `GitAdapter.getRemoteUrl(os.pwd)` (returns `Option[GitRemote]`)
5. Determine repository identifier: `config.repository.getOrElse(...)`
6. Push sub-branch: `GitAdapter.push(branchName, os.pwd, setUpstream = true)`
7. Optionally build file URL base: `FileUrlBuilder.build(remote, branchName)` — `FileUrlBuilder` is in `iw.core.model`, not adapters
8. Generate PR body if not provided (include file URL links, phase info)
9. Create PR/MR based on tracker type:
   - GitHub: `GitHubClient.createPullRequest(repository, headBranch, featureBranch, title, body)`
   - GitLab: `GitLabClient.createMergeRequest(repository, headBranch, featureBranch, title, body)`
10. Update review-state: status "awaiting_review", pr_url set
11. If `--batch`:
    a. Squash-merge PR/MR via `ProcessAdapter.run` (see "Batch-mode merge" section below)
    b. Checkout feature branch: `GitAdapter.checkoutBranch(featureBranch, os.pwd)`
    c. Advance to remote state via `ProcessAdapter.run` (see "Batch-mode advance" section below)
    d. Update review-state: status "phase_merged"
12. Print `PrOutput(...).toJson` to stdout

**Batch-mode merge (step 11a):**
```scala
// GitHub: squash merge and delete remote branch
ProcessAdapter.run(Seq("gh", "pr", "merge", "--squash", "--delete-branch", prUrl))

// GitLab: squash merge
ProcessAdapter.run(Seq("glab", "mr", "merge", "--squash", mrUrl))
```

**Batch-mode advance (step 11c):**
```scala
// Fetch the updated remote after squash merge
ProcessAdapter.run(Seq("git", "-C", os.pwd.toString, "fetch", "origin"))
// Reset local feature branch to match remote (safe — see rationale below)
ProcessAdapter.run(Seq("git", "-C", os.pwd.toString, "reset", "--hard", s"origin/$featureBranch"))
```

**Why `git fetch && git reset --hard` instead of `git pull`:** After squash-merging the phase sub-branch PR, the feature branch may have local checkpoint commits (context/tasks generation from the workflow) that are already included in the squash. A normal `git pull` attempts to rebase these, causing spurious conflicts. Since the feature branch only advances via squash merges from phase sub-branches, `origin/{branch}` is guaranteed to be a superset of all local content. `reset --hard` is the correct operation.

**Output JSON:**
```json
{
  "issueId": "IW-238",
  "phaseNumber": "02",
  "prUrl": "https://github.com/iterative-works/iw-cli/pull/240",
  "headBranch": "IW-238-phase-02",
  "baseBranch": "IW-238",
  "merged": false
}
```

**Errors:**
- Not on a phase sub-branch → exit 1
- Missing `--title` → exit 1
- `gh`/`glab` not available or not authenticated → exit 1
- PR creation fails → exit 1 (push already happened, report that PR needs manual creation)
- Merge fails (--batch) → exit 1 (PR was created, report PR URL and that merge needs manual action)

## Tracker Type Detection

The commands need to know whether to use GitHub or GitLab for PR/MR operations:

```scala
val configPath = os.pwd / Constants.Paths.IwDir / "config.conf"
val config = ConfigFileRepository.read(configPath) match
  case Some(c) => c
  case None =>
    Output.error("No config found. Run 'iw init' first.")
    sys.exit(1)

config.trackerType match
  case IssueTrackerType.GitHub => // use GitHubClient
  case IssueTrackerType.GitLab => // use GitLabClient
  case IssueTrackerType.Linear | IssueTrackerType.YouTrack =>
    Output.error(s"PR operations not supported for ${config.trackerType} tracker")
    sys.exit(1)
```

Repository identifier: `config.repository.getOrElse(...)` — for GitHub this is `owner/repo`, for GitLab similar.

## Branch Name Parsing

Several commands need to extract issue ID and phase number from the current branch name. The branch pattern is `{ISSUE-ID}-phase-{NN}` where ISSUE-ID itself matches `[A-Z]+-[0-9]+`.

Helper logic (can be inline in commands or a small utility):
```scala
// Extract feature branch and phase number from a phase sub-branch name
// "IW-238-phase-02" → Some(("IW-238", "02"))
// "IW-238" → None (not a phase branch)
val PhaseSubBranchPattern = """^(.+)-phase-(\d+)$""".r

branch match
  case PhaseSubBranchPattern(featureBranch, phaseNum) => ...
  case _ => // not on a phase sub-branch
```

For issue ID extraction from the feature branch part, use `IssueId.fromBranch(featureBranch)`. Note: `IssueId.fromBranch` uppercases the branch before matching, which works correctly for extracting the issue ID prefix.

## Review-State Update Patterns

Each command updates review-state at specific points. Review-state updates are best-effort — if the review-state.json file doesn't exist, log a warning but don't fail the command.

**phase-start:**
```scala
ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
  status = Some("implementing"),
  displayText = Some(s"Phase $phaseNum: Implementing"),
  displaySubtext = Some(phaseTitle),
  displayType = Some("progress"),
  message = Some(s"Phase $phaseNum implementation started"),
  badges = Some(List(("In Progress", "info"))),
  badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
))
```

**phase-pr (awaiting review):**
```scala
ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
  status = Some("awaiting_review"),
  displayText = Some(s"Phase $phaseNum: Awaiting Review"),
  displayType = Some("warning"),
  needsAttention = Some(true),
  prUrl = Some(prUrl),
  badges = Some(List(("Review Needed", "warning"))),
  badgesMode = ReviewStateUpdater.ArrayMergeMode.Append,
  actions = Some(List(("view-pr", "View Pull Request", "external-link"))),
  actionsMode = ReviewStateUpdater.ArrayMergeMode.Append
))
```

**phase-pr --batch (merged):**
```scala
ReviewStateAdapter.update(reviewStatePath, ReviewStateUpdater.UpdateInput(
  status = Some("phase_merged"),
  displayText = Some(s"Phase $phaseNum: Merged"),
  displayType = Some("success"),
  prUrl = Some(prUrl),
  badges = Some(List(("Complete", "success"))),
  badgesMode = ReviewStateUpdater.ArrayMergeMode.Append
))
```

## Review-State File Path

All commands derive the review-state path from issue ID:
```scala
val reviewStatePath = os.pwd / "project-management" / "issues" / issueId.value / "review-state.json"
```

## Command Pattern Reference

Follow the pattern from `status.scala`:
```scala
// PURPOSE: ...
// PURPOSE: ...

import iw.core.model.*
import iw.core.adapters.*
import iw.core.output.*

@main def phaseStart(args: String*): Unit =
  // Parse arguments
  // Validate preconditions
  // Execute sequence with early-exit on Left
  // Print JSON to stdout
```

Error handling: match on `Either` results, `Output.error(msg)` + `sys.exit(1)` on `Left`.

## Testing Strategy

E2E tests using BATS in `.iw/test/` directory, following the pattern from `start.bats` and `status.bats`:

**`.iw/test/phase-start.bats`:**
- Creates a temp git repo with a feature branch, runs `phase-start 1`, verifies:
  - JSON output contains correct fields
  - Branch was created (`git branch --show-current` returns `{issue}-phase-01`)
  - review-state.json was updated (if present)
- Error cases: invalid phase number, already on phase branch, branch already exists

**`.iw/test/phase-commit.bats`:**
- Creates a phase branch setup, makes a file change, runs `phase-commit --title "Test"`, verifies:
  - JSON output with commit SHA
  - Commit actually exists in git log
  - Phase task file was updated (if it exists)
- Error cases: not on phase branch, nothing to commit, missing title

**`.iw/test/phase-pr.bats`:**
- Limited testability without real GitHub — focus on argument validation and error cases
- Test: not on phase branch → error
- Test: missing title → error
- Test: `gh` not available → appropriate error message

## Files to Create

| File | Purpose |
|------|---------|
| `.iw/commands/phase-start.scala` | phase-start command script |
| `.iw/commands/phase-commit.scala` | phase-commit command script |
| `.iw/commands/phase-pr.scala` | phase-pr command script |
| `.iw/test/phase-start.bats` | E2E tests for phase-start |
| `.iw/test/phase-commit.bats` | E2E tests for phase-commit |
| `.iw/test/phase-pr.bats` | E2E tests for phase-pr |

## Acceptance Criteria

1. `iw phase-start 2` creates sub-branch, outputs valid JSON, updates review-state
2. `iw phase-commit --title "Phase 2: Infrastructure"` stages, commits, outputs JSON
3. `iw phase-pr --title "Phase 2: Infrastructure Layer"` pushes, creates PR, outputs JSON
4. `iw phase-pr --title "Phase 2" --batch` creates PR, squash-merges, checks out feature branch, resets to remote
5. All three commands print errors to stderr and exit 1 on failure
6. All three commands infer issue ID and phase number from branch when not provided
7. E2E tests pass for happy paths and key error cases
8. All existing tests continue to pass (no regressions)
