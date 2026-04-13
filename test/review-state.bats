#!/usr/bin/env bats
# PURPOSE: E2E tests for iw review-state command (all subcommands)
# PURPOSE: Tests validate, write, and update subcommands with various options

# Get the project root directory (parent of test/)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    TEST_TMPDIR="$(mktemp -d)"
}

teardown() {
    rm -rf "$TEST_TMPDIR"
}

# ============================================================================
# VALIDATE SUBCOMMAND TESTS
# ============================================================================

@test "review-state validate: valid minimal file - exit 0" {
    run "$PROJECT_ROOT/iw" review-state validate "$PROJECT_ROOT/core/test/resources/review-state/valid-minimal.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "review-state validate: valid full file - exit 0" {
    run "$PROJECT_ROOT/iw" review-state validate "$PROJECT_ROOT/core/test/resources/review-state/valid-full.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "review-state validate: invalid file with missing required fields - exit 1" {
    run "$PROJECT_ROOT/iw" review-state validate "$PROJECT_ROOT/core/test/resources/review-state/invalid-missing-required.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"issue_id"* ]]
}

@test "review-state validate: invalid file with wrong types - exit 1" {
    run "$PROJECT_ROOT/iw" review-state validate "$PROJECT_ROOT/core/test/resources/review-state/invalid-wrong-types.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"version"* ]]
    [[ "$output" == *"issue_id"* ]]
}

@test "review-state validate: malformed JSON - exit 1 with parse error" {
    local tmpfile
    tmpfile="$(mktemp)"
    echo "not json at all {" > "$tmpfile"
    run "$PROJECT_ROOT/iw" review-state validate "$tmpfile"
    rm -f "$tmpfile"
    [ "$status" -eq 1 ]
    [[ "$output" == *"parse"* ]] || [[ "$output" == *"Parse"* ]]
}

@test "review-state validate: non-existent file - exit 1 with error" {
    run "$PROJECT_ROOT/iw" review-state validate "/tmp/does-not-exist-$RANDOM.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"not found"* ]] || [[ "$output" == *"File not found"* ]]
}

@test "review-state validate: stdin mode with valid JSON - exit 0" {
    run bash -c "echo '{\"version\": 1, \"issue_id\": \"IW-1\", \"status\": \"implementing\", \"artifacts\": [], \"last_updated\": \"2026-01-28T12:00:00Z\"}' | '$PROJECT_ROOT/iw' review-state validate --stdin"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "review-state validate: stdin mode with invalid JSON - exit 1" {
    run bash -c "echo '{\"version\": \"bad\"}' | '$PROJECT_ROOT/iw' review-state validate --stdin"
    [ "$status" -eq 1 ]
}

@test "review-state validate: no arguments shows usage error - exit 1" {
    run "$PROJECT_ROOT/iw" review-state validate
    [ "$status" -eq 1 ]
    [[ "$output" == *"No file path"* ]] || [[ "$output" == *"Usage"* ]]
}

# ============================================================================
# WRITE SUBCOMMAND TESTS
# ============================================================================

@test "review-state write: minimal flags creates file and exits 0" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]
    [ -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Review state written"* ]]
}

@test "review-state write: all v2 flags produces correct JSON" {
    run "$PROJECT_ROOT/iw" review-state write \
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
assert d['needs_attention'] == True
assert d['message'] == 'Phase 3 review complete'
assert len(d['artifacts']) == 2
assert d['artifacts'][0]['category'] == 'input'
assert len(d['available_actions']) == 1
assert d['pr_url'] == 'https://github.com/org/repo/pull/99'
"
}

