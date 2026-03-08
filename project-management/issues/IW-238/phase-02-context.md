# Phase 2: Infrastructure Layer

**Issue:** IW-238
**Phase:** 2 of 3
**Estimated Effort:** 6-9 hours

## Purpose

Implement the infrastructure layer (adapters) for the phase lifecycle commands. This phase extends existing adapters with git/gh/glab operations needed by the three phase commands (`phase-start`, `phase-commit`, `phase-pr`), and introduces two new components: `FileUrlBuilder` (pure URL derivation in `model/`) and `ReviewStateAdapter` (shared review-state I/O in `adapters/`).

## Goals

1. Extend `GitAdapter` with branch creation, checkout, staging, committing, pushing, diff, pull, and full SHA operations
2. Extend `GitHubClient` with PR creation, merge, and response parsing
3. Extend `GitLabClient` with MR creation, merge, and response parsing
4. Create `FileUrlBuilder` in `model/` for deriving file-browseable URLs from git remote URLs and branch names
5. Create `ReviewStateAdapter` in `adapters/` to encapsulate the read/merge/validate/write sequence for review-state.json

## Scope

**In Scope:**
- New methods on existing `GitAdapter` object in `.iw/core/adapters/Git.scala`
- New methods on existing `GitHubClient` object in `.iw/core/adapters/GitHubClient.scala`
- New methods on existing `GitLabClient` object in `.iw/core/adapters/GitLabClient.scala`
- New file `.iw/core/model/FileUrlBuilder.scala` (pure URL construction — no I/O)
- New file `.iw/core/adapters/ReviewStateAdapter.scala` (read/merge/validate/write for review-state.json)
- Unit tests for all new functionality

**Out of Scope:**
- Command scripts `phase-start.scala`, `phase-commit.scala`, `phase-pr.scala` (Phase 3)
- E2E/BATS tests (Phase 3)
- Any changes to domain model types from Phase 1

## Dependencies

**From Phase 1 (Domain Layer):**
- `PhaseBranch` — used for branch name derivation in `GitAdapter.createBranch`
- `PhaseNumber` — used for phase number parameters
- `CommitMessage` — available for callers but not directly used by adapters
- `PhaseOutput` — available for callers but not directly used by adapters

**From Existing Codebase:**
- `GitAdapter` (`.iw/core/adapters/Git.scala`) — extend with new methods
- `GitHubClient` (`.iw/core/adapters/GitHubClient.scala`) — extend with PR operations
- `GitLabClient` (`.iw/core/adapters/GitLabClient.scala`) — extend with MR operations
- `ProcessAdapter` (`.iw/core/adapters/Process.scala`) — for executing git commands (existing pattern)
- `CommandRunner` (`.iw/core/adapters/CommandRunner.scala`) — for gh/glab commands (existing pattern)
- `ReviewStateUpdater` (`.iw/core/model/ReviewStateUpdater.scala`) — pure merge logic reused by `ReviewStateAdapter`
- `ReviewStateValidator` (`.iw/core/model/ReviewStateValidator.scala`) — validation logic reused by `ReviewStateAdapter`
- `GitRemote` (`.iw/core/model/GitRemote.scala`) — for remote URL parsing in `FileUrlBuilder`
- `Fixtures` trait (`.iw/core/test/TestFixtures.scala`) — `gitRepo` fixture for adapter tests

## Component Specifications

---

### 1. `GitAdapter` Extensions

**File:** `.iw/core/adapters/Git.scala` (modify existing)

**New methods to add:**

```scala
// Create and checkout a new branch from HEAD
def createAndCheckoutBranch(name: String, dir: os.Path): Either[String, Unit]

// Checkout an existing branch
def checkoutBranch(name: String, dir: os.Path): Either[String, Unit]

// Stage all changes (git add -A)
def stageAll(dir: os.Path): Either[String, Unit]

// Commit with message (git commit -m)
def commit(message: String, dir: os.Path): Either[String, String]  // Returns commit SHA

// Push branch to origin, optionally setting upstream
def push(branch: String, dir: os.Path, setUpstream: Boolean = false): Either[String, Unit]

// List changed files since a baseline commit (git diff --name-only)
def diffNameOnly(baseline: String, dir: os.Path): Either[String, List[String]]

// Pull current branch from origin
def pull(dir: os.Path): Either[String, Unit]

// Get full (non-abbreviated) HEAD SHA
def getFullHeadSha(dir: os.Path): Either[String, String]
```

**Pattern:** Use `ProcessAdapter.run()` for all new methods (consistent with the newer methods `getCurrentBranch`, `getHeadSha`, `hasUncommittedChanges`). Do NOT use `scala.sys.process.Process` directly (the older `getRemoteUrl` style). Return `Either[String, T]`. Use `Seq("git", "-C", dir.toString, ...)` for command construction.

