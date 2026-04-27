# Technical Analysis: Layer 0 — Update release packaging for built artifacts

**Issue:** IW-346
**Created:** 2026-04-27
**Status:** Draft

## Problem Statement

After IW-344 (core jar pre-compilation, merged in 259bc54) and IW-345 (Mill dashboard module, merged in 8a9b603), iw-cli's runtime expects two pre-built jars — `core.jar` produced by Mill and `dashboard.assembly` produced by Mill — and `iw-run` resolves both via `./mill --ticker false show <task>` (`iw-run:36-60, 65-72, 77-84`).

Release packaging has not caught up. `scripts/package-release.sh` (47 lines) still ships only:
- `iw-run`, `iw-bootstrap`, `VERSION` at the package root
- `commands/**/*.scala`
- `core/**/*.{scala,css,js}` (excluding `test/`)

It does **not** include the Mill wrapper (`./mill`), the `build.mill` definition, the pinned `.mill-version`, or any pre-built jars. As a result, an extracted tarball today has a latent bug: `iw-run --bootstrap` calls `ensure_core_jar`, which falls through to `mill_jar_path core.jar` and tries to invoke `./mill` from `$INSTALL_DIR` — a file that does not exist in the tarball. The same applies to any dashboard-launching command path. Installations created via `iw-bootstrap` from a published release would either fail outright on first command, or would only work on hosts that happen to have Mill provisioned (a coincidence, not a contract).

The issue asks us to ship pre-built jars in `build/iw-core.jar` and `build/iw-dashboard.jar`, update the launcher to consume them, and update CI to produce them. The user impact is that fresh installs work offline — no Mill, no Yarn, no Vite, no Web Awesome registry token required at the install site. Only `scala-cli` (and a JRE) must be present.

## Proposed Solution

### High-Level Approach

Move the build step from install-time to release-time. The release pipeline (CI on tag push) builds both jars inside a Mill-capable environment with `WEBAWESOME_NPM_TOKEN` available, then drops them into `build/iw-core.jar` and `build/iw-dashboard.jar` inside the staged tarball directory before `tar -czf`. The launcher already has the right seam: `IW_CORE_JAR` and `IW_DASHBOARD_JAR` environment variables short-circuit the Mill query when set to a readable jar (`iw-run:65-84`). We extend the resolution order to prefer `$INSTALL_DIR/build/*.jar` when present, falling back to Mill only when running from a dev checkout.

The bootstrap flow stays identical from the user's perspective: `iw-bootstrap` downloads the tarball, extracts it, and invokes `iw-run --bootstrap`. With this change, `--bootstrap` becomes a no-op verification step on installed tarballs ("jars are here, you're good") rather than a Mill-driven compile step, and remains a real compile step in dev mode.

### Why This Approach

- **The seam already exists.** `IW_CORE_JAR` / `IW_DASHBOARD_JAR` were added in IW-344/IW-345 specifically to let pre-provisioned jars bypass Mill. Wiring `iw-run` to auto-detect `$INSTALL_DIR/build/*.jar` and treat that as an implicit pre-provision is a small, additive change.
- **Keeps dev mode untouched.** A repo checkout has no `$INSTALL_DIR/build/iw-core.jar` (Mill writes to `out/core/jar.dest/out.jar`). The auto-detect logic only activates in installed tarballs, so contributors keep their incremental Mill workflow.
- **Avoids shipping a build toolchain.** Including Mill, Node, Yarn, and a Web Awesome registry token at every install site would be a much larger surface and a real security concern (the token is a Pro license credential).
- **Matches IW-345's L4 expectation.** The dashboard module is already designed to be launched as `java -jar <dashboard.assembly>`. Pre-shipping that jar is the natural completion of that design.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Packaging Layer (`scripts/package-release.sh`)

**Components:**
- New build step before staging: invoke Mill from `$PROJECT_ROOT` to produce `out/core/jar.dest/out.jar` and `out/dashboard/assembly.dest/out.jar`. Resolve their paths via `./mill show core.jar` and `./mill show dashboard.assembly` (same protocol `iw-run` uses) so we never hardcode Mill's internal layout.
- New tarball directory `build/` containing the two resolved jars renamed to `iw-core.jar` and `iw-dashboard.jar`.
- Per Decision 1: copy only `core/project.scala` (not the rest of `core/`).
- Verification tail: `tar -tzf` listing should show `build/iw-core.jar` and `build/iw-dashboard.jar` for visual sanity in CI logs.

