# Code Review Results

**Review Context:** Phase 02 for issue IWLE-76
**Files Reviewed:** 6 files
**Skills Applied:** 7 (scala3, composition, architecture, api, style, testing, security)
**Timestamp:** 2025-12-19 18:47:19
**Git Context:** git diff 18da676..HEAD

---

<review skill="scala3">

## Scala 3 Idioms Review

### Critical Issues

None found.

### Warnings

#### Consider Opaque Type for Label IDs
**Location:** `.iw/core/Constants.scala:49-51`
**Problem:** Label IDs are plain String values without type safety
**Impact:** Could potentially mix up Bug and Feature label IDs since they're both Strings. Type system cannot prevent accidentally using a label ID in place of a team ID or other UUID string.
**Recommendation:** Consider using opaque types for stronger type safety. However, this may be overkill for internal constants - current approach is acceptable if these IDs are only used in controlled locations.

### Suggestions

1. **Excellent Enum Usage** - The `enum IssueType` with companion object `fromString` method is exactly the right Scala 3 pattern. Well done.

2. **Extension Methods Opportunity** - Consider adding a `toLabelId` extension method on `IssueType` to move the pattern matching from `feedback.scala:38-40` into the domain type itself.

3. **Early Return in Parser** - Uses procedural `return` statement at `.iw/core/FeedbackParser.scala:28`. Consider pure functional approach using pattern matching or for-comprehension instead.

</review>

---

<review skill="composition">

## Composition Patterns Review

### Critical Issues

None found.

### Warnings

#### Procedural Command Entry Point Could Use Effect Composition
**Location:** `.iw/commands/feedback.scala:15-59`
**Problem:** The `@main def feedback` function uses imperative pattern matching with side effects (sys.exit) scattered throughout rather than composing effects that return results.
**Impact:** Command logic is harder to test in isolation, side effects are scattered, and multiple exit points make control flow harder to reason about.
**Recommendation:** Consider extracting core logic into a pure function `runFeedback(args: Seq[String]): Either[String, CreatedIssue]` that composes Either values, with effects only at the edges.

#### Flag Parsing Logic Not Composed into Reusable Functions
**Location:** `.iw/core/FeedbackParser.scala:24-40`
**Problem:** The `parseFeedbackArgs` function contains sequential parsing steps that could be composed from smaller, reusable parsing functions.
**Impact:** Reduced reusability if other commands need similar parsing logic.
**Recommendation:** Consider extracting `parseTitle` and `parseOptionalFlag` functions if this pattern appears in other commands.

### Suggestions

1. **Consider extractFlagValue as Generic Utility** - If other commands need similar flag parsing, move to a shared `ArgParser` utility module.

2. **Small, Focused Components** - The code demonstrates good component size and focus. Each component is small, testable, and focused on a single responsibility.

</review>

---

<review skill="architecture">

## Architecture Review (FCIS/DDD)

### Critical Issues

None found.

### Warnings

#### Imperative Shell Mixing with Core in commands/ Directory
**Location:** `.iw/commands/feedback.scala:37-40`
**Problem:** The label ID mapping logic is business logic embedded in the imperative shell layer.
**Impact:** If this mapping changes or becomes more complex, it will be harder to test since it's coupled with I/O operations.
**Recommendation:** Extract the label mapping into `FeedbackParser.getLabelIdForIssueType()` in the core layer.

#### Side Effects in LinearClient Without Effect Type Wrapper
**Location:** `.iw/core/LinearClient.scala:14-29, 31-50, 171-190`
**Problem:** LinearClient is in the core layer but performs I/O operations (HTTP requests) directly without any effect type wrapper.
**Impact:** Functions in the core layer are performing I/O, making them impure and harder to test. Creates architectural inconsistency.
**Recommendation:** Consider either:
1. Move LinearClient to an `infrastructure/` layer to clearly signal it's imperative shell code
2. Document that `.iw/core/` contains some effectful operations
3. Consider wrapping side effects in lazy evaluation `() => Either[...]`

### Suggestions

1. **Feature-Based Organization** - For future features, consider organizing by feature domain (e.g., `core/feedback/`, `core/linear/`)

2. **FeedbackRequest Could Be a Value Object with Validation** - Consider smart constructor pattern to make illegal states unrepresentable

