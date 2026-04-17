# Phase 2: Update command execution to use jar

**Issue:** IW-344
**Phase:** 2 of 3
**Estimate:** 2-3h
**Status:** Pending

## Goals

- Switch all three `execute_command()` invocation paths in `iw-run` (project, plugin, shared) from passing `$core_files` as source files to passing `--jar "$CORE_JAR" "$CORE_DIR/project.scala"` to `scala-cli run`.
- Remove the redundant `core_files` find-and-collect logic from each of those three paths.
- Wire `ensure_core_jar` (defined in Phase 1) as the single pre-execution hook so the jar is rebuilt on-demand when core sources are newer than the jar.
- Preserve every other aspect of command execution: hook discovery, plugin lib discovery, `IW_HOOK_CLASSES` env propagation, `check_version_requirement`, `cd "$PROJECT_DIR"`, and `exec scala-cli run` semantics.

This phase delivers the runtime switch that realises the ~27x cold-start speedup measured in the spike. Dev-loop behavior (mtime-triggered auto-rebuild) becomes user-visible for the first time.

## Scope

### In scope

- `iw-run` `execute_command()` — three invocation sites (project commands, plugin commands, shared commands) rewritten to use `--jar "$CORE_JAR" "$CORE_DIR/project.scala"`.
- A single `ensure_core_jar` call placed at the right spot so all three paths benefit without duplication.
- Removal of the three now-redundant `core_files=$(find ... )` blocks in `execute_command()`.

### Out of scope (deferred)

- **Phase 3:** BATS test updates, `test.scala` jar pre-build, new BATS tests covering missing/stale jar paths, `IW_CORE_JAR` override coverage, `project-commands-execute.bats` regression.
- Changes to `scripts/package-release.sh` (separate concern, mentioned in analysis).
- Any modifications to `commands/*.scala`, `core/*.scala`, or `core/project.scala` — this phase is strictly an `iw-run` change.
- `bootstrap()`, `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()` — Phase 1 already shipped these, and their bodies are not edited in this phase.

## Dependencies

Phase 1 (merged) delivered the following pieces that Phase 2 consumes directly. Verified against `/home/mph/Devel/iw/iw-cli-IW-344/iw-run` as it currently stands:

- `CORE_JAR` variable (line 14), default `$INSTALL_DIR/build/iw-core.jar`, override via `IW_CORE_JAR` env var.
- `IW_CORE_JAR` re-exported (line 15) so sub-processes see the resolved path.
- `build_core_jar()` function (lines 27-47) — prints "Rebuilding core jar..." on stderr, runs `scala-cli --power package --library -o "$CORE_JAR" $core_files --server=false`.
- `core_jar_stale()` function (lines 49-62) — returns 0 when jar missing or any non-test `.scala` source is newer.
- `ensure_core_jar()` function (lines 64-69) — calls `build_core_jar` when `core_jar_stale` returns true. Defined in Phase 1 but currently unused; this phase wires it in.
- `CORE_DIR` variable (line 13), points at `$IW_CORE_DIR` (default `$INSTALL_DIR/core`).
- `core/project.scala` — exists at `/home/mph/Devel/iw/iw-cli-IW-344/core/project.scala`, contains only `//> using dep` directives (no code). scala-cli picks up the dep directives from it when passed as a source file alongside `--jar`.

External: `scala-cli --jar` accepts a library jar and mixes its classpath into compilation. The spike (analysis.md lines 85-103) confirmed this recipe works and produces correct output for the core-heavy `commands/status.scala`.

## Approach

### Which code paths change

`execute_command()` in `iw-run` (lines 515-722) has three mutually-exclusive branches, each ending in an `exec scala-cli run ...` call. The three branches and their current end-of-branch invocations are:

1. **Project command** (lines 524-553): ends at line 553.
   ```bash
   exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $core_files -- "$@"
   ```
2. **Plugin command** (lines 555-636): ends at line 636.
   ```bash
   IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $hook_files $core_files $lib_files -- "$@"
   ```
