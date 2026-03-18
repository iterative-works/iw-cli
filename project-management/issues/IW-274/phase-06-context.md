# Phase 6: Documentation — llms.txt and module docs

## Goals

Update developer-facing documentation to reflect changes from Phases 1–5:
- `ReviewState` gained `activity` and `workflowType` fields
- `WorktreeSummary` was redesigned with 10 new fields
- `WorktreesFormatter` gained activity, workflow type, and progress display

## Scope

### In Scope
1. `.iw/docs/WorkflowTypes.md` — Update `ReviewState` API to include `activity` and `workflowType` fields
2. `.iw/docs/WorktreeSummary.md` — Create new doc file for redesigned `WorktreeSummary` (referenced in llms.txt but missing)
3. `.iw/llms.txt` — Update descriptions for `WorkflowTypes` and `WorktreeSummary` entries to mention new fields

### Out of Scope
- The `review-state-schema` skill (iterative-works skill, not local)
- Skill updates for workflow transition points (separate issue per analysis)
- Any code changes

## Dependencies on Prior Phases

- Phase 2: `ReviewState` field additions (activity, workflowType)
- Phase 5: `WorktreeSummary` redesign, `WorktreesFormatter` updates

## Approach

1. Read current source files to get accurate field signatures
2. Update `WorkflowTypes.md` to match current `ReviewState` case class
3. Create `WorktreeSummary.md` documenting the redesigned case class
4. Update llms.txt entry descriptions

## Files to Modify

- `.iw/docs/WorkflowTypes.md` — update ReviewState section
- `.iw/docs/WorktreeSummary.md` — create new file
- `.iw/llms.txt` — update entry descriptions

## Testing Strategy

- No code changes, so no unit tests needed
- Verify documentation accuracy against source code

## Acceptance Criteria

- [ ] `WorkflowTypes.md` shows `activity` and `workflowType` on `ReviewState`
- [ ] `WorktreeSummary.md` exists with complete API documentation
- [ ] `llms.txt` descriptions are accurate for both entries
- [ ] All documented types match current source code
