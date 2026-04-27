# Phase 3: Frontend pipeline + fat-jar assembly + integration tests

**Issue:** IW-345
**Layer:** L2 + L3 (merged — single conceptual unit: bundle assets, embed in jar, prove end-to-end)
**Branch:** IW-345 (feature branch)
**Status:** Not started
**Estimated:** 5-9 hours

## Goals

- Stand up a frontend build at `dashboard/frontend/` using Yarn 4 (via Corepack), Vite 8, Tailwind CSS v4, and cherry-picked Web Awesome Pro components. Package-manager pin via `package.json`'s `packageManager` field; Web Awesome Pro registry authenticated through `WEBAWESOME_NPM_TOKEN` in `.yarnrc.yml`.
- Introduce a Mill `frontend` module wrapping the Vite build as a Mill task (`frontend.viteBuild`) whose output is a directory of bundled assets (JS + CSS + any Web Awesome static assets).
- Wire `frontend.viteBuild`'s output into the `dashboard` module's resource set so the Vite dist folder is embedded at classpath root before fat-jar assembly runs.
- Add a `dashboard.assembly` task that produces `build/iw-dashboard.jar` — a runnable fat jar containing compiled `iw.core.*` + `iw.dashboard.*` classes, their runtime deps, the existing `dashboard/jvm/resources/**` tree, and the Vite-built assets.
- Add a new Mill test module `object itest extends ScalaTests with TestModule.Munit` on the `dashboard` module. Integration tests launch Cask in-process using the assembled classpath, hit a handful of routes over HTTP on an ephemeral port, and assert that bundled assets resolve from the classpath. Jar-shape assertions (manifest main-class, classpath resources, Vite bundle present) are separate contents-level checks, not live-HTTP subprocess tests.
- Extend the CI with a `dashboard-build` job that exposes `WEBAWESOME_NPM_TOKEN` as a secret, runs `./mill dashboard.assembly` and `./mill dashboard.itest.test`, and is separate from the existing `compile`/`format`/`lint`/`test` jobs so failures are attributed cleanly.
- Extend `./iw ./test` with an `itest` stage that invokes `./mill dashboard.itest.test`; a bare `./iw ./test` runs unit + itest + compile + e2e.

## Scope

### In scope

- **Frontend source tree** at `dashboard/frontend/`:
  - `package.json` with `"packageManager": "yarn@4.9.x"` (current stable at implementation time — pick the exact patch version from Yarn's release page at implementation), `type: "module"`, and dependencies as listed in Approach.
  - `yarn.lock` committed for reproducibility.
  - `.yarnrc.yml` with the Yarn 4 registry-auth config for Web Awesome Pro (`npmScopes` entry referencing `${WEBAWESOME_NPM_TOKEN}`), plus `nodeLinker: node-modules` (CLARIFY 1 — see below).
  - `vite.config.js` configured for Tailwind v4 (via `@tailwindcss/vite`), entry at `src/main.js`, `build.outDir: "dist"`, `build.assetsDir: "assets"`, `server: { cors: true }`.
  - `src/main.js` — the single Vite entry. Cherry-picks the specific Web Awesome components the dashboard uses today (button, tag, icon, input, textarea, tree, tree-item, card, page per analysis §L2; exact list validated against `dashboard/jvm/src/presentation/views/**` at implementation — CLARIFY 2), plus `import "./main.css"` and `import "htmx.org"`.
  - `src/main.css` — `@import "tailwindcss";` (Tailwind v4 syntax) + `@source "../jvm/src/**/*.scala";` so Tailwind scans Scala templates for class names.
- **Mill `frontend` module** in `build.mill`:
  - Not a `ScalaModule`. A plain `Module` (or `Task.Module`) exposing `def viteBuild = Task { ... }` that runs `yarn install --immutable` + `yarn build` in `dashboard/frontend/`, captures the resulting `dist/` directory, and returns a `PathRef` to it inside `Task.dest`.
  - Input tracking: declare `Task.Sources` over `dashboard/frontend/package.json`, `dashboard/frontend/yarn.lock`, `dashboard/frontend/vite.config.js`, `dashboard/frontend/src/**`, and the Tailwind `@source` dependency on `dashboard/jvm/src/**/*.scala`. Decision: include the Scala source dep so a template's Tailwind classes trigger a frontend rebuild — correctness over dev-loop speed for Phase 3; Phase 4's dev mode sidesteps this via Vite HMR.
- **Resource folding** on the `dashboard` module: override `def resources` so the existing `dashboard/jvm/resources/` tree plus `frontend.viteBuild()` output are both returned — Mill copies both into the assembly's classpath root. Exact override shape in Approach.
- **`dashboard.assembly` task**: Mill's stock `assembly` produces `out/dashboard/assembly.dest/out.jar`. A top-level wrapper task (analogous to Phase 1's `iwCoreJar()`) copies that jar to `build/iw-dashboard.jar` for launcher discoverability.
- **Static-asset Cask route update in `CaskServer.scala`** — the current `@cask.get("/static/:filename")` handler resolves files from a filesystem path under `IW_CORE_DIR/dashboard/resources/static/` (`dashboard/jvm/src/CaskServer.scala:609-639`). Switch it to classpath resolution via `getResourceAsStream("/static/$filename")`, and add a parallel `/assets/:filename` handler for the Vite bundle under classpath `/assets/`. Two handlers, ~10 extra lines; no template edits. This is the minimum server-side change required for the itest to pass; no broader refactor.
- **`object itest extends ScalaTests with TestModule.Munit`** as an inner module on `dashboard`:
  - Sources at `dashboard/jvm/itest/src/`.
  - `moduleDeps` includes the `dashboard` module — `ScalaTests` inherits the enclosing module's `runClasspath`, which includes the folded-in Vite resources. No dependency on `dashboard.assembly`'s output.
  - In-process tests (mandatory): launch Cask on a pre-picked high port with retry on `BindException`, `GET /` returns 200 with HTML referencing the bundled `main.js` + `main.css`, `GET /static/<bundled-file>` (or `/assets/...`) returns 200 with the expected `Content-Type`.
  - Jar-contents tests (mandatory, not live HTTP): after `./mill iwDashboardJar`, assert on `build/iw-dashboard.jar`: `META-INF/MANIFEST.MF` `Main-Class: iw.dashboard.ServerDaemon`; classpath entries for `assets/main.js` and `static/dashboard.css` present. These are zip-level assertions, not subprocess launches — they catch fat-jar packaging bugs without needing new `ServerDaemon` CLI args.
  - Tailwind `@source` assertion is a manual spot-check (see Testing strategy), not an itest case.
  - If time permits: a subprocess smoke test that `java -jar build/iw-dashboard.jar` boots with an existing server config (no new CLI args) and does not `ClassNotFoundException` at startup. Skip entirely if it requires adding a `--port` flag or scraping stdout for a bound port — those changes are Phase 4.
