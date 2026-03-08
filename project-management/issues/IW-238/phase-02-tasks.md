# Phase 2 Tasks: Infrastructure Layer

**Issue:** IW-238
**Phase:** 2 of 3
**Context:** [phase-02-context.md](phase-02-context.md)

## Setup

- [ ] [setup] Verify all existing tests pass (`./iw test unit`) to establish a clean baseline
- [ ] [setup] Create `.iw/core/model/FileUrlBuilder.scala` with package declaration and PURPOSE header
- [ ] [setup] Create `.iw/core/adapters/ReviewStateAdapter.scala` with package declaration and PURPOSE header

## Tests First (TDD)

### GitAdapter extension tests

- [ ] [test] Add to `.iw/core/test/GitTest.scala`: `createAndCheckoutBranch` creates a new branch and checks it out (verify with `getCurrentBranch`)
- [ ] [test] Test: `createAndCheckoutBranch` on an already-existing branch name returns `Left`
- [ ] [test] Test: `checkoutBranch` switches to an existing branch (create branch first, checkout a different branch, then switch back)
- [ ] [test] Test: `checkoutBranch` on a non-existent branch returns `Left`
- [ ] [test] Test: `stageAll` on a repo with unstaged changes stages all files (verify via git status)
- [ ] [test] Test: `stageAll` on a clean worktree succeeds with no error (no-op case)
- [ ] [test] Test: `commit` with a staged change returns `Right` containing a 40-character SHA string
- [ ] [test] Test: `commit` with no staged changes returns `Left`
- [ ] [test] Test: `push` with `setUpstream = true` produces `git push -u origin <branch>` invocation (use a bare repo as remote)
- [ ] [test] Test: `push` on a branch with no remote configured returns `Left`
- [ ] [test] Test: `diffNameOnly` lists files changed since a baseline commit SHA
- [ ] [test] Test: `diffNameOnly` with an invalid/non-existent baseline SHA returns `Left`
- [ ] [test] Test: `diffNameOnly` with no changes since baseline returns `Right(Nil)`
- [ ] [test] Test: `pull` on a branch with nothing to pull succeeds (use bare repo remote with no new commits)
- [ ] [test] Test: `getFullHeadSha` returns a 40-character hex string (not abbreviated)
- [ ] [test] Test: `getFullHeadSha` result differs from `getHeadSha` abbreviated output when the repo has enough commits for abbreviation to differ

### GitHubClient extension tests

- [ ] [test] Add to `.iw/core/test/GitHubClientTest.scala`: `buildCreatePrCommand` produces the correct `gh pr create` argument array (repo, head, base, title, body)
- [ ] [test] Test: `buildCreatePrCommand` includes `--repo`, `--head`, `--base`, `--title`, `--body` flags with correct values
- [ ] [test] Test: `parseCreatePrResponse` extracts PR URL from plain-URL stdout (e.g., `"https://github.com/owner/repo/pull/42\n"`)
- [ ] [test] Test: `parseCreatePrResponse` returns `Left` on empty or unrecognised output
- [ ] [test] Test: `buildMergePrCommand` produces the correct `gh pr merge` argument array with `--merge` flag and PR URL
- [ ] [test] Test: `createPullRequest` calls `isCommandAvailable("gh")` and returns `Left` when `gh` is unavailable
- [ ] [test] Test: `createPullRequest` invokes `execCommand` with the result of `buildCreatePrCommand` and returns parsed PR URL on success
- [ ] [test] Test: `createPullRequest` returns `Left` when `execCommand` returns `Left`
- [ ] [test] Test: `mergePullRequest` calls `isCommandAvailable("gh")` and returns `Left` when `gh` is unavailable
- [ ] [test] Test: `mergePullRequest` invokes `execCommand` with the result of `buildMergePrCommand` and returns `Right(())` on success

### GitLabClient extension tests

- [ ] [test] Add to `.iw/core/test/GitLabClientTest.scala`: `buildCreateMrCommand` produces the correct `glab mr create` argument array (repo, head, base, title, description)
- [ ] [test] Test: `buildCreateMrCommand` uses `--description` (not `--body`) for the MR body
- [ ] [test] Test: `buildCreateMrCommand` uses `--head` and `--base` for branch specification
- [ ] [test] Test: `parseCreateMrResponse` extracts MR URL from glab output (e.g., `"https://gitlab.com/owner/project/-/merge_requests/42\n"`)
- [ ] [test] Test: `parseCreateMrResponse` returns `Left` on empty or unrecognised output
- [ ] [test] Test: `buildMergeMrCommand` produces the correct `glab mr merge` argument array with the MR URL
- [ ] [test] Test: `createMergeRequest` calls `isCommandAvailable("glab")` and returns `Left` when `glab` is unavailable
- [ ] [test] Test: `createMergeRequest` invokes `execCommand` with the result of `buildCreateMrCommand` and returns parsed MR URL on success
- [ ] [test] Test: `createMergeRequest` returns `Left` when `execCommand` returns `Left`
- [ ] [test] Test: `mergeMergeRequest` calls `isCommandAvailable("glab")` and returns `Left` when `glab` is unavailable
- [ ] [test] Test: `mergeMergeRequest` invokes `execCommand` with the result of `buildMergeMrCommand` and returns `Right(())` on success

### FileUrlBuilder tests

