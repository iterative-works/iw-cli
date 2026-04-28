# Phase 1: Packaging + Launcher + Bootstrap

**Issue:** IW-346
**Estimate:** 2.5-5.5h
**Status:** Not started
**Predecessor:** none (first phase)
**Successor:** Phase 2 (CI + Tests + Docs)

## Goals

- Produce a release tarball that ships pre-built jars at `build/iw-core.jar` and `build/iw-dashboard.jar`, so an extracted install needs only `scala-cli` + a JRE.
- Teach `iw-run` to consume those jars when present, falling back to Mill only in dev checkouts.
- Make `iw-run --bootstrap` a verify-only step on installed tarballs (no Mill invocation), preserving the dev compile path when run from a checkout.
- Establish locally (manually) that the produce/consume cycle works end-to-end before Phase 2 automates and tests it.

## Scope

**In scope (this phase):**
- `scripts/package-release.sh` — build jars via Mill, drop them into `build/`, ship only `core/project.scala`.
- `iw-run` — extend `ensure_core_jar`, `ensure_dashboard_jar`, `bootstrap` with `$INSTALL_DIR/build/*.jar` lookup; add a comment block documenting the resolution order and the `out/` vs `build/` vs `.scala-build/` convention.
- `iw-bootstrap` — verify-only inspection; no structural change expected. Optional defensive post-extract jar presence check (see Component specs below) is acceptable but not required.
- Audit gate: confirm nothing under `commands/` or `core/` reads `core/**/*.{scala,css,js}` at runtime beyond `core/project.scala`.
- Manual local verification of the end-to-end flow.

**Explicitly deferred to Phase 2:**
- `.github/workflows/release.yml` (CI) — Decision 4.
- `test/bootstrap.bats` updates (structural assertions, Mill-not-on-PATH regression test).
- `RELEASE.md` doc updates and the read-only-tarball contract from Decision 5.

## Dependencies

**Phase 1 depends on:**
- IW-344 merged (commit 259bc54) — provides `core.jar` Mill task.
- IW-345 merged (commit 8a9b603) — provides `dashboard.assembly` Mill task.
- Local toolchain to run `package-release.sh` end-to-end: Mill (via `./mill`), Node 20 + Yarn via Corepack, and `WEBAWESOME_NPM_TOKEN` exported in env. Without these, the packaging step will fail at the Vite build.
- `scala-cli`, `jq`, `tar`, `rsync`, `curl` (already used by existing scripts).

**Phase 2 will depend on Phase 1 producing:**
- A working `package-release.sh` that CI can call without further argument changes.
- A launcher that, given `build/*.jar`, runs without Mill — the regression contract that BATS will lock in.
- A frozen tarball layout (Decision 1's "ship only `project.scala` from core") so the BATS structural assertions are stable.

## Approach

The merged-layer strategy walks one dependency arc:

1. **Packaging produces the artifact.** `scripts/package-release.sh` invokes Mill to build `core.jar` and `dashboard.assembly`, resolves their paths via the same `./mill --ticker false show <task>` protocol `iw-run` already uses (Decision 3 codifies this), copies them into the staging dir as `build/iw-core.jar` and `build/iw-dashboard.jar`, and ships only `core/project.scala` (not the rest of `core/`, per Decision 1).

2. **Launcher consumes the artifact.** `iw-run`'s `ensure_core_jar` and `ensure_dashboard_jar` get a middle resolution rung between the `IW_*_JAR` env override and the Mill query. The order is fixed by Decision 3:
   - (a) `$IW_CORE_JAR` env (if set and points to a readable `.jar`) — wins.
   - (b) `$INSTALL_DIR/build/iw-core.jar` if it exists — implicit pre-provision.
   - (c) `mill_jar_path core.jar` — dev-checkout fallback.
   Same three-tier order for the dashboard jar with `iw-dashboard.jar`. Pure file-presence — no sentinel, no `IW_PREFER_MILL` escape hatch (Decision 3 explicitly rules these out; the existing `IW_*_JAR` env vars remain the escape hatch).

3. **Bootstrap stops compiling on installed tarballs.** `bootstrap()` in `iw-run` already calls `ensure_core_jar`. Once `ensure_core_jar` resolves via the `build/` rung, no Mill invocation happens — `--bootstrap` becomes a verify-only "ready" message. In a dev checkout (no `build/` dir), the same code path falls through to Mill exactly as today. No new flag needed.

4. **No changes to command-execution paths.** Per Decision 2, the three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR" …` invocations at `iw-run:565, 644, 729` stay exactly as written; `project.scala` remains the deps manifest, the resolved `$CORE_JAR` provides compiled classes.

5. **Decision 5 lock-in.** `./mill`, `.mill-version`, and `build.mill` are NOT shipped in the tarball. If a user extracts a tarball into a workspace and runs `./mill`, they'll get "command not found" — a clear signal, not a partial-build. (Documenting this is a Phase 2 task in `RELEASE.md`; in Phase 1 we just don't add those paths to `package-release.sh`.)

