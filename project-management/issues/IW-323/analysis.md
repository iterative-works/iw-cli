# Technical Analysis: Support Plugin Command Directories for Extensible Command Discovery

**Issue:** IW-323
**Created:** 2026-03-30
**Status:** Ready for Implementation

## Problem Statement

iw-cli currently discovers commands from exactly two directories: shared commands (`$IW_COMMANDS_DIR`) and project-local commands (`$PROJECT_DIR/.iw/commands/`). As AI-specific commands are being extracted from iw-cli core into platform-specific plugins (e.g., kanon), we need a mechanism for plugins to register their own command directories so iw-cli can discover and execute plugin commands without embedding them in core.

Without this, plugins would need to either: (a) copy commands into one of the two existing directories (fragile, breaks on updates), or (b) require users to invoke commands through a separate tool (poor UX, no integration with hooks or `--list`).

## Proposed Solution

### High-Level Approach

Extend the `iw-run` shell script's command resolution to support plugin command directories discovered via XDG auto-discovery (`$XDG_DATA_HOME/iw/plugins/*/`). Plugin commands are invoked with an explicit `<plugin>/<command>` prefix (e.g., `iw kanon/implement`), keeping them in a separate namespace — consistent with how project commands use the `./` prefix.

Each plugin directory contains:
- `commands/` — command `.scala` files (discovered by iw-run)
- `lib/` — support `.scala` files (compiled alongside that plugin's commands)
- `hooks/` — hook files that extend core commands (e.g., doctor hooks)

Registration is via symlink: `ln -s /path/to/plugin ~/.local/share/iw/plugins/<name>`

### Why This Approach

- **Global, not per-project**: Plugins like kanon are installed system-wide, not per-project. XDG auto-discovery matches this.
- **No config parsing**: Avoids the complexity of reading HOCON lists from bash. Directory scanning is simple and fast.
- **Explicit namespacing**: `kanon/implement` is unambiguous, consistent with `./name` for project commands. No shadowing concerns.
- **Self-contained plugins**: Each plugin's `lib/` is only compiled with its own commands, keeping plugins isolated.

## Architecture Design

### Plugin Directory Structure

```
$XDG_DATA_HOME/iw/plugins/
└── kanon/                    # plugin name (or symlink to plugin source)
    ├── commands/             # command .scala files
    │   ├── implement.scala
    │   └── batch-implement.scala
    ├── lib/                  # support .scala files (compiled with commands)
    │   └── ClaudeCodeAdapter.scala
    └── hooks/                # hooks that extend core commands
        └── claude.hook-doctor.scala
```

### Command Namespaces

| Prefix | Source | Example |
|--------|--------|---------|
| (none) | Core shared commands | `iw start` |
| `<plugin>/` | Plugin commands | `iw kanon/implement` |
| `./` | Project-local commands | `iw ./test` |

### Domain Layer (Scala model/)

**Components:**
- `Constants.EnvVars.IwPluginDirs` -- env var name `IW_PLUGIN_DIRS` (for dev/testing override)
- `Constants.Paths.PluginsDir` -- XDG auto-discovery path segment `plugins`
- `Constants.CommandHeaders.Requires` -- header field name `REQUIRES`

**Responsibilities:**
- Define canonical names for new environment variables, paths, and header fields

**Estimated Effort:** 1 hour
**Complexity:** Straightforward

---

### Infrastructure Layer (iw-run shell script)

This is where the majority of the work lives.

**Components:**
- `discover_plugins()` -- new function: scans `$XDG_DATA_HOME/iw/plugins/*/` (and `$IW_PLUGIN_DIRS` if set) for plugin directories, validates each has a `commands/` subdirectory
- `check_version_requirement()` -- new function: parses `// REQUIRES:` header from a command file, compares with iw-cli version, produces human-readable error on mismatch
- `list_commands()` -- extend: show plugin commands grouped by plugin name between shared and project sections
- `describe_command()` -- extend: resolve `<plugin>/<command>` syntax, indicate source
- `execute_command()` -- extend: resolve `<plugin>/<command>` syntax, include plugin's `lib/*.scala` + `$IW_CORE_DIR` files on classpath, run version check
- Version comparison utility -- semver-aware bash comparison for `// REQUIRES:` checking

**Responsibilities:**
- Plugin directory discovery and validation
- `<plugin>/<command>` parsing and routing
- Plugin `lib/` inclusion on classpath
- Hook discovery from plugin `hooks/` directories for core commands
- Project-level hook discovery for plugin commands
- Version compatibility gating
- Grouped command listing with source attribution

**Estimated Effort:** 8-12 hours
**Complexity:** Moderate-to-complex -- the main work is extending existing bash functions with a new namespace, but the patterns are already established by project command support

---

### Presentation Layer (CLI output)

Integrated into `iw-run` changes.

**Components:**
- Grouped `--list` output: `Plugin commands (kanon):` section with `kanon/<command>` entries
- Source annotation in `--describe` output
- Version mismatch error message format

**Estimated Effort:** 1-2 hours (included in iw-run estimate)
**Complexity:** Straightforward

---

## Technical Decisions

### Resolved Design Questions

**1. Plugin registration mechanism:**
Global XDG auto-discovery only. No per-project config, no HOCON parsing from bash. Symlinks for registration. `IW_PLUGIN_DIRS` env var as secondary mechanism for dev/testing.

**2. Shadowing policy:**
Not applicable. Plugin commands live in a separate namespace (`kanon/implement`), so they cannot shadow core commands. Same approach as project commands (`./name`).

**3. Invocation syntax:**
`<plugin>/<command>` prefix. Explicit, unambiguous, consistent with `./` for project commands.

**4. Hook discovery:**
Two directions:
- **Plugin hooks into core commands**: Plugin `hooks/` directory is scanned when running core commands (alongside shared and project hook dirs).
- **Project hooks into plugin commands**: Project `.iw/commands/` directory is scanned for hooks when running plugin commands.
Plugin-to-plugin hook discovery is not supported.

**5. Plugin install command:**
Deferred. Registration is a symlink. Plugin projects can ship their own install scripts.

**6. Plugin lib isolation:**
Each plugin's `lib/*.scala` files are only compiled with that plugin's commands. No cross-plugin lib sharing.

### Technology Choices

- **Shell**: All command resolution stays in bash (`iw-run`). No Scala compilation needed for plugin discovery.
- **Version file**: Extract `iwVersion` into a standalone file (e.g., `.iw/VERSION`) readable by both bash and Scala, rather than parsing `version.scala`.
- **XDG default**: `$XDG_DATA_HOME` defaults to `$HOME/.local/share` per spec.

### Integration Points

- `iw-run` scans `$XDG_DATA_HOME/iw/plugins/*/commands/` for plugin commands
- `iw-run` scans `$XDG_DATA_HOME/iw/plugins/*/hooks/` for core command hooks
- `iw-run` includes `$XDG_DATA_HOME/iw/plugins/<name>/lib/*.scala` when compiling plugin commands
- `iw-run` reads `// REQUIRES:` header from command `.scala` files
- Plugin commands compile against `$IW_CORE_DIR` + plugin's own `lib/` (no cross-plugin deps)

## Total Estimates

**Per-Layer Breakdown:**
- Domain Layer (Constants): 1 hour
- Infrastructure Layer (iw-run script): 8-12 hours
- Testing (BATS E2E): 5-8 hours

**Total Range:** 14 - 21 hours

**Confidence:** Medium-High

**Reasoning:**
- CLARIFYs are all resolved, reducing uncertainty significantly
- No HOCON parsing, no config model changes, no shadowing logic -- scope is tighter than initial estimate
- The `<plugin>/<command>` parsing mirrors existing `./name` project command logic
- Shell script testing (BATS) follows established patterns from `project-commands-*.bats`

## Testing Strategy

### Infrastructure Layer (iw-run -- BATS E2E)

- `plugin-discovery.bats`: Test plugin directory scanning
  - Plugin found via XDG auto-discovery
  - Plugin found via `IW_PLUGIN_DIRS` env var
  - Non-existent plugin dir is skipped
  - Plugin without `commands/` subdirectory is skipped
  - Empty plugins directory is no-op

- `plugin-commands-list.bats`: Test `--list` with plugin commands
  - Plugin commands shown in separate section with `kanon/<command>` format
  - Plugin name derived from directory name
  - Hook files excluded from listing
  - Multiple plugins shown in separate sections
  - `lib/` files excluded from listing

- `plugin-commands-execute.bats`: Test plugin command execution
  - `iw kanon/implement` finds and runs the command
  - Plugin command receives arguments
  - Plugin command compiles against core + plugin lib
  - Plugin lib files are on classpath
  - Unknown plugin name gives clear error
  - Unknown command within plugin gives clear error

- `plugin-commands-describe.bats`: Test `--describe` with `kanon/<command>`
  - Shows purpose, usage, source info

- `plugin-version-check.bats`: Test `// REQUIRES:` header
  - Command with satisfied version requirement runs
  - Command with unsatisfied version requirement shows error with upgrade hint
  - Command without REQUIRES header runs normally
  - Malformed REQUIRES header is ignored with warning

- `plugin-hooks.bats`: Test hook discovery
  - Hook in plugin `hooks/` dir discovered when running core command
  - Project hook discovered when running plugin command

**Test Data Strategy:**
- Each BATS test creates temp directories with mock plugin structures
- Mock plugin commands are minimal `.scala` files with `@main` and `println`
- Tests use `IW_SERVER_DISABLED=1` per project convention
- Follow existing patterns from `project-commands-*.bats`

**Regression Coverage:**
- All existing E2E tests must continue passing
- `./name` still routes to project commands, not plugins
- Core commands without plugins work identically to current behavior

## Risks & Mitigations

### Risk 1: Version comparison edge cases in bash
**Likelihood:** Medium
**Impact:** Low
**Mitigation:** Only support `major.minor.patch` format (no pre-release tags). Use a well-tested semver comparison function.

### Risk 2: scala-cli compilation with plugin lib files
**Likelihood:** Low
**Impact:** Medium
**Mitigation:** Plugin lib files are just additional `.scala` sources passed to scala-cli, same as core files. Already proven pattern.

### Risk 3: Symlink resolution across platforms
**Likelihood:** Low
**Impact:** Low
**Mitigation:** `find` and bash follow symlinks by default. Test on Linux (primary platform).

## Implementation Sequence

**Recommended Order:**

1. **Domain Layer** (Constants) -- Add new constant names
2. **Infrastructure Layer** (iw-run) -- Plugin discovery, namespace parsing, execution
   - Start with `discover_plugins()` and `list_commands()` extension
   - Add `execute_command()` extension with `<plugin>/<command>` parsing
   - Add `lib/` classpath inclusion
   - Add `hooks/` discovery for core commands
   - Add project hooks for plugin commands
   - Add `// REQUIRES:` version checking
   - Add `describe_command()` extension
3. **Testing** -- BATS tests alongside iw-run changes

**Rationale:**
- iw-run is the primary deliverable and the Scala changes are minimal
- BATS tests can be written incrementally as each function is extended
- Version checking can be done last as it's independent of the core plugin mechanism

## Documentation Requirements

- [ ] Code documentation (inline comments in iw-run for new functions)
- [ ] Update `iw --help` output to mention plugins
- [ ] Document plugin directory structure conventions (README or docs/)
- [ ] Document `// REQUIRES:` header convention for plugin command authors
- [ ] Document `IW_PLUGIN_DIRS` env var for development
- [ ] Document XDG auto-discovery path

---

**Analysis Status:** Ready for Implementation

**Next Steps:**
1. Run **wf-create-tasks** to generate phase-based task breakdown
2. Run **wf-implement** for implementation