3. **Shared command** (lines 638-721): ends at line 720.
   ```bash
   IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $hook_files $core_files -- "$@"
   ```

Each branch also has its own `core_files=$(find ... )` collection:
- Project: line 546.
- Plugin: line 609.
- Shared: line 710.

All three `core_files` blocks and the references in the `exec` lines are rewritten in this phase.

### Ordering within the phase

1. Add a single `ensure_core_jar` call near the top of `execute_command()`, before any of the three branches diverge. This centralises the jar freshness check and eliminates the risk of drift between branches.
2. Rewrite each of the three branches in turn: remove the `core_files=$(find ... )` block, then change the `exec scala-cli run` line to drop `$core_files` and add `"$CORE_DIR/project.scala" --jar "$CORE_JAR"` in its place.
3. Keep hook file discovery and `IW_HOOK_CLASSES` propagation exactly as they are.
4. Verify: run the smoke-test matrix (see Testing Strategy) before finalising the commit.

### Sharing vs duplication

A prior design question is whether to share a single `exec scala-cli run` line across branches. Answer: **no** — the three branches have genuinely different source-file lists (hooks, lib, project vs shared) and a different working directory for project commands would be awkward to express through a helper. The right level of sharing is `ensure_core_jar` (one call, covers all three), not the `exec` line itself.

## Files to Modify

- `/home/mph/Devel/iw/iw-cli-IW-344/iw-run` — only file touched in this phase. Changes are localised to `execute_command()` (lines 515-722).

No other file — no `commands/`, no `core/`, no tests — is touched in Phase 2.

## Component Specifications / API Contracts

### `ensure_core_jar` call-site contract

Placed once at the top of `execute_command()`, immediately after the `cmd_name` / `shift` dance (i.e., around line 518, before the `if [[ "$cmd_name" == ./* ]]` branch). Rationale: every branch needs a fresh jar, and all branches run `check_version_requirement` + `cd "$PROJECT_DIR"` + `exec scala-cli run`; putting `ensure_core_jar` up front makes the freshness guarantee a property of `execute_command` itself.

Contract:
- On entry: `$CORE_JAR` points at a path that may or may not exist.
- On exit: `$CORE_JAR` exists and is at least as new as every non-test `.scala` under `$CORE_DIR`.
- Side effect: if rebuild happens, `"Rebuilding core jar..."` is emitted on stderr. Otherwise silent.
- On failure: `set -euo pipefail` at the top of `iw-run` propagates any `scala-cli package` failure and `execute_command` exits non-zero before reaching the three branches.

### `scala-cli run` invocation pattern

The target invocation pattern for all three branches is:

```bash
scala-cli run -q --suppress-outdated-dependency-warning \
    "$cmd_file" <branch-specific-source-files> "$CORE_DIR/project.scala" \
    --jar "$CORE_JAR" \
    -- "$@"
```

Where `<branch-specific-source-files>` is:
- **Project command:** (nothing — project commands have no hooks or lib)
- **Plugin command:** `$hook_files $lib_files`
- **Shared command:** `$hook_files`

Concrete post-Phase-2 invocations:

**Project command (replaces line 553):**
```bash
# shellcheck disable=SC2086
exec scala-cli run -q --suppress-outdated-dependency-warning \
    "$cmd_file" "$CORE_DIR/project.scala" \
    --jar "$CORE_JAR" -- "$@"
```

**Plugin command (replaces line 636):**
```bash
# shellcheck disable=SC2086
IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning \
    "$cmd_file" $hook_files $lib_files "$CORE_DIR/project.scala" \
    --jar "$CORE_JAR" -- "$@"
```

**Shared command (replaces line 720):**
```bash
# shellcheck disable=SC2086
IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning \
    "$cmd_file" $hook_files "$CORE_DIR/project.scala" \
    --jar "$CORE_JAR" -- "$@"
```