@test "review-state write: --from-stdin validates and writes" {
    local json='{"version":1,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-28T12:00:00Z"}'
    run bash -c "echo '$json' | '$PROJECT_ROOT/iw' review-state write --from-stdin --output '$TEST_TMPDIR/review-state.json'"
    [ "$status" -eq 0 ]
    [ -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Review state written"* ]]
}

@test "review-state write: invalid status in stdin exits 1 and does not write file" {
    local json='{"version":"bad"}'
    run bash -c "echo '$json' | '$PROJECT_ROOT/iw' review-state write --from-stdin --output '$TEST_TMPDIR/review-state.json'"
    [ "$status" -eq 1 ]
    [ ! -f "$TEST_TMPDIR/review-state.json" ]
    [[ "$output" == *"Validation failed"* ]]
}

@test "review-state write: --output flag writes to specified path" {
    local outfile="$TEST_TMPDIR/subdir/nested/state.json"
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-99 \
        --output "$outfile"
    [ "$status" -eq 0 ]
    [ -f "$outfile" ]
}

@test "review-state write: written file passes validation" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --display-text "Implementing" \
        --display-type progress \
        --output "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]

    run "$PROJECT_ROOT/iw" review-state validate "$TEST_TMPDIR/review-state.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

# ============================================================================
# UPDATE SUBCOMMAND TESTS
# ============================================================================

@test "review-state update: scalar field (display-text) updates value" {
    # Create initial state
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","display":{"text":"Old","type":"info"}}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "New" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"updated"* ]]

    # Verify update
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['display']['text'] == 'New'
assert d['display']['type'] == 'info'  # Preserved
"
}

@test "review-state update: partial object update preserves other fields" {
    # Create initial state
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","display":{"text":"Old","subtext":"Subtext","type":"warning"}}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "New" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify only text changed
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['display']['text'] == 'New'
assert d['display']['subtext'] == 'Subtext'  # Preserved
assert d['display']['type'] == 'warning'  # Preserved
"
}

@test "review-state update: replace array replaces all items" {
    # Create initial state with artifacts
    echo '{"version":2,"issue_id":"IW-1","artifacts":[{"label":"Old1","path":"old1.md"},{"label":"Old2","path":"old2.md"}],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --artifact "New1:new1.md" \
        --artifact "New2:new2.md" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify artifacts replaced
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert len(d['artifacts']) == 2
assert d['artifacts'][0]['label'] == 'New1'
assert d['artifacts'][1]['label'] == 'New2'
"
}

@test "review-state update: append to array adds to existing" {
    # Create initial state with one artifact
    echo '{"version":2,"issue_id":"IW-1","artifacts":[{"label":"Existing","path":"existing.md"}],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --append-artifact "New:new.md" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify artifact appended
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert len(d['artifacts']) == 2
assert d['artifacts'][0]['label'] == 'Existing'
assert d['artifacts'][1]['label'] == 'New'
"
}

@test "review-state update: clear array removes all items" {
    # Create initial state with artifacts
    echo '{"version":2,"issue_id":"IW-1","artifacts":[{"label":"Old1","path":"old1.md"},{"label":"Old2","path":"old2.md"}],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --clear-artifacts \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify artifacts cleared
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert len(d['artifacts']) == 0
"
}

@test "review-state update: clear optional field removes it" {
    # Create initial state with message
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","message":"Old message"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --clear-message \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify message removed
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert 'message' not in d
"
}

@test "review-state update: last_updated changed after update" {
    # Create initial state
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "New" \
        --display-type "info" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify last_updated changed
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['last_updated'] != '2026-01-01T12:00:00Z'
"
}

@test "review-state update: git_sha preserved when not provided" {
    # Create initial state with git_sha
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","git_sha":"original123"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "New" \
        --display-type "info" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify git_sha preserved
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['git_sha'] == 'original123'
"
}

@test "review-state update: version and issue_id preserved" {
    # Create initial state
    echo '{"version":2,"issue_id":"IW-42","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-42 \
        --display-text "New" \
        --display-type "info" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Verify version and issue_id preserved
    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['version'] == 2
assert d['issue_id'] == 'IW-42'
"
}

@test "review-state update: file not found - exit 1 with error" {
    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "New" \
        --input "/tmp/does-not-exist-$RANDOM.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"not found"* ]]
}

@test "review-state update: validation failed - exit 1, file not modified" {
    # Create a valid initial state
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"
    cp "$TEST_TMPDIR/state.json" "$TEST_TMPDIR/state-backup.json"

    # Try an update that would make it invalid (display.text without display.type)
    # Actually, our UpdateInput doesn't allow creating invalid states easily
    # So skip this test or design it differently
    skip "Need to design a way to create invalid state through update"
}

@test "review-state update: updated file passes validation" {
    # Create initial state
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "New" \
        --display-type "info" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # Validate the updated file
    run "$PROJECT_ROOT/iw" review-state validate "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

# ============================================================================
# UPDATE --HELP TESTS
# ============================================================================

@test "review-state update --help exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state update --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--status"* ]]
    [[ "$output" == *"--display-text"* ]]
}

@test "review-state update -h exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state update -h
    [ "$status" -eq 0 ]
    [[ "$output" == *"--status"* ]]
    [[ "$output" == *"--display-text"* ]]
}

@test "review-state update --help does not modify review-state.json" {
    # Create initial state file
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"
    local before
    before="$(cat "$TEST_TMPDIR/state.json")"

    run "$PROJECT_ROOT/iw" review-state update --help --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    # File must be identical after --help
    local after
    after="$(cat "$TEST_TMPDIR/state.json")"
    [ "$before" = "$after" ]
}

# ============================================================================
# WRITE --HELP TESTS
# ============================================================================

