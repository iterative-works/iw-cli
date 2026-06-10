#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw dashboard (full round-trip through scala-cli + live adapters)
# PURPOSE: Launcher decision logic covered by core/test/DashboardHarnessTest.scala;
# PURPOSE: dashboard server HTTP behavior covered by dashboard-dev-mode.bats and dashboard-dev-gate.bats

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "iw dashboard --help exits 0 and lists expected flags" {
    run "$PROJECT_ROOT/iw" dashboard --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--state-path"* ]]
    [[ "$output" == *"--sample-data"* ]]
    [[ "$output" == *"--dev"* ]]
}
