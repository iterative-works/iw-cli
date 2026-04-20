---
name: build-iw-cli-skills
description: |
  Build the published iw-cli-ops and iw-command-creation skills (plus llms.txt and docs/api/) from current iw-cli source, then stage them for publishing to the dev-docs plugin.

  Use when:
  - User asks to "build", "rebuild", "regenerate", "update", or "publish" the iw-cli skills
  - User mentions that iw-cli-ops is stale or referencing removed/missing commands
  - After adding, removing, or significantly changing commands in `commands/*.scala`
  - After adding, removing, or changing public API of modules in `core/`
  - User mentions the skills in dev-docs are out of date with iw-cli
---

# Build iw-cli Skills

This skill generates the canonical, project-agnostic versions of the two published
iw-cli skills plus the `llms.txt` core module index and `docs/api/*.md` per-module
documentation. Output is staged in `build/published-skills/` for the user to copy
to the dev-docs plugin.

## What Gets Generated

| Artifact | Source | Purpose |
|----------|--------|---------|
| `iw-cli-ops/SKILL.md` | `commands/*.scala` (excluding `*.hook-*.scala`) | Command reference and workflow guide for agents |
| `iw-command-creation/SKILL.md` | `core/` module structure + `llms.txt` | Guide for writing ad-hoc scripts and project commands |
| `llms.txt` | `core/model/`, `core/adapters/`, `core/output/` | Top-level index of all public core modules |
| `docs/api/<Module>.md` | Individual source files in `core/` | Per-module API reference with signatures and examples |

## Where Output Goes

Stage everything under `build/published-skills/`:

```
build/published-skills/
├── iw-cli-ops/SKILL.md
├── iw-command-creation/SKILL.md
├── llms.txt
└── docs/api/*.md
```

Do NOT write directly to dev-docs. The user copies the staged output to
`~/ops/kanon/.claude/skills/` (or wherever the plugin lives) and commits from there.

## Determining Scope: Incremental vs Full

The skill supports two modes:

### Full rebuild (default for first run or when requested)
Regenerate everything. Use when:
- User explicitly asks for a full rebuild
- `build/published-skills/.last-built` does not exist
- Core module set has changed (files added or removed in `core/`)

### Incremental update
Only regenerate pieces whose sources have changed since the last build. Use when:
- `build/published-skills/.last-built` exists and contains a valid SHA
- User has not asked for a full rebuild

**How to compute the changed set:**

1. Read the SHA from `build/published-skills/.last-built` (one line, a git commit SHA).
2. Run `git diff --name-only <sha>..HEAD -- commands/ core/ llms.txt docs/api/` to find touched files.
3. Apply these rules:
   - Any `commands/*.scala` (excluding `*.hook-*.scala`) changed → regenerate `iw-cli-ops/SKILL.md`
   - Any file in `core/model/`, `core/adapters/`, or `core/output/` changed → regenerate the corresponding `docs/api/<Module>.md`
   - Core module set changed (files added/removed in those directories) → regenerate `llms.txt` AND the module table in `iw-command-creation/SKILL.md`
   - Any changes touching `commands/` or `core/` → regenerate `iw-command-creation/SKILL.md` only if core module set or llms.txt structure changed

4. After generating, update `build/published-skills/.last-built` with the current HEAD SHA (`git rev-parse HEAD`).

**Always tell the user what scope you chose** at the start ("Full rebuild — no prior build marker" or "Incremental — N files changed since <sha>: ...").

## Generation Rules

### Project-Agnostic Content (CRITICAL)

The old skills failed because they had project-specific context baked in (repo name,
team prefix, tracker type). The generated skills MUST work identically in any iw-cli
project. Follow these rules:

- **Never hardcode** a tracker type, repository name, team prefix, or issue ID format
- **Always instruct the agent to read `.iw/config.conf`** at runtime for project-specific details
- **Use generic placeholders** in examples (`<issue-id>`, `<N>`, `TEAM-123`)
- **Mention all supported trackers** when discussing authentication/env vars (GitHub, GitLab, Linear, YouTrack)
- The `iw` script is **always** invoked as `./iw` (project-local, not on PATH)

### iw-cli-ops/SKILL.md

1. **Discover commands:** List `commands/*.scala`, excluding any file matching `*.hook-*.scala` (those are internal hook implementations, not user commands).
2. **Extract purpose:** For each command, read the file and find the `// PURPOSE:` comment lines or the `showUsage` block. The first PURPOSE line is the command's summary.
3. **Read with `--describe` if unclear:** For commands where the purpose line is ambiguous, you can run `./iw --describe <command>` to get the full help text, but only if needed — prefer source inspection.
4. **Group commands** into these categories (adjust if new categories emerge):
   - Issue Information (`issue`)
   - Worktree Lifecycle (`start`, `open`, `rm`, `worktrees`, `status`, `register`)
   - Phase Workflow (`phase-start`, `phase-commit`, `phase-pr`, `phase-merge`, `phase-advance`)
   - Implementation (`analyze`)
   - Project Setup (`init`, `doctor`, `config`, `project-context`)
   - Dashboard & Server (`dashboard`, `server`)
   - Review State (`review-state`)
   - Utilities (`version`, `feedback`, `projects`)
5. **Frontmatter description:** Problem-centric, lead with what problems agents solve by invoking the skill. List "Use when" triggers using natural phrases users actually say. Never list commands in the description.
6. **Required sections:** Intro, Project Configuration (points at `config.conf`), Commands (by category), Discovering Commands (`./iw`, `./iw --describe`), Common Workflows, Troubleshooting, Environment Variables.

