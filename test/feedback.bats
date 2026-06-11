#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw feedback (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/FeedbackHarnessTest.scala
#
# NOTE: The feedback command always creates issues in the iw-cli repository
# (iterative-works/iw-cli) regardless of the current directory or local config.

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

@test "feedback: --help prints usage banner without touching GitHub" {
    run "$PROJECT_ROOT/iw" feedback --help

    [ "$status" -eq 0 ]
    [[ "$output" == *"Submit feedback to the iw-cli team"* ]]
    [[ "$output" == *"Usage:"* ]]
}
