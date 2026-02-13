#!/usr/bin/env bats
# PURPOSE: Integration tests for iw-run launcher and bootstrap
# PURPOSE: Tests release package structure and offline operation

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Skip if release package not built
    if [ ! -f "$PROJECT_ROOT/release/iw-cli-0.1.0-dev.tar.gz" ]; then
        skip "Release package not found (run package-release.sh first)"
    fi

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "iw-run lists commands from installation directory" {
    # Setup: extract release package to simulate installation
    tar -xzf "$PROJECT_ROOT/release/iw-cli-0.1.0-dev.tar.gz"
    cd iw-cli-0.1.0-dev

    # Run iw-run --list
    run ./iw-run --list

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Available commands"* ]]
    [[ "$output" == *"Command: version"* ]]
    [[ "$output" == *"Command: init"* ]]
}

@test "iw-run --bootstrap pre-compiles successfully" {
    # Setup: extract release package
    tar -xzf "$PROJECT_ROOT/release/iw-cli-0.1.0-dev.tar.gz"
    cd iw-cli-0.1.0-dev

    # Run bootstrap
    run timeout 60 ./iw-run --bootstrap

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Bootstrapping"* ]]
    [[ "$output" == *"Bootstrap complete"* ]]
}

@test "iw-run executes commands from installation directory" {
    # Setup: extract release package and create a test project
    tar -xzf "$PROJECT_ROOT/release/iw-cli-0.1.0-dev.tar.gz"
    mkdir test-project
    cd test-project
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run version command via iw-run
    run "$TEST_DIR/iw-cli-0.1.0-dev/iw-run" version

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"iw-cli"* ]]
}

@test "release package contains required structure" {
    # Extract and verify structure
    tar -xzf "$PROJECT_ROOT/release/iw-cli-0.1.0-dev.tar.gz"

    # Assert directory structure
    [ -d "iw-cli-0.1.0-dev" ]
    [ -f "iw-cli-0.1.0-dev/iw-run" ]
    [ -d "iw-cli-0.1.0-dev/commands" ]
    [ -d "iw-cli-0.1.0-dev/core" ]

    # Assert iw-run is executable
    [ -x "iw-cli-0.1.0-dev/iw-run" ]

    # Assert core files exist
    [ -f "iw-cli-0.1.0-dev/core/Config.scala" ]
    [ -f "iw-cli-0.1.0-dev/core/project.scala" ]

    # Assert command files exist
    [ -f "iw-cli-0.1.0-dev/commands/version.scala" ]
    [ -f "iw-cli-0.1.0-dev/commands/init.scala" ]
}