**Design note:** The analysis listed separate `createBranch(name, startPoint, dir)` and `checkoutBranch` methods. We combine them into `createAndCheckoutBranch` which always branches from HEAD (no `startPoint` parameter) since the phase commands only ever create branches from current HEAD. `checkoutBranch` is kept separately for switching to existing branches. The analysis's `stageFiles(files, dir)` is dropped because the "stage everything" decision means `stageAll` is sufficient.

**Edge Cases:**
- `createAndCheckoutBranch` should use `git checkout -b` for atomic create+checkout (always from HEAD)
- `commit` should return the resulting commit SHA (parse from `git rev-parse HEAD` after commit)
- `push` with `setUpstream=true` should use `-u origin`
- `diffNameOnly` with non-existent baseline should return Left
- `stageAll` on a clean worktree is a no-op (git add -A succeeds with no output)

**Test File:** `.iw/core/test/GitTest.scala` (add to existing)

---

### 2. `GitHubClient` Extensions

**File:** `.iw/core/adapters/GitHubClient.scala` (modify existing)

**New methods to add:**

```scala
// Build gh pr create command arguments
def buildCreatePrCommand(
  repository: String,
  headBranch: String,
  baseBranch: String,
  title: String,
  body: String
): Array[String]

// Parse PR URL from gh pr create output
def parseCreatePrResponse(output: String): Either[String, String]  // Returns PR URL

// Create PR via gh CLI
def createPullRequest(
  repository: String,
  headBranch: String,
  baseBranch: String,
  title: String,
  body: String,
  dir: os.Path,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] =
    (cmd, args) => CommandRunner.execute(cmd, args)
): Either[String, String]  // Returns PR URL

// Build gh pr merge command arguments
def buildMergePrCommand(prUrl: String): Array[String]

// Merge PR via gh CLI
def mergePullRequest(
  prUrl: String,
  dir: os.Path,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] =
    (cmd, args) => CommandRunner.execute(cmd, args)
): Either[String, Unit]
```

**Pattern:** Follow existing `GitHubClient` patterns:
- Pure `buildXxxCommand` methods for testable command construction
- `parseXxxResponse` for testable output parsing
- Higher-level methods with injected `isCommandAvailable` and `execCommand` for testability (same signature as existing methods)

**Note on working directory:** The `dir: os.Path` parameter is passed to `CommandRunner.execute` via the existing `workingDir` parameter: `CommandRunner.execute(cmd, args, Some(dir.toString))`. The injected `execCommand` keeps the existing two-argument signature `(String, Array[String]) => Either[String, String]` for consistency with existing methods. The working directory is handled inside the method body, not through injection. This means the injected `execCommand` in tests doesn't need to handle `dir` — the `buildXxxCommand` methods produce the full command arrays, and tests verify those arrays.

**Edge Cases:**
- `parseCreatePrResponse`: gh outputs the PR URL on stdout (e.g., `https://github.com/owner/repo/pull/42\n`)
- `mergePullRequest` may fail if PR has merge conflicts or required checks haven't passed

**Test File:** `.iw/core/test/GitHubClientTest.scala` (add to existing)

---

### 3. `GitLabClient` Extensions

**File:** `.iw/core/adapters/GitLabClient.scala` (modify existing)

**New methods to add:**

```scala
// Build glab mr create command arguments
def buildCreateMrCommand(
  repository: String,
  headBranch: String,
  baseBranch: String,
  title: String,
  body: String
): Array[String]

// Parse MR URL from glab mr create output
def parseCreateMrResponse(output: String): Either[String, String]  // Returns MR URL

// Create MR via glab CLI
def createMergeRequest(
  repository: String,
  headBranch: String,
  baseBranch: String,
  title: String,
  body: String,
  dir: os.Path,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] =
    (cmd, args) => CommandRunner.execute(cmd, args)
): Either[String, String]  // Returns MR URL

// Build glab mr merge command arguments
def buildMergeMrCommand(mrUrl: String): Array[String]

// Merge MR via glab CLI
def mergeMergeRequest(
  mrUrl: String,
  dir: os.Path,
  isCommandAvailable: String => Boolean = CommandRunner.isCommandAvailable,
  execCommand: (String, Array[String]) => Either[String, String] =
    (cmd, args) => CommandRunner.execute(cmd, args)
): Either[String, Unit]
```

**Pattern:** Mirror `GitHubClient` extension patterns but with glab-specific syntax:
- `glab mr create --repo <repo> --head <head> --base <base> --title <title> --description <body>`
- `glab mr merge <url>`

**Edge Cases:**
- glab MR URL format: `https://gitlab.com/owner/project/-/merge_requests/42`
- glab uses `--description` (not `--body` like gh)
- glab uses `--head` and `--base` for branch specification

