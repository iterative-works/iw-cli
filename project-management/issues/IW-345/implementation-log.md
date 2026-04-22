# Implementation Log: IW-345 — Layer 0: Set up Mill module for dashboard with Vite + Tailwind + Web Awesome

Issue: IW-345

This log tracks the evolution of implementation across phases.

---

## Phase 1: Mill bootstrap + core jar production (2026-04-21)

**Layer:** L0 — Build infrastructure

**What was built:**
- `build.mill` — Mill 1.1.5 build definition; `object core extends ScalaModule` (Scala 3.3.7, explicit `core/` subdir sources, `mvnDeps` mirroring `core/project.scala` including dashboard-only deps); top-level `iwCoreJar()` as `Task.Command` that copies the compiled jar to `build/iw-core.jar`.
- `mill` — vendored canonical Mill launcher (bash wrapper) that reads `.mill-version`, caches the downloaded binary under `~/.cache/mill/`, and exec's with args passed through. PURPOSE header added.
- `.mill-version` — pins Mill to `1.1.5`.
- `.github/deps/mill` (gitignored) — Mill 1.1.5 native-linux-amd64 binary (~59MB), consumed by Dockerfile.ci `COPY deps/mill /usr/local/bin/mill`.
- `.github/deps/node.tar.xz` (gitignored) — Node 20.20.2 linux-x64 tarball (~25MB), consumed by Dockerfile.ci for the future dashboard frontend build.

**Modified:**
- `iw-run` — `build_core_jar()` body replaced with `(cd "$INSTALL_DIR" && ./mill iwCoreJar)`; `ensure_core_jar()` and `core_jar_stale()` untouched as required.
- `core/project.scala` — added SYNC comment cross-referencing `build.mill`'s `mvnDeps` as the mirror of `//> using dep` entries.
- `.github/Dockerfile.ci` — inserted Node 20 tarball extraction, `corepack enable`, Mill 1.1.5 install between the Coursier and BATS blocks; extended the final verification line to include `node --version`, `corepack --version`, `mill --version`.
- `.gitignore` — added `out/` so Mill's scratch directory stays out of commits.
- `.scalafmt.conf` — added `project.excludePaths = ["glob:**/out/**"]` so `scala-cli fmt --check .` (used by pre-commit) doesn't scan Mill-generated sources inside `out/` (scalafmt does not respect `.gitignore`).

**Deviations from plan:**

1. `iwCoreJar` is a `Task.Command` (not a normal `Task`). Mill 1.1.5 forbids plain `Task`s from writing outside `Task.dest`; `Task.Command` is the correct pattern for tasks that materialise files at arbitrary paths. Invocation (`./mill iwCoreJar`) is unchanged.
2. `sources` enumerates explicit subdirectories (`adapters`, `dashboard`, `model`, `output`) plus the single root file `IssueCreateParser.scala`, rather than the whole `core/` directory. Including `core/` wholesale would pull in `core/test/**` and fail compilation with ~4200 errors. **Maintenance note:** if new root-level `.scala` files land in `core/`, the `sources` list in `build.mill` must be extended — otherwise they will be silently excluded from the Mill build.
3. Vendored binaries landed at `.github/deps/` (Docker build context is `.github/`, so `COPY deps/...` directives are relative to that). This matches the pre-existing `deps/` pattern for `jdk.tar.gz`, `scala-cli`, `cs`.

**Code review:**
- Iterations: 1 review pass + 1 fix pass (no critical issues; 6 warnings of which 4 fixed, 2 deferred).
- Review file: `review-phase-01-20260421-165907.md`
- Fixed in iteration 2: temporal phase-name references in `build.mill:24`, `build.mill:30-31`, `.github/Dockerfile.ci:44`; added PURPOSE header and `DEFAULT_MILL_VERSION` clarifying comment to `./mill` wrapper.
- Deferred: checksum verification for Mill launcher download (vendored upstream wrapper) and Node tarball (matches pre-existing `deps/` pattern) — flagged for future project-wide hardening, not addressed in this phase.

