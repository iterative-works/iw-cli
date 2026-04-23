---
generated_from: c1386dfcc48bf6cca0418fa604ee95fbea7209bb
generated_at: 2026-04-23T22:48:08Z
branch: IW-345
issue_id: IW-345
phase: "1-4 (complete)"
files_analyzed:
  - build.mill
  - iw-run
  - commands/dashboard.scala
  - core/adapters/ProcessManager.scala
  - dashboard/jvm/src/CaskServer.scala
  - dashboard/jvm/src/ServerDaemon.scala
  - dashboard/jvm/src/DevModeConfig.scala
  - dashboard/jvm/src/presentation/views/AssetUrl.scala
  - dashboard/jvm/src/SampleDataCli.scala
  - dashboard/jvm/itest/src/CaskServerItest.scala
  - dashboard/frontend/vite.config.js
  - dashboard/frontend/src/main.js
  - dashboard/frontend/src/main.css
  - .github/Dockerfile.ci
  - .github/workflows/ci.yml
  - .iw/commands/test.scala
---

# Review Packet: IW-345 — Mill module for dashboard with Vite + Tailwind + Web Awesome

## Goals

This branch introduces Mill as a second build tool and migrates the dashboard from
an in-process scala-cli dependency into a standalone fat jar with a modern frontend
pipeline. The result is `dashboard.assembly` — a self-contained JVM process with
Vite-bundled assets baked in — replacing the previous ad-hoc classpath loading.

Key objectives:

- Establish Mill 1.1.5 alongside scala-cli; Mill owns the dashboard, scala-cli keeps
  owning `core/` and command scripts.
- Extract the dashboard Scala sources from `core/dashboard/` into a proper Mill module
  at `dashboard/jvm/`, with the package rename `iw.core.dashboard.*` → `iw.dashboard.*`.
- Introduce a Vite 8 + Tailwind v4 + Web Awesome Pro 3.2.1 frontend pipeline that
  produces bundled CSS/JS baked into the fat jar's classpath resources.
- Replace `CaskServer.start` called in-process from scala-cli with `java -jar
  build/iw-dashboard.jar`, transparently triggered by `iw-run`'s rebuild gate.
- Provide a safety-gated dev mode: Vite HMR routing activates only when BOTH `--dev`
  flag AND `VITE_DEV_URL` env var are present, with loopback-host validation.

This is infrastructure work — the user-visible CLI surface (`iw dashboard`,
`iw server`) is preserved, and no feature is added or removed.

Per-phase details live in the phase context files:
[phase-01-context.md](phase-01-context.md) |
[phase-02-context.md](phase-02-context.md) |
[phase-03-context.md](phase-03-context.md) |
[phase-04-context.md](phase-04-context.md)

---

## Scenarios

These are the acceptance scenarios covering the full feature:

**Build infrastructure**
- [ ] `./mill core.jar` produces a core-only thin jar consumed by scala-cli commands
- [ ] `./mill dashboard.assembly` produces a fat jar with `Main-Class: iw.dashboard.ServerDaemon` and embedded Vite assets under `/assets/`
- [ ] `./mill frontend.viteBuild` requires `WEBAWESOME_NPM_TOKEN`; fails fast and clearly without it
- [ ] The Mill-built core jar passes all 193 existing unit tests

**Package relocation**
- [ ] `rg 'iw\.core\.dashboard' --type scala` returns zero results anywhere in the repo
- [ ] `rg 'iw\.dashboard' commands/` returns zero results (commands no longer import dashboard types)

**Launcher behaviour**
- [ ] `./iw dashboard` starts the server via `java -jar` (not in-process scala-cli)
- [ ] `./iw server start` works and also triggers `ensure_dashboard_jar`
- [ ] `./iw status` and other non-dashboard commands do NOT trigger a dashboard jar rebuild
- [ ] Touching a dashboard source triggers a Mill rebuild on next `./iw dashboard` invocation
- [ ] Sample data generation (`--sample-data`) still works, now via `SampleDataCli`

**Dev mode safety**
- [ ] `./iw dashboard --dev` without `VITE_DEV_URL` set: starts in prod mode with a warning
- [ ] `VITE_DEV_URL=http://localhost:5173 ./iw dashboard` (no `--dev`): prod mode, no dev routing
- [ ] `VITE_DEV_URL=http://localhost:5173 ./iw dashboard --dev`: logs warning, HTML references Vite URL
- [ ] `VITE_DEV_URL=https://localhost:5173 ./iw dashboard --dev`: refuses with scheme-must-be-http error
- [ ] `VITE_DEV_URL=http://example.com ./iw dashboard --dev`: refuses with loopback-host error

