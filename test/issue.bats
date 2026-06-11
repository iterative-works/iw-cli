#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw issue (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/IssueHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "issue: returns config-not-found error before reaching tracker" {
    run "$PROJECT_ROOT/iw" issue TEST-1

    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration file not found"* ]]
}
