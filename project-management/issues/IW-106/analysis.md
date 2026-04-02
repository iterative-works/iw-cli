# Technical Analysis: Restructure source code location - separate from .iw directory

**Issue:** IW-106
**Created:** 2026-04-02
**Status:** Draft

## Problem Statement

The iw-cli source code lives under `.iw/` (commands/, core/, test/, scripts/, VERSION), but this nesting is vestigial. Consumer projects use `iw-bootstrap` which downloads releases to `~/.local/share/iw/versions/` with a flat layout. The mismatch between dev layout (nested under `.iw/`) and release layout (flat) creates real bugs and unnecessary complexity:

1. **VERSION is broken in dev mode.** `read_iw_version` in `iw-run` reads `$INSTALL_DIR/VERSION`, but INSTALL_DIR is the repo root and VERSION sits at `.iw/VERSION`. Dev always reports `0.0.0`.
2. **`./iw` must override defaults.** The dev bootstrap sets `IW_COMMANDS_DIR` and `IW_CORE_DIR` because the defaults (`$INSTALL_DIR/commands`, `$INSTALL_DIR/core`) point to the wrong place.
3. **`iw-bootstrap` layout sniffing.** IW_HOME mode has a branch that checks whether `.iw/commands` or `commands` exists -- purely to accommodate the dev/release mismatch.
4. **`package-release.sh` remaps paths.** It copies `.iw/commands/` to `commands/` and `.iw/core/` to `core/`.
5. **`release.sh` updates the wrong VERSION.** It patches `.iw/commands/version.scala` but does not touch `.iw/VERSION`.

## Proposed Solution

### High-Level Approach

Move source directories (`commands/`, `core/`, `test/`, `scripts/`, `VERSION`) from `.iw/` to the repo root so that dev layout matches release layout. After the move, `iw-run` defaults work in dev mode without overrides, VERSION is found correctly, `package-release.sh` becomes a simple copy, and `iw-bootstrap` drops its layout-sniffing branch.

The `.iw/` directory continues to exist in the iw-cli repo, but only for project-level config (`config.conf`) and transient cache -- the same role it plays in consumer projects.

### Why This Approach

The alternative -- changing the release tarball to match the `.iw/` layout -- would break all existing consumer installations and add the `.iw/` nesting to every consumer project. That's backward. The flat layout is already the correct target; the source tree should match it.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

This issue is a structural refactoring with no new domain logic, application services, or UI components. The standard domain/application/infrastructure/presentation layering does not apply. Instead, the work breaks down into functional areas.

---

### Layer 1: File System Move

**Components:**
- `commands/` -- move from `.iw/commands/` to repo root
- `core/` -- move from `.iw/core/` to repo root (includes `core/test/`)
- `test/` -- move from `.iw/test/` to repo root (BATS E2E tests)
- `scripts/` -- move from `.iw/scripts/` to repo root
- `VERSION` -- move from `.iw/VERSION` to repo root
- `docs/` -- move from `.iw/docs/` to `docs/api/` (API reference docs, disjoint from existing operational docs)
- `llms.txt` -- move from `.iw/llms.txt` to repo root

**Responsibilities:**
- `git mv` all source directories/files
- Preserve git history via renames
- Ensure `.iw/` retains only `config.conf`, `cache/`, and `commands/test.scala` (project-specific command)

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### Layer 2: Shell Script Updates

**Components:**
- `./iw` (dev bootstrap) -- remove `IW_COMMANDS_DIR` and `IW_CORE_DIR` overrides; defaults in `iw-run` now work
- `iw-bootstrap` -- remove layout-sniffing branch in `IW_HOME` mode
- `iw-run` -- no changes needed (defaults already point to `$INSTALL_DIR/commands` and `$INSTALL_DIR/core`)
- `scripts/package-release.sh` -- update `PROJECT_ROOT` derivation (now one level up instead of two); simplify copy since source layout matches target
- `scripts/release.sh` -- update `PROJECT_ROOT` derivation; update path to `version.scala` and `VERSION`

**Responsibilities:**
- Eliminate dev-vs-release path divergence
- Fix VERSION file resolution (now `$INSTALL_DIR/VERSION` works in both dev and release)
- Simplify `package-release.sh` path remapping

**Estimated Effort:** 2-3 hours
**Complexity:** Moderate (must verify all path derivations are correct)

---

### Layer 3: Build & CI Updates

**Components:**
- `.git-hooks/pre-commit` -- update `scala-cli compile` path from `.iw/core/` to `core/`
- `.git-hooks/pre-push` -- update compile and scalafix paths
- `.github/workflows/ci.yml` -- update compile, lint, and test paths
- `.github/workflows/release.yml` -- update `package-release.sh` path
- `.gitignore` -- keep `.iw/cache/` entry (still valid for consumer projects using iw-bootstrap)

