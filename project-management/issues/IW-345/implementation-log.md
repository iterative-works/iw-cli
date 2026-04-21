# Implementation Log: IW-345 — Layer 0: Set up Mill module for dashboard with Vite + Tailwind + Web Awesome

Issue: IW-345

This log tracks the evolution of implementation across phases.

---

## Phase 1: Mill bootstrap + core jar production (2026-04-21)

**Layer:** L0 — Build infrastructure

**What was built:**
- `build.mill` — Mill 1.1.5 build definition; `object core extends ScalaModule` (Scala 3.3.7, explicit `core/` subdir sources, `mvnDeps` mirroring `core/project.scala` including dashboard-only deps); top-level `iwCoreJar()` as `Task.Command` that copies the compiled jar to `build/iw-core.jar`.
- `mill` — vendored canonical Mill launcher (bash wrapper) that reads `.mill-version`, caches the downloaded binary under `~/.cache/mill/`, and exec's with args passed through. PURPOSE header added.
- `.mill-version` — pins Mill to `1.1.5`.
- `.github/deps/mill` (gitignored) — Mill 1.1.5 native-linux-amd64 binary (~59MB), consumed by Dockerfile.ci `COPY deps/mill /usr/local/bin/mill`.
- `.github/deps/node.tar.xz` (gitignored) — Node 20.20.2 linux-x64 tarball (~25MB), consumed by Dockerfile.ci for the future dashboard frontend build.

**Modified:**
- `iw-run` — `build_core_jar()` body replaced with `(cd "$INSTALL_DIR" && ./mill iwCoreJar)`; `ensure_core_jar()` and `core_jar_stale()` untouched as required.
- `core/project.scala` — added SYNC comment cross-referencing `build.mill`'s `mvnDeps` as the mirror of `//> using dep` entries.
- `.github/Dockerfile.ci` — inserted Node 20 tarball extraction, `corepack enable`, Mill 1.1.5 install between the Coursier and BATS blocks; extended the final verification line to include `node --version`, `corepack --version`, `mill --version`.
- `.gitignore` — added `out/` so Mill's scratch directory stays out of commits.
- `.scalafmt.conf` — added `project.excludePaths = ["glob:**/out/**"]` so `scala-cli fmt --check .` (used by pre-commit) doesn't scan Mill-generated sources inside `out/` (scalafmt does not respect `.gitignore`).

**Deviations from plan:**

1. `iwCoreJar` is a `Task.Command` (not a normal `Task`). Mill 1.1.5 forbids plain `Task`s from writing outside `Task.dest`; `Task.Command` is the correct pattern for tasks that materialise files at arbitrary paths. Invocation (`./mill iwCoreJar`) is unchanged.
2. `sources` enumerates explicit subdirectories (`adapters`, `dashboard`, `model`, `output`) plus the single root file `IssueCreateParser.scala`, rather than the whole `core/` directory. Including `core/` wholesale would pull in `core/test/**` and fail compilation with ~4200 errors. **Maintenance note:** if new root-level `.scala` files land in `core/`, the `sources` list in `build.mill` must be extended — otherwise they will be silently excluded from the Mill build.
3. Vendored binaries landed at `.github/deps/` (Docker build context is `.github/`, so `COPY deps/...` directives are relative to that). This matches the pre-existing `deps/` pattern for `jdk.tar.gz`, `scala-cli`, `cs`.

**Code review:**
- Iterations: 1 review pass + 1 fix pass (no critical issues; 6 warnings of which 4 fixed, 2 deferred).
- Review file: `review-phase-01-20260421-165907.md`
- Fixed in iteration 2: temporal phase-name references in `build.mill:24`, `build.mill:30-31`, `.github/Dockerfile.ci:44`; added PURPOSE header and `DEFAULT_MILL_VERSION` clarifying comment to `./mill` wrapper.
- Deferred: checksum verification for Mill launcher download (vendored upstream wrapper) and Node tarball (matches pre-existing `deps/` pattern) — flagged for future project-wide hardening, not addressed in this phase.

**Testing:**
- Unit tests: 0 new tests added (build infrastructure — no Scala logic to unit-test).
- Regression: all 141 existing munit tests pass against `build/iw-core.jar` produced by Mill (`./iw ./test unit` green).
- Smoke: `./iw status` runs end to end against the Mill-built jar (no NoClassDefFoundError / classpath drift).
- Pre-push hook: format + scalafix + `-Werror` compile + unit tests + command compilation all green with the Mill-built jar in place.
- Build verification: `./mill iwCoreJar` from a clean tree (wiped `out/` and `build/iw-core.jar`) produces the jar; first run ~22s, second run ~0.3s cache hit.

**Contract for next phases:**
- `build/iw-core.jar` on-disk contract unchanged (same path, thin jar of compiled classes only). `iw-run` continues to consume the jar via `$CORE_JAR`.
- `core/project.scala` remains the runtime-classpath source of truth for scala-cli command invocations. `build.mill` is the build-time jar producer. Both dep lists must be kept in sync manually; the SYNC comment is a reader's cue, not an enforcement mechanism.
- Dashboard-only deps (`cask`, `scalatags`, `flexmark`) are intentionally duplicated across both files and are scheduled to move together when Phase 2 introduces a dedicated dashboard module. Do not split the dep cleanup from the directory move.
- Node 20 + Corepack land in the CI image now but are not consumed until the frontend pipeline phase. This was a deliberate bundling decision to amortise the image-rebuild cost.

**Files changed:**
```
M	.github/Dockerfile.ci
M	.gitignore
M	.scalafmt.conf
M	core/project.scala
M	iw-run
A	.mill-version
A	build.mill
A	mill
```

---

