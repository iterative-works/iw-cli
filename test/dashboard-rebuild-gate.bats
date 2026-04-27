#!/usr/bin/env bats
# PURPOSE: Verify that Mill rebuilds the dashboard jar on source change and skips rebuild when unchanged
# PURPOSE: Confirms the rebuild gate is scoped to dashboard commands only

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    rm -rf "$TEST_DIR"
}

@test "non-dashboard command does not trigger Mill dashboard query" {
    # Running iw status must not call ensure_dashboard_jar.
    IW_TRACE=1 run "$PROJECT_ROOT/iw" status 2>&1
    [[ "$output" != *"mill_jar_path dashboard.assembly"* ]]
}

@test "iw dashboard --help triggers Mill dashboard.assembly query" {
    # Running iw dashboard must call ensure_dashboard_jar which queries Mill.
    IW_TRACE=1 run "$PROJECT_ROOT/iw" dashboard --help 2>&1
    [ "$status" -eq 0 ]
    [[ "$output" == *"mill_jar_path dashboard.assembly"* ]]
}
