#!/usr/bin/env bats
# PURPOSE: Integration tests for iw init command
# PURPOSE: Tests non-interactive mode with --tracker and --team flags

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

@test "init creates config with --tracker and --team flags" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with non-interactive flags
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]

    # Assert config file exists and has correct content
    [ -f ".iw/config.conf" ]
    grep -q "type = linear" .iw/config.conf
    grep -q "team = IWLE" .iw/config.conf
}

@test "init creates config with youtrack tracker" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with youtrack tracker
    run "$PROJECT_ROOT/iw" init --tracker=youtrack --team=TEST

    # Assert success
    [ "$status" -eq 0 ]

    # Assert config has youtrack
    grep -q "type = youtrack" .iw/config.conf
    grep -q "team = TEST" .iw/config.conf
}

@test "init fails outside git repository" {
    # No git init - just a plain directory

    # Run init
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Not in a git repository"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init fails when config already exists" {
    # Setup: create a git repo with existing config
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    mkdir -p .iw
    echo "existing config" > .iw/config.conf

    # Run init without --force
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration already exists"* ]]
    [[ "$output" == *"--force"* ]]

    # Assert config was not overwritten
    grep -q "existing config" .iw/config.conf
}

@test "init --force overwrites existing config" {
    # Setup: create a git repo with existing config
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
    mkdir -p .iw
    echo "old config" > .iw/config.conf

    # Run init with --force
    run "$PROJECT_ROOT/iw" init --force --tracker=linear --team=NEWTEAM

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Configuration created"* ]]

    # Assert config was overwritten with new content
    grep -q "type = linear" .iw/config.conf
    grep -q "team = NEWTEAM" .iw/config.conf
    ! grep -q "old config" .iw/config.conf
}

@test "init fails with invalid tracker type" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with invalid tracker
    run "$PROJECT_ROOT/iw" init --tracker=invalid --team=IWLE

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid tracker type"* ]]
    [[ "$output" == *"linear"* ]]
    [[ "$output" == *"youtrack"* ]]

    # Assert no config created
    [ ! -f ".iw/config.conf" ]
}

@test "init shows LINEAR_API_TOKEN hint for linear tracker" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with linear tracker
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert output contains Linear token hint
    [ "$status" -eq 0 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
}

@test "init shows YOUTRACK_API_TOKEN hint for youtrack tracker" {
    # Setup: create a git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init with youtrack tracker
    run "$PROJECT_ROOT/iw" init --tracker=youtrack --team=TEST

    # Assert output contains YouTrack token hint
    [ "$status" -eq 0 ]
    [[ "$output" == *"YOUTRACK_API_TOKEN"* ]]
}

@test "init uses directory name as project name" {
    # Setup: create a git repo in a directory with known name
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run init
    run "$PROJECT_ROOT/iw" init --tracker=linear --team=IWLE

    # Assert config has project name (will be the temp dir basename)
    [ "$status" -eq 0 ]
    [ -f ".iw/config.conf" ]
    grep -q "name = " .iw/config.conf
}
