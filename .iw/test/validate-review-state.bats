#!/usr/bin/env bats
# PURPOSE: E2E tests for iw validate-review-state command
# PURPOSE: Tests file validation, stdin mode, error reporting, and exit codes

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

@test "validates valid minimal file - exit 0" {
    run "$PROJECT_ROOT/iw" validate-review-state "$PROJECT_ROOT/.iw/core/test/resources/review-state/valid-minimal.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "validates valid full file - exit 0" {
    run "$PROJECT_ROOT/iw" validate-review-state "$PROJECT_ROOT/.iw/core/test/resources/review-state/valid-full.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "invalid file with missing required fields - exit 1" {
    run "$PROJECT_ROOT/iw" validate-review-state "$PROJECT_ROOT/.iw/core/test/resources/review-state/invalid-missing-required.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"issue_id"* ]]
}

@test "invalid file with wrong types - exit 1" {
    run "$PROJECT_ROOT/iw" validate-review-state "$PROJECT_ROOT/.iw/core/test/resources/review-state/invalid-wrong-types.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"version"* ]]
    [[ "$output" == *"issue_id"* ]]
}

@test "malformed JSON - exit 1 with parse error" {
    local tmpfile
    tmpfile="$(mktemp)"
    echo "not json at all {" > "$tmpfile"
    run "$PROJECT_ROOT/iw" validate-review-state "$tmpfile"
    rm -f "$tmpfile"
    [ "$status" -eq 1 ]
    [[ "$output" == *"parse"* ]] || [[ "$output" == *"Parse"* ]]
}

@test "non-existent file - exit 1 with error" {
    run "$PROJECT_ROOT/iw" validate-review-state "/tmp/does-not-exist-$RANDOM.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"not found"* ]] || [[ "$output" == *"File not found"* ]]
}

@test "any status value is accepted without warning in v2 schema" {
    local tmpfile
    tmpfile="$(mktemp)"
    cat > "$tmpfile" << 'EOF'
{
  "version": 1,
  "issue_id": "IW-99",
  "status": "exotic_unknown_status",
  "artifacts": [],
  "last_updated": "2026-01-28T12:00:00Z"
}
EOF
    run "$PROJECT_ROOT/iw" validate-review-state "$tmpfile"
    rm -f "$tmpfile"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "no arguments shows usage error - exit 1" {
    run "$PROJECT_ROOT/iw" validate-review-state
    [ "$status" -eq 1 ]
    [[ "$output" == *"No file path"* ]] || [[ "$output" == *"Usage"* ]]
}

@test "stdin mode with valid JSON - exit 0" {
    run bash -c "echo '{\"version\": 1, \"issue_id\": \"IW-1\", \"status\": \"implementing\", \"artifacts\": [], \"last_updated\": \"2026-01-28T12:00:00Z\"}' | '$PROJECT_ROOT/iw' validate-review-state --stdin"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "stdin mode with invalid JSON - exit 1" {
    run bash -c "echo '{\"version\": \"bad\"}' | '$PROJECT_ROOT/iw' validate-review-state --stdin"
    [ "$status" -eq 1 ]
}