**Responsibilities:**
- Build artifacts deterministically from a clean state (CI runs in fresh containers; locally, callers may have stale `out/` — script should not blow up but should rebuild before staging).
- Produce a self-contained tarball: extracting it onto a host with only `scala-cli` + JRE available must yield a working install.
- Fail loudly if either jar is missing or zero-bytes after Mill returns.

**Estimated Effort:** 1.5-3 hours
**Complexity:** Straightforward

---

### Launcher Layer (`iw-run`)

**Components:**
- `ensure_core_jar()` (`iw-run:65-72`): extend resolution order to (1) `$IW_CORE_JAR` env, (2) `$INSTALL_DIR/build/iw-core.jar` if it exists, (3) `mill_jar_path core.jar`. Set `IW_CORE_JAR` after resolving so child processes inherit it.
- `ensure_dashboard_jar()` (`iw-run:77-84`): same pattern with `$INSTALL_DIR/build/iw-dashboard.jar`.
- `bootstrap()` (`iw-run:87-91`): when both pre-built jars are present, print a "ready" message without invoking Mill. When dev mode (jars absent), behave as today.
- Per Decision 2: leave the three command-execution paths at lines 565, 644, 729 unchanged — `project.scala` continues to provide the deps manifest, `--jar` provides the compiled classes.

**Responsibilities:**
- Detect installed-tarball layout vs dev-checkout layout transparently. No new flag, no user-visible behavior change.
- Preserve every existing fallback path so dev workflows (`./iw <cmd>` from a checkout) remain unaffected.
- Surface a clear error if neither pre-built jars nor Mill are available, rather than a cryptic Mill-not-found message.

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### Bootstrap Layer (`iw-bootstrap`)

**Components:**
- Inspection only; no expected structural changes. `iw-bootstrap` (`iw-bootstrap:1-114`) only does download + `tar -xzf --strip-components=1` + `iw-run --bootstrap`. Tarball contents are opaque to it.
- Possible add: post-extract validation (`-f $version_dir/build/iw-core.jar`) with a friendlier error than letting `iw-run` discover the missing jar later. Optional polish, not strictly required.

**Responsibilities:**
- Continue to extract-and-delegate. The new tarball layout is a strict superset of the old one (everything moves to `build/` rather than replacing existing paths).

**Estimated Effort:** 0-0.5 hours
**Complexity:** Straightforward

---

### CI Layer (`.github/workflows/release.yml`)

**Components:**
- Provisioning for Mill + Node + Yarn + `WEBAWESOME_NPM_TOKEN` so `./mill dashboard.assembly` succeeds in the release job. Today, `release.yml` runs on `ubuntu-latest` with only `temurin:21` and `scala-cli` (`release.yml:13-19`); it has none of these.
- Per Decision 4: switch the workflow to `self-hosted` + `iw-cli-ci` container, mirroring `ci.yml:dashboard-build`.
- Step ordering: install toolchain → run `./iw ./test` (still on ubuntu-latest, as today) → invoke `package-release.sh` (which now triggers Mill builds). Or alternatively, build jars in a dedicated step and pass them via `IW_*_JAR` env to `package-release.sh` to keep the script's responsibilities narrow.
- The existing `dashboard-build` job in `ci.yml:68-85` is the working reference: it uses `ghcr.io/iterative-works/iw-cli-ci:latest` with `WEBAWESOME_NPM_TOKEN` from secrets.

**Responsibilities:**
- Produce a release tarball with both jars on every `v*` tag push.
- Keep the manual `scripts/release.sh` flow (`scripts/release.sh:1-106`) working for local testing — that script delegates to `package-release.sh`, so as long as the local maintainer has Mill + Node + the token, it should work the same.

**Estimated Effort:** 1.5-3 hours
**Complexity:** Moderate (depending on the container vs. ubuntu-latest decision)

---

### Test Layer (`test/bootstrap.bats`)

**Components:**
- Update structural assertions in test "release package contains required structure" (`test/bootstrap.bats:73-93`) to require `build/iw-core.jar` and `build/iw-dashboard.jar`; drop the `core/Config.scala` assertion (Decision 1 stops shipping it), keep the `core/project.scala` assertion.
- Update test "iw-run --bootstrap pre-compiles successfully" (`test/bootstrap.bats:42-54`) — with jars pre-shipped, `--bootstrap` no longer "compiles"; assertion strings need adjustment.
- The hardcoded `0.1.0-dev` filename pattern (lines 13, 29, 44, 58, 66, 75-91) is already stale (current VERSION is 0.5.0). Don't broaden scope to fix unless trivially adjacent — note in the implementation as a separate concern.
- New invariant test: extract tarball, then run `PATH=/usr/bin:/bin ./iw-run --list` (i.e. without Mill on PATH) and confirm success. This is the regression that protects against re-introducing the latent bug.