- **`.iw/commands/test.scala` extension**: new `itest` stage invoking `./mill dashboard.itest.test`; `all` stage adds itest between unit and e2e. Bare `./iw ./test` still runs unit + itest + e2e (not compile — compile is a separate explicit stage today).
- **CI additions (`.github/workflows/ci.yml`)**:
  - New `dashboard-build` job, `needs: compile`, same `ghcr.io/iterative-works/iw-cli-ci:latest` container.
  - `env: WEBAWESOME_NPM_TOKEN: ${{ secrets.WEBAWESOME_NPM_TOKEN }}` exposed at job level.
  - Steps: checkout, `./mill dashboard.assembly` (produces `build/iw-dashboard.jar`), `./mill dashboard.itest.test` (integration tests run against the assembled jar).
  - `test` job (`./iw ./test`) continues to run unit + itest + e2e. `itest` overlaps with `dashboard-build`; decision: accept the duplication so local `./iw ./test` gives full coverage and `dashboard-build`'s failure attribution stays clean. CI runs them in parallel, not sequentially.
- **`.gitignore` additions**:
  - `dashboard/frontend/node_modules/`
  - `dashboard/frontend/dist/`
  - `dashboard/frontend/.yarn/` (Yarn 4 internal cache/state; do not commit)

### Out of scope — deferred to Phase 4

- Rewriting `commands/dashboard.scala` and `commands/server-daemon.scala` to spawn `java -jar build/iw-dashboard.jar`. Those two scripts still launch via the in-process scala-cli path at end of Phase 3. The `build/iw-dashboard.jar` artifact exists and passes its own itests, but `iw dashboard` / `iw server-daemon` do not yet consume it.
- **Any change to `ServerDaemon`'s CLI surface** — no new flags (`--port`, `--port 0`, etc.), no new arg parsing, no logging of bound-port lines. The subprocess itest may only invoke `ServerDaemon` with config already parseable today, or must be omitted entirely. Adjusting `ServerDaemon`'s command-line contract is Phase 4 territory per `tasks.md` L4.
- Deleting the transitional scoped `//> using dep` lines on `commands/dashboard.scala` and `commands/server-daemon.scala` (Phase 2 added those; Phase 4 deletes them alongside the launcher rewrite).
- `ensure_dashboard_jar` / `needs_dashboard_rebuild` helpers in `iw-run`, and the launcher-side rebuild gate.
- Double-gated dev mode (`--dev` flag + `VITE_DEV_URL` env var + loopback-host validation), the `assetUrl` template helper, `dashboard/frontend/start-dev.sh`.
- README / top-level CLAUDE.md documentation of the two-build-tool boundary and `WEBAWESOME_NPM_TOKEN` prereq.
- Replacing `iwCoreJar`'s stage-and-repack with `dashboard.assembly` in `iw-run`'s build gate. Phase 3 leaves `iwCoreJar` untouched; Phase 4 retires it.
- Any refactor of the `/static/` route beyond what's needed to make the itest pass — no broader server-side cleanup.
- Launching the assembled jar as a live HTTP server from the itest. Proving the jar boots and responds over HTTP against new CLI args is Phase 4 work. Phase 3 proves the jar's *contents* (manifest, classpath resources, Vite bundle presence) — see the updated acceptance criterion.

## Dependencies

### Prior phases

