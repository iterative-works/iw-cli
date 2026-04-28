# Phase 2: CI + Tests + Docs

**Issue:** IW-346
**Estimate:** 3-6h
**Status:** Not started
**Predecessor:** Phase 1 (Packaging + Launcher + Bootstrap) — MERGED via PR #371
**Successor:** none (final phase)

## Goals

- Update `.github/workflows/release.yml` so CI builds the pre-built jars on `v*` tag push and publishes the new tarball layout reproducibly (Decision 4).
- Update `test/bootstrap.bats` to assert the new tarball structure and add the regression test that locks in Phase 1's "no Mill needed when `build/*.jar` is present" contract.
- Update `RELEASE.md` to describe the new tarball contents and the read-only-tarball contract (Decision 5).
- After this phase: a `v*` tag push produces a working tarball end-to-end, BATS asserts the contract that protects against the Phase 1 regression, and the docs accurately describe the install flow.

## Scope

**In scope (this phase):**
- `.github/workflows/release.yml` — switch to `self-hosted` + `iw-cli-ci` container; add `WEBAWESOME_NPM_TOKEN` to the env of the build step; ensure jar building happens before `package-release.sh` runs (Phase 1's `package-release.sh` already drives Mill itself, so the workflow change is mostly toolchain provisioning).
- `test/bootstrap.bats` — update structural assertions for `build/iw-core.jar` / `build/iw-dashboard.jar` and `core/project.scala` (drop `core/Config.scala` per Decision 1); update the `--bootstrap` success-message assertion to match Phase 1's new output; add a Mill-hidden-from-PATH regression test that runs `./iw-run --list` and `./iw-run --bootstrap` against an extracted tarball and confirms both succeed.
- `RELEASE.md` — update step 2's tarball description, the troubleshooting checklist for "Release tarball too large", and add a new section documenting the read-only-tarball contract (Decision 5).
- (Optional) Release notes content for the next minor version — leave a stub if not authoring full notes here.

**Explicitly out of scope:**
- Anything Phase 1 already shipped (`scripts/package-release.sh`, `iw-run`, `iw-bootstrap`, `commands/review-state.scala`).
- Broadening fixes to `test/bootstrap.bats`'s hardcoded `0.1.0-dev` filename pattern. Current `VERSION` is `0.5.0`, so the BATS file already skips on every CI run that doesn't pre-build a `0.1.0-dev` tarball; this is a separate concern and noted in Risks below. Implementer's call whether to parameterise on `$(cat VERSION)` if it falls out trivially from the structural assertion update; otherwise leave it as a follow-up.
- Adding new BATS tests beyond the Mill-hidden-from-PATH regression (and the structural assertion updates).
- Splitting `release.yml` across self-hosted build + hosted publish — Decision 4 explicitly accepts the self-hosted-only trade-off; revisit only if reliability bites.

## Dependencies

**Phase 2 depends on Phase 1 producing (already shipped via PR #371):**
- A working `scripts/package-release.sh` that resolves `core.jar` and `dashboard.assembly` via `./mill --ticker false show`, copies them into `build/iw-core.jar` and `build/iw-dashboard.jar`, ships only `core/project.scala` from `core/`, and verifies the tarball contains all three. The CI step just calls this script with a version arg.
- A launcher (`iw-run`) whose `ensure_core_jar` / `ensure_dashboard_jar` honour `$INSTALL_DIR/build/*.jar` (rung 2). This is what the Mill-hidden-from-PATH regression test exercises.
- The `bootstrap()` success message: `iw-cli is ready (pre-built jars present at $INSTALL_DIR/build/).` when rung 2 hit, or the existing `Bootstrap complete. iw-cli is ready for offline use.` in dev mode. The BATS bootstrap assertion needs to accept the new message.

**External prerequisites (already in place — no setup work):**
- The self-hosted runner is up and was used by `ci.yml`'s `compile`, `format`, `lint`, `test`, and `dashboard-build` jobs.
- `WEBAWESOME_NPM_TOKEN` is in the repo's secrets (already consumed by `ci.yml:dashboard-build`).
- `ghcr.io/iterative-works/iw-cli-ci:latest` is published and the GitHub Actions runner has registry credentials via `secrets.GITHUB_TOKEN`.

## Approach

The merged-layer strategy walks one cleavage line ("automate, verify, document"):

1. **CI Layer makes Phase 1 reproducible.** `release.yml` today runs on `ubuntu-latest` with only `temurin:21` + `scala-cli` and a hand-rolled apt install of `tmux jq` and `bats-core` source-build. With Phase 1's `package-release.sh` now invoking `./mill` and indirectly Vite (via `dashboard.assembly`), `ubuntu-latest` would need Mill provisioning, Node 20, Yarn via Corepack, and the Web Awesome registry token. Decision 4 picks the simpler resolution: switch to `self-hosted` + `ghcr.io/iterative-works/iw-cli-ci:latest`, mirroring `ci.yml:dashboard-build` (the working reference for the container pattern). The container ships Mill, Node, Yarn, scala-cli, tmux, jq, and bats; the workflow becomes a thin wrapper around the existing build script.

2. **Test Layer locks in Phase 1's contract.** Two-line update for the structural assertion (drop `core/Config.scala`, add `build/iw-core.jar` and `build/iw-dashboard.jar`); one-line update for the bootstrap-success assertion (the Phase 1 launcher prints either the legacy `Bootstrap complete` message or the new `iw-cli is ready (pre-built jars present …)` message); one new test that hides Mill from PATH and confirms `./iw-run --list` and `./iw-run --bootstrap` succeed. That last test is the regression contract: if a future change re-introduces a Mill dependency in the launcher's resolution path, this test fails.

3. **Docs Layer describes the new state.** Step 2 in `RELEASE.md` currently describes the tarball as "iw-run, commands, and core files" — that's stale for two reasons (we now ship build jars, and we narrowed `core/` to just `project.scala`). The "Release tarball too large" troubleshooting list is similarly stale. And Decision 5's read-only-tarball contract needs an explicit section so future maintainers don't drift back to shipping `./mill` or `build.mill`.

4. **No code changes outside the three files.** `iw-run`, `iw-bootstrap`, `scripts/package-release.sh` were all settled in Phase 1. If something uncovered during Phase 2 testing demands a code change, surface it as a Phase 2 finding before implementing.

## Component specifications

### CI Layer — `.github/workflows/release.yml`

**Files to modify:**
- `.github/workflows/release.yml`

**Concrete YAML changes (mirror `ci.yml:dashboard-build` at lines 68-85):**
- `runs-on: ubuntu-latest` (line 13) → `runs-on: self-hosted`.
- Add a `container:` block immediately after `runs-on`:
  ```yaml
  container:
    image: ghcr.io/iterative-works/iw-cli-ci:latest
    credentials:
      username: ${{ github.actor }}
      password: ${{ secrets.GITHUB_TOKEN }}
  ```
- Drop the `coursier/setup-action@v1` step (lines 16-19) and the `coursier/cache-action@v6` step (line 20). The `iw-cli-ci` container already has Java + scala-cli baked in.
- Drop the `Install E2E test dependencies` step (lines 22-26) — `tmux`, `jq`, and `bats` are all in the container.
- Keep `actions/checkout@v4` (line 15).
- Keep the `Run all tests` step (lines 28-30) — but add the same `WEBAWESOME_NPM_TOKEN` env var that `ci.yml:test` uses (lines 60-61), so the test job runs in a fully-toolchained environment if any test ends up touching the dashboard. (Defensive; tests today don't, but this matches the `ci.yml:test` pattern.)
- Keep the `Extract version from tag` step (lines 32-42) verbatim — version-extraction logic is not affected by the container switch.
- For the `Build release tarball` step (lines 44-45), add an `env:` block:
  ```yaml
  env:
    WEBAWESOME_NPM_TOKEN: ${{ secrets.WEBAWESOME_NPM_TOKEN }}
  ```
  This is the load-bearing change — `package-release.sh` invokes `./mill ... show dashboard.assembly`, which triggers a Vite build that fetches Web Awesome from the Pro npm registry. Without the token in env, the Vite step fails and no tarball is produced.
- Keep the `Upload to versioned release` and `Update vlatest release` steps (lines 47-80) verbatim — they only consume `release/iw-cli-${VERSION}.tar.gz` and `iw-bootstrap`.

**What NOT to add:**
- No explicit `./mill dashboard.assembly` build step before `package-release.sh`. Phase 1's `package-release.sh` already invokes Mill internally; running Mill twice wastes 30+ seconds with no benefit. (If we ever decompose `package-release.sh` to take pre-built jars via `IW_*_JAR` env vars, that's a future refactor — out of scope here.)
- No `coursier/cache-action`. The container has its own `~/.cache/coursier` and the workflow doesn't run frequently enough for cache hit-rate to matter.

**Verification in CI logs:**
- The existing `tar -tzf "$tarball" | head -30` and the `tar -tzvf … | grep -E "iw-cli-${VERSION}/build/"` guard at the tail of `package-release.sh` (lines 91-102) are visible in the workflow's `Build release tarball` step output. Reviewers can confirm both jars are in the tarball directly from the action log.

### Test Layer — `test/bootstrap.bats`

**Files to modify:**
- `test/bootstrap.bats`

**Update the structure test (currently lines 73-93):**
- Drop the `core/Config.scala` assertion at line 87 — Phase 1 (Decision 1) stops shipping that file.
- Keep the `core/project.scala` assertion at line 88 — that's the deps manifest the launcher reads.
- Add two new assertions for the pre-built jars:
  ```
  [ -f "iw-cli-0.1.0-dev/build/iw-core.jar" ]
  [ -f "iw-cli-0.1.0-dev/build/iw-dashboard.jar" ]
  ```
  And verify both are non-empty (e.g. `[ -s "iw-cli-0.1.0-dev/build/iw-core.jar" ]`). The launcher does a literal path test on these names — drift would silently break installed users.

**Update the `--bootstrap` test (currently lines 42-54):**
- Phase 1's `bootstrap()` prints either `Bootstrap complete. iw-cli is ready for offline use.` (dev fallback, no `build/`) or `iw-cli is ready (pre-built jars present at $INSTALL_DIR/build/).` (rung 2, when `build/iw-core.jar` exists). For an extracted release tarball, rung 2 always fires.
- The current assertion `[[ "$output" == *"Bootstrap complete"* ]]` (line 53) won't match the rung-2 message. Update to accept either:
  ```
  [[ "$output" == *"is ready"* ]]
  ```
  Or more strictly, assert the rung-2 message specifically since that's the path an extracted tarball must take:
  ```
  [[ "$output" == *"pre-built jars present"* ]]
  ```
  Pick the stricter form — it doubles as a contract check that bootstrap from a tarball does NOT fall through to Mill.
- Keep the `[[ "$output" == *"Bootstrapping"* ]]` assertion (line 52) — the entry message is unchanged.

**Add a new regression test (Decision 3 / Risk 2 lock-in):**

```bats
@test "iw-run works without Mill on PATH (bundled jars only)" {
    tar -xzf "$PROJECT_ROOT/release/iw-cli-0.1.0-dev.tar.gz"
    cd iw-cli-0.1.0-dev

    # Hide Mill from PATH; keep core utilities + scala-cli + java
    local saved_path="$PATH"
    PATH="/usr/bin:/bin:$(dirname "$(command -v scala-cli)"):$(dirname "$(command -v java)"):$(dirname "$(command -v jq)")"
    export PATH

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Available commands"* ]]

    run ./iw-run --bootstrap
    [ "$status" -eq 0 ]
    [[ "$output" == *"pre-built jars present"* ]]

    PATH="$saved_path"
}
```

The exact PATH munging may need adjustment based on the CI container's layout; the principle is "remove `./mill` reachability while keeping `scala-cli`, `java`, `jq`". An alternative is `PATH=/usr/bin:/bin command -v mill` in the assertion to confirm Mill is unreachable, then run the launcher commands — pick whichever the implementer finds most robust.

**The hardcoded `0.1.0-dev` version pattern:** lines 13, 29, 44, 58, 66, 75-91 all reference `iw-cli-0.1.0-dev.tar.gz` / `iw-cli-0.1.0-dev/`. Current `VERSION` is `0.5.0`. The BATS suite already skips when that filename isn't present (line 13: `skip "Release package not found"`). This is pre-existing drift from before IW-346; do NOT broaden Phase 2's scope to fix unless it falls out trivially while updating the structural assertions. If the implementer chooses to parameterize on `$(cat "$PROJECT_ROOT/VERSION")`, that's acceptable but strictly optional. Otherwise: note as a separate follow-up in the implementation log.

### Docs Layer — `RELEASE.md`

**Files to modify:**
- `RELEASE.md`

**Update step 2's description (line 26):**
Currently:
```
- `release/iw-cli-0.1.0.tar.gz` - Contains iw-run, commands, and core files
```
Replace with something that reflects the Phase 1 reality, e.g.:
```
- `release/iw-cli-0.1.0.tar.gz` - Contains iw-run, iw-bootstrap, commands, build/iw-core.jar, build/iw-dashboard.jar, and core/project.scala (deps manifest)
```

**Update "Release tarball too large" troubleshooting (lines 112-117):**
Currently lists "iw-run script / VERSION / commands/*.scala / core/*.scala". Replace with the actual Phase 1 layout:
- `iw-run`, `iw-bootstrap`
- `VERSION`
- `commands/**/*.scala`
- `core/project.scala` (deps manifest only — NOT all of `core/`)
- `build/iw-core.jar`, `build/iw-dashboard.jar`

Add a callout that the tarball MUST NOT contain `./mill`, `.mill-version`, `build.mill`, `out/`, `.bsp/`, `dashboard/jvm/`, or `dashboard/frontend/` (Decision 5).

**Add a new section: "Tarball Contract"** (placement: after "Distribution Model" at line 108, before "Troubleshooting" at line 110, OR as a subsection under "Distribution Model" — implementer's call):

The section MUST cover:
- The tarball is a **read-only artifact**: no Mill, no `build.mill`, no `.mill-version`. An extracted tarball cannot rebuild itself.
- The launcher's three-tier resolution order (env override → `$INSTALL_DIR/build/*.jar` → Mill) means an installed tarball uses rung 2; only dev checkouts use rung 3.
- For development, clone the repo (`git clone iterative-works/iw-cli`); do not use an extracted tarball as a workspace.
- Failure mode: if a user extracts the tarball into a workspace and tries `./mill`, they get "command not found" — a clear signal, not a partial-build error.

Cite Decision 5 from `analysis.md` if helpful, but keep the wording user-facing (no "Decision N" labels in `RELEASE.md` itself).

**Update the "Pre-compilation fails" troubleshooting entry (lines 125-129):**
Currently mentions `scala-cli` and `project.scala`. With Phase 1, `--bootstrap` on an installed tarball is a verify-only step, not a compile step. Adjust the entry to reflect that "pre-compilation fails" is now mostly about dev-checkout flows (where Mill drives the build) — or reframe as "Bootstrap reports missing jars" with guidance to re-extract the tarball. Implementer's call on the exact wording.

**Optional polish:**
- Brief paragraph after step 2 listing the tarball's `build/` contents for auditors. Pulls from `analysis.md:129`.
- Add Mill / Node 20 / Yarn / `WEBAWESOME_NPM_TOKEN` to the prerequisites for running `package-release.sh` locally (somewhere near step 2). Pulls from `analysis.md:128`.

## API contracts between layers

- **CI Layer → consumers:** the `release.yml` workflow produces a `release/iw-cli-${VERSION}.tar.gz` with the same structure that Phase 1's `package-release.sh` produces (and that Phase 1's manual verification confirmed). Any consumer (`iw-bootstrap`, BATS tests, manual extraction) sees the same layout in CI as locally.
- **Test Layer → CI:** the BATS suite is the regression gate that protects the launcher's "no Mill needed when `build/*.jar` present" contract. The Mill-hidden-from-PATH test is the explicit assertion of that contract; the structure test asserts the contract on the packaging side. CI's `test` job (or the equivalent in `release.yml`'s `Run all tests` step) executes the BATS suite, so any drift in either packaging or launcher fails fast.
- **Docs Layer → maintainers/users:** `RELEASE.md` accurately describes the install flow as of Phase 1, including the read-only-tarball contract. A new maintainer following `RELEASE.md` step-by-step produces the same release CI does.

## Files to modify

- `/home/mph/Devel/iw/iw-cli-IW-346/.github/workflows/release.yml`
- `/home/mph/Devel/iw/iw-cli-IW-346/test/bootstrap.bats`
- `/home/mph/Devel/iw/iw-cli-IW-346/RELEASE.md`

No other files are expected to change in Phase 2. If verification turns up an `iw-run` or `package-release.sh` issue, surface it before patching.

## Testing strategy for Phase 2

**1. BATS suite locally and in CI.**
- Locally: `./iw ./test e2e` (or `bats test/bootstrap.bats` directly) with a freshly-built `release/iw-cli-${VERSION}.tar.gz`. The structure test, `--bootstrap` test, and the new Mill-hidden-from-PATH test must all pass.
- CI: the `release.yml` workflow's `Run all tests` step (or the `ci.yml` `test` job for PR validation) runs BATS in the `iw-cli-ci` container. Confirm green on the PR.
- The hardcoded-version drift means BATS today skips on every CI run that doesn't pre-build a `0.1.0-dev` tarball. Either parameterise the BATS file on `$(cat VERSION)` or arrange the `Run all tests` step to call `scripts/package-release.sh 0.1.0-dev` first. Implementer's call; recommend the parameterise route since it removes future drift.

**2. Release-candidate tag dry run.**
- Push a `v0.5.1-rc1` tag (or use `workflow_dispatch` against the branch) to trigger `release.yml` end-to-end.
- Confirm the workflow completes without manual intervention. Inspect the resulting `release/iw-cli-0.5.1-rc1.tar.gz` via `gh release download`.
- Extract the downloaded tarball, run `./iw-run --list` and `./iw-run --bootstrap` on a host that does NOT have Mill provisioned. Both must succeed.
- Verify the `vlatest` rolling-tag flow still works (`release.yml` lines 60-80) — the upload steps were not modified, so this is mostly a smoke test.

**3. The Mill-hidden-from-PATH BATS test is the Phase 1 contract guard.**
- It asserts that `./iw-run --list` and `./iw-run --bootstrap` succeed without any Mill on PATH, against an extracted tarball. If a future change re-introduces a Mill dependency in the launcher's resolution path (e.g. someone removes the rung-2 `build/iw-core.jar` check), this test fails immediately.
- This test is the regression gate that protects against the latent bug the analysis identified: "extracted tarball has no `./mill`, so `mill_jar_path` fails". With Phase 1's pre-built jars and rung-2 resolution, that path is unreachable; the BATS test asserts it stays unreachable.

## Risks specific to Phase 2

### Risk 1 (analysis): `release.yml` builds break because Mill/Node/Yarn provisioning is incomplete
- **Likelihood:** Medium.
- **Impact:** High — blocks all releases.
- **Mitigation:** Decision 4 (use the `iw-cli-ci` container) eliminates the provisioning question entirely, since the container already passes `ci.yml:dashboard-build`. The remaining risk is the YAML mechanics of switching `runs-on` and adding the `container:` block. **Test via release-candidate tag (`v0.5.1-rc1`) before any real release.** This is a non-negotiable verification step; merging Phase 2 without a green RC dry run leaves the next real release to chance.

### Risk 4 (analysis): `WEBAWESOME_NPM_TOKEN` leak into tarball or build logs
- **Likelihood:** Low.
- **Impact:** High — license credential exposure.
- **Mitigation:** The token is consumed only at Vite build time inside Mill's `dashboard.assembly` task; the resulting jar contains compiled assets, not the token, and `package-release.sh` only copies the resolved jar, not any source files Vite touched. Defense-in-depth idea from `analysis.md:342`: add a guard step in `package-release.sh` that greps the staged tarball directory for the token value before tarring. **Optional in Phase 2** — implementer's call. Recommend adding it as a one-liner if it's trivial to express; otherwise log as a follow-up.
- GitHub Actions automatically masks secret values in workflow logs, so the build step's stdout/stderr is safe even if Vite or Mill ever printed the token.

### BATS test version drift (pre-existing)
- The hardcoded `iw-cli-0.1.0-dev.tar.gz` paths in `test/bootstrap.bats` (lines 13, 29, 44, 58, 66, 75-91) are stale; current `VERSION` is `0.5.0`. The setup hook on line 13 skips the entire suite when the file isn't present, so today the BATS tests effectively no-op in CI unless someone pre-builds the `0.1.0-dev` tarball.
- **Phase 2 disposition:** parameterise on `$(cat "$PROJECT_ROOT/VERSION")` if it falls out trivially while updating the structural assertion. Otherwise: note as a separate follow-up in the implementation log; do NOT broaden Phase 2's scope. Either way, the Phase 2 workflow change should ensure the BATS suite actually runs against a real tarball — most likely by adding a `scripts/package-release.sh "$(cat VERSION)"` step before `Run all tests` in `release.yml`, OR by accepting that the suite runs against `0.1.0-dev` and the workflow pre-builds that name.

(Risks 2 and 3 from the analysis were Phase 1 concerns and are settled.)

## Acceptance criteria

- [ ] `.github/workflows/release.yml` runs on `self-hosted` with `container: ghcr.io/iterative-works/iw-cli-ci:latest` (mirroring `ci.yml:dashboard-build`).
- [ ] `WEBAWESOME_NPM_TOKEN` is in `env:` for the `Build release tarball` step (so Mill's `dashboard.assembly` Vite build can fetch Web Awesome).
- [ ] The existing version-extraction step (current lines 32-42), the `Upload to versioned release` step (lines 47-58), and the `Update vlatest release` step (lines 60-80) continue to work unmodified — no regressions in the publish flow.
- [ ] `coursier/setup-action`, `coursier/cache-action`, and the manual `apt-get install tmux jq` + bats source-build are removed from `release.yml` (the container provides these).
- [ ] BATS structure test (`test/bootstrap.bats`) asserts presence of `build/iw-core.jar`, `build/iw-dashboard.jar`, and `core/project.scala`; does NOT assert `core/Config.scala`.
- [ ] BATS `--bootstrap` test passes against a Phase 1 tarball, asserting on the rung-2 success message (`pre-built jars present` substring) rather than the legacy `Bootstrap complete` substring.
- [ ] New BATS regression test: with Mill hidden from PATH but `scala-cli`/`java`/`jq` still reachable, `./iw-run --list` and `./iw-run --bootstrap` both succeed against an extracted tarball.
- [ ] `RELEASE.md` step 2 description (line 26) updated to reflect the new tarball contents (build jars + `core/project.scala`).
- [ ] `RELEASE.md` "Release tarball too large" troubleshooting list (lines 112-117) updated to match the Phase 1 layout.
- [ ] `RELEASE.md` includes a new section (or subsection under "Distribution Model") documenting the read-only-tarball contract: no Mill in the tarball, dev work uses a clone.
- [ ] Decision 4 honoured: `release.yml` uses `self-hosted` + `iw-cli-ci` container.
- [ ] Decision 5 honoured: read-only-tarball contract is explicit in `RELEASE.md` and the BATS structure test continues to NOT assert presence of `./mill`, `.mill-version`, or `build.mill`.
- [ ] BATS suite green locally (`./iw ./test e2e`) AND in CI (the `test` job under `ci.yml`, or the `Run all tests` step in `release.yml`).
- [ ] Release-candidate tag (`v0.5.1-rc1` or `workflow_dispatch`) dry run produces a working tarball and `gh release download` retrieves it; extraction + `./iw-run --bootstrap` on a Mill-less host succeeds.
- [ ] BATS hardcoded-version drift (`0.1.0-dev` vs `0.5.0`) either fixed or recorded as a follow-up in the implementation log — explicitly acknowledged either way.