## Component specifications

### Packaging Layer — `scripts/package-release.sh`

**Files to modify:**
- `scripts/package-release.sh`

**Add a Mill build step before staging:**
- From `$PROJECT_ROOT`, invoke `./mill --ticker false show core.jar` and `./mill --ticker false show dashboard.assembly`.
- Parse the JSON-quoted output the same way `iw-run:50` does: `jq -r '.' | sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##'`. Don't hardcode `out/core/jar.dest/out.jar`.
- Fail loudly (`set -euo pipefail` is already on) if either path is empty, missing, not a `.jar`, or zero-bytes.

**Stage `build/` into the tarball:**
- `mkdir -p "$RELEASE_PACKAGE_DIR/build"`.
- `cp` the resolved core jar to `"$RELEASE_PACKAGE_DIR/build/iw-core.jar"`.
- `cp` the resolved dashboard jar to `"$RELEASE_PACKAGE_DIR/build/iw-dashboard.jar"`.

**Switch the `core/` rsync to a single-file copy (Decision 1):**
- Replace the current rsync at `package-release.sh:40-49` (which copies all `*.scala`, `*.css`, `*.js` under `core/`) with `cp "$PROJECT_ROOT/core/project.scala" "$RELEASE_PACKAGE_DIR/core/project.scala"`. Keep the `core/` directory creation at `package-release.sh:19`.

**Verification tail:**
- After `tar -czf`, the existing `tar -tzf … | head -20` already prints the listing. Either widen it (`head -30`) or add a dedicated `tar -tzf "$tarball" | grep -E '^iw-cli-[^/]+/build/'` so the CI logs visibly confirm both jars are in the tarball.

**Do NOT add to the tarball** (Decision 5): `./mill`, `.mill-version`, `build.mill`, `.bsp/`, `out/`, `dashboard/jvm/`, `dashboard/frontend/`. The current `package-release.sh` already excludes these implicitly by only copying `iw-run`, `iw-bootstrap`, `VERSION`, `commands/**/*.scala`, and `core/**`. Leave that posture; just narrow the `core/` copy.

### Launcher Layer — `iw-run`

**Files to modify:**
- `iw-run`

**`ensure_core_jar()` (currently `iw-run:65-72`):**
- Contract: on return, sets `CORE_JAR` to a readable absolute path to a `.jar` file, and exports `IW_CORE_JAR` to the same value so child processes inherit it.
- Resolution order (verbatim from Decision 3):
  1. If `$IW_CORE_JAR` is set and points to a readable `.jar` → use it.
  2. Else if `$INSTALL_DIR/build/iw-core.jar` exists → use it (set `CORE_JAR` and export `IW_CORE_JAR`).
  3. Else → `CORE_JAR="$(mill_jar_path core.jar)"`; export `IW_CORE_JAR`.
- Add a comment block above the function describing the order and the convention "Mill writes to `out/`, scala-cli to `.scala-build/`, releases write to `build/`" (load-bearing, per Decision 3).

**`ensure_dashboard_jar()` (currently `iw-run:77-84`):**
- Same three-tier pattern, with `$INSTALL_DIR/build/iw-dashboard.jar` as rung 2 and `mill_jar_path dashboard.assembly` as rung 3.
- On return, sets `DASHBOARD_JAR` and exports `IW_DASHBOARD_JAR`.

**`bootstrap()` (currently `iw-run:87-91`):**
- After `ensure_core_jar`, check whether resolution went through rung 2 (or rung 1 with a `build/` path). The simplest test is: if `$INSTALL_DIR/build/iw-core.jar` exists after the call, print "iw-cli is ready (pre-built jars present at $INSTALL_DIR/build/)." If it doesn't (dev checkout), keep the existing "Bootstrap complete" message, which now reflects that Mill ran during `ensure_core_jar`.
- Optional: also call `ensure_dashboard_jar` from `bootstrap` so verify-mode confirms both jars at once. The analysis doesn't mandate this; weigh against the dev-mode cost of forcing a Mill `dashboard.assembly` build at bootstrap time. Recommend leaving `bootstrap` as core-only to match current behavior.