**Responsibilities:**
- Validate the new tarball structure and the offline-operation contract.
- Catch any future drift where the launcher silently re-acquires a Mill dependency at install time.

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### Docs Layer (`RELEASE.md`)

**Components:**
- Update "Troubleshooting → Release tarball too large" checklist (`RELEASE.md:112-117`) to reference the new layout (build jars instead of source).
- Update step 2 description (`RELEASE.md:25-26`) to reflect that the tarball now ships compiled artifacts.
- Mention the toolchain prerequisites for running `package-release.sh` locally (Mill via `./mill`, Node 20, Yarn via Corepack, `WEBAWESOME_NPM_TOKEN`).
- Optional: a paragraph on the "what's in `build/`" structure for auditors of the release tarball.

**Responsibilities:**
- Keep the documented release process accurate. Out-of-date instructions are how the next maintainer recreates the IW-346 latent bug.

**Estimated Effort:** 0.5-1 hour
**Complexity:** Straightforward

---

## Technical Decisions

### Patterns

- **Layered fallback for jar resolution.** Env override → installed-tarball path → Mill query. This is an additive extension of the existing two-layer fallback in `ensure_core_jar` / `ensure_dashboard_jar` and preserves both dev and install workflows from a single launcher script.
- **Build at release-time, not install-time.** Move all Mill/Vite/Tailwind invocations to the release pipeline. Install-time work is reduced to extract + verify.
- **Scripts read paths, never hardcode them.** `package-release.sh` should ask Mill where the jars are (`./mill show core.jar` returns the JSON-quoted absolute path) rather than baking in `out/core/jar.dest/out.jar`. Same protocol `iw-run` already uses.

### Technology Choices

- **Frameworks/Libraries:** No new ones. Continue using Mill 1.1.5 (already pinned via `.mill-version`) and the existing scala-cli pipeline for `core/` source compilation in dev.
- **Data Storage:** N/A. Tarball-on-disk only.
- **External Systems:** GitHub Releases for artifact distribution (unchanged). Web Awesome Pro npm registry, accessed only at release-build time (already configured for `ci.yml:dashboard-build`).

### Integration Points

- `package-release.sh` ↔ `./mill` (release-time): `mill show core.jar` and `mill show dashboard.assembly` produce the source paths to copy into `build/`.
- `iw-run` ↔ `$INSTALL_DIR/build/` (install-time): file-existence check determines whether to use the pre-built jar or fall back to Mill.
- `iw-run` ↔ `scala-cli` (every command): unchanged — still invokes `scala-cli run … --jar "$CORE_JAR" …` for command execution.
- CI `release.yml` ↔ Mill toolchain: provisioned via the `iw-cli-ci` container (Decision 4).

## Resolved Decisions

### Decision 1: Ship only `core/project.scala` (drop the rest of `core/` source)

**Resolution:** Option C — the tarball will contain `core/project.scala` (the `//> using dep` deps file) and nothing else from `core/`. The compiled jar at `build/iw-core.jar` is the single source of truth for code; `project.scala` stays only because the existing `scala-cli run … --jar` invocation pattern at `iw-run:565, 644, 729` still passes it as the deps manifest.

**Implementation gates this decision adds to Phase 1:**
- 5-minute audit of `commands/` and `core/` to confirm nothing reads `core/**/*.{scala,css,js}` at runtime (hook discovery, template loading, asset serving). If the audit surfaces any such path, raise it before completing Phase 1.
- `package-release.sh` switches from rsyncing all of `core/` to copying only `core/project.scala` into the staged tarball.
- `test/bootstrap.bats:80-91` updates: drop the `core/Config.scala` assertion; keep `core/project.scala`; add `build/iw-core.jar` and `build/iw-dashboard.jar`.

**Consequences for CLARIFY 2:** Pre-decided — we are keeping `project.scala` as the deps manifest, so CLARIFY 2 collapses to "Option A". No changes to the `scala-cli run` invocation pattern in `iw-run`.

---

### Decision 2: Keep `project.scala` as the deps manifest; no launcher changes

