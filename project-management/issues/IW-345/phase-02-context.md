# Phase 2: Dashboard JVM module — move, rename, test migration

**Issue:** IW-345
**Layer:** L1
**Branch:** IW-345 (feature branch)
**Status:** Not started
**Estimated:** 4-7 hours

## Goals

- Move all dashboard server sources from `core/dashboard/**` to `dashboard/jvm/src/**` and rename the package `iw.core.dashboard.*` → `iw.dashboard.*` across every file in that tree.
- Introduce a Mill `dashboard` ScalaModule with `moduleDeps = Seq(core)` so Mill handles cross-module change detection natively; give it its own `mvnDeps` (cask, scalatags, scalatags-webawesome, flexmark) and an inner `object test extends ScalaTests with TestModule.Munit`.
- Migrate the dashboard-touching unit tests from `core/test/*.scala` to `dashboard/jvm/test/src/*.scala` so they run under `./mill dashboard.test`.
- Drop dashboard-only deps (`cask`, `scalatags`, `flexmark`) from BOTH `core/project.scala` AND `build.mill`'s `core.mvnDeps` in a single coordinated edit; narrow `build.mill`'s `core.sources` enumeration to exclude the now-moved `dashboard` directory.
- Keep the dashboard running bit-identically to today: `commands/dashboard.scala` and `commands/server-daemon.scala` still launch the in-process scala-cli path; only their imports + scoped `//> using dep` lines are touched so they keep compiling during the transition (Option A — transitional bridge). Full launcher rewrite is Phase 4.
- Extend `./iw ./test unit` so the unit stage now also invokes `./mill dashboard.test`.

## Scope

### In scope
- Directory move of every file under `core/dashboard/**` (top-level `.scala` files + `application/`, `domain/`, `infrastructure/`, `presentation/`, `resources/`) to `dashboard/jvm/**`, preserving sub-package structure one-for-one. Resources land under `dashboard/jvm/resources/` (Mill default layout) — see Directory move.
- Mechanical package rename `iw.core.dashboard.*` → `iw.dashboard.*` across every moved file's `package` declaration and every internal import.
- `commands/server-daemon.scala`'s `package` declaration itself changes from `package iw.core.dashboard` to `package iw.dashboard`. The script relies on same-package visibility to reach `CaskServer.start` (line 17) and on `ServerDaemon`'s fully-qualified class name lining up with the `--main-class` string literal in `ProcessManager.scala:115`. All three must land together (see Risks).
- Test migration: move the dashboard-touching subset of `core/test/*.scala` into `dashboard/jvm/test/src/*.scala`, updating their imports to `iw.dashboard.*`. Classifier spelled out in Test migration.
- `build.mill` delta: new `object dashboard extends ScalaModule` with `moduleDeps = Seq(core)`, explicit sources under `dashboard/jvm/src`, an explicit `resources` override pointing at `dashboard/jvm/resources`, its own `mvnDeps` list, and an inner `object test extends ScalaTests with TestModule.Munit` whose sources live under `dashboard/jvm/test/src`. Dependency wiring on the dashboard module: `cask 0.11.3`, `scalatags 0.13.1`, `works.iterative::scalatags-webawesome:3.2.1.1`, `flexmark-all 0.64.8`, plus `org.scalameta::munit::1.2.1` on the test sub-module.
- `build.mill` delta on `core`: remove `cask`, `scalatags`, `flexmark` from `core.mvnDeps`; drop `core/dashboard` from the explicit `sources` enumeration (leaving `adapters`, `model`, `output`, and the root `IssueCreateParser.scala` file).
- `core/project.scala` delta: remove the three `//> using dep` lines for `cask`, `scalatags`, `flexmark`.
- Option A — transitional bridge: `commands/dashboard.scala` and `commands/server-daemon.scala` each gain scoped `//> using dep` lines for `cask 0.11.3`, `scalatags 0.13.1`, `flexmark-all 0.64.8` so the scala-cli launch path keeps resolving dashboard deps without `core/project.scala` pulling them project-wide. These two `//> using dep` blocks are earmarked for deletion in Phase 4 when the scripts stop importing `iw.dashboard.*` types.
- Minimal external-reference updates (imports/string only — no logic changes):
  - `commands/dashboard.scala` — update imports of dashboard types from `iw.core.dashboard.*` to `iw.dashboard.*`; add the scoped `//> using dep` lines above.
  - `commands/server-daemon.scala` — update `package` declaration from `iw.core.dashboard` to `iw.dashboard`; add the scoped `//> using dep` lines above.
  - `core/adapters/ProcessManager.scala:115` — update the string literal `"iw.core.dashboard.ServerDaemon"` to `"iw.dashboard.ServerDaemon"`.
  - `core/CLAUDE.md` — the line `// Do NOT import iw.core.dashboard.*` updates to `// Do NOT import iw.dashboard.*` so project guidance stays truthful.
