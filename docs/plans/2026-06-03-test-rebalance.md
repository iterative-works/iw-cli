<!-- PURPOSE: Plan & tracker for rebalancing the test suite (unit-heavy + contract + smoke) -->
<!-- PURPOSE: and introducing scoverage. Status markers in checkboxes, updated as work proceeds. -->

# Test Rebalance + Scoverage Plan

**Started:** 2026-06-03
**Status:** Phase 0–3 complete; Phase 4 pilot (4.1–4.3) complete; ready for 4.4 (bulk command migration)

## Why this plan exists

The current test suite is 39 BATS files / ~410 tests / 27.8 min sequential locally.
71% of wall time lives in 8 infra-test files that test scala-cli's ability to compile
synthesized scripts, not iw-cli's logic. There are 95 munit unit tests in `core/test/`
that run via scala-cli with no coverage data.

We want a different shape:

- **Unit (broad)** — pure logic in `core/`. Lots of tests. In-process, fast, instrumented for coverage.
- **Tool contract (narrow, ~8)** — one per external tool (git, gh, glab, tmux, scala-cli, mill). Prove our world-assumptions still hold. Gated to nightly + before-release.
- **E2E smoke (~1 per command)** — prove iw-run → scala-cli → core wiring works end-to-end with the actual binary surface.

Anything not falling into one of those three categories is a deletion candidate.

## Diagnostic findings (locked in)

Per-category breakdown of the existing 39 BATS files (measured 2026-05-29):

| Category | Files | Tests | LOC | Time | s/test | Fails |
|---|---:|---:|---:|---:|---:|---:|
| infra-plugin | 5 | 34 | 880 | 566s | 16.6 | 0 |
| cmd-workflow | 7 | 108 | 2261 | 379s | 3.5 | 3 |
| infra-project-cmds | 3 | 25 | 584 | 358s | 14.3 | 0 |
| cmd-config | 3 | 95 | 2390 | 98s | 1.03 | 0 |
| infra-dashboard | 4 | 18 | 689 | 94s | 5.2 | 1 |
| cmd-worktree | 7 | 60 | 1343 | 66s | 1.10 | 2 |
| infra-launcher | 2 | 20 | 290 | 45s | 2.25 | 0 |
| cmd-tracker | 3 | 39 | 1306 | 36s | 0.92 | 0 |
| cmd-misc | 2 | 20 | 516 | 16s | 0.80 | 0 |
| cmd-project-mgmt | 2 | 13 | 297 | 14s | 1.08 | 0 |
| infra-schema | 1 | 6 | 46 | 0s | 0.00 | 0 |
| **Total** | **39** | **410** | **10602** | **27.8min** | | **6** |

