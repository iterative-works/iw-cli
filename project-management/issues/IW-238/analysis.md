# Technical Analysis: Add deterministic phase lifecycle commands

**Issue:** IW-238
**Created:** 2026-03-07
**Status:** Draft

## Problem Statement

LLM agents executing the iw-cli implementation workflow currently construct and run ~20 individual bash/git/gh commands for deterministic phase lifecycle operations: creating sub-branches, staging files, constructing commit messages, creating PRs, and updating review-state. This manual orchestration is unreliable (wrong commands, forgotten steps, wrong order), wasteful (burns tokens on boilerplate bash execution), slow (each bash call is a round-trip), and hard to debug (mid-workflow git failures leave inconsistent state).

The user need is clear: LLM agents should call a single `iw` command for each lifecycle transition (start phase, commit phase, create PR) instead of assembling shell scripts on the fly. This shifts deterministic work into reliable, testable Scala code and frees agents to focus on actual implementation.

## Proposed Solution

### High-Level Approach

Introduce three new `iw` subcommands: `phase-start`, `phase-commit`, and `phase-pr`. Each encapsulates a deterministic sequence of git/gh/filesystem operations behind a single CLI invocation, outputting structured JSON for machine consumption.

The commands follow the established FCIS pattern: pure domain logic in `model/` (branch name derivation, commit message construction, phase task file parsing/rewriting), I/O operations in `adapters/` (git branch creation, staging, committing, pushing, PR creation), and imperative orchestration in `commands/` scripts. The commands will use the existing `review-state update` command (via shell invocation) to update review-state, rather than duplicating that logic.

Each command is a standalone `.iw/commands/*.scala` file, consistent with the existing command pattern. No subcommand dispatcher is needed since these are three distinct, self-contained operations rather than variations on one concept (unlike `review-state validate/write/update` which all manipulate the same resource).

### Why This Approach

Three standalone commands rather than a `phase` subcommand dispatcher because:
1. Each command has distinct inputs, outputs, and side effects
2. The existing dispatcher pattern (review-state.scala) adds indirection and an extra scala-cli invocation
3. Standalone commands are simpler and match the majority of existing commands (start, open, rm, etc.)

Using direct library calls for review-state changes via a shared `ReviewStateAdapter`:
1. Faster than subprocess invocation (no extra scala-cli process)
2. Extracts the read/merge/validate/write sequence into a shared adapter
3. Both `review-state update` command and phase commands use the same adapter

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer (`model/`)

**Components:**
- `PhaseBranch` -- value object for phase sub-branch name derivation from feature branch and phase number
- `CommitMessage` -- pure construction of structured commit messages from title and item list
- `PhaseTaskFile` -- pure functions for parsing and rewriting phase task markdown (update Phase Status line, update `[reviewed]` checkboxes from `[impl]` checkboxes)
- `PhaseOutput` -- data types for the JSON output structures of each command

**Responsibilities:**
- Derive sub-branch name: `{feature-branch}-phase-{NN}` from feature branch name and phase number
- Validate phase number format (zero-padded two digits)
- Construct commit message body from title and optional item list
- Parse phase-NN-tasks.md to find and update "Phase Status" line to "Complete"
- Parse and update `[reviewed]` checkboxes: mark all checked `[impl]` as `[reviewed]`
- Define JSON output shapes for each command (typed case classes)
- Determine which files are "tracking files" to exclude from staging (tasks.md, phase-*.md, review-state.json, etc.)

**Estimated Effort:** 4-6 hours
**Complexity:** Moderate (the checkbox rewriting logic and file exclusion rules need careful specification)

---

### Infrastructure Layer (`adapters/`)

**Components:**
- Extend `GitAdapter` with:
  - `createBranch(name, startPoint, dir)` -- create a new branch from current HEAD or a start point
  - `checkoutBranch(name, dir)` -- switch to an existing branch
  - `stageFiles(files, dir)` -- `git add` specific files
  - `stageAll(dir)` -- `git add -A`
  - `commit(message, dir)` -- `git commit -m`
  - `push(branch, dir, setUpstream)` -- `git push` with optional `-u`
  - `diffNameOnly(baseline, dir)` -- list changed files since a commit
  - `pull(dir)` -- `git pull` on current branch
  - `getFullHeadSha(dir)` -- full (not abbreviated) SHA for baseline tracking
