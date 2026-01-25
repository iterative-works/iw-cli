#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw issue create command
# PURPOSE: Tests help display, argument validation, and basic command structure

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "issue create without arguments shows help and exits 1" {
    # Run with no arguments
    run "$PROJECT_ROOT/iw" issue create

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create --help shows usage and exits 0" {
    # Run with --help flag
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create -h shows usage and exits 0" {
    # Run with -h short flag
    run "$PROJECT_ROOT/iw" issue create -h

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
}

@test "issue create help text contains --title flag" {
    # Run with --help to get help text
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert help contains --title
    [ "$status" -eq 0 ]
    [[ "$output" == *"--title"* ]]
}

@test "issue create help text contains --description flag" {
    # Run with --help to get help text
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert help contains --description
    [ "$status" -eq 0 ]
    [[ "$output" == *"--description"* ]]
}

@test "issue create help text contains usage examples" {
    # Run with --help to get help text
    run "$PROJECT_ROOT/iw" issue create --help

    # Assert help contains Examples section
    [ "$status" -eq 0 ]
    [[ "$output" == *"Examples:"* ]]
}
