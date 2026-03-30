# Technical Analysis: Support Plugin Command Directories for Extensible Command Discovery

**Issue:** IW-323
**Created:** 2026-03-30
**Status:** Draft

## Problem Statement

iw-cli currently discovers commands from exactly two directories: shared commands (`$IW_COMMANDS_DIR`) and project-local commands (`$PROJECT_DIR/.iw/commands/`). As AI-specific commands are being extracted from iw-cli core into platform-specific plugins (e.g., kanon), we need a mechanism for plugins to register their own command directories so iw-cli can discover and execute plugin commands without embedding them in core.

Without this, plugins would need to either: (a) copy commands into one of the two existing directories (fragile, breaks on updates), or (b) require users to invoke commands through a separate tool (poor UX, no integration with hooks or `--list`).

## Proposed Solution

### High-Level Approach

Extend the `iw-run` shell script's command resolution to support an ordered list of plugin command directories, in addition to the existing shared and project directories. Plugin directories are discovered from three sources in this priority: environment variable (`IW_PLUGIN_DIRS`), config file entries (`.iw/config.conf`), and XDG auto-discovery (`$XDG_DATA_HOME/iw/plugins/*/commands/`).

The `list_commands()`, `describe_command()`, and `execute_command()` functions in `iw-run` are extended to iterate over plugin directories. Plugin commands are treated as a middle tier: higher priority than shared (core) commands, lower than project commands. A `// REQUIRES: <version>` header convention in command files enables version compatibility checking before execution.

### Why This Approach

The bulk of command discovery and execution lives in `iw-run` (a bash script), so that is where the core changes go. The Scala model layer needs only minor additions (config keys, constants). Keeping the registration mechanism multi-source (env var, config, auto-discovery) gives flexibility: env var for development/CI, config for project-level pinning, XDG for system-level plugin installation.

## Architecture Design

**Purpose:** Define WHAT components each layer needs, not HOW they're implemented.

### Domain Layer (Scala model/)

**Components:**
- `Constants.EnvVars.IwPluginDirs` -- new env var name `IW_PLUGIN_DIRS`
- `Constants.ConfigKeys.PluginDirs` -- new config key `plugins.dirs` (list of paths)
- `Constants.Paths.PluginsDir` -- XDG auto-discovery path `plugins`
- `Constants.CommandHeaders.Requires` -- header field name `REQUIRES`

**Responsibilities:**
- Define the canonical names for all new environment variables, config keys, paths
- Keep the single-source-of-truth for magic strings

**Estimated Effort:** 1-2 hours
**Complexity:** Straightforward

---

### Application Layer (Scala adapters/)

**Components:**
- `ConfigSerializer.fromHocon` -- extend to parse `plugins.dirs` list from config
- `ProjectConfiguration` or a new `PluginConfig` -- carry parsed plugin directory list

**Responsibilities:**
- Deserialize plugin directory configuration from HOCON
- Provide plugin dirs to callers (though the primary consumer is the shell script, which can read config directly or call a Scala helper)

**Estimated Effort:** 2-3 hours
**Complexity:** Moderate -- need to decide whether the shell script reads plugin config itself (simpler) or delegates to Scala (cleaner but adds a compilation step for a config query)

---

### Infrastructure Layer (iw-run shell script)

This is where the majority of the work lives.

**Components:**
- `resolve_plugin_dirs()` -- new function: collects plugin directories from all three sources (env var, config file, XDG auto-discovery), deduplicates, validates each exists
- `check_version_requirement()` -- new function: parses `// REQUIRES:` header from a command file, compares with `iwVersion` (read from `version.scala` or a dedicated version file), produces human-readable error on mismatch
- `list_commands()` -- extend: iterate plugin dirs between shared and project sections, group by plugin name, show source
- `describe_command()` -- extend: resolve command from plugin dirs, indicate source (core/plugin/project)
- `execute_command()` -- extend: resolve command from plugin dirs, run version check, discover hooks from plugin dirs, execute via scala-cli
- `resolve_command()` -- new helper: given a command name, return the file path and source type (shared/plugin/project), implementing the priority order
- Version comparison utility -- semver-aware bash comparison for `// REQUIRES:` checking

**Responsibilities:**
- Plugin directory discovery and validation
- Command resolution across all directory sources with defined priority
- Version compatibility gating
- Hook discovery from plugin directories
- Grouped command listing with source attribution
- Shadow detection (plugin command hiding a core command)

**Estimated Effort:** 10-16 hours
**Complexity:** Complex -- this is the heart of the feature; command resolution, hook discovery, version parsing, and display formatting all need coordinated changes in bash

---

### Presentation Layer (CLI output)