- Extend `GitHubClient` with:
  - `createPullRequest(repo, headBranch, baseBranch, title, body, dir)` -- `gh pr create`
  - `mergePullRequest(prUrl, dir)` -- `gh pr merge --merge`
  - `parseCreatePrResponse(output)` -- extract PR URL from gh output
- Extend `GitLabClient` with:
  - `createMergeRequest(repo, headBranch, baseBranch, title, body, dir)` -- `glab mr create`
  - `mergeMergeRequest(mrUrl, dir)` -- `glab mr merge`
  - `parseCreateMrResponse(output)` -- extract MR URL from glab output
- `FileUrlBuilder` -- derive GitHub/GitLab file URL base from remote URL and branch name
- `ReviewStateAdapter` -- shared read/merge/validate/write sequence for review-state.json (extracted from `review-state update` command to avoid duplication)

**Responsibilities:**
- Execute all git operations (branch creation, staging, committing, pushing, pulling)
- Execute gh/glab CLI operations (PR/MR creation, PR/MR merge)
- Return `Either[String, T]` for all operations, consistent with existing adapters
- Build file-browseable URLs for PR body templates (GitHub and GitLab patterns)
- Provide shared review-state read/merge/validate/write operations

**Estimated Effort:** 6-9 hours
**Complexity:** Moderate (many operations but each is straightforward; the PR creation/merge flow has more surface area)

---

### Application Layer (embedded in commands)

**Note:** This project does not have a separate application/service layer. Command scripts in `.iw/commands/` serve as the orchestration layer, directly composing domain model functions with adapter I/O calls. This is consistent with all existing commands.

**Components:**
- Orchestration logic within each of the three command scripts
- Error handling and early-exit flow
- JSON output construction and printing

**Responsibilities:**
- `phase-start`: validate inputs -> check branch state -> create sub-branch -> capture baseline SHA -> update review-state via shared adapter -> print JSON output
- `phase-commit`: validate inputs -> stage all changes -> construct commit message -> execute commit -> update phase task file -> update reviewed checkboxes -> print JSON output
- `phase-pr`: validate inputs -> determine feature branch -> push sub-branch -> create PR/MR (GitHub or GitLab) with templated body -> update review-state via shared adapter -> optionally merge + checkout + pull -> print JSON output

**Estimated Effort:** (included in Presentation Layer estimate since commands ARE the presentation)
**Complexity:** N/A (folded into Presentation Layer)

---

### Presentation Layer (`commands/`)

**Components:**
- `phase-start.scala` -- command script
- `phase-commit.scala` -- command script
- `phase-pr.scala` -- command script

**Responsibilities:**
- Parse CLI arguments (issue-id, phase-num, --title, --items, --batch)
- Orchestrate domain + adapter calls in sequence with error handling
- Print structured JSON to stdout on success
- Print human-readable error/info messages to stderr via `Output.*`
- Update review-state via shared `ReviewStateAdapter`
- Exit with appropriate codes (0 success, 1 error)

**Estimated Effort:** 6-8 hours
**Complexity:** Moderate (three commands, each with multi-step orchestration; `phase-pr` has the most complexity due to the optional `--batch` merge flow)

---

## Technical Decisions

### Patterns

- **FCIS**: Pure domain logic (branch name derivation, commit message construction, markdown parsing) in `model/`, I/O in `adapters/`, orchestration in `commands/`
- **Either-based error handling**: All adapter operations return `Either[String, T]`, commands use early-exit on `Left`
- **Shared adapter for review-state**: Commands use a shared `ReviewStateAdapter` for read/merge/validate/write operations, avoiding subprocess overhead while preventing logic duplication
- **JSON output to stdout**: Machine-consumable output via `ujson` on stdout; human messages via `Output.*` on stderr

### Technology Choices