**Testing:**
- Unit tests: 0 new tests added (build infrastructure — no Scala logic to unit-test).
- Regression: all 141 existing munit tests pass against `build/iw-core.jar` produced by Mill (`./iw ./test unit` green).
- Smoke: `./iw status` runs end to end against the Mill-built jar (no NoClassDefFoundError / classpath drift).
- Pre-push hook: format + scalafix + `-Werror` compile + unit tests + command compilation all green with the Mill-built jar in place.
- Build verification: `./mill iwCoreJar` from a clean tree (wiped `out/` and `build/iw-core.jar`) produces the jar; first run ~22s, second run ~0.3s cache hit.

**Contract for next phases:**
- `build/iw-core.jar` on-disk contract unchanged (same path, thin jar of compiled classes only). `iw-run` continues to consume the jar via `$CORE_JAR`.
- `core/project.scala` remains the runtime-classpath source of truth for scala-cli command invocations. `build.mill` is the build-time jar producer. Both dep lists must be kept in sync manually; the SYNC comment is a reader's cue, not an enforcement mechanism.
- Dashboard-only deps (`cask`, `scalatags`, `flexmark`) are intentionally duplicated across both files and are scheduled to move together when Phase 2 introduces a dedicated dashboard module. Do not split the dep cleanup from the directory move.
- Node 20 + Corepack land in the CI image now but are not consumed until the frontend pipeline phase. This was a deliberate bundling decision to amortise the image-rebuild cost.

**Files changed:**
```
M	.github/Dockerfile.ci
M	.gitignore
M	.scalafmt.conf
M	core/project.scala
M	iw-run
A	.mill-version
A	build.mill
A	mill
```

---


## Phase 2: Dashboard JVM module — move, rename, test migration (2026-04-22)

**Layer:** L1 — Dashboard module extraction

**What was built:**
- Relocated 41 dashboard source files (`core/dashboard/**` → `dashboard/jvm/src/**`), 47 dashboard-touching tests (`core/test/*.scala` → `dashboard/jvm/test/src/*.scala`), and 2 static resources (`core/dashboard/resources/**` → `dashboard/jvm/resources/**`). All via `git mv` (rename history preserved).
- Package rename `iw.core.dashboard.*` → `iw.dashboard.*` across every moved file, plus three external references: `commands/server-daemon.scala` package declaration, `core/adapters/ProcessManager.scala:115` `--main-class` string literal, `core/CLAUDE.md` guidance line.
- New Mill module `object dashboard extends ScalaModule` in `build.mill` with `moduleDeps = Seq(core)`, explicit `def resources` for Cask classpath serving, and inner `object test extends ScalaTests with TestModule.Munit` at `munit 1.2.1` (parity with `core/project.scala`).
- `iwCoreJar` reshaped to stage-and-repack `core.jar` + `dashboard.jar` into `build/iw-core.jar` so `iw-run`'s scala-cli bridge can resolve both `iw.core.*` and `iw.dashboard.*` from one classpath entry.
- `.iw/commands/test.scala`: `unit` stage now runs scala-cli core tests AND `./mill dashboard.test` (both must pass); `compile` stage adds `dashboard/jvm/src/` as a source root when compiling the two bridge scripts.
- Scoped `//> using dep` lines on `commands/dashboard.scala` and `commands/server-daemon.scala` for `cask 0.11.3`, `scalatags 0.13.1`, `flexmark-all 0.64.8` (transitional bridge; earmarked for Phase 4 deletion).