@test "review-state write --help exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state write --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--status"* ]]
    [[ "$output" == *"--display-text"* ]]
    [[ "$output" == *"--from-stdin"* ]]
}

@test "review-state write -h exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state write -h
    [ "$status" -eq 0 ]
    [[ "$output" == *"--status"* ]]
    [[ "$output" == *"--display-text"* ]]
    [[ "$output" == *"--from-stdin"* ]]
}

@test "review-state write --help does not write any files" {
    run "$PROJECT_ROOT/iw" review-state write --help --output "$TEST_TMPDIR/should-not-exist.json"
    [ "$status" -eq 0 ]
    [ ! -f "$TEST_TMPDIR/should-not-exist.json" ]
}

# ============================================================================
# VALIDATE --HELP TESTS
# ============================================================================

@test "review-state validate --help exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state validate --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"file-path"* ]]
    [[ "$output" == *"--stdin"* ]]
}

@test "review-state validate -h exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state validate -h
    [ "$status" -eq 0 ]
    [[ "$output" == *"file-path"* ]]
    [[ "$output" == *"--stdin"* ]]
}

@test "review-state validate --help does not read any files" {
    run "$PROJECT_ROOT/iw" review-state validate --help "/tmp/does-not-exist-$RANDOM.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"file-path"* ]]
    [[ "$output" == *"--stdin"* ]]
}

# ============================================================================
# DISPATCHER --HELP TESTS
# ============================================================================

@test "review-state --help exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"validate"* ]]
    [[ "$output" == *"write"* ]]
    [[ "$output" == *"update"* ]]
}

@test "review-state -h exits 0 with usage" {
    run "$PROJECT_ROOT/iw" review-state -h
    [ "$status" -eq 0 ]
    [[ "$output" == *"validate"* ]]
    [[ "$output" == *"write"* ]]
    [[ "$output" == *"update"* ]]
}

# ============================================================================
# WRITE --activity / --workflow-type FLAG TESTS
# ============================================================================

@test "review-state write: --activity flag writes activity field" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --activity working \
        --output "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['activity'] == 'working', f\"expected 'working', got {d.get('activity')}\"
"
}

@test "review-state write: --workflow-type flag writes workflow_type field" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --workflow-type agile \
        --output "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['workflow_type'] == 'agile', f\"expected 'agile', got {d.get('workflow_type')}\"
"
}

@test "review-state write: --activity and --workflow-type together produces valid JSON" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --activity waiting \
        --workflow-type diagnostic \
        --output "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['activity'] == 'waiting'
assert d['workflow_type'] == 'diagnostic'
"

    run "$PROJECT_ROOT/iw" review-state validate "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]
}

@test "review-state write: invalid --activity value fails validation" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --activity invalid \
        --output "$TEST_TMPDIR/state.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"activity"* ]]
}

@test "review-state write: invalid --workflow-type value fails validation" {
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --workflow-type invalid \
        --output "$TEST_TMPDIR/state.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"workflow_type"* ]]
}

@test "review-state write --help shows activity and workflow-type flags" {
    run "$PROJECT_ROOT/iw" review-state write --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--activity"* ]]
    [[ "$output" == *"--workflow-type"* ]]
}

# ============================================================================
# UPDATE --activity / --workflow-type FLAG TESTS
# ============================================================================

@test "review-state update: --activity flag sets activity field" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --activity working \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['activity'] == 'working', f\"expected 'working', got {d.get('activity')}\"
"
}

@test "review-state update: --workflow-type flag sets workflow_type field" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --workflow-type waterfall \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['workflow_type'] == 'waterfall', f\"expected 'waterfall', got {d.get('workflow_type')}\"
"
}

@test "review-state update: --clear-activity removes activity field" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","activity":"working"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --clear-activity \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert 'activity' not in d, f\"expected 'activity' absent, found {d.get('activity')}\"
"
}

@test "review-state update: --clear-workflow-type removes workflow_type field" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","workflow_type":"agile"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --clear-workflow-type \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert 'workflow_type' not in d, f\"expected 'workflow_type' absent, found {d.get('workflow_type')}\"
"
}

@test "review-state update: activity and workflow_type preserved when not updated" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z","activity":"waiting","workflow_type":"waterfall"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --message "test" \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 0 ]

    local json
    json="$(cat "$TEST_TMPDIR/state.json")"
    echo "$json" | python3 -c "
import json, sys
d = json.load(sys.stdin)
assert d['activity'] == 'waiting'
assert d['workflow_type'] == 'waterfall'
"
}

@test "review-state update: invalid --activity value fails validation" {
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --activity bogus \
        --input "$TEST_TMPDIR/state.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"activity"* ]]
}