**Surface a clear error** if neither pre-built jars nor Mill are available. Today, missing Mill produces the message at `iw-run:43-44` ("Mill failed to resolve …"). When `$INSTALL_DIR/mill` doesn't exist (extracted tarball with no `build/`), `./mill` will fail with the shell's "No such file or directory" — that's confusing. Add a guard in `mill_jar_path` (or before calling it) that detects missing `./mill` in `$INSTALL_DIR` and prints "ERROR: pre-built jars not found at $INSTALL_DIR/build/ and Mill is not available" before exiting. This is a small additive safety net; it doesn't change the resolution order.

### Bootstrap Layer — `iw-bootstrap`

**Files to modify:**
- `iw-bootstrap` (likely no change required)

**Inspection only.** `iw-bootstrap`'s contract is download → `tar -xzf --strip-components=1` → `iw-run --bootstrap`. The new tarball layout is a strict superset of the old one (the old `core/**/*.scala` is replaced by `core/project.scala` plus `build/*.jar`), so `iw-bootstrap` continues to work without modification.

**Optional defensive check:** after `tar -xzf` in `download_release()`, `[[ -f "$version_dir/build/iw-core.jar" ]] || { echo "Error: extracted tarball missing build/iw-core.jar"; exit 1; }`. This gives a friendlier error than letting `iw-run --bootstrap` discover the problem two layers deeper. Not required by the analysis (`analysis.md:79` calls it "optional polish, not strictly required") — implementer's call.

## API contracts between layers

- **Packaging → Launcher:** the launcher resolves jars by file presence at `$INSTALL_DIR/build/iw-core.jar` and `$INSTALL_DIR/build/iw-dashboard.jar`. Packaging MUST produce exactly those file names at exactly those relative paths. No alternate names ("core.jar", "iw-core-0.5.0.jar") — the launcher does a literal path test.
- **Launcher → Bootstrap (`iw-bootstrap`):** the launcher's `--bootstrap` mode is verify-only when `$INSTALL_DIR/build/iw-core.jar` is present. `iw-bootstrap` relies on this: it expects `--bootstrap` to succeed without requiring Mill on `PATH`.
- **Launcher → child commands:** unchanged. After `ensure_core_jar`, `$IW_CORE_JAR` is exported; the three `scala-cli run … --jar "$CORE_JAR"` invocations at `iw-run:565, 644, 729` keep working as today.

## Files to modify

- `/home/mph/Devel/iw/iw-cli-IW-346/scripts/package-release.sh` — Mill build step, `build/` staging, narrow `core/` copy.
- `/home/mph/Devel/iw/iw-cli-IW-346/iw-run` — `ensure_core_jar`, `ensure_dashboard_jar`, `bootstrap` updates plus comment block.
- `/home/mph/Devel/iw/iw-cli-IW-346/iw-bootstrap` — only if implementing the optional defensive check.

No other files are expected to change in Phase 1. If the audit gate (next section) surfaces an additional file, raise it before completing the phase.

## Testing strategy for Phase 1

**Manual local verification only — BATS updates are Phase 2.**

1. **Build a tarball locally.** With `WEBAWESOME_NPM_TOKEN` exported and Node 20 + Yarn available:
   ```
   ./scripts/package-release.sh 0.5.0-test
   ```
   Then inspect: `tar -tzf release/iw-cli-0.5.0-test.tar.gz | grep -E 'build/'` should list two non-zero entries: `iw-cli-0.5.0-test/build/iw-core.jar` and `iw-cli-0.5.0-test/build/iw-dashboard.jar`. Confirm via the tar listing that the entries are non-empty (size column).

2. **Extract and run without Mill.** Extract the tarball to a temp dir, then with `PATH=/usr/bin:/bin` (Mill removed):
   - `./iw-run --bootstrap` — must succeed silently (no Mill invocation).
   - `./iw-run --list` — must succeed and list all commands.
   - `IW_PROJECT_DIR=/tmp ./iw-run version` (or any command exercising `ensure_core_jar`) — must succeed using the bundled `build/iw-core.jar`.

3. **Dev-checkout regression.** From the iw-cli repo root (no `build/` dir):
   - `./iw --list` and `./iw <some-command>` continue to drive Mill, exactly as today. No new behavior.
   - `./iw --bootstrap` continues to invoke Mill (the rung-3 fallback in `ensure_core_jar`).