### iw-command-creation/SKILL.md

1. **Enumerate core modules:** Scan `core/model/*.scala`, `core/adapters/*.scala`, `core/output/*.scala`. Exclude `project.scala`, `CLAUDE.md`, `test/`, `dashboard/`, files in subdirectories, and the `IssueCreateParser.scala` root file (which is an internal helper).
2. **Module table:** One row per module with name, brief purpose (from PURPOSE comment), and link to `docs/<Module>.md`. Sort within each group (Model / Adapters / Output).
3. **Reference llms.txt:** The skill must tell agents to read `$IW_CORE_DIR/../llms.txt` first, not re-list all APIs inline.
4. **Keep the examples stable** — the existing examples (simple command, command with config) are good and should be preserved unless the API they depend on has changed.
5. **Script invocation:** Both `./iw ./script-name` (for commands in `.iw/commands/`) and `scala-cli run script.scala $IW_CORE_DIR/*.scala` (for ad-hoc scripts).

### llms.txt

Follow the [llmstxt.org](https://llmstxt.org) format:

```markdown
# iw-cli Core Modules

> Functional Scala modules for CLI automation and issue tracking. Organized as FCIS (Functional Core, Imperative Shell) with pure domain types, I/O adapters, and output formatting.

## Quick Start

[Import examples]

## Model (Pure Types)

Pure domain types with no I/O. Safe to use anywhere.

- [ModuleName](docs/ModuleName.md): One-line description from PURPOSE comment

## Adapters (I/O Operations)

[...same format...]

## Output (CLI Formatting)

[...same format...]

## Architecture Notes

Dependencies flow inward: `adapters/` imports `model/`, `output/` imports `model/`.

Scripts in `.iw/commands/` should NOT import from `dashboard/` (internal API).
```

The link paths are `docs/<Module>.md` (relative to llms.txt). When staged under
`build/published-skills/`, the actual file lives at `build/published-skills/docs/api/<Module>.md`
but the link in llms.txt should remain `docs/<Module>.md` to match the existing layout.

### docs/api/<Module>.md

For each public module, generate a doc with this structure:

```markdown
# ModuleName

[One-paragraph description from PURPOSE comment]

## Import

\`\`\`scala
import iw.core.<subpackage>.<ModuleName>
\`\`\`

## API

[Public functions with signatures and param/return types. Extract from source.]

## Examples

[1-2 usage snippets, ideally lifted from real command files that import this module]
```

Extract examples by grepping `commands/*.scala` and `core/` for imports of the module
and picking a short, representative usage.

## Execution Steps

Run these in order:

1. **Determine scope.** Check `build/published-skills/.last-built`. If missing or user requested full rebuild, do a full rebuild. Otherwise compute the changed file set and announce the scope.

2. **Read sources.** For each artifact to regenerate, read the relevant source files. Use `Glob` and `Read` tools.

3. **Generate artifacts.** Write each artifact to its location under `build/published-skills/`. Create the directory if needed.

4. **Update the marker.** Write `git rev-parse HEAD` output to `build/published-skills/.last-built`.

5. **Report.** Tell the user:
   - Which artifacts were regenerated
   - Where they are staged
   - The exact `cp` commands to copy them into the dev-docs plugin at `~/ops/kanon/.claude/skills/` (and wherever llms.txt + docs/api/ are published to)
   - That the user still needs to commit from the dev-docs repo

## Anti-Patterns to Avoid

- **Do not** run the skill generation by invoking Claude recursively. Generate content directly.
- **Do not** write to `.claude/skills/` in this repo — that's where *this* skill lives, not where the built artifacts go.
- **Do not** write to `~/ops/kanon/` directly. Always stage under `build/published-skills/` first.
- **Do not** include project-specific hardcoded values in generated skills.
- **Do not** list `claude-sync` as a command — it was removed in PR #320.
- **Do not** invent commands or modules that don't exist in the current source. Only document what's actually there.
- **Do not** skip the `.last-built` marker update — it breaks incremental mode.

## Quick Reference: Current Known State

- `commands/` uses files matching `*.hook-*.scala` for internal hook implementations — always filter these out
- `core/` has three public subpackages: `model/`, `adapters/`, `output/`
- `core/dashboard/` is internal and must NOT be referenced in generated skills
- `core/IssueCreateParser.scala` is an internal helper (not in a subpackage) — exclude from module lists
- `llms.txt` currently lives at repo root; `docs/api/*.md` lives under `docs/api/`
- When staging, use `build/published-skills/docs/api/*.md` to match that layout

## When Done

Print a summary like:

```
Built published iw-cli skills (<full|incremental>):
  ✓ iw-cli-ops/SKILL.md (<reason>)
  ✓ iw-command-creation/SKILL.md (<reason>)
  ✓ llms.txt (<reason>)
  ✓ docs/api/<N> modules (<reason>)

Staged at: build/published-skills/

To publish:
  cp -r build/published-skills/iw-cli-ops ~/ops/kanon/.claude/skills/
  cp -r build/published-skills/iw-command-creation ~/ops/kanon/.claude/skills/
  # plus llms.txt and docs/api/ to their plugin locations
  cd ~/ops/kanon && git add ... && git commit

Marker updated: build/published-skills/.last-built -> <sha>
```