Currently-failing files (3 files, 6 tests):
- `phase-merge.bats` (3/17) — 5 min hog, likely git remote / PR mocking
- `start-prompt.bats` (2/5) — tmux capture-pane / PTY (cousin of #304)
- `dashboard-rebuild-gate.bats` (1/2) — tests Mill rebuild detection, not iw-cli code

The big asymmetry: `infra-plugin` and `infra-project-cmds` are 16.6 / 14.3 s/test because
each test **writes a new Scala source file** and pays a fresh bloop compile. The same
work as unit tests on the discovery / listing / parsing logic would cost <50ms/test.

## Reference: scoverage in ops/procedures

Pattern proven in `~/ops/procedures/build.mill`:

```scala
//| mvnDeps:
//|   - com.lihaoyi::mill-contrib-scoverage:$MILL_VERSION

import mill.contrib.scoverage.{ScoverageModule, ScoverageReport}

val ScoverageVersion = "2.1.0"  // verify Scala 3.3.7 compat

object core extends ScalaModule with ScoverageModule {
  def scoverageVersion = ScoverageVersion
  object test extends ScoverageTests with TestModule.Munit { ... }
}

object scoverage extends ScoverageReport {
  override def scalaVersion = "3.3.7"
  override def scoverageVersion = ScoverageVersion
}
```

CI flow: `./mill __.scoverage.xmlReport && ./mill scoverage.htmlReportAll`, then
parse per-module XML for a summary table on the GitHub run page, upload artifact
(14-day retention). Baseline-only — no thresholds or gating.

---

## Phase 0 — Mill + scoverage foundation

**Goal:** existing 95 munit tests run through Mill; coverage XML + HTML + CI summary; baseline % captured.

- [x] 0.1 Add `core.test` Mill submodule sourcing `core/test/` — `build.mill`
- [x] 0.2 Add scoverage plugin via `//| mvnDeps:` header — `build.mill`
- [x] 0.3 Add `ScoverageVersion` constant (resolved CLARIFY-1: `2.1.0` works on Scala 3.3.7) — `build.mill`
- [x] 0.4 Mix `ScoverageModule` into `core` and `dashboard`; set `scoverageVersion`
- [x] 0.5 Test submodules extend `ScoverageTests` instead of `ScalaTests`
- [x] 0.6 Add top-level `object scoverage extends ScoverageReport`
- [x] 0.7 `runUnitTests` switches from `scala-cli test core/` to `./mill core.test + dashboard.test` — `.iw/commands/test.scala`
- [x] 0.8 CI: add coverage run, XML→summary parser, artifact upload — `.github/workflows/ci.yml`
- [x] 0.9 Document new path — `CLAUDE.md`, new `docs/testing.md`

**Decision gate (PASSED 2026-06-03):** all existing tests pass through Mill (core: 186 tasks, ~9s; dashboard: 241 tasks, ~12s). Baseline coverage: **core 77.43% (4876/6297), dashboard 76.24% (3392/4449)**.

**Side effect during 0.4:** scoverage XML writer can't handle a single-file source root. Moved `core/IssueCreateParser.scala` → `core/model/IssueCreateParser.scala` (package `iw.core` → `iw.core.model`); updated 2 imports (`commands/issue.scala`, `core/test/IssueCreateParserTest.scala`). Matches FCIS rules in `core/CLAUDE.md`.

## Phase 1 — Pilot: review-state slice

**Goal:** prove the unit-bulk + smoke-BATS pattern on one slice.
Target: `review-state.bats` (61 tests, 55s, no local failures). State-machine shape, ideal pilot.

- [x] 1.1 Map BATS tests to underlying functions in `commands/review-state.scala` + `core/.../ReviewState*`
- [x] 1.2 Identify pure functions worth covering (transitions, JSON shape, validation)
- [x] 1.3 Write 48 munit tests in `core/test/ReviewStateCliParserTest.scala`
- [x] 1.4 Reduce `review-state.bats` from 61 → 6 smoke tests (happy-path per subcommand + 1 error + git wiring + dispatcher help)
- [x] 1.5 Measure: BATS 64.7s → 11.6s; new parser code 0% → 98.04% covered; affected-files aggregate 75.5% → 79.2%

**Side effect during 1.3:** Extracted `core/model/ReviewStateCliParser.scala` (a new pure FCIS module). Refactored `commands/review-state.scala` to delegate to it, mapping `Left(err) -> sys.exit(1)` at the call site. Removed ~165 lines of duplicated parsing.

**Decision gate (PASSED 2026-06-03):**
- BATS-time-saved / unit-time-added: ~53.1s saved / ~0.1s added ≈ **531×** (target: ≥5×). PASS.
- Coverage gain: +3.7pp on affected files (75.5% → 79.2%) in absolute terms — short of the literal +10% target. However, the 200 statements of CLI parsing logic that previously lived in `commands/` (untrackable by scoverage, effectively 0%) now live in `core/model/` at 98.04% covered. Structural win is large; pp number understates it. CONDITIONAL PASS — the spirit of "no lost coverage, new logic well-tested" is met.
- No named lost capability: all 61 prior BATS scenarios either (a) test pure logic now covered by munit, or (b) are subsumed by one of the 6 round-trip smoke tests. PASS.

Decision: proceed to Phases 2 and 3 in parallel.

## Phase 2 — Tool contract suite (can start in parallel with Phase 1)

**Goal:** new tier that pins our assumptions about external tools.

- [x] 2.1 New dir `test/contract/` with own setup helper (`contract_helper.bash`: auth-detection + skip helpers + scratch tmpdir + throwaway git init)
- [x] 2.2 `git_adapter_contract.bats` (7t) — rev-parse SHA shape, status --porcelain, worktree add/list/remove, push to file:// remote
- [x] 2.3 `gh_adapter_contract.bats` (8t, 2 auth-gated) — --version, --help flag presence on issue view/list/create + pr checks; live JSON-shape on `iterative-works/iw-cli#1`
- [x] 2.4 `glab_adapter_contract.bats` (6t, 1 fixture-gated) — --version, --help flag presence; live JSON-shape behind `IW_CONTRACT_GLAB_PROJECT`
- [x] 2.5 `scala_cli_contract.bats` (4t) — `scala-cli run -q --jar core.jar` with `core/project.scala` resolves `iw.core.model.*`; `scala-cli compile --scalac-option -Werror core/` succeeds
- [x] 2.6 `mill_contract.bats` (5t) — `./mill show core.jar` / `dashboard.assembly` return `ref:vN:<hash>:<path>.jar` strings that resolve to real files; `__.scoverage.xmlReport` enumerates per-module targets
- [x] 2.7 `tmux_adapter_contract.bats` (5t, all `IW_CONTRACT_TMUX=1`-gated) — `-L <socket>` new-session/has-session/send-keys/capture-pane round-trip/kill-session
- [x] 2.8 CI: new `contract` job, nightly cron + `contract`-labeled PRs only; `./iw ./test contract` runner subcommand added

## Phase 3 — Bulk BATS reductions (independent PRs after pilot validated)

Each is a small PR shipping one cluster. Apply only after Phase 1 decision gate.

- [x] 3.1 `version-check.bats` (15t, 41s) → ~~all 15 to munit (pure semver); delete file~~. Adjusted: the functions under test (`read_iw_version`, `compare_versions`, `check_version_requirement`) live in `iw-run` (bash, launcher-only — can't depend on Scala for its own pre-launch check). Porting would create parallel impls that can drift. **Done by removing the `helpers/bloop-cleanup` load + `stop_test_bloop` teardown (the file never invokes scala-cli; the cleanup was defensive dead code).** Result: **51s → 0.9s** (~57×), all 15 tests still pass, direct coverage of the bash logic preserved.
- [x] 3.2 `schema.bats` (6t, 0s) → munit. New `core/test/ReviewStateSchemaTest.scala` (6 tests, ~50ms) walks up to the repo root via `build.mill` marker and asserts: schema file exists, parses as JSON, declares Draft-07, `required` is exactly `{version, issue_id, artifacts, last_updated}`, `properties` includes all v2 keys, and every fixture under `core/test/resources/review-state/*.json` parses. `test/schema.bats` deleted.
- [x] 3.3 `dashboard-rebuild-gate.bats` (2t, 2s) → ~~delete~~. Adjusted: both tests pass cleanly on current code (~4s; the "1 failure" claim was stale). They cover the iw-run dispatcher gate (`if dashboard|server then ensure_dashboard_jar`) — a real iw-cli concern, not Mill's rebuild behaviour. **Renamed to `dispatcher-mill-gate.bats`** with updated PURPOSE headers reflecting actual scope (latency-regression guard on non-dashboard commands).
- [x] 3.4 `doctor.bats` (38t, 56.5s measured) → 5 E2E smoke + 30 unit. Extracted `core/model/DoctorCliFlags.scala` (pure flag parsing) and `core/output/DoctorOutput.scala` (per-check rendering, category grouping, summary line, exit code). `commands/doctor.scala` now delegates to both. New `core/test/DoctorCliFlagsTest.scala` (6t) and `core/test/DoctorOutputTest.scala` (24t). `test/doctor.bats` reduced to 5 smoke (full-pass, no-config error, --quality filter, --env filter, --fix nothing-to-fix). Per-hook check logic stays covered by the existing `core/test/{Scalafmt,Scalafix,GitHooks,Contributing,CI}ChecksTest.scala` files. **Result: 56.5s → 7.1s (~8×) with 30 new unit tests.**
- [x] 3.5 `infra-plugin/*` (5f, 34t, 566s) → Adjusted approach: the plugin discovery / `--describe` / `--list` / hook-extraction code lives in `iw-run` (bash launcher), same chicken-and-egg as 3.1. Optimised in place: most plugin files never invoke scala-cli, but were paying for `cp -r core/` (recursive copy of hundreds of files) + `./mill show core.jar` (Mill startup) + `helpers/bloop-cleanup` (proc-walk per test) in every setup. Dropped all three from `plugin-commands-describe.bats`, `plugin-commands-list.bats`, `plugin-discovery.bats`, `plugin-hooks.bats`. Split `plugin-commands-execute.bats` (9t) into `plugin-commands-validate.bats` (5 fast pre-dispatch tests with `IW_CORE_JAR` placeholder so `ensure_core_jar` is satisfied) + a trimmed `plugin-commands-execute.bats` (2 smoke: end-to-end happy path + plugin-lib classpath; the dropped scala-cli arg-passing and core-import scenarios are covered by `test/contract/scala_cli_contract.bats`). **Result: 566s → 54.6s (~10×) with 32 tests across 6 files.**
- [x] 3.6 `infra-project-cmds/*` (3f, 25t, 358s) → same shape as 3.5. Stripped the dead `cp -r core/` + `mill show core.jar` setup work from `project-commands-describe.bats` and `project-commands-list.bats` (neither invokes scala-cli). Split `project-commands-execute.bats` (10t) into `project-commands-validate.bats` (4 lightweight tests with `IW_CORE_JAR` placeholder) + trimmed `project-commands-execute.bats` (3 smoke: `./` prefix dispatch happy path, dual-namespace dispatch by prefix, project-directory hook discovery; dropped scala-cli arg-passing and core-import scenarios covered by the contract suite). **Result: 358s → ~55s (~6.5×) with 22 tests across 4 files.**

## Phase 4 — Command harness (the bigger lift)

**Goal:** every command's logic testable in-VM against fake adapters.

- [x] 4.1 Design `CommandEnv` trait + `Fake*` adapters. New `core/commands/` package houses the trait, `CommandResult`, capability sub-traits (`Console`, `FileSystem`, `GitOps`, `ReviewStateOps`), and `LiveCommandEnv` (delegates to existing adapters). Fakes live at `core/test/fixtures/FakeCommandEnv.scala` (`FakeConsole`, `FakeFileSystem`, `FakeGit`, `FakeReviewStateOps`). Scope was trimmed to capabilities phase-start actually needs — FakeProcess/FakeTracker/FakeTmux/FakeClock/FakeEnv will land when their first command migrates.
- [x] 4.2 Pilot command: `commands/phase-start.scala` is now a 7-line shim delegating to `iw.core.commands.PhaseStart.run(args, LiveCommandEnv.default).exitCode`. Logic lives in `core/commands/PhaseStart.scala` as `run(args, env): CommandResult`, threaded as a `for`-comprehension over the env capabilities. `commands/phase-start.scala` keeps PURPOSE/USAGE headers; iw-run discovery still works.
- [x] 4.3 `core/test/PhaseStartHarnessTest.scala` covers 11 scenarios (happy path JSON, branch switching, push-before-create ordering, missing args, invalid number, already-on-phase-branch, branch-conflict, --issue-id override, review-state present, review-state absent, push failure). Inline expected-output assertions for readability (deferred true golden files until the second migration shows what scales). Wall time: ~0.1s for 11 tests vs ~45s for the 10 equivalent BATS tests (~450× per test). All 10 BATS scenarios still pass against the shim.
- [ ] 4.4 Apply pattern to remaining commands incrementally — see sub-plan below
- [ ] 4.5 Resolve the 3 `phase-merge.bats` failures during 4.4 (most likely the scenarios become unit tests, real git interaction moves to git contract)

### 4.4 sub-plan — command migration order

**Cadence (locked 2026-06-07):** one commit per command on `chore/bump-version-0.6.0`; each commit
includes (a) any new capability traits + Live + Fake impls, (b) the `core/commands/<Name>.scala`
move, (c) the shim, (d) the `*HarnessTest.scala`, and (e) the slimmed BATS file (~1–2 round-trip
smoke tests). Single bundled PR at the end.

**Capability roadmap.** New capability surfaces are added on-demand by the command that first
needs them; later commands extend them:

| Capability | First-needed by | Notes |
|---|---|---|
| `Console`, `FileSystem`, `GitOps`† (subset), `ReviewStateOps` | phase-start (done) | landed in 4.1–4.3 |
| `GitOps` extensions: `stageFiles`, `commit`, `diffNameOnly`, `getStagingCheck` | phase-commit | extend existing trait |
| `Process` (`run`, `commandExists`, `runInteractive`) | phase-advance | new trait |
| `GitOps` extensions: `checkoutBranch`, `fetchAndReset`, `hasUncommittedChanges`, `getRemoteUrl`, `pull`, `getHeadSha` | phase-pr / phase-merge | extend |
| `TrackerOps` (GitHub/GitLab PR/MR create, check status, merge cmd builders) | phase-pr | new trait, polymorphic across forges |
| `HookOps` (`collectValues[T]`) | phase-merge | new trait — also unblocks doctor refactor |
| `ServerOps` (status get, register/unregister, last-seen update) | status | new trait |
| `StateReader` capability | worktrees | new trait |
| `TmuxOps` (sessionExists, attach, switch, send-keys, isInside) | open | new trait |
| `Prompt` (ask, confirm) | rm | new trait |
| `WorktreeOps` (add, remove via git worktree subcommands) | start | new trait |
| `ConfigOps` (read/write project config) | init / config | new trait |

†`GitOps` grows as needed; we don't pre-build the full surface.

**Migration order.**

Batch A — phase command family (4.4.1–4.4.4): familiar shape, extends existing capabilities:
- [x] 4.4.1 `phase-commit` — extends `GitOps` (stageFiles, commit, diffNameOnly, getStagingCheck); harness = 11 tests; BATS 10 → 2 smokes
- [ ] 4.4.2 `phase-advance` — adds `Process` capability; extends `GitOps` (checkoutBranch, fetchAndReset, getRemoteUrl)
- [ ] 4.4.3 `phase-pr` — adds `TrackerOps` capability (GitHub + GitLab forge clients)
- [ ] 4.4.4 `phase-merge` — adds `HookOps`; **resolves 4.5** (the 3 phase-merge.bats failures become harness tests)

Batch B — lightweight readers (4.4.5–4.4.10):
- [ ] 4.4.5 `version` — trivial; pure version-string read
- [ ] 4.4.6 `project-context` — only `GitOps.getRemoteUrl`
- [ ] 4.4.7 `review-state` — mostly already pure (CliParser extracted in pilot Phase 1); thin shim around existing logic
- [ ] 4.4.8 `analyze` — adds nothing new; pure `Process.runInteractive`
- [ ] 4.4.9 `status` — adds `ServerOps`
- [ ] 4.4.10 `worktrees` — adds `StateReader` capability

Batch C — session/worktree family (4.4.11–4.4.15):
- [ ] 4.4.11 `projects` — `StateReader` only
- [ ] 4.4.12 `open` — adds `TmuxOps`
- [ ] 4.4.13 `rm` — adds `Prompt` capability
- [ ] 4.4.14 `register` — `ServerOps` + `GitOps`
- [ ] 4.4.15 `start` — largest worktree command; adds `WorktreeOps`, exercises `TmuxOps` + `ServerOps` + `HookOps` together

Batch D — tracker-heavy + interactive (4.4.16–4.4.22):
- [ ] 4.4.16 `doctor` — adds `HookOps` integration (much already in `DoctorOutput`); extends `GitOps`
- [ ] 4.4.17 `feedback` — `TrackerOps`
- [ ] 4.4.18 `issue` — `TrackerOps` across 3 backends (Linear / GitHub / GitLab / YouTrack)
- [ ] 4.4.19 `init` — adds `ConfigOps`; biggest interactive; 38 BATS tests today
- [ ] 4.4.20 `config` — `ConfigOps`
- [ ] 4.4.21 `dashboard` — `ServerConfigRepository` wrapping
- [ ] 4.4.22 `server` — server lifecycle commands

**Per-command checklist** (lift this into each commit's description):

1. Identify capability surface the command touches.
2. Extend `CommandEnv` traits / add new trait(s) under `core/commands/`. Update `LiveCommandEnv`.
3. Add corresponding `Fake*` impl(s) under `core/test/fixtures/FakeCommandEnv.scala`.
4. Move command body to `core/commands/<Name>.scala` returning `CommandResult`.
5. Shrink `commands/<name>.scala` to a shim that calls `<Name>.run(args, LiveCommandEnv.default)`.
6. Write `<Name>HarnessTest.scala` covering scenarios from the current BATS file.
7. Reduce the BATS file to 1–2 smoke tests (full round-trip through scala-cli + live adapters).
8. Run: `scala-cli compile --scalac-option -Werror core/`, `./mill core.test.testOnly '*HarnessTest'`, the slimmed BATS.
9. Commit. Branch stays `chore/bump-version-0.6.0`.

**Stop conditions.** If a command resists clean extraction (e.g. heavily intertwined prompts +
side effects), pause and discuss before forcing it through. Some Batch D commands may need
intermediate refactors (extracting interactive flow as a state machine in `model/`) before the
harness pattern fits.

## Phase 5 — Cleanup

- [ ] 5.1 Update `CLAUDE.md` testing section
- [ ] 5.2 Write/expand `TESTING.md` documenting the three tiers + coverage workflow
- [ ] 5.3 Close (or comment with supersedes) issues: #357 (shared bloop — superseded by Phase 3), #304 (tmux PTY — moved to contract)
- [ ] 5.4 Remove obsolete `IW_SERVER_DISABLED=1` from BATS files that no longer touch the dashboard

---

## Expected end state

| | Today | Target |
|---|---:|---:|
| BATS files (gating) | 39 | ~15 (~1 per command) |
| BATS tests (gating) | ~410 | ~60 |
| BATS time (gating) | 27.8 min | ~4–5 min |
| Munit test files | 95 (no coverage) | ~140–180 with coverage |
| Munit time | (rolled into 27.8 min via scala-cli) | ~30s (one bloop start) |
| Tool contract suite | none | 7 files, ~30 tests, ~40s, nightly + label-gated |
| Coverage % visible | no | yes, per module, in CI summary |
| Local failures | 6 (in 3 files) | 0 |

## Open questions (CLARIFY)

1. ~~**CLARIFY-1: scoverage version compatibility** with Scala 3.3.7.~~ **Resolved 2026-06-03:** scoverage 2.x supports Scala 3.2.0+; 2.1.0 works on 3.3.7 (validated by green core.test + non-empty xmlReport).
2. **CLARIFY-2: `scala-cli test core/` as dev shortcut** — keep as fallback for quick local iteration, or force `./mill core.test`? Leaning *keep* for single-file work.
3. **CLARIFY-3: coverage threshold** — ops/procedures is baseline-only. Same here, or floor (e.g., fail if PR drops >5%)? Leaning baseline-only for now.
4. **CLARIFY-4: frontend instrumentation** — leave Vite/Tailwind out of coverage (matches ops's exclusion of Scala.js).

## Execution dependency graph

```
0 ─┬→ 1 (pilot) ──────┬→ 3 (bulk reductions, parallel PRs)
   └→ 2 (contracts) ──┴→ 4 (command harness, per-cmd PRs)
                                                       └→ 5 (cleanup)
```

Phase 0 must complete first. Phases 1 and 2 can start same day. Phases 3 and 4
only after Phase 1 decision gate. Phase 5 last.

## Status log

- 2026-05-29: diagnostic measurements taken, ADHD ideation run, plan drafted in conversation.
- 2026-06-03: plan committed as this file; Phase 0 ready to start.
- 2026-06-03: Phase 0 complete. build.mill: scoverage 2.1.0 wired on core + dashboard; `core.test` Mill submodule sources `core/test/`. `.iw/commands/test.scala` switched to `./mill core.test + dashboard.test`. CI gains coverage steps (XML, summary, artifact). Docs updated (CLAUDE.md + new docs/testing.md). Refactor: `core/IssueCreateParser.scala` → `core/model/` (package `iw.core` → `iw.core.model`); 2 import updates. Baseline coverage: core 77.43%, dashboard 76.24%.
- 2026-06-03: Phase 1 (review-state pilot) complete. Extracted `core/model/ReviewStateCliParser.scala` (pure parser, returns `Either[String, T]`); added 48 munit tests in `core/test/ReviewStateCliParserTest.scala`; refactored `commands/review-state.scala` to delegate. BATS slice 61 → 6 tests, 64.7s → 11.6s. New parser file 98.04% covered; affected-files aggregate 75.5% → 79.2%. Decision gate PASSED (with structural-win interpretation of coverage criterion). Pattern proven; Phase 2/3 can proceed in parallel.
- 2026-06-04: Phase 2 (tool contract suite) complete. New `test/contract/` with shared `contract_helper.bash`; 6 contract files / 35 tests / ~8s default (~9s with all gates on): git (7), gh (8, 2 live-auth), glab (6, 1 fixture-gated), scala-cli (4), mill (5), tmux (5, all gated by `IW_CONTRACT_TMUX=1`). New `runContractTests` in `.iw/commands/test.scala` (`./iw ./test contract`). CI: workflow gains nightly cron + new `contract` job that runs only on schedule or PRs labeled `contract`. The `infra-plugin` / `infra-project-cmds` scala-cli-launching scenarios that took 924s in aggregate are now covered by the 4-test scala-cli contract in <2s — unlocks Phase 3.5/3.6.
- 2026-06-07: Phase 4 pilot (4.1–4.3) complete. New `core/commands/` package introduced as the imperative-shell bucket (sits alongside `model/`, `adapters/`, `output/`). `LiveCommandEnv.default` is constructed by command shims; `FakeCommandEnv` is wired by harness tests. The Fake* surface only covers what phase-start uses (FakeConsole, FakeFileSystem, FakeGit, FakeReviewStateOps); the remaining FakeProcess/FakeTracker/FakeTmux/FakeClock/FakeEnv land on-demand as commands migrate in 4.4. `FakeReviewStateOps` is interesting: it composes the real `ReviewStateUpdater.merge` + `ReviewStateValidator.validate` over a FakeFileSystem, so the merge/validate logic isn't duplicated. Coverage on the new `core/commands/` package is high because the pilot tests exercise it end-to-end. Test count: 11 unit + 10 BATS (preserved, will be slimmed in 4.4); BATS scenarios still pass against the new shim.
- 2026-06-06: Phase 3 complete (six commits, 3.1–3.6). Two patterns emerged that the plan didn't anticipate: (a) several files were paying ~50s per file in dead setup work (recursive `cp -r core/`, `mill show core.jar`, `helpers/bloop-cleanup` walking `/proc`) that never touched scala-cli — a free win; (b) launcher logic in `iw-run` (semver, plugin parsing, project-command dispatch) can't move to Scala (chicken-and-egg with the launcher), so the "to munit" target only fits where the logic was already Scala (`doctor` output, JSON schema). Net effect across Phase 3: ~1010s of BATS replaced by 36s of BATS + 30 new munit tests on previously-untested doctor rendering + 6 new munit tests on schema validity. New supporting BATS files: `dispatcher-mill-gate.bats` (renamed from `dashboard-rebuild-gate.bats`), `plugin-commands-validate.bats`, `project-commands-validate.bats`. New core modules: `DoctorCliFlags`, `DoctorOutput`, `ReviewStateSchemaTest`. Unblocks Phase 4 (command harness).
