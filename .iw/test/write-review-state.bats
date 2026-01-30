#!/usr/bin/env bats
# PURPOSE: E2E tests for iw write-review-state command (v2 schema)
# PURPOSE: Tests flag-based writing, stdin mode, validation, and auto-inference

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    TEST_TMPDIR="$(mktemp -d)"
}

teardown() {
    rm -rf "$TEST_TMPDIR"
}

@test "write with minimal flags creates file and exits 0" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --issue-id IW-1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]
    [ -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Review state written"* ]]
}

@test "write with all v2 flags produces correct JSON" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --status awaiting_review \
        --issue-id IW-42 \
        --display-text "Awaiting Review" \
        --display-subtext "Phase 3 of 4" \
        --display-type warning \
        --badge "TDD:success" \
        --task-list "Phase 3:project-management/issues/IW-42/phase-03-tasks.md" \
        --needs-attention \
        --message "Phase 3 review complete" \
        --artifact "Analysis:analysis.md=input" \
        --artifact "Context:phase-03-context.md" \
        --action "continue:Continue:ag-implement" \
        --pr-url "https://github.com/org/repo/pull/99" \
        --version 1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    # Verify JSON content
    local json
    json="$(cat "$TEST_TMPDIR/review-state.json")"

    # Check fields
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['version'] == 1
assert d['issue_id'] == 'IW-42'
assert d['status'] == 'awaiting_review'
assert d['display']['text'] == 'Awaiting Review'
assert d['display']['subtext'] == 'Phase 3 of 4'
assert d['display']['type'] == 'warning'
assert len(d['badges']) == 1
assert d['badges'][0]['label'] == 'TDD'
assert d['badges'][0]['type'] == 'success'
assert len(d['task_lists']) == 1
assert d['task_lists'][0]['label'] == 'Phase 3'
assert d['needs_attention'] == True
assert d['message'] == 'Phase 3 review complete'
assert len(d['artifacts']) == 2
assert d['artifacts'][0]['label'] == 'Analysis'
assert d['artifacts'][0]['category'] == 'input'
assert d['artifacts'][1]['label'] == 'Context'
assert 'category' not in d['artifacts'][1]  # No category for second artifact
assert len(d['available_actions']) == 1
assert d['available_actions'][0]['id'] == 'continue'
assert d['pr_url'] == 'https://github.com/org/repo/pull/99'
assert 'git_sha' in d
assert 'last_updated' in d
"
}

@test "write with --from-stdin validates and writes" {
    local json='{"version":1,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-28T12:00:00Z"}'
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
        --issue-id IW-99 \
        --output "$outfile"
    [ "$status" -eq 0 ]
    [ -f "$outfile" ]
}

@test "artifact with category is preserved in output" {
    run "$PROJECT_ROOT/iw" write-review-state \
        --issue-id IW-1 \
        --artifact "Analysis:analysis.md=input" \
        --artifact "Log:implementation-log.md=output" \
        --artifact "Tasks:tasks.md" \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/review-state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert len(d['artifacts']) == 3
assert d['artifacts'][0]['category'] == 'input'
assert d['artifacts'][1]['category'] == 'output'
assert 'category' not in d['artifacts'][2]  # No category
"
}

@test "auto-infers issue ID from current branch" {
    # We are on branch IW-136, so issue ID should be IW-136
    run "$PROJECT_ROOT/iw" write-review-state \
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
        --issue-id IW-1 \
        --display-text "Implementing" \
        --display-type progress \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    run "$PROJECT_ROOT/iw" validate-review-state "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}
