# Code Review Results

**Review Context:** Phase 1: Bootstrap script runs tool via scala-cli for issue IWLE-72 (Iteration 1/3)
**Files Reviewed:** 9 files
**Skills Applied:** 2 (code-review-style, code-review-security)
**Timestamp:** 2025-12-11
**Git Context:** git diff fb520fc

---

<review skill="code-review-style">

## Code Review: Style and Documentation

### Critical Issues

None found.

### Warnings

#### 1. Inconsistent use of Output utility in version.scala

**Location:** `.iw/commands/version.scala:28`

**Problem:** The version command uses `println` directly instead of the `Output.info()` helper for the non-verbose case, while using `Output` helpers for the verbose case. This creates inconsistency.

```scala
// Current code (line 28):
println(s"iw-cli version $version")
```

**Impact:** Inconsistent output formatting makes the codebase harder to maintain. If output formatting needs to change (e.g., adding color, prefixes, or logging), changes must be made in multiple places.

**Recommendation:** Use `Output.info()` consistently:

```scala
// Should be:
Output.info(s"iw-cli version $version")
```

#### 2. Shell script uses mixed quoting styles

**Location:** `iw` (lines 24, 27, 30, 33)

**Problem:** The script uses `sed` with pipe delimiters (`'s|...|...|'`) in some places, which is less common than the traditional slash delimiter (`'s/.../.../'`). While this works correctly (pipe is chosen to avoid escaping slashes in paths), it's inconsistent with typical shell script conventions.

**Impact:** Minor readability concern. Developers unfamiliar with alternate sed delimiters may find this unusual.

**Recommendation:** This is actually fine given the context (avoiding path delimiter conflicts), but consider adding a comment explaining the delimiter choice:

```bash
# Using | as delimiter to avoid escaping / in paths
sed 's|^// PURPOSE: *||'
```

### Suggestions

#### 1. Add more descriptive function documentation in shell script

**Location:** `iw` (functions starting at lines 18, 39, 58, 94)

**Problem:** While the shell script functions have inline comments describing what they do, they lack detailed parameter documentation.

#### 2. Consider extracting hard-coded strings to constants

**Location:** `.iw/commands/version.scala:14-15`

**Problem:** Version numbers are defined as inline values. While appropriate for this phase, having a single source of truth for version would be beneficial.

#### 3. Empty lines at end of files

**Location:** Most Scala files end without a final newline

**Problem:** Files don't have a trailing newline at the end of the file. POSIX defines a line as ending with a newline character.

#### 4. Object naming could be more consistent

**Location:** All command files (e.g., `version.scala:13`)

**Problem:** Objects are named with "Command" suffix (e.g., `VersionCommand`, `InitCommand`), but this suffix doesn't add semantic value since the files are already in a `commands/` directory.

</review>

---

<review skill="code-review-security">

## Code Review: Security

### Critical Issues

#### 1. Shell command injection vulnerability in command execution

**Location:** `iw:106`

**Problem:** The script directly passes user-provided command name to construct a file path without validation or sanitization:

```bash
local cmd_file="$COMMANDS_DIR/${cmd_name}.scala"
```

**Impact:** An attacker could exploit this with path traversal to execute arbitrary Scala files:

```bash
./iw "../../etc/passwd" args
./iw "../../../tmp/malicious.scala" args
```

**Recommendation:** Validate that the command name contains only safe characters and doesn't include path separators:

```bash
execute_command() {
    local cmd_name="$1"

    # Validate command name - only alphanumeric, dash, underscore
    if [[ ! "$cmd_name" =~ ^[a-zA-Z0-9_-]+$ ]]; then
        echo "Error: Invalid command name '$cmd_name'" >&2
        echo "Command names must contain only letters, numbers, dashes, and underscores" >&2
        exit 1
    fi

    shift
    local cmd_file="$COMMANDS_DIR/${cmd_name}.scala"

    # ... rest of implementation
}
```

#### 2. Same path traversal issue in describe_command function

**Location:** `iw:60`

**Problem:** The `describe_command` function has the same path traversal vulnerability.

**Recommendation:** Apply the same validation as recommended for `execute_command`.

### Warnings

#### 1. Unrestricted argument passing to Scala commands

**Location:** `iw:106`

**Problem:** All arguments after the command name are passed directly to the Scala command without any inspection.

**Recommendation:** Document clearly that each command implementation MUST validate all inputs.

#### 2. No protection against symlink attacks in list_commands

**Location:** `iw:43-54`

**Problem:** The `list_commands` function iterates over files in `COMMANDS_DIR` but doesn't verify they're regular files (not symlinks to files outside the directory).

### Suggestions

#### 1. Consider adding integrity checking for command files

For future phases, consider storing checksums or making `.iw/commands/` read-only after installation.

#### 2. Error messages may leak path information

Current approach is fine for a development tool. Just be aware of this principle for future security-sensitive implementations.

#### 3. Scala command arguments not validated for injection

As you implement each command, validate all user inputs against expected patterns.

</review>

---

## Summary

- **Critical issues:** 2 (must fix before merge)
- **Warnings:** 4 (should fix)
- **Suggestions:** 6 (nice to have)

### By Skill
- code-review-style: 0 critical, 2 warnings, 4 suggestions
- code-review-security: 2 critical, 2 warnings, 2 suggestions

### Most Important Action Items

1. **Implement input validation** in the shell script to prevent path traversal attacks (Critical)
2. **Use `Output.info()` consistently** instead of mixing with direct `println` calls (Warning)
3. **Add validation guidelines** for future command implementations to prevent injection vulnerabilities (Warning)
