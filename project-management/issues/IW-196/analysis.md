# Story-Driven Analysis: Extend iw doctor to check project quality gates

**Issue:** IW-196
**Created:** 2026-02-09
**Status:** Draft
**Classification:** Feature

## Problem Statement

`iw doctor` currently only validates system-level prerequisites (git, gh CLI, tmux, API tokens, config file). It tells you whether the tool itself can run, but says nothing about whether the project you are working on follows engineering quality standards.

After establishing a reference quality gate setup on iw-support (SUPP-9), we want every project to have CI, linting, formatting, git hooks, and contributor documentation. `iw doctor` should detect missing quality gates, and `iw doctor --fix` should remediate them via a Claude Code session.

The value is twofold: (1) developers get immediate feedback on what their project is missing, and (2) the fix path is low-friction because Claude Code handles the adaptation to each project's build system and CI platform.

## User Stories

### Story 1: Scalafmt configuration check

```gherkin
Funkce: Kontrola konfigurace Scalafmt
  Jako vývojář
  Chci vidět zda projekt má správně nakonfigurovaný Scalafmt
  Aby jsem si byl jistý že formátování kódu je vynucováno

Scénář: Projekt má platnou konfiguraci Scalafmt
  Pokud projekt obsahuje soubor .scalafmt.conf
  A soubor obsahuje konfigurovanou verzi Scalafmt
  Když spustím iw doctor
  Pak vidím úspěšnou kontrolu "Scalafmt config"
  A vidím úspěšnou kontrolu "Scalafmt version"

Scénář: Projekt nemá Scalafmt konfiguraci
  Pokud projekt neobsahuje soubor .scalafmt.conf
  Když spustím iw doctor
  Pak vidím chybu "Scalafmt config" s nápovědou pro vytvoření souboru

Scénář: Scalafmt konfigurace neobsahuje verzi
  Pokud projekt obsahuje soubor .scalafmt.conf
  A soubor neobsahuje klíč "version"
  Když spustím iw doctor
  Pak vidím varování "Scalafmt version" s nápovědou pro doplnění verze
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
This is a file-existence and content-parsing check. The existing `Check` / `CheckResult` infrastructure supports this directly. The check functions are pure (take `ProjectConfiguration`, return `CheckResult`) and can read files from `os.pwd`. This is the simplest quality gate check and establishes the pattern for all subsequent stories.

**Acceptance:**
- `iw doctor` reports Scalafmt config presence and version status
- Checks are pure functions with injected file-reading for testability
- Unit tests cover all three scenarios
- E2E test validates output format

---

### Story 2: Scalafix configuration check

```gherkin
Funkce: Kontrola konfigurace Scalafix
  Jako vývojář
  Chci vidět zda projekt má správně nakonfigurovaný Scalafix
  Aby jsem si byl jistý že statická analýza kódu je nastavena

Scénář: Projekt má platnou konfiguraci Scalafix s DisableSyntax
  Pokud projekt obsahuje soubor .scalafix.conf
  A soubor obsahuje pravidlo DisableSyntax s noNulls, noVars, noThrows, noReturns
  Když spustím iw doctor
  Pak vidím úspěšnou kontrolu "Scalafix config"
  A vidím úspěšnou kontrolu "Scalafix rules"

Scénář: Projekt nemá Scalafix konfiguraci
  Pokud projekt neobsahuje soubor .scalafix.conf
  Když spustím iw doctor
  Pak vidím chybu "Scalafix config" s nápovědou pro vytvoření souboru

Scénář: Scalafix konfigurace neobsahuje DisableSyntax pravidlo
  Pokud projekt obsahuje soubor .scalafix.conf
  A soubor neobsahuje pravidlo DisableSyntax
  Když spustím iw doctor
  Pak vidím varování "Scalafix rules" s nápovědou pro doplnění pravidla
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
Same pattern as Story 1. HOCON parsing is already available via `com.typesafe:config` dependency. The `.scalafix.conf` file is HOCON format, so we can reuse the existing parser. Checking for specific rules (DisableSyntax with noNulls etc.) requires parsing nested config keys.

**Acceptance:**
- `iw doctor` reports Scalafix config presence and rule configuration
- Check verifies DisableSyntax rule with required sub-rules
- Unit tests cover all scenarios
- E2E test validates output

---

### Story 3: Git hooks check