**Resolution:** Option A — pre-decided by Decision 1. The three `scala-cli run … "$CORE_DIR/project.scala" --jar "$CORE_JAR"` invocations at `iw-run:565, 644, 729` remain unchanged. `project.scala` continues to provide the `//> using dep` directives for runtime classpath resolution, while `build/iw-core.jar` provides the compiled core classes.

**Implication:** No changes required to the launcher's command-execution paths in this issue.

---

### Decision 3: Pure file-presence check (no sentinel, no escape hatch)

**Resolution:** Option A — `iw-run` decides "installed tarball" by checking whether `$INSTALL_DIR/build/iw-core.jar` (and `iw-dashboard.jar` for dashboard paths) exists. If yes, use it; otherwise fall through to the Mill query. No sentinel file, no `IW_PREFER_MILL` env var.

**Load-bearing convention this locks in:** "Mill writes to `out/`, scala-cli to `.scala-build/`, releases write to `build/`." Nothing else in the repo writes to `build/` today; we keep it that way. Document this convention in a comment block near `ensure_core_jar` / `ensure_dashboard_jar` in `iw-run`.

**Escape hatch (already exists, not new):** `IW_CORE_JAR=/explicit/path` and `IW_DASHBOARD_JAR=/explicit/path` continue to win over both `build/*.jar` and the Mill query. A developer with deliberately-stale `build/` can either delete the dir or set the env var.

---

### Decision 4: Switch `release.yml` to `self-hosted` + `iw-cli-ci` container

**Resolution:** Option A — `release.yml` will run on `self-hosted` using `ghcr.io/iterative-works/iw-cli-ci:latest`, mirroring the CI workflow's `dashboard-build` job (`ci.yml:68-85`). `WEBAWESOME_NPM_TOKEN` is read from secrets in the build job that invokes Mill (Vite consumes it at build time; the resulting `dashboard.assembly` jar contains compiled assets, not the token).

**Concrete YAML changes for `release.yml`:**
- `runs-on: ubuntu-latest` → `runs-on: self-hosted`
- Add `container:` block with `image: ghcr.io/iterative-works/iw-cli-ci:latest` and `credentials:` (matching the `dashboard-build` pattern).
- Drop the `coursier/setup-action@v1` step (toolchain is in the container).
- Drop the explicit tmux/jq/bats install (already in the container).
- Add `WEBAWESOME_NPM_TOKEN` to the env of the build/package step.

**Risk accepted:** Release publishing now depends on the self-hosted runner being up at tag-push time. Acceptable trade-off; can evolve to Option C (split self-hosted build / hosted publish) later if reliability bites.

---

### Decision 5: Don't ship `build.mill` or `./mill` in the tarball

**Resolution:** Option A — installed tarballs do not contain `./mill`, `.mill-version`, or `build.mill`. The launcher's Mill fallback path is reachable only in dev checkouts. Combined with Decision 1 (drop most of `core/` source), this establishes a clear contract: **installed tarballs are read-only artifacts; for development, clone the repo.**

**Documentation requirement:** `RELEASE.md` must state this explicitly. Add a short section under the tarball-contents description.

**Failure mode if violated:** A user who extracts a tarball into a workspace and tries `./mill` gets "command not found" — a clear signal rather than a confusing partial-build error.

---

## Total Estimates

**Per-Layer Breakdown:**
- Packaging Layer: 1.5-3 hours
- Launcher Layer: 1-2 hours
- Bootstrap Layer: 0-0.5 hours
- CI Layer: 1.5-3 hours
- Test Layer: 1-2 hours
- Docs Layer: 0.5-1 hour

**Total Range:** 5.5-11.5 hours

**Confidence:** Medium

**Reasoning:**
- The seam (`IW_CORE_JAR` / `IW_DASHBOARD_JAR`) already exists; launcher work is mostly an additive `if` branch in two functions.
- Packaging script is short (47 lines) and the new logic is additive — uncertainty is mostly the `core/` source decision.
- CI work has a real fork in the road (CLARIFY 4) with a meaningful effort delta between options.
- BATS tests are mechanical to update once the structure is decided.
- The CLARIFY 1/2 decisions could push effort up if Option B (drop `core/` entirely, translate using-deps) is chosen.

## Recommended Phase Plan

Total estimate is 5.5-11.5h. Per the phase-sizing policy (4-12h total → 1-3 phases; merge layers below the 3h floor with adjacent ones in dependency order), Bootstrap (0-0.5h), Test (1-2h), and Docs (0.5-1h) all fall below the floor. The dependency order is: Packaging produces artifacts → Launcher consumes them → Bootstrap is structurally untouched (just verifies) → CI automates → Tests assert → Docs describe. Two phases line up cleanly along the "produce-the-artifact" / "consume-and-distribute-it" cleavage.

