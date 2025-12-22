#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw feedback command
# PURPOSE: Tests feedback submission, error handling, and help text
#
# NOTE: Tests that create real Linear issues use [TEST] prefix for identification.
# These test issues should be periodically cleaned up manually in Linear:
#   1. Go to Linear > IWLE team > Issues
#   2. Search for "[TEST]" in issue titles
#   3. Select and archive/delete test issues
# Consider running live API tests sparingly to avoid issue accumulation.

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup_file() {
    if [ -n "$LINEAR_API_TOKEN" ] && [ -n "$ENABLE_LIVE_API_TESTS" ]; then
        echo "⚠️  WARNING: Live API tests enabled - real Linear issues will be created" >&3
    fi
}

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "feedback without LINEAR_API_TOKEN fails" {
    # Run without token
    unset LINEAR_API_TOKEN
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "feedback without title fails" {
    # Setup: ensure token is available (even if invalid, just for parsing test)
    export LINEAR_API_TOKEN="dummy-token"

    # Run with only --description flag
    run "$PROJECT_ROOT/iw" feedback --description "Only description"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Title is required"* ]]
}

@test "feedback with invalid type fails" {
    # Setup: ensure token is available
    export LINEAR_API_TOKEN="dummy-token"

    # Run with invalid --type value
    run "$PROJECT_ROOT/iw" feedback "Test issue" --type invalid

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Type must be"* ]]
}

@test "feedback creates issue successfully" {
    # Skip if no real token available
    if [ -z "$LINEAR_API_TOKEN" ] || [ -z "$ENABLE_LIVE_API_TESTS" ]; then
        skip "Live API tests disabled. Set LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 to enable."
    fi

    # Create issue with [TEST] prefix for easy identification
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E test issue from feedback command"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue ID:"* ]]
    [[ "$output" == *"URL:"* ]]
}

@test "feedback with description creates issue" {
    # Skip if no real token available
    if [ -z "$LINEAR_API_TOKEN" ] || [ -z "$ENABLE_LIVE_API_TESTS" ]; then
        skip "Live API tests disabled. Set LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 to enable."
    fi

    # Create issue with description
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E test with description" --description "This is a test description from E2E tests"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue ID:"* ]]
    [[ "$output" == *"URL:"* ]]
}

@test "feedback with bug type creates issue" {
    # Skip if no real token available
    if [ -z "$LINEAR_API_TOKEN" ] || [ -z "$ENABLE_LIVE_API_TESTS" ]; then
        skip "Live API tests disabled. Set LINEAR_API_TOKEN and ENABLE_LIVE_API_TESTS=1 to enable."
    fi

    # Create bug issue
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E test bug report" --type bug --description "Test bug description"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "feedback --help shows usage" {
    # Run with --help flag
    run "$PROJECT_ROOT/iw" feedback --help

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"--description"* ]]
    [[ "$output" == *"--type"* ]]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "feedback -h shows usage" {
    # Run with -h short flag
    run "$PROJECT_ROOT/iw" feedback -h

    # Assert success - same output as --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"--description"* ]]
    [[ "$output" == *"--type"* ]]
}