**Test File:** `.iw/core/test/GitLabClientTest.scala` (add to existing)

---

### 4. `FileUrlBuilder`

**File:** `.iw/core/model/FileUrlBuilder.scala` (new)

**What it does:** Pure function that derives a file-browseable URL base from a git remote URL and branch name. Used in PR body templates to link to specific files.

**API:**

```scala
package iw.core.model

object FileUrlBuilder:
  /** Build a browseable file URL base from remote URL and branch name.
    *
    * GitHub pattern: https://github.com/owner/repo/blob/{branch}/
    * GitLab pattern: https://gitlab.com/owner/project/-/blob/{branch}/
    *
    * @param remote Git remote URL (HTTPS or SSH)
    * @param branch Branch name
    * @return Right(url base) or Left(error) if remote format unrecognized
    */
  def build(remote: GitRemote, branch: String): Either[String, String]
```

**URL Patterns:**
- GitHub HTTPS: `https://github.com/owner/repo.git` → `https://github.com/owner/repo/blob/{branch}/`
- GitHub SSH: `git@github.com:owner/repo.git` → `https://github.com/owner/repo/blob/{branch}/`
- GitLab HTTPS: `https://gitlab.com/owner/project.git` → `https://gitlab.com/owner/project/-/blob/{branch}/`
- GitLab SSH: `git@gitlab.com:owner/project.git` → `https://gitlab.com/owner/project/-/blob/{branch}/`

**Note:** Use `GitRemote.host` (returns `Either[String, String]`) to distinguish GitHub vs GitLab. If `host` returns `Left` (unrecognized URL format), propagate the error as `Left`. If `host` returns `Right` with a recognized host:
- `github.com` → GitHub blob URL pattern
- Any other host (including self-hosted GitLab) → GitLab blob URL pattern (the `/-/blob/` convention)

The implementer must also extract the repo path from the remote URL (strip `.git` suffix, extract path after host). Consider extracting a helper or reusing `GitRemote.repositoryOwnerAndName` (note: that method currently rejects non-GitHub URLs, so it may need to be generalized or bypassed).