- **Frameworks/Libraries**: Existing stack (os-lib for filesystem, ujson for JSON, scala-cli for execution)
- **Data Storage**: No new storage; reads/writes existing files (phase-NN-tasks.md, review-state.json) and git operations
- **External Systems**: git CLI, gh CLI (GitHub)

### Integration Points

- `phase-start` creates the branch that `phase-commit` and `phase-pr` operate on
- `phase-commit` stages and commits work done between `phase-start` and itself
- `phase-pr` pushes the branch created by `phase-start` and creates a PR
- All three commands update review-state.json via shared `ReviewStateAdapter` (direct library call)
- All three commands infer issue ID from branch name (fallback) or accept it as argument

## Technical Risks & Uncertainties

### RESOLVED: Phase task file format and location

**Decision:** Use existing patterns from the codebase.
- Path: `project-management/issues/{ISSUE-ID}/phase-{NN}-tasks.md`
- Phase Status line: `**Phase Status:** Complete` (at end of file)
- Checkboxes use inline tags: `- [x] [impl]` and `- [x] [reviewed]` (e.g., `- [x] [test] [x] [reviewed] Write test: ...`)

---

### RESOLVED: Tracking file exclusion list

**Decision:** No exclusion. Stage everything including tracking files. All files changed during a phase (code + tracking) are committed together.

---

### RESOLVED: Review-state update invocation method

**Decision:** Direct library call to `ReviewStateUpdater.merge()` + `ReviewStateValidator.validate()` + `os.write.over()`. Faster, no subprocess overhead, and the commands already share the core library via scala-cli compilation.

This means extracting a shared adapter for the read/merge/validate/write sequence (Option C from original analysis) to avoid duplicating file-location logic.

---

### RESOLVED: GitHub-only or multi-tracker support for phase-pr

**Decision:** Support both GitHub (`gh`) and GitLab (`glab`) from the start. This requires:
- Extending `GitLabClient` with PR (merge request) creation and merge operations
- `FileUrlBuilder` supporting both GitHub and GitLab URL patterns
- Tracker-type detection in `phase-pr` to dispatch to the right client

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer: 4-6 hours
- Infrastructure Layer: 6-9 hours
- Presentation Layer: 6-8 hours

**Total Range:** 16 - 23 hours

**Confidence:** Medium

**Reasoning:**
- The domain logic (branch naming, commit messages) is well-defined and straightforward
- The adapter extensions are numerous but individually simple (wrapping git/gh commands)
- The `phase-pr` command has the most complexity due to optional batch mode (merge + checkout + pull)
- Uncertainty in phase task file format could add 2-3 hours if the format is more complex than expected
- Testing overhead (unit tests for pure logic, E2E tests for command integration) is significant given three commands
- Review-state integration approach (subprocess vs direct call) affects implementation time

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: `PhaseBranchTest` -- branch name derivation from various feature branch formats
- Unit: `CommitMessageTest` -- message construction with/without items
- Unit: `PhaseTaskFileTest` -- markdown parsing and rewriting (Phase Status update, checkbox updates)
- Unit: `PhaseOutputTest` -- JSON serialization of output structures
- Pure function testing: all domain tests are pure input->output, no mocking needed

**Infrastructure Layer:**
- Unit: `GitAdapter` extensions tested with real temp git repos (following existing `GitTest` pattern with `Fixtures.gitRepo`)
- Unit: `GitHubClient.buildCreatePrCommand` / `parseCreatePrResponse` -- pure argument construction and response parsing (following existing pattern)
- Integration: `GitHubClient.createPullRequest` -- tested with injected `execCommand` function (following existing pattern)
- Unit: `FileUrlBuilder` -- pure URL construction from remote URL and branch

**Presentation Layer (Commands):**
- E2E (BATS): `phase-start.bats` -- test branch creation in temp git repo, verify JSON output structure, verify branch exists after command
- E2E (BATS): `phase-commit.bats` -- test staging and committing in temp git repo, verify commit message format, verify JSON output
- E2E (BATS): `phase-pr.bats` -- limited testability without real GitHub repo; test argument validation, error cases, and possibly mock gh responses
- E2E: Verify error messages and exit codes for invalid inputs (wrong phase format, no changes to commit, etc.)

