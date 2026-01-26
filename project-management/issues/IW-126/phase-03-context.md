# Phase 3: Integrate llms.txt with iw-command-creation skill

**Issue:** IW-126
**Phase:** 3 of 3
**Story:** Story 4 - Agent finds documentation via skill

## Goals

Update the `iw-command-creation` skill to reference the newly created llms.txt documentation instead of directing agents to read source files directly. This is the final phase that connects all the previous work together.

## Scope

### In Scope
- Update `.claude/skills/iw-command-creation/SKILL.md` to reference llms.txt
- Update the "Finding Core Module Documentation" section to point to llms.txt
- Update the "Available Core Modules" table to reference docs/ files
- Ensure path resolution works regardless of iw-cli installation location

### Out of Scope
- Creating new documentation files (done in Phase 2)
- Modifying any Scala source code
- Changes to the llms.txt or docs/ files themselves

## Dependencies

### From Phase 1
- FCIS directory structure established (`model/`, `adapters/`, `output/`, `dashboard/`)
- Package imports updated to reflect new structure

### From Phase 2
- `.iw/llms.txt` index file exists
- `.iw/docs/*.md` documentation files exist (25 files)
- Documentation covers all public API modules

## Technical Approach

### Current State (to change)

The skill currently directs agents to read source files:
```markdown
## Finding Core Module Documentation

The core modules are located in the iw-cli installation. To find them:
...
To explore a module's API, read its source:
```bash
cat $IW_CORE_DIR/Output.scala
```

### Target State

The skill should direct agents to read llms.txt:
```markdown
## Finding Core Module Documentation

Complete API documentation is available in llms.txt format:

```bash
# Read the main index
cat $IW_CORE_DIR/../llms.txt

# Read specific module docs
cat $IW_CORE_DIR/../docs/Output.md
```

The llms.txt file follows the standard format with:
- Module index with brief descriptions
- Links to detailed per-module documentation
- Examples extracted from real commands
```

### Changes Required

1. **Update "Finding Core Module Documentation" section**
   - Replace source file reading instructions with llms.txt references
   - Use `$IW_CORE_DIR/../llms.txt` for path resolution (llms.txt is in `.iw/`, IW_CORE_DIR points to `.iw/core/`)

2. **Update "Available Core Modules" section**
   - Either replace the table with a reference to llms.txt, OR
   - Keep the table but note that detailed docs are in llms.txt
   - Prefer the latter for quick reference

3. **Add note about documentation format**
   - Brief explanation that llms.txt follows the standard format
   - Agents can read the index first, then dive into specific modules

4. **Verify path resolution**
   - `$IW_CORE_DIR` typically points to `.iw/core/`
   - llms.txt is at `.iw/llms.txt`
   - Relative path: `$IW_CORE_DIR/../llms.txt`
   - Docs are at: `$IW_CORE_DIR/../docs/`

## Files to Modify

1. `.claude/skills/iw-command-creation/SKILL.md`
   - Lines ~103-119: "Finding Core Module Documentation" section
   - Lines ~121-139: "Available Core Modules" table (update or supplement)

## Testing Strategy

### Verification Steps
1. Path resolution test:
   ```bash
   # Simulate what an agent would do
   export IW_CORE_DIR=".iw/core"
   test -f "$IW_CORE_DIR/../llms.txt" && echo "OK" || echo "FAIL"
   test -d "$IW_CORE_DIR/../docs" && echo "OK" || echo "FAIL"
   ```

2. Skill file validation:
   - YAML frontmatter still valid
   - Markdown renders correctly
   - Links/paths are correct

3. Manual test:
   - Load the skill in Claude
   - Verify agent can follow the documentation path
   - Verify llms.txt content is readable and useful

## Acceptance Criteria

- [ ] Skill references llms.txt instead of source files for API documentation
- [ ] Path uses `$IW_CORE_DIR/../llms.txt` pattern (or equivalent)
- [ ] Skill still provides quick module overview for convenience
- [ ] Agents can follow the path to find llms.txt and docs/
- [ ] No breaking changes to skill loading/parsing

## Estimated Effort

1-2 hours (mostly documentation text updates)

## Notes

- This is primarily a text update to the skill file
- No Scala code changes required
- The change completes the documentation discoverability loop:
  Agent → Skill → llms.txt → docs/*.md → correct API usage
