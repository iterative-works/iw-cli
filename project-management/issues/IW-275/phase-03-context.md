# Phase 3: batch-implement command script

**Issue:** IW-275
**Phase:** 3 of 3
**Story:** The `iw batch-implement` command orchestrates unattended phase-by-phase implementation by invoking `claude -p` per phase, handling outcomes, merging PRs, and advancing through tasks.md

## Goals

Create `.iw/commands/batch-implement.scala` — the imperative shell that ties together the pure decision functions from Phase 2 (`BatchImplement`) with existing I/O adapters (`ProcessAdapter`, `GitAdapter`, `ReviewStateAdapter`, forge clients). This is a direct port of the existing `batch-implement.sh` shell script (473 lines), eliminating the `jq` dependency and gaining type safety.

## Scope

### In Scope
- `.iw/commands/batch-implement.scala` command script with:
  - Argument parsing: `ISSUE_ID` (positional), `ag|wf` (positional), `--max-budget-usd N` (named), `--model MODEL` (named, default "opus"), `--max-turns N` (named, default 50), `--max-retries N` (named, default 1)
  - Auto-detect issue ID from branch name if not provided
  - Auto-detect workflow type from `review-state.json` `workflow_type` field if not provided
  - Pre-flight checks: `tasks.md` exists, `claude` CLI available, forge CLI (`gh`/`glab`) available, clean working tree
  - Phase loop: find next unchecked phase, invoke `claude -p`, handle outcome, advance or fail
  - Recovery loop: if claude exits without reaching a terminal status, resume with targeted prompts (max configurable attempts)
  - PR merging via `ProcessAdapter.run` with forge CLI: regular merge (not squash), matching analysis decision. GitHub: `gh pr merge --merge <url>`, GitLab: `glab mr merge <id> --yes`
  - Branch advancement after merge: fetch and reset feature branch to match remote
  - Task completion: mark phase done in `tasks.md` using `BatchImplement.markPhaseComplete`
  - Review state updates after merge
  - Completion flow: after all phases done, invoke `claude -p` for the final wf/ag-implement run
  - Logging to `{issue-dir}/batch-implement.log`
  - Commit any uncommitted changes claude left behind
- `.iw/test/batch-implement.bats` E2E tests for:
  - Argument parsing and auto-detection
  - Pre-flight validation errors (missing tasks.md, missing CLI tools, dirty working tree)
  - Happy path with a stubbed `claude` script

### Out of Scope
- Modifying `BatchImplement` model functions (Phase 2)
- Adding new adapter methods (all I/O uses existing adapters)
- Modifying `MarkdownTaskParser` (Phase 1)
- Interactive mode or human checkpoints (this is batch/unattended by design)
- Cost/turn tracking from claude JSON output (deferred — success/failure determined by exit code and review-state status)

## Dependencies

### From Prior Phases
- **Phase 1:** `MarkdownTaskParser` in `iw.core.model` with `parsePhaseIndex(lines): List[PhaseIndexEntry]`
- **Phase 2:** `BatchImplement` in `iw.core.model` with:
  - `decideOutcome(status: String): PhaseOutcome` — MergePR, MarkDone, Recover (no prompt field), Fail(reason)
  - `isTerminal(status: String): Boolean` — true only for MarkDone statuses (phase_merged, all_complete)
  - `nextPhase(phases: List[PhaseIndexEntry]): Option[Int]`
  - `resolveWorkflowCode(workflowType: Option[String]): Either[String, String]`
  - `markPhaseComplete(tasksContent: String, phaseNumber: Int): Either[String, String]`

### Existing Adapters Used
- `ProcessAdapter.runStreaming(command, timeoutMs)` — invoke `claude -p` with real-time output, returns exit code
- `ProcessAdapter.run(command)` — invoke `gh`/`glab` for PR merge, returns `ProcessResult`
- `ProcessAdapter.commandExists(command)` — pre-flight CLI checks
- `GitAdapter.getCurrentBranch(dir)` — auto-detect issue ID
- `GitAdapter.hasUncommittedChanges(dir)` — clean working tree check
- `GitAdapter.fetchAndReset(branch, dir)` — advance feature branch after merge
- `GitAdapter.checkoutBranch(name, dir)` — switch to feature branch
- `GitAdapter.stageAll(dir)` + `GitAdapter.commit(message, dir)` — commit leftovers
- `GitAdapter.getRemoteUrl(dir)` — detect forge type
- `ReviewStateAdapter.read(path)` — read review-state.json as string
- `ReviewStateAdapter.update(path, input)` — update review-state after merge
- `ConfigFileRepository.read(path)` — read project config for tracker type
- `ForgeType.resolve(remoteOpt: Option[GitRemote], trackerType: IssueTrackerType)` — determine GitHub vs GitLab

### Important: Workflow Type Source
`ProjectConfiguration` does NOT have a `workflowType` field. The workflow type lives only in `review-state.json` as the `workflow_type` JSON field. Read it via `ujson` after `ReviewStateAdapter.read()`.

## Approach

### Step 1: Argument parsing and auto-detection

Follow the existing command pattern:
```scala
@main def batchImplement(args: String*): Unit =
  val argList = args.toList
```

