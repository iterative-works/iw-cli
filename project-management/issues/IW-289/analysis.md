# Story-Driven Analysis: Add `iw phase-merge` command: wait for CI, auto-merge or resume agent on failure

**Issue:** IW-289
**Created:** 2026-03-20
**Status:** Draft
**Classification:** Feature

## Problem Statement

The batch-implement workflow currently merges PRs immediately after creation (via `phase-pr --batch`), bypassing CI entirely. In real projects with CI pipelines (formatting, tests, linting), this means either:

1. PRs get merged with failing CI (bad), or
2. The agent creates a PR and exits, leaving nobody to monitor CI results and fix failures (gap in automation)

We need a command that bridges the gap between PR creation and merge by waiting for CI, then either auto-merging on success or invoking the agent to fix failures.

## User Stories

### Story 1: CI polling and auto-merge on pass (GitHub)

```gherkin
Funkce: Automaticke slouceni PR po uspesnem CI
  Jako automatizovany agent
  Chci pockat na dokonceni CI a automaticky sloucit PR
  Aby faze workflow mohla pokracovat bez lidske intervence

Scenar: CI projde a PR je sloucen
  Pokud existuje otevreny PR pro aktualni fazovou vetev
  A CI pipeline bezi
  Kdyz spustim "iw phase-merge"
  Pak prikaz opakovaně kontroluje stav CI kontrol
  A po uspesnem dokonceni vsech kontrol PR slouci (squash)
  A aktualizuje review-state.json na status "phase_merged"
```

**Estimated Effort:** 8-12h
**Complexity:** Moderate

**Technical Feasibility:**
GitHub's `gh pr checks` provides JSON output with check status. Polling is straightforward. The main complexity is correctly detecting "all checks passed" vs "some still pending" vs "some failed". There is also a subtlety around PRs that have no required checks at all.

**Acceptance:**
- `iw phase-merge` polls CI until all checks pass, then squash-merges
- review-state.json updated to `phase_merged` with appropriate display/badges
- Feature branch advanced (checkout + fetch/reset) after merge

---

### Story 2: Pure decision logic for CI check results

```gherkin
Funkce: Rozhodovaci logika pro vysledky CI kontrol
  Jako vyvojar
  Chci mit cistou rozhodovaci funkci pro stavy CI
  Aby logika byla testovatelna bez I/O

Scenar: Vsechny kontroly prosly
  Pokud seznam kontrol obsahuje jen "pass" a "success" stavy
  Kdyz vyhodnotim rozhodovaci funkci
  Pak vysledek je "AllPassed"

Scenar: Nektera kontrola selhala
  Pokud seznam kontrol obsahuje alespon jeden "failure" stav
  Kdyz vyhodnotim rozhodovaci funkci
  Pak vysledek je "SomeFailed" se seznamem selhavsich kontrol

Scenar: Nektera kontrola stale bezi
  Pokud seznam kontrol obsahuje "pending" nebo "in_progress" stav
  A zadna kontrola neselhala
  Kdyz vyhodnotim rozhodovaci funkci
  Pak vysledek je "StillRunning"
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Follows the existing `BatchImplement.decideOutcome` pattern exactly. Pure function on domain types, trivially testable. This story enables TDD for the entire decision pipeline before any I/O is written.

**Acceptance:**
- `PhaseMerge` object in `model/` with pure decision functions
- Covers: all-passed, some-failed, still-running, timeout, no-checks-found
- Comprehensive unit tests for each scenario

---

### Story 3: CI failure recovery via agent re-invocation

```gherkin
Funkce: Oprava CI selhani pomoci agenta
  Jako automatizovany agent
  Chci byt znovu vyvolany s kontextem selhani CI
  Aby mohl opravit problemy a znovu pushnout

Scenar: CI selze a agent opravi problem
  Pokud CI kontroly selhaly na PR
  Kdyz phase-merge detekuje selhani
  Pak obnovi claude agenta s popisem selhanych kontrol
  A agent opravuje problemy a pushne
  A phase-merge znovu ceka na CI