**Assets**
- [ ] `GET /static/dashboard.css` and `GET /static/dashboard.js` return 200 from classpath
- [ ] `GET /assets/main.js` returns 200 (Vite bundle embedded in jar)
- [ ] Path traversal on both `/static/` and `/assets/` routes is rejected

**CI**
- [ ] New `dashboard-build` job in CI runs `./mill dashboard.assembly` and `./mill dashboard.itest.test`

---

## Entry Points

| File | Symbol | Why Start Here |
|------|--------|----------------|
| `iw-run` | `mill_jar_path()`, `ensure_core_jar()`, `ensure_dashboard_jar()` | Shell-side rebuild gate — the protocol boundary between shell and Mill |
| `build.mill` | `object core`, `object dashboard`, `object frontend` | Complete picture of the three-module build graph |
| `dashboard/jvm/src/ServerDaemon.scala` | `ServerDaemon.main` | New `java -jar` entry point; parses CLI args, delegates to `CaskServer.start` |
| `dashboard/jvm/src/DevModeConfig.scala` | `DevModeConfig.resolve` | Double-gate logic; validates scheme + loopback host; pure function |
| `dashboard/jvm/src/CaskServer.scala` | `CaskServer.start`, `serveClasspathResource` | Reads env, resolves `DevModeConfig`, owns asset serving routes for both `/static/` and `/assets/` |
| `dashboard/jvm/src/presentation/views/AssetUrl.scala` | `AssetUrl.apply`, `AssetContext` | Single prod/dev URL switch consumed by every HTML template |
| `commands/dashboard.scala` | `startServerAndOpenBrowser` | Scala-cli command that now spawns `java -jar "$IW_DASHBOARD_JAR"` |
| `core/adapters/ProcessManager.scala` | `spawnServerProcess` | Background daemon launch; old scala-cli indirection replaced with `java -jar` |
| `dashboard/frontend/vite.config.js` | — | Vite entry config; defines `src/main.js` entry and `dist/assets/` output |
| `dashboard/jvm/itest/src/CaskServerItest.scala` | — | Integration tests; exercises full jar (asset resolution, path traversal, dev/prod rendering) |

---

## Diagrams

### Build graph

```
scala-cli commands          Mill modules
─────────────────           ─────────────
core/project.scala  <─SYNC─ build.mill
 //> using dep               object core
                              └─ sources: core/{adapters,model,output}
                              └─ mvnDeps: config, sttp, upickle, os-lib, flexmark
                              └─ core.jar ◄─── iw-run queries via ./mill show
                             object dashboard
                              └─ moduleDeps = Seq(core)
                              └─ mvnDeps: cask, scalatags, scalatags-webawesome
                              └─ resources = jvmResources ++ frontend.viteBuild()
                              └─ dashboard.assembly ◄── iw-run queries via ./mill show
                             object frontend
                              └─ viteBuild: yarn install --immutable && yarn build
                              └─ output consumed by dashboard.resources
```

### Runtime launch flow

```
./iw dashboard
     │
     ▼
iw-run dispatch
     │
     ├─ ensure_core_jar()
     │    └─ ./mill show core.jar → CORE_JAR path → export IW_CORE_JAR
     │
     ├─ ensure_dashboard_jar()
     │    └─ ./mill show dashboard.assembly → fat jar path → export IW_DASHBOARD_JAR
     │
     └─ scala-cli run commands/dashboard.scala
          │
          ├─ (optional) java -cp $IW_DASHBOARD_JAR iw.dashboard.SampleDataCli <statePath>
          │
          └─ java -jar $IW_DASHBOARD_JAR <statePath> <port> <hosts> [--dev]
                   │
                   └─ ServerDaemon.main
                        └─ CaskServer.start(statePath, port, hosts, devMode, viteDevUrl)
                              │
                              ├─ DevModeConfig.resolve(devFlag, viteDevUrl)
                              │    ├─ validate scheme == http
                              │    └─ validate host in {localhost, 127.0.0.1}
                              │
                              └─ new CaskServer(statePath, port, hosts, startedAt, devModeConfig)
                                   └─ assetContext = AssetContext(devModeConfig)
                                        └─ AssetUrl("main.js", ctx) → /assets/main.js | <VITE_URL>/src/main.js
```

### Module structure (Phase 2 relocation)