**Responsibilities:**
- All CI and hook paths reference the new locations
- Build and test pipeline works with the new layout

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### Layer 4: Scala Source Updates

**Components:**
- `.iw/commands/test.scala` -- stays in place as a **project-specific command** (not shipped in release tarball). Update `runUnitTests`, `runCommandCompileCheck`, `runE2ETests` to find `core/`, `commands/`, `test/` via `IW_INSTALL_DIR` env var instead of `Constants.Paths.IwDir`
- `iw-run` -- export `INSTALL_DIR` as `IW_INSTALL_DIR` so project-specific commands can find the installation root
- `commands/version.scala` -- currently reads VERSION relative to `IW_COMMANDS_DIR`; after the move, the path resolution stays the same (`$IW_COMMANDS_DIR/../VERSION` still works since commands/ and VERSION are siblings)
- `core/model/Constants.scala` -- `Paths.IwDir` stays as `.iw` (still used for consumer project config path)
- Documentation files (`CLAUDE.md`, `core/CLAUDE.md`, `docs/`) -- update directory references

**Responsibilities:**
- `test.scala` must find core, commands, and test directories using `IW_INSTALL_DIR`
- `iw-run` must export `IW_INSTALL_DIR`
- No changes to `Constants.Paths.IwDir` -- it correctly describes where consumer projects keep config

**Estimated Effort:** 2-3 hours
**Complexity:** Moderate

---

### Layer 5: BATS Test Updates

**Components:**
- `test/review-state.bats` -- 4 lines referencing `$PROJECT_ROOT/.iw/core/test/resources/`; update to `$PROJECT_ROOT/core/test/resources/`
- `test/schema.bats` -- 1 line referencing `$PROJECT_ROOT/.iw/core/test/resources/`
- All BATS test helpers/setup -- verify `PROJECT_ROOT` derivation still works after test/ moves

**Responsibilities:**
- Tests that reference iw-cli source paths (5 lines across 2 files) update to new locations
- Tests that create `.iw/commands/` in temp dirs (simulating consumer projects) remain unchanged
- E2E tests pass after restructure

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

## Technical Decisions

### Patterns

- Use `git mv` for all moves to preserve blame history
- Single commit for the file move, separate commits for script/code updates (or one atomic commit -- see CLARIFY below)

### Technology Choices

- **Tools**: git mv, shell scripting, Scala source edits
- **No new dependencies**

### Integration Points

- `test.scala` currently derives paths from `Constants.Paths.IwDir` (which means "the consumer's .iw dir"). After the move, `test.scala` needs a different path strategy for finding iw-cli's own source code. The cleanest approach: use `IW_COMMANDS_DIR` and `IW_CORE_DIR` env vars (already set by `iw-run`) to find commands/ and core/, and derive test/ as a sibling of core/.
- `version.scala` reads `$IW_COMMANDS_DIR/../VERSION` -- this continues to work since commands/ and VERSION remain siblings.
- `scripts/release.sh` and `scripts/package-release.sh` derive `PROJECT_ROOT` as `$SCRIPT_DIR/../..` (two levels up from `.iw/scripts/`). After moving to `scripts/` at repo root, this becomes `$SCRIPT_DIR/..` (one level up).

## Technical Risks & Uncertainties

### RESOLVED: Commit Strategy

**Decision:** Single atomic commit. Move files and update all references in one commit. No intermediate broken state, no broken `git bisect`.

---

### RESOLVED: test.scala Path Resolution

**Decision:** `test.scala` stays at `.iw/commands/test.scala` as a **project-specific command** (not a shared command). After the move, shared commands live in `commands/` at repo root; `.iw/commands/` becomes the project-specific command directory as `iw-run` already expects. `test.scala` will use `IW_INSTALL_DIR` (exported from `iw-run`) to find `core/`, `commands/`, and `test/` at the installation root.

`iw-run` already computes `INSTALL_DIR` but doesn't export it. Exporting it as `IW_INSTALL_DIR` gives project-specific commands a clean way to find the installation root.

---

### RESOLVED: .iw/docs/ Merge Strategy

**Decision:** Merge `.iw/docs/` into `docs/api/`. The contents are disjoint — `.iw/docs/` contains Scala API reference docs (class/module docs for command authors), while `docs/` contains operational/design docs (server config, command reference, plans). Both target iw-cli developers but serve different purposes. The `api/` subdirectory keeps them organized.

---

