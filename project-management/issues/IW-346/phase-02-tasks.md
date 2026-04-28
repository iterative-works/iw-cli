# Phase 2 Tasks: CI + Tests + Docs

**Issue:** IW-346
**Phase:** 2 of 2
**Estimate:** 3-6h
**Status:** Not started
**Context:** [phase-02-context.md](phase-02-context.md)

## Guard rails (read first)

- **Decision 4:** `release.yml` MUST run on `self-hosted` with `container: ghcr.io/iterative-works/iw-cli-ci:latest` (mirroring `ci.yml:dashboard-build`). Do NOT split self-hosted build + hosted publish — single-runner trade-off accepted.
- **Decision 5:** Read-only-tarball contract is binding. Do NOT add `./mill`, `.mill-version`, `build.mill`, `out/`, `.bsp/`, `dashboard/jvm/`, or `dashboard/frontend/` to the tarball; do NOT assert their presence in BATS. `RELEASE.md` must explicitly call this out.
- **Out of scope:** Anything Phase 1 already shipped (`scripts/package-release.sh`, `iw-run`, `iw-bootstrap`, `commands/review-state.scala`). New BATS tests beyond the Mill-hidden-from-PATH regression. Splitting `release.yml` across runners.
- **No code changes outside the three target files** (`release.yml`, `bootstrap.bats`, `RELEASE.md`). If verification surfaces an `iw-run` or `package-release.sh` issue, surface it before patching.
- **BATS hardcoded-version drift (pre-existing):** `test/bootstrap.bats` references `iw-cli-0.1.0-dev.tar.gz` everywhere; current `VERSION` is `0.5.0`, so the suite skips on every CI run that doesn't pre-build a `0.1.0-dev` tarball. Do NOT broaden Phase 2 scope to fix this; parameterise on `$(cat VERSION)` only if it falls out trivially while updating the structural assertions. Otherwise log as a follow-up.
- **No refactoring tasks** are planned for Phase 2.

## Setup

