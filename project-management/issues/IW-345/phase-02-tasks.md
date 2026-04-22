# Phase 2 Tasks: Dashboard JVM module — move, rename, test migration

## Setup

- [x] [setup] Create the `dashboard/jvm/src/`, `dashboard/jvm/resources/`, and `dashboard/jvm/test/src/` target directory tree at the repo root so the subsequent `git mv` operations land on prepared paths.
- [x] [setup] Run the discovery command `rg 'iw\.core\.dashboard' core/test/ -l` and capture the full hit list; classify each file per phase-02-context (dashboard-touching → migrate; fully-qualified or fixture-only → stay) before any file moves so the migration set is pinned in writing before the mechanical pass.
- [x] [setup] Inspect any shared `TestFixtures.scala` / test-utility file referenced by both migrated and stayed tests; decide whether to duplicate, move, or split it and record the decision alongside the classifier output.

## Tests

- [x] [test] Regression checkpoint (baseline): before any build or source changes, run `./iw ./test unit` on the current tree and capture the passing-test count (~141) as the Phase 2 baseline.
- [ ] [test] Regression checkpoint (baseline): before any changes, run the full pre-push hook (format + scalafix + `-Werror` compile + unit tests + command compilation) and confirm it is green so any later failure is attributable to Phase 2 edits.
- [ ] [test] Regression checkpoint (post-change): after the rename, build edits, and test migration are complete, run `./iw ./test unit` end to end and confirm the combined scala-cli core-test run and `./mill dashboard.test` run produce the same total test count as the baseline (no coverage regression).
- [ ] [test] Regression checkpoint (post-change): run the pre-push hook on the final state and confirm it passes with the scala-cli core tests AND `./mill dashboard.test` both green.

## Implementation

- [x] [impl] Use `git mv` to relocate every top-level `core/dashboard/*.scala` file (`CaskServer.scala`, `DashboardService.scala`, `StateRepository.scala`, `ArtifactService.scala`, `GitStatusService.scala`, `IssueCacheService.scala`, `IssueSearchResult.scala`, `IssueSearchService.scala`, `PathValidator.scala`, `ProjectRegistrationService.scala`, `PullRequestCacheService.scala`, `RefreshThrottle.scala`, `ReviewStateService.scala`, `ServerStateService.scala`, `WorkflowProgressService.scala`, `WorktreeCardService.scala`, `WorktreeListSync.scala`, `WorktreeListView.scala`, `WorktreeRegistrationService.scala`, `WorktreeUnregistrationService.scala`) to `dashboard/jvm/src/`, preserving filenames one-for-one.
- [x] [impl] Use `git mv` to relocate `core/dashboard/application/**`, `core/dashboard/domain/**`, `core/dashboard/infrastructure/**`, and `core/dashboard/presentation/**` to `dashboard/jvm/src/{application,domain,infrastructure,presentation}/**`, preserving sub-package structure one-for-one with no reorganisation.
- [x] [impl] Use `git mv` to relocate every file under `core/dashboard/resources/**` to `dashboard/jvm/resources/**`, preserving the internal directory structure so Cask's classpath resource lookups keep resolving.
- [x] [impl] Use `git mv` to relocate the classified dashboard-touching subset of `core/test/*.scala` to `dashboard/jvm/test/src/*.scala` (one file at a time, preserving filenames). Move any shared test utility per the Setup decision.
- [ ] [impl] Execute the package-rename `sed` pass across the enumerated paths: `rg -l 'iw\.core\.dashboard' dashboard/ core/test/ commands/ core/adapters/ProcessManager.scala core/CLAUDE.md | xargs sed -i 's/iw\.core\.dashboard/iw.dashboard/g'`. This covers the moved source tree, migrated tests, stayed tests that still reference dashboard types, both bridge command scripts, the ProcessManager string literal, and the CLAUDE.md guidance line in one pass.
- [ ] [impl] Verify `commands/server-daemon.scala`'s own `package` line flipped from `package iw.core.dashboard` to `package iw.dashboard` (load-bearing for same-package visibility into `CaskServer.start` at line 17).
- [ ] [impl] Edit `build.mill` `core` module: remove the three dashboard-only `mvnDeps` entries (`cask`, `scalatags`, `flexmark`) along with the in-transit comment, and drop `moduleDir / os.up / "core" / "dashboard",` from the `sources` enumeration so the final list is `adapters`, `model`, `output`, plus root `IssueCreateParser.scala`.
- [ ] [impl] Add a new `object dashboard extends ScalaModule` to `build.mill` alongside `core`, with `scalaVersion = "3.3.7"`, `moduleDeps = Seq(core)`, `def sources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "src")`, explicit `def resources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "resources")`, `mvnDeps` for `cask 0.11.3`, `scalatags 0.13.1`, `works.iterative::scalatags-webawesome:3.2.1.1`, `flexmark-all 0.64.8`, and an inner `object test extends ScalaTests with TestModule.Munit` whose `sources` points at `dashboard/jvm/test/src` and whose `mvnDeps` lists `org.scalameta::munit::1.2.1`.
- [ ] [impl] Edit `core/project.scala`: remove the three `//> using dep` lines for `com.lihaoyi::cask:0.11.3`, `com.lihaoyi::scalatags:0.13.1`, and `com.vladsch.flexmark:flexmark-all:0.64.8`. Leave the SYNC comment in place.
- [ ] [impl] Edit `commands/dashboard.scala`: add scoped `//> using dep` lines at the top for `com.lihaoyi::cask:0.11.3`, `com.lihaoyi::scalatags:0.13.1`, `com.vladsch.flexmark:flexmark-all:0.64.8` (transitional bridge, earmarked for Phase 4 deletion). Imports already flipped to `iw.dashboard.*` by the sed pass — confirm no logic changes.
- [ ] [impl] Edit `commands/server-daemon.scala`: add scoped `//> using dep` lines at the top for `com.lihaoyi::cask:0.11.3`, `com.lihaoyi::scalatags:0.13.1`, `com.vladsch.flexmark:flexmark-all:0.64.8` (transitional bridge). `package` declaration already flipped by the sed pass — confirm no logic changes.
- [ ] [impl] Verify `core/adapters/ProcessManager.scala:115` string literal changed from `"iw.core.dashboard.ServerDaemon"` to `"iw.dashboard.ServerDaemon"` via the sed pass; no surrounding logic touched.
- [ ] [impl] Verify the `core/CLAUDE.md` guidance line flipped from `// Do NOT import iw.core.dashboard.*` to `// Do NOT import iw.dashboard.*` via the sed pass.
- [ ] [impl] Edit `.iw/commands/test.scala` so the `unit` stage invokes BOTH the existing scala-cli munit run against the remaining `core/test/` AND `./mill dashboard.test`; both invocations must succeed for the stage to pass. The no-arg `./iw ./test` (unit + e2e) composition is unchanged.

