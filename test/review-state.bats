#!/usr/bin/env bats
# PURPOSE: E2E smoke tests for iw review-state command (one per subcommand)
# PURPOSE: Parser, builder, updater, and validator logic is covered by munit;
# PURPOSE: this file only proves iw -> script -> core -> file system wiring works.

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

setup_git_repo() {
    local dir="$1"
    git -C "$dir" init -q
    git -C "$dir" config user.email "test@test.com"
    git -C "$dir" config user.name "Test"
    git -C "$dir" commit -q --allow-empty -m "initial"
}

@test "review-state validate: valid file on disk - exit 0" {
    run "$PROJECT_ROOT/iw" review-state validate \
        "$PROJECT_ROOT/core/test/resources/review-state/valid-minimal.json"
    [ "$status" -eq 0 ]
    [[ "$output" == *"valid"* ]]
}

@test "review-state validate: non-existent file - exit 1" {
    run "$PROJECT_ROOT/iw" review-state validate "/tmp/does-not-exist-$RANDOM.json"
    [ "$status" -eq 1 ]
    [[ "$output" == *"not found"* ]]
}

@test "review-state write: flags produce a file that round-trips through validate" {
    local outfile="$TEST_TMPDIR/state.json"
    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-42 \
        --status implementing \
        --display-text "Implementing" \
        --display-type progress \
        --badge "TDD:success" \
        --artifact "Analysis:analysis.md=input" \
        --output "$outfile"
    [ "$status" -eq 0 ]
    [ -f "$outfile" ]

    run "$PROJECT_ROOT/iw" review-state validate "$outfile"
    [ "$status" -eq 0 ]
}

@test "review-state update: edits existing file and round-trips through validate" {
    local state_file="$TEST_TMPDIR/state.json"
    echo '{"version":2,"issue_id":"IW-1","artifacts":[],"last_updated":"2026-01-01T12:00:00Z"}' > "$state_file"

    run "$PROJECT_ROOT/iw" review-state update \
        --issue-id IW-1 \
        --display-text "Updated" \
        --display-type info \
        --input "$state_file"
    [ "$status" -eq 0 ]

    run "$PROJECT_ROOT/iw" review-state validate "$state_file"
    [ "$status" -eq 0 ]
}

@test "review-state update --commit: stages and commits the file (clean tree after)" {
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

    run git -C "$TEST_TMPDIR" status --porcelain
    [ "$output" = "" ]

    local msg
    msg="$(git -C "$TEST_TMPDIR" log -1 --format=%s)"
    [[ "$msg" == *"IW-42"* ]]
    [[ "$msg" == *"done"* ]]
}

@test "review-state --help: dispatcher prints subcommand list" {
    run "$PROJECT_ROOT/iw" review-state --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"validate"* ]]
    [[ "$output" == *"write"* ]]
    [[ "$output" == *"update"* ]]
}