- [x] [setup] Confirm the self-hosted runner is reachable and currently servicing `ci.yml`'s `compile`, `format`, `lint`, `test`, and `dashboard-build` jobs (so the same runner pool will pick up the modified `release.yml`).
- [x] [setup] Confirm `ghcr.io/iterative-works/iw-cli-ci:latest` exists in GHCR and the runner has registry credentials via `secrets.GITHUB_TOKEN`.
- [x] [setup] Confirm `WEBAWESOME_NPM_TOKEN` is configured in the repo's secrets (already consumed by `ci.yml:dashboard-build`).
- [x] [setup] Confirm Phase 1 commits are in the working tree (PR #371 merged; `scripts/package-release.sh`, `iw-run`, `iw-bootstrap` reflect Phase 1 layout).
- [x] [setup] Have a freshly-built local tarball available for BATS verification (`./scripts/package-release.sh "$(cat VERSION)"` or `./scripts/package-release.sh 0.1.0-dev` depending on whether the version-parameterisation optional task is taken).

## Implementation — `.github/workflows/release.yml`

- [x] [impl] Change `runs-on: ubuntu-latest` (line 13) to `runs-on: self-hosted`.
- [x] [impl] Add a `container:` block immediately after `runs-on:` with image `ghcr.io/iterative-works/iw-cli-ci:latest` and credentials (`username: ${{ github.actor }}`, `password: ${{ secrets.GITHUB_TOKEN }}`), mirroring `ci.yml:dashboard-build` lines 68-85.
- [x] [impl] Drop the `coursier/setup-action@v1` step (lines 16-19). The container provides Java + scala-cli.
- [x] [impl] Drop the `coursier/cache-action@v6` step (line 20). The container has its own coursier cache.
- [x] [impl] Drop the `Install E2E test dependencies` step (lines 22-26). `tmux`, `jq`, and `bats` are baked into the container.
- [x] [impl] Keep `actions/checkout@v4` (line 15) verbatim.
- [x] [impl] Add `WEBAWESOME_NPM_TOKEN: ${{ secrets.WEBAWESOME_NPM_TOKEN }}` to the `env:` of the `Run all tests` step (lines 28-30) — defensive parity with `ci.yml:test` (lines 60-61).
- [x] [impl] Keep the `Extract version from tag` step (lines 32-42) verbatim.
- [x] [impl] Add an `env:` block to the `Build release tarball` step (lines 44-45) containing `WEBAWESOME_NPM_TOKEN: ${{ secrets.WEBAWESOME_NPM_TOKEN }}`. This is **load-bearing** — `package-release.sh` invokes `./mill ... show dashboard.assembly`, which runs Vite, which fetches Web Awesome from the Pro npm registry.
- [x] [impl] Keep the `Upload to versioned release` step (lines 47-58) verbatim.
- [x] [impl] Keep the `Update vlatest release` step (lines 60-80) verbatim.

## Implementation — `test/bootstrap.bats`

- [x] [impl] In the structure test (currently lines 73-93), drop the `core/Config.scala` assertion at line 87. Phase 1 (Decision 1) stops shipping that file.
- [x] [impl] Keep the `core/project.scala` assertion at line 88 — that's the deps manifest the launcher reads.
- [x] [impl] Add `[ -f "iw-cli-0.1.0-dev/build/iw-core.jar" ]` to the structure test.
- [x] [impl] Add `[ -f "iw-cli-0.1.0-dev/build/iw-dashboard.jar" ]` to the structure test.
- [x] [impl] Add non-empty assertions for both jars (e.g. `[ -s "iw-cli-0.1.0-dev/build/iw-core.jar" ]` and `[ -s "iw-cli-0.1.0-dev/build/iw-dashboard.jar" ]`) — the launcher does a literal path test, drift would silently break installed users.
- [x] [impl] Update the `--bootstrap` test (currently lines 42-54): replace the `[[ "$output" == *"Bootstrap complete"* ]]` assertion (line 53) with `[[ "$output" == *"pre-built jars present"* ]]`. The stricter form doubles as a contract check that bootstrap from a tarball does NOT fall through to Mill.
- [x] [impl] Keep the `[[ "$output" == *"Bootstrapping"* ]]` entry-message assertion (line 52) unchanged.
- [x] [impl] Add a new test `@test "iw-run works without Mill on PATH (bundled jars only)"` exactly as drafted in phase-02-context.md lines 121-141: extracts the tarball, narrows `PATH` to keep `scala-cli`/`java`/`jq` reachable but Mill hidden, runs `./iw-run --list` (assert success and `Available commands` substring) and `./iw-run --bootstrap` (assert success and `pre-built jars present` substring), restores `PATH`. Adjust PATH munging to the CI container's layout if necessary; principle is "remove `./mill` reachability while keeping `scala-cli`, `java`, `jq`".
- [x] [optional] [impl] Parameterise the hardcoded `iw-cli-0.1.0-dev.tar.gz` / `iw-cli-0.1.0-dev/` paths (lines 13, 29, 44, 58, 66, 75-91) on `$(cat "$PROJECT_ROOT/VERSION")` **only if it falls out trivially** while updating the structural assertions. Otherwise leave it and record as a follow-up in `implementation-log.md`. Implementer's call — document the outcome either way.

## Implementation — `RELEASE.md`

- [x] [impl] Rewrite step 2's tarball description (line 26) from `release/iw-cli-0.1.0.tar.gz - Contains iw-run, commands, and core files` to reflect the Phase 1 reality: `release/iw-cli-0.1.0.tar.gz - Contains iw-run, iw-bootstrap, commands, build/iw-core.jar, build/iw-dashboard.jar, and core/project.scala (deps manifest)`.
- [x] [impl] Replace the "Release tarball too large" troubleshooting checklist (lines 112-117) with the actual Phase 1 layout: `iw-run`, `iw-bootstrap`, `VERSION`, `commands/**/*.scala`, `core/project.scala` (deps manifest only — NOT all of `core/`), `build/iw-core.jar`, `build/iw-dashboard.jar`.
- [x] [impl] Add a callout to the same troubleshooting entry that the tarball MUST NOT contain `./mill`, `.mill-version`, `build.mill`, `out/`, `.bsp/`, `dashboard/jvm/`, or `dashboard/frontend/` (Decision 5).
- [x] [impl] Add a new "Tarball Contract" section (placement: after "Distribution Model" at line 108 / before "Troubleshooting" at line 110, OR as a subsection under "Distribution Model"). The section MUST cover:
  - The tarball is a **read-only artifact**: no Mill, no `build.mill`, no `.mill-version`. An extracted tarball cannot rebuild itself.
  - The launcher's three-tier resolution order (env override → `$INSTALL_DIR/build/*.jar` → Mill) means an installed tarball uses rung 2; only dev checkouts use rung 3.
  - For development, clone the repo (`git clone iterative-works/iw-cli`); do not use an extracted tarball as a workspace.
  - Failure mode: extracting the tarball into a workspace and running `./mill` yields "command not found" — a clear signal, not a partial-build error.
- [x] [impl] Reframe the "Pre-compilation fails" troubleshooting entry (lines 125-129): on an installed tarball `--bootstrap` is a verify-only step (not a compile step), so this entry is now mostly about dev-checkout flows. Either adjust the wording to reflect that, or reframe as "Bootstrap reports missing jars" with guidance to re-extract the tarball. Implementer's call on exact wording.
- [x] [optional] [impl] Add a brief paragraph after step 2 listing the tarball's `build/` contents for auditors (pulls from `analysis.md:129`).
- [x] [optional] [impl] Add Mill / Node 20 / Yarn / `WEBAWESOME_NPM_TOKEN` to the prerequisites for running `package-release.sh` locally, near step 2 (pulls from `analysis.md:128`).

## Verification

- [x] [verify] **Local BATS run** against a freshly-built tarball: `./iw ./test e2e` (or `bats test/bootstrap.bats` directly). The updated structure test, the updated `--bootstrap` test, and the new "iw-run works without Mill on PATH" test must all pass.
- [ ] [optional] [verify] **Local CI-container dry run** if practical: pull `ghcr.io/iterative-works/iw-cli-ci:latest`, mount the working tree, and run the BATS suite inside the container to mirror CI conditions.
- [ ] [verify] **Release-candidate tag dry run (non-negotiable per Risk 1 mitigation).** Push `v0.5.1-rc1` (or use `workflow_dispatch` against the branch) to trigger `release.yml` end-to-end. Confirm:
  - The workflow completes without manual intervention.
  - `gh release download v0.5.1-rc1` retrieves `iw-cli-0.5.1-rc1.tar.gz`.
  - Extracting the downloaded tarball on a host that does NOT have Mill provisioned and running `./iw-run --list` and `./iw-run --bootstrap` both succeed.
  - The `vlatest` rolling-tag flow (release.yml lines 60-80) still works (smoke test — those steps were not modified).
- [ ] [verify] Inspect the `Build release tarball` step's CI log to confirm `package-release.sh`'s tail-of-script `tar -tzf … | grep -E '^iw-cli-[^/]+/build/'` lists both jars.
- [x] [verify] Record outcomes in `implementation-log.md`: BATS results, RC tag dry-run results, and explicit disposition of the BATS hardcoded-version drift (fixed in this phase or punted to a follow-up).

## Acceptance Checklist

- [x] `.github/workflows/release.yml` runs on `self-hosted` with `container: ghcr.io/iterative-works/iw-cli-ci:latest` (mirroring `ci.yml:dashboard-build`).
- [x] `WEBAWESOME_NPM_TOKEN` is in `env:` for the `Build release tarball` step (so Mill's `dashboard.assembly` Vite build can fetch Web Awesome).
- [x] The existing version-extraction step (current lines 32-42), the `Upload to versioned release` step (lines 47-58), and the `Update vlatest release` step (lines 60-80) continue to work unmodified — no regressions in the publish flow.
- [x] `coursier/setup-action`, `coursier/cache-action`, and the manual `apt-get install tmux jq` + bats source-build are removed from `release.yml` (the container provides these).
- [x] BATS structure test (`test/bootstrap.bats`) asserts presence of `build/iw-core.jar`, `build/iw-dashboard.jar`, and `core/project.scala`; does NOT assert `core/Config.scala`.
- [x] BATS `--bootstrap` test passes against a Phase 1 tarball, asserting on the rung-2 success message (`pre-built jars present` substring) rather than the legacy `Bootstrap complete` substring.
- [x] New BATS regression test: with Mill hidden from PATH but `scala-cli`/`java`/`jq` still reachable, `./iw-run --list` and `./iw-run --bootstrap` both succeed against an extracted tarball.
- [x] `RELEASE.md` step 2 description (line 26) updated to reflect the new tarball contents (build jars + `core/project.scala`).
- [x] `RELEASE.md` "Release tarball too large" troubleshooting list (lines 112-117) updated to match the Phase 1 layout.
- [x] `RELEASE.md` includes a new section (or subsection under "Distribution Model") documenting the read-only-tarball contract: no Mill in the tarball, dev work uses a clone.
- [x] Decision 4 honoured: `release.yml` uses `self-hosted` + `iw-cli-ci` container.
- [x] Decision 5 honoured: read-only-tarball contract is explicit in `RELEASE.md` and the BATS structure test continues to NOT assert presence of `./mill`, `.mill-version`, or `build.mill`.
- [ ] BATS suite green locally (`./iw ./test e2e`) AND in CI (the `test` job under `ci.yml`, or the `Run all tests` step in `release.yml`).
- [ ] Release-candidate tag (`v0.5.1-rc1` or `workflow_dispatch`) dry run produces a working tarball and `gh release download` retrieves it; extraction + `./iw-run --bootstrap` on a Mill-less host succeeds.
- [x] BATS hardcoded-version drift (`0.1.0-dev` vs `0.5.0`) either fixed or recorded as a follow-up in the implementation log — explicitly acknowledged either way.

**Phase Status:** Complete
