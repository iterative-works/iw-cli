# Phase 3 Tasks: Frontend pipeline + fat-jar assembly + integration tests

## Setup

- [ ] [setup] Confirm `WEBAWESOME_NPM_TOKEN` is exported in the local shell (`echo ${WEBAWESOME_NPM_TOKEN:+set}` prints `set`); abort the phase and request the token from Michal if unset.
- [ ] [setup] Confirm `corepack --version` and `node --version` (Node 20) succeed locally; if missing, run `corepack enable` and re-verify before any `./mill frontend.viteBuild` invocation.
- [ ] [setup] Resolve CLARIFY 1 by reading the procedures-repo / `~/Devel/iw/support-libs` reference `.yarnrc.yml` (or Web Awesome Pro onboarding docs) to pin the exact registry hostname and `npmScopes` key (`web.awesome.me` vs other) used in `dashboard/frontend/.yarnrc.yml`; record the chosen hostname in the implementation log.
- [ ] [setup] Resolve CLARIFY 10 by running `rg -n '<wa-[a-z-]+' dashboard/jvm/src/presentation/` and capturing the deduplicated set of `wa-*` tags actually used by templates; this list pins the cherry-pick imports written into `src/main.js`.
- [ ] [setup] Resolve CLARIFY 14 by checking whether stock `dashboard.assembly` writes a non-blank `Main-Class` in `META-INF/MANIFEST.MF`; default to adding `def mainClass = Some("iw.dashboard.ServerDaemon")` to the `dashboard` module unless verification proves it is already populated.
- [ ] [setup] Pin the exact Yarn 4 patch version (e.g. `yarn@4.9.2`) by checking https://yarnpkg.com/blog or `corepack prepare yarn@stable --activate` output, and record the pinned string for use in `package.json`.
- [ ] [setup] Capture pre-change baseline: run `./iw ./test unit` and record the passing-test count (currently 188 dashboard + scala-cli core), and run the pre-push hook end to end and confirm it is green so any later regression is attributable.

## Tests

- [ ] [test] Write `dashboard/jvm/itest/src/CaskServerItest.scala` (with `// PURPOSE:` header) — in-process Cask launch test that picks a high port with `BindException` retry, asserts `GET /` returns 200 with `Content-Type: text/html`, body contains `<html` and references `/static/dashboard.css` and `/assets/main.js`.
- [ ] [test] Add a bundled-asset resolution test case (same itest module): `GET /assets/main.js` returns 200 with `Content-Type: application/javascript` and non-empty body; `GET /assets/<bundled-css>` returns 200 with `Content-Type: text/css` (use prefix match for the hashed filename).
- [ ] [test] Add a jar-contents test case (same itest module) that opens `build/iw-dashboard.jar` as a zip from test code and asserts `META-INF/MANIFEST.MF` contains `Main-Class: iw.dashboard.ServerDaemon`, the zip directory lists `assets/main.js`, at least one `assets/*.css` entry, and the pre-existing `static/dashboard.css` + `static/dashboard.js` entries.
- [ ] [test] Regression checkpoint (post-change): run `./iw ./test unit` and confirm the passing-test count matches the baseline captured in Setup (no test loss from migration or restructure).
- [ ] [test] Regression checkpoint (post-change): run the pre-push hook end to end and confirm it is green (format + scalafix + `-Werror` compile + unit tests + command compilation) — pre-push intentionally does not run itest or frontend build.
- [ ] [test] Regression checkpoint (post-change): run `./iw dashboard` end to end and `curl -s http://<host>:<port>/ | grep -q '<html'` to confirm the in-process scala-cli launch path still works (Phase 4 will flip this to `java -jar`).

## Implementation