**Components:**
- Grouped `--list` output with plugin sections: `Plugin commands (kanon):` headers
- Source annotation in `--describe` output: `Source: plugin (kanon)` or `Source: core`
- Version mismatch error message format
- Shadow warning format (when plugin command has same name as core command)

**Responsibilities:**
- Human-readable CLI output formatting for plugin-aware command listing
- Clear error messages for version incompatibility

**Estimated Effort:** 2-3 hours (included in iw-run work above, called out separately for clarity)
**Complexity:** Straightforward

---

### Plugin Management Layer (new iw commands)

**Components:**
- `plugin` command (or subcommands: `plugin install`, `plugin list`, `plugin remove`) -- optional, could be deferred
- Alternatively, just document manual registration via config or XDG directory

**Responsibilities:**
- Register/unregister plugin directories in config
- List installed plugins and their commands

**Estimated Effort:** 4-6 hours (if implemented; 0 if deferred)
**Complexity:** Moderate

---

## Technical Decisions

### Patterns

- **Priority chain for command resolution:** project > plugin > shared. This means a project command always wins (existing behavior preserved), plugin commands override shared commands, and shared commands are the fallback.
- **Plugin identification:** derive plugin name from directory structure. For XDG auto-discovery, the plugin name is the directory name under `plugins/`. For env var and config, the plugin name can be derived from the parent directory name.
- **Version comparison:** semantic versioning with major.minor.patch. Only `>=` comparison needed for `REQUIRES`.

### Technology Choices

- **Shell**: All command resolution stays in bash (`iw-run`). No Scala compilation needed for plugin discovery -- this keeps startup fast.
- **Config reading**: For reading `plugins.dirs` from HOCON in bash, use `grep`/`sed` for the simple list case, or invoke a tiny scala-cli snippet. The former is fragile with HOCON; the latter adds latency.
- **Version file**: Extract `iwVersion` into a standalone file (e.g., `.iw/VERSION`) readable by both bash and Scala, rather than parsing `version.scala`.

### Integration Points

- `iw-run` reads plugin dirs from env var, config file, and XDG directory
- `iw-run` reads `// REQUIRES:` header from command `.scala` files
- `iw-run` compares required version against `iwVersion` (needs access to version string)
- Plugin commands compile against `$IW_CORE_DIR` (same as shared commands -- no change)
- Hook files in plugin directories follow same `*.hook-<cmd>.scala` convention

## Technical Risks & Uncertainties

### CLARIFY: Config reading from bash

Reading HOCON config from bash is non-trivial. HOCON supports includes, substitutions, and complex types. A `plugins.dirs` list in HOCON looks like `plugins.dirs = ["/path/a", "/path/b"]`, which is awkward to parse with grep/sed.

**Questions to answer:**
1. Should `iw-run` parse the config file itself (fast but fragile)?
2. Should we add a Scala helper command (e.g., `iw config get plugins.dirs`) that `iw-run` calls?
3. Should we use a separate, simpler config format for plugin registration (e.g., a plain text file `.iw/plugins.conf` with one path per line)?

**Options:**
- **Option A**: Simple grep/sed parsing of `plugins.dirs` from HOCON. Pros: fast, no compilation. Cons: breaks on complex HOCON features (includes, multiline).
- **Option B**: Scala helper command invoked by `iw-run`. Pros: correct HOCON parsing. Cons: adds ~1-2s startup latency for scala-cli compilation on first run.
- **Option C**: Separate plain-text file (`.iw/plugins.conf`, one path per line). Pros: trivial to parse in bash, no HOCON complexity. Cons: yet another config file.

**Impact:** Affects startup latency, config complexity, and correctness of plugin discovery.

---

### CLARIFY: Plugin command shadowing policy

The issue says "Plugin commands should not be able to shadow core commands (or at least warn)." This needs a concrete decision.

**Questions to answer:**
1. Should shadowing be an error (refuse to run), a warning (run with warning), or silent (just use priority order)?
2. Does this apply only to exact name matches, or also to aliases?
3. Should `--list` visually indicate when a plugin command shadows a core command?

**Options:**
- **Option A**: Hard error -- refuse to run a plugin command that shadows a core command. Pros: prevents confusion. Cons: breaks legitimate override use cases.
- **Option B**: Warning on `--list` and first execution, but allow it. Pros: informs user, allows overrides. Cons: warning fatigue.
- **Option C**: Silent, priority-based resolution (core always wins over plugin for non-prefixed invocation). Pros: simple, predictable. Cons: plugin command is effectively unreachable if it has the same name.

**Impact:** Affects command resolution logic and user experience.

---

### CLARIFY: Plugin command invocation syntax

Currently, `./name` means project command. How do users invoke a plugin command explicitly?