## Integration

- [ ] [integration] Run the blocking grep invariant `rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'` and confirm zero results repo-wide — abort and audit if anything remains before running tests.
- [ ] [integration] Run `rg 'iw\.core\.dashboard' core/test/` and confirm zero results (migration is mechanically complete).
- [ ] [integration] Run `rg 'cask|scalatags|flexmark' core/project.scala` and confirm zero results (dep cleanup landed).
- [ ] [integration] Run `rg '^\s*mvn"com\.lihaoyi::cask' build.mill` (and equivalents for `scalatags` / `flexmark`) and confirm none appear under `core.mvnDeps` — they live only under `dashboard.mvnDeps`.
- [ ] [integration] Run `rg 'iw\.dashboard' dashboard/jvm/test/src/` and confirm positive hits, cross-checking against the classifier output that every migrated test is present and no file appears in both `core/test/` and `dashboard/jvm/test/src/`.
- [ ] [integration] Run `./mill dashboard.compile` from a clean tree and confirm it succeeds.
- [ ] [integration] Run `./mill dashboard.test` and confirm all migrated unit tests pass.
- [ ] [integration] Run `./mill iwCoreJar` and confirm core still compiles cleanly and produces `build/iw-core.jar` without the three dashboard-only deps.
- [ ] [integration] Run `scala-cli compile commands/` and confirm the four external references (`commands/dashboard.scala`, `commands/server-daemon.scala`, `core/adapters/ProcessManager.scala`, `core/CLAUDE.md`) keep compiling via the minimal import/package/string updates plus the scoped `//> using dep` bridge lines.
- [ ] [integration] Cross-check version strings line-by-line between `build.mill`'s `dashboard.mvnDeps` and the scoped `//> using dep` lines on `commands/dashboard.scala` and `commands/server-daemon.scala` — confirm `cask 0.11.3`, `scalatags 0.13.1`, `flexmark-all 0.64.8` match exactly with no drift.
- [ ] [integration] From a fully clean tree (`rm -rf out/ build/`), run `./mill dashboard.test` and confirm Mill compiles core and dashboard via the module graph and all migrated tests pass green.
- [ ] [integration] Smoke-check the dashboard launch: `IW_SERVER_DISABLED=0 ./iw dashboard --state-path /tmp/iw-phase02-smoke &`, `PID=$!; sleep 2`, `curl -s http://localhost:<port>/ | grep -q '<html'`, `kill $PID`. If a named BATS test in `test/` already covers `./iw dashboard` end-to-end, run that test instead and record its name. Confirms the transitional bridge still resolves `iw.dashboard.*` types at runtime.
- [ ] [integration] Run the pre-push hook end to end with the post-change tree and confirm green: format + scalafix + `-Werror` compile + unit tests (scala-cli core + `./mill dashboard.test`) + command compilation.

