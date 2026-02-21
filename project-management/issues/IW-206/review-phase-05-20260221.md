# Code Review Results

**Review Context:** Phase 5: Project cards link to project details for issue IW-206
**Files Reviewed:** 2
**Skills Applied:** code-review-style, code-review-testing, code-review-scala3, code-review-architecture
**Timestamp:** 2026-02-21
**Git Context:** git diff 1640035..8bb8076

---

<review skill="code-review-style">

### Critical Issues
None found.

### Warnings
None found.

### Suggestions

1. **Test assertion with `&&` hides which condition failed** — `assert(html.contains("<a ") && html.contains("iw-cli</h3>"), ...)` in `"project name link wraps the project heading"` would be clearer as two separate assertions with distinct messages.

</review>

---

<review skill="code-review-testing">

### Critical Issues
None found.

### Warnings

#### Fragile HTML structure assertion
**Location:** `.iw/core/test/MainProjectsViewTest.scala:161`
**Problem:** Test checks for `"<a "` and `"iw-cli</h3>"` separately anywhere in the HTML. These could match even if the elements are not nested. A more precise assertion like `html.contains("<a href=\"/projects/iw-cli\"><h3>iw-cli</h3></a>")` would verify actual structure.
**Impact:** Pre-existing pattern across the codebase — not a regression.

### Suggestions

1. **Missing test for multiple projects with unique links** — Tests verify a single project card. Adding a test with two projects would confirm each card links to its own details page.

2. **No test for project names with special characters** — The `projectName` is interpolated directly into the href without URL encoding. If project names can contain spaces or special characters, this could break. Worth a test or a note that names are constrained to URL-safe identifiers.

</review>

---

<review skill="code-review-scala3">

### Critical Issues
None found.

### Warnings
None found.

### Suggestions

1. **Consider opaque type for ProjectName** — `projectName` is a plain `String` that gets interpolated into URL paths. An opaque type would add type safety and prevent confusion with other string fields like `team`. Same suggestion as Phase 4 review for URL-encoded paths — worth considering holistically rather than per-phase.

</review>

---

<review skill="code-review-architecture">

### Critical Issues
None found.

### Warnings
None found.

### Suggestions

1. **Project name not URL-encoded in href** — `s"/projects/${project.projectName}"` interpolates the name directly. Phase 4 used `URLEncoder.encode` for project paths. If project names are guaranteed URL-safe (alphanumeric + hyphens), this is fine. If not, encoding would be needed for consistency. The CaskServer route handler would also need to decode.

### Notes

- View remains a pure rendering function — no side effects
- Change is minimal and focused (wrapping h3 in anchor tag)
- Correctly placed in presentation layer
- Tests appropriately cover the new behavior

</review>

---

## Summary

- **Critical issues:** 0
- **Warnings:** 1 (fragile HTML assertion — pre-existing pattern)
- **Suggestions:** 4 (URL encoding of project names, opaque types, multi-project test, assertion clarity)

**Assessment:** Code review passes. Very clean, minimal change — wraps an `h3` in an `<a>` tag. The main substantive suggestion across multiple skills is whether project names need URL encoding. If names are constrained to URL-safe identifiers (which appears to be the case given `projectName` is derived from directory names), the current approach is correct. No blocking issues.