- [ ] [test] Create `.iw/core/test/FileUrlBuilderTest.scala` with test: GitHub HTTPS remote with `.git` suffix produces `https://github.com/owner/repo/blob/main/`
- [ ] [test] Test: GitHub SSH remote (`git@github.com:owner/repo.git`) produces the same HTTPS blob URL
- [ ] [test] Test: GitLab HTTPS remote with `.git` suffix produces `https://gitlab.com/owner/project/-/blob/main/`
- [ ] [test] Test: GitLab SSH remote (`git@gitlab.com:owner/project.git`) produces the same HTTPS GitLab blob URL
- [ ] [test] Test: Remote URL without `.git` suffix still produces a correct URL
- [ ] [test] Test: A non-github.com host (self-hosted GitLab) uses the GitLab `/-/blob/` URL pattern
- [ ] [test] Test: Branch name is correctly interpolated into the output URL
- [ ] [test] Test: Unrecognisable remote URL format (no parseable host) returns `Left`

### ReviewStateAdapter tests

- [ ] [test] Create `.iw/core/test/ReviewStateAdapterTest.scala` with test: `read` returns `Right(jsonString)` when file exists and is readable
- [ ] [test] Test: `read` returns `Left` when file does not exist
- [ ] [test] Test: `update` reads existing file, merges, validates, and writes updated content to disk
- [ ] [test] Test: `update` returns `Left` when the target file does not exist (no write attempt)
- [ ] [test] Test: `update` does NOT write to disk when validation fails after merge (file content unchanged)
- [ ] [test] Test: `update` returns `Left` with a formatted error message listing validation errors when validation fails

## Implementation

### GitAdapter extensions

- [ ] [impl] Implement `createAndCheckoutBranch(name: String, dir: os.Path): Either[String, Unit]` using `git checkout -b`
- [ ] [impl] Implement `checkoutBranch(name: String, dir: os.Path): Either[String, Unit]` using `git checkout`
- [ ] [impl] Implement `stageAll(dir: os.Path): Either[String, Unit]` using `git add -A`
- [ ] [impl] Implement `commit(message: String, dir: os.Path): Either[String, String]` using `git commit -m` and then `git rev-parse HEAD` to return the SHA
- [ ] [impl] Implement `push(branch: String, dir: os.Path, setUpstream: Boolean = false): Either[String, Unit]` using `git push` with optional `-u origin`
- [ ] [impl] Implement `diffNameOnly(baseline: String, dir: os.Path): Either[String, List[String]]` using `git diff --name-only`
- [ ] [impl] Implement `pull(dir: os.Path): Either[String, Unit]` using `git pull`
- [ ] [impl] Implement `getFullHeadSha(dir: os.Path): Either[String, String]` using `git rev-parse HEAD`
- [ ] [test] Run `GitTest` and verify all new tests pass

### GitHubClient extensions

- [ ] [impl] Implement `buildCreatePrCommand(repository, headBranch, baseBranch, title, body): Array[String]`
- [ ] [impl] Implement `parseCreatePrResponse(output: String): Either[String, String]`
- [ ] [impl] Implement `createPullRequest(...)` using `buildCreatePrCommand`, `parseCreatePrResponse`, and the injected `execCommand`
- [ ] [impl] Implement `buildMergePrCommand(prUrl: String): Array[String]`
- [ ] [impl] Implement `mergePullRequest(...)` using `buildMergePrCommand` and the injected `execCommand`
- [ ] [test] Run `GitHubClientTest` and verify all new tests pass

### GitLabClient extensions

- [ ] [impl] Implement `buildCreateMrCommand(repository, headBranch, baseBranch, title, body): Array[String]`
- [ ] [impl] Implement `parseCreateMrResponse(output: String): Either[String, String]`
- [ ] [impl] Implement `createMergeRequest(...)` using `buildCreateMrCommand`, `parseCreateMrResponse`, and the injected `execCommand`
- [ ] [impl] Implement `buildMergeMrCommand(mrUrl: String): Array[String]`
- [ ] [impl] Implement `mergeMergeRequest(...)` using `buildMergeMrCommand` and the injected `execCommand`
- [ ] [test] Run `GitLabClientTest` and verify all new tests pass

### FileUrlBuilder

- [ ] [impl] Implement `FileUrlBuilder.build(remote: GitRemote, branch: String): Either[String, String]` with GitHub and GitLab URL patterns
- [ ] [test] Run `FileUrlBuilderTest` and verify all tests pass

### ReviewStateAdapter

- [ ] [impl] Implement `ReviewStateAdapter.read(path: os.Path): Either[String, String]`
- [ ] [impl] Implement `ReviewStateAdapter.update(path: os.Path, update: ReviewStateUpdater.UpdateInput): Either[String, Unit]` with read → merge → validate → conditional write sequence
- [ ] [test] Run `ReviewStateAdapterTest` and verify all tests pass

## Integration

- [ ] [int] Run full unit test suite (`./iw test unit`) -- all tests pass, no regressions
- [ ] [int] Run full test suite (`./iw test`) -- no regressions
- [ ] [int] Verify all new files have PURPOSE comments (two lines starting with `// PURPOSE:`)
- [ ] [int] Verify `FileUrlBuilder` is in the `iw.core.model` package (pure, no I/O)
- [ ] [int] Verify `ReviewStateAdapter` is in the `iw.core.adapters` package (performs I/O)
- [ ] [int] Verify all new adapter methods use `ProcessAdapter.run()` (not `scala.sys.process.Process` directly)
- [ ] [int] Verify all new adapter methods return `Either[String, T]`

**Phase Status:** Not Started