```
Before                          After
──────                          ─────
core/                           core/
  dashboard/                      adapters/
    *.scala       ──git mv──►     model/
    application/                  output/
    domain/                       IssueCreateParser.scala
    infrastructure/
    presentation/               dashboard/
    resources/                    jvm/
  test/                             src/        ← iw.dashboard.*
    *Test.scala   ──git mv──►         application/
                                      domain/
                                      infrastructure/
                                      presentation/views/
                                    test/src/   ← mill dashboard.test
                                    itest/src/  ← mill dashboard.itest
                                    resources/  ← static assets
                                  frontend/
                                    src/        ← Vite entry
                                    package.json, yarn.lock, vite.config.js
```

---

## Test Summary

### Unit tests (Mill — `./mill dashboard.test`)

48 test files in `dashboard/jvm/test/src/`. 193 tests pass. Key new files:

| File | Type | Coverage |
|------|------|----------|
| `DevModeConfigTest.scala` | Unit | All branches of `DevModeConfig.resolve` incl. IPv6 rejection, substring-attack |
| `AssetUrlTest.scala` | Unit | Prod/dev URL output, trailing-slash, subdirectory paths |
| `CaskServerTest.scala` | Unit | `CaskServer` constructor + route registration |
| `DashboardServiceTest.scala` | Unit | `renderDashboard` with `AssetContext` |
| `PageLayoutTest.scala` | Unit | `PageLayout.render` with both `AssetContext` modes |
| `StaticFilesTest.scala` | Unit | Classpath resource presence |

All existing dashboard-touching tests (migrated from `core/test/` in Phase 2) continue to pass under Mill.

### Integration tests (Mill — `./mill dashboard.itest.testForked`)

1 file: `dashboard/jvm/itest/src/CaskServerItest.scala` — 15 tests covering:
- Home page renders and references `/assets/main.js` (prod) / Vite URL (dev)
- `/static/dashboard.css` + `/static/dashboard.js` return 200 from classpath
- `/assets/main.js` returns 200 from embedded Vite bundle
- 404 handling for unknown paths under both prefixes
- Path traversal rejection on `/static/` and `/assets/` (raw `HttpURLConnection` preserving `%2F`)
- Jar manifest has `Main-Class: iw.dashboard.ServerDaemon`
- Required zip entries present (`assets/main.js`, `assets/main.css`, `static/dashboard.css`)

### E2E tests (BATS)

3 new BATS files in `test/`:

| File | Scenarios |
|------|-----------|
| `test/dashboard-dev-gate.bats` | Loopback-only + http-only refusals; pre-builds jar in `setup_file` |
| `test/dashboard-jar-launch.bats` | `--help` CLI smoke test verifying `java -jar` path |
| `test/dashboard-rebuild-gate.bats` | `ensure_dashboard_jar` fires on dashboard commands, not others |

---

## Files Changed

124 files changed across 4 phases. Grouped by concern:

<details>
<summary>Build infrastructure (Phase 1)</summary>

| File | Change |
|------|--------|
| `build.mill` | New — Mill build definition (core + dashboard + frontend modules) |
| `mill` | New — vendored Mill launcher wrapper |
| `.mill-version` | New — pins Mill 1.1.5 |
| `iw-run` | Modified — `build_core_jar()` → `mill_jar_path()`; new `ensure_dashboard_jar()` |
| `.github/Dockerfile.ci` | Modified — Node 20 + Corepack + Mill 1.1.5 pre-installed |
| `.gitignore` | Modified — added `out/` |
| `.scalafmt.conf` | Modified — excludes `out/` from format scans |
| `core/project.scala` | Modified — SYNC comment; dashboard deps removed in Phase 2 |

</details>

<details>
<summary>Dashboard module extraction (Phase 2)</summary>

41 Scala source files moved from `core/dashboard/**` → `dashboard/jvm/src/**` via `git mv`.
47 test files moved from `core/test/*.scala` → `dashboard/jvm/test/src/*.scala` via `git mv`.
Package rename `iw.core.dashboard.*` → `iw.dashboard.*` across all moved files + 3 external references.

Notable non-move changes:

| File | Change |
|------|--------|
| `build.mill` | Added `object dashboard extends ScalaModule` with `moduleDeps = Seq(core)` |
| `commands/dashboard.scala` | Imports updated; transitional `//> using dep` lines added (removed Phase 4) |
| `core/adapters/ProcessManager.scala` | FQCN string literal updated |
| `.iw/commands/test.scala` | `unit` stage now also runs `./mill dashboard.test` |

</details>

<details>
<summary>Frontend pipeline + fat jar (Phase 3)</summary>