**Questions to answer:**
1. Do plugin commands use bare names (same as core), relying on priority?
2. Should there be a prefix syntax like `kanon:batch-implement`?
3. Or do plugin commands just work by bare name, with plugins expected to use unique names?

**Options:**
- **Option A**: Bare names only, priority-based. Plugin commands are found before core but after project. Pros: simple UX. Cons: no way to explicitly target a plugin.
- **Option B**: Optional `plugin:name` prefix syntax. Pros: explicit disambiguation. Cons: more complex parsing, new syntax to learn.
- **Option C**: Bare names, but `--list` shows the source so users know what they are getting. Pros: simple invocation, informed users. Cons: ambiguity when multiple plugins provide same command name.

**Impact:** Affects command resolution, parsing logic, and documentation.

---

### CLARIFY: Hook discovery for plugin commands

The issue says "Hooks should work for plugin commands too." Currently, hooks are only discovered for shared commands (not project commands). Need to define hook behavior for plugin commands.

**Questions to answer:**
1. Should plugin commands discover hooks from: (a) only their own plugin directory, (b) their dir + project dir, (c) all plugin dirs + project dir?
2. Should shared command hooks also be discovered from plugin directories?

**Options:**
- **Option A**: Plugin commands discover hooks from their own plugin dir + project dir. Shared commands additionally discover hooks from all plugin dirs. Pros: consistent, extensible. Cons: more complex hook resolution.
- **Option B**: Plugin commands discover hooks only from their own directory. Pros: simple, isolated. Cons: project can't hook into plugin commands.
- **Option C**: Same as current -- hooks only for shared commands, plugin commands have no hook support initially. Pros: minimal change. Cons: limits extensibility.

**Impact:** Affects hook discovery logic in `execute_command()`.

---

### CLARIFY: Scope of `plugin install` command

**Questions to answer:**
1. Is a `plugin install` command needed for v1, or is manual registration (config + XDG) sufficient?
2. If needed, what does install do -- just register a path, or also download/clone?

**Options:**
- **Option A**: Defer `plugin install` command. Manual registration only for v1. Pros: smaller scope. Cons: higher friction for users.
- **Option B**: Simple `plugin install <path>` that writes to config. Pros: better UX. Cons: more work.
- **Option C**: Full install with download support. Pros: complete solution. Cons: significant scope increase.

**Impact:** Affects total effort by 4-6 hours.

---

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer (Constants/model): 1-2 hours
- Application Layer (Config parsing): 2-3 hours
- Infrastructure Layer (iw-run script): 10-16 hours
- Presentation Layer (CLI output): 2-3 hours (partially overlaps with iw-run)
- Plugin management command: 0-6 hours (depends on CLARIFY: scope)
- Testing: 6-10 hours

**Total Range:** 21 - 40 hours

**Confidence:** Medium

**Reasoning:**
- The iw-run shell script changes are the riskiest and largest portion
- HOCON config parsing from bash is an unresolved design question that could shift effort
- Several CLARIFY items could significantly change scope (especially plugin install command and shadowing policy)
- Shell script testing (BATS) is straightforward but time-consuming to set up per scenario
- Version comparison in bash is fiddly but well-understood

## Testing Strategy

### Per-Layer Testing

**Domain Layer:**
- Unit: Verify new constants exist and have expected values (trivial, covered by compilation)

**Application Layer:**
- Unit: Test `ConfigSerializer.fromHocon` with `plugins.dirs` field present and absent
- Unit: Test round-trip serialization of config with plugin dirs

**Infrastructure Layer (iw-run -- BATS E2E):**
- `plugin-dirs-env.bats`: Test `IW_PLUGIN_DIRS` env var discovery
  - Single plugin dir
  - Multiple colon-separated dirs
  - Non-existent dir is skipped with warning
  - Empty env var is no-op
- `plugin-dirs-config.bats`: Test config-based discovery
  - `plugins.dirs` in config.conf
  - Missing key is no-op
- `plugin-dirs-xdg.bats`: Test auto-discovery
  - Commands found in `$XDG_DATA_HOME/iw/plugins/*/commands/`
  - Empty plugins dir is no-op
- `plugin-commands-list.bats`: Test `--list` with plugin commands
  - Plugin commands shown in separate section
  - Plugin name derived from directory
  - Hook files excluded from listing
  - Multiple plugins shown in separate sections
- `plugin-commands-execute.bats`: Test plugin command execution
  - Plugin command found and executed
  - Plugin command receives arguments
  - Plugin command compiles against core
  - Priority: project > plugin > shared
- `plugin-commands-describe.bats`: Test `--describe` with source info
  - Source shown as `core`, `plugin (name)`, or `project`
- `plugin-version-check.bats`: Test `// REQUIRES:` header
  - Command with satisfied version requirement runs
  - Command with unsatisfied version requirement shows error
  - Command without REQUIRES header runs (no check)
  - Malformed REQUIRES header is ignored or warned