Scenar: Agent nevypravi selhani po maximu pokusu
  Pokud CI kontroly opakovane selhavaji
  A dosahli jsme maximalniho poctu opakovani
  Kdyz phase-merge vzdava opravy
  Pak nastavi review-state na activity "waiting"
  A ukonci se s chybovym kodem
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Mirrors the existing `attemptRecovery` pattern in `batch-implement.scala`. The prompt construction needs to include failed check names and ideally log URLs. The retry loop structure is already proven in the codebase.

**Acceptance:**
- On CI failure, resumes claude with failure context (check names + statuses)
- Retries up to configurable limit (default 2)
- On exhaustion, sets `activity: "waiting"` and exits non-zero
- review-state transitions: `ci_pending` -> `ci_fixing` -> back to `ci_pending` on re-push

---

### Story 4: GitLab CI status support

```gherkin
Funkce: Podpora stavu CI pro GitLab
  Jako uzivatel s GitLab projektem
  Chci aby phase-merge fungoval i s GitLab CI pipelines
  Aby prikaz nebyl omezen jen na GitHub

Scenar: GitLab CI projde a MR je sloucen
  Pokud existuje otevreny MR pro aktualni fazovou vetev
  A GitLab CI pipeline bezi
  Kdyz spustim "iw phase-merge"
  Pak prikaz kontroluje stav CI pomoci glab CLI
  A po uspesnem dokonceni MR slouci
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
`glab ci status` and `glab mr view --json` provide pipeline status. The abstraction needs to unify GitHub and GitLab check results into the same domain model from Story 2. The `ForgeType` enum already handles this dispatch pattern.

**Acceptance:**
- CI polling works with `glab` CLI
- Same decision logic handles both forge types
- MR merged with squash on success

---

### Story 5: Timeout and configurable polling

```gherkin
Funkce: Konfigurovatelny timeout a interval dotazovani
  Jako uzivatel
  Chci nastavit timeout a interval dotazovani
  Aby prikaz fungoval s ruznymi CI rychlostmi

Scenar: CI neprojde v casovem limitu
  Pokud CI kontroly stale bezi
  A uplynul casovy limit (vychozi 30 minut)
  Kdyz phase-merge vyhodnoti timeout
  Pak nastavi review-state na activity "waiting"
  A ukonci se s chybovym kodem a zpravou o timeoutu

Scenar: Vlastni interval dotazovani
  Pokud uzivatel zada "--poll-interval 60s"
  Kdyz phase-merge dotazuje CI
  Pak ceka 60 sekund mezi kazdym dotazem
```

**Estimated Effort:** 2-4h
**Complexity:** Straightforward

**Technical Feasibility:**
Duration parsing is simple (Ns, Nm patterns). The polling loop is the main orchestration concern. Timeout is just a wall-clock check in the poll loop.

**Acceptance:**
- `--timeout 30m` (default 30m) stops polling after duration
- `--poll-interval 30s` (default 30s) controls sleep between polls
- Timeout sets `activity: "waiting"` and exits non-zero
- Both flags accept `Ns`, `Nm` format

---

### Story 6: Integration with batch-implement

```gherkin
Funkce: Integrace phase-merge do batch-implement
  Jako automatizovany agent
  Chci aby batch-implement pouzival phase-merge misto okamziteho merge
  Aby CI bylo overeno pred sloucenim kazde faze

Scenar: batch-implement pouziva phase-merge po vytvoreni PR
  Pokud batch-implement orchestruje fazovy cyklus
  Kdyz faze vytvori PR (phase-pr bez --batch)
  Pak batch-implement zavola phase-merge pro cekani na CI
  A az po uspesnem merge pokracuje na dalsi fazi
