#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw init (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/InitHarnessTest.scala

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

@test "init: writes config.conf with --tracker=linear --team=TEST" {
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=TEST

    [ "$status" -eq 0 ]
    [ -f ".iw/config.conf" ]
    grep -q 'type = linear' .iw/config.conf
    grep -q 'team = "TEST"\|team = TEST' .iw/config.conf
}