## Acceptance Criteria Coverage

- AC1 `./mill dashboard.compile` succeeds from a clean tree → `[integration]` clean-tree `./mill dashboard.compile` run; `[integration]` clean-tree `./mill dashboard.test` run.
- AC2 `./mill dashboard.test` runs the migrated unit tests; all pass → `[impl]` test migration via `git mv`; `[impl]` sed-pass package rename; `[integration]` `./mill dashboard.test` run.
- AC3 `./mill iwCoreJar` still succeeds after dashboard extraction and dep removal → `[impl]` `build.mill` core-module edits; `[impl]` `core/project.scala` dep removal; `[integration]` `./mill iwCoreJar` run.
- AC4 Repo-wide grep `rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'` returns zero → `[impl]` sed-pass package rename; `[integration]` repo-wide grep invariant.
- AC5 `rg 'iw\.core\.dashboard' core/test/` returns zero → `[impl]` test migration via `git mv`; `[impl]` sed-pass package rename; `[integration]` `core/test/` grep invariant.
- AC6 `core/project.scala` no longer lists `cask`, `scalatags`, or `flexmark` → `[impl]` `core/project.scala` dep removal; `[integration]` `cask|scalatags|flexmark` grep against `core/project.scala`.
- AC7 `commands/dashboard.scala` and `commands/server-daemon.scala` declare scoped `//> using dep` lines for the three bridge deps → `[impl]` `commands/dashboard.scala` scoped dep add; `[impl]` `commands/server-daemon.scala` scoped dep add; `[integration]` version-string cross-check.
- AC8 `build.mill` `core.mvnDeps` no longer lists the three deps; `core.sources` no longer enumerates `dashboard` → `[impl]` `build.mill` core-module edits; `[integration]` `build.mill` mvn-entry grep under `core.mvnDeps`.
- AC9 `build.mill` `dashboard` module declares `moduleDeps = Seq(core)`, the four `mvnDeps`, explicit `def resources`, and inner `object test extends ScalaTests with TestModule.Munit` → `[impl]` `build.mill` dashboard-module add; `[integration]` `./mill dashboard.compile`; `[integration]` `./mill dashboard.test`.
- AC10 `scala-cli compile commands/` still succeeds via the four external-reference edits → `[impl]` sed-pass (ProcessManager string + CLAUDE.md line); `[impl]` scoped-dep additions on both bridge scripts; `[impl]` `server-daemon.scala` package-line flip verification; `[integration]` `scala-cli compile commands/` run.
- AC11 Migrated dashboard tests pass under Mill; remaining core tests pass under scala-cli; total test count unchanged from the Phase 1 baseline of 141 → `[test]` baseline `./iw ./test unit`; `[test]` post-change `./iw ./test unit`; `[integration]` `rg 'iw\.dashboard' dashboard/jvm/test/src/` partition check.
- AC12 `./iw ./test unit` runs both scala-cli core tests and `./mill dashboard.test`; both green for the stage to succeed → `[impl]` `.iw/commands/test.scala` unit-stage extension; `[test]` post-change `./iw ./test unit`.
- AC13 Pre-push hook passes end to end (format + scalafix + `-Werror` compile + unit tests + command compilation) → `[test]` baseline pre-push run; `[test]` post-change pre-push run; `[integration]` pre-push hook end-to-end run.
- AC14 Smoke check passes: dashboard launches, `curl` sees `<html`, teardown is clean → `[integration]` dashboard smoke-check (or named BATS equivalent).
- AC15 From-clean-tree `./mill dashboard.test` compiles core and dashboard via the Mill graph and runs migrated tests green → `[integration]` clean-tree (`rm -rf out/ build/`) `./mill dashboard.test` run.

**Phase Status:** Pending