- `plugin-hooks.bats`: Test hook discovery from plugin dirs
  - Hook in plugin dir discovered for shared command
  - Hook in plugin dir discovered for plugin command (if decided)
- `plugin-shadow.bats`: Test shadowing behavior
  - Warning/error when plugin command shadows core command (per CLARIFY decision)

**Test Data Strategy:**
- Each BATS test creates temp directories with mock plugin structures
- Mock plugin commands are minimal `.scala` files with `@main` and `println`
- Tests use `IW_SERVER_DISABLED=1` per project convention
- Follow existing test patterns from `project-commands-*.bats`

**Regression Coverage:**
- Existing `project-commands-list.bats` and `project-commands-execute.bats` must continue passing
- All existing E2E tests must pass (plugin feature is additive)
- Specific regression: ensure `./name` still routes to project commands, not plugins

## Deployment Considerations

### Database Changes
None.

### Configuration Changes
- New optional config key: `plugins.dirs` (list of strings) in `.iw/config.conf`
- New env var: `IW_PLUGIN_DIRS` (colon-separated paths)
- New XDG convention: `$XDG_DATA_HOME/iw/plugins/*/commands/`
- New command header: `// REQUIRES: <version>`

### Rollout Strategy
- Feature is entirely additive -- no existing behavior changes
- If no plugin dirs are configured, behavior is identical to current
- Can be released as a minor version bump (0.4.0)

### Rollback Plan
- Revert `iw-run` changes; config entries and env vars are simply ignored
- No data migration to undo

## Dependencies

### Prerequisites
- Decision on CLARIFY items (especially config parsing approach and shadowing policy)
- Agreement on version string location (standalone file vs parsing version.scala)

### Layer Dependencies
- Domain layer (Constants) must be done first -- other layers reference these constants
- iw-run changes are independent of Scala application layer changes
- Testing can begin as soon as iw-run changes are in place

### External Blockers
- None -- this is internal infrastructure

## Risks & Mitigations

### Risk 1: HOCON parsing from bash is fragile
**Likelihood:** High
**Impact:** Medium
**Mitigation:** Use a simple, dedicated file format for plugin registration (Option C in CLARIFY), or accept the latency of a Scala helper.

### Risk 2: Version comparison edge cases in bash
**Likelihood:** Medium
**Impact:** Low
**Mitigation:** Use a well-tested semver comparison function. Only support `major.minor.patch` format (no pre-release tags initially).

### Risk 3: scala-cli compilation time with many plugin dirs
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Plugin commands compile individually (same as current), so adding plugin dirs doesn't change compilation scope for any single command execution.

### Risk 4: Name collisions across multiple plugins
**Likelihood:** Medium
**Impact:** Medium
**Mitigation:** Define clear priority order and surface conflicts in `--list`. Consider requiring plugin-namespaced command names by convention (e.g., `kanon-batch-implement`).

---

## Implementation Sequence

**Recommended Layer Order:**

1. **Domain Layer** (Constants) -- Pure additions, no dependencies, foundation for naming consistency
2. **Infrastructure Layer** (iw-run) -- Core feature implementation; can reference constant names by convention even before Scala constants compile
3. **Application Layer** (Config parsing) -- Only needed if we choose the Scala-helper approach for config reading
4. **Presentation Layer** -- Integrated into iw-run changes (list/describe formatting)
5. **Testing** -- BATS tests alongside or after iw-run changes

**Ordering Rationale:**
- iw-run is the primary deliverable and can be developed/tested independently with BATS
- Scala model changes are small and can be done in parallel with iw-run
- Application layer may not be needed at all (depends on CLARIFY: config reading approach)
- The `plugin install` command (if in scope) comes last as it depends on everything else

**Opportunities for parallel implementation:**
- Domain layer + iw-run script changes can proceed in parallel
- BATS test files can be written as each iw-run function is completed

## Documentation Requirements

- [ ] Code documentation (inline comments in iw-run for new functions)
- [ ] Update `iw --help` output to mention plugins
- [ ] Document plugin directory conventions (README or docs/)
- [ ] Document `// REQUIRES:` header convention for command authors
- [ ] Document `IW_PLUGIN_DIRS` env var
- [ ] Document `.iw/config.conf` `plugins.dirs` key
- [ ] Document XDG auto-discovery convention

---

**Analysis Status:** Ready for Review

**Next Steps:**
1. Resolve CLARIFY markers with stakeholders (especially config parsing approach, shadowing policy, and invocation syntax)
2. Decide whether `plugin install` command is in scope for v1
3. Run **wf-create-tasks** with the issue ID
4. Run **wf-implement** for layer-by-layer implementation
