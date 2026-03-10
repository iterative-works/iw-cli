# Analysis: Extract deterministic decisions from implementation-workflow pipeline

**Issue:** IW-248
**Source:** DEVDOCS-106 pipeline review
**Created:** 2026-03-10

## Problem Statement

The implementation-workflow pipeline (in dev-docs `blueprints/snippets/implementation-workflow-pipeline.md`) asks the LLM agent to make ~14 decisions that are fully deterministic. These include file existence checks, markdown parsing, git queries, integer comparisons, and pattern matching. LLMs are unreliable at these tasks — they miscount checkboxes, lose track of iteration counters across long conversations, and inconsistently apply mapping rules.

Each decision point below is a candidate for a new or extended `iw` CLI command.

## Decision Points by Priority

Priority is based on error likelihood and impact when the LLM gets it wrong.

### P1: High Impact (LLM errors observed or highly likely)

#### D1: Parse review severity counts (Step 3.3)

**Current:** The agent reads its own code review markdown output and extracts counts of critical issues, warnings, and suggestions from `## Summary`. Then branches on `critical > 0`.

**Problem:** LLM parsing structured text to extract numbers is fragile. Hallucinated counts lead to either skipping critical issues or unnecessary fix iterations.

**Proposed:** `iw review-parse --file <path>` → outputs JSON:
```json
{"critical": 2, "warnings": 1, "suggestions": 3}
```
Exit code 0 if no critical issues, 1 if critical issues found.

---

#### D2: Select applicable review skills (Step 3.2)

**Current:** Given a list of changed files, the agent applies rules to determine which code-review skills to activate:
- `*.scala` → `code-review-scala3`, `code-review-composition`
- Path contains `domain/` or `application/` → `code-review-architecture`
- Contains ZIO imports → `code-review-zio`
- Path contains `repository/` → `code-review-repository`
- Path contains `api/` or `routes/` → `code-review-api`
- Always: `code-review-style`, `code-review-testing`, `code-review-security`

**Problem:** The agent may inconsistently apply these rules across invocations, miss edge cases, or apply skills to wrong files.

**Proposed:** `iw review-skills --files <file1> <file2> ...` → outputs skill list:
```
code-review-style
code-review-testing
code-review-security
code-review-scala3
code-review-architecture
```
Rules encoded in a config file (glob pattern → skill name), so projects can customize.

---

#### D3: Verify all [impl] checkboxes checked (Step 2.4)

**Current:** The agent re-reads the task file and checks whether every `[impl]` checkbox is `[x]`.

**Problem:** LLM miscounting checkboxes can either falsely proceed (missed unchecked task) or falsely block.

**Proposed:** `iw check-tasks --file <path> --check impl` → exits 0 if all checked, 1 with list of unchecked tasks on stderr.

---

#### D4: Maintain iteration counter across conversation turns (Step 3.3)

**Current:** The agent initializes `iteration_count = 1`, increments it after each review-fix cycle, and compares against `max_iterations = 3`.

**Problem:** LLMs maintaining counters across long multi-turn interactions is notoriously unreliable. The counter can reset, skip values, or be hallucinated.

**Proposed:** Store iteration count in `review-state.json` (new field `review_iterations`). Commands:
- `iw review-state increment-iteration --input <path>` → increments and returns current count
- `iw review-state check-iteration --input <path> --max 3` → exits 0 if under max, 1 if at/over max

---

### P2: Medium Impact (straightforward to extract)

#### D5: Mark phase complete in tasks.md (Step 6)

**Current:** The agent checks if `tasks.md` has `- [ ] Phase N: [Title]` and replaces with `- [x] Phase N: [Title]`.

**Problem:** Simple find-replace, but the agent occasionally edits the wrong line or introduces formatting inconsistencies.

**Proposed:** Fold into `phase-commit` — it already updates the phase task file, so it could also update the phase index. Or: `iw phase-mark-complete --phase N --tasks-file <path>`.