## Total Estimates

**Per-Layer Breakdown:**
- Layer 1 (File Move): 1-2 hours
- Layer 2 (Shell Scripts): 2-3 hours
- Layer 3 (Build & CI): 1-2 hours
- Layer 4 (Scala Sources): 2-3 hours
- Layer 5 (BATS Tests): 1-2 hours

**Total Range:** 7 - 12 hours

**Confidence:** High

**Reasoning:**
- All changes are mechanical path updates with clear before/after states
- No new logic or architecture -- purely structural
- The BATS test update scope is small (5 lines referencing source paths; ~250 lines referencing consumer `.iw/commands/` remain unchanged)
- The main risk is missing a path reference, but grep/test coverage catches that

## Testing Strategy

### Per-Layer Testing

**Layer 1 (File Move):**
- Verify: `git status` shows renames, no deletions without corresponding additions
- Verify: `.iw/` contains only `config.conf` and `cache/`

**Layer 2 (Shell Scripts):**
- Manual: `./iw version` shows correct version (not 0.0.0)
- Manual: `./iw --list` works
- Manual: `IW_HOME=/path/to/repo ./iw-bootstrap --list` works in a consumer project

**Layer 3 (Build & CI):**
- Run pre-commit hook: `scala-cli compile --scalac-option -Werror core/`
- Run pre-push hook locally
- CI pipeline passes on PR

**Layer 4 (Scala Sources):**
- `./iw test unit` passes
- `./iw test compile` passes

**Layer 5 (BATS Tests):**
- `./iw test e2e` passes
- Specifically verify review-state and schema tests pass

**Regression Coverage:**
- Full test suite (`./iw test`) must pass
- `package-release.sh` produces a valid tarball with the expected structure
- Consumer project bootstrap (`iw-bootstrap`) works with IW_HOME pointing to the restructured repo

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
None. Consumer projects' `.iw/config.conf` is unaffected.

### Rollout Strategy
Merge to main. The next release tarball will be identical in structure to the current one (already flat). No consumer impact.

### Rollback Plan
Revert the commit. All changes are in the dev repo only; no consumer-facing impact.

## Dependencies

### Prerequisites
- Clean working tree on the branch

### Layer Dependencies
- Layer 1 (file move) must happen first
- Layers 2-5 can be done in any order after Layer 1, but practically they should be done together in one atomic commit

### External Blockers
- None

## Risks & Mitigations

### Risk 1: Missed path reference
**Likelihood:** Medium
**Impact:** Low (broken build caught immediately by CI)
**Mitigation:** After the move, grep the entire repo for `.iw/commands`, `.iw/core`, `.iw/test`, `.iw/scripts`, `.iw/VERSION` and verify each remaining reference is about consumer project config, not source layout.

### Risk 2: scala-cli .scala-build cache invalidation
**Likelihood:** High
**Impact:** Low (slower first build after move)
**Mitigation:** Delete `.scala-build/` directories after the move. Document this in the commit message.

### Risk 3: Git blame disruption
**Likelihood:** High (inherent to file moves)
**Impact:** Low
**Mitigation:** Use `git mv` so `git log --follow` works. Single commit for the move keeps the rename detection clean.

---

## Implementation Sequence

**Recommended approach: Single atomic commit**

1. **File moves** (`git mv`) -- commands/, core/, test/, scripts/, VERSION, docs/, llms.txt
2. **Shell script updates** -- ./iw, iw-bootstrap, scripts/release.sh, scripts/package-release.sh
3. **Build/CI updates** -- pre-commit, pre-push, ci.yml, release.yml
4. **Scala source updates** -- test.scala path resolution, documentation files
5. **BATS test updates** -- review-state.bats, schema.bats source-path references
6. **Verification** -- grep for stale `.iw/` source references, run full test suite

**Ordering Rationale:**
- All steps should be in one commit to avoid intermediate broken states
- Within that commit, the logical order is: move files, then update all references, then verify
- There is no opportunity for parallel implementation since everything depends on the file move

## Documentation Requirements

- [x] Update `CLAUDE.md` at repo root (architecture section references `.iw/` layout)
- [x] Update `core/CLAUDE.md` (path references)
- [x] Update `README.md` if it references `.iw/` source layout
- [x] Update `CONTRIBUTING.md` if it references `.iw/` paths
- [x] Update `RELEASE.md` if it references `.iw/scripts/`
- [ ] No API changes
- [ ] No migration guide needed (no consumer impact)

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers (commit strategy, test.scala path resolution, docs merge)
2. Run **wf-create-tasks** with the issue ID
3. Run **wf-implement** for layer-by-layer implementation