```

**Estimated Effort:** 4-6h
**Complexity:** Moderate

**Technical Feasibility:**
Requires modifying `batch-implement.scala` to call `phase-merge` as a subprocess instead of doing inline merge. The current `handleMergePR` function would be replaced by a `phase-merge` invocation. The `phase-pr` command would no longer use `--batch` in the batch-implement flow.

**Acceptance:**
- `batch-implement` calls `phase-merge` after `phase-pr` (without `--batch`)
- If `phase-merge` fails (exhausted retries or timeout), batch-implement stops
- Existing `handleMergePR` logic removed or deprecated in favor of `phase-merge`
- Phase loop: `phase-start -> agent -> phase-pr -> phase-merge`

---

### Story 7: PR number extraction from review-state

```gherkin
Funkce: Extrakce cisla PR z review-state
  Jako phase-merge prikaz
  Chci automaticky zjistit cislo PR z review-state.json
  Aby uzivatel nemusel zadavat cislo PR rucne

Scenar: PR URL nalezena v review-state
  Pokud review-state.json obsahuje "pr_url"
  Kdyz phase-merge zjistuje cislo PR
  Pak extrahuje cislo z URL a pouzije ho pro dotazovani CI

Scenar: PR URL chybi
  Pokud review-state.json neobsahuje "pr_url"
  Kdyz phase-merge zjistuje cislo PR
  Pak vypise chybu a ukonci se
