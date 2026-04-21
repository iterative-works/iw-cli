# Phase 1: Mill bootstrap + core jar production

**Issue:** IW-345
**Layer:** L0
**Branch:** IW-345 (feature branch)
**Status:** Not started
**Estimated:** 3-5 hours

## Goals

- Introduce Mill 1.1.5 as a second build tool alongside scala-cli, via a committed `./mill` wrapper, `.mill-version`, and a `build.mill` at the repo root.
- Define a single `core` Mill module that compiles `core/**/*.scala` (excluding tests) and produces `build/iw-core.jar` as a thin jar (compiled classes only).
- Flip `iw-run`'s `build_core_jar()` from `scala-cli package --library` to the Mill-produced jar, preserving the `ensure_core_jar()` / `core_jar_stale()` mtime gate unchanged.
- Keep `core/project.scala` intact as the runtime-classpath source of truth for scala-cli command invocations; add a sync-comment pointing at `build.mill`'s `core.mvnDeps` (and vice versa).
- Extend `.github/Dockerfile.ci` with pre-provisioned Node 20 + Corepack + Mill 1.1.5, matching the existing `deps/` pattern for offline-reproducible image builds.
- Add `out/` to `.gitignore` so Mill's scratch directory never lands in commits.

## Scope

### In scope
- New `build.mill` with a single `core` ScalaModule targeting Scala 3.3.7 and the same dep coords as `core/project.scala`.
- New `./mill` wrapper script (executable) that downloads Mill 1.1.5 to a local cache on first run and re-execs.
- New `.mill-version` file pinning `1.1.5`.
- `iw-run` edit: replace the `scala-cli package --library ...` invocation inside `build_core_jar()` with a Mill task call. Keep `ensure_core_jar()` and `core_jar_stale()` logic untouched.
- Sync-comment lines in both `core/project.scala` and `build.mill` calling out the manual dep-list coupling.
- Dockerfile additions: Node 20 tarball via `deps/node.tar.xz`, `corepack enable`, Mill 1.1.5 launcher via `deps/mill`, extended verification line.
- `.gitignore` entry for `out/`.

### Out of scope
- Phase 2 (L1): moving `core/dashboard/**` to `dashboard/jvm/src/**`, renaming `iw.core.dashboard.*` → `iw.dashboard.*`, migrating the ~20 dashboard tests, dropping dashboard-only deps from `core/project.scala`. Dashboard-only deps (`cask`, `scalatags`, `flexmark`) STAY in `core/project.scala` AND must appear in `build.mill`'s `core.mvnDeps` for this phase — they move together in Phase 2.
- Phase 3 (L2+L3): `dashboard/frontend/` Vite/Tailwind/Web Awesome pipeline, the `dashboard` Mill module, `frontend` module, fat-jar assembly, `itest` module, new CI `dashboard-build` job, `WEBAWESOME_NPM_TOKEN` secret.
- Phase 4 (L4): `commands/dashboard.scala` and `commands/server-daemon.scala` rewrites, `ensure_dashboard_jar` helper in `iw-run`, double-gated dev mode, `assetUrl` template helper, README/CLAUDE.md documentation updates.
- Any behavior change visible to end users (`./iw status`, `./iw dashboard`, etc. run identically).

## Dependencies

### Prior phases
- **IW-344 (merged, commit 259bc54):** established the `build/iw-core.jar` on-disk contract — command scripts already consume it via `--jar "$CORE_JAR"` (see `iw-run:550, 629, 709`). Phase 1 inherits that contract; only the builder command flips.