- **Phase 1 (landed):** `./mill` wrapper, `.mill-version` (1.1.5), `build.mill` with `object core`, Node 20 + Corepack + Mill pre-installed in the CI Docker image (`ghcr.io/iterative-works/iw-cli-ci:latest` via `.github/Dockerfile.ci` lines 44-56). Node and Corepack land here in Phase 3 for the first time — Phase 1 pre-provisioned them deliberately.
- **Phase 2 (landed):** `dashboard/jvm/src/`, `dashboard/jvm/resources/` (contains `static/dashboard.css` + `static/dashboard.js`), `dashboard/jvm/test/src/`, `object dashboard extends ScalaModule` with `moduleDeps = Seq(core)` and its `mvnDeps` (cask 0.11.3, scalatags 0.13.1, scalatags-webawesome 3.2.1.1; flexmark is transitively via core). `iw.dashboard.*` package rename complete across all dashboard sources and tests. `iwCoreJar` is a stage-and-repack that combines core+dashboard classes into `build/iw-core.jar` — **Phase 3 leaves `iwCoreJar` alone**; its replacement by `dashboard.assembly` is a Phase 4 concern (per Phase 2's explicit contract).
- **Phase 2 transitional bridge:** scoped `//> using dep` lines on `commands/dashboard.scala` and `commands/server-daemon.scala` (cask, scalatags, flexmark-all). Phase 3 **does not touch these**. They are a Phase 4 cleanup.

### External

- **`WEBAWESOME_NPM_TOKEN` — already provisioned for Michal.** Must be set (a) locally in the developer's environment before running `./mill frontend.viteBuild`, and (b) in GitHub Actions repo secrets before the new `dashboard-build` job runs. Without it, `yarn install` fails on the Web Awesome Pro package with a 401 from the registry — that's the expected fail-fast.
- **Node 20 + Corepack** — already in the CI image and assumed installed on contributor machines who work on the dashboard. `./mill frontend.viteBuild` will fail fast with a clear error if Node or Corepack are missing.
- **Yarn 4** — not installed system-wide; driven per-project via Corepack reading `package.json`'s `packageManager` field. Contributor's one-time setup is `corepack enable`.
- **Web Awesome Pro 3.2.1** package — fetched from the Web Awesome registry at `yarn install` time using the token. Never committed.
- **scalatags-webawesome 3.2.1.1** (already in `dashboard.mvnDeps` from Phase 2) — generates Scala-side template bindings for Web Awesome components. Compatible with the Pro 3.2.1 runtime components bundled by Vite.

### Coexistence contract (`iwCoreJar` vs `iwDashboardJar` during Phase 3)

For Phase 3's duration, both artifacts exist side-by-side in `build/` with no coupling:

(a) `iwCoreJar` remains the artifact `iw-run` consumes via `ensure_core_jar` → `./mill iwCoreJar` → `build/iw-core.jar`. Every `iw` invocation still goes through this path. `./iw ./test` still runs `iwCoreJar` transitively (via the command-launch gate), and CI's existing `compile` job continues to produce `build/iw-core.jar` exactly as today.

(b) `iwDashboardJar` is a parallel artifact produced only by (i) the new CI `dashboard-build` job and (ii) `dashboard.itest` as a build prerequisite of the jar-contents tests. No existing code path consumes it. No existing CI job calls it.

(c) `build/iw-core.jar` and `build/iw-dashboard.jar` sit in the same directory with no interaction: distinct filenames, no shared intermediate artifacts, no build ordering between them. `./mill iwCoreJar && ./mill iwDashboardJar` produces both; either alone produces only its target. Phase 4 retires `iwCoreJar` and flips the launcher to `iwDashboardJar` atomically.

## Approach

### `dashboard/frontend/package.json` shape

```json
{
  "name": "iw-dashboard-frontend",
  "version": "0.0.0",
  "private": true,
  "type": "module",
  "packageManager": "yarn@4.9.x",
  "scripts": {
    "build": "vite build",
    "dev": "vite"
  },
  "dependencies": {
    "htmx.org": "^2",
    "@web.awesome.me/webawesome-pro": "3.2.1"
  },
  "devDependencies": {
    "vite": "^8",
    "@tailwindcss/vite": "^4",
    "tailwindcss": "^4"
  }
}
```

Note on the `packageManager` string: pin the exact patch version (e.g. `yarn@4.9.2`) when writing the file; Corepack treats a wildcard as a resolution hint rather than a pin, which defeats the reproducibility intent.

Decision: drop `markdown-it` from the initial `package.json`. Analysis §L2 lists it but the dashboard renders Markdown server-side through Flexmark, and Phase 3 does not introduce client-side Markdown rendering. Re-add in a later phase if a specific view requires it.

### `dashboard/frontend/.yarnrc.yml` shape

```yaml
nodeLinker: node-modules
enableGlobalCache: false

npmScopes:
  web.awesome.me:
    npmRegistryServer: "https://registry.webawesome.com"
    npmAlwaysAuth: true
    npmAuthToken: "${WEBAWESOME_NPM_TOKEN}"
```

CLARIFY 1 — the exact registry hostname (`registry.webawesome.com` vs a scoped path or a vendor-specific URL) needs verification against the Web Awesome Pro onboarding docs. Procedures' existing `.yarnrc.yml` (if present in `~/Devel/iw/support-libs` or procedures' repo) is the canonical reference — copy verbatim, adjusted for scope name if the package spec is `@web.awesome.me/webawesome-pro` rather than a Yarn scope.