**Edge Cases:**
- Remote URL without `.git` suffix should still work
- SSH URLs (`git@host:path.git`) must be handled alongside HTTPS
- Self-hosted GitLab instances: any non-github.com host uses GitLab pattern
- `GitRemote.host` returning `Left` → propagate as `Left` (don't swallow errors)

**Test File:** `.iw/core/test/FileUrlBuilderTest.scala` (new)

---

### 5. `ReviewStateAdapter`

**File:** `.iw/core/adapters/ReviewStateAdapter.scala` (new)

**What it does:** Encapsulates the read/merge/validate/write sequence for review-state.json updates. The existing `review-state update` command performs this same sequence inline; extracting it into a shared adapter prevents duplication when phase commands also need to update review-state.

**API:**

```scala
package iw.core.adapters

import iw.core.model.{ReviewStateUpdater, ReviewStateValidator}

object ReviewStateAdapter:
  /** Update review-state.json by reading, merging, validating, and writing.
    *
    * @param path Path to review-state.json
    * @param update Update input to merge
    * @return Right(()) on success, Left(error) on failure
    */
  def update(
    path: os.Path,
    update: ReviewStateUpdater.UpdateInput
  ): Either[String, Unit]

  /** Read and parse review-state.json.
    *
    * @param path Path to review-state.json
    * @return Right(json string) on success, Left(error) if file missing or unreadable
    */
  def read(path: os.Path): Either[String, String]
```

**Behavior of `update`:**
1. Read existing JSON from `path` (error if missing)
2. Call `ReviewStateUpdater.merge(existingJson, update)` to produce updated JSON
3. Call `ReviewStateValidator.validate(updatedJson)` — returns `ValidationResult`
4. Check `validationResult.isValid`:
   - `true` → write updated JSON to `path`, return `Right(())`
   - `false` → do NOT write, return `Left` with formatted error from `validationResult.errors` (each is a `ValidationError(field, message)`)
5. Return `Left(error)` if any I/O step fails

**Key type:** `ReviewStateValidator.validate()` returns `ValidationResult(errors: List[ValidationError], warnings: List[String])` with `isValid: Boolean = errors.isEmpty`. The adapter must convert validation failures into a single `Left(String)` by formatting the error list.

**Edge Cases:**
- File doesn't exist → Left with clear error
- File exists but isn't valid JSON → Left from merge/validate
- Validation fails after merge → Left, do NOT write the invalid state
- File permissions → Left with I/O error message

**Test File:** `.iw/core/test/ReviewStateAdapterTest.scala` (new)

---

## File Locations Summary

| File | Type | Description |
|------|------|-------------|
| `.iw/core/adapters/Git.scala` | Modify | Add branch, staging, commit, push, diff, pull operations |
| `.iw/core/adapters/GitHubClient.scala` | Modify | Add PR creation and merge operations |
| `.iw/core/adapters/GitLabClient.scala` | Modify | Add MR creation and merge operations |
| `.iw/core/model/FileUrlBuilder.scala` | New | Pure URL derivation for file links |
| `.iw/core/adapters/ReviewStateAdapter.scala` | New | Shared review-state read/merge/validate/write |
| `.iw/core/test/GitTest.scala` | Modify | Tests for new GitAdapter methods |
| `.iw/core/test/GitHubClientTest.scala` | Modify | Tests for PR operations |
| `.iw/core/test/GitLabClientTest.scala` | Modify | Tests for MR operations |
| `.iw/core/test/FileUrlBuilderTest.scala` | New | Tests for URL derivation |
| `.iw/core/test/ReviewStateAdapterTest.scala` | New | Tests for review-state I/O |

## Testing Strategy

**GitAdapter extensions:**
- Use `gitRepo` fixture from `Fixtures` trait (real temp git repos)
- Test each operation with real git commands, verifying outcomes
- Follow existing `GitTest.scala` patterns exactly

**GitHubClient/GitLabClient extensions:**
- Pure `buildXxxCommand` tests: verify command array construction
- Pure `parseXxxResponse` tests: verify URL extraction from CLI output
- Integration tests with injected `execCommand`: verify orchestration flow
- Follow existing test patterns in `GitHubClientTest.scala` / `GitLabClientTest.scala`

**FileUrlBuilder:**
- Pure input→output tests (no I/O)
- Test GitHub HTTPS, GitHub SSH, GitLab HTTPS, GitLab SSH patterns
- Test with/without `.git` suffix

**ReviewStateAdapter:**
- Use `tempDir` fixture for filesystem operations
- Create sample review-state.json, apply updates, verify output
- Test error cases: missing file, invalid JSON, validation failure

**Framework:** munit `FunSuite` with `Fixtures` trait
**Run command:** `./iw test unit`

## Acceptance Criteria

### Functional
- [ ] `GitAdapter.createAndCheckoutBranch("feat-phase-01", dir)` creates and checks out a new branch
- [ ] `GitAdapter.stageAll(dir)` stages all changes
- [ ] `GitAdapter.commit("msg", dir)` commits and returns SHA
- [ ] `GitAdapter.push("feat-phase-01", dir, setUpstream=true)` pushes with -u flag
- [ ] `GitAdapter.diffNameOnly(baseline, dir)` lists changed files
- [ ] `GitAdapter.getFullHeadSha(dir)` returns full 40-char SHA
- [ ] `GitHubClient.buildCreatePrCommand(...)` produces correct gh CLI arguments
- [ ] `GitHubClient.parseCreatePrResponse(url)` extracts PR URL
- [ ] `GitHubClient.createPullRequest(...)` orchestrates prerequisite check + command + parse
- [ ] `GitHubClient.mergePullRequest(prUrl, dir)` executes merge command
- [ ] `GitLabClient.buildCreateMrCommand(...)` produces correct glab CLI arguments
- [ ] `GitLabClient.parseCreateMrResponse(url)` extracts MR URL
- [ ] `GitLabClient.createMergeRequest(...)` orchestrates prerequisite check + command + parse
- [ ] `GitLabClient.mergeMergeRequest(mrUrl, dir)` executes merge command
- [ ] `FileUrlBuilder.build(githubRemote, "main")` returns GitHub blob URL
- [ ] `FileUrlBuilder.build(gitlabRemote, "main")` returns GitLab blob URL
- [ ] `ReviewStateAdapter.update(path, input)` reads, merges, validates, and writes
- [ ] `ReviewStateAdapter.read(path)` reads file contents
- [ ] `ReviewStateAdapter.update` returns Left when file is missing
- [ ] `ReviewStateAdapter.update` does not write when validation fails

### Technical
- [ ] All new files have PURPOSE comments
- [ ] New `FileUrlBuilder` is in `iw.core.model` package (pure, no I/O)
- [ ] New `ReviewStateAdapter` is in `iw.core.adapters` package (performs I/O)
- [ ] All adapter methods return `Either[String, T]`
- [ ] Code matches existing style conventions
- [ ] No existing tests break

### Testing
- [ ] All unit tests pass: `./iw test unit`
- [ ] Full test suite passes: `./iw test` (no regressions)
- [ ] GitAdapter tests use real temp git repos (via Fixtures)
- [ ] Client tests use injected execCommand for testability
- [ ] FileUrlBuilder tests are pure (no I/O)
- [ ] ReviewStateAdapter tests use real temp files