**Deviations from Phase 2 context plan (documented here for the reviewer):**
1. **Flexmark stayed in `core.mvnDeps` and `core/project.scala`.** The context's "drop flexmark as dashboard-only" instruction was factually wrong — `core/output/MarkdownRenderer.scala` imports `com.vladsch.flexmark.{html.HtmlRenderer, parser.Parser, util.data.MutableDataSet}` directly. Flexmark remains a core dep; removed only from `dashboard.mvnDeps` (where it's available transitively via `moduleDeps = Seq(core)`).
2. **`TestFixtures.scala` stayed in `core/test/`.** Per the "Test migration" guidance in the context, this shared fixture is referenced by non-dashboard tests (`ConfigRepositoryTest`, `ConfigFileTest`, `GitTest`, `LinearIssueTrackerTest`). Only the unused `import iw.core.dashboard.domain.*` line was removed (nothing in the file actually referenced dashboard domain types).
3. **`iwCoreJar` became a combined core+dashboard jar.** The context didn't specify how `iw.dashboard.*` classes reach the bridge scripts at runtime. Three options: (a) make `iw-run` pass two jars, (b) update `iw-run` to use Mill run/assembly, (c) keep `iw-run` unchanged and produce a combined jar. Chose (c) — smallest change, keeps the launcher untouched, honours "dashboard runs bit-identically to today". Transitional until Phase 4 replaces the bridge with `dashboard.assembly`.

**Dependencies on other layers:**
- **Phase 1 (Mill bootstrap + `iwCoreJar`):** extended the `core` module and `iwCoreJar` command originally introduced by Phase 1. No launcher changes.
- **Phase 3 (frontend + assembly):** will add `dashboard/frontend/`, a `frontend` Mill module, and replace `iwCoreJar` with `dashboard.assembly` for fat-jar production. The combined-jar workaround above is the placeholder.
- **Phase 4 (launcher rewrite):** will delete the six scoped `//> using dep` lines from the two bridge scripts when the scripts stop importing `iw.dashboard.*` types.

**Testing:**
- Unit tests (scala-cli, remaining core): all pass.
- Unit tests (Mill, migrated dashboard): 188/188 pass under `./mill dashboard.test`.
- Command compile: 33/33 commands compile via `./iw ./test compile` (including bridge scripts with dashboard sources as extra source root).
- Smoke check: `test/dashboard-dev-mode.bats` "dev mode creates temp directory" passes — confirms the in-process scala-cli path still launches the dashboard.
- Blocking grep invariants all pass (`rg 'iw\.core\.dashboard'` → 0 hits in scala+md; no `cask`/`scalatags` in `core/project.scala`; no `cask`/`scalatags`/`flexmark` under `core.mvnDeps` in `build.mill`).

**Code review:**
- Iterations: 1 (passed)
- Review file: `review-phase-02-20260422-080341.md`
- Fixes applied in-iteration: SYNC comments added to both bridge scripts, flexmark deduplicated from `dashboard.mvnDeps`, "what" comments rewritten as "why" comments in `build.mill` and `.iw/commands/test.scala`.
- Deferred (documented in review file): integration-style tests placed in unit test module (`CaskServerTest`, `StaticFilesTest`, `ServerClientTest`) — pre-existing classification, phase context forbids reorganization. TOCTOU port race in test helpers — pre-existing. `iwCoreJar` jar-overwrite guard — structurally impossible collision given package prefixes.

**Contract for next phases:**
- `dashboard/jvm/src/` and `dashboard/jvm/resources/` are the canonical dashboard source layout. `dashboard/jvm/test/src/` is the Mill test-sources path.
- `build.mill`'s `dashboard.mvnDeps` is the authoritative dep list for the dashboard module at build time. The scoped `//> using dep` lines on the two bridge scripts are a transitional mirror of those versions (tagged SYNC); they vanish in Phase 4.
- `build/iw-core.jar` is now a combined core+dashboard jar. Phase 3 should replace the `iwCoreJar` stage-and-repack with `dashboard.assembly` once the frontend pipeline is in place; `iw-run` will need to follow when Phase 4 rewrites the bridge to spawn `java -jar`.
- Munit version parity between scala-cli core tests (`core/project.scala`) and Mill dashboard tests (`dashboard.test.mvnDeps`) must remain at `1.2.1`. Drifting the test-runner version is a test-output divergence risk.