`nodeLinker: node-modules` is the classic `node_modules/` layout (as opposed to Yarn 4's default PnP). Decision: node-modules — (a) Vite's plugin ecosystem is friendlier to node-modules than PnP, and (b) Mill's Task.Source on `node_modules/` is more predictable than watching a PnP cache.

### `dashboard/frontend/vite.config.js` shape

```js
import { defineConfig } from "vite";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  plugins: [tailwindcss()],
  build: {
    outDir: "dist",
    assetsDir: "assets",
    emptyOutDir: true,
    rollupOptions: {
      input: "src/main.js",
    },
  },
  server: {
    cors: true,
  },
});
```

`emptyOutDir: true` so stale bundles from a previous build don't end up in the resource set. `server.cors: true` is required for Phase 4's dev mode (browser loads assets from `localhost:5173` while dashboard serves HTML from a different port); land it here so Phase 4 has one less thing to add.

### `dashboard/frontend/src/main.js` cherry-picks

```js
import "./main.css";
import "htmx.org";

import "@web.awesome.me/webawesome-pro/dist/components/button/button.js";
import "@web.awesome.me/webawesome-pro/dist/components/tag/tag.js";
import "@web.awesome.me/webawesome-pro/dist/components/icon/icon.js";
import "@web.awesome.me/webawesome-pro/dist/components/input/input.js";
import "@web.awesome.me/webawesome-pro/dist/components/textarea/textarea.js";
import "@web.awesome.me/webawesome-pro/dist/components/tree/tree.js";
import "@web.awesome.me/webawesome-pro/dist/components/tree-item/tree-item.js";
import "@web.awesome.me/webawesome-pro/dist/components/card/card.js";
import "@web.awesome.me/webawesome-pro/dist/components/page/page.js";
```

CLARIFY 2 — the exact import paths for Web Awesome Pro 3.2.1 components (`/dist/components/<name>/<name>.js` is the procedures convention but the Pro package's own export map may be more idiomatic, e.g. a single entry or named exports). Validate at implementation by reading `node_modules/@web.awesome.me/webawesome-pro/package.json`'s `exports` field. The full-kitchen-sink import is explicitly banned — we cherry-pick to keep the bundle small.

CLARIFY 10 — confirm the nine components above against what `dashboard/jvm/src/presentation/views/**` actually uses via scalatags-webawesome. If a view references `wa-dialog` or `wa-switch`, add it; if nothing uses `wa-textarea`, drop it. `rg -n '<wa-[a-z-]+' dashboard/jvm/src/presentation/` at implementation gives the authoritative list.

### `dashboard/frontend/src/main.css` shape

```css
@import "tailwindcss";
@source "../jvm/src/**/*.scala";
```

Tailwind v4 changes the import syntax from `@tailwind base; @tailwind components; @tailwind utilities;` (v3) to a single `@import "tailwindcss"`. The `@source` directive tells Tailwind where to scan for class names — without it, the Scala templates' class strings would never reach Tailwind's class extractor and every utility class would be purged.

CLARIFY 11 — Tailwind v4 `@source` globbing across arbitrary file extensions (`.scala`) may require an explicit `extract` hook or a custom extractor. Tailwind v4 defaults scan HTML-ish files. Test at implementation: build the frontend, grep the resulting `main.css` for a Tailwind utility class known to be used in a Scala template (e.g. `rg -l 'class="[^"]*flex\\b' dashboard/jvm/src/presentation/` yields a template; check whether `.flex {` lands in the output CSS). If the scan misses Scala, add an explicit pattern.

### `build.mill` delta — `frontend` module

Append after the existing `dashboard` module:

```scala
// Frontend build pipeline — yarn + vite + tailwind produce `dist/assets/`
// consumed by `dashboard.resources` before assembly.
object frontend extends Module {

  def frontendDir = Task.Sources(
    moduleDir / os.up / "dashboard" / "frontend" / "package.json",
    moduleDir / os.up / "dashboard" / "frontend" / "yarn.lock",
    moduleDir / os.up / "dashboard" / "frontend" / "vite.config.js",
    moduleDir / os.up / "dashboard" / "frontend" / "src",
    moduleDir / os.up / "dashboard" / "frontend" / ".yarnrc.yml",
  )

  // Tailwind @source scans Scala sources; changes there must re-trigger viteBuild.
  def scalaTemplates = Task.Sources(
    moduleDir / os.up / "dashboard" / "jvm" / "src"
  )

  def viteBuild: T[PathRef] = Task {
    val srcDir = moduleDir / os.up / "dashboard" / "frontend"
    frontendDir()
    scalaTemplates()
    // Run yarn install + yarn build with inherited env (WEBAWESOME_NPM_TOKEN).
    os.proc("yarn", "install", "--immutable").call(cwd = srcDir, stdout = os.Inherit, stderr = os.Inherit)
    os.proc("yarn", "build").call(cwd = srcDir, stdout = os.Inherit, stderr = os.Inherit)
    // Copy dist/ into Task.dest so Mill owns it and change-detection is correct.
    val out = Task.dest / "dist"
    os.copy(srcDir / "dist", out, replaceExisting = true, createFolders = true)
    PathRef(out)
  }
}
```

Including `scalaTemplates()` as a `Task.Sources` input means Scala edits re-trigger `viteBuild`. Accepted as the correctness-first choice for Phase 3; Phase 4's dev mode (Vite HMR) sidesteps the feedback-loop cost.

`os.Inherit` for stdout/stderr on `os.proc(...).call` is the procedures-style approach for Mill 1.1.x; fail forward if the 1.1.x idiom has shifted.

### `build.mill` delta — fold vite output into `dashboard.resources`

The current `dashboard` module declares `def resources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "resources")`. Override it to also include `frontend.viteBuild()`:

```scala
object dashboard extends ScalaModule {
  // ... existing definitions ...

  def resources = Task {
    val jvmResources = PathRef(moduleDir / os.up / "dashboard" / "jvm" / "resources")
    val viteAssets = frontend.viteBuild()
    Seq(jvmResources, viteAssets)
  }

  // ... existing test module ...

  object itest extends ScalaTests with TestModule.Munit {
    def sources = Task.Sources(moduleDir / os.up / "dashboard" / "jvm" / "itest" / "src")
    def mvnDeps = Seq(
      mvn"org.scalameta::munit::1.2.1",
      mvn"com.softwaremill.sttp.client4::core:4.0.15",
    )
    // itest needs the production classes to launch CaskServer; inherited from enclosing dashboard module.
  }
}
```

The `def resources` override returns `Seq[PathRef]` (Mill 1.1.x multi-source signature); adapt if the 1.1.x API expects `Task.Sources`. The invariant: both trees land at the classpath root of the assembled jar so Vite's `dist/assets/main.js` is accessible via `getResourceAsStream("/assets/main.js")`.

`object itest` as an inner module of `dashboard` inherits `runClasspath` (including `resources`) per standard Mill 1.1.x `ScalaTests` semantics — no explicit wiring needed.

### `build.mill` delta — `dashboard.assembly` wrapper

```scala
// Materialises the dashboard fat jar at `build/iw-dashboard.jar` for launcher discoverability.
// Phase 4 will replace `iwCoreJar`'s stage-and-repack with this artifact and rewrite the launcher.
def iwDashboardJar(): Command[PathRef] = Task.Command {
  val target = dashboard.moduleDir / os.up / "build" / "iw-dashboard.jar"
  os.makeDir.all(target / os.up)
  os.copy.over(dashboard.assembly().path, target)
  PathRef(target)
}
```

`dashboard.assembly` is stock `ScalaModule.assembly` — Mill produces a fat jar with all compile + runtime deps on the classpath. The wrapper is the cosmetic copy that matches Phase 1's `iwCoreJar()` pattern so launchers (and Phase 4's `ensure_dashboard_jar`) have a stable on-disk path.

CLARIFY 14 — Mill's default `assembly` may need a `mainClass` override so `java -jar build/iw-dashboard.jar` works without `--main-class`. The natural main is `iw.dashboard.ServerDaemon` (per `ProcessManager.scala:115`'s string literal). Add `def mainClass = Some("iw.dashboard.ServerDaemon")` on the `dashboard` module if `assembly` leaves the `Main-Class` manifest entry blank.