Parse positional args (issue ID, workflow code) and named args (`--max-budget-usd`, `--model`, `--max-turns`, `--max-retries`). Auto-detect issue ID from branch via `IssueId.fromBranch`. Auto-detect workflow from review-state.json by parsing JSON with ujson:
```scala
val json = ujson.read(reviewStateJson)
val workflowType = json.obj.get("workflow_type").map(_.str)
val workflowCode = CommandHelpers.exitOnError(BatchImplement.resolveWorkflowCode(workflowType))
```

### Step 2: Pre-flight checks

1. Check `tasks.md` exists
2. Check `claude` CLI available
3. Detect forge type, check forge CLI available
4. Check clean working tree

### Step 3: Phase loop

```
while next unchecked phase exists:
  1. Run claude -p "/iterative-works:{wf|ag}-implement {ISSUE_ID} --phase {N}"
     via ProcessAdapter.runStreaming (30-minute timeout)
  2. Commit any uncommitted changes claude left
  3. Read review-state.json status via ReviewStateAdapter.read + ujson
  4. Decide outcome via BatchImplement.decideOutcome(status):
     - MergePR: merge via forge CLI, checkout feature branch, fetch+reset, mark done
     - MarkDone: already merged, mark done in tasks.md
     - Recover: enter recovery loop (see below)
     - Fail: exit with error
  5. Update tasks.md via BatchImplement.markPhaseComplete, commit, log

Recovery loop (when outcome is Recover):
  - Build a status-specific prompt inline (e.g. "implementing" → "Continue the implementation workflow...")
  - Resume claude session with ProcessAdapter.runStreaming using --resume flag
  - Commit any uncommitted changes, re-read status
  - Repeat up to MAX_RECOVERY_ATTEMPTS times
  - If still Recover after max attempts, fail with error
```

**Recovery prompt construction:** Since `PhaseOutcome.Recover` has no prompt field, the command script builds prompts inline based on the raw status string — similar to the `recovery_prompt_for_status` function in the original shell script. This is intentionally in the command script (imperative shell) rather than the model (functional core), since the prompt text is a presentation concern.

### Step 4: Completion flow

After all phases complete, invoke `claude -p "/iterative-works:{wf|ag}-implement {ISSUE_ID}"` (without `--phase`) to run the completion flow (release notes, final PR).

### Step 5: Logging

All output goes to both stderr (user-visible) and `batch-implement.log` in the issue directory.

## Files to Create

### `.iw/commands/batch-implement.scala`
- Package: none (command script with `@main`)
- Imports: `iw.core.model.*`, `iw.core.adapters.*`, `iw.core.output.*`
- PURPOSE comments as required
- Pattern matches existing commands (phase-advance.scala, phase-pr.scala)

### `.iw/test/batch-implement.bats`
- PURPOSE comments
- Tests use `IW_SERVER_DISABLED=1`
- Tests create temporary git repos
- Claude CLI is NOT called — tests use a stub script

## Files NOT Modified
- No changes to existing model, adapter, or other command files

## Testing Strategy

### E2E Tests (`.iw/test/batch-implement.bats`)

**Pre-flight validation tests:**
1. Missing tasks.md → error with helpful message
2. Missing claude CLI → error mentioning claude
3. Dirty working tree → error mentioning commit/stash
4. No issue ID detectable → error with usage hint

**Argument parsing tests:**
5. Issue ID from positional arg
6. Workflow code from positional arg
7. Auto-detect issue ID from branch name

**Note on happy path:** Full happy-path E2E testing requires a real GitHub remote and `gh` CLI with auth. These tests are limited to argument validation and pre-flight checks. The decision logic is fully covered by unit tests in Phase 2.

### Test Data Strategy
- Temporary git repos with `tasks.md` and `review-state.json`
- Stub `claude` script that writes expected review-state
- No real claude invocations in tests

### Verification Commands
```bash
scala-cli compile --scalac-option -Werror .iw/core/   # no core changes, should still pass
./iw test compile                                       # verify batch-implement.scala compiles
./iw test e2e                                           # run E2E tests
```

## Acceptance Criteria

- [ ] `.iw/commands/batch-implement.scala` exists and compiles
- [ ] Command auto-detects issue ID from branch name
- [ ] Command auto-detects workflow type from review-state.json
- [ ] Command validates pre-flight conditions (tasks.md, claude, forge CLI, clean tree)
- [ ] Command invokes `claude -p` per phase via `ProcessAdapter.runStreaming`
- [ ] Command handles MergePR outcome: merges via forge CLI, advances branch, marks done
- [ ] Command handles MarkDone outcome: marks phase complete in tasks.md
- [ ] Command handles recovery: resumes session with targeted prompts
- [ ] Command runs completion flow after all phases
- [ ] Command logs to `batch-implement.log`
- [ ] Command commits uncommitted changes claude leaves behind
- [ ] E2E tests pass for argument validation and pre-flight checks
- [ ] `scala-cli compile --scalac-option -Werror .iw/core/` still passes
- [ ] All existing unit tests pass (`./iw test unit`)
- [ ] All existing E2E tests pass (`./iw test e2e`)