- [ ] [impl] Create `dashboard/frontend/package.json` with `name: iw-dashboard-frontend`, `private: true`, `type: "module"`, `packageManager: "yarn@<pinned-patch>"`, scripts `build: vite build` and `dev: vite`, dependencies `htmx.org ^2` and `@web.awesome.me/webawesome-pro 3.2.1`, devDependencies `vite ^8`, `@tailwindcss/vite ^4`, `tailwindcss ^4`. Do not include `markdown-it`.
- [ ] [impl] Create `dashboard/frontend/.yarnrc.yml` with `nodeLinker: node-modules`, `enableGlobalCache: false`, and `npmScopes:` entry for the Web Awesome scope (resolved via CLARIFY 1) referencing `${WEBAWESOME_NPM_TOKEN}` (env-var substitution; never literal token).
- [ ] [impl] Create `dashboard/frontend/vite.config.js` (with `// PURPOSE:` header) using `defineConfig`, `plugins: [tailwindcss()]`, `build.outDir: "dist"`, `build.assetsDir: "assets"`, `build.emptyOutDir: true`, `build.rollupOptions.input: "src/main.js"`, `server.cors: true`.
- [ ] [impl] Create `dashboard/frontend/src/main.js` (with `// PURPOSE:` header comment) containing `import "./main.css"`, `import "htmx.org"`, then one cherry-picked `@web.awesome.me/webawesome-pro/dist/components/<name>/<name>.js` import per tag from the CLARIFY 10 grep result. No full-bundle import.
- [ ] [impl] Verify CLARIFY 2 at this step by reading `dashboard/frontend/node_modules/@web.awesome.me/webawesome-pro/package.json`'s `exports` field after first `yarn install`; adjust the cherry-pick import paths in `src/main.js` if the export-map idiom differs from `/dist/components/<name>/<name>.js`.
- [ ] [impl] Create `dashboard/frontend/src/main.css` (with `/* PURPOSE: */` header) containing `@import "tailwindcss";` and `@source "../jvm/src/**/*.scala";`.
- [ ] [impl] Run `yarn install` once in `dashboard/frontend/` to generate `dashboard/frontend/yarn.lock`; commit the lockfile.
- [ ] [impl] Verify CLARIFY 11 at this step: build the frontend, locate a Scala template using a Tailwind utility (e.g. `rg -l 'class="[^"]*\bflex\b' dashboard/jvm/src/presentation/`), grep the produced CSS for `.flex{` (or compiled equivalent), and add an explicit `extract` hook in `main.css` or `vite.config.js` only if Tailwind misses Scala sources.
- [ ] [impl] Edit `build.mill` — append `object frontend extends Module` with `def frontendDir = Task.Sources(...)` over `package.json`, `yarn.lock`, `vite.config.js`, `src`, `.yarnrc.yml`; `def scalaTemplates = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "src")`; `def viteBuild: T[PathRef] = Task { ... }` that depends on both source sets, runs `os.proc("yarn", "install", "--immutable").call(cwd = srcDir, stdout = os.Inherit, stderr = os.Inherit)` then `os.proc("yarn", "build")...`, copies `dist/` into `Task.dest / "dist"`, returns `PathRef(out)`.
- [ ] [impl] Edit `build.mill` `object dashboard` — override `def resources = Task { Seq(PathRef(<jvm/resources>), frontend.viteBuild()) }` so both the existing JVM resources tree and the Vite output land at the assembled jar's classpath root.
- [ ] [impl] Edit `build.mill` `object dashboard` — add `def mainClass = Some("iw.dashboard.ServerDaemon")` (per CLARIFY 14 resolution) so `java -jar build/iw-dashboard.jar` works without `--main-class`.
- [ ] [impl] Edit `build.mill` — add `object itest extends ScalaTests with TestModule.Munit` as an inner module on `dashboard`, with `def sources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "itest" / "src")` and `def mvnDeps = Seq(mvn"org.scalameta::munit::1.2.1", mvn"com.softwaremill.sttp.client4::core:4.0.15")`.
- [ ] [impl] Edit `build.mill` — add top-level `def iwDashboardJar(): Command[PathRef] = Task.Command { val target = ... / "build" / "iw-dashboard.jar"; os.makeDir.all(target / os.up); os.copy.over(dashboard.assembly().path, target); PathRef(target) }`.
- [ ] [impl] Edit `dashboard/jvm/src/CaskServer.scala` `/static/:filename` handler (lines 609-639) — switch the body from filesystem resolution under `IW_CORE_DIR/dashboard/resources/static/` to `this.getClass.getResourceAsStream("/static/$filename")`, preserving Content-Type dispatch and 404 semantics.
- [ ] [impl] Edit `dashboard/jvm/src/CaskServer.scala` — add a parallel `@cask.get("/assets/:filename")` handler that resolves via `this.getClass.getResourceAsStream("/assets/$filename")` with appropriate Content-Type dispatch (js/css/etc) and 404 fallback.
- [ ] [impl] Edit `.iw/commands/test.scala` — add `case "itest" => sys.exit(if runIntegrationTests() then 0 else 1)` to the match, extend the `case "all"` flow to chain `unitResult && itestResult && compileResult && e2eResult`, and add `def runIntegrationTests(): Boolean = { Output.section("Running Dashboard Integration Tests"); val installDir = os.Path(System.getenv("IW_INSTALL_DIR")); ProcessAdapter.runStreaming(Seq((installDir / "mill").toString, "dashboard.itest.test")) == 0 }`.
- [ ] [impl] Edit `.iw/commands/test.scala` `showUsage` to list `itest` alongside `unit`/`compile`/`e2e`.
- [ ] [impl] Edit `.github/workflows/ci.yml` — append a new `dashboard-build` job after `test`, with `needs: compile`, `runs-on: self-hosted`, container `ghcr.io/iterative-works/iw-cli-ci:latest` (with `credentials` block), `env: WEBAWESOME_NPM_TOKEN: ${{ secrets.WEBAWESOME_NPM_TOKEN }}`, steps `actions/checkout@v4`, `./mill iwDashboardJar` (timeout 15m), `./mill dashboard.itest.test` (timeout 15m).
- [ ] [impl] Edit `.gitignore` — add `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`, `dashboard/frontend/.yarn/`.

