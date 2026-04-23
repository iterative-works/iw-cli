#!/usr/bin/env bats
# PURPOSE: Verify that iw dashboard launches via java -jar with the expected CLI surface
# PURPOSE: Checks that expected flags are present and help exits 0

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    rm -rf "$TEST_DIR"
}

@test "iw dashboard --help exits 0 and lists expected flags" {
    run "$PROJECT_ROOT/iw" dashboard --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--state-path"* ]]
    [[ "$output" == *"--sample-data"* ]]
    [[ "$output" == *"--dev"* ]]
}
