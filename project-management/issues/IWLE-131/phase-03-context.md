# Phase 3 Context: Mock-based unit tests with sttp backend injection

**Issue:** IWLE-131
**Phase:** 3 of 3
**Estimated:** 3-4 hours

## Goals

Add comprehensive unit tests for LinearClient HTTP operations using sttp's BackendStub for mocking. This allows testing the full request/response cycle without making real API calls.

## Scope

### In Scope
- Refactor LinearClient methods to accept optional Backend parameter
- Add unit tests for `createIssue` with mocked HTTP responses
- Add unit tests for `fetchIssue` with mocked HTTP responses
- Add unit tests for `validateToken` with mocked HTTP responses
- Test error handling: network errors, 401 unauthorized, malformed responses

### Out of Scope
- E2E tests (already covered in Phase 1-2)
- Live API tests (already have opt-in mechanism)
- Changes to existing parsing tests (already comprehensive)

## Dependencies

- **Phase 1 (Complete):** E2E tests skip real API by default
- **Phase 2 (Complete):** Warning messages and documentation
- Phase 3 is independent and enhances unit test coverage

## Technical Approach

### Current State

LinearClient methods use `quickRequest` which makes real HTTP calls:

```scala
def createIssue(...): Either[String, CreatedIssue] =
  val response = quickRequest
    .post(uri"$apiUrl")
    .header("Authorization", token.value)
    .header("Content-Type", "application/json")
    .body(mutation)
    .send()  // Makes real HTTP call
```

### Target State

Refactor to accept optional Backend parameter with default:

```scala
import sttp.client4.{Backend, DefaultSyncBackend}

def createIssue(
  title: String,
  description: String,
  teamId: String,
  token: ApiToken,
  labelIds: Seq[String] = Seq.empty,
  backend: Backend[Identity] = DefaultSyncBackend()
): Either[String, CreatedIssue] =
  val response = basicRequest
    .post(uri"$apiUrl")
    .header("Authorization", token.value)
    .header("Content-Type", "application/json")
    .body(mutation)
    .send(backend)  // Uses injected backend
```

### Testing with BackendStub

```scala
import sttp.client4.testing.BackendStub

test("createIssue returns success for valid response"):
  val testBackend = BackendStub.synchronous
    .whenRequestMatches(_.uri.path.endsWith(List("graphql")))
    .thenRespond("""{"data":{"issueCreate":{"success":true,"issue":{"id":"123","url":"https://..."}}}}""")

  val result = LinearClient.createIssue(
    title = "Test",
    description = "Desc",
    teamId = "team-1",
    token = ApiToken("test").get,
    backend = testBackend
  )

  assert(result.isRight)
```

## Files to Modify

| File | Changes |
|------|---------|
| `.iw/core/LinearClient.scala` | Add backend parameter to `createIssue`, `fetchIssue`, `validateToken` |
| `.iw/core/test/LinearClientMockTest.scala` | New file with mock-based unit tests |

## Testing Strategy

### Test Cases for createIssue

1. **Success response** - Returns Right(CreatedIssue)
2. **401 Unauthorized** - Returns Left with token error
3. **500 Server Error** - Returns Left with API error
4. **Network error** - Returns Left with network error
5. **Malformed response** - Returns Left with parse error

### Test Cases for fetchIssue

1. **Success response** - Returns Right(Issue)
2. **404 Not Found** - Returns Left with not found
3. **401 Unauthorized** - Returns Left with token error
4. **Malformed response** - Returns Left with parse error

### Test Cases for validateToken

1. **200 OK** - Returns true
2. **401 Unauthorized** - Returns false
3. **Network error** - Returns false

### Regression Check

- All existing parsing tests continue to pass
- Live API tests (when enabled) still work
- E2E tests unaffected

## Acceptance Criteria

- [ ] LinearClient.createIssue accepts optional backend parameter
- [ ] LinearClient.fetchIssue accepts optional backend parameter
- [ ] LinearClient.validateToken accepts optional backend parameter
- [ ] Default behavior unchanged (uses real HTTP when no backend provided)
- [ ] New unit tests cover success and error scenarios
- [ ] All tests pass without LINEAR_API_TOKEN set
- [ ] No real API calls made in new unit tests

## Notes

- sttp BackendStub is well-documented: https://sttp.softwaremill.com/en/latest/testing.html
- Using `Identity` type for synchronous backend (no effects)
- Default parameter ensures existing callers are unaffected
- Backend injection follows Dependency Injection pattern for testability