### External
- **Mill 1.1.5 launcher binary** — pulled from the Mill releases GitHub repo. In local dev the `./mill` wrapper downloads on first run (cached under `~/.cache/mill/` or equivalent, following procedures' convention). In CI the launcher is vendored into `deps/mill` at image build, installed to `/usr/local/bin/mill`, and called via `PATH`.
- **Node 20 tarball** (`node-v20.x.x-linux-x64.tar.xz`) — vendored into `deps/node.tar.xz`, extracted to `/opt/node` at image build, with `node`/`npm`/`npx` symlinked into `PATH`. Brings `corepack` with it; `corepack enable` runs during image build. Node is not consumed in this phase but lands here so Phase 3's frontend work needs no further CI image changes.

## Approach

### `build.mill` shape (Mill 1.1.x syntax)

```
package build
import mill.*
import mill.scalalib.*

object core extends ScalaModule {
  def scalaVersion = "3.3.7"

  def sources = Task.Sources(
    moduleDir / os.up / "core"
  )

  def mvnDeps = Seq(
    mvn"com.typesafe:config:1.4.5",
    mvn"com.softwaremill.sttp.client4::core:4.0.15",
    mvn"com.lihaoyi::upickle:4.4.2",
    mvn"com.lihaoyi::os-lib:0.11.6",
    // Dashboard-only deps — move to a dedicated dashboard module in Phase 2.
    mvn"com.lihaoyi::cask:0.11.3",
    mvn"com.lihaoyi::scalatags:0.13.1",
    mvn"com.vladsch.flexmark:flexmark-all:0.64.8",
  )

  // Exclude test sources — Mill `jar` packages compile classes only.
  // core/test/** stays with scala-cli for Phase 1 (141 munit tests).
}

// Materialises `core.jar` at `build/iw-core.jar` for launcher discoverability.
def iwCoreJar: T[PathRef] = Task {
  val target = BuildCtx.workspaceRoot / "build" / "iw-core.jar"
  os.makeDir.all(target / os.up)
  os.copy.over(core.jar().path, target)
  PathRef(target)
}
```

Exact syntax surfaces on first `./mill core.compile` — Mill 1.1.x differs slightly from the procedures reference (1.0.x-era) and the above is the expected shape. Fix forward if Mill rejects any of it.

The `core` module compiles all non-test `.scala` under `core/`. `core/test/**` is deliberately excluded in Phase 1 — tests continue to run via scala-cli consuming the Mill-built jar, matching the runtime split scala-cli already enforces. Phase 2 introduces a Mill test module on the relocated dashboard.

### `./mill` wrapper behaviour

The wrapper script is a small bash file that:
1. Reads `.mill-version` (`1.1.5`).
2. Checks for a cached Mill launcher at `~/.cache/mill/download/1.1.5` (or platform equivalent).
3. If missing, downloads `https://github.com/com-lihaoyi/mill/releases/download/1.1.5/1.1.5` to the cache and `chmod +x`.
4. `exec`s the cached launcher with all arguments passed through.

Match the shape of procedures' wrapper verbatim — it's the canonical reference and the download URL structure is stable.

### `.mill-version`

Single line: `1.1.5`. No trailing content.

### `iw-run` change shape

`build_core_jar()` at `iw-run:29-47` currently invokes `scala-cli --power package --library -o "$CORE_JAR" -f $core_files --server=false`. Replace the function body (keeping the `"Rebuilding core jar..."` log and the `mkdir -p` line) with a single Mill invocation:

```
build_core_jar() {
    echo "Rebuilding core jar..." >&2
    mkdir -p "$(dirname "$CORE_JAR")"
    (cd "$INSTALL_DIR" && ./mill iwCoreJar)
}
```

`ensure_core_jar()` (lines 65-69) and `core_jar_stale()` (lines 51-62) do not change — they already gate on mtime against `core/**/*.scala` and the jar path is driven by `$CORE_JAR`, which `iwCoreJar` writes to.

### `core/project.scala` sync comment

Add one line at the top of the existing `// PURPOSE:` block (or immediately after):

```
// SYNC: Dep list mirrors `build.mill`'s `core.mvnDeps`. Keep both in sync by hand.
```

And add the reverse line in `build.mill` near `core.mvnDeps`:

```
// SYNC: mvnDeps mirror `core/project.scala`'s `//> using dep` entries. Keep both in sync by hand.
```

### CI Dockerfile deltas (`.github/Dockerfile.ci`)

After the existing Coursier block (line 40) and before the BATS block (line 45), insert:

1. **Node 20** — `COPY deps/node.tar.xz /tmp/node.tar.xz`; `RUN tar xJf /tmp/node.tar.xz -C /opt && rm /tmp/node.tar.xz && ln -s /opt/node-v20.* /opt/node`; extend `PATH` with `/opt/node/bin`.
2. **Corepack** — `RUN corepack enable` (ships with Node 20).
3. **Mill 1.1.5** — `COPY deps/mill /usr/local/bin/mill` + `RUN chmod +x /usr/local/bin/mill && mill --version` (mirrors the scala-cli pre-download pattern at lines 35-36).
4. **Verification line** (line 51) — extend to `... && node --version && corepack --version && mill --version`.

Node is pre-provisioned here even though Phase 1 doesn't consume it — rebuilding the CI image is the expensive step, so bundling Node + Mill together amortises the cost across this phase and Phase 3.

## Files to modify or create

### New files
- `/home/mph/Devel/iw/iw-cli-IW-345/build.mill`
- `/home/mph/Devel/iw/iw-cli-IW-345/mill` (executable wrapper, `chmod +x`)
- `/home/mph/Devel/iw/iw-cli-IW-345/.mill-version`

### Modified files
- `/home/mph/Devel/iw/iw-cli-IW-345/iw-run` — `build_core_jar()` body replaced with `./mill iwCoreJar`
- `/home/mph/Devel/iw/iw-cli-IW-345/core/project.scala` — add SYNC sync-comment line
- `/home/mph/Devel/iw/iw-cli-IW-345/.github/Dockerfile.ci` — Node 20 + Corepack + Mill 1.1.5 provisioning + extended verification line
- `/home/mph/Devel/iw/iw-cli-IW-345/.gitignore` — add `out/`

## Testing strategy

- **Unit level (direct):** none. This is build infrastructure with no Scala logic of its own to unit-test.
- **Regression — core tests:** `./iw ./test unit` runs the full munit suite using `build/iw-core.jar` built by Mill. All 141 existing tests must pass (they already consume the jar produced upstream; the producer identity is transparent to munit).
- **Regression — commands:** `./iw status`, `./iw start <sample>`, or any command that exercises scala-cli's runtime classpath still resolves all runtime deps through `core/project.scala`. Smoke-run at least one command end to end.
- **CI image verification:** inside the rebuilt CI Docker image, `mill --version`, `node --version`, `corepack --version` all exit 0. The existing tool checks (`java`, `scala-cli`, `scalafix`, `bats`, `tmux`, `jq`, `python3`, `gh`) remain green.
- **From-clean-clone:** with `out/` and `build/` wiped, `./mill iwCoreJar` produces `build/iw-core.jar` without any prior state. First run downloads Mill via `./mill`; second run is a cache hit.
- **Pre-push hook:** unchanged hook (format + scalafix + `-Werror` core compile + unit tests + command compilation) passes with the Mill-built jar in place of the scala-cli-built jar.

## Acceptance criteria

- [ ] `./mill iwCoreJar` produces `build/iw-core.jar` from a clean tree (no prior `out/` or `build/iw-core.jar`).
- [ ] `./iw ./test unit` passes all 141 existing munit tests against the Mill-built jar.
- [ ] Pre-push hook passes (format + scalafix + `-Werror` compile + unit tests + command compilation).
- [ ] `rg -n "scala-cli package" iw-run` returns no results inside `build_core_jar()` (the old invocation is gone; unrelated mentions in comments are fine if present).
- [ ] `rg -n "^out/$" .gitignore` finds the new entry.
- [ ] `build.mill` declares Mill 1.1.x syntax (`object core extends ScalaModule`, `mvnDeps = Seq(...)`, `Task.Sources(...)`) and a single `core` module plus a top-level `iwCoreJar` task.
- [ ] `.github/Dockerfile.ci` contains Node 20 tarball extraction, `corepack enable`, Mill 1.1.5 launcher install, and the final verification line runs `mill --version`, `node --version`, `corepack --version` alongside the existing checks.
- [ ] Sync-comment present in both `core/project.scala` and `build.mill`, each referencing the other file.
- [ ] `./mill` wrapper is executable (`test -x mill`), `.mill-version` contains exactly `1.1.5`.
- [ ] Smoke-run: at least one scala-cli command (`./iw status` or equivalent) runs end to end against the Mill-built jar.
- [ ] No CLAUDE.md rule violations: `build.mill` opens with a `PURPOSE:` header line, no temporal/historical wording in comments, file purposes adhere to core/CLAUDE.md's FCIS boundaries.

## Risks

- **Mill 1.1.5 syntax divergence from procedures (1.0.x-era reference).** Surfaces on first `./mill core.compile`. Fix forward; Mill's error messages typically point at the exact API difference (`mvnDeps` vs older `ivyDeps`, `Task.Sources` vs `T.sources`, `BuildCtx.workspaceRoot` vs `millSourcePath` etc.).
- **`mvnDeps` drift from `//> using dep`.** Both files must declare the same coords at the same versions or the Mill-compiled classpath will diverge from the scala-cli runtime classpath — symptom is `NoClassDefFoundError` at command runtime, not at compile time. Cross-check line by line before committing; the sync-comment is the reader's cue, not an enforcement mechanism.
- **CI image rebuild latency.** The Dockerfile.ci change triggers a full image rebuild on the runner. The first `dashboard-build` (Phase 3) and any pre-existing jobs on the new image will be slow until the warm image is published. Rebuild cadence is already managed per the procedures pattern (image only rebuilds when `Dockerfile.ci` or `deps/` changes).
- **Mill cache location portability.** The `./mill` wrapper's download target (`~/.cache/mill/`) may differ on macOS vs Linux. Match procedures' wrapper exactly — it already handles this cross-platform.

## Notes for reviewer

- Phase 1 is intentionally invisible at runtime: `./iw dashboard`, `./iw status`, and every other command behave bit-identically. Only the on-disk builder of `build/iw-core.jar` changes. All 141 existing tests pass without modification.
- The `build/iw-core.jar` output contract is unchanged: same path, same thin-jar format (compiled classes only, no transitive deps). Any script that already expects the jar at `$INSTALL_DIR/build/iw-core.jar` keeps working.
- Dashboard-only deps (`cask`, `scalatags`, `flexmark`) remain in BOTH `core/project.scala` and `build.mill` for this phase. They relocate together in Phase 2. Do not pre-empt that move here — the dep cleanup is coupled with the dashboard directory move and Mill `dashboard` module introduction, and splitting it would leave Phase 1 with a broken scala-cli compile of dashboard code.
- Node 20 and Corepack are installed in the CI image but not consumed by any job until Phase 3. This is a deliberate trade-off to pay the image-rebuild cost once.
