# Phase 3 Tasks: Mock-based unit tests with sttp backend injection

**Issue:** IWLE-131
**Phase:** 3 of 3

## Implementation Tasks

### Refactor LinearClient for Backend Injection

- [x] [impl] Add backend parameter to LinearClient.validateToken method
- [x] [impl] Add backend parameter to LinearClient.fetchIssue method
- [x] [impl] Add backend parameter to LinearClient.createIssue method
- [x] [test] Verify existing tests still pass after refactoring

### Add Mock-based Unit Tests

- [x] [impl] Create LinearClientMockTest.scala with BackendStub setup
- [x] [test] Add test: validateToken returns true for 200 OK response
- [x] [test] Add test: validateToken returns false for 401 Unauthorized
- [x] [test] Add test: fetchIssue returns Right(Issue) for valid response
- [x] [test] Add test: fetchIssue returns Left for 401 Unauthorized
- [x] [test] Add test: createIssue returns Right(CreatedIssue) for valid response
- [x] [test] Add test: createIssue returns Left for 401 Unauthorized
- [x] [test] Add test: createIssue returns Left for 500 Server Error

### Verification

- [x] [test] Run all unit tests to confirm no regressions
- [x] [test] Verify no real API calls made in new tests (no LINEAR_API_TOKEN needed)

## Notes

- Use sttp BackendStub.synchronous for mock backend
- Default parameter ensures backward compatibility
- Test file location: `.iw/core/test/LinearClientMockTest.scala`