@test "review-state update --help shows activity and workflow-type flags" {
    run "$PROJECT_ROOT/iw" review-state update --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--activity"* ]]
    [[ "$output" == *"--clear-activity"* ]]
    [[ "$output" == *"--workflow-type"* ]]
    [[ "$output" == *"--clear-workflow-type"* ]]
}

# ============================================================================
# UPDATE --commit FLAG TESTS
# ============================================================================

setup_git_repo() {
    local dir="$1"
    git -C "$dir" init -q
    git -C "$dir" config user.email "test@test.com"
    git -C "$dir" config user.name "Test"
    # Create an initial commit so HEAD exists
    git -C "$dir" commit -q --allow-empty -m "initial"
}

@test "review-state update: --commit stages and commits review-state.json (clean tree after)" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$state_file"
    git -C "$TEST_TMPDIR" add "$state_file"
    git -C "$TEST_TMPDIR" commit -q -m "add state"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --status implementing \
        --input "$state_file" \
        --commit
    [ "$status" -eq 0 ]

    # Working tree should be clean (file committed)
    run git -C "$TEST_TMPDIR" status --porcelain
    [ "$output" = "" ]
}

@test "review-state update: --commit message contains issue ID and status" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"
    echo '{"version":2,"issue_id":"IW-42","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$state_file"
    git -C "$TEST_TMPDIR" add "$state_file"
    git -C "$TEST_TMPDIR" commit -q -m "add state"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-42 \
        --status done \
        --input "$state_file" \
        --commit
    [ "$status" -eq 0 ]

    local msg
    msg="$(git -C "$TEST_TMPDIR" log -1 --format=%s)"
    [[ "$msg" == *"IW-42"* ]]
    [[ "$msg" == *"done"* ]]
}

@test "review-state update: --commit without --status uses generic commit message" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"
    echo '{"version":2,"issue_id":"IW-10","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$state_file"
    git -C "$TEST_TMPDIR" add "$state_file"
    git -C "$TEST_TMPDIR" commit -q -m "add state"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-10 \
        --message "hello" \
        --input "$state_file" \
        --commit
    [ "$status" -eq 0 ]

    local msg
    msg="$(git -C "$TEST_TMPDIR" log -1 --format=%s)"
    [[ "$msg" == *"IW-10"* ]]
    [[ "$msg" != *" to "* ]]
}

@test "review-state update: without --commit, file is written but not committed" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$state_file"
    git -C "$TEST_TMPDIR" add "$state_file"
    git -C "$TEST_TMPDIR" commit -q -m "add state"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --status implementing \
        --input "$state_file"
    [ "$status" -eq 0 ]

    # File should be modified but not committed
    run git -C "$TEST_TMPDIR" status --porcelain "$state_file"
    [[ "$output" != "" ]]
}

@test "review-state update: --help includes --commit flag" {
    run "$PROJECT_ROOT/iw" review-state update --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--commit"* ]]
}

# ============================================================================
# WRITE --commit FLAG TESTS
# ============================================================================

@test "review-state write: --commit stages and commits the written file (clean tree after)" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --status implementing \
        --output "$state_file" \
        --commit
    [ "$status" -eq 0 ]

    # Working tree should be clean
    run git -C "$TEST_TMPDIR" status --porcelain
    [ "$output" = "" ]
}

@test "review-state write: without --commit, file is written but not committed" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --status implementing \
        --output "$state_file"
    [ "$status" -eq 0 ]

    # File should exist but not be committed
    [ -f "$state_file" ]
    run git -C "$TEST_TMPDIR" status --porcelain "$state_file"
    [[ "$output" != "" ]]
}

@test "review-state write: --from-stdin --commit stages and commits (clean tree after)" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"
    local json='{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-28T12:00:00Z"}'

    run bash -c "echo '$json' | '$PROJECT_ROOT/iw' review-state write --from-stdin --commit --issue-id IW-1 --output '$state_file'"
    [ "$status" -eq 0 ]

    # Working tree should be clean
    run git -C "$TEST_TMPDIR" status --porcelain
    [ "$output" = "" ]
}

@test "review-state write: --commit message contains issue ID and status" {
    setup_git_repo "$TEST_TMPDIR"
    local state_file="$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-55 \
        --status reviewing \
        --output "$state_file" \
        --commit
    [ "$status" -eq 0 ]

    local msg
    msg="$(git -C "$TEST_TMPDIR" log -1 --format=%s)"
    [[ "$msg" == *"IW-55"* ]]
    [[ "$msg" == *"reviewing"* ]]
}

@test "review-state write: --help includes --commit flag" {
    run "$PROJECT_ROOT/iw" review-state write --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--commit"* ]]
}