### `CaskServer.scala` — static-asset classpath resolution

The current `/static/:filename` handler (lines 609-639) resolves files from `IW_CORE_DIR/dashboard/resources/static/` on the filesystem. That path does not exist inside the assembled jar. Minimum change: replace the body with a `getResourceAsStream("/static/$filename")` lookup, preserving the Content-Type dispatch and 404 semantics. This keeps the PageLayout references `/static/dashboard.css` and `/static/dashboard.js` (`dashboard/jvm/src/presentation/views/PageLayout.scala:44-46`) working both from filesystem (dev) and classpath (jar).

Route layout decision: keep two route prefixes. `/static/*` keeps resolving the pre-existing `dashboard.css`/`dashboard.js` (from `dashboard/jvm/resources/static/`). A new `/assets/*` handler resolves via `getResourceAsStream("/assets/$filename")`, hitting the Vite output embedded at classpath root by `dashboard.resources`. Two handlers cost ~10 lines but leave existing templates untouched.

### `dashboard.itest` — what the integration tests cover

Minimum test set, in `dashboard/jvm/itest/src/`:

1. **In-process Cask launch test** (`CaskServerItest.scala`): start CaskServer on a pre-picked high port with retry on `BindException`, `GET /` returns 200 with `Content-Type: text/html`, the body contains `<html` and references `/static/dashboard.css` and `/assets/main.js` (both paths — proves both handlers work). No change to `CaskServer.start`'s signature; the test loops on `BindException` until a free port is found. This keeps `CaskServer` untouched beyond the `/static/` handler change.
2. **Bundled asset resolution test**: from the same in-process server, `GET /assets/main.js` returns 200 with `Content-Type: application/javascript`, non-empty body. `GET /assets/<bundled-css>` similar. (The exact bundled filenames depend on Vite's hashing config; use a prefix match or configure Vite for stable names.)
3. **Jar-contents assertions**: after the test's `@BeforeAll`-equivalent runs `./mill iwDashboardJar` (or the test framework's build-dep mechanism ensures it), open `build/iw-dashboard.jar` as a zip from test code: `META-INF/MANIFEST.MF` contains `Main-Class: iw.dashboard.ServerDaemon`; the zip directory lists `assets/main.js`, at least one `assets/*.css`, and the pre-existing `static/dashboard.css` + `static/dashboard.js`. These are classpath-shape checks, not live HTTP.

Explicitly not in Phase 3:
- Launching `build/iw-dashboard.jar` as a subprocess and hitting it over HTTP. That requires either (a) `ServerDaemon` accepting a new `--port` flag and logging the bound port, or (b) scraping an already-emitted port line from stdout. Both are `ServerDaemon` CLI surface changes and belong in Phase 4.
- The Tailwind `@source` scan assertion. Done as a manual spot-check in Testing strategy instead.

### `.iw/commands/test.scala` extension

Add an `itest` case to the match and to the `all` flow:

```scala
case "itest" =>
  val result = runIntegrationTests()
  sys.exit(if result then 0 else 1)
case "all" =>
  val unitResult = runUnitTests()
  val itestResult = runIntegrationTests()
  val compileResult = runCommandCompileCheck()
  val e2eResult = runE2ETests()
  sys.exit(if unitResult && itestResult && compileResult && e2eResult then 0 else 1)
```

```scala
def runIntegrationTests(): Boolean =
  val installDir = os.Path(System.getenv("IW_INSTALL_DIR"))
  Output.section("Running Dashboard Integration Tests")
  val millCommand = Seq((installDir / "mill").toString, "dashboard.itest.test")
  ProcessAdapter.runStreaming(millCommand) == 0
```

Update `showUsage` to list `itest` alongside `unit`/`compile`/`e2e`.

### CI workflow additions (`.github/workflows/ci.yml`)

Append after the `test` job:

```yaml
  dashboard-build:
    needs: compile
    runs-on: self-hosted
    container:
      image: ghcr.io/iterative-works/iw-cli-ci:latest
      credentials:
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}
    env:
      WEBAWESOME_NPM_TOKEN: ${{ secrets.WEBAWESOME_NPM_TOKEN }}
    steps:
      - uses: actions/checkout@v4
      - name: Assemble dashboard jar
        run: ./mill iwDashboardJar
        timeout-minutes: 15
      - name: Run dashboard integration tests
        run: ./mill dashboard.itest.test
        timeout-minutes: 15
```

CLARIFY 16 — `WEBAWESOME_NPM_TOKEN` must be added to GitHub repo secrets before this job can run green. Phase 3 implementation should coordinate with repo-admin (Michal) to provision the secret in the same PR as the workflow change, or as a pre-PR step. Without it, the job fails at `yarn install` — clean failure, not silent.

Note on CI itest duplication: `test` runs `./iw ./test`, which calls `./mill dashboard.itest.test` transitively, and `dashboard-build` runs it directly. Accepted — `./iw ./test` stays useful locally, and CI parallelism means `dashboard-build` runs alongside `test`, not after, so the wall-clock cost is only the job-startup overhead.