**Files changed:**
```
M	.iw/commands/test.scala
M	build.mill
M	commands/dashboard.scala
M	commands/server-daemon.scala
M	core/CLAUDE.md
M	core/adapters/ProcessManager.scala
M	core/project.scala
M	core/test/TestFixtures.scala
R	core/dashboard/**.scala (41 files) → dashboard/jvm/src/**.scala
R	core/dashboard/resources/** (2 files) → dashboard/jvm/resources/**
R	core/test/*.scala (47 files) → dashboard/jvm/test/src/*.scala
```

---


## Phase 3: Frontend pipeline + fat-jar assembly + integration tests (2026-04-22)

**Layer:** L2 + L3 — Bundle assets, embed in jar, prove end-to-end

**What was built:**
- Frontend source tree at `dashboard/frontend/`: `package.json` (Yarn 4.9.2 via Corepack, Vite 8, Tailwind v4 via `@tailwindcss/vite`, Web Awesome Pro 3.2.1, htmx ^2), `.yarnrc.yml` (npmScopes entry for `@web.awesome.me` scope with `${WEBAWESOME_NPM_TOKEN}` substitution, `nodeLinker: node-modules`, `enableGlobalCache: false`), `vite.config.js` (entry `src/main.js`, `dist/assets/` output, `server.cors: true`), `src/main.js` (9 cherry-picked Web Awesome components: button, tag, icon, input, textarea, tree, tree-item, card, page), `src/main.css` (`@import "tailwindcss"` + `@source "../jvm/src/**/*.scala"`), `yarn.lock` committed for reproducibility.
- Mill `object frontend extends Module` in `build.mill` with `viteBuild: T[PathRef]` — runs `yarn install --immutable` + `yarn build`, copies `dist/` into `Task.dest / "dist"`, declares `Task.Sources` over frontend inputs + Scala templates for change tracking.
- `dashboard.resources` override folds `frontend.viteBuild()` output alongside `dashboard/jvm/resources/` — both land at classpath root of the assembled jar.
- `dashboard.mainClass = Some("iw.dashboard.ServerDaemon")` for `java -jar` discoverability.
- `object itest extends ScalaTests with TestModule.Munit` inner module on `dashboard` at `dashboard/jvm/itest/src/` — munit 1.2.1 + sttp-client4 4.0.15. Sources resolution uses `os.up / os.up` anchor from the nested module dir.
- Top-level `iwDashboardJar(): Command[PathRef]` — copies `dashboard.assembly()` to `build/iw-dashboard.jar`, matching Phase 1's `iwCoreJar` pattern for launcher discoverability.
- `dashboard/jvm/itest/src/CaskServerItest.scala` — 15 tests total: in-process Cask launch (home page renders, references `/static/dashboard.css` + `/assets/main.js`), static asset resolution (`/static/dashboard.css` + `/static/dashboard.js` return 200 from classpath), `/assets/` Vite bundle resolution, 404 handling for both routes, path-traversal rejection via raw `HttpURLConnection` that preserves `%2F` on the wire (for both `/static/` and `/assets/`), jar-contents assertions (Main-Class manifest, required zip entries).
- `CaskServer.scala` updates: `/static/:filename` handler switched from filesystem (`IW_CORE_DIR/dashboard/resources/static/`) to classpath via `getResourceAsStream`; new `/assets/:filename` handler for Vite output; shared `serveClasspathResource` helper with path-traversal guard (strips known prefix, rejects `..`, `/`, `\`) and `Using.resource` stream closure.
- `.iw/commands/test.scala`: new `itest` case invoking `./mill dashboard.itest.test`, added to `all` flow chain, `showUsage` updated.
- `.github/workflows/ci.yml`: new `dashboard-build` job (`needs: compile`, exposes `WEBAWESOME_NPM_TOKEN` via `env:`, runs `./mill iwDashboardJar` + `./mill dashboard.itest.test` with 15-minute step timeouts).
- `.gitignore`: `dashboard/frontend/{node_modules,dist,.yarn}/`.

**Deviations from Phase 3 context plan:**
1. **`dashboard.resources` override shape.** The context suggested `Seq(PathRef(jvmResources), frontend.viteBuild())`; final implementation follows Mill 1.1.x multi-source idioms after iteration.
2. **Integration test scope.** The optional "subprocess smoke test" was deliberately skipped — verifying jar contents (manifest + zip entries) suffices without requiring `ServerDaemon` CLI arg changes (Phase 4 territory).
3. **Path-traversal test technique.** sttp's `uri"..."` interpolator normalises `%2F`, which meant the straightforward traversal test could pass via "route not matched" 404 rather than exercising the guard. Switched to `java.net.URI(...).toURL` + `HttpURLConnection` to preserve `%2F` on the wire. Meta-verification (temporarily removing the guard) confirmed Java's classloader rejects `..` segments in `getResourceAsStream` as a second barrier — the test thus locks in regression coverage against future refactors that remove either the guard or the classloader barrier.

**Dependencies on other layers:**
- **Phase 1:** Mill 1.1.5 + Node 20 + Corepack already in CI image; `iwCoreJar` stays untouched (Phase 3 coexistence contract preserves it).
- **Phase 2:** `dashboard` Mill module (`moduleDeps = Seq(core)`) extended with `resources` override + `mainClass` + `object itest`; dashboard-only `mvnDeps` (cask, scalatags, scalatags-webawesome) remain as in Phase 2.
- **Phase 4 (will consume):** `build/iw-dashboard.jar` is the artifact Phase 4's launcher rewrite will spawn via `java -jar`. `iwCoreJar` retirement and `commands/{dashboard,server-daemon}.scala` scoped `//> using dep` cleanup are atomic Phase 4 concerns.

**CLARIFY resolutions:**
- **CLARIFY 1 (registry hostname):** `https://registry.webawesome.com` with scope `@web.awesome.me`, `npmAlwaysAuth: true`, `npmAuthToken: "${WEBAWESOME_NPM_TOKEN}"`.
- **CLARIFY 2 (import path idiom):** `/dist/components/<name>/<name>.js` confirmed against installed package's `exports` field.
- **CLARIFY 10 (component cherry-pick):** 9 components (button, tag, icon, input, textarea, tree, tree-item, card, page) derived from `rg '<wa-[a-z-]+' dashboard/jvm/src/presentation/`.
- **CLARIFY 11 (Tailwind `@source` scanning `.scala`):** verified Tailwind v4 picks up Scala template class names; no explicit extract hook needed.
- **CLARIFY 14 (mainClass override):** added explicitly — `dashboard.assembly` leaves `Main-Class` blank otherwise.
- **CLARIFY 16 (CI secret):** `WEBAWESOME_NPM_TOKEN` provisioning in GitHub Actions is an action item coordinated with Michal out of band; `dashboard-build` job will fail-fast on `yarn install` auth until the secret is present.

**Testing:**
- Integration tests: 15 tests added in `dashboard.itest.testForked` — 8 HTTP-level (home page, static/assets resolution, 404, path traversal × 2) + 5 jar-contents (Main-Class manifest + required zip entries) + 2 more coverage cases.
- Unit tests: 192/192 dashboard.test SUCCESS; scala-cli core tests unchanged.
- Regression: `./iw dashboard --help` in-process path still works; pre-push hook green (format + compile + unit tests + 33 commands).
- Build verification: `./mill frontend.viteBuild` from clean produces `out/frontend/viteBuild.dest/assets/{main.js, main.css}`; `./mill iwDashboardJar` produces 41 MB `build/iw-dashboard.jar` in 29s; `unzip -l` confirms both `assets/*` (Vite) and `static/*` (pre-existing) prefixes; `Main-Class: iw.dashboard.ServerDaemon` in manifest.
- Clean-clone reproducibility: `rm -rf out/ build/ dashboard/frontend/{node_modules,dist}/` then `./mill iwDashboardJar && ./mill dashboard.itest.testForked` succeeds end to end.
- Grep invariants: `rg WEBAWESOME_NPM_TOKEN dashboard/frontend/` — single hit in `.yarnrc.yml` (env-var substitution, not literal); `rg webawesome-pro dashboard/frontend/src/` — 9 component imports.

**Code review:**
- Iterations: 3 (full loop).
- Review file: `review-phase-03-20260422-170721.md`.
- Iteration 1 critical issues (all resolved in iteration 2): path traversal in `serveClasspathResource` (filename param unvalidated), InputStream from `getResourceAsStream` not closed (resource leak), `freePort()` TOCTOU race (flaky CI under `testForked` parallelism).
- Iteration 2 critical issues (all resolved in iteration 3): path-traversal test could have passed for the wrong reason due to sttp URI normalisation (switched to raw `HttpURLConnection`), server thread silently swallowed non-`BindException` errors (added `AtomicReference[Throwable]` capture + re-throw). Also bundled: `/assets/` traversal parallel test, `Random.nextLong().abs` replaced with `Files.createTempDirectory`, unused import removal, test helpers made `private`, trailing comma in `itest` `mvnDeps`, temporal-phase comment on `iwDashboardJar` removed.
- Deferred (documented, not addressed this phase): `CaskServer` god object (pre-existing, architectural), missing security headers (`X-Content-Type-Options`, `X-Frame-Options`) — follow-up design decision, `Cache-Control` headers on static asset responses, 404 body contract alignment with JSON error shape used elsewhere, jar-contents test consolidation via `withJar` bracket, `frontend` module placement (top-level vs `dashboard.frontend` nested).

**Contract for next phases:**
- `build/iw-dashboard.jar` at repo root is the artifact Phase 4's launcher rewrite (`commands/dashboard.scala`, `commands/server-daemon.scala`) will spawn via `java -jar`. Jar includes `iw.core.*` + `iw.dashboard.*` compile classes + runtime deps + `dashboard/jvm/resources/**` (at classpath root) + `frontend.viteBuild()` Vite output (at classpath root). `Main-Class` in manifest is `iw.dashboard.ServerDaemon`.
- `iwCoreJar` remains the artifact `iw-run` consumes until Phase 4 atomically retires it and flips the launcher.
- `serveClasspathResource` guard + `Using.resource` closure are production code worth preserving across future `CaskServer` refactors.
- `.iw/commands/test.scala` `itest` case: `./iw ./test itest` runs `./mill dashboard.itest.test`; bare `./iw ./test` runs `unit + itest + compile + e2e`.
- `dashboard-build` CI job fails fast if `WEBAWESOME_NPM_TOKEN` repo secret is absent — provisioning is an out-of-band action item with Michal.
- Pre-push hook intentionally does NOT run itest or frontend build (expensive); CI carries the itest signal.

**Files changed:**
```
M	.github/workflows/ci.yml
M	.gitignore
M	.iw/commands/test.scala
M	build.mill
A	dashboard/frontend/.yarnrc.yml
A	dashboard/frontend/package.json
A	dashboard/frontend/src/main.css
A	dashboard/frontend/src/main.js
A	dashboard/frontend/vite.config.js
A	dashboard/frontend/yarn.lock
A	dashboard/jvm/itest/src/CaskServerItest.scala
M	dashboard/jvm/src/CaskServer.scala
M	dashboard/jvm/src/presentation/views/PageLayout.scala
```

---