```

**Estimated Effort:** 2-3h
**Complexity:** Straightforward

**Technical Feasibility:**
`phase-pr` already stores `pr_url` in review-state.json. Extracting PR/MR number from URL is trivial regex. This is prerequisite plumbing for Stories 1 and 4.

**Acceptance:**
- Reads `pr_url` from review-state.json
- Extracts numeric PR/MR number from GitHub and GitLab URL formats
- Clear error if `pr_url` missing or unparseable

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Story 2: Pure decision logic for CI check results

**Domain Layer (`model/`):**
- `CICheckResult` — value object representing a single check (name, status, optional URL)
- `CICheckStatus` — enum: Passed, Failed, Pending, Cancelled, Unknown
- `CIVerdict` — enum: AllPassed, SomeFailed(failedChecks), StillRunning, NoChecksFound, TimedOut
- `PhaseMerge` — object with pure decision functions: `evaluateChecks`, `shouldRetry`, `buildRecoveryPrompt`
- `PhaseMergeConfig` — case class for timeout, poll interval, max retries

**No other layers needed for this story.**

---

### For Story 7: PR number extraction from review-state

**Domain Layer (`model/`):**
- Pure function to extract PR/MR number from URL string (GitHub and GitLab patterns)
- Added to `PhaseMerge` object or standalone helper

**No other layers needed.**

---

### For Story 1: CI polling and auto-merge on pass (GitHub)

**Application/Adapter Layer (`adapters/`):**
- `CIStatusClient` or extension of `GitHubClient` — fetches check statuses via `gh pr checks --json`
- Parsing of `gh pr checks` JSON output into `List[CICheckResult]`

**Presentation Layer (`commands/`):**
- `phase-merge.scala` — command script with argument parsing, polling loop, merge invocation

**Infrastructure:**
- Uses existing `ProcessAdapter.run` for CLI calls
- Uses existing `ReviewStateAdapter` for state updates
- Uses existing `GitAdapter` for branch operations post-merge

---

### For Story 4: GitLab CI status support

**Adapter Layer (`adapters/`):**
- Extension of `GitLabClient` or `CIStatusClient` — fetches pipeline status via `glab mr view --json` or `glab ci status`
- Parsing of glab JSON output into same `List[CICheckResult]`

---

### For Story 3: CI failure recovery via agent re-invocation

**Domain Layer (`model/`):**
- `PhaseMerge.buildRecoveryPrompt(failedChecks: List[CICheckResult]): String`

**Command Layer (`commands/`):**
- Recovery loop in `phase-merge.scala` — invokes `claude` CLI with recovery prompt, then re-polls

---

### For Story 5: Timeout and configurable polling

**Domain Layer (`model/`):**
- Duration parsing function (e.g., "30s" -> 30000ms, "5m" -> 300000ms)
- Part of `PhaseMergeConfig`

**Command Layer:**
- Argument parsing in `phase-merge.scala`

---

### For Story 6: Integration with batch-implement

**Command Layer (`commands/`):**
- Modified `batch-implement.scala` — replace `handleMergePR` with subprocess call to `iw phase-merge`
- Modified `phase-pr` invocation — drop `--batch` flag in batch-implement flow

## Technical Risks & Uncertainties

### CLARIFY: GitHub checks vs status API differences

GitHub has two overlapping systems: "checks" (Check Runs via GitHub Actions) and "commit statuses" (older API, used by external CI like Jenkins). `gh pr checks` may not capture both.

**Questions to answer:**
1. Do all target projects use GitHub Actions, or do some use external CI (Jenkins, CircleCI)?
2. Does `gh pr checks` include commit statuses, or only check runs?
3. Should we use `gh pr checks --json` or `gh api` for more complete status?

**Options:**
- **Option A**: Use `gh pr checks --json` only — simpler, covers GitHub Actions. External CI not supported.
- **Option B**: Use `gh api` to query both checks and statuses — complete but more complex parsing.
- **Option C**: Start with Option A, add Option B later if needed.

**Impact:** Determines whether phase-merge works with all CI systems or only GitHub Actions.

---

### CLARIFY: Merge strategy (squash vs merge commit)

The issue says "squash merge" but `batch-implement.scala` currently uses `--merge` (merge commit). `phase-pr --batch` uses `--squash --delete-branch`.

**Questions to answer:**
1. Should phase-merge always squash, always merge, or make it configurable?
2. Should the phase sub-branch be deleted after merge?

**Options:**
- **Option A**: Always squash + delete branch (matches phase-pr --batch behavior)
- **Option B**: Configurable `--merge-strategy` flag
- **Option C**: Match whatever batch-implement currently does (merge commit)

**Impact:** Affects git history cleanliness and branch cleanup.

---

### CLARIFY: Where to run the agent for CI fixes

When CI fails, `phase-merge` needs to invoke `claude` to fix the issue. But the current branch may have been switched back to the feature branch after PR creation.

**Questions to answer:**
1. Should `phase-merge` be run while still on the phase sub-branch?
2. Or should it handle checking out the phase branch if needed?
3. Does `batch-implement` need to change its branch management around the `phase-merge` call?

**Options:**
- **Option A**: Require `phase-merge` to be run on the phase sub-branch (simplest)
- **Option B**: `phase-merge` auto-checks out the phase branch from `pr_url` metadata
- **Option C**: `phase-merge` operates from the feature branch and pushes to the phase branch

**Impact:** Determines command prerequisites and batch-implement integration complexity.

---

### CLARIFY: PRs with no CI checks configured

Some repositories might not have any CI configured. `gh pr checks` would return empty.

**Questions to answer:**
1. Should phase-merge treat "no checks" as pass and merge immediately?
2. Or should it warn and require a flag like `--allow-no-checks`?

**Options:**
- **Option A**: No checks = immediate merge (graceful degradation)
- **Option B**: No checks = error (force users to configure CI or use `--allow-no-checks`)
- **Option C**: No checks = warn but proceed with merge

**Impact:** User experience for projects without CI.

## Total Estimates

**Story Breakdown:**
- Story 2 (Pure decision logic): 4-6 hours
- Story 7 (PR number extraction): 2-3 hours
- Story 1 (GitHub CI polling + merge): 8-12 hours
- Story 4 (GitLab CI support): 4-6 hours
- Story 3 (Failure recovery): 6-8 hours
- Story 5 (Timeout + config): 2-4 hours
- Story 6 (batch-implement integration): 4-6 hours

**Total Range:** 30 - 45 hours

**Confidence:** Medium

**Reasoning:**
- Decision logic and extraction stories are well-understood (high confidence)
- CI polling is conceptually simple but `gh`/`glab` CLI output format specifics may surprise us
- Agent recovery loop mirrors existing pattern but CI-specific prompt engineering is new
- batch-implement refactoring has moderate risk of breaking existing flow

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests**
2. **Integration Tests**
3. **E2E Scenario Tests**

**Story-Specific Testing Notes:**

**Story 2 (Pure decision logic):**
- Unit: All `CIVerdict` outcomes, edge cases (empty list, mixed statuses, unknown statuses)
- Integration: N/A (pure functions)
- E2E: N/A (no I/O)

**Story 7 (PR number extraction):**
- Unit: GitHub URL parsing, GitLab URL parsing, invalid URLs, missing pr_url
- Integration: N/A (pure functions)
- E2E: N/A

**Story 1 (GitHub CI polling + merge):**
- Unit: `gh pr checks` JSON parsing into `CICheckResult` list
- Integration: Mock `execCommand` to simulate check status progression
- E2E (BATS): Happy path with mock gh CLI returning pass statuses, then merge

**Story 4 (GitLab CI support):**
- Unit: `glab` JSON parsing into `CICheckResult` list
- Integration: Mock `execCommand` for glab output
- E2E (BATS): GitLab happy path (if feasible with mock)

**Story 3 (Failure recovery):**
- Unit: Recovery prompt construction, retry decision logic
- Integration: Mock claude CLI and gh CLI for failure->fix->pass cycle
- E2E (BATS): Failure scenario with retry

**Story 5 (Timeout + config):**
- Unit: Duration parsing ("30s", "5m", invalid input)
- Integration: N/A
- E2E: Timeout scenario (short timeout with slow mock)

**Story 6 (batch-implement integration):**
- Unit: Updated `decideOutcome` if status values change
- Integration: N/A
- E2E (BATS): batch-implement with phase-merge subprocess (if feasible)

**Test Data Strategy:**
- Fixture JSON files for `gh pr checks` and `glab mr view` outputs in `.iw/core/test/fixtures/`
- Injected `execCommand` functions for adapter tests (existing pattern)
- BATS tests with `IW_SERVER_DISABLED=1` as required

**Regression Coverage:**
- Existing `batch-implement` E2E tests must still pass
- Existing `phase-pr` tests must still pass
- `phase-pr --batch` behavior unchanged (still available for manual use)

## Deployment Considerations

### Database Changes
None. All state is in review-state.json files.

### Configuration Changes
No new config file fields. All configuration is via CLI flags with sensible defaults.

### Rollout Strategy
- New command `phase-merge` is additive -- no breaking changes
- `batch-implement` integration (Story 6) is the only modification to existing behavior
- Can ship Stories 1-5 independently before Story 6

### Rollback Plan
- Story 6 can be reverted by restoring `--batch` flag usage in batch-implement
- Other stories are purely additive

## Dependencies

### Prerequisites
- IW-274 (activity field in review-state) -- done
- IW-275 (batch-implement command) -- done

### Story Dependencies
- Story 2 must come before Stories 1, 3, 4 (decision logic needed first)
- Story 7 must come before Story 1 (PR number needed for CI queries)
- Story 1 must come before Story 3 (polling loop needed before recovery)
- Story 1 must come before Story 6 (command must exist before integration)
- Story 4 can be parallel with Story 3

### External Blockers
None.

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 2: Pure decision logic** -- Foundation for everything, enables TDD
2. **Story 7: PR number extraction** -- Small prerequisite for CI queries
3. **Story 1: GitHub CI polling + merge** -- Core happy path, delivers user value
4. **Story 5: Timeout + config** -- Quick addition to Story 1's polling loop
5. **Story 3: Failure recovery** -- Error handling for the core flow
6. **Story 4: GitLab CI support** -- Extends to second forge type
7. **Story 6: batch-implement integration** -- Ties it all together

**Iteration Plan:**

- **Iteration 1** (Stories 2, 7, 1): Core functionality -- poll CI, merge on pass (GitHub only)
- **Iteration 2** (Stories 5, 3): Robustness -- timeout handling + failure recovery
- **Iteration 3** (Stories 4, 6): Completeness -- GitLab support + batch-implement integration

## Documentation Requirements

- [ ] Gherkin scenarios serve as living documentation
- [ ] API documentation (new `iw phase-merge` command help text)
- [ ] Update batch-implement documentation to reflect phase-merge integration
- [ ] Add `phase-merge` to `.claude/skills/` via `iw claude-sync --force`

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders
2. Run **ag-create-tasks** with the issue ID
3. Run **ag-implement** for iterative story implementation
