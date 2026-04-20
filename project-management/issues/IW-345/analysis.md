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

### L0 — Mill Bootstrap

**Components:**
- `build.mill` at repo root (top-level Mill build definition, empty or minimal at this layer — real modules land in L1/L2)
- `./mill` launcher script (downloads Mill on first run, analogous to the existing scala-cli bootstrap)
- `.mill-version` pinning the Mill release
- `.gitignore` updates for `out/` (Mill's build cache)

**Responsibilities:**
- Provide a reproducible Mill invocation for developers and CI without requiring a global install.
- Pin the Mill version so the `mvnDeps` / `Task.Sources` syntax in the reference implementation compiles.

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### L1 — Dashboard JVM Module

**Components:**
- `dashboard/jvm/src/` — Scala sources relocated from `core/dashboard/**` (CaskServer, StateRepository, DashboardService, ArtifactService, GitStatusService, ServerDaemon, and `application/`, `domain/`, `infrastructure/`, `presentation/`, `resources/` sub-packages)
- `dashboard` ScalaModule in `build.mill` with Scala 3 version matching core
- Dependency wiring: cask 0.11.3, scalatags 0.13.1, scalatags-webawesome 3.2.1, flexmark-all 0.64.8, plus any core-wide deps the dashboard actually uses (typesafe-config, upickle, os-lib, sttp if needed)
- Unmanaged classpath entry pointing at `build/iw-core.jar`
- Package declaration updates (only if we rename — see CLARIFY)

**Responsibilities:**
- Compile the dashboard server code as a standalone Mill module.
- Consume core types through the pre-built jar, matching how command scripts consume it today.
- Expose assembly targets for L3.

**Estimated Effort:** 3-6 hours
**Complexity:** Moderate

---

### L2 — Frontend Pipeline

**Components:**
- `dashboard/frontend/package.json` (vite, @tailwindcss/vite, tailwindcss, htmx.org, markdown-it, @web.awesome.me/webawesome-pro)
- `dashboard/frontend/vite.config.js` (tailwind plugin, entry at `src/main.js`, output to dist/assets/)
- `dashboard/frontend/src/main.js` — cherry-picked Web Awesome imports (button, tag, icon, input, textarea, tree, tree-item, card, page), plus htmx and main.css import
- `dashboard/frontend/src/main.css` — `@import "tailwindcss"` plus `@source` directives pointing at `../jvm/src/**/*.scala`
- `yarn.lock` (or `package-lock.json` — see CLARIFY)
- `.npmrc` configuration for Web Awesome Pro auth token (see CLARIFY)
- Mill `frontend` module wrapping `yarn install` and `yarn build` as tasks

**Responsibilities:**
- Produce bundled CSS and JS assets for the dashboard.
- Scan Scala sources for Tailwind class names so unused CSS is purged.
- Expose a `viteBuild` task whose output is consumed by L3.

**Estimated Effort:** 2-4 hours
**Complexity:** Moderate

---

### L3 — Asset Bundling & Fat Jar

**Components:**
- Mill task graph wiring: `frontend.viteBuild` → `dashboard.resources` (or an override that copies vite output into the resources set before assembly)
- `dashboard.assembly` producing `build/iw-dashboard.jar`
- Cask route adjustment to serve bundled assets from classpath (`getResourceAsStream` pattern) — verify current code path; may be a no-op if resources are already loaded via classpath
- Output-path convention: Mill writes to `out/dashboard/assembly.dest/out.jar`; a wrapper or post-task copies/symlinks to `build/iw-dashboard.jar` for launcher discoverability

**Responsibilities:**
- Guarantee asset bundling is part of the same build that produces the server jar.
- Keep the on-disk artifact path stable (`build/iw-dashboard.jar`) so command scripts have a fixed target.

**Estimated Effort:** 2-4 hours
**Complexity:** Moderate

---

### L4 — Command Integration & Dev Mode

**Components:**
- `commands/dashboard.scala` updated to `java -jar build/iw-dashboard.jar` (fg, browser-open preserved)
- `commands/server-daemon.scala` updated to launch the jar in background; drop the `CaskServer.start` direct import
- `iw-run` helpers `ensure_dashboard_jar` (mirroring `ensure_core_jar` / `needs_rebuild` at `iw-run:29-74`) that call Mill when sources are newer than the jar
- Dev-mode switch: `VITE_DEV_URL` env var consumed by the server to decide between bundled assets and Vite dev server (exact mechanism pending CLARIFY)
- `dashboard/frontend/start-dev.sh` (or equivalent) to launch `yarn dev` on port 5173

**Responsibilities:**
- Preserve the existing launcher UX — `iw dashboard`, `iw server-daemon` keep working.
- Enable HMR during dashboard development without requiring jar rebuilds per change.
- Trigger rebuilds transparently when Scala or frontend sources change.

**Estimated Effort:** 2-4 hours
**Complexity:** Moderate

---

## Technical Decisions

### Patterns

- **Two build tools cohabiting**: scala-cli for `core/` + `commands/`, Mill for `dashboard/`. Boundary is the jar artifact contract at `build/iw-core.jar` and `build/iw-dashboard.jar`.
- **Fat-jar-per-process**: dashboard runs as a separate JVM, not as an in-process library call from command scripts.
- **Functional core / imperative shell**: existing dashboard sub-packages (`domain/`, `application/`, `infrastructure/`, `presentation/`) are preserved wholesale — this layer only moves files, it does not refactor them.
- **Resource-embedded assets**: Vite output is bundled into the jar's resources rather than shipped as a sidecar directory.

### Technology Choices

- **Build tool**: Mill (version TBD per CLARIFY 1)
- **Frontend bundler**: Vite 8
- **CSS**: Tailwind CSS v4 via `@tailwindcss/vite`
- **Component library**: Web Awesome Pro via `scalatags-webawesome:3.2.1`
- **HTTP server**: Cask 0.11.3 (unchanged)
- **Package manager**: yarn (matching procedures) or npm — pending CLARIFY 5

### Integration Points

- `build/iw-core.jar` → consumed by `dashboard` Mill module as unmanaged classpath entry.
- `frontend.viteBuild` output → copied into `dashboard` resources before assembly.
- `build/iw-dashboard.jar` → launched by `commands/dashboard.scala` and `commands/server-daemon.scala`.
- `iw-run` rebuild gate → invokes Mill when dashboard sources or frontend sources are newer than the jar.
- `VITE_DEV_URL` env var → read by running dashboard JVM to toggle asset resolution strategy.

## Technical Risks & Uncertainties

### CLARIFY: Mill version and launcher strategy

Procedures uses `Mill 1.0.x`-era syntax (`mvnDeps`, `Task.Sources`). We need to pin the same version range and decide how developers get Mill.

**Questions to answer:**
1. Which exact Mill version do we pin?
2. Do we commit a `./mill` wrapper script (procedures pattern), a bare `.mill-version`, or both?
3. Do we document Mill as a prerequisite instead of bundling a launcher?

**Options:**
- **Option A — Commit `./mill` wrapper + `.mill-version`**: matches procedures, zero-install for contributors. Cons: another script to maintain, binary downloaded on first run.
- **Option B — `.mill-version` only**: relies on `mill` being on PATH. Simpler, but adds a prerequisite.
- **Option C — Document Mill install in README**: cheapest, but raises the bar for casual contributors.

**Impact:** Developer onboarding, CI workflow, `iw --bootstrap` scope.

---

### CLARIFY: iw-core.jar consumption pattern

Three viable ways for the Mill dashboard module to depend on core:

**Questions to answer:**
1. How stale can `build/iw-core.jar` be before Mill notices and rebuilds?
2. Do we want Mill to trigger `ensure_core_jar` itself, or rely on `iw-run` to keep the jar fresh?
3. Is ivy2Local publishing acceptable given it pollutes the user's local ivy cache?

**Options:**
- **Option A — Unmanaged classpath**: `def unmanagedClasspath = Task { Agg(PathRef(build/iw-core.jar)) }`. Simplest, but Mill won't detect staleness unless we wire a file-watch; risk of building against old core.
- **Option B — Publish core to ivy2Local first, depend via `mvnDeps`**: idiomatic, Mill handles caching. Cons: pollutes `~/.ivy2/local`, requires coordinating scala-cli publish step.
- **Option C — Duplicate core as a Mill module**: most idiomatic Mill, but duplicates build config and bifurcates source-of-truth.

**Impact:** Rebuild ergonomics, CI reliability, how often "stale jar" bugs surface during development.

---

### CLARIFY: Web Awesome license and distribution

`@web.awesome.me/webawesome-pro` requires a paid license and authenticated npm registry access.

**Questions to answer:**
1. Is there an existing iw license we're already using for procedures that covers iw-cli too?
2. What's the auth mechanism — `.npmrc` with `_authToken`? Is the token per-developer or shared?
3. Is there a non-Pro community edition of Web Awesome we could use instead for this OSS-ish project?

**Options:**
- **Option A — Reuse existing Pro license**: fastest if license terms permit. Requires token provisioning for contributors and CI.
- **Option B — Switch to Web Awesome community edition (if one exists)**: removes auth complexity but may restrict component set.
- **Option C — Drop Web Awesome for now and use raw Tailwind + htmx**: fallback if licensing is blocking.

**Impact:** Contributor onboarding, CI secrets management, component vocabulary available to the dashboard.

---

### CLARIFY: Dev-mode proxy mechanism

When `VITE_DEV_URL` is set, the dashboard needs to serve assets from the Vite dev server instead of the jar's resources.

**Questions to answer:**
1. Does Cask proxy `/assets/*` to `http://localhost:5173/`, or does the HTML template emit absolute Vite URLs?
2. Does htmx / Web Awesome work correctly with absolute cross-origin asset URLs during dev?
3. What's the CORS story — procedures sets `server: { cors: true }` in vite config; do we need more?

**Options:**
- **Option A — Emit absolute Vite URLs** (procedures pattern): simplest server-side, no proxy logic. Requires Vite CORS enabled.
- **Option B — Cask reverse-proxies `/assets/*`**: HTML is unchanged between dev and prod. More server code, but single-origin.

**Impact:** Server-side complexity, dev-mode debuggability.

---

### CLARIFY: Node/yarn toolchain expectations

Frontend build requires Node and a package manager.

**Questions to answer:**
1. Should `./iw --bootstrap` install Node/yarn automatically, or document them as prerequisites?
2. yarn or npm? Procedures uses yarn; iw-cli has no precedent.
3. Do we commit `yarn.lock` / `package-lock.json`?

**Options:**
- **Option A — Document as prereq, commit lockfile**: standard, least magical.
- **Option B — Bootstrap installs Node via a version manager**: smoother onboarding, more moving parts in the bootstrap script.

**Impact:** Contributor setup friction, CI provisioning.

---

### CLARIFY: CI impact on self-hosted runner

The iw-cli self-hosted runner currently provisions scala-cli.

**Questions to answer:**
1. Does the runner already have Node, yarn, and Mill available?
2. If not, do we install them in the workflow or pre-provision the runner?
3. Do we need an npm auth secret for Web Awesome Pro?

**Options:**
- **Option A — Pre-provision runner**: faster CI runs, one-time setup cost.
- **Option B — Install in workflow**: reproducible, slower per run.

**Impact:** CI runtime, self-hosted runner maintenance burden, secret management.

---

### CLARIFY: Test strategy for the Mill module

The dashboard currently has whatever test coverage lives under `core/`. Moving it to Mill changes the test runner path.

**Questions to answer:**
1. Does the Mill module need `object test extends ScalaTests with TestModule.Munit`?
2. Do we port existing dashboard tests (if any) or defer to behavioral smoke tests (launch jar, curl `/`, check 200 + asset references)?
3. How does `./iw ./test` discover and run Mill tests?

**Options:**
- **Option A — Full Mill test module + integrate with `./iw ./test`**: complete coverage, more wiring.
- **Option B — Smoke test only at L4**: minimal, relies on manual QA for deeper coverage.

**Impact:** Test-suite completeness, `./iw ./test` orchestration complexity.

---

### CLARIFY: Package relocation blast radius

Moving `core/dashboard/**` to `dashboard/jvm/src/**` is a directory move; packages might also need renaming.

**Questions to answer:**
1. Keep packages as `iw.core.dashboard.*` (minimal churn) or rename to `iw.dashboard.*` (reflects module boundary)?
2. Are there references to `iw.core.dashboard.*` types from outside the dashboard (command scripts, tests) that would need updating?
3. Does `ServerDaemon` get imported from `commands/server-daemon.scala` today? (Yes per issue description — will become jar-launch instead.)

**Options:**
- **Option A — Keep package names**: directory-only move, zero API impact beyond the launcher changes.
- **Option B — Rename packages to `iw.dashboard.*`**: cleaner long-term, but touches every file in the module plus any imports.

**Impact:** Diff size, reviewer load, risk of stale references.

---

### CLARIFY: scalatags-webawesome version compatibility

Procedures pins `scalatags-webawesome:3.2.1`.

**Questions to answer:**
1. Is 3.2.1 still the latest and still published?
2. Is it cross-built for Scala 3.3.7 (core's version)?
3. Does it transitively pull in anything that conflicts with current core deps?

**Options:**
- **Option A — Pin 3.2.1**: matches procedures, known-good.
- **Option B — Pick latest**: fresher, potential incompatibilities.

**Impact:** Classpath health, feature parity with procedures dashboard patterns.

---

## Total Estimates

**Per-Layer Breakdown:**
- L0 Mill Bootstrap: 1-2 hours
- L1 Dashboard JVM Module: 3-6 hours
- L2 Frontend Pipeline: 2-4 hours
- L3 Asset Bundling & Fat Jar: 2-4 hours
- L4 Command Integration & Dev Mode: 2-4 hours

**Total Range:** 10-20 hours

**Confidence:** Medium

**Reasoning:**
- Procedures provides a working reference, which compresses the uncertainty on L1-L3.
- Web Awesome licensing and dev-mode details are real unknowns that could add hours if they go sideways.
- Package-rename scope (CLARIFY 8) could double L1 if we choose to rename.
- Two-build-tool cohabitation in CI/bootstrap is an integration risk we haven't rehearsed in iw-cli before.

## Recommended Phase Plan

Total estimate is 10-20 hours, landing in the 12-24h tier which supports 2-5 phases. Applying the 3h floor: L0 (1-2h) is below the floor and must merge with an adjacent layer; L2, L3, L4 (2-4h each) all sit at or just below the floor and benefit from merging; L1 (3-6h) is above the floor but is the most coupled to L0 setup decisions.

- **Phase 1: Mill bootstrap + dashboard JVM module**
  - Includes: L0 + L1
  - Estimate: 4-8 hours
  - Rationale: L0 is sub-floor and produces nothing useful on its own. Merging with L1 lets a single PR establish Mill in the repo and demonstrate it compiling actual code. Package-relocation decisions (CLARIFY 8) naturally belong with the module-creation commit.
- **Phase 2: Frontend pipeline + fat-jar assembly**
  - Includes: L2 + L3
  - Estimate: 4-8 hours
  - Rationale: L2 and L3 are a single conceptual unit — bundle assets, then embed them in a jar. Splitting them produces an intermediate state (vite output exists but is orphaned) that has no review value. Combined, the PR ends with `build/iw-dashboard.jar` containing working assets and being runnable via `java -jar`.
- **Phase 3: Command integration and dev mode**
  - Includes: L4
  - Estimate: 2-4 hours
  - Rationale: Kept separate despite being near the floor because it's the user-facing cutover — the `iw dashboard` and `iw server-daemon` behavior changes here, and HMR support is distinct enough to warrant focused review. Floor exception: this phase isolates the launcher contract and the dev/prod switching logic, both of which benefit from an independent PR.

**Total phases:** 3 (for total estimate 10-20 hours)

## Testing Strategy

### Per-Layer Testing

**L0 Mill Bootstrap:**
- Manual: `./mill --version` succeeds from a clean clone.
- Manual: `./mill resolve _` lists modules without errors.

**L1 Dashboard JVM Module:**
- Manual: `./mill dashboard.compile` succeeds.
- Manual: `./mill dashboard.compile` fails loudly if `build/iw-core.jar` is missing.
- Integration: if a test module is introduced (CLARIFY 7), port any existing dashboard unit tests.

**L2 Frontend Pipeline:**
- Manual: `./mill frontend.viteBuild` produces `dist/assets/main.js` and `dist/assets/main.css`.
- Manual: Tailwind classes used in Scala sources appear in the bundled CSS (smoke-check a known class).

**L3 Asset Bundling & Fat Jar:**
- Integration: `./mill dashboard.assembly` produces `build/iw-dashboard.jar` with `assets/main.js` and `assets/main.css` inside.
- Integration: `java -jar build/iw-dashboard.jar` starts, responds to `GET /` with 200, and HTML references `/assets/main.js`.

**L4 Command Integration & Dev Mode:**
- E2E (BATS): `iw dashboard` launches the jar, opens the browser target URL — mirror existing dashboard BATS tests with `IW_SERVER_DISABLED=1` semantics adapted for the new launch path.
- Manual: with `VITE_DEV_URL=http://localhost:5173` set and `yarn dev` running, HMR reloads on CSS changes.
- Manual: without `VITE_DEV_URL`, assets are served from the jar.

**Test Data Strategy:**
- No fixtures needed for this layer — tests exercise build outputs and HTTP smoke behavior only.

**Regression Coverage:**
- Existing BATS tests that touch `iw dashboard` or `iw server-daemon` must still pass.
- Any existing scala-cli-level tests that import `iw.core.dashboard.*` types will need updates if CLARIFY 8 lands on package rename.
- `./iw ./test` must still pass end-to-end, including any compile checks over command scripts.

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
- **IW-344 merged** — done (commit 259bc54). `build/iw-core.jar` is available.
- Node + yarn (or npm) installed locally and on CI.
- Mill installed or bootstrappable.
- Web Awesome Pro npm auth configured (pending CLARIFY 3).

### Layer Dependencies
- L0 is a hard prerequisite for L1, L2, L3 (Mill must exist).
- L1 and L2 are independent and can be developed in parallel once L0 lands.
- L3 depends on both L1 (jar to assemble) and L2 (assets to embed).
- L4 depends on L3 (jar must exist to launch).

### External Blockers
- Web Awesome Pro license availability.
- CI runner Node/yarn/Mill provisioning.

## Risks & Mitigations

### Risk 1: Stale `build/iw-core.jar` causes silent compile-against-old-types
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Wire Mill to check jar mtime against core sources, or require `iw-run`'s `ensure_core_jar` to run before any Mill invocation. Document the contract.

### Risk 2: Web Awesome Pro licensing blocks contributors without tokens
**Likelihood:** Medium
**Impact:** High
**Mitigation:** Resolve CLARIFY 3 upfront. Fall back to community components or pure Tailwind if the license cannot be distributed.

### Risk 3: Two build tools confuse `./iw ./test` and contributor onboarding
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Document the division clearly in CLAUDE.md and the top-level README. Ensure `./iw ./test` orchestrates both (or clearly delegates).

### Risk 4: CI self-hosted runner lacks Node/yarn/Mill
**Likelihood:** High
**Impact:** Medium
**Mitigation:** Resolve CLARIFY 6 before merging. Either pre-provision or add install steps to the workflow.

### Risk 5: Package rename (if chosen) breaks untracked references
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** If CLARIFY 8 resolves to "rename," grep the repo for `iw.core.dashboard` and update exhaustively, then run full test suite.

### Risk 6: Vite dev-mode HMR integration is subtler than reference suggests
**Likelihood:** Medium
**Impact:** Low
**Mitigation:** Treat dev-mode as a stretch goal within L4. If it slips, ship bundled-assets-only and follow up.

---

## Implementation Sequence

**Recommended Layer Order:**

1. **L0 Mill Bootstrap** — zero dependencies; nothing else starts until Mill is callable.
2. **L1 Dashboard JVM Module** — unblocks compilation of Scala sources under Mill; prerequisite for L3 assembly.
3. **L2 Frontend Pipeline** — can overlap with L1 since they share no source files; the asset output is what L3 consumes.
4. **L3 Asset Bundling & Fat Jar** — requires both L1 and L2 outputs.
5. **L4 Command Integration & Dev Mode** — flips the user-facing launchers to the new jar; last to minimize churn while earlier layers stabilize.

**Ordering Rationale:**
- Build tooling (L0) must exist before any module definitions (L1, L2).
- Scala compilation (L1) and frontend build (L2) are independent and can be done in either order or in parallel — they only meet at L3.
- L3 is the integration point that produces the artifact L4 launches.
- L4 is intentionally last because it changes observable behavior (`iw dashboard` launch semantics) and should only flip after the jar it launches is proven to work.

## Documentation Requirements

- [ ] Update `CLAUDE.md` with the two-build-tool boundary (scala-cli for core/commands, Mill for dashboard).
- [ ] Top-level README note on Node/yarn/Mill prerequisites.
- [ ] ADR documenting the choice of Mill + Vite + Tailwind + Web Awesome for the dashboard, and why Cask stays.
- [ ] Inline comments in `build.mill` explaining the unmanaged `iw-core.jar` dependency (if we go Option A of CLARIFY 2).
- [ ] Update MEMORY.md patterns once the Mill integration stabilizes.

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders (especially 1, 2, 3, 6, 8 — these shape scope).
2. Run **wf-create-tasks** with the issue ID.
3. Run **wf-implement** for phase-by-phase implementation.
