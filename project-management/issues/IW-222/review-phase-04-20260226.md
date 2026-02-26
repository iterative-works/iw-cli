# Code Review Results

**Review Context:** Phase 4: --prompt support for start/open for issue IW-222 (Iteration 1/3)
**Files Reviewed:** 4
**Skills Applied:** code-review-style, code-review-testing (manual), code-review-security
**Timestamp:** 2026-02-26
**Git Context:** git diff 0fd8cc4

---

<review skill="code-review-style">

## Code Style Review

### Critical Issues

None found.

### Warnings

#### Duplicated `extractPrompt` Function
**Location:** `.iw/commands/open.scala:40-50` and `.iw/commands/start.scala:42-52`
**Problem:** Identical `extractPrompt` function appears in both files.
**Impact:** Code duplication creates maintenance burden. Changes must be made in two places.
**Note:** These are standalone scala-cli scripts that don't share compilation units. Duplication is a known trade-off of the script-per-command architecture.

#### Duplicated Session Join Logic
**Location:** `.iw/commands/open.scala:56-87` and `.iw/commands/start.scala:115-147`
**Problem:** Nearly identical session joining logic in both files.
**Impact:** Same as above — architectural trade-off of standalone scripts.

### Suggestions

#### Inconsistent Function Indentation in `open.scala`
**Location:** `.iw/commands/open.scala:89`
**Problem:** `openWorktreeSession` function body uses 6-space indent (pre-existing from before this phase).
**Note:** Pre-existing style, not introduced by this change.

</review>

---

<review skill="code-review-testing">

## Testing Review (Manual)

### Critical Issues

None found.

### Warnings

None found.

### Suggestions

#### Testing reviewer skill unavailable
Manual assessment: E2E test coverage is comprehensive — 7 tests for start-prompt, 8 for open-prompt. Tests cover happy path, error cases, regression, edge cases (empty string, quotes), and pane content verification.

</review>

---

<review skill="code-review-security">

## Security Review

### Critical Issues

#### Shell Injection via Inadequate Quote Escaping
**Location:** `.iw/commands/start.scala:119-120` and `.iw/commands/open.scala:60-61`
**Problem:** Escaping only handles double quotes with `prompt.replace("\"", "\\\"")`. When embedded in double-quoted string and typed into a shell via tmux, `$()`, backticks, and other metacharacters are still interpreted.
**Impact:** Prompt text like `$(malicious_command)` would execute in the tmux pane's shell.
**Recommendation:** Use single-quote wrapping: `"'" + s.replace("'", "'\\''") + "'"` which protects all shell metacharacters.

### Warnings

#### Use of `--dangerously-skip-permissions` Flag
**Location:** Both commands
**Problem:** Hardcoded flag bypasses Claude CLI permission checks.
**Note:** This is an intentional design decision per the analysis (IW-222 spec). Documented in the analysis as "Hard-code for now. YAGNI."

#### No Input Validation on Prompt Length
**Location:** Both `extractPrompt` functions
**Problem:** No length limit on prompt text.
**Impact:** Low — tmux has practical limits, and the user is the one typing the command.

### Suggestions

#### Add Shell Metacharacter Tests
**Problem:** Tests check quote handling but not `$()` or backtick injection.
**Recommendation:** Add tests verifying literal treatment of shell metacharacters.

</review>

---

## Summary

- **Critical issues:** 1 (shell escaping — must fix)
- **Warnings:** 4 (2 duplication — architectural trade-off, 1 intentional design, 1 low-impact)
- **Suggestions:** 3