Argument-order rationale:
- `$cmd_file` first keeps the primary "what is this" readable.
- Hook files and plugin lib files (when present) come before `project.scala` because they may declare additional `//> using dep` directives via their own directives.
- `"$CORE_DIR/project.scala"` must be passed as a source (it contains `//> using dep` directives that scala-cli only honors when the file is part of the compilation set).
- `--jar "$CORE_JAR"` placed after all source files for readability; scala-cli accepts flag-after-positional.
- `--` before `"$@"` remains unchanged — this separates scala-cli's args from the command's args.
- `# shellcheck disable=SC2086` directive remains on the `exec` line because `$hook_files` / `$lib_files` are deliberately word-split.

### Removed: `core_files=$(find ... )` blocks

The following three blocks are **deleted** as part of this phase (no replacement):
- Line 546: project command `core_files=$(find "$CORE_DIR" -name "*.scala" -not -path "*/test/*" -not -path "*/.scala-build/*" | tr '\n' ' ')`
- Line 609: plugin command (identical)
- Line 710: shared command (identical)

The `local core_files` declarations (lines 545, 608, 709) are also removed with them.

Note: this does **not** affect `build_core_jar()`, which has its own `core_files` collection at line 37. That one stays because the jar itself needs to be built from sources.

## Testing Strategy

### Manual smoke tests (pre-commit, before Phase 3 lands automation)

Run from `/home/mph/Devel/iw/iw-cli-IW-344`:

1. **Fresh jar present, no source changes:** `./iw version`. Expect: command runs, no "Rebuilding core jar..." message, output is the installed version string.
2. **Stale jar:** `touch core/model/Settings.scala; ./iw version`. Expect: "Rebuilding core jar..." on stderr, jar rebuilt, command runs. Second invocation of `./iw version` should be silent again.
3. **Missing jar:** `rm -f build/iw-core.jar; ./iw version`. Expect: "Rebuilding core jar..." on stderr, jar built, command runs.
4. **Shared command with core types:** `./iw status` (which imports core model types). Expect: output is identical to Phase-1 behavior. This is the cold-start-speedup poster-child.
5. **Project command:** `./iw ./test unit` or any other `.iw/commands/*.scala` command. Expect: works as before. Note from memory: project commands must import via full sub-package paths (e.g., `iw.core.adapters.*`).
6. **Plugin command (if any plugin available):** `./iw <plugin>/<cmd>`. Expect: hooks and lib still load correctly, `IW_HOOK_CLASSES` still set.
7. **`IW_CORE_JAR` override:** `IW_CORE_JAR=/tmp/test-iw-core.jar ./iw --bootstrap && IW_CORE_JAR=/tmp/test-iw-core.jar ./iw version`. Expect: jar built at `/tmp/test-iw-core.jar`, command uses it.

### Automated test expectations

- All existing BATS tests in `test/` **must continue to pass**. They currently exercise the source-file mode; after Phase 2 they will exercise jar mode transparently, as long as the jar is built at the expected path. Tests that pre-build the jar (via the Phase-3 `test.scala` change) will pass; tests that don't will trigger an `ensure_core_jar` auto-rebuild on first invocation.
- Per memory, BATS E2E tests export `IW_SERVER_DISABLED=1` in `setup()`. The `IW_CORE_JAR` env var handling in tests is a Phase 3 concern.
- Regression indicator: `test/project-commands-execute.bats` — this file exercises a project command that imports core library types, so if jar-mode breaks classpath visibility for core types, this test fails first.

### Pre-commit gates

- `shellcheck` (if configured) must pass on modified `iw-run`. Keep the `# shellcheck disable=SC2086` directive on each `exec` line.
- Format check and `scala-cli compile --scalac-option -Werror core/` (per project pre-commit) — these are unaffected since no `.scala` file changes in this phase.

## Acceptance Criteria