---

#### D6: Detect project type / agent_type fallback (Step 2.2)

**Current:** If `agent_type` not provided, the agent checks for `build.sbt` or `*.scala` files to decide between workflow-specific implementer and `general-purpose`.

**Problem:** Low risk (agent_type is usually provided), but when fallback fires, detection may be unreliable.

**Proposed:** `iw detect-project-type` → outputs `scala` | `typescript` | `unknown`. Or better: the calling command resolves `agent_type` before invoking the skill, so the fallback never fires.

---

#### D7: Mark refactoring task + status complete (Step 5.1, refactoring only)

**Current:** Find-and-replace in two files: check `[impl]` and `[reviewed]` boxes in phase-tasks.md, and change `**Status:** Planned` to `**Status:** Complete` in the refactoring file.

**Proposed:** `iw mark-refactoring-complete --phase-tasks <path> --refactoring-file <path> --refactoring-id R1`

---

#### D8: Branch on batch_mode for PR command (Step 5.5)

**Current:** `if batch_mode then phase-pr --batch else phase-pr`.

**Problem:** Trivial boolean branch, but could be eliminated entirely.

**Proposed:** `phase-pr` reads batch_mode from review-state.json (new field), so the caller just runs `iw phase-pr --input <path>` unconditionally.

---

### P3: Low Impact (nice to have)

#### D9: Count test files in diff (Step 4.2)

**Current:** `git diff [baseline] --stat | grep -i test` to count test files changed.

**Proposed:** `iw phase-stats --baseline <sha>` → JSON with `files_added`, `files_modified`, `test_files_changed`, etc.

---

#### D10: Check if implementation log exists (Step 4.1)

**Current:** If `implementation-log.md` doesn't exist, create it from a template.

**Proposed:** `iw ensure-file --template implementation-log --issue-id <id>`. Or fold into `phase-start`.

---

#### D11: Detect changed files / empty check (Step 3.1)

**Current:** `git diff [baseline] --name-only | grep -v tracking-files`, then check if empty.

**Proposed:** `iw phase-changed-files --baseline <sha>` → file list, exit code 1 if empty.

---

#### D12: Verify output files exist (Steps 2.5, 3.2)

**Current:** `test -f <path>` after subagent completes, then LLM interprets the result.

**Proposed:** Subagents should return structured results (exit code + JSON) rather than requiring post-hoc file existence checks.

---

#### D13: Branch on agent_type for prompt template (Step 2.3)

**Current:** `if agent_type == "general-purpose" then prompt_B else prompt_A`.

**Proposed:** Resolve prompt template upstream in the calling command, or `iw render-prompt --agent-type <type> --phase <N> --issue <id>`.

---

## Implementation Strategy

The P1 items (D1-D4) deliver the most reliability improvement. Suggested phasing:

1. **D4 (iteration counter in review-state)** — smallest change, prevents the most common failure mode (lost counter). Just add a field to review-state.json and two subcommands.

2. **D1 (review-parse)** — new command, parses the code-review markdown format the pipeline already generates. Eliminates LLM number-extraction.

3. **D3 (check-tasks)** — new command, validates checkbox state in phase task files. Simple grep-based implementation.

4. **D2 (review-skills)** — new command with config-driven rules. Requires defining the mapping format, but the rules are already documented in the pipeline.

5. **D5 + D7** — fold into existing `phase-commit` or add small focused commands. These are mechanical find-replace operations.

P3 items can be addressed opportunistically or deferred.

## Notes

- The pipeline blueprint lives in `iterative-works/dev-docs` (DEVDOCS repo), not this repo. Changes here (new CLI commands) must be paired with pipeline updates there.
- The code-review markdown format is defined by the `code-review-coordinator` skill in dev-docs. The `review-parse` command needs to understand that format.
- `review-state.json` schema is defined in `skills/review-state-schema/SKILL.md` in dev-docs. Adding fields (like `review_iterations`) requires coordinating both repos.