4. **Env override still wins.** `IW_CORE_JAR=/tmp/fake.jar ./iw-run --bootstrap` should use the env value (and fail clean because it's not a real jar) rather than picking up `build/iw-core.jar` — confirms rung 1 priority is preserved.

Do NOT write the BATS code in this phase. Phase 2 owns `test/bootstrap.bats:42-54, 73-93` updates and the new "Mill-hidden-from-PATH" regression test.

## Audit gate (from Decision 1)

**Before completing Phase 1**, run a 5-minute audit confirming nothing under `commands/` or `core/` reads `core/**/*.{scala,css,js}` at runtime beyond `core/project.scala`. The audit protects against shipping a tarball that's missing a file the runtime expects (Risk 3).

**How to audit:**
- `rg -n 'core/' commands/ core/` and `rg -n 'IW_CORE_DIR|CORE_DIR' commands/ core/ iw-run` to find all references.
- Inspect each match: is it a build-time reference (deps manifest, scala-cli invocation) or a runtime read (file open, asset load, hook discovery)?
- Specifically check for: hook discovery scanning `$IW_CORE_DIR`, dashboard server reading `core/*.css` or `core/*.js` as static assets, command code calling `Source.fromFile` on a `core/` path.
- The launcher's three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR" …` invocations are EXPECTED references to `project.scala` — those are fine (Decision 2).

**If the audit surfaces any other runtime read of `core/**/*.{scala,css,js}`:** STOP, surface it (e.g. as a CLARIFY in the implementation log or by pushing back to Michal), and decide whether to (a) ship that file from `core/` like we ship `project.scala`, (b) bundle it into one of the jars, or (c) refactor the runtime to not need it. Don't silently expand the tarball.

If the audit is clear, document the result in the Phase 1 implementation log.

## Risks specific to Phase 1

### Risk 2: Launcher auto-detection picks up a stale `build/` directory in a dev checkout
- **Likelihood:** Low (nothing in the repo currently writes to `build/`).
- **Impact:** Medium — a developer who once ran `package-release.sh` locally now has stale jars in `build/`, and the launcher silently uses them.
- **Mitigation:** The "Mill writes to `out/`, releases write to `build/`" convention is documented in the comment block added to `iw-run` near `ensure_core_jar` (per Decision 3). The `IW_CORE_JAR` env var (rung 1) remains a deliberate-stale escape hatch; `rm -rf build/` is the other. If this risk materializes, Decision 3 leaves the door open to add an `IW_PREFER_MILL` env var later — out of scope for Phase 1.

### Risk 3: Dropping most of `core/` source breaks an undocumented runtime code path
- **Likelihood:** Medium.
- **Impact:** High — would break installed users on first command after the tarball ships.
- **Mitigation:** The audit gate above. If anything reads `core/**/*` at runtime, surface it before merging Phase 1.

(Risk 1 from `analysis.md` is a CI risk — Phase 2. Risk 4 is the `WEBAWESOME_NPM_TOKEN` leak risk — also primarily Phase 2's concern, since Phase 1 packaging runs locally where the token is already in the developer's env.)

## Acceptance criteria

- [ ] `./scripts/package-release.sh 0.5.0-test` runs to completion locally and produces `release/iw-cli-0.5.0-test.tar.gz`.
- [ ] `tar -tzf release/iw-cli-0.5.0-test.tar.gz` listing contains `iw-cli-0.5.0-test/build/iw-core.jar` and `iw-cli-0.5.0-test/build/iw-dashboard.jar`, both non-empty.
- [ ] The same listing contains `iw-cli-0.5.0-test/core/project.scala` and does NOT contain other `core/**/*.scala`, `core/**/*.css`, or `core/**/*.js` files.
- [ ] Extracted tarball, with `PATH=/usr/bin:/bin` (Mill hidden), can run:
  - [ ] `./iw-run --bootstrap` (succeeds without invoking Mill)
  - [ ] `./iw-run --list`
  - [ ] At least one real command (e.g. `./iw-run version`) that exercises `ensure_core_jar` end-to-end.
- [ ] In the dev checkout (no `build/` dir), `./iw <cmd>` and `./iw --bootstrap` continue to drive Mill — no regression.
- [ ] `IW_CORE_JAR=/path/to/explicit.jar` env override still wins over `$INSTALL_DIR/build/iw-core.jar` (rung 1 > rung 2).
- [ ] Audit gate clear: no runtime read of `core/**/*.{scala,css,js}` beyond `core/project.scala` found in `commands/` or `core/`. Result recorded in implementation log.
- [ ] Decisions 1, 2, 3, 5 honored:
  - 1: only `core/project.scala` shipped from `core/`.
  - 2: no changes to the three `scala-cli run … --jar` invocations at `iw-run:565, 644, 729`.
  - 3: pure file-presence resolution at `$INSTALL_DIR/build/*.jar`; no sentinel, no `IW_PREFER_MILL`.
  - 5: no `./mill`, `.mill-version`, or `build.mill` shipped in the tarball.
- [ ] Decision 4 (CI on self-hosted + `iw-cli-ci` container) explicitly acknowledged as Phase 2 — not in this PR.
- [ ] Comment block in `iw-run` near `ensure_core_jar` documents the resolution order and the `out/` vs `build/` vs `.scala-build/` convention.
