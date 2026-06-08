#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw review-state (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed validate/write/update scenarios live in core/test/ReviewStateHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_TMPDIR="$(mktemp -d)"
    cd "$TEST_TMPDIR"
}

teardown() {
    cd /
    rm -rf "$TEST_TMPDIR"
}

setup_git_repo() {
    git -C "$1" init -q
    git -C "$1" config user.email "test@example.com"
    git -C "$1" config user.name "Test User"
}

@test "review-state write then validate round-trip" {
    local state_file="$TEST_TMPDIR/state.json"

    run "$PROJECT_ROOT/iw" review-state write \
        --issue-id IW-1 \
        --display-text "Implementing" \
        --display-type progress \
        --output "$state_file"
    [ "$status" -eq 0 ]

    run "$PROJECT_ROOT/iw" review-state validate "$state_file"
    [ "$status" -eq 0 ]
}

@test "review-state update --commit stages, commits, leaves a clean tree" {
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
}
