# Phase 2 Tasks: Infrastructure Layer

**Issue:** IW-238
**Phase:** 2 of 3
**Context:** [phase-02-context.md](phase-02-context.md)

## Setup

- [x] [setup] Verify all existing tests pass (`./iw test unit`) to establish a clean baseline
- [x] [setup] Create `.iw/core/model/FileUrlBuilder.scala` with package declaration and PURPOSE header
- [x] [setup] Create `.iw/core/adapters/ReviewStateAdapter.scala` with package declaration and PURPOSE header

## Tests First (TDD)

### GitAdapter extension tests

- [x] [test] Add to `.iw/core/test/GitTest.scala`: `createAndCheckoutBranch` creates a new branch and checks it out (verify with `getCurrentBranch`)
- [x] [test] Test: `createAndCheckoutBranch` on an already-existing branch name returns `Left`
- [x] [test] Test: `checkoutBranch` switches to an existing branch (create branch first, checkout a different branch, then switch back)
- [x] [test] Test: `checkoutBranch` on a non-existent branch returns `Left`
- [x] [test] Test: `stageAll` on a repo with unstaged changes stages all files (verify via git status)
- [x] [test] Test: `stageAll` on a clean worktree succeeds with no error (no-op case)
- [x] [test] Test: `commit` with a staged change returns `Right` containing a 40-character SHA string
- [x] [test] Test: `commit` with no staged changes returns `Left`
- [x] [test] Test: `push` with `setUpstream = true` produces `git push -u origin <branch>` invocation (use a bare repo as remote)
- [x] [test] Test: `push` on a branch with no remote configured returns `Left`
- [x] [test] Test: `diffNameOnly` lists files changed since a baseline commit SHA
- [x] [test] Test: `diffNameOnly` with an invalid/non-existent baseline SHA returns `Left`
- [x] [test] Test: `diffNameOnly` with no changes since baseline returns `Right(Nil)`
- [x] [test] Test: `pull` on a branch with nothing to pull succeeds (use bare repo remote with no new commits)
- [x] [test] Test: `getFullHeadSha` returns a 40-character hex string (not abbreviated)
- [x] [test] Test: `getFullHeadSha` result differs from `getHeadSha` abbreviated output when the repo has enough commits for abbreviation to differ

### GitHubClient extension tests

- [x] [test] Add to `.iw/core/test/GitHubClientTest.scala`: `buildCreatePrCommand` produces the correct `gh pr create` argument array (repo, head, base, title, body)
- [x] [test] Test: `buildCreatePrCommand` includes `--repo`, `--head`, `--base`, `--title`, `--body` flags with correct values
- [x] [test] Test: `parseCreatePrResponse` extracts PR URL from plain-URL stdout (e.g., `"https://github.com/owner/repo/pull/42\n"`)
- [x] [test] Test: `parseCreatePrResponse` returns `Left` on empty or unrecognised output
- [x] [test] Test: `buildMergePrCommand` produces the correct `gh pr merge` argument array with `--merge` flag and PR URL
- [x] [test] Test: `createPullRequest` calls `isCommandAvailable("gh")` and returns `Left` when `gh` is unavailable
- [x] [test] Test: `createPullRequest` invokes `execCommand` with the result of `buildCreatePrCommand` and returns parsed PR URL on success
- [x] [test] Test: `createPullRequest` returns `Left` when `execCommand` returns `Left`
- [x] [test] Test: `mergePullRequest` calls `isCommandAvailable("gh")` and returns `Left` when `gh` is unavailable
- [x] [test] Test: `mergePullRequest` invokes `execCommand` with the result of `buildMergePrCommand` and returns `Right(())` on success

### GitLabClient extension tests

- [x] [test] Add to `.iw/core/test/GitLabClientTest.scala`: `buildCreateMrCommand` produces the correct `glab mr create` argument array (repo, head, base, title, description)
- [x] [test] Test: `buildCreateMrCommand` uses `--description` (not `--body`) for the MR body
- [x] [test] Test: `buildCreateMrCommand` uses `--head` and `--base` for branch specification
- [x] [test] Test: `parseCreateMrResponse` extracts MR URL from glab output (e.g., `"https://gitlab.com/owner/project/-/merge_requests/42\n"`)
- [x] [test] Test: `parseCreateMrResponse` returns `Left` on empty or unrecognised output
- [x] [test] Test: `buildMergeMrCommand` produces the correct `glab mr merge` argument array with the MR URL
- [x] [test] Test: `createMergeRequest` calls `isCommandAvailable("glab")` and returns `Left` when `glab` is unavailable
- [x] [test] Test: `createMergeRequest` invokes `execCommand` with the result of `buildCreateMrCommand` and returns parsed MR URL on success
- [x] [test] Test: `createMergeRequest` returns `Left` when `execCommand` returns `Left`
- [x] [test] Test: `mergeMergeRequest` calls `isCommandAvailable("glab")` and returns `Left` when `glab` is unavailable
- [x] [test] Test: `mergeMergeRequest` invokes `execCommand` with the result of `buildMergeMrCommand` and returns `Right(())` on success

### FileUrlBuilder tests