## Files to modify or create

### New files

- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/package.json`
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/yarn.lock` (generated by first `yarn install`, committed)
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/.yarnrc.yml`
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/vite.config.js`
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/src/main.js`
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/frontend/src/main.css`
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/itest/src/CaskServerItest.scala` (or split across 2-3 files by concern)

### Modified files

- `/home/mph/Devel/iw/iw-cli-IW-345/build.mill` — new `object frontend extends Module` with `viteBuild`; `dashboard.resources` override pulling in `frontend.viteBuild()`; `object itest extends ScalaTests` inner module on `dashboard`; `def mainClass = Some("iw.dashboard.ServerDaemon")` on `dashboard` (pending CLARIFY 14 on whether the override is strictly needed); new top-level `iwDashboardJar()` `Task.Command` copying `dashboard.assembly()` to `build/iw-dashboard.jar`.
- `/home/mph/Devel/iw/iw-cli-IW-345/dashboard/jvm/src/CaskServer.scala` — `/static/:filename` handler switched from filesystem to classpath resolution; new `/assets/:filename` handler serves the Vite output.
- `/home/mph/Devel/iw/iw-cli-IW-345/.iw/commands/test.scala` — new `itest` case in match + `runIntegrationTests()` helper + updated `showUsage`.
- `/home/mph/Devel/iw/iw-cli-IW-345/.github/workflows/ci.yml` — new `dashboard-build` job exposing `WEBAWESOME_NPM_TOKEN`.
- `/home/mph/Devel/iw/iw-cli-IW-345/.gitignore` — `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`, `dashboard/frontend/.yarn/`.

### Intentionally untouched

- `iw-run` — launcher-side rebuild gate for the dashboard jar is Phase 4.
- `commands/dashboard.scala`, `commands/server-daemon.scala` — Phase 4 rewrites.
- `iwCoreJar()` in `build.mill` — remains the transitional bridge until Phase 4 retires it.
- `core/project.scala` — no changes in Phase 3.
- `core/adapters/ProcessManager.scala` — no changes in Phase 3.
- Top-level `README.md`, `CLAUDE.md` — documentation updates are Phase 4.

## Testing strategy

- **Frontend build (direct):** `./mill frontend.viteBuild` from a clean tree (with `WEBAWESOME_NPM_TOKEN` set in env) produces `out/frontend/viteBuild.dest/dist/` containing `assets/main.js` and `assets/*.css` (exact filenames depend on Vite hashing). `yarn install --immutable` succeeds on first run; second run is a near-no-op.
- **Frontend build (failure mode):** unset `WEBAWESOME_NPM_TOKEN` and confirm `./mill frontend.viteBuild` fails with a 401/auth error from `yarn install`. This is the contributor onboarding failure to document in Phase 4's README work; for Phase 3, verifying the failure mode suffices.
- **Assembly:** `./mill iwDashboardJar` produces `build/iw-dashboard.jar`. `unzip -l build/iw-dashboard.jar | grep -E "(assets|static)"` shows both the Vite output (`assets/main.js`, `assets/*.css`) and the pre-existing resources (`static/dashboard.css`, `static/dashboard.js`).
- **Assembly main class:** `unzip -p build/iw-dashboard.jar META-INF/MANIFEST.MF | grep Main-Class` prints `iw.dashboard.ServerDaemon`. `java -jar build/iw-dashboard.jar --help` (or equivalent no-op invocation) exits without `NoSuchMethodError` / `ClassNotFoundException`.
- **Integration tests:** `./mill dashboard.itest.test` runs green. All mandatory tests described in "`dashboard.itest` — what the integration tests cover" pass: in-process Cask launch, bundled asset resolution, jar-contents assertions.
- **Tailwind `@source` manual spot-check:** after `./mill frontend.viteBuild`, run `rg -o 'class="[^"]*\bflex\b' dashboard/jvm/src/presentation/` to find a Scala template using `flex`, then grep the Vite-built CSS in `out/frontend/viteBuild.dest/dist/assets/` for `.flex{` (or the Tailwind-compiled form). This is the canonical verification that the `@source` directive picks up Scala sources. Document the outcome in the implementation log; do not automate into itest.
- **Regression — unit tests:** `./mill dashboard.test` still green (Phase 2 tests unaffected). `scala-cli test core/` still green. `./iw ./test unit` covers both.
- **Regression — commands:** `scala-cli compile commands/` still succeeds (the transitional `//> using dep` lines on `dashboard.scala` / `server-daemon.scala` are untouched).
- **Regression — in-process dashboard launch:** `./iw dashboard` still launches the in-process scala-cli path end to end (Phase 4 flips this to `java -jar`). Hitting `/` returns HTML with `<html` present.
- **Blocking grep invariants:**
  - `rg -n 'WEBAWESOME_NPM_TOKEN' dashboard/frontend/` — matches exactly in `.yarnrc.yml` as `${WEBAWESOME_NPM_TOKEN}`, never as a literal value.
  - `rg -n 'webawesome-pro' dashboard/frontend/src/` — matches at least one cherry-picked component import.
  - `unzip -l build/iw-dashboard.jar | grep -c 'assets/'` — greater than zero.
- **Clean-clone reproducibility:** wipe `out/`, `build/iw-dashboard.jar`, `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`. With `WEBAWESOME_NPM_TOKEN` set, `./mill iwDashboardJar && ./mill dashboard.itest.test` runs green end to end.
- **CI green:** all five jobs (`compile`, `format`, `lint`, `test`, `dashboard-build`) pass on the PR. `dashboard-build` is the new job; the other four continue to pass unchanged.
- **Pre-push hook:** unchanged hook (format + scalafix + `-Werror` compile + unit tests + command compilation) passes. Note: the hook currently does **not** run itest or frontend build — that's deliberate (frontend build is expensive; itest needs assembled jar). Phase 3 does **not** add either to pre-push. CI carries the itest signal.

## Acceptance criteria

- [ ] `dashboard/frontend/package.json` declares `packageManager: "yarn@4.9.x"` (exact patch pinned), Vite ^8, Tailwind v4 via `@tailwindcss/vite`, Web Awesome Pro 3.2.1, htmx 2.
- [ ] `dashboard/frontend/yarn.lock` committed.
- [ ] `dashboard/frontend/.yarnrc.yml` authenticates the Web Awesome scope via `${WEBAWESOME_NPM_TOKEN}`; no literal token.
- [ ] `dashboard/frontend/src/main.js` cherry-picks Web Awesome components; no full-bundle import.
- [ ] `dashboard/frontend/src/main.css` uses `@import "tailwindcss"` + `@source "../jvm/src/**/*.scala"`.
- [ ] `./mill frontend.viteBuild` succeeds from a clean tree with `WEBAWESOME_NPM_TOKEN` set; output contains `dist/assets/main.js` + CSS.
- [ ] `./mill frontend.viteBuild` fails fast with a clear auth error when `WEBAWESOME_NPM_TOKEN` is unset.
- [ ] `build.mill` declares a `frontend` module with a `viteBuild: T[PathRef]` task; `dashboard.resources` folds `frontend.viteBuild()` into its result; `dashboard.mainClass` is `iw.dashboard.ServerDaemon` (if needed per CLARIFY 14); `iwDashboardJar()` copies the assembly to `build/iw-dashboard.jar`.
- [ ] `./mill iwDashboardJar` produces `build/iw-dashboard.jar`; `unzip -l` shows both Vite assets (`assets/*`) and pre-existing resources (`static/*`).
- [ ] `build/iw-dashboard.jar` jar-contents assertions pass: `META-INF/MANIFEST.MF` has `Main-Class: iw.dashboard.ServerDaemon`; `unzip -l` shows `assets/main.js`, a Vite CSS bundle under `assets/`, and the pre-existing `static/dashboard.css` + `static/dashboard.js`. (No requirement to launch the jar as a subprocess and hit HTTP; `ServerDaemon` CLI-arg changes are Phase 4.)
- [ ] `dashboard/jvm/itest/src/` contains munit tests covering: in-process `GET /` returns 200 with HTML referencing `main.js` and `dashboard.css`; `/assets/main.js` resolves from classpath with correct `Content-Type`; the jar-contents assertions above.
- [ ] `./mill dashboard.itest.test` runs all itest cases green from a clean tree.
- [ ] `CaskServer.scala`'s `/static/:filename` handler resolves files via `getResourceAsStream` (classpath) rather than from `IW_CORE_DIR`; new `/assets/:filename` handler serves the Vite output from classpath.
- [ ] `.iw/commands/test.scala` exposes an `itest` sub-command and includes it in the default `all` run. `./iw ./test itest` runs `./mill dashboard.itest.test`. `./iw ./test` (no arg) runs unit + itest + compile + e2e.
- [ ] `.github/workflows/ci.yml` has a `dashboard-build` job, `needs: compile`, exposing `WEBAWESOME_NPM_TOKEN` via `env:`, running `./mill iwDashboardJar` + `./mill dashboard.itest.test`. Other four jobs unchanged.
- [ ] `WEBAWESOME_NPM_TOKEN` provisioned as a GitHub Actions repo secret (coordinated with Michal — action item, not a code criterion).
- [ ] `.gitignore` has entries for `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`, `dashboard/frontend/.yarn/`.
- [ ] All 141+ existing unit tests (core + Phase 2 migrated dashboard.test) still pass.
- [ ] `./iw dashboard` in-process launch still works end to end (Phase 4 cutover has not happened yet).
- [ ] All Phase 2 transitional elements are preserved: `iwCoreJar` stage-and-repack intact; `commands/dashboard.scala` + `commands/server-daemon.scala` scoped `//> using dep` lines untouched; `core/adapters/ProcessManager.scala:115` string literal untouched.
- [ ] No CLAUDE.md rule violations: new files open with `// PURPOSE:` header, no temporal/historical comment wording, no emoji in code.