| File | Change |
|------|--------|
| `dashboard/frontend/package.json` | New — Yarn 4.9.2, Vite 8, Tailwind v4, Web Awesome Pro 3.2.1 |
| `dashboard/frontend/yarn.lock` | New — committed for reproducibility |
| `dashboard/frontend/.yarnrc.yml` | New — Web Awesome Pro registry auth via `WEBAWESOME_NPM_TOKEN` |
| `dashboard/frontend/vite.config.js` | New — entry `src/main.js`, output `dist/assets/`, `cors: true` |
| `dashboard/frontend/src/main.js` | New — 9 cherry-picked Web Awesome components |
| `dashboard/frontend/src/main.css` | New — `@import "tailwindcss"` + `@source` scanning Scala templates |
| `dashboard/jvm/itest/src/CaskServerItest.scala` | New — 15 integration tests |
| `dashboard/jvm/src/CaskServer.scala` | Modified — classpath asset serving (`serveClasspathResource`); `/assets/` route |
| `build.mill` | Modified — `object frontend` module; `dashboard.resources` override; `mainClass` |
| `.github/workflows/ci.yml` | Modified — new `dashboard-build` job |

</details>

<details>
<summary>Command integration + dev mode (Phase 4)</summary>

| File | Change |
|------|--------|
| `dashboard/jvm/src/DevModeConfig.scala` | New — pure double-gate resolver with URI validation |
| `dashboard/jvm/src/ServerDaemon.scala` | New — `java -jar` main entry point |
| `dashboard/jvm/src/SampleDataCli.scala` | New — separate main for sample-data seeding |
| `dashboard/jvm/src/presentation/views/AssetUrl.scala` | New — prod/dev URL helper |
| `dashboard/jvm/test/src/DevModeConfigTest.scala` | New — 12 unit test cases |
| `dashboard/jvm/test/src/AssetUrlTest.scala` | New — 5 unit test cases |
| `commands/dashboard.scala` | Modified — `java -jar` spawn; all `iw.dashboard.*` imports removed |
| `commands/server-daemon.scala` | Deleted — no callers after `ProcessManager` flip |
| `core/adapters/ProcessManager.scala` | Modified — `spawnServerProcess` now `java -jar` |
| `iw-run` | Modified — `mill_jar_path()` helper; mtime-scan gate retired |
| `build.mill` | Modified — `iwCoreJar`/`iwDashboardJar` tasks deleted; `forkEnv` for tests |
| `test/dashboard-dev-gate.bats` | New |
| `test/dashboard-jar-launch.bats` | New |
| `test/dashboard-rebuild-gate.bats` | New |
| `CLAUDE.md` | Modified — two-build-tool boundary; dashboard dev workflow |
| `README.md` | Modified — dashboard contributor prerequisites |
| `dashboard/frontend/start-dev.sh` | New — Vite dev server convenience wrapper |

</details>

---

## Notable Implementation Decisions

Three decisions deviated from the analysis plan and are worth reviewer attention:

**`iwCoreJar` became a combined core+dashboard jar (Phase 2 bridge, now retired)**
The analysis planned a pure thin core jar for scala-cli commands. In Phase 2, the
scala-cli bridge scripts still needed `iw.dashboard.*` on the classpath, so `iwCoreJar`
was temporarily widened to include dashboard classes. Phase 4 retired both
`iwCoreJar` and `iwDashboardJar` entirely — `iw-run` now queries Mill directly via
`./mill show core.jar` and `./mill show dashboard.assembly`. The staging `build/*.jar`
paths are gone; jars live in Mill's `out/` directory.

**Flexmark stayed in `core.mvnDeps`**
The plan marked flexmark as a dashboard-only dep to be moved. `core/output/MarkdownRenderer.scala`
imports it directly, so it remains in `core`. It is NOT in `dashboard.mvnDeps` (available
transitively via `moduleDeps = Seq(core)`).

**Path-traversal test uses raw `HttpURLConnection`**
sttp normalises `%2F` in URIs, which meant traversal tests could pass via "route not matched"
rather than exercising the guard. The integration tests switched to `java.net.URI.toURL` +
`HttpURLConnection` to preserve `%2F` on the wire.

---

## Key Invariants to Verify

- `rg 'iw\.core\.dashboard' --type scala` → zero results
- `rg 'iw\.dashboard' commands/` → zero results (commands no longer import dashboard types)
- `unzip -l "$CORE_JAR" | grep 'iw/dashboard/'` → zero entries (core jar is core-only)
- `unzip -l "$DASHBOARD_JAR" | grep 'assets/main.js'` → present (Vite bundle embedded)
- `VITE_DEV_URL=http://localhost.example.com ./iw dashboard --dev` → refused (substring attack)
- `VITE_DEV_URL=http://[::1]:5173 ./iw dashboard --dev` → refused (IPv6 excluded)