- **Phase 1: Packaging + Launcher (build jars, ship them, consume them)**
  - Includes: Packaging Layer, Launcher Layer, Bootstrap Layer (sanity check only)
  - Estimate: 2.5-5.5 hours
  - Rationale: These layers form the minimum coherent slice — without both, the tarball either lacks jars or has them but can't find them. Reviewing them as one PR lets the reviewer follow the artifact from `package-release.sh` writing `build/*.jar` to `iw-run` reading it. Bootstrap belongs here because it's tested in the same end-to-end "extract a tarball and run iw-run" flow.

- **Phase 2: CI + Tests + Docs (automate, verify, document)**
  - Includes: CI Layer, Test Layer, Docs Layer
  - Estimate: 3-6 hours
  - Rationale: Once Phase 1 produces a working tarball locally, Phase 2 makes that tarball reproducible from CI, locked in by BATS structure tests, and explained in `RELEASE.md`. Bundling these is justified because they share the same target (the published release) and a single PR keeps the release-pipeline change reviewable as one unit. The CI work is the largest individual layer here and naturally anchors the phase.

**Total phases:** 2 (for total estimate 5.5-11.5 hours)

## Testing Strategy

### Per-Layer Testing

**Packaging Layer:**
- Manual: run `scripts/package-release.sh 0.5.0-test` locally, inspect tarball with `tar -tzf`, confirm `build/iw-core.jar` and `build/iw-dashboard.jar` are present and non-empty.
- Manual: extract tarball to a clean temp dir, confirm structure matches the documented layout.

**Launcher Layer:**
- BATS: extend `test/bootstrap.bats` with a test that extracts the tarball, hides Mill from `PATH`, and runs `./iw-run --list` and `./iw-run --bootstrap` — both must succeed.
- BATS: confirm `IW_CORE_JAR=/explicit/path ./iw-run …` still wins over `$INSTALL_DIR/build/iw-core.jar` (env override has highest priority).
- Unit (manual): in a dev checkout (no `build/` dir), `./iw --bootstrap` still drives Mill — regression check that we didn't break the dev path.

**Bootstrap Layer:**
- BATS: existing `test "iw-run --bootstrap pre-compiles successfully"` updated to assert success without invoking Mill.
- Manual: full end-to-end run of `iw-bootstrap` against a real GitHub release once published.

**CI Layer:**
- Dry run: push a `v0.5.1-rc1` tag (or use `workflow_dispatch`) and confirm `release.yml` produces the expected tarball. Inspect the uploaded artifact via `gh release download`.
- Verify the `vlatest` rolling-tag flow still works end-to-end (`release.yml:60-80`).

**Test Layer:**
- Self-test: BATS suite must be green both locally (`./iw ./test e2e`) and in CI's `test` job.

**Docs Layer:**
- Editorial review only.

**Test Data Strategy:**
- BATS tests build the tarball from the current checkout (re-running `package-release.sh` if needed). The hardcoded `iw-cli-0.1.0-dev.tar.gz` filename in `test/bootstrap.bats:13, 29, 44, 58, 66, 75-91` is already stale (current VERSION is 0.5.0); flag for a separate fix unless the BATS update naturally makes this trivial to address.

**Regression Coverage:**
- The latent "no Mill in tarball → bootstrap fails" bug needs a test that explicitly hides Mill from `PATH` during BATS execution. That test is the regression gate.
- Dev-mode workflow (`./iw <cmd>` from a checkout) must keep working — covered by the existing test suite, which runs from the checkout.

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
None at install time. Possibly a new (optional) `IW_PREFER_MILL` env var if CLARIFY 3 Option C is chosen.

### Rollout Strategy
- This is a packaging-format change. Any existing installation downloaded under the old format keeps working until the user runs `iw update` (per `iw-bootstrap:58-77`), which re-downloads the current version's tarball. Old tarballs published before this change continue to be downloadable from their versioned releases — no retroactive invalidation.
- Cut a new minor version (e.g. 0.6.0) on first release with the new format, so users can pin to the old format if needed.

### Rollback Plan
- If the new `release.yml` produces a broken tarball, `gh release delete vX.Y.Z` and republish from a hotfix branch. The `vlatest` rolling tag also needs to be force-moved back to a known-good version.
- The launcher change is backward-compatible: a tarball without `build/` falls through to the Mill query, exactly as today. So even if packaging breaks, the launcher itself doesn't regress.