## Risks

- **Web Awesome Pro registry auth config gets the hostname or scope wrong.** Symptom: `yarn install` fails with 404 for the Pro package. Mitigation: copy the registry config from procedures verbatim at implementation time; test locally before committing. Resolution lives in CLARIFY 1.
- **Tailwind v4 `@source` does not scan `.scala` files by default.** Symptom: Vite-built CSS omits all Tailwind utilities used only in Scala templates; dashboard renders unstyled. Mitigation: test explicitly per CLARIFY 11. If Tailwind needs an extract hook, add it to `main.css` or `vite.config.js`.
- **Mill `dashboard.resources` override shape is wrong for 1.1.x.** Symptom: the Vite output is not on the assembled jar's classpath root; `/assets/main.js` returns 404 even when the file exists in `out/frontend/viteBuild.dest/`. Mitigation: verify via `unzip -l build/iw-dashboard.jar | grep assets/` after first assembly; iterate on the override until the files land at the expected path. Resolution at implementation — Mill 1.1.x docs + procedures reference.
- **`dashboard.assembly` merges resource trees with filename collisions.** The existing `dashboard/jvm/resources/static/dashboard.css` and a hypothetical Vite `dist/static/something.css` would collide if Vite ever outputs to `static/` — our config outputs to `assets/` to avoid this, but any future Vite config change must preserve the separation.
- **Cask classpath resource resolution vs `getResource` behaviour across JARs.** If `CaskServer` is loaded by a different classloader than the one that embeds the Vite assets (unlikely with `java -jar`, more likely in a nested classloader scenario), `getResourceAsStream` returns null. Mitigation: use `this.getClass.getResourceAsStream` (tied to the loading classloader); avoid `ClassLoader.getSystemClassLoader` unless required.
- **`yarn install --immutable` requires the lockfile to be up to date.** If a contributor bumps a dep without refreshing `yarn.lock`, CI's `--immutable` rejects the install. Mitigation: pre-commit hook could run `yarn install` locally before committing `package.json` changes — but that's out of scope for Phase 3. Document the invariant in Phase 4's README work.
- **`dashboard-build` CI duplication with `test` job.** itest runs in both jobs. Accepted for fast attribution; must be documented so nobody tries to "optimise" it by removing the itest step from `./iw ./test`.
- **`WEBAWESOME_NPM_TOKEN` leak in logs.** Yarn 4 masks token-containing URLs in its output, but the token itself must never be echoed. Mitigation: `echo "$WEBAWESOME_NPM_TOKEN"` must not appear anywhere in build scripts or workflow files; `.yarnrc.yml` uses the env-var substitution syntax `${WEBAWESOME_NPM_TOKEN}`.

