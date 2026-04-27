# Technical Analysis: Layer 0 — Set up Mill module for dashboard with Vite + Tailwind + Web Awesome

**Issue:** IW-345
**Created:** 2026-04-20
**Status:** Draft

## Problem Statement

The iw-cli dashboard is currently compiled alongside the rest of `core/` via scala-cli. That works for a server-rendered HTML UI assembled from scalatags, but it cannot drive a modern frontend pipeline: there is no bundler, no CSS toolchain, and no integration point for component libraries like Web Awesome.

The parent effort (#343) repositions the dashboard as the primary control plane for iw-cli. To get there, the dashboard needs Vite (asset bundling + HMR), Tailwind CSS v4 (scanning Scala sources for class names), and Web Awesome components (via the `scalatags-webawesome` bridge). Scala-cli has no ergonomic story for orchestrating a JS toolchain, and Mill does — procedures already demonstrates the pattern.

This layer is pure build infrastructure: no user-visible feature changes. The deliverable is a `build/iw-dashboard.jar` that behaves like the current in-process dashboard, but is produced by Mill, has Vite-bundled assets baked into its resources, and supports a dev mode with Vite HMR. It depends on the pre-compiled `build/iw-core.jar` produced by the already-merged IW-344.

## Proposed Solution

### High-Level Approach

Introduce Mill as a second build tool, living side-by-side with scala-cli. Scala-cli keeps owning `core/` and the command scripts; Mill owns only the dashboard. A `build.mill` file at the repo root defines two modules: a frontend module wrapping the Vite build, and a JVM ScalaModule for the dashboard server. The JVM module consumes `build/iw-core.jar` as an unmanaged dependency, compiles the dashboard server code (moved out of `core/dashboard/`), and assembles a fat jar with the vite-built assets embedded as resources.

The `commands/dashboard.scala` and `commands/server-daemon.scala` launchers shift from calling `CaskServer.start` in-process (via the scala-cli classpath) to spawning `java -jar build/iw-dashboard.jar`. A rebuild gate (mirroring `ensure_core_jar` in `iw-run`) keeps the jar in sync with sources during development. Dev mode is opt-in: when a `VITE_DEV_URL` env var is set, the server emits HTML that points at the Vite dev server; otherwise it serves bundled assets from the classpath.

### Why This Approach

- **Mill is the least-disruptive tool that solves the JS integration problem.** We already have a reference implementation in procedures, and Mill's task graph handles the vite → resources → assembly pipeline naturally.
- **Two-tool cohabitation is cheap for now.** Only the dashboard needs Mill; keeping scala-cli for `core/` and command scripts avoids re-platforming every script at once. IW-346 will revisit packaging.
- **Fat jar plus launcher isolates the dashboard process.** This matches how #343 wants the dashboard to evolve (independent process, separate lifecycle), and it avoids classpath conflicts between the command scripts and dashboard deps.
- **Cask stays.** Migrating to Tapir/Netty is explicitly out of scope; swapping HTTP servers while also introducing Mill + Vite + Tailwind + Web Awesome would compound risk.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### L0 — Mill Bootstrap + Core Jar Production

**Components:**
- `build.mill` at repo root with a top-level `core` Mill module that compiles `core/**/*.scala` and produces `build/iw-core.jar` (thin jar, compiled classes only — transitive deps resolved at consumer-side). Mill version 1.1.5; `mvnDeps` for core's runtime needs: `config 1.4.5`, `sttp-client4 4.0.15`, `upickle 4.4.2`, `os-lib 0.11.6`. (The dashboard-only deps — cask, scalatags, flexmark — migrate out of core in L1.)
- `./mill` launcher script (downloads Mill 1.1.5 on first run), `.mill-version` pinning the version, `.gitignore` entry for `out/`.
- `iw-run` update: `build_core_jar()` switches from `scala-cli package ...` to `./mill core.jar` (or equivalent). `ensure_core_jar()` / `needs_rebuild()` mtime gate is preserved; only the underlying builder command changes.
- `core/project.scala` keeps its `//> using dep` directives — they resolve the runtime classpath for every scala-cli command invocation. Dep list must stay in sync with `build.mill`'s core `mvnDeps`; add a sync-comment in both files.
- CI Dockerfile additions (`.github/Dockerfile.ci`): pre-download Node 20 tarball + install to `/opt/node`, `corepack enable`, pre-download Mill 1.1.5 binary to `/usr/local/bin/mill`, extend the verification line.

**Responsibilities:**
- Make Mill callable in the repo for both developers (via wrapper) and CI (via pre-installed binary in the Docker image).
- Be the single source of truth for compiling core — no duplication with scala-cli.
- Preserve the existing `build/iw-core.jar` contract so scala-cli command scripts are unaffected.

**Estimated Effort:** 3-5 hours
**Complexity:** Moderate (flips IW-344's builder and adds CI image work)

---

### L1 — Dashboard JVM Module

**Components:**
- `dashboard/jvm/src/` — Scala sources relocated from `core/dashboard/**` (CaskServer, StateRepository, DashboardService, ArtifactService, GitStatusService, ServerDaemon, and `application/`, `domain/`, `infrastructure/`, `presentation/`, `resources/` sub-packages). **Package rename `iw.core.dashboard.*` → `iw.dashboard.*`** across all 89 dashboard files (package declarations + internal imports). Mechanical `sed`/`rg` pass; post-rename grep `rg 'iw\.core\.dashboard' --type scala` must return zero.
- `dashboard/jvm/test/src/` — ~20 dashboard-touching tests migrated from `core/test/` (`PageLayoutTest`, `MainProjectsViewTest`, `WorkflowProgressServiceTest`, `WorktreeCardServiceTest`, `SampleDataTest`, `ReviewStateServiceTest`, `StaticFilesTest`, and others). Imports updated to `iw.dashboard.*`.
- `dashboard` ScalaModule in `build.mill` with Scala 3.3.7 and `moduleDeps = Seq(core)` — native Mill change-detection across core → dashboard.
- Dependency wiring on the dashboard module: `cask 0.11.3`, `scalatags 0.13.1`, `works.iterative::scalatags-webawesome:3.2.1.1`, `flexmark-all 0.64.8`. These deps **move out of** `core/project.scala` as part of this phase — core shrinks to its actual responsibilities.
- `object test extends ScalaTests with TestModule.Munit` on the dashboard module for the migrated unit tests.
- Updates to the 3 external `iw.core.dashboard` references: `core/adapters/ProcessManager.scala:115` (string-literal FQCN), `commands/dashboard.scala`, `commands/server-daemon.scala`. (Those two command scripts are fully rewritten in L4; here we only keep them compiling during the transition.)

**Responsibilities:**
- Compile the dashboard server code as a standalone Mill module.
- Preserve all ~20 dashboard-related unit tests; no coverage loss.
- Narrow core's dep surface by relocating dashboard-only deps.

**Estimated Effort:** 4-7 hours
**Complexity:** Moderate (directory move + 89-file rename + test migration + dep cleanup, all in one cohesive change)

---

### L2 — Frontend Pipeline

**Components:**
- `dashboard/frontend/package.json`:
  - `"packageManager": "yarn@4.9.x"` (current stable at implementation time) — Corepack pins Yarn 4 per project.
  - Dependencies: `vite ^8`, `@tailwindcss/vite ^4`, `tailwindcss ^4`, `htmx.org ^2`, `markdown-it ^14`, `@web.awesome.me/webawesome-pro:3.2.1`.
- `dashboard/frontend/yarn.lock` — committed for reproducibility.
- `dashboard/frontend/.yarnrc.yml` — Yarn 4 native registry config including Web Awesome Pro registry auth. References `WEBAWESOME_NPM_TOKEN` via env-var substitution; token value never committed.
- `dashboard/frontend/vite.config.js` — Tailwind plugin, entry at `src/main.js`, output to `dist/assets/`, `server: { cors: true }` to allow cross-origin dev loads.
- `dashboard/frontend/src/main.js` — cherry-picked Web Awesome components (button, tag, icon, input, textarea, tree, tree-item, card, page), plus htmx and main.css import.
- `dashboard/frontend/src/main.css` — `@import "tailwindcss"` + `@source "../jvm/src/**/*.scala"` so Tailwind scans Scala templates for class names.
- Mill `frontend` module in `build.mill` wrapping `yarn install` and `yarn build` as tasks.
- `.gitignore` additions: `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`.

**Responsibilities:**
- Produce bundled CSS and JS assets via `./mill frontend.viteBuild`.
- Scan Scala sources for Tailwind class names so unused CSS is purged.
- Expose a `viteBuild` task whose output is consumed by L3.

**Estimated Effort:** 2-4 hours
**Complexity:** Moderate (Yarn 4 / Corepack is straightforward; `.yarnrc.yml` registry auth is the main novelty)

---

### L3 — Asset Bundling, Fat Jar, Integration Tests

**Components:**
- Mill task graph wiring: `frontend.viteBuild` output → `dashboard.resources` (override that folds the vite dist directory into the resources set before assembly).
- `dashboard.assembly` producing `build/iw-dashboard.jar` (fat jar with embedded assets). Output-path convention: Mill writes to `out/dashboard/assembly.dest/out.jar`; a post-task copies/symlinks to `build/iw-dashboard.jar` for launcher discoverability.
- Cask route for static assets — verify/add a `getResourceAsStream` serving path for `/assets/*` when running from the assembled jar. Likely a small adjustment to existing `StaticFilesTest` targets.
- **`object itest extends ScalaTests with TestModule.Munit`** on the dashboard module — integration tests under `dashboard/jvm/itest/src/`. Scope: launch the assembled jar or start Cask in-process, hit `GET /` and a handful of key routes, assert 200 + HTML references `/assets/main.js` + the asset resolves from classpath.
- CI: add a new `dashboard-build` job in `.github/workflows/ci.yml` that runs `./mill dashboard.assembly` and `./mill dashboard.itest`. Exposes `WEBAWESOME_NPM_TOKEN` via `env:`.
- `./iw ./test` orchestration update (project-local command at `.iw/commands/test.scala`): add `itest` subcommand that runs `./mill dashboard.itest`; top-level `./iw ./test` runs unit + itest + e2e.

**Responsibilities:**
- Guarantee asset bundling and server compilation are part of the same build artifact.
- Preserve the `build/iw-dashboard.jar` on-disk contract that L4 launchers depend on.
- Exercise the assembled artifact end-to-end via integration tests — no manual QA dependency.

**Estimated Effort:** 3-5 hours
**Complexity:** Moderate (Mill assembly + resource folding is standard; the new itest module is the main addition)

---

### L4 — Command Integration & Dev Mode

**Components:**
- `commands/dashboard.scala` rewritten to spawn `java -jar build/iw-dashboard.jar ...` (foreground, browser-open preserved). Drops direct imports of `iw.core.dashboard.*` types.
- `commands/server-daemon.scala` rewritten to launch the jar in background. Drops the `CaskServer.start` direct call; uses `ProcessBuilder` or existing process-manager helpers.
- `core/adapters/ProcessManager.scala:115` — updated to match the new launch strategy (either removes the `--main-class "iw.core.dashboard.ServerDaemon"` string literal entirely or updates it for the jar-based path).
- `iw-run` helpers `ensure_dashboard_jar` / `needs_dashboard_rebuild` (mirroring `ensure_core_jar` / `needs_rebuild` at `iw-run:29-74`) — invoke Mill when any of `dashboard/jvm/src/**`, `dashboard/frontend/src/**`, or `dashboard/frontend/package.json` is newer than `build/iw-dashboard.jar`.
- **Dev-mode double-gate**: dev asset routing activates only when BOTH (a) `--dev` flag is passed to the dashboard command (`DashboardArgs.devMode` already exists) AND (b) `VITE_DEV_URL` env var is set. Env-var-only is insufficient. On startup, validate `VITE_DEV_URL` scheme is `http://` and host is loopback (`localhost`/`127.0.0.1`); refuse anything else with a clear error. Log loudly when dev mode is active: `⚠ Dev mode: serving assets from <URL>`.
- `assetUrl(path: String): String` template helper — single helper used by every HTML template. In prod: returns `/assets/$path`. In dev (gated): returns `${VITE_DEV_URL}/src/$path` (exact path mapping TBD at implementation against Vite entry config).
- `dashboard/frontend/start-dev.sh` — convenience script to launch `yarn dev` on port 5173 with Tailscale IP detection (ported from procedures).
- Documentation: update `CLAUDE.md` with the two-build-tool boundary; update top-level README to document Node 20+/Yarn 4/Mill 1.1.5/`WEBAWESOME_NPM_TOKEN` prereqs for dashboard development.

**Responsibilities:**
- Preserve the existing launcher UX — `iw dashboard`, `iw server-daemon` keep working from the user's perspective.
- Enable Vite HMR during dashboard development without jar rebuilds per change.
- Trigger dashboard jar rebuilds transparently when Scala or frontend sources change.
- Prevent dev-mode asset proxying from accidentally activating in production.

**Estimated Effort:** 3-5 hours
**Complexity:** Moderate (safety-gating around dev mode is the subtle piece; launch rewriting is mechanical)

---

## Technical Decisions

### Patterns

- **Two build tools cohabiting**: scala-cli for `core/` + `commands/`, Mill for `dashboard/`. Boundary is the jar artifact contract at `build/iw-core.jar` and `build/iw-dashboard.jar`.
- **Fat-jar-per-process**: dashboard runs as a separate JVM, not as an in-process library call from command scripts.
- **Functional core / imperative shell**: existing dashboard sub-packages (`domain/`, `application/`, `infrastructure/`, `presentation/`) are preserved wholesale — this layer only moves files, it does not refactor them.
- **Resource-embedded assets**: Vite output is bundled into the jar's resources rather than shipped as a sidecar directory.

### Technology Choices

- **Build tool**: Mill **1.1.5**, via committed `./mill` launcher + `.mill-version`.
- **Frontend bundler**: Vite 8.
- **CSS**: Tailwind CSS v4 via `@tailwindcss/vite`.
- **Component library**: Web Awesome Pro 3.2.1 via `works.iterative::scalatags-webawesome:3.2.1.1` (Maven Central, MIT licensed).
- **HTTP server**: Cask 0.11.3 (unchanged).
- **Package manager**: **Yarn 4** via Corepack (pinned through `package.json` `packageManager` field). Node 20+.
- **License**: Web Awesome Pro under the existing IW license; `WEBAWESOME_NPM_TOKEN` already provisioned.

### Integration Points

- **Core → dashboard:** Mill `dashboard` module has `moduleDeps = Seq(core)` — Mill compiles both and tracks cross-module changes natively.
- **Core → scala-cli commands:** `build/iw-core.jar` (produced by `./mill core.jar`) consumed by scala-cli via `--jar build/iw-core.jar`. Runtime dep resolution handled by `core/project.scala`'s `//> using dep` directives (kept in sync with `build.mill`'s core `mvnDeps` by manual discipline + sync comment).
- **Frontend → dashboard:** `frontend.viteBuild` output folded into `dashboard` resources before `dashboard.assembly` runs.
- **Jar → launchers:** `build/iw-dashboard.jar` launched by `commands/dashboard.scala` and `commands/server-daemon.scala` via `java -jar`.
- **Rebuild gating:** `iw-run`'s `ensure_core_jar` and new `ensure_dashboard_jar` helpers invoke `./mill core.jar` / `./mill dashboard.assembly` when sources are newer than the jar.
- **Dev mode:** `--dev` flag AND `VITE_DEV_URL` env var together activate asset routing to Vite. Validated scheme + loopback host; logged loudly on startup.

## Technical Risks & Uncertainties

### RESOLVED: Mill version and launcher strategy

**Decision:** Mill **1.1.5** (current release), with a committed `./mill` wrapper script + `.mill-version` file (Option A).

**Rationale:** Zero-install for contributors and CI — the wrapper downloads Mill on first run. Matches procedures' pattern. The `.mill-version` file pins 1.1.5 so everyone resolves the same binary.

**Implication:** `build.mill` uses Mill 1.1.x syntax (`mvnDeps`, `Task.Sources`, `BuildCtx`). L0 adds `./mill`, `.mill-version`, and a `.gitignore` entry for `out/`.

---

### RESOLVED: iw-core.jar consumption pattern

**Decision:** **Mill owns core jar production.** Mill compiles `core/**/*.scala` via a `core` Mill module and produces `build/iw-core.jar` as a thin jar (compiled classes only; transitive deps resolved at consumer-side). Scala-cli commands continue to consume the jar via `--jar build/iw-core.jar` — their runtime classpath is unchanged. The Mill `dashboard` module uses `moduleDeps = Seq(core)`, giving Mill native change-detection across the core → dashboard compile graph.

**What flips from IW-344:**
- `iw-run`'s `build_core_jar()` switches from `scala-cli package ...` to `./mill core.jar` (or equivalent Mill task).
- `iw-run`'s `ensure_core_jar()` / `needs_rebuild()` mtime gate is preserved — only the underlying builder command changes.
- `core/project.scala` keeps its `//> using dep` directives — they resolve the runtime classpath for every scala-cli command.

**Rationale:**
- Single source of truth for compiling core (no two build systems compile the same files).
- Mill's dashboard build knows core changed and rebuilds correctly without external coordination.
- No ivy2Local pollution, no extra publish step.
- Dev-only concern: release-bundled jars are fixed and don't care which tool built them.

**Thin jar trade-off:** Transitive deps (`config`, `sttp`, `upickle`, `os-lib`) must be declared in both `core/project.scala` (for scala-cli runtime) and `build.mill`'s `core` module (for Mill compile). Manual sync is acceptable for now — the list is short (~4 deps after the dashboard-only deps move to L1). Add comments in both files pointing at the other. Revisit with tooling if drift becomes painful. A fat jar would eliminate the duplication but adds several MB to every command invocation; not worth it at this scale.

**Side benefit:** `core/project.scala`'s dashboard-only deps (`cask`, `scalatags`, `flexmark`) move to the dashboard Mill module's `mvnDeps` in L1. Core's footprint shrinks to its actual responsibilities.

**Implication:** Mill becomes a hard dev prerequisite for iw-cli — not just dashboard work, but any core edit. Release consumers are unaffected.

---

### RESOLVED: Web Awesome license and distribution

**Decision:** Use Web Awesome Pro (`@web.awesome.me/webawesome-pro:3.2.1`) under the existing IW Pro license. Access via `WEBAWESOME_NPM_TOKEN` already provisioned in the environment.

**License verification (from [Web Awesome Pro License](https://webawesome.com/license/pro)):**
- Creators may integrate Pro Assets into distributed applications ("Projects"). ✅ `iw-dashboard.jar` with embedded Pro CSS/JS qualifies.
- End users running the Project do not need their own Pro license — permission passes through. ✅ Users running `iw dashboard` are covered.
- Standalone redistribution of Pro Assets to non-Creators is prohibited. Compiled/bundled Pro assets embedded in our jar are not standalone copies. ✅
- Public-repo source code that `import`s the Pro package is not a standalone copy — it's a reference; the actual package is fetched from the authenticated registry per-Creator. ✅

**Context:**
- iw-cli repo is public (`github.com/iterative-works/iw-cli`, verified).
- Release artifacts (built `iw-dashboard.jar`) are published to GitHub Releases — permitted as Project distribution.
- Contributor onboarding: set `WEBAWESOME_NPM_TOKEN`. Without it, `yarn install` fails on the Pro package. Contributors without a license can still build core and run commands (scala-cli path is unaffected).
- CI: add `WEBAWESOME_NPM_TOKEN` as a GitHub Actions secret for workflows that build the dashboard.

**What must NOT land in the repo:**
- `node_modules/` — gitignore (standard).
- Raw/standalone Pro asset files committed outside `node_modules/`.
- The token itself — `.npmrc`/`.yarnrc.yml` references the env var, never the literal.

**Exact registry config** (e.g., `.npmrc` `//registry.webawesome.com/:_authToken=${WEBAWESOME_NPM_TOKEN}`) is an L2 implementation detail — match the procedures pattern when that phase runs.

---

### RESOLVED: Dev-mode proxy mechanism

**Decision:** **Option A — emit absolute Vite URLs from HTML when in dev mode.** The template branches on a dev-mode flag to emit either `/assets/main.js` (prod, served from classpath) or `http://localhost:5173/src/main.js` (dev, direct to Vite). No server-side proxy.

**Rationale:** Option B (Cask reverse-proxies `/assets/*` to Vite) would also require a WebSocket proxy for Vite's HMR client, which shuttles frames bidirectionally between the browser and Vite. That's 50–100 lines of server code with real security surface, for the payoff of "HTML has no `if dev` branch." Option A's dev branch lives in one template helper, the prod code path is unchanged from today, and procedures has proven the pattern.

**Dev-mode gating (safety against accidental prod activation):**
- The dev branch must only activate when **both** the `--dev` flag is passed to the dashboard command (already in `DashboardArgs`) **and** the `VITE_DEV_URL` env var is set. Env-var-only is too easy to leak.
- Validate `VITE_DEV_URL` scheme is `http://` and host resolves to loopback (`localhost`/`127.0.0.1`). Refuse anything else at startup with a clear error.
- Log loudly on startup when dev mode is active (e.g., `⚠ Dev mode: serving assets from http://localhost:5173`).

**Vite config:** `server: { cors: true }` (as in procedures) so the browser accepts cross-origin loads from `http://localhost:5173` to the dashboard page served from a different port.

**Template helper shape (L1/L4):** A single `assetUrl(path: String): String` helper used by every HTML template. In prod: returns `/assets/$path`. In dev (gated): returns `${VITE_DEV_URL}/src/$path` (or `/assets/$path` depending on Vite entry mapping — TBD at L2/L4 implementation).

---

### RESOLVED: Node/yarn toolchain expectations

**Decision:** **Yarn 4 via Corepack, with `yarn.lock` committed. Node + Yarn are documented prereqs for dashboard development only.**

**Scope clarification:**
- **End-user bootstrap** (from release tarball): no Node, no Yarn, no Mill. Tarball ships with prebuilt `build/iw-dashboard.jar`; users only need a JVM + scala-cli. Unchanged from today.
- **Contributor bootstrap** (fresh clone): Node 20+ and Yarn 4 required only for dashboard work. Anyone editing core or commands doesn't need them. `./iw --bootstrap` does not auto-install.

**Setup:**
- `package.json` pins version via `"packageManager": "yarn@4.9.x"` (or current stable at implementation time). Corepack reads this and shims the right Yarn per project.
- Contributor runs `corepack enable` once system-wide; `yarn install` Just Works from there.
- `yarn.lock` committed for reproducibility.
- Registry auth config lives in `.yarnrc.yml` (Yarn 4 native format). `.npmrc` fallback also works if simpler.

**Divergence from procedures:** procedures currently uses Yarn 1 (classic). iw-cli moves to Yarn 4 directly. Michal plans to update procedures to match separately — not this issue's concern.

**Failure mode:** `./mill frontend.viteBuild` fails fast with a clear error if Node/Yarn/Corepack are missing. README documents the prereqs.

---

### RESOLVED: CI impact

**Context:** iw-cli CI runs on a self-hosted runner inside the versioned Docker image `ghcr.io/iterative-works/iw-cli-ci:latest`, built from `.github/Dockerfile.ci` (Ubuntu 24.04 + pre-installed JDK 21, scala-cli, scalafix, bats, gh, tmux, jq, python3, all pulled from pre-downloaded `deps/` at image build). No per-run toolchain downloads. `ci.yml` has four jobs: `compile`, `format`, `lint`, `test`.

**Decision:** Extend the CI Docker image with Node + Corepack + Mill; keep everything pre-provisioned at image build time. Add `WEBAWESOME_NPM_TOKEN` as a repo secret.

**Dockerfile additions (`.github/Dockerfile.ci`):**
- **Node 20** — pre-download `node-v20.x.x-linux-x64.tar.xz` to `deps/node.tar.xz`, extract to `/opt/node`, symlink `node`/`npm`/`npx` into `PATH`. Matches the existing JDK-via-`deps/jdk.tar.gz` pattern.
- **Corepack** — ships with Node 20; `RUN corepack enable` during image build. Yarn 4 is then driven by `package.json`'s `packageManager` field — no global Yarn install.
- **Mill 1.1.5** — pre-download the Mill launcher binary to `deps/mill`, install to `/usr/local/bin/mill` (same pattern as scala-cli). The repo's `./mill` wrapper stays for local dev; in CI the system-installed binary satisfies the wrapper or is called directly.
- **Verification line** — extend the final `RUN` to include `node --version && yarn --version && mill --version`.

**`ci.yml` additions:**
- New `WEBAWESOME_NPM_TOKEN` repo secret, exposed via `env:` to any job running `yarn install`.
- New `dashboard-build` job running `./mill dashboard.assembly` (and `./mill dashboard.test` if Mill tests are added per CLARIFY 7). Separate from `test` so failures are attributed clearly.

**Image rebuild cadence:** the image only rebuilds when `Dockerfile.ci` or `deps/` changes. Adding Node/Mill is a one-time image bump; subsequent CI runs hit the warm image.

**Impact on existing jobs:** `compile`, `format`, `lint`, `test` unchanged — they don't need Node/Mill. Only the new dashboard-related job consumes the added toolchain.

---

### RESOLVED: Test strategy for the Mill module

**Decision:** Full Mill test modules, co-locate tests with sources, with a separate **integration-test (`itest`) module** alongside the unit-test module.

**Baseline:** `core/test/` currently holds 141 test files, **~20 of which touch dashboard types** (`PageLayoutTest`, `MainProjectsViewTest`, `WorkflowProgressServiceTest`, `WorktreeCardServiceTest`, `SampleDataTest`, `ReviewStateServiceTest`, `StaticFilesTest`, and others). All are preserved, none skipped.

**Module structure (in `build.mill`):**
```
object dashboard extends ScalaModule {
  object test extends ScalaTests with TestModule.Munit {
    // Unit tests — pure logic, views, services.
    // Migrated from core/test/*.scala for dashboard-touching tests.
  }
  object itest extends ScalaTests with TestModule.Munit {
    // Integration tests — launch Cask server, HTTP smoke tests,
    // verify assembled jar resolves assets correctly, etc.
  }
}
```

**Source relocation:**
- ~20 dashboard-touching tests move from `core/test/*.scala` to `dashboard/jvm/test/src/*.scala`.
- Core tests that don't touch `iw.core.dashboard.*` types stay under `core/test/` (scala-cli runs them).
- Integration tests (launch jar, smoke HTTP, verify bundled assets) are new files under `dashboard/jvm/itest/src/`.

**`./iw ./test` orchestration:**
- `./iw ./test unit` runs both scala-cli core tests (unchanged) and `./mill dashboard.test` (new).
- `./iw ./test itest` runs `./mill dashboard.itest`.
- `./iw ./test e2e` runs BATS suite (unchanged).
- `./iw ./test` (no arg) runs unit + itest + e2e.

**Rationale for itest separation:** Integration tests require an assembled jar (produced by L3) and a running HTTP server. They're slower and have different failure modes from unit tests. Keeping them in a dedicated Mill module lets CI parallelize and lets contributors skip them during tight dev loops.

**Coverage rule compliance:** CLAUDE.md mandates unit + integration + e2e coverage with no exceptions unless explicitly authorized. This structure satisfies that: unit (scala-cli + mill), integration (mill itest), e2e (BATS).

---

### RESOLVED: Package relocation blast radius

**Decision:** **Rename packages from `iw.core.dashboard.*` to `iw.dashboard.*`.** Directory move + package rename land in the same L1 commit.

**Blast radius verified (grep against current tree):**
- 92 files reference `iw.core.dashboard.*`.
- 89 of those are dashboard code or dashboard tests — they move together with the rename (mechanical `sed`/`rg` pass over package declarations + imports).
- 3 are external references, all superseded by L4's `java -jar` launch and disappearing anyway:
  - `commands/dashboard.scala` — imports `CaskServer`, `StateRepository`, `SampleDataGenerator`.
  - `commands/server-daemon.scala` — imports `ServerDaemon`.
  - `core/adapters/ProcessManager.scala:115` — string-literal FQCN (`"iw.core.dashboard.ServerDaemon"`) passed to scala-cli's `--main-class`.

**Effective external blast radius: zero** — all three external references are being rewritten or removed in L4 regardless of rename choice.

**Rationale:** Package name should match the actual module boundary in a dual-build-tool repo. After the move, `iw.core.*` lives under `core/` (scala-cli), `iw.dashboard.*` lives under `dashboard/` (Mill). Navigability wins, churn is trivial given the 89 edits are mechanical.

**Execution notes:**
- Rename happens in L1 along with the directory move; don't split into two commits.
- Post-rename grep check: `rg 'iw\.core\.dashboard' --type scala` must return zero results.

---

### RESOLVED: scalatags-webawesome version compatibility

**Decision:** Pin `works.iterative::scalatags-webawesome:3.2.1.1`.

**Verification:**
- **Published on Maven Central** (MIT licensed). Confirmed via `repo1.maven.org/maven2/works/iterative/scalatags-webawesome_3/3.2.1.1/` (200 OK for pom and jar) and `central.sonatype.com` browse API. Published 2026-03-05.
- **Available versions on Central:** `3.2.1` (2026-02-20), `3.2.1.1` (2026-03-05, latest release). `3.2.1.1` picked as newer and still compatible.
- **Transitive deps** (per the ivy/pom metadata):
  - `com.lihaoyi::scalatags:0.13.1` — matches iw-cli core exactly.
  - `org.scala-lang::scala3-library:3.3.7` — matches iw-cli core exactly.
- **No transitive conflicts.** The lib is ~43KB — a thin set of Scalatags tag/attribute definitions.

**Build wiring:** Standard Mill `mvnDeps` entry (`mvn"works.iterative::scalatags-webawesome:3.2.1.1"`). No `ivy2Local` resolver needed — Central resolves fine for released versions. (Procedures adds `ivy2Local` mainly for SNAPSHOT iteration on its own builds; iw-cli doesn't need that.)

---

## Total Estimates

**Per-Layer Breakdown (post-CLARIFY resolution):**
- L0 Mill Bootstrap + Core Jar Production: 3-5 hours
- L1 Dashboard JVM Module (move + rename + test migration + dep cleanup): 4-7 hours
- L2 Frontend Pipeline (Yarn 4, vite, tailwind, Web Awesome): 2-4 hours
- L3 Asset Bundling + Fat Jar + itest module + CI job: 3-5 hours
- L4 Command Integration + Dev Mode (double-gated): 3-5 hours

**Total Range:** 15-26 hours

**Confidence:** Medium

**Reasoning:**
- All nine CLARIFYs resolved, so most of the original uncertainty has been priced in. The range reflects real unknowns (Mill 1.1.5 syntax specifics, Cask classpath asset serving, first-time Yarn 4 + Corepack setup on the CI image).
- L0 grew because it now includes flipping IW-344's jar producer to Mill and extending the CI Docker image.
- L1 grew slightly to account for the 89-file package rename + 20 test-file migration, though both are mechanical.
- L3 grew because the `itest` module is a new module (not just a test file), and the new `dashboard-build` CI job needs wiring with `WEBAWESOME_NPM_TOKEN`.
- L4 grew because the double-gated dev mode needs careful implementation to avoid prod accidents.

## Recommended Phase Plan

Total estimate is 15-26 hours, landing in the 12-28h tier which supports 3-5 phases. All layers now meet or exceed the 3h floor individually, so the plan can preserve the natural layer boundaries — no merging needed.

- **Phase 1: Mill bootstrap + core jar production**
  - Includes: L0
  - Estimate: 3-5 hours
  - Rationale: Establishes Mill in the repo, flips the core jar builder from scala-cli to Mill, and extends the CI Docker image with Node/Corepack/Mill. Deliverable: `./mill core.jar` produces the same `build/iw-core.jar` that scala-cli commands already consume. No dashboard-facing changes yet. Standalone reviewable: validates the build-tool foundation before anything depends on it.
- **Phase 2: Dashboard JVM module (move + rename + tests)**
  - Includes: L1
  - Estimate: 4-7 hours
  - Rationale: Pure code-reorganization phase. Directory move (`core/dashboard/**` → `dashboard/jvm/src/**`), package rename (`iw.core.dashboard.*` → `iw.dashboard.*`), migrate ~20 tests from `core/test/` to `dashboard/jvm/test/src/`, move dashboard-only deps out of `core/project.scala` into the Mill `dashboard` module. All tests still pass. The dashboard still compiles and runs exactly as before — nothing user-visible changes yet.
- **Phase 3: Frontend pipeline + fat-jar assembly + integration tests**
  - Includes: L2 + L3
  - Estimate: 5-9 hours
  - Rationale: L2 and L3 are a single conceptual unit — bundle assets, embed them in a jar, prove the jar works end-to-end via the new `itest` module. Splitting them produces an orphaned intermediate state (vite output with nowhere to land). Combined, this phase ends with `build/iw-dashboard.jar` runnable via `java -jar`, CI's new `dashboard-build` job green, and integration tests passing.
- **Phase 4: Command integration + dev mode**
  - Includes: L4
  - Estimate: 3-5 hours
  - Rationale: The user-facing cutover. `commands/dashboard.scala` and `commands/server-daemon.scala` switch from in-process `CaskServer.start` to `java -jar`. Double-gated dev mode lands here. `iw-run` adds the `ensure_dashboard_jar` helper. This is the PR where `iw dashboard` starts using the new build.

**Total phases:** 4 (for total estimate 15-26 hours)

**Merge order contract:** Phases land strictly in sequence — each one produces a fully green tree with all existing tests passing. Phase 2 is the longest mechanical change and is the riskiest for hidden regressions (the 89-file rename + 20 test migrations); accept a larger diff here and let reviewers focus on it without also reviewing build-tool or frontend work.

## Testing Strategy

### Per-Layer Testing

**L0 Mill Bootstrap + Core Jar:**
- Automated (pre-push hook + CI): `./mill core.jar` produces `build/iw-core.jar` from a clean clone.
- Regression: all 121 non-dashboard tests in `core/test/` still pass via scala-cli (they consume the Mill-built jar transparently).
- Regression: all existing commands (`./iw status`, `./iw start`, etc.) still run — they resolve runtime deps via `core/project.scala` as before.
- CI image verification: `mill --version`, `node --version`, `yarn --version` all succeed in the Docker image.

**L1 Dashboard JVM Module:**
- Automated: `./mill dashboard.compile` succeeds.
- Automated: `./mill dashboard.test` runs the ~20 migrated unit tests — all must pass (no coverage regression).
- Grep check: `rg 'iw\.core\.dashboard' --type scala` must return zero matches after the rename.
- Regression: scala-cli compile of `commands/` still passes (temporary bridge code keeps L4 launchers compiling).

**L2 Frontend Pipeline:**
- Automated: `./mill frontend.viteBuild` produces `dist/assets/main.js` and `dist/assets/main.css` from a clean clone (given `WEBAWESOME_NPM_TOKEN` set).
- Smoke: inspect bundled CSS for a known Tailwind class used in a Scala template — confirms the `@source` scan is working.
- Fail-fast: `yarn install` without a token gives a clear error (to validate onboarding docs).

**L3 Asset Bundling + Fat Jar + itest:**
- Automated: `./mill dashboard.assembly` produces `build/iw-dashboard.jar`. `unzip -l` shows `assets/main.js` and `assets/main.css` inside.
- Automated (`dashboard.itest`): launch the assembled jar (or Cask in-process), `GET /` returns 200, HTML references `/assets/main.js`, and the asset path resolves from classpath.
- CI: new `dashboard-build` job runs both `dashboard.assembly` and `dashboard.itest` against the updated Docker image.

**L4 Command Integration + Dev Mode:**
- E2E (BATS): `iw dashboard` launches the jar, opens the browser target URL — existing dashboard BATS tests adapted for the new launch path; `IW_SERVER_DISABLED=1` semantics preserved.
- E2E (BATS): `iw server-daemon` starts the jar in background, status endpoint responds, teardown cleanly stops it.
- Dev-mode gate test (manual or BATS): setting `VITE_DEV_URL` alone (without `--dev`) must NOT activate dev routing — bundled assets still served.
- Dev-mode gate test: setting `VITE_DEV_URL` to a non-loopback host must refuse to start.
- Manual: with `--dev` + `VITE_DEV_URL=http://localhost:5173` and `yarn dev` running, editing a Scala template updates the browser; editing `main.css` triggers HMR.
- Rebuild gating: `ensure_dashboard_jar` triggers Mill when `dashboard/jvm/src/**` or `dashboard/frontend/src/**` is newer than the jar.

**Test Data Strategy:**
- Unit tests: existing fixtures move with the test files; no new fixtures.
- Integration tests: in-memory state only; no external services, no filesystem beyond the jar's embedded resources.

**Regression Coverage:**
- 141 core tests must all pass (121 remain in `core/test/`, 20 move to `dashboard/jvm/test/src/` and pass under Mill).
- All existing BATS tests that touch `iw dashboard` or `iw server-daemon` still pass.
- `./iw ./test` (unit + itest + e2e) passes end-to-end after each phase, including the scala-cli compile check over command scripts.

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
- New env var: `VITE_DEV_URL` (optional, dev-mode only).
- Potential new file: `.npmrc` with Web Awesome auth token (see CLARIFY 3).
- `.gitignore` additions: `out/`, `dashboard/frontend/node_modules/`, `dashboard/frontend/dist/`.

### Rollout Strategy
This is a build infrastructure layer — there is no runtime rollout. The merge is the rollout. Release packaging changes (IW-346) handle distribution separately.

### Rollback Plan
Revert the PR(s). `core/dashboard/**` is restored, command scripts go back to launching `CaskServer.start` directly, and the Mill build is removed. Because `build/iw-core.jar` exists independently (from IW-344), nothing else breaks.

## Dependencies

### Prerequisites
- **IW-344 merged** — done (commit 259bc54). `build/iw-core.jar` exists; its builder flips from scala-cli to Mill in L0 (acknowledged retcon, scope-limited to `iw-run`'s `build_core_jar()`).
- **Developer prereqs** (dashboard work only): Node 20+, Corepack enabled (`corepack enable`), `WEBAWESOME_NPM_TOKEN` set. Core/commands work needs none of these.
- **End-user prereqs** (unchanged): JVM, scala-cli. Released tarballs ship prebuilt jars.
- **CI prereqs**: updated Docker image with Node 20 + Corepack + Mill 1.1.5, plus the `WEBAWESOME_NPM_TOKEN` repo secret.

### Layer Dependencies
- L0 is a hard prerequisite for L1, L2, L3 (Mill must exist).
- L1 and L2 are independent and can be developed in parallel once L0 lands.
- L3 depends on both L1 (jar to assemble) and L2 (assets to embed).
- L4 depends on L3 (jar must exist to launch).

### External Blockers
- Web Awesome Pro license availability.
- CI runner Node/yarn/Mill provisioning.

## Risks & Mitigations

### Risk 1: Stale `build/iw-core.jar` during dashboard development
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Mill's native `moduleDeps = Seq(core)` wiring means Mill tracks core source changes when building dashboard. For the scala-cli path, `iw-run`'s existing `ensure_core_jar` gate runs before every command invocation. Residual risk is a stale jar invoked directly without going through `iw-run` — document the contract in CLAUDE.md.

### Risk 2: Core `mvnDeps` in `build.mill` drifts from `//> using dep` directives in `core/project.scala`
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Short dep list (~4 after the dashboard-only deps move to L1). Sync-comment in both files points at the other. Add a CI check (grep the two files, diff the dep coords) if drift becomes a recurring issue.

### Risk 3: Dev-mode asset routing accidentally activates in production
**Likelihood:** Low
**Impact:** High
**Mitigation:** Double-gate on `--dev` flag AND `VITE_DEV_URL` env var. Validate scheme is `http://` and host is loopback; refuse anything else at startup. Log loudly when dev mode is active. BATS test asserts that env-var-alone does NOT activate dev routing.

### Risk 4: Package rename (89 files) leaves stale references
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Post-rename grep check `rg 'iw\.core\.dashboard' --type scala` must return zero. Full test run (141 tests) validates no compile-level regressions.

### Risk 5: `WEBAWESOME_NPM_TOKEN` missing for contributors or CI
**Likelihood:** Medium (for new contributors)
**Impact:** Medium (blocks dashboard build only; core/commands unaffected)
**Mitigation:** Clear README documentation of the prereq and the failure mode. Scope the token to dashboard-related CI jobs only so core/commands CI stays independent.

### Risk 6: Mill 1.1.5 syntax divergence from reference implementation
**Likelihood:** Low
**Impact:** Low
**Mitigation:** Procedures reference is Mill 1.0.x-era; 1.1.x should be backward compatible but not verified. Syntax differences surface at first `./mill core.compile` — fix forward or pin an earlier 1.0.x version.

### Risk 7: Integration tests flake on CI (port conflicts, timing)
**Likelihood:** Medium
**Impact:** Low
**Mitigation:** `itest` launches Cask on an ephemeral port (port 0 + discover actual port). No external services. If flakes surface, retry policy in CI config is a last resort — prefer root-cause fixes.

---

## Implementation Sequence

**Strict sequential order — one phase per PR:**

1. **Phase 1 (L0)** — Mill bootstrap + core jar production. Foundation: nothing depends on dashboard changes yet. `build/iw-core.jar` now comes from Mill, all existing tests green.
2. **Phase 2 (L1)** — Dashboard JVM module. Pure relocation: directory move + package rename + test migration + dep cleanup. No behavior change; dashboard still runs from the in-process code path via scala-cli (temporary bridge until L4).
3. **Phase 3 (L2+L3)** — Frontend pipeline + fat-jar assembly + itest. Ends with `build/iw-dashboard.jar` runnable via `java -jar` and a new `dashboard-build` CI job green.
4. **Phase 4 (L4)** — Command integration + dev mode. Flips `iw dashboard` and `iw server-daemon` to launch the jar; adds the double-gated dev-mode switch.

**Ordering rationale:**
- Build tooling (Phase 1) must exist before any module definitions reference it.
- Code reorg (Phase 2) happens in isolation, no frontend work co-mingled, so reviewers focus purely on the rename/move correctness.
- Frontend build + assembly + itest (Phase 3) are one conceptual unit — bundle, embed, prove end-to-end.
- Launcher cutover (Phase 4) is last because it's the only phase with user-visible behavior changes; everything it depends on is already proven green in prior phases.

## Documentation Requirements

- [ ] Update `CLAUDE.md` with the two-build-tool boundary (scala-cli for core/commands, Mill for dashboard).
- [ ] Top-level README note on Node/yarn/Mill prerequisites.
- [ ] ADR documenting the choice of Mill + Vite + Tailwind + Web Awesome for the dashboard, and why Cask stays.
- [ ] Inline comments in `build.mill` explaining the unmanaged `iw-core.jar` dependency (if we go Option A of CLARIFY 2).
- [ ] Update MEMORY.md patterns once the Mill integration stabilizes.

---

**Analysis Status:** Ready for task breakdown — all 9 CLARIFYs resolved (2026-04-20 → 2026-04-21).

**Next Steps:**
1. Run `/iterative-works:wf-create-tasks IW-345` to generate phase files.
2. Run `/iterative-works:wf-implement IW-345` for phase-by-phase implementation.