## Integration

- [ ] [integration] Run `./mill frontend.viteBuild` from a clean tree (with `WEBAWESOME_NPM_TOKEN` set) and confirm `out/frontend/viteBuild.dest/dist/` contains `assets/main.js` and at least one `assets/*.css`.
- [ ] [integration] Auth-failure mode: `unset WEBAWESOME_NPM_TOKEN; ./mill frontend.viteBuild` and confirm `yarn install` fails with a 401/auth error from the registry; re-export the token afterwards.
- [ ] [integration] Run `./mill iwDashboardJar` and confirm `build/iw-dashboard.jar` is produced.
- [ ] [integration] Run `unzip -l build/iw-dashboard.jar | grep -E "(assets|static)"` and confirm both `assets/main.js` (+ `assets/*.css`) and `static/dashboard.css` + `static/dashboard.js` appear.
- [ ] [integration] Run `unzip -p build/iw-dashboard.jar META-INF/MANIFEST.MF | grep Main-Class` and confirm it prints `Main-Class: iw.dashboard.ServerDaemon`.
- [ ] [integration] Run `./mill dashboard.itest.test` from a clean tree and confirm all itest cases pass green.
- [ ] [integration] Tailwind `@source` manual spot-check: pick a Scala template using a known utility class (per the CLARIFY 11 procedure), grep the Vite-built CSS for the compiled rule, and document the outcome in the implementation log.
- [ ] [integration] Run `rg -n 'WEBAWESOME_NPM_TOKEN' dashboard/frontend/` and confirm the only hit is the env-var reference in `.yarnrc.yml` (`${WEBAWESOME_NPM_TOKEN}`); abort if any literal token text appears.
- [ ] [integration] Run `rg -n 'webawesome-pro' dashboard/frontend/src/` and confirm at least one cherry-picked component import line matches.
- [ ] [integration] Clean-clone reproducibility: `rm -rf out/ build/iw-dashboard.jar dashboard/frontend/node_modules/ dashboard/frontend/dist/`, then `./mill iwDashboardJar && ./mill dashboard.itest.test` and confirm both succeed end to end.
- [ ] [integration] Confirm CI is green on all five jobs (`compile`, `format`, `lint`, `test`, `dashboard-build`) on the PR.
- [ ] [integration] Run the pre-push hook on the final tree and confirm it passes (no itest or frontend build expected — hook scope unchanged).
- [ ] [integration] Run `./iw dashboard` end to end and confirm in-process launch still works: `curl -s http://<host>:<port>/ | grep -q '<html'`, then teardown cleanly. The Phase 4 cutover to `java -jar` has not happened yet.

## Acceptance Criteria Coverage