## Dependencies

### Prerequisites
- IW-344 merged (commit 259bc54) — provides `core.jar` Mill task. ✅
- IW-345 merged (commit 8a9b603) — provides `dashboard.assembly` Mill task. ✅
- `WEBAWESOME_NPM_TOKEN` available to whichever runner builds the release. (Already configured for `ci.yml:dashboard-build`.)

### Layer Dependencies
- Packaging Layer must complete before Launcher Layer can be tested against a real tarball.
- CI Layer depends on Packaging Layer being correct (CI runs `package-release.sh`).
- Test Layer depends on Packaging Layer being stable (asserts tarball structure).
- Bootstrap Layer is order-independent (no structural change expected).
- Docs Layer is naturally last (describes the final state).

### External Blockers
- None. `iw-cli-ci` container is already published; Web Awesome token is already in the secrets store.

## Risks & Mitigations

### Risk 1: `release.yml` builds break because Mill/Node/Yarn provisioning is incomplete on `ubuntu-latest`
**Likelihood:** Medium
**Impact:** High (blocks all releases)
**Mitigation:** Test the workflow against a release-candidate tag (`v0.5.1-rc1`) before any real release. CLARIFY 4 Option A (use the existing `iw-cli-ci` container) eliminates this risk.

### Risk 2: Launcher auto-detection picks up a stale `build/` directory in a dev checkout
**Likelihood:** Low
**Impact:** Medium (developer sees stale behavior, debugging confusion)
**Mitigation:** The convention of "Mill writes to `out/`, releases write to `build/`" is already implicit; document it explicitly in the launcher comment block. If the risk materializes, add CLARIFY 3 Option C escape hatch.

### Risk 3: Dropping most of `core/` source breaks an undocumented runtime code path
**Likelihood:** Medium
**Impact:** High (breaks installed users on first command)
**Mitigation:** Decision 1 keeps `core/project.scala`; the audit gate (5-minute scan of `commands/` and `core/` for runtime reads of `core/**/*.{scala,css,js}`) must pass before Phase 1 completes. If anything else under `core/` is read at runtime, surface it before merging Phase 1.

### Risk 4: `WEBAWESOME_NPM_TOKEN` leaks into tarball or build logs
**Likelihood:** Low
**Impact:** High (license credential exposure)
**Mitigation:** Token is consumed only at Vite build time; the resulting `dashboard.assembly` jar contains compiled assets, not the token. Add a guard step in `package-release.sh` that greps the staged tarball directory for the token value before tarring (defense-in-depth).

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Packaging Layer** — first, because it produces the artifact every other layer is reasoning about. Implementing it produces a tarball you can extract and probe.
2. **Launcher Layer** — second, because its correctness is verified by extracting the tarball produced in step 1 and running it.
3. **Bootstrap Layer** — third (trivial; verify only). May be no-op if no changes are needed.
4. **Test Layer** — fourth, locking in the contract that the launcher and packaging both honor. Done in parallel with Phase 2 of the recommended phase plan.
5. **CI Layer** — fifth, automating what works locally. Tested via release-candidate tag.
6. **Docs Layer** — last, describing the final state.

**Ordering Rationale:**
- Each layer's correctness depends on the previous layer producing a stable artifact.
- Phases 4 and 5 can be interleaved (write the BATS test, run it locally, push to CI) but tests should land before CI changes are merged so the CI job has the new assertions to protect against regressions.
- All five CLARIFY markers were resolved (Decisions 1-5) before implementation; see "Technical Risks & Uncertainties" section for the recorded decisions.

## Documentation Requirements

- [ ] Code documentation: comment block in `iw-run` near `ensure_core_jar` / `ensure_dashboard_jar` describing the resolution order.
- [ ] API documentation: N/A.
- [ ] Architecture decision record: optional but recommended — capture the "build at release-time, ship jars" decision and the `core/` source disposition.
- [ ] User-facing documentation: N/A (the change is invisible to users at the command level).
- [ ] Migration guide: brief note in release notes — "v0.6.0 ships pre-built jars; install size on disk changes; offline operation no longer requires Mill."
- [ ] `RELEASE.md`: update tarball contents description, troubleshooting checklist, and step 2 of the release process.

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. ✅ All CLARIFY markers resolved (Decisions 1-5 recorded above).
2. Run **wf-create-tasks** with IW-346.
3. Run **wf-implement** for layer-by-layer implementation, starting with Phase 1 (Packaging + Launcher).