- [x] [test] Create `.iw/core/test/FileUrlBuilderTest.scala` with test: GitHub HTTPS remote with `.git` suffix produces `https://github.com/owner/repo/blob/main/`
- [x] [test] Test: GitHub SSH remote (`git@github.com:owner/repo.git`) produces the same HTTPS blob URL
- [x] [test] Test: GitLab HTTPS remote with `.git` suffix produces `https://gitlab.com/owner/project/-/blob/main/`
- [x] [test] Test: GitLab SSH remote (`git@gitlab.com:owner/project.git`) produces the same HTTPS GitLab blob URL
- [x] [test] Test: Remote URL without `.git` suffix still produces a correct URL
- [x] [test] Test: A non-github.com host (self-hosted GitLab) uses the GitLab `/-/blob/` URL pattern
- [x] [test] Test: Branch name is correctly interpolated into the output URL
- [x] [test] Test: Unrecognisable remote URL format (no parseable host) returns `Left`

### ReviewStateAdapter tests

- [x] [test] Create `.iw/core/test/ReviewStateAdapterTest.scala` with test: `read` returns `Right(jsonString)` when file exists and is readable
- [x] [test] Test: `read` returns `Left` when file does not exist
- [x] [test] Test: `update` reads existing file, merges, validates, and writes updated content to disk
- [x] [test] Test: `update` returns `Left` when the target file does not exist (no write attempt)
- [x] [test] Test: `update` does NOT write to disk when validation fails after merge (file content unchanged)
- [x] [test] Test: `update` returns `Left` with a formatted error message listing validation errors when validation fails

## Implementation

### GitAdapter extensions

- [x] [impl] Implement `createAndCheckoutBranch(name: String, dir: os.Path): Either[String, Unit]` using `git checkout -b`
- [x] [impl] Implement `checkoutBranch(name: String, dir: os.Path): Either[String, Unit]` using `git checkout`
- [x] [impl] Implement `stageAll(dir: os.Path): Either[String, Unit]` using `git add -A`
- [x] [impl] Implement `commit(message: String, dir: os.Path): Either[String, String]` using `git commit -m` and then `git rev-parse HEAD` to return the SHA
- [x] [impl] Implement `push(branch: String, dir: os.Path, setUpstream: Boolean = false): Either[String, Unit]` using `git push` with optional `-u origin`
- [x] [impl] Implement `diffNameOnly(baseline: String, dir: os.Path): Either[String, List[String]]` using `git diff --name-only`
- [x] [impl] Implement `pull(dir: os.Path): Either[String, Unit]` using `git pull`
- [x] [impl] Implement `getFullHeadSha(dir: os.Path): Either[String, String]` using `git rev-parse HEAD`
- [x] [test] Run `GitTest` and verify all new tests pass

### GitHubClient extensions

- [x] [impl] Implement `buildCreatePrCommand(repository, headBranch, baseBranch, title, body): Array[String]`
- [x] [impl] Implement `parseCreatePrResponse(output: String): Either[String, String]`
- [x] [impl] Implement `createPullRequest(...)` using `buildCreatePrCommand`, `parseCreatePrResponse`, and the injected `execCommand`
- [x] [impl] Implement `buildMergePrCommand(prUrl: String): Array[String]`
- [x] [impl] Implement `mergePullRequest(...)` using `buildMergePrCommand` and the injected `execCommand`
- [x] [test] Run `GitHubClientTest` and verify all new tests pass

### GitLabClient extensions

- [x] [impl] Implement `buildCreateMrCommand(repository, headBranch, baseBranch, title, body): Array[String]`
- [x] [impl] Implement `parseCreateMrResponse(output: String): Either[String, String]`
- [x] [impl] Implement `createMergeRequest(...)` using `buildCreateMrCommand`, `parseCreateMrResponse`, and the injected `execCommand`
- [x] [impl] Implement `buildMergeMrCommand(mrUrl: String): Array[String]`
- [x] [impl] Implement `mergeMergeRequest(...)` using `buildMergeMrCommand` and the injected `execCommand`
- [x] [test] Run `GitLabClientTest` and verify all new tests pass

### FileUrlBuilder

- [x] [impl] Implement `FileUrlBuilder.build(remote: GitRemote, branch: String): Either[String, String]` with GitHub and GitLab URL patterns
- [x] [test] Run `FileUrlBuilderTest` and verify all tests pass

### ReviewStateAdapter

- [x] [impl] Implement `ReviewStateAdapter.read(path: os.Path): Either[String, String]`
- [x] [impl] Implement `ReviewStateAdapter.update(path: os.Path, update: ReviewStateUpdater.UpdateInput): Either[String, Unit]` with read → merge → validate → conditional write sequence
- [x] [test] Run `ReviewStateAdapterTest` and verify all tests pass

## Integration

- [x] [int] Run full unit test suite (`./iw test unit`) -- all tests pass, no regressions
- [x] [int] Run full test suite (`./iw test`) -- no regressions
- [x] [int] Verify all new files have PURPOSE comments (two lines starting with `// PURPOSE:`)
- [x] [int] Verify `FileUrlBuilder` is in the `iw.core.model` package (pure, no I/O)
- [x] [int] Verify `ReviewStateAdapter` is in the `iw.core.adapters` package (performs I/O)
- [x] [int] Verify all new adapter methods use `ProcessAdapter.run()` (not `scala.sys.process.Process` directly)
- [x] [int] Verify all new adapter methods return `Either[String, T]`

**Phase Status:** Complete