### Deviation from Phase 2 contract

Phase 2's `implementation-log.md` contract-for-next-phases states: "Phase 3 should replace the `iwCoreJar` stage-and-repack with `dashboard.assembly`." This context document defers that replacement to Phase 4. Concrete reason: `iw-run`'s `ensure_core_jar` invokes `./mill iwCoreJar` to produce `build/iw-core.jar`, which every `iw` command (including `./iw ./test`) consumes. Deleting the `iwCoreJar` Mill task without simultaneously rewriting `iw-run` to call `./mill iwDashboardJar` instead breaks every `iw` invocation — `ensure_core_jar` would fail, and no command could launch. The launcher-rewrite and the `iwCoreJar` retirement are a single atomic change; splitting them across phase boundaries is what forces the deferral.

Risk during Phase 3: `iwCoreJar` and `iwDashboardJar` coexist as two uncoordinated assembly pipelines. Mitigation: the coexistence contract (in Dependencies) explicitly carves out who produces what. Phase 4's first task is a single PR that (i) deletes `iwCoreJar`, (ii) points `iw-run` at `iwDashboardJar`, (iii) removes the Phase 2 transitional `//> using dep` lines from `commands/dashboard.scala` and `commands/server-daemon.scala`.

## Open CLARIFYs carried into implementation

Decisions for other items are stated inline in Approach; only genuine unknowns remain here.

| # | Topic | Recommendation |
|---|-------|----------------|
| 1 | Web Awesome Pro registry hostname / scope name in `.yarnrc.yml` | Copy from procedures verbatim; verify against Pro onboarding docs |
| 2 | Exact Web Awesome import paths (`/dist/components/...` vs export-map) | Validate at implementation by reading the installed package's `exports` field |
| 10 | Cherry-pick list of Web Awesome components | Validate via `rg -n '<wa-[a-z-]+' dashboard/jvm/src/presentation/` |
| 11 | Tailwind v4 `@source` scanning `.scala` files | Test at implementation; add explicit extract if needed |
| 14 | `dashboard.mainClass = Some("iw.dashboard.ServerDaemon")` needed or inferred | Add explicitly — assembly `Main-Class` manifest must be non-blank for `java -jar` |
| 16 | `WEBAWESOME_NPM_TOKEN` provisioned in GitHub Actions secrets | Coordinate with Michal (repo admin) — action item, not code |

## Notes for reviewer

Phase 3 produces the on-disk artifact `build/iw-dashboard.jar` and proves it works via integration tests — but it does **not** switch the user-facing `iw dashboard` launcher to consume it. That cutover is Phase 4. Reviewer attention should focus on:

1. **Resource pipeline correctness.** `frontend.viteBuild()` output lands at the classpath root of `build/iw-dashboard.jar`. The itest asserts this two ways: in-process via `getResourceAsStream` over HTTP, and zip-level by opening the jar and listing entries. Reviewer spot-check: `unzip -l build/iw-dashboard.jar | head -40` confirms both `assets/` (Vite) and `static/` (pre-existing) prefixes are present.
2. **Registry-auth hygiene.** `WEBAWESOME_NPM_TOKEN` appears exactly once in source (`.yarnrc.yml`, as an env-var substitution), once in the CI workflow (`env:` exposure), and nowhere else. No literal token, no echo, no leak path.
3. **Cherry-picked Web Awesome components match what templates actually use.** The whole point of cherry-picking is to keep the bundle small; a drift between `main.js` imports and `<wa-*>` tag usage in Scala templates produces silent visual bugs (unregistered custom elements render as plain HTML). CLARIFY 10's grep is the canonical reconciliation.
4. **`iwCoreJar` is preserved, not replaced.** Phase 2's contract said Phase 3 *should* replace it; this context document defers that to Phase 4 because the launcher rewrite and the builder retirement should land together. If the reviewer wants the retirement in Phase 3, pull it forward — but be aware it couples Phase 3 to `iw-run` changes that were explicitly Phase 4 territory.
5. **Integration tests prove the assembled jar's shape, not a running subprocess.** The jar-contents assertions (manifest main-class, classpath entries for `assets/main.js` + `static/dashboard.css`) are the acceptance-level evidence that Phase 3 produced a correctly-packaged artifact. Live HTTP against a subprocess `java -jar` is Phase 4 territory because it requires `ServerDaemon` CLI-arg changes.
6. **CLARIFYs above are explicit.** Implementation may resolve them differently from the recommendations — that's fine, but each resolution should be called out in the implementation log so Phase 4 inherits a clear picture.

No Phase 4 work creeps into Phase 3: no `iw-run` helpers, no launcher rewrites, no dev-mode gating, no README documentation. If any of those arrive in the diff, they belong in Phase 4.