**Test Data Strategy:**
- Temp git repos via `Fixtures.gitRepo` for unit/integration tests
- Sample phase-NN-tasks.md files in `test/resources/` for markdown parsing tests
- BATS temp directories for E2E tests (following existing pattern)

**Regression Coverage:**
- Existing `review-state.bats` tests should continue to pass (review-state update is used by phase commands)
- Existing `GitTest` and `GitWorktreeAdapterTest` should continue to pass (adapter extensions only add methods)
- Run full test suite (`iw test`) before and after

## Deployment Considerations

### Database Changes
None. No database is involved.

### Configuration Changes
None. The commands use existing configuration (`config.conf` for tracker type, repository).

### Rollout Strategy
The three commands are additive -- no existing commands are modified. They can be deployed independently. Workflow skills can be updated to use the new commands after deployment.

### Rollback Plan
Remove the three command files and any new adapter/model files. No state migration needed.

## Dependencies

### Prerequisites
- Existing `review-state update` command must be working (it is)
- `gh` CLI must be installed and authenticated for `phase-pr` (already a prerequisite for GitHub-tracked projects)
- Git must be available (already a prerequisite)

### Layer Dependencies
- Domain Layer has no dependencies on other layers (pure)
- Infrastructure Layer depends on Domain Layer (for value objects like `PhaseBranch`)
- Presentation Layer depends on both Domain and Infrastructure layers
- Domain and Infrastructure can be implemented in parallel since Infrastructure only needs Domain types (not logic)

### External Blockers
- None. All dependencies are already in the project.

## Risks & Mitigations

### Risk 1: Phase task file format is underspecified
**Likelihood:** High
**Impact:** Medium
**Mitigation:** Examine actual phase-NN-tasks.md files from recent workflow executions to pin down the exact format before implementing `PhaseTaskFile` parsing. Start with the simplest viable parser and iterate.

### Risk 2: Shared ReviewStateAdapter refactoring may break existing review-state commands
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Extract the adapter as a new file, then have the existing `review-state update` command delegate to it. Existing tests must continue passing.

### Risk 3: `phase-pr --batch` merge could fail mid-way, leaving inconsistent state
**Likelihood:** Medium
**Impact:** Medium (PR created but not merged, or merged but feature branch not checked out)
**Mitigation:** Implement the batch steps sequentially with clear error messages at each step. The user can manually complete any remaining steps. Consider outputting the PR URL before attempting merge so it is not lost on failure.

### Risk 4: Concurrent phase operations on the same branch
**Likelihood:** Low
**Impact:** Low (phases are sequential by design)
**Mitigation:** No locking needed. Each phase operates on its own sub-branch. Document that phases should be run sequentially.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** -- Pure logic with no dependencies. Branch name derivation, commit message construction, and markdown parsing can be fully tested in isolation. Foundation for all other layers.
2. **Infrastructure Layer** -- Extends existing adapters with git/gh operations. Depends on Domain types but not on Domain logic. Can be tested with temp git repos.
3. **Presentation Layer** -- The three command scripts that orchestrate Domain + Infrastructure. Must come last as they depend on both layers. E2E tests validate the full flow.

**Ordering Rationale:**
- Domain and Infrastructure are largely independent and could be parallelized by two developers
- Presentation depends on both and must come after
- Within Presentation, `phase-start` should be implemented first (simplest), then `phase-commit` (medium), then `phase-pr` (most complex with --batch mode)
- Each command can be implemented and tested independently

## Documentation Requirements

- [x] Code documentation (PURPOSE comments on all new files, inline comments for complex logic)
- [ ] API documentation (update llms.txt and relevant docs/ files for new adapter methods)
- [ ] Architecture decision record (document the subprocess vs direct call decision for review-state)
- [ ] User-facing documentation (update SKILL.md files to document new commands)
- [ ] Migration guide (not needed -- purely additive)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders (especially phase task file format)
2. Run **wf-create-tasks** with the issue ID
3. Run **wf-implement** for layer-by-layer implementation
