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

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "feedback without config file fails" {
    # Run without config
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration file not found"* ]]
}

@test "feedback with Linear tracker without LINEAR_API_TOKEN fails" {
    # Create Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Run without token
    unset LINEAR_API_TOKEN
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "feedback without title fails" {
    # Create Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Setup: ensure token is available (even if invalid, just for parsing test)
    export LINEAR_API_TOKEN="dummy-token"

    # Run with only --description flag
    run "$PROJECT_ROOT/iw" feedback --description "Only description"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Title is required"* ]]
}

@test "feedback with invalid type fails" {
    # Create Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Setup: ensure token is available
    export LINEAR_API_TOKEN="dummy-token"

    # Run with invalid --type value
    run "$PROJECT_ROOT/iw" feedback "Test issue" --type invalid

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Type must be"* ]]
}

@test "feedback creates issue successfully with Linear" {
    # Skip if no real token available
    if [ -z "$LINEAR_API_TOKEN" ]; then
        skip "LINEAR_API_TOKEN not set, skipping live API test"
    fi

    # Create Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Create issue with [TEST] prefix for easy identification
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E test issue from feedback command"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue:"* ]]
    [[ "$output" == *"URL:"* ]]
}

@test "feedback with description creates issue with Linear" {
    # Skip if no real token available
    if [ -z "$LINEAR_API_TOKEN" ]; then
        skip "LINEAR_API_TOKEN not set, skipping live API test"
    fi

    # Create Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Create issue with description
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E test with description" --description "This is a test description from E2E tests"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue:"* ]]
    [[ "$output" == *"URL:"* ]]
}

@test "feedback with bug type creates issue with Linear" {
    # Skip if no real token available
    if [ -z "$LINEAR_API_TOKEN" ]; then
        skip "LINEAR_API_TOKEN not set, skipping live API test"
    fi

    # Create Linear config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

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

# GitHub Tracker Tests

@test "feedback with GitHub tracker without repository fails" {
    # Create GitHub config without repository
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
}
project {
  name = test-project
}
EOF

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure - config loading should fail
    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to read config"* || "$output" == *"repository required"* ]]
}

@test "feedback with GitHub tracker and missing repository in config fails" {
    # Create valid GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "iterative-works/iw-cli"
}
project {
  name = test-project
}
EOF

    # Mock gh command that returns error (gh not found or not authenticated)
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
echo "gh: command not found" >&2
exit 127
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Failed to create issue"* ]]
}

@test "feedback with GitHub tracker creates issue successfully" {
    # Skip if no real gh CLI or test repo not configured
    if ! command -v gh &> /dev/null || [ -z "$IW_TEST_GITHUB_REPO" ]; then
        skip "gh CLI not available or IW_TEST_GITHUB_REPO not set, skipping GitHub test"
    fi

    # Create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "$IW_TEST_GITHUB_REPO"
}
project {
  name = test-project
}
EOF

    # Create issue
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E GitHub feedback test"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue:"* ]]
    [[ "$output" == *"URL:"* ]]
}

@test "feedback with GitHub tracker and bug type applies bug label" {
    # Skip if no real gh CLI or test repo not configured
    if ! command -v gh &> /dev/null || [ -z "$IW_TEST_GITHUB_REPO" ]; then
        skip "gh CLI not available or IW_TEST_GITHUB_REPO not set, skipping GitHub test"
    fi

    # Create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "$IW_TEST_GITHUB_REPO"
}
project {
  name = test-project
}
EOF

    # Create bug issue
    run "$PROJECT_ROOT/iw" feedback "[TEST] E2E GitHub bug report" --type bug --description "Test bug"

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
}

@test "feedback with GitHub tracker shows issue number in output" {
    # Create GitHub config with mock gh
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = github
  repository = "owner/repo"
}
project {
  name = test-project
}
EOF

    # Mock gh command that returns success
    mkdir -p bin
    cat > bin/gh <<'SCRIPT'
#!/bin/bash
# Mock successful gh issue create response
echo '{"number": 123, "url": "https://github.com/owner/repo/issues/123"}'
exit 0
SCRIPT
    chmod +x bin/gh
    export PATH="$TEST_DIR/bin:$PATH"

    # Run feedback command
    run "$PROJECT_ROOT/iw" feedback "Test issue"

    # Assert success with issue number
    [ "$status" -eq 0 ]
    [[ "$output" == *"Feedback submitted successfully"* ]]
    [[ "$output" == *"Issue: #123"* ]]
    [[ "$output" == *"URL: https://github.com/owner/repo/issues/123"* ]]
}