```gherkin
Funkce: Kontrola git hooků
  Jako vývojář
  Chci vidět zda projekt má správně nastavené git hooky
  Aby jsem si byl jistý že formátování a testy se kontrolují před commitem a pushem

Scénář: Projekt má správně nainstalované git hooky
  Pokud adresář .git-hooks/ existuje
  A obsahuje spustitelný pre-commit hook
  A obsahuje spustitelný pre-push hook
  A hooky jsou nainstalované v .git/hooks/
  Když spustím iw doctor
  Pak vidím úspěšné kontroly pro všechny hook kontroly

Scénář: Adresář .git-hooks/ neexistuje
  Pokud adresář .git-hooks/ neexistuje
  Když spustím iw doctor
  Pak vidím chybu "Git hooks dir" s nápovědou pro vytvoření adresáře

Scénář: Hook soubory existují ale nejsou nainstalované
  Pokud adresář .git-hooks/ existuje
  A obsahuje pre-commit a pre-push hooky
  Ale hooky nejsou symlinkovány do .git/hooks/
  Když spustím iw doctor
  Pak vidím varování "Hooks installed" s nápovědou pro instalaci hooků
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
File existence checks are straightforward. Checking executable permissions requires `os.perms` or equivalent. Checking symlink installation into `.git/hooks/` requires resolving the git directory (which may differ in worktrees -- `git rev-parse --git-dir`). The symlink check adds moderate complexity since we need to verify the symlink target matches the hook source.

**Acceptance:**
- `iw doctor` reports hook directory, hook files, executable permissions, and installation status
- Handles both normal repos and worktrees (uses `git rev-parse --git-dir`)
- Unit tests cover all scenarios with temp directory fixtures
- E2E test validates hook checks

---

### Story 4: CI workflow check

```gherkin
Funkce: Kontrola CI workflow
  Jako vývojář
  Chci vidět zda projekt má správně nakonfigurovaný CI workflow
  Aby jsem si byl jistý že CI kontroluje kvalitu kódu automaticky

Scénář: Projekt má kompletní GitHub Actions CI workflow
  Pokud soubor .github/workflows/ci.yml existuje
  A workflow obsahuje compile krok
  A workflow obsahuje test krok
  A workflow obsahuje format check krok
  A workflow obsahuje lint check krok
  Když spustím iw doctor
  Pak vidím úspěšné kontroly pro všechny CI kontroly

Scénář: CI workflow soubor neexistuje
  Pokud soubor .github/workflows/ci.yml neexistuje
  A soubor .gitlab-ci.yml neexistuje
  Když spustím iw doctor
  Pak vidím chybu "CI workflow" s nápovědou pro vytvoření workflow

Scénář: CI workflow chybí některé kroky
  Pokud soubor .github/workflows/ci.yml existuje
  Ale neobsahuje lint check krok
  Když spustím iw doctor
  Pak vidím úspěšné kontroly pro existující kroky
  A vidím varování pro chybějící lint krok

Scénář: Detekce CI platformy podle konfigurace
  Pokud konfigurace používá GitHub tracker
  Když spustím iw doctor
  Pak kontrola hledá .github/workflows/ci.yml
```

**Estimated Effort:** 8-12h
**Complexity:** Moderate

**Technical Feasibility:**
File existence is simple. Content checking requires YAML parsing -- the project does not currently have a YAML dependency. However, we can use simple string/regex matching on the YAML content rather than adding a YAML parser, since we are checking for the presence of keywords like `compile`, `test`, `scalafmtCheckAll`/`checkFormat`, and `scalafixAll`/`fix --check`. CI platform detection can use the existing `IssueTrackerType` from config (GitHub -> GitHub Actions, GitLab -> GitLab CI). The main risk is brittle string matching vs. YAML structure.

**Acceptance:**
- `iw doctor` reports CI workflow presence and step completeness
- Detects CI platform from tracker type in config
- Checks for compile, test, format, and lint steps
- Unit tests with fixture YAML content
- E2E test validates CI checks

---

### Story 5: Contributor documentation check

```gherkin
Funkce: Kontrola dokumentace pro přispěvatele
  Jako vývojář
  Chci vidět zda projekt má CONTRIBUTING.md s potřebnými sekcemi
  Aby noví přispěvatelé věděli jak správně přispívat

Scénář: Projekt má kompletní CONTRIBUTING.md
  Pokud soubor CONTRIBUTING.md existuje
  A dokumentuje CI kontroly
  A dokumentuje instalaci git hooků
  A dokumentuje spuštění kontrol lokálně
  Když spustím iw doctor
  Pak vidím úspěšné kontroly pro dokumentaci

