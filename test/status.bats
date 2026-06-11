#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw status (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/StatusHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md
    git commit -q -m "Initial commit"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "status with server disabled: surfaces the error" {
    git checkout -q -b IWLE-123
    run "$PROJECT_ROOT/iw" status

    [ "$status" -eq 1 ]
    [[ "$output" == *"Server communication is disabled"* ]]
}
