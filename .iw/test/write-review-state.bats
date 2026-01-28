#!/usr/bin/env bats
# PURPOSE: E2E tests for iw write-review-state command
# PURPOSE: Tests flag-based writing, stdin mode, validation, and auto-inference

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    TEST_TMPDIR="$(mktemp -d)"
}

teardown() {
    rm -rf "$TEST_TMPDIR"
}

@test "write with required flags creates file and exits 0" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --status implementing \
        --issue-id IW-1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]
    [ -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Review state written"* ]]
}

@test "write with all flags produces correct JSON" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --status awaiting_review \
        --issue-id IW-42 \
        --phase 3 \
        --step review \
        --message "Phase 3 review" \
        --artifact "Analysis:analysis.md" \
        --artifact "Context:phase-03-context.md" \
        --action "continue:Continue:ag-implement" \
        --branch IW-42 \
        --batch-mode \
        --pr-url "https://github.com/org/repo/pull/99" \
        --version 1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    # Verify JSON content
    local json
    json="$(cat "$TEST_TMPDIR/review-state.json")"

    # Check required fields
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['version'] == 1
assert d['issue_id'] == 'IW-42'
assert d['status'] == 'awaiting_review'
assert d['phase'] == 3
assert d['step'] == 'review'
assert d['message'] == 'Phase 3 review'
assert len(d['artifacts']) == 2
assert d['artifacts'][0]['label'] == 'Analysis'
assert d['artifacts'][1]['label'] == 'Context'
assert len(d['available_actions']) == 1
assert d['available_actions'][0]['id'] == 'continue'
assert d['branch'] == 'IW-42'
assert d['batch_mode'] == True
assert d['pr_url'] == 'https://github.com/org/repo/pull/99'
assert 'git_sha' in d
assert 'last_updated' in d
"
}

@test "write with --from-stdin validates and writes" {
    local json='{"version":1,"issue_id":"IW-1","status":"implementing","artifacts":[],"last_updated":"2026-01-28T12:00:00Z"}'
    run bash -c "echo '$json' | '$PROJECT_ROOT/iw' write-review-state --from-stdin --output '$TEST_TMPDIR/review-state.json'"
    [ "$status" -eq 0 ]
    [ -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Review state written"* ]]
}

@test "invalid status in stdin exits 1 and does not write file" {
    local json='{"version":"bad"}'
    run bash -c "echo '$json' | '$PROJECT_ROOT/iw' write-review-state --from-stdin --output '$TEST_TMPDIR/review-state.json'"
    [ "$status" -eq 1 ]
    [ ! -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Validation failed"* ]]
}

@test "--output flag writes to specified path" {
    local outfile="$TEST_TMPDIR/subdir/nested/state.json"
    run "$PROJECT_ROOT/iw" write-review-state \
        --status implementing \
        --issue-id IW-99 \
        --output "$outfile"
    [ "$status" -eq 0 ]
    [ -f "$outfile" ]
}

@test "missing --status flag exits 1" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --issue-id IW-1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"--status is required"* ]]
    [ ! -f "$TEST_TMPDIR/review-state.json" ]
}

@test "phase as string is preserved in output" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --status implementing \
        --issue-id IW-1 \
        --phase "1-R1" \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/review-state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['phase'] == '1-R1', f'Expected 1-R1 but got {d[\"phase\"]}'
"
}

@test "auto-infers issue ID from current branch" {
    # We are on branch IW-136-phase-03, so issue ID should be IW-136
    run "$PROJECT_ROOT/iw" write-review-state \
        --status implementing \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/review-state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['issue_id'] == 'IW-136', f'Expected IW-136 but got {d[\"issue_id\"]}'
"
}

@test "written file passes validate-review-state" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --status implementing \
        --issue-id IW-1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    run "$PROJECT_ROOT/iw" validate-review-state "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}