- `./iw ./test unit` update (in `.iw/commands/test.scala`) so the unit stage invokes both the scala-cli munit run against remaining `core/test/*.scala` AND `./mill dashboard.test`.

### Out of scope
- Phase 3 (L2 + L3): `dashboard/frontend/` Yarn 4 + Vite + Tailwind + Web Awesome pipeline, `frontend` Mill module, `dashboard.assembly` fat jar, `object itest` integration test module, new `dashboard-build` CI job, `WEBAWESOME_NPM_TOKEN` provisioning.
- Phase 4 (L4): full rewrite of `commands/dashboard.scala` and `commands/server-daemon.scala` to spawn `java -jar build/iw-dashboard.jar`, `ensure_dashboard_jar` / `needs_dashboard_rebuild` in `iw-run`, double-gated dev mode, `VITE_DEV_URL` validation, `assetUrl` template helper, README/CLAUDE.md documentation of the two-build-tool boundary. Deleting the scoped `//> using dep` lines from `commands/dashboard.scala` and `commands/server-daemon.scala` is part of this Phase 4 cleanup.
- Any refactoring of dashboard code beyond the mechanical rename. FCIS sub-packages (`application/`, `domain/`, `infrastructure/`, `presentation/`) move wholesale — no reorganisation.
- Any user-visible behaviour change. `./iw dashboard` runs bit-identically; it still launches via the in-process scala-cli path, not a jar.

## Dependencies

### Prior phases
- **Phase 1 (landed, commits 4f35f4c + ff6ee20):** Mill 1.1.5 in the repo, `./mill` wrapper, `.mill-version`, `build.mill` with `object core extends ScalaModule` and a top-level `iwCoreJar()` `Task.Command` that materialises `build/iw-core.jar`. `core.sources` is an explicit enumeration (`adapters`, `dashboard`, `model`, `output`, plus root `IssueCreateParser.scala`) and Phase 2 must edit that list. Dashboard-only deps (`cask`, `scalatags`, `flexmark`) currently live in BOTH `core/project.scala` AND `build.mill`'s `core.mvnDeps` per Phase 1's deliberate deferral — Phase 2 drops them from both locations simultaneously.

### External
- None new. Mill 1.1.5 and the `./mill` wrapper already exist. `scalatags-webawesome:3.2.1.1` resolves from Maven Central (verified in analysis); no Web Awesome Pro token needed at this phase because no frontend pipeline is in play yet.

## Approach

### Directory move

Every file under `core/dashboard/**` moves to `dashboard/jvm/**`, preserving sub-package structure:

- `core/dashboard/*.scala` → `dashboard/jvm/src/*.scala` (top-level services: `CaskServer.scala`, `DashboardService.scala`, `StateRepository.scala`, `ArtifactService.scala`, `GitStatusService.scala`, `IssueCacheService.scala`, `IssueSearchResult.scala`, `IssueSearchService.scala`, `PathValidator.scala`, `ProjectRegistrationService.scala`, `PullRequestCacheService.scala`, `RefreshThrottle.scala`, `ReviewStateService.scala`, `ServerStateService.scala`, `WorkflowProgressService.scala`, `WorktreeCardService.scala`, `WorktreeListSync.scala`, `WorktreeListView.scala`, `WorktreeRegistrationService.scala`, `WorktreeUnregistrationService.scala`).
- `core/dashboard/application/**` → `dashboard/jvm/src/application/**`.
- `core/dashboard/domain/**` → `dashboard/jvm/src/domain/**`.
- `core/dashboard/infrastructure/**` → `dashboard/jvm/src/infrastructure/**`.
- `core/dashboard/presentation/**` → `dashboard/jvm/src/presentation/**`.
- `core/dashboard/resources/**` → `dashboard/jvm/resources/**` (classpath resources served by Cask; sits on Mill's default `resources` path for the module, matching the layout `def resources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "resources")` sets up in `build.mill`).

Use `git mv` so the rename history survives. Do not reorganise files during the move — reviewers focus on rename correctness, and any non-mechanical reshuffle makes that impossible to verify by diff.

Asymmetry with `core`: the core module enumerates subdirs in `sources` to exclude `test/` (which lives inside `core/`). The new `dashboard` module can safely use a single `dashboard/jvm/src` include because its tests live at the sibling path `dashboard/jvm/test/src`, outside the `src` tree. No subdir enumeration needed.

### Package rename

Mechanical pass across every moved source file, every migrated test file, and every external reference. Enumerated paths (Phase 1's preference):

```
rg -l 'iw\.core\.dashboard' dashboard/ core/test/ commands/ core/adapters/ProcessManager.scala core/CLAUDE.md \
  | xargs sed -i 's/iw\.core\.dashboard/iw.dashboard/g'
```

Touches `package iw.core.dashboard...` declarations (including `commands/server-daemon.scala`'s own `package` line), every `import iw.core.dashboard...` line, the `ProcessManager.scala:115` string literal, and the `core/CLAUDE.md` guidance line. Post-rename invariant (blocking), repo-wide safety net excluding the historical record:

```
rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'
```

must return zero results. If any hit remains, implementation is not done — audit it before running tests.

### `build.mill` delta

On the existing `core` module:
- Remove the three dashboard-only `mvnDeps` entries (`cask`, `scalatags`, `flexmark`) and the comment that flagged them as in-transit.
- Remove `moduleDir / os.up / "core" / "dashboard",` from the `sources` enumeration. Final `sources` list: `adapters`, `model`, `output`, plus root `IssueCreateParser.scala`.

New `dashboard` module alongside `core`:

```
object dashboard extends ScalaModule {
  def scalaVersion = "3.3.7"
  def moduleDeps = Seq(core)
  def sources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "src")
  def resources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "resources")
  def mvnDeps = Seq(
    mvn"com.lihaoyi::cask:0.11.3",
    mvn"com.lihaoyi::scalatags:0.13.1",
    mvn"works.iterative::scalatags-webawesome:3.2.1.1",
    mvn"com.vladsch.flexmark:flexmark-all:0.64.8",
  )

  object test extends ScalaTests with TestModule.Munit {
    def sources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "test" / "src")
    def mvnDeps = Seq(mvn"org.scalameta::munit::1.2.1")
  }
}
```

The explicit `def resources` override is required because Cask serves static assets from the classpath; the resource tree lives at `dashboard/jvm/resources/` and must be copied to the classpath root by Mill's resource pipeline. Exact Mill 1.1.x shape (`Task.Sources`, `mvnDeps`, `moduleDir / os.up / ...`) mirrors Phase 1's `core` module. Fix forward on first `./mill dashboard.compile` if any API detail drifts.

### `core/project.scala` delta — Option A (transitional bridge)

Drop three lines from `core/project.scala`:

```
//> using dep com.lihaoyi::cask:0.11.3
//> using dep com.lihaoyi::scalatags:0.13.1
//> using dep com.vladsch.flexmark:flexmark-all:0.64.8
```

The SYNC comment remains. After this edit, scala-cli's runtime classpath for command scripts no longer resolves dashboard deps project-wide.

The two bridge scripts that still import `iw.dashboard.*` types during the transition — `commands/dashboard.scala` and `commands/server-daemon.scala` — each gain scoped `//> using dep` lines for `cask 0.11.3`, `scalatags 0.13.1`, `flexmark-all 0.64.8`. This is the "transitional bridge": the deps live at the exact scripts that need them, no broader. The bridge disappears in Phase 4 when those scripts are rewritten to spawn `java -jar build/iw-dashboard.jar` and stop importing dashboard types entirely; at that point the six `//> using dep` lines are deleted alongside the launcher rewrite.

### Test migration

Discovery command (run first):

```
rg 'iw\.core\.dashboard' core/test/ -l
```

Every hit is a candidate. Classifier (mechanical, not by headcount):

- A test is **dashboard-touching** (migrates to `dashboard/jvm/test/src/`) if it imports any symbol from `iw.dashboard.{application,domain,infrastructure,presentation}` OR any of the top-level `iw.dashboard.*` services listed under Directory move (`CaskServer`, `DashboardService`, `StateRepository`, `ArtifactService`, `GitStatusService`, `IssueCacheService`, `IssueSearchResult`, `IssueSearchService`, `PathValidator`, `ProjectRegistrationService`, `PullRequestCacheService`, `RefreshThrottle`, `ReviewStateService`, `ServerStateService`, `WorkflowProgressService`, `WorktreeCardService`, `WorktreeListSync`, `WorktreeListView`, `WorktreeRegistrationService`, `WorktreeUnregistrationService`).
- Tests that only reference a dashboard constant via fully-qualified path or via a fixture helper **stay** in `core/test/` with their import updated to `iw.dashboard.*`.

Expected partition against the ~48 grep hits: ~20 migrate, the remainder stay — but the criterion is mechanical, not a headcount.

Test imports update via the same `sed` pass as the source rename. Any `TestFixtures.scala` or shared test utility referenced by both migrated and stayed tests needs careful handling: if it stays in `core/test/`, the migrated tests can't see it from `dashboard/jvm/test/src/`. Options: (a) duplicate the minimal fixture into `dashboard/jvm/test/src/`, (b) move the fixture to the migrated side if nothing in `core/test/` depends on it, (c) split the fixture. Pick whichever minimises churn — inspect at implementation time.

### External-reference updates (transitional)

Four references outside the moved tree update to keep compiling during the transition. Full rewrite is reserved for Phase 4.

- **`commands/dashboard.scala`** — imports of `iw.core.dashboard.CaskServer`, `iw.core.dashboard.StateRepository`, `iw.core.dashboard.SampleDataGenerator` (and whatever else it references) change to `iw.dashboard.*`. Adds the three scoped `//> using dep` lines for `cask`, `scalatags`, `flexmark` at the top. No logic changes.
- **`commands/server-daemon.scala`** — `package iw.core.dashboard` declaration becomes `package iw.dashboard`. The script relies on same-package visibility to reach `CaskServer.start` (line 17), so the package line is what's load-bearing, not imports. Adds the three scoped `//> using dep` lines. No logic changes.
- **`core/adapters/ProcessManager.scala:115`** — the string literal `"iw.core.dashboard.ServerDaemon"` (a `--main-class` argument passed to scala-cli) becomes `"iw.dashboard.ServerDaemon"`. One-string-edit, no surrounding logic touched.
- **`core/CLAUDE.md:50`** — the guidance comment `// Do NOT import iw.core.dashboard.*` updates to `// Do NOT import iw.dashboard.*`.

### `./iw ./test` orchestration

`.iw/commands/test.scala` currently runs scala-cli munit against `core/test/`. After Phase 2:

- `./iw ./test unit` runs (a) scala-cli munit against remaining `core/test/` AND (b) `./mill dashboard.test`. Both must pass for the stage to succeed.
- `./iw ./test e2e` runs the BATS suite unchanged.
- `./iw ./test` with no arg runs unit + e2e unchanged.

The Mill test invocation goes through the existing `./mill` wrapper; no new launcher plumbing needed.

## Files to modify or create

### New files
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/**` — every `.scala` file moved from `core/dashboard/**`, preserving sub-package structure. Absolute paths for top-level services: `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/CaskServer.scala`, `.../DashboardService.scala`, `.../StateRepository.scala`, `.../ArtifactService.scala`, `.../GitStatusService.scala`, etc. Sub-packages at `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/{application,domain,infrastructure,presentation}/**`.
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/resources/**` — every resource moved from `core/dashboard/resources/**` (Cask static assets).
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/test/src/**` — the migrated dashboard-touching test files.

### Moved files
- `/home/mph/Devel/iw/iw-cli-IW-345/core/dashboard/*.scala` and sub-packages → `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/**` (via `git mv`).
- `/home/mph/Devel/iw/iw-cli-IW-345/core/dashboard/resources/**` → `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/resources/**` (via `git mv`).
- Subset of `/home/mph/Devel/iw/iw-cli-IW-345/core/test/*.scala` → `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/test/src/*.scala` (precise list discovered by the classifier in Test migration).

### Renamed packages
- `iw.core.dashboard.*` → `iw.dashboard.*` across every moved source and test file, every migrated test, plus the four external references listed below. Post-rename invariant: `rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'` returns zero results.

### Modified files
- `/home/mph/Devel/iw/iw-cli-IW-345/build.mill` — `core.mvnDeps` drops cask/scalatags/flexmark; `core.sources` drops the `dashboard` entry; new `object dashboard extends ScalaModule` with its own mvnDeps, `moduleDeps = Seq(core)`, explicit `def resources` override, and inner `object test extends ScalaTests with TestModule.Munit`.
- `/home/mph/Devel/iw/iw-cli-IW-345/core/project.scala` — removes the three `//> using dep` lines for cask, scalatags, flexmark.
- `/home/mph/Devel/iw/iw-cli-IW-345/commands/dashboard.scala` — imports flip to `iw.dashboard.*`; adds scoped `//> using dep` lines for cask, scalatags, flexmark.
- `/home/mph/Devel/iw/iw-cli-IW-345/commands/server-daemon.scala` — `package` declaration flips from `iw.core.dashboard` to `iw.dashboard`; adds scoped `//> using dep` lines for cask, scalatags, flexmark.
- `/home/mph/Devel/iw/iw-cli-IW-345/core/adapters/ProcessManager.scala` — string literal at line 115 updated from `"iw.core.dashboard.ServerDaemon"` to `"iw.dashboard.ServerDaemon"`.
- `/home/mph/Devel/iw/iw-cli-IW-345/core/CLAUDE.md` — guidance line updated from `iw.core.dashboard.*` to `iw.dashboard.*`.
- `/home/mph/Devel/iw/iw-cli-IW-345/.iw/commands/test.scala` — unit stage also invokes `./mill dashboard.test`.

## Testing strategy

- **Unit level:** no new Scala unit tests added — this phase is pure relocation. The test-level validation is that the migrated tests and the remaining core tests all still pass.
- **Regression — core tests (scala-cli):** `scala-cli test core/` runs the tests that stayed; all must pass. These no longer have `cask`/`scalatags`/`flexmark` on the classpath (removed from `core/project.scala`), so any test that incidentally relied on them must have been migrated or its import updated.
- **Regression — dashboard tests (Mill):** `./mill dashboard.test` runs the migrated tests; all must pass. Munit version must match what core uses (`1.2.1`) so behaviour is identical.
- **Compile verification:** `./mill core.compile`, `./mill dashboard.compile`, and `./mill iwCoreJar` all succeed. `scala-cli compile commands/` also succeeds (the external references keep compiling via minimal import/package updates + scoped per-script deps).
- **Grep invariants (blocking):**
  - `rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'` — zero results.
  - `rg 'iw\.core\.dashboard' core/test/` — zero results (migration complete).
  - `rg 'cask|scalatags|flexmark' core/project.scala` — zero results.
  - `rg '^\s*mvn"com\.lihaoyi::cask' build.mill` and the equivalent for `scalatags`/`flexmark` — zero results under the `core` module's `mvnDeps` block (they now live only under `dashboard.mvnDeps`).
- **From-clean-clone:** with `out/` and `build/` wiped, `./mill dashboard.test` compiles core and dashboard via the Mill graph and runs the migrated tests green.
- **Smoke — dashboard launch:** launch the in-process scala-cli path against a throwaway state path, hit the home page, tear down cleanly:
  ```
  IW_SERVER_DISABLED=0 ./iw dashboard --state-path /tmp/iw-phase02-smoke &
  PID=$!; sleep 2
  curl -s http://localhost:<port>/ | grep -q '<html'
  kill $PID
  ```
  If an existing BATS test in `test/` already exercises `./iw dashboard` end-to-end, reference and run that test by name instead of this ad-hoc script. The check confirms the import/dep flips didn't break the transitional bridge.
- **Pre-push hook:** unchanged hook (format + scalafix + `-Werror` compile + unit tests + command compilation) passes, where unit tests now run under both scala-cli AND Mill per the orchestration update.

## Acceptance criteria

- [ ] `./mill dashboard.compile` succeeds from a clean tree.
- [ ] `./mill dashboard.test` runs the migrated unit tests; all pass.
- [ ] `./mill iwCoreJar` still succeeds (core compiles cleanly after dashboard extraction and without the three dashboard-only deps).
- [ ] `rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'` returns zero results.
- [ ] `rg 'iw\.core\.dashboard' core/test/` returns zero results (all dashboard-touching tests migrated).
- [ ] `core/project.scala` no longer lists `cask`, `scalatags`, or `flexmark`.
- [ ] `commands/dashboard.scala` and `commands/server-daemon.scala` each declare scoped `//> using dep` lines for `cask 0.11.3`, `scalatags 0.13.1`, `flexmark-all 0.64.8`.
- [ ] `build.mill`'s `core.mvnDeps` no longer lists `cask`, `scalatags`, or `flexmark`; `core.sources` no longer enumerates `dashboard`.
- [ ] `build.mill`'s new `dashboard` module declares `moduleDeps = Seq(core)`, its own `mvnDeps` (cask, scalatags, scalatags-webawesome 3.2.1.1, flexmark-all), an explicit `def resources` pointing at `dashboard/jvm/resources`, and an inner `object test extends ScalaTests with TestModule.Munit`.
- [ ] `scala-cli compile commands/` still succeeds — the four external references (`commands/dashboard.scala`, `commands/server-daemon.scala`, `core/adapters/ProcessManager.scala:115`, `core/CLAUDE.md`) keep compiling via minimal import/package/string updates.
- [ ] Migrated dashboard tests run under Mill and pass; remaining core tests still run under scala-cli and pass. Total test count unchanged from Phase 1's baseline of 141.
- [ ] `./iw ./test unit` runs both scala-cli's core tests and `./mill dashboard.test`; both green for the stage to succeed.
- [ ] Pre-push hook (format + scalafix + `-Werror` compile + unit tests + command compilation) passes end to end.
- [ ] Smoke check passes: launch the in-process dashboard against `/tmp/iw-phase02-smoke`, `curl -s http://localhost:<port>/ | grep -q '<html'` succeeds, teardown is clean. If a named BATS test already covers this path, run it in place of the ad-hoc script.

## Risks

- **Three-way alignment of the `ServerDaemon` FQCN.** `commands/server-daemon.scala`'s `package` declaration, the actual `iw.dashboard.ServerDaemon` class, and `ProcessManager.scala:115`'s `--main-class "iw.dashboard.ServerDaemon"` string literal must all agree. A drift between any two compiles cleanly but fails at runtime with `ClassNotFoundException`. Keep these three edits in the same commit and re-verify after the `sed` pass.
- **Stale `iw.core.dashboard` references surviving the rename.** A single forgotten import compiles against nothing and breaks at runtime as `ClassNotFoundException`. Mitigation: the blocking grep invariant `rg 'iw\.core\.dashboard' --type-add 'md:*.md' -t scala -t md -g '!project-management/'` must return zero before acceptance; run it as the last step of the rename pass, not after test runs.
- **Test-module classpath gaps.** `dashboard.test`'s `mvnDeps` must include `munit` (`org.scalameta::munit::1.2.1`) and any transitive test-utility deps. If a migrated test imports from a shared fixture that stayed in `core/test/`, the Mill test module won't see it — the fixture must be moved or duplicated (see Test migration section). Symptom: `./mill dashboard.test` compile errors referencing unresolved types from `TestFixtures` or similar.
- **Dashboard code that touches non-dashboard core types.** `moduleDeps = Seq(core)` gives the dashboard module access to everything `iw.core.*` — which is correct for Phase 2. Risk is accidental widening: if a dashboard file starts reaching into types it shouldn't, the module boundary drifts. Keep the diff strictly mechanical; no signature or import changes beyond the rename.
- **Scala-cli-vs-Mill dep drift.** `build.mill`'s `dashboard.mvnDeps` and the scoped `//> using dep` lines in `commands/dashboard.scala`/`commands/server-daemon.scala` both claim to supply the same three deps (`cask 0.11.3`, `scalatags 0.13.1`, `flexmark-all 0.64.8`). If any version string drifts, the Mill-built module and the scala-cli-launched bridge disagree on dashboard behaviour. Cross-check version strings line by line before committing.
- **Test-runner divergence.** Munit version in `dashboard.test.mvnDeps` must match the version scala-cli resolves for `core/test/` (currently `1.2.1`). If they drift, behaviour (e.g. `.orFail`/`.assertNoDiff` output) can differ between runners and a test that passes under one fails under the other.
- **Transitional `//> using dep` lines in command scripts.** The scoped deps on `commands/dashboard.scala` and `commands/server-daemon.scala` are deliberate tech debt (Option A). They are earmarked for deletion in Phase 4 alongside the launcher rewrite, at which point both scripts stop importing `iw.dashboard.*` types entirely. Keeping the bridge narrow (two scripts only, not `core/project.scala`) makes the Phase 4 cleanup a pure deletion.
- **Mill `def resources` misconfigured.** If the resource path is wrong or omitted, Cask serves 404s for every static asset (CSS/JS/images) at runtime even though compile and tests pass. Mitigation: the smoke check (`curl -s http://localhost:<port>/ | grep -q '<html'`) plus spot-check of a static asset URL catches this before acceptance.
- **BATS tests that exercise `iw dashboard` / `iw server-daemon`.** They launch the scala-cli in-process path, which still exists and now resolves `iw.dashboard.*` types. They should pass unchanged. If any fails, the root cause is almost certainly a missed import, a missed `package` line, or a classpath dep mismatch, not a BATS-level issue.

## Notes for reviewer

Phase 2 is intentionally invisible at runtime: `./iw dashboard` still spawns from the in-process scala-cli path (transitional bridge), and the package rename is mechanical. This is the biggest diff size in the entire feature — ~89 source files + migrated test files move, and every one of them has its `package` and `import` lines rewritten. Reviewer attention should focus on:

1. **Rename correctness.** Spot-check a handful of moved files to verify `package iw.dashboard...` and no lingering `iw.core.dashboard` imports.
2. **Three-way FQCN alignment.** `commands/server-daemon.scala`'s `package`, the actual `ServerDaemon` class path, and `ProcessManager.scala:115`'s string literal all say `iw.dashboard`.
3. **Dep-list alignment.** Cross-reference `build.mill`'s `dashboard.mvnDeps` against the scoped `//> using dep` lines on the two bridge scripts — identical versions, no drift. Confirm `core/project.scala` no longer carries the three deps.
4. **The four external-reference edits** (`commands/dashboard.scala`, `commands/server-daemon.scala`, `core/adapters/ProcessManager.scala:115`, `core/CLAUDE.md`) are the only places where a human-readable diff reveals intent beyond the mechanical rename. Confirm they are import/package/string updates only — no logic changes.
5. **Test partition.** `rg 'iw\.core\.dashboard' core/test/` returns zero; `rg 'iw\.dashboard' dashboard/jvm/test/src/` returns positive hits; no test file appears in both locations. Migrated set matches the classifier (imports an `iw.dashboard.{application,domain,infrastructure,presentation}` symbol or a top-level dashboard service).

There is no new logic in this phase. Any commentary on architecture, behaviour, or design patterns belongs to Phase 3 (assembly + itest) or Phase 4 (launcher rewrite), not here.