Scénář: CONTRIBUTING.md neexistuje
  Pokud soubor CONTRIBUTING.md neexistuje
  Když spustím iw doctor
  Pak vidím varování "CONTRIBUTING.md" s nápovědou pro vytvoření souboru

Scénář: CONTRIBUTING.md existuje ale chybí sekce
  Pokud soubor CONTRIBUTING.md existuje
  Ale nedokumentuje instalaci git hooků
  Když spustím iw doctor
  Pak vidím varování s nápovědou o chybějících sekcích
```

**Estimated Effort:** 4-6h
**Complexity:** Straightforward

**Technical Feasibility:**
File existence check plus simple content scanning for section keywords ("CI", "hook", "locally", "troubleshoot"). This is inherently fuzzy -- we are checking for the presence of topics, not exact content. Using case-insensitive regex matching on section headings is reasonable. Missing documentation is a warning, not an error, since the project can function without it.

**Acceptance:**
- `iw doctor` reports CONTRIBUTING.md presence and section coverage
- Missing doc is a warning (not error) -- project still functions
- Section checks are keyword-based (not exact text matching)
- Unit tests with fixture markdown content

---

### Story 6: Check grouping and filtering

```gherkin
Funkce: Filtrování kontrol podle kategorie
  Jako vývojář
  Chci spustit pouze konkrétní skupinu kontrol
  Aby jsem rychle zkontroloval specifickou oblast

Scénář: Spuštění všech kontrol
  Pokud spustím iw doctor bez parametrů
  Když se kontroly provedou
  Pak vidím systémové kontroly i kontroly kvality projektu

Scénář: Spuštění pouze CI kontrol
  Pokud spustím iw doctor --ci
  Když se kontroly provedou
  Pak vidím pouze kontroly CI workflow
  A nevidím systémové kontroly

Scénář: Spuštění pouze kontrol kvality projektu
  Pokud spustím iw doctor --quality
  Když se kontroly provedou
  Pak vidím kontroly Scalafmt, Scalafix, git hooků, CI a dokumentace
  A nevidím systémové kontroly

Scénář: Zobrazení skupin kontrol ve výstupu
  Pokud spustím iw doctor
  Když se kontroly provedou
  Pak systémové kontroly jsou seskupeny pod hlavičkou "Environment"
  A kontroly kvality jsou seskupeny pod hlavičkou "Project Quality Gates"
```

**Estimated Effort:** 6-8h
**Complexity:** Moderate

**Technical Feasibility:**
Requires extending the `doctor.scala` command to parse arguments (`--ci`, `--quality`, `--scalafmt`, `--scalafix`, `--hooks`, `--docs`). The `Check` type or the check collection needs a grouping mechanism -- either a `category` field on `Check` or separate check lists per category. The display logic in `doctor.scala` needs section headers. This is a presentation-layer change that builds on the checks from Stories 1-5.

**Acceptance:**
- `iw doctor` shows all checks grouped by category
- Filter flags (`--ci`, `--quality`, etc.) limit output to specific groups
- Section headers visually separate check groups
- E2E tests validate filtering behavior

---

### Story 7: Fix remediation via Claude Code

```gherkin
Funkce: Oprava chybějících quality gates
  Jako vývojář
  Chci automaticky opravit chybějící quality gates
  Aby jsem nemusel konfigurovat vše ručně

Scénář: Spuštění opravy pro všechny chybějící kontroly
  Pokud některé quality gate kontroly selhaly
  Když spustím iw doctor --fix
  Pak se spustí Claude Code session s promptem popisujícím chybějící kontroly
  A prompt obsahuje informace o build systému projektu
  A prompt obsahuje informace o CI platformě

Scénář: Detekce build systému
  Pokud projekt obsahuje build.mill
  Když spustím iw doctor --fix
  Pak prompt pro Claude Code specifikuje Mill jako build systém

Scénář: Žádné kontroly neselhaly
  Pokud všechny quality gate kontroly prošly
  Když spustím iw doctor --fix
  Pak se zobrazí zpráva že není co opravovat
