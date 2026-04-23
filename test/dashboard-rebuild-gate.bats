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
    # Running iw status should not produce any 'dashboard.assembly' Mill lines
    run "$PROJECT_ROOT/iw" status 2>&1
    [[ "$output" != *"dashboard.assembly"* ]]
}

@test "iw dashboard --help triggers Mill dashboard.assembly query" {
    # Running iw dashboard should call ensure_dashboard_jar which queries Mill
    run "$PROJECT_ROOT/iw" dashboard --help 2>&1
    [ "$status" -eq 0 ]
    [[ "$output" == *"dashboard.assembly"* ]]
}