- AC1 `dashboard/frontend/package.json` declares `packageManager: "yarn@4.9.x"` (exact patch pinned), Vite ^8, Tailwind v4 via `@tailwindcss/vite`, Web Awesome Pro 3.2.1, htmx 2 → `[setup]` Yarn patch pin; `[impl]` create `package.json`.
- AC2 `dashboard/frontend/yarn.lock` committed → `[impl]` generate and commit `yarn.lock`.
- AC3 `dashboard/frontend/.yarnrc.yml` authenticates the Web Awesome scope via `${WEBAWESOME_NPM_TOKEN}`; no literal token → `[setup]` resolve CLARIFY 1; `[impl]` create `.yarnrc.yml`; `[integration]` `WEBAWESOME_NPM_TOKEN` grep invariant.
- AC4 `dashboard/frontend/src/main.js` cherry-picks Web Awesome components; no full-bundle import → `[setup]` resolve CLARIFY 10 via `<wa-` grep; `[impl]` create `src/main.js`; `[impl]` verify CLARIFY 2; `[integration]` `webawesome-pro` grep invariant.
- AC5 `dashboard/frontend/src/main.css` uses `@import "tailwindcss"` + `@source "../jvm/src/**/*.scala"` → `[impl]` create `src/main.css`; `[impl]` verify CLARIFY 11; `[integration]` Tailwind `@source` spot-check.
- AC6 `./mill frontend.viteBuild` succeeds from a clean tree with `WEBAWESOME_NPM_TOKEN` set; output contains `dist/assets/main.js` + CSS → `[setup]` confirm token exported; `[impl]` add `frontend` Mill module; `[integration]` `./mill frontend.viteBuild` clean-tree run.
- AC7 `./mill frontend.viteBuild` fails fast with a clear auth error when `WEBAWESOME_NPM_TOKEN` is unset → `[integration]` auth-failure mode run.
- AC8 `build.mill` declares a `frontend` module with `viteBuild: T[PathRef]`; `dashboard.resources` folds `frontend.viteBuild()` into its result; `dashboard.mainClass` is `iw.dashboard.ServerDaemon`; `iwDashboardJar()` copies the assembly to `build/iw-dashboard.jar` → `[setup]` resolve CLARIFY 14; `[impl]` add `frontend` Mill module; `[impl]` `dashboard.resources` override; `[impl]` add `dashboard.mainClass`; `[impl]` add `iwDashboardJar()`.
- AC9 `./mill iwDashboardJar` produces `build/iw-dashboard.jar`; `unzip -l` shows both Vite assets and pre-existing resources → `[integration]` `./mill iwDashboardJar` run; `[integration]` `unzip -l` assets/static check.
- AC10 Jar-contents assertions pass: manifest has `Main-Class: iw.dashboard.ServerDaemon`; zip lists `assets/main.js`, a Vite CSS bundle under `assets/`, and pre-existing `static/dashboard.css` + `static/dashboard.js` → `[test]` jar-contents test case; `[integration]` `unzip -p ... META-INF/MANIFEST.MF` Main-Class check; `[integration]` `unzip -l` assets/static check.
- AC11 `dashboard/jvm/itest/src/` contains munit tests covering in-process `GET /`, `/assets/main.js`, and the jar-contents assertions → `[test]` write `CaskServerItest.scala`; `[test]` bundled-asset resolution case; `[test]` jar-contents case.
- AC12 `./mill dashboard.itest.test` runs all itest cases green from a clean tree → `[impl]` `object itest` inner module; `[integration]` clean-clone reproducibility run; `[integration]` `./mill dashboard.itest.test` run.
- AC13 `CaskServer.scala`'s `/static/:filename` handler resolves via `getResourceAsStream`; new `/assets/:filename` handler serves Vite output from classpath → `[impl]` `/static/` classpath switch; `[impl]` new `/assets/` handler.
- AC14 `.iw/commands/test.scala` exposes `itest` sub-command and includes it in default `all` run; `./iw ./test itest` runs `./mill dashboard.itest.test`; `./iw ./test` (no arg) runs unit + itest + compile + e2e → `[impl]` `test.scala` `itest` case + `runIntegrationTests`; `[impl]` `showUsage` update.
- AC15 `.github/workflows/ci.yml` has a `dashboard-build` job, `needs: compile`, exposing `WEBAWESOME_NPM_TOKEN`, running `./mill iwDashboardJar` + `./mill dashboard.itest.test`; other four jobs unchanged → `[impl]` CI workflow append; `[integration]` CI five-job green check.
- AC16 `WEBAWESOME_NPM_TOKEN` provisioned as a GitHub Actions repo secret (action item) → `[setup]` confirm token set locally (CLARIFY 16 coordination handled out of band with Michal).
- AC17 `.gitignore` has entries for `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`, `dashboard/frontend/.yarn/` → `[impl]` `.gitignore` additions.
- AC18 All 141+ existing unit tests still pass → `[test]` baseline `./iw ./test unit`; `[test]` post-change `./iw ./test unit`.
- AC19 `./iw dashboard` in-process launch still works end to end → `[test]` post-change `./iw dashboard` smoke; `[integration]` `./iw dashboard` curl check.
- AC20 Phase 2 transitional elements preserved: `iwCoreJar` stage-and-repack intact; `commands/dashboard.scala` + `commands/server-daemon.scala` scoped `//> using dep` lines untouched; `core/adapters/ProcessManager.scala:115` string literal untouched → covered by intentionally-untouched scope (no Phase 3 task edits these files); `[test]` post-change pre-push validates command compilation.
- AC21 No CLAUDE.md rule violations: new files open with `// PURPOSE:` header, no temporal/historical wording, no emoji → `[impl]` `vite.config.js` PURPOSE header; `[impl]` `src/main.js` PURPOSE header; `[impl]` `src/main.css` PURPOSE header; `[test]` `CaskServerItest.scala` PURPOSE header.
- AC22 Frontend build pipeline (Yarn 4 via Corepack, Vite 8, Tailwind v4) functional locally → `[setup]` Corepack/Node verification; `[setup]` Yarn patch pin; `[integration]` `./mill frontend.viteBuild` clean-tree run.
- AC23 Reproducibility from clean clone with token set → `[integration]` clean-clone reproducibility run.
- AC24 Pre-push hook still green (unchanged scope) → `[test]` baseline pre-push; `[test]` post-change pre-push; `[integration]` post-change pre-push run.