3. **Test Package Structure** - Tests are in `iw.tests` but production is in `iw.core`. Consider mirroring package structure.

</review>

---

<review skill="api">

## API Design Review

### Critical Issues

None found.

### Warnings

#### Inconsistent Error Response Structure Between Operations
**Location:** `.iw/core/LinearClient.scala:46-48, 186-188`
**Problem:** Different operations use different error response patterns. Makes it difficult for callers to handle errors systematically.
**Recommendation:** Define a structured error type (`sealed trait LinearError`) with specific cases like `UnauthorizedError`, `NetworkError`, `ApiError`, `ParseError`.

#### Missing HTTP Status Code Granularity
**Location:** `.iw/core/LinearClient.scala:182-188`
**Problem:** Only explicitly handles 200 OK and 401 Unauthorized. All other codes are lumped into generic catch-all.
**Impact:** Loses semantic information about why a request failed. A 400 (bad input) is very different from a 500 (server error).
**Recommendation:** Handle key HTTP status codes explicitly (400, 403, 404, 422, 5xx) to provide better error messages.

#### GraphQL Error Structure Not Following HTTP Status Code Semantics
**Location:** `.iw/core/LinearClient.scala:120-160`
**Problem:** GraphQL APIs typically return HTTP 200 even for application-level errors. The parsing correctly checks for GraphQL errors but this behavior should be documented.
**Recommendation:** Document Linear's actual error response behavior in comments.

### Suggestions

1. **Content-Type Validation** - Consider checking response Content-Type is `application/json` before parsing
2. **User-Agent Header** - Add `User-Agent: iw-cli/1.0.0` header for API client identification
3. **Request Timeout Configuration** - Make timeout explicit rather than relying on sttp defaults

</review>

---

<review skill="style">

## Code Style & Documentation Review

### Critical Issues

None found.

### Warnings

#### Missing Scaladoc on Public API Methods
**Location:** `.iw/core/FeedbackParser.scala:24`
**Problem:** The public method `parseFeedbackArgs` lacks Scaladoc documentation
**Recommendation:** Add Scaladoc with @param, @return tags, and examples

#### Missing Scaladoc on Companion Object Method
**Location:** `.iw/core/FeedbackParser.scala:12`
**Problem:** The public method `IssueType.fromString` lacks Scaladoc documentation
**Recommendation:** Add Scaladoc explaining case-insensitive matching and error cases

#### Helper Function showHelp Should Be Private
**Location:** `.iw/commands/feedback.scala:61`
**Problem:** The `showHelp()` function is defined at package level without access modifier
**Recommendation:** Mark as `private def showHelp()` or nest inside a containing object

### Suggestions

1. Add Scaladoc to `FeedbackRequest` case class
2. Add documentation to `IssueType` enum values mentioning Linear label mapping
3. Enhance `IwCliLabels` documentation with guidance on finding/updating label UUIDs

### Formatting & Naming

All checks pass:
- [x] Consistent indentation (2 spaces, Scala 3 style)
- [x] Line length under 100 characters
- [x] PascalCase for classes/traits/objects
- [x] camelCase for methods/variables
- [x] PURPOSE comments present on all files

</review>

---

<review skill="testing">

## Testing Review

### Critical Issues

#### Missing Edge Case Tests in FeedbackParserTest
**Location:** `.iw/core/test/FeedbackParserTest.scala:1-49`
**Problem:** The unit tests do not cover several important edge cases for argument parsing
**Impact:** Untested parsing scenarios could lead to runtime failures
**Recommendation:** Add tests for:
1. Multiple word descriptions: `Seq("Title", "--description", "Long", "multi", "word", "description")`
2. Multiple flags combined: `Seq("Title", "--type", "bug", "--description", "Some desc")`
3. Empty description flag: `Seq("Title", "--description")`
4. Empty type flag: `Seq("Title", "--type")`
5. Case sensitivity of type: `Seq("Title", "--type", "BUG")`
6. Multiple occurrences of same flag

#### E2E Tests Create Test Pollution Without Cleanup
**Location:** `.iw/test/feedback.bats:53-97`
**Problem:** Three E2E tests create real Linear issues with `[TEST]` prefix but never clean them up
**Impact:** Over time, these tests will create hundreds of test issues in the Linear team
**Recommendation:** Either:
1. Add teardown to delete created issues
2. Use a dedicated test team ID
3. Document manual cleanup process

