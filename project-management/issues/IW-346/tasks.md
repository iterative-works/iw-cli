# Implementation Tasks: Layer 0 — Update release packaging for built artifacts

**Issue:** IW-346
**Created:** 2026-04-27
**Status:** 0/2 phases complete (0%)

## Phase Index

- [ ] Phase 1: Packaging + Launcher + Bootstrap (Est: 2.5-5.5h) → `phase-01-context.md`
- [ ] Phase 2: CI + Tests + Docs (Est: 3-6h) → `phase-02-context.md`

## Progress Tracker

**Completed:** 0/2 phases
**Estimated Total:** 5.5-11.5 hours
**Time Spent:** 0 hours

## Phase Composition

Each phase merges multiple architectural layers from the analysis. The dependency cleavage is "produce the artifact" / "automate, verify, document it":

- **Phase 1** merges:
  - **Packaging Layer** (`scripts/package-release.sh`) — build jars via Mill, copy to `build/`, ship only `core/project.scala` (per Decision 1).
  - **Launcher Layer** (`iw-run`) — extend `ensure_core_jar` / `ensure_dashboard_jar` with `$INSTALL_DIR/build/*.jar` lookup (Decision 3, pure file-presence); `--bootstrap` becomes verify-only when jars present.
  - **Bootstrap Layer** (`iw-bootstrap`) — verify-only; no structural changes expected.
  - **Audit gate**: confirm nothing reads `core/**/*.{scala,css,js}` at runtime beyond `project.scala` (Decision 1's risk mitigation).

- **Phase 2** merges:
  - **CI Layer** (`.github/workflows/release.yml`) — switch to `self-hosted` + `ghcr.io/iterative-works/iw-cli-ci:latest` container, add `WEBAWESOME_NPM_TOKEN`, build jars before packaging (Decision 4).
  - **Test Layer** (`test/bootstrap.bats`) — update structural assertions for `build/*.jar`, drop `core/Config.scala` assertion, add Mill-not-on-PATH regression test.
  - **Docs Layer** (`RELEASE.md`) — update tarball contents description and add the read-only-tarball contract (Decision 5).

## Notes

- Phase context files generated just-in-time during implementation.
- Use `wf-implement` to start the next phase automatically.
- Estimates are rough and will be refined during implementation.
- Phases follow dependency order: Phase 1 produces a working tarball locally; Phase 2 makes it reproducible from CI, locked in by tests, and documented.
- All five CLARIFY markers from the analysis are resolved (Decisions 1-5 in `analysis.md`); no open uncertainties.
- Sub-3h layers (Bootstrap 0-0.5h, Test 1-2h, Docs 0.5-1h) were merged with adjacent layers per the phase-size floor policy.