```

**Estimated Effort:** 8-12h
**Complexity:** Complex

**Technical Feasibility:**
This story delegates the actual fixing to Claude Code via `claude --prompt`. The main work is: (1) collecting failed quality gate checks into a structured prompt, (2) detecting the project's build system (Mill vs SBT -- check for `build.mill` vs `build.sbt`), (3) detecting CI platform (from config tracker type), and (4) launching the Claude Code process. The prompt needs to be detailed enough for Claude Code to set up all missing pieces. The `ProcessAdapter` already supports launching external processes. The prompt should reference the iw-support SUPP-9 reference implementation.

Risk: The quality of the fix depends entirely on the Claude Code prompt. We cannot unit-test the fix output in a meaningful way. E2E testing would require a Claude Code session, which is expensive and non-deterministic.

**Acceptance:**
- `iw doctor --fix` launches Claude Code with a remediation prompt
- Prompt includes detected build system, CI platform, and list of failures
- Prompt references the expected artifact structure
- No-op when all checks pass
- Unit tests verify prompt generation (not Claude Code output)
- E2E test verifies the command launches without error

---

## Architectural Sketch

**Purpose:** List WHAT components each story needs, not HOW they're implemented.

### For Stories 1-5: Quality gate checks (Scalafmt, Scalafix, Git hooks, CI, Docs)

**Domain Layer (`model/`):**
- `CheckCategory` enum (or similar) -- groups checks into Environment vs QualityGate
- Possibly extend `Check` with an optional `category` field, or keep check lists separate

**Application Layer (not applicable -- checks are composed in `doctor.scala`):**
- N/A -- the existing `DoctorChecks.runAll()` pattern handles this

**Infrastructure Layer:**
- Pure check functions in a quality-gate-specific file (analogous to `GitHubHookDoctor.scala`)
- File reading via `os.read` / `os.exists` / `os.perms` for checking project files
- These check functions should accept injected file-system operations for testability (same pattern as `checkGhInstalledWith`)

**Presentation Layer (`commands/`):**
- New `*.hook-doctor.scala` file exposing quality gate checks for hook discovery
- Or: quality gate checks defined directly in core and composed in `doctor.scala`

---

### For Story 6: Check grouping and filtering

**Domain Layer (`model/`):**
- `CheckGroup` or `CheckCategory` to label checks
- Possibly `case class Check(name: String, category: String, run: ...)` or a wrapper

**Presentation Layer (`commands/doctor.scala`):**
- Argument parsing for filter flags
- Grouped output with section headers
- Exit code logic considers only displayed checks

---

### For Story 7: Fix remediation

**Domain Layer (`model/`):**
- `BuildSystem` enum (`Mill`, `SBT`, `Unknown`)
- `CIPlatform` enum (`GitHubActions`, `GitLabCI`, `Unknown`)
- Pure function to detect build system from file presence
- Pure function to map tracker type to CI platform

**Infrastructure Layer (`adapters/`):**
- Build system detection (check `build.mill` vs `build.sbt` file existence)
- Claude Code process launcher

**Presentation Layer (`commands/doctor.scala`):**
- `--fix` argument handling
- Prompt assembly from failed checks + detected context
- Process launch

---

## Technical Risks & Uncertainties

### CLARIFY: Where should quality gate check functions live?

The existing pattern has two locations for check logic:
1. **`core/dashboard/`** (e.g., `GitHubHookDoctor.scala`) -- pure check functions with injected dependencies
2. **`commands/*.hook-doctor.scala`** (e.g., `github.hook-doctor.scala`) -- thin wrappers exposing `Check` values

The `CLAUDE.md` in `core/` says "Scripts in `.iw/commands/` should NOT import from `dashboard/`" but `doctor.scala` already imports `iw.core.dashboard.{Check, CheckResult, DoctorChecks}`.

**Questions to answer:**
1. Should quality gate checks follow the same `core/dashboard/` + `commands/hook-doctor.scala` split?
2. Or should `Check` and `CheckResult` be moved out of `dashboard/` into `model/` since they are clearly shared domain types?
3. Should quality gate checks be discoverable via the hook mechanism, or hardcoded in `doctor.scala`?

**Options:**
- **Option A: Follow existing pattern** -- Quality gate pure logic in `core/dashboard/QualityGateChecks.scala`, hook wrappers in `commands/quality.hook-doctor.scala`. Pros: consistent. Cons: `Check`/`CheckResult` in `dashboard/` is architecturally questionable.
- **Option B: Move Check types to model/, checks to new location** -- Move `Check`/`CheckResult`/`DoctorChecks` to `core/model/`, put quality gate checks alongside. Pros: cleaner architecture. Cons: refactoring existing code, more scope.
- **Option C: Quality gate checks directly in doctor.scala** -- Define quality gate check functions inline or in a companion file. Pros: simple, no architectural changes. Cons: bloats command file, harder to test.

**Impact:** Affects code organization and testability for all quality gate stories.

---

### CLARIFY: YAML parsing approach for CI workflow checks

**Questions to answer:**
1. Should we add a YAML parser dependency for proper CI file analysis?
2. Or is regex/string matching sufficient for checking step presence?

**Options:**
- **Option A: String/regex matching** -- Scan YAML content for keywords like `compile`, `test`, `scalafmtCheck`, `scalafixAll`. Pros: no new dependency, simple. Cons: brittle, could match comments or unrelated strings.
- **Option B: Add YAML parser dependency** -- Use `snakeyaml` or `circe-yaml` to parse CI files properly. Pros: accurate, can check structure. Cons: new dependency, more code.
- **Option C: Line-by-line heuristic** -- Scan for lines matching patterns like `run:.*compile`, `run:.*test`. Pros: middle ground, more accurate than naive string search, no dependency. Cons: still somewhat brittle.

**Impact:** Affects accuracy and maintenance burden of CI checks.

---

### CLARIFY: How should `--fix` interact with Claude Code?

**Questions to answer:**
1. What exact Claude Code CLI invocation should be used? (`claude --prompt "..."` or `claude -p "..."` or something else?)
2. Should the fix prompt be stored as a skill file or generated dynamically?
3. Should `--fix` run all fixes at once or ask for confirmation per category?

**Options:**
- **Option A: Single claude invocation with dynamic prompt** -- Generate prompt from failed checks, launch `claude -p "..."`. Pros: simple, self-contained. Cons: prompt could be very long.
- **Option B: Skill-based approach** -- Store fix instructions as a `.claude/skills/` file, reference it from prompt. Pros: reusable, editable. Cons: coupling to skill file format.
- **Option C: Per-category fix** -- `iw doctor --fix --ci` fixes only CI, etc. Pros: incremental, less overwhelming. Cons: more complex argument handling.

**Impact:** Affects UX and maintainability of the fix workflow.

---

### CLARIFY: Should quality gate checks run on the project being worked on or on iw-cli itself?

The `doctor` command runs in the context of `os.pwd` (the project directory). When running `iw doctor` from an iw-cli worktree, it checks iw-cli itself. When running from another project's worktree, it checks that project.

**Questions to answer:**
1. Is this the intended behavior -- quality gate checks run on whatever project `os.pwd` points to?
2. Should there be a way to skip quality gate checks for non-Scala projects?

**Options:**
- **Option A: Always check os.pwd** -- Quality gates are universal. Pros: simple. Cons: may report irrelevant failures for non-Scala projects.
- **Option B: Skip if no Scala build detected** -- Only run Scala-specific checks if `build.mill` or `build.sbt` exists. Pros: avoids false positives. Cons: requires build system detection before checks.
- **Option C: Configurable in .iw/config.conf** -- Add `qualityGates.enabled = true/false` or `project.type = scala`. Pros: explicit. Cons: config changes needed.

**Impact:** Affects whether quality gate checks produce noise on non-Scala projects.

---

## Total Estimates

**Story Breakdown:**
- Story 1 (Scalafmt check): 4-6 hours
- Story 2 (Scalafix check): 4-6 hours
- Story 3 (Git hooks check): 6-8 hours
- Story 4 (CI workflow check): 8-12 hours
- Story 5 (Contributor docs check): 4-6 hours
- Story 6 (Check grouping/filtering): 6-8 hours
- Story 7 (Fix via Claude Code): 8-12 hours

**Total Range:** 40 - 58 hours

**Confidence:** Medium

**Reasoning:**
- Stories 1-2 are straightforward file checks following an established pattern -- high confidence
- Story 3 adds moderate complexity with symlink/permissions checks and worktree handling
- Story 4 has risk around YAML parsing approach -- depends on CLARIFY resolution
- Story 5 is straightforward but fuzzy (keyword matching for doc sections)
- Story 6 requires argument parsing changes and display refactoring
- Story 7 has the most uncertainty -- Claude Code integration is novel and prompt quality determines value

## Testing Approach

**Per Story Testing:**

Each story should have:
1. **Unit Tests** (in `.iw/core/test/`)
2. **E2E Tests** (in `.iw/test/doctor.bats`)

**Story-Specific Testing Notes:**

**Story 1 (Scalafmt):**
- Unit: Pure check functions with mock file content (test with/without `.scalafmt.conf`, with/without version)
- E2E: BATS test creating temp project with/without `.scalafmt.conf`

**Story 2 (Scalafix):**
- Unit: Pure check functions with mock HOCON content (test rule presence/absence)
- E2E: BATS test creating temp project with/without `.scalafix.conf`

**Story 3 (Git hooks):**
- Unit: Pure check functions with injected file-system queries (directory exists, file exists, permissions, symlink target)
- E2E: BATS test creating temp project with/without `.git-hooks/` and hooks

**Story 4 (CI workflow):**
- Unit: Pure check functions with fixture YAML content strings
- E2E: BATS test creating temp project with/without `.github/workflows/ci.yml`

**Story 5 (Docs):**
- Unit: Pure check functions with fixture markdown content
- E2E: BATS test creating temp project with/without `CONTRIBUTING.md`

**Story 6 (Filtering):**
- Unit: Test check filtering logic (given categories and filter flag, correct subset is returned)
- E2E: BATS tests for `iw doctor --ci`, `iw doctor --quality`

**Story 7 (Fix):**
- Unit: Test prompt generation (given failed checks and detected build system, correct prompt is assembled)
- Unit: Test build system detection (given file presence, correct enum returned)
- E2E: Test that `iw doctor --fix` with no failures outputs "nothing to fix" message

**Test Data Strategy:**
- Temporary directories with fixture files (existing BATS pattern)
- HOCON/YAML content as string constants in unit tests
- Injected file-reading functions for pure check testability (following `checkGhInstalledWith` pattern)

**Regression Coverage:**
- Existing `doctor.bats` tests must continue to pass
- Existing `DoctorChecksTest.scala` must continue to pass
- Quality gate checks should not affect exit code when all system checks pass (unless quality gates also fail)

## Deployment Considerations

### Database Changes
None -- iw-cli uses file-based configuration only.

### Configuration Changes
- Potentially add `project.buildSystem` and/or `qualityGates` section to `.iw/config.conf` (depends on CLARIFY resolution)
- No environment variable changes

### Rollout Strategy
- Quality gate checks appear as new sections in `iw doctor` output
- No breaking changes to existing behavior
- Existing system checks remain unchanged

### Rollback Plan
- Remove the quality gate hook-doctor file to disable checks
- Or revert the commit -- no persistent state changes

## Dependencies

### Prerequisites
- Existing `Check` / `CheckResult` / `DoctorChecks` infrastructure (already exists)
- Existing hook-doctor discovery mechanism (already exists)

### Story Dependencies
- Stories 1-5 are independent of each other (can be developed in parallel)
- Story 6 depends on at least one check story (1-5) to have something to filter
- Story 7 depends on Stories 1-5 (needs failed checks to generate fix prompt)

### External Blockers
- Story 7 requires Claude Code CLI to be installed on the developer's machine
- Reference implementation in iw-support (SUPP-9) should be merged and available

---

## Implementation Sequence

**Recommended Story Order:**

1. **Story 1: Scalafmt check** -- Simplest quality gate, establishes the pattern and testability approach for all subsequent stories
2. **Story 2: Scalafix check** -- Same pattern, slightly more complex content parsing (HOCON rules)
3. **Story 3: Git hooks check** -- Introduces filesystem permission and symlink checks
4. **Story 5: Contributor docs check** -- Simple file/keyword check, low risk
5. **Story 4: CI workflow check** -- Most complex check (YAML-ish parsing), benefits from pattern established in 1-3
6. **Story 6: Check grouping/filtering** -- Presentation layer, needs checks to exist first
7. **Story 7: Fix via Claude Code** -- Depends on all checks, highest uncertainty

**Iteration Plan:**

- **Iteration 1** (Stories 1-2): Core quality gate pattern + formatting/linting checks. Delivers immediate value -- developers see Scalafmt/Scalafix status.
- **Iteration 2** (Stories 3-5): Remaining detection checks. Full quality gate visibility after this iteration.
- **Iteration 3** (Stories 6-7): UX polish (grouping/filtering) and automated remediation.

## Documentation Requirements

- [ ] Gherkin scenarios serve as living documentation
- [ ] Update `iw --describe doctor` header comment with new flags
- [ ] Document quality gate check categories in project README or CLAUDE.md
- [ ] Add `--fix` usage instructions to `iw doctor` help output
- [ ] Reference iw-support SUPP-9 as the quality gate standard

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders (especially: where to put check functions, YAML parsing approach, `--fix` UX)
2. Run **ag-create-tasks** with the issue ID
3. Run **ag-implement** for iterative story implementation