- [ ] `execute_command()` calls `ensure_core_jar` exactly once, before the three branches diverge.
- [ ] Project command branch (ex-line 546 block + line 553 exec) no longer references `$core_files`; its `exec scala-cli run` line passes `"$CORE_DIR/project.scala" --jar "$CORE_JAR"` instead.
- [ ] Plugin command branch (ex-line 609 block + line 636 exec) no longer references `$core_files`; its `exec scala-cli run` line passes `"$CORE_DIR/project.scala" --jar "$CORE_JAR"` and still preserves `$hook_files`, `$lib_files`, and `IW_HOOK_CLASSES`.
- [ ] Shared command branch (ex-line 710 block + line 720 exec) no longer references `$core_files`; its `exec scala-cli run` line passes `"$CORE_DIR/project.scala" --jar "$CORE_JAR"` and still preserves `$hook_files` and `IW_HOOK_CLASSES`.
- [ ] All three `local core_files=$(find ... )` declarations are removed from `execute_command()`.
- [ ] `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()`, `bootstrap()` are not edited in this phase.
- [ ] `./iw version` (shared), `./iw status` (core-heavy shared), at least one project command, and at least one plugin command (if available) all execute successfully against jar mode.
- [ ] When core source is newer than the jar, a single `"Rebuilding core jar..."` message appears on stderr before the command runs.
- [ ] Existing BATS tests continue to pass (after any Phase-3 test harness adjustments; for this phase, a hand-spot-check of the key tests is sufficient).
- [ ] Pre-commit hooks pass on the modified `iw-run`.

## Risks & Notes

### Risk: `scala-cli` does not accept `--jar` before the trailing `--`

Mitigated by spike. The confirmed recipe (analysis.md lines 222-227) places `--jar "$CORE_JAR"` before the `--` separator, alongside source files. No change to scala-cli CLI parsing behavior expected.

### Risk: `"$CORE_DIR/project.scala"` missing in some installation layouts

`core/project.scala` is currently tracked in git at `/home/mph/Devel/iw/iw-cli-IW-344/core/project.scala` (603 bytes, `//> using dep` directives only). The dev layout and the release layout both include `core/`, per CLAUDE.md ("Dev layout matches release layout"). If a stripped-down install somehow lacks `project.scala`, `scala-cli run` will fail loudly with a clear "file not found" error — this is acceptable and matches the fail-fast behavior of `build_core_jar()`.

### Risk: interactive commands compete with jar rebuild output

`ensure_core_jar` emits `"Rebuilding core jar..."` on stderr. For interactive commands (e.g., a command that prompts via stdin), this message appears before the prompt. Acceptable — it's a one-time message per edit, on stderr, and signals why the first response is slow. No fallback or quiet mode needed for Phase 2.

### Risk: concurrent invocations of `execute_command` both see a stale jar

Two simultaneous `./iw <cmd>` invocations could both call `ensure_core_jar` and both start rebuilding. `scala-cli package` with `--server=false` writes atomically to `$CORE_JAR` (well, close to atomically — it writes to a temp file and renames). Worst case: one rebuild wins, the other is wasted work. No lock file needed for Phase 2 — dev loops are single-user, and the cost of a redundant 1.1s rebuild is negligible. Flag for Phase 3 or a later hardening pass if we observe it.

### Note: `project.scala` appears twice in the classpath pipeline

When `build_core_jar` runs, it includes `core/project.scala` as a source (via the `find` glob at line 37). That puts `project.scala`'s `//> using dep` directives into... nothing, actually — scala-cli ignores directives in jar-packaged sources at consumption time; only the compiled `.class`/`.tasty` files end up in the jar. This is exactly why Phase 2 must re-pass `project.scala` as a source file at `scala-cli run` time: the directives need to reach the command compilation, which they only do when the file is in the compilation source set.

### Note: `ensure_core_jar` placement is an explicit design call

Alternative placements considered and rejected:
- Inside each of the three branches, immediately before `exec` — works but duplicates the call three times.
- Inside `main()` before dispatching to `execute_command` — rejected because `main()` also handles `--list` and `--describe`, which don't need the jar.
- Inside `check_version_requirement` — rejected because that function should stay focused on version checking.

The chosen spot (top of `execute_command`, before branch selection) is the tightest scope that fires exactly once per command invocation.