#### No Tests for extractFlagValue Edge Cases
**Location:** `.iw/core/FeedbackParser.scala:42-50`
**Problem:** The `extractFlagValue` helper function has untested edge cases (flags at end, missing values)
**Recommendation:** Add focused tests for these scenarios

### Warnings

1. **E2E Tests Don't Verify Label Assignment** - Test for `--type bug` doesn't verify the bug label was actually applied
2. **No Test Coverage for Help Flag Edge Cases** - Only tests `--help`, not `-h` alias
3. **Test Doesn't Verify Exact Error Messages** - Only checks for keywords, not full messages

### Suggestions

1. Consider property-based testing for argument parser
2. Add test for special characters in title/description
3. Make skip messages more helpful for developers

</review>

---

<review skill="security">

## Security Review

### Critical Issues

None found. (Previous "critical" items reassessed as warnings given the context)

### Warnings

#### Incomplete Input Escaping for GraphQL
**Location:** `.iw/core/LinearClient.scala:61-62`
**Problem:** User-provided title and description escape backslashes and quotes but not newlines or other special characters
**Impact:** Edge cases with newlines could cause malformed JSON or unexpected behavior
**Recommendation:** Also escape newlines (`\n` → `\\n`), carriage returns, and tabs

#### Missing Input Length Validation
**Location:** `.iw/core/FeedbackParser.scala:24-40`
**Problem:** No validation of input length before passing to Linear API
**Impact:** Extremely long titles or descriptions could cause API errors or denial of service
**Recommendation:** Add maximum length validation (e.g., 500 chars for title, 10000 for description)

#### API Token in Exception Messages (Potential)
**Location:** `.iw/core/LinearClient.scala:189-190`
**Problem:** Network exceptions are caught and message is returned directly, which could contain headers or sensitive data in some edge cases
**Recommendation:** Return generic "Network error occurred" instead of full exception message

#### No Rate Limiting
**Location:** `.iw/core/LinearClient.scala:171-190`
**Problem:** No protection against rapid-fire API calls (minor for CLI tool)
**Recommendation:** Document rate limit behavior in API method docs

### Suggestions

1. Verify `ApiToken` has secure `toString` that redacts the value
2. Verify sttp has proper SSL/TLS verification enabled by default
3. Consider more careful error message wrapping to avoid information leakage

</review>

---

## Summary

- **Critical issues:** 3 (testing gaps - should fix before merge)
- **Warnings:** 14 (should fix or document)
- **Suggestions:** 20+ (nice to have)

### By Skill
| Skill | Critical | Warnings | Suggestions |
|-------|----------|----------|-------------|
| scala3 | 0 | 1 | 3 |
| composition | 0 | 2 | 2 |
| architecture | 0 | 2 | 3 |
| api | 0 | 3 | 3 |
| style | 0 | 3 | 3 |
| testing | 3 | 3 | 3 |
| security | 0 | 4 | 3 |

---

## Priority Action Items

### Must Fix Before Merge (Critical)

1. **Add missing edge case tests for FeedbackParser**
   - Combined flags (`--type` + `--description`)
   - Multi-word description values
   - Empty flag values
   - Flag at end of args without value

2. **Add test for help flag alias (`-h`)**

3. **Document E2E test cleanup process** or add teardown

### Should Fix (High Priority Warnings)

1. **Add input length validation** in FeedbackParser (security)
2. **Improve GraphQL escaping** for newlines (security)
3. **Add Scaladoc** to public API methods (style)
4. **Make `showHelp()` private** (style)
5. **Extract label mapping to core layer** (architecture)

### Consider Fixing (Moderate Warnings)

6. Define structured error types in LinearClient (api)
7. Handle more HTTP status codes explicitly (api)
8. Refactor procedural returns to functional style (composition)
9. Document sttp SSL configuration (security)

---

## Overall Assessment

**APPROVE WITH COMMENTS**

The implementation is solid and production-ready. The main gaps are:
- Test coverage for edge cases
- Documentation on public APIs
- Minor security hardening

The architecture follows good patterns (FCIS separation, focused components) and the code quality is high. The warnings represent opportunities for improvement rather than blockers, except for the testing gaps which should be addressed before merge.
