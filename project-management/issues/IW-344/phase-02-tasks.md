# Phase 2 Tasks: Update command execution to use jar

**Issue:** IW-344
**Phase:** 2 of 3
**Estimate:** 2-3h

## Setup

- [ ] [setup] Re-read `execute_command()` in `iw-run` (lines 515-722), particularly the three `exec scala-cli run` lines (553, 636, 720) and the three `core_files=$(find ... )` blocks (545-546, 608-609, 709-710), to confirm context matches the line numbers in phase-02-context.md.
- [ ] [setup] Confirm Phase 1 pieces are in place: `CORE_JAR` (line 14), `IW_CORE_JAR` export (line 15), `build_core_jar` (lines 29-47), `core_jar_stale` (lines 51-62), `ensure_core_jar` (lines 65-69). `ensure_core_jar` exists but is currently unused — this phase wires it in.
- [ ] [setup] Confirm `core/project.scala` exists at `$CORE_DIR/project.scala` and contains only `//> using dep` directives (no code).

## Tests

- [ ] [test] No new automated tests in this phase; BATS coverage for missing/stale jar paths and `IW_CORE_JAR` override is deferred to Phase 3.
- [ ] [test] Before editing, run `./iw version` once to capture baseline output — used for comparison after each invocation-site change.
- [ ] [test] Hand-spot-check the regression indicator `test/project-commands-execute.bats` mentally: it exercises a project command that imports core library types, so it is the first test to rerun manually if jar-mode breaks classpath visibility.

## Implementation

- [ ] [impl] In `execute_command()`, insert a single `ensure_core_jar` call immediately after `shift` (line 517) and before the `local is_project_cmd=false` declaration — i.e., around line 518, before any of the three `if/elif/else` branches. This centralises the jar freshness check for all three code paths.
- [ ] [impl] In the project-command branch (lines 524-553): remove the `local core_files` declaration (line 545) and the `core_files=$(find ... )` block (line 546), along with the preceding `# Find core files excluding test directory` comment (line 544).
- [ ] [impl] In the project-command branch: replace the `exec scala-cli run ...` line (553) with `exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" "$CORE_DIR/project.scala" --jar "$CORE_JAR" -- "$@"`. Keep the `# shellcheck disable=SC2086` directive on the line above; the `cd "$PROJECT_DIR"` at line 551 stays unchanged.
- [ ] [impl] In the plugin-command branch (lines 555-636): remove the `local core_files` declaration (line 608) and the `core_files=$(find ... )` block (line 609), along with the preceding `# Find core files excluding test directory` comment (line 607). Keep the lib-files discovery block (lines 611-615) and the hook-files discovery block (lines 617-629) intact.
- [ ] [impl] In the plugin-command branch: replace the `exec scala-cli run ...` line (636) with `IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $hook_files $lib_files "$CORE_DIR/project.scala" --jar "$CORE_JAR" -- "$@"`. Keep the `# shellcheck disable=SC2086` directive and the `cd "$PROJECT_DIR"` at line 634 unchanged.
- [ ] [impl] In the shared-command branch (lines 638-721): remove the `local core_files` declaration (line 709) and the `core_files=$(find ... )` block (line 710), along with the preceding `# Find core files excluding test directory` comment (line 708). Keep all hook-files discovery (shared, project, plugin) intact.
- [ ] [impl] In the shared-command branch: replace the `exec scala-cli run ...` line (720) with `IW_HOOK_CLASSES="$hook_classes" exec scala-cli run -q --suppress-outdated-dependency-warning "$cmd_file" $hook_files "$CORE_DIR/project.scala" --jar "$CORE_JAR" -- "$@"`. Keep the `# shellcheck disable=SC2086` directive and the `cd "$PROJECT_DIR"` at line 715 unchanged.
- [ ] [impl] Confirm no residual references to `$core_files` remain anywhere in `execute_command()` (Grep for `core_files` inside the function body — only `build_core_jar`'s local use at line 37 should remain).

## Verification

- [ ] [verify] **Fresh jar, no source changes:** with `build/iw-core.jar` present and no core edits since its mtime, run `./iw version`. Expect: command succeeds, no `"Rebuilding core jar..."` on stderr, output matches the baseline captured in Setup.
- [ ] [verify] **Stale jar:** `touch core/model/Settings.scala && ./iw version`. Expect: `"Rebuilding core jar..."` on stderr exactly once, jar rebuilt, command succeeds. Re-run `./iw version` and confirm the rebuild message does not reappear.
- [ ] [verify] **Missing jar:** `rm -f build/iw-core.jar && ./iw version`. Expect: `"Rebuilding core jar..."` on stderr, jar built at the default path, command succeeds.
- [ ] [verify] **Core-heavy shared command:** `./iw status`. Expect: output correct (this command imports core model types and is the cold-start poster-child; failure here signals a classpath visibility regression).
- [ ] [verify] **Project command:** run a project command from `.iw/commands/` (e.g., `./iw ./test unit` — or any other available project command). Expect: success, including resolution of imports from full sub-package paths like `iw.core.adapters.*`.
- [ ] [verify] **Plugin command (optional):** if a plugin is available, invoke `./iw <plugin>/<cmd>` and confirm hooks and lib still load, and `IW_HOOK_CLASSES` is still set in the child process.
- [ ] [verify] **`IW_CORE_JAR` override:** `IW_CORE_JAR=/tmp/test-iw-core.jar ./iw --bootstrap && IW_CORE_JAR=/tmp/test-iw-core.jar ./iw version`. Expect: jar built at `/tmp/test-iw-core.jar`, version command succeeds using the overridden path.
- [ ] [verify] Run shellcheck on the modified `iw-run` (or trust the pre-commit hook to run it) and confirm it passes; the three `# shellcheck disable=SC2086` directives must remain on the `exec scala-cli run` lines because `$hook_files` / `$lib_files` are deliberately word-split.
- [ ] [verify] Run pre-commit hooks on the modified `iw-run` (format + `scala-cli compile --scalac-option -Werror core/` + shellcheck if configured) and confirm they pass. No `.scala` file changes in this phase, so the `-Werror` compile is unaffected.

## Notes

- `ensure_core_jar` placement is deliberate: top of `execute_command`, before the three branches diverge. Alternatives (inside each branch, inside `main()`, inside `check_version_requirement`) were considered and rejected in the context doc — one call, tightest scope, fires exactly once per command.
- `"$CORE_DIR/project.scala"` must be passed as a source file (not just via `--jar`) because scala-cli only honors `//> using dep` directives from source-set files, not from jar-packaged sources. This is the recipe confirmed by the spike (analysis.md lines 85-103).
- Argument order in each `exec` line: `$cmd_file` → branch-specific source files (hooks, lib) → `"$CORE_DIR/project.scala"` → `--jar "$CORE_JAR"` → `-- "$@"`. Hooks/lib come before `project.scala` so their own `//> using dep` directives compose correctly.
- This phase does not touch `build_core_jar()`, `core_jar_stale()`, `ensure_core_jar()`, `bootstrap()`, `commands/*.scala`, `core/*.scala`, BATS tests, or `scripts/package-release.sh`.
- Concurrent invocations racing on jar rebuild are acceptable for Phase 2 (worst case: redundant 1.1s rebuild). No lock file needed; flag for a later hardening pass if observed.
