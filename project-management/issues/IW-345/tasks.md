# Implementation Tasks: Layer 0 — Set up Mill module for dashboard with Vite + Tailwind + Web Awesome

**Issue:** IW-345
**Created:** 2026-04-21
**Status:** 2/4 phases complete (50%)

## Phase Index

- [x] Phase 1: Mill bootstrap + core jar production (Est: 3-5h) → `phase-01-context.md`
- [x] Phase 2: Dashboard JVM module — move, rename, test migration (Est: 4-7h) → `phase-02-context.md`
- [ ] Phase 3: Frontend pipeline + fat-jar assembly + integration tests (Est: 5-9h) → `phase-03-context.md`
- [ ] Phase 4: Command integration + dev mode (Est: 3-5h) → `phase-04-context.md`

## Progress Tracker

**Completed:** 2/4 phases
**Estimated Total:** 15-26 hours
**Time Spent:** 0 hours

## Phase Summary

### Phase 1: Mill bootstrap + core jar production
Covers analysis layer **L0**. Establishes Mill 1.1.5 in the repo (committed `./mill` launcher, `.mill-version`, `build.mill` with a `core` module), flips `iw-run`'s `build_core_jar()` from scala-cli to Mill, and extends the CI Docker image with Node 20 + Corepack + Mill. All existing tests stay green; no dashboard-facing changes.

### Phase 2: Dashboard JVM module
Covers analysis layer **L1**. Pure code-reorganization phase: move `core/dashboard/**` → `dashboard/jvm/src/**`, rename `iw.core.dashboard.*` → `iw.dashboard.*` across 89 files, migrate ~20 dashboard-touching tests from `core/test/` to `dashboard/jvm/test/src/`, move dashboard-only deps (cask, scalatags, flexmark, scalatags-webawesome 3.2.1.1) out of `core/project.scala` into the Mill `dashboard` module. Mill `dashboard.test` runs the migrated unit tests. Dashboard still runs from the in-process scala-cli path as a temporary bridge.

### Phase 3: Frontend pipeline + fat-jar assembly + integration tests
Covers analysis layers **L2 + L3** (merged — they form a single conceptual unit). Frontend pipeline: `dashboard/frontend/` with Yarn 4 via Corepack (`packageManager` pin in `package.json`), `.yarnrc.yml` with Web Awesome registry auth via `WEBAWESOME_NPM_TOKEN`, Vite + Tailwind v4 + cherry-picked Web Awesome components. Assembly: Mill task graph wires `frontend.viteBuild` output into `dashboard.resources` before `dashboard.assembly` produces `build/iw-dashboard.jar`. Integration tests: new `object itest extends ScalaTests` on the dashboard module, launches the assembled jar and asserts asset resolution from classpath. CI gets a new `dashboard-build` job exposing `WEBAWESOME_NPM_TOKEN`.

### Phase 4: Command integration + dev mode
Covers analysis layer **L4**. User-facing cutover: `commands/dashboard.scala` and `commands/server-daemon.scala` rewritten to spawn `java -jar build/iw-dashboard.jar`. `core/adapters/ProcessManager.scala:115` FQCN string literal updated or removed. `iw-run` gains `ensure_dashboard_jar` / `needs_dashboard_rebuild` helpers. Double-gated dev mode: `--dev` flag AND `VITE_DEV_URL` env var both required to activate; loopback host validation on startup; loud logging when dev mode is active. Single `assetUrl` template helper for prod/dev asset paths. Docs: CLAUDE.md two-build-tool boundary, README prereqs for dashboard development.

## Notes

- Phase context files (`phase-NN-context.md`) are generated just-in-time at the start of each phase by `wf-implement`.
- Phases land strictly in sequence; each phase produces a fully green tree (all 141 existing tests passing, new tests added per phase).
- Estimates are ranges; time spent is tracked against the low-end for calibration.
- Per analysis, Phase 2 carries the largest mechanical diff (89-file rename + 20 test migrations) — reviewers should focus on rename correctness here without also reviewing build-tool or frontend work.
- Dependency on external secret: Phase 3 onward requires `WEBAWESOME_NPM_TOKEN` set in CI and locally.
