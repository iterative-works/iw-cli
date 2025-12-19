# Implementation Log: Add `iw feedback` command

Issue: IWLE-76

This log tracks the evolution of implementation across phases.

---

## Phase 1: LinearClient Extension (2025-12-18)

**What was built:**
- Constant: `.iw/core/Constants.scala` - Added `IwCliTeamId` for IWLE Linear team
- Case class: `CreatedIssue(id: String, url: String)` in LinearClient.scala
- Function: `buildCreateIssueMutation(title, description, teamId)` - GraphQL mutation builder with proper string escaping
- Function: `parseCreateIssueResponse(json)` - Parser handling success, API errors, and malformed responses
- Function: `createIssue(title, description, teamId, token)` - Full integration of mutation, HTTP call, and parsing

**Decisions made:**
- Keep `Either[String, Result]` pattern for consistency with existing codebase (rather than typed error ADT)
- Place `CreatedIssue` case class in LinearClient.scala near existing `Issue` type
- Add IwCliTeamId at top level of Constants (not nested) for direct access

**Patterns applied:**
- Pure functions for parsing and mutation building (testable without HTTP)
- Effects at edges (createIssue method performs HTTP)
- Defensive parsing with explicit error messages for each failure mode

**Testing:**
- Unit tests: 7 tests (1 for Constants, 6 for LinearClient create issue)
- Test coverage: Valid response parsing, API error handling, malformed JSON, mutation structure, string escaping, invalid token error path

**Code review:**
- Iterations: 1
- Review file: Inline review (no critical issues)
- Major findings: Suggestions for future - separate domain/infrastructure, add port interface for multi-tracker support

**For next phases:**
- Available utilities: `LinearClient.createIssue(title, description, teamId, token)` returns `Either[String, CreatedIssue]`
- Extension points: None specific to Phase 2
- Notes: Phase 2 will use `Constants.IwCliTeamId` for the IWLE team

**Files changed:**
```
M	.iw/core/Constants.scala
M	.iw/core/LinearClient.scala
M	.iw/core/test/ConstantsTest.scala
A	.iw/core/test/LinearClientCreateIssueTest.scala
```

---
