#!/usr/bin/env bats
# PURPOSE: Integration tests for iw config command
# PURPOSE: Tests querying configuration values from .iw/config.conf

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo (required for iw commands)
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "config get trackerType returns tracker type for GitHub config" {
    # Setup: create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get trackerType

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"GitHub" ]]
}

@test "config get repository returns repository value" {
    # Setup: create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get repository

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"iterative-works/iw-cli"* ]]
}

@test "config get teamPrefix returns team prefix value" {
    # Setup: create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get teamPrefix

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"IW"* ]]
}

@test "config get projectName returns project name" {
    # Setup: create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get projectName

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"test-project"* ]]
}

@test "config get nonexistent returns error with exit code 1" {
    # Setup: create GitHub config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get nonexistent

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Unknown configuration field"* ]]
}

@test "config get youtrackBaseUrl when unset returns error" {
    # Setup: create GitHub config (without youtrackBaseUrl)
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = github
  repository = "iterative-works/iw-cli"
  teamPrefix = "IW"
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get youtrackBaseUrl

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"not set"* ]]
}

@test "config get trackerType without config file returns error" {
    # No config file created - directory is empty

    # Run command
    run "$PROJECT_ROOT/iw" config get trackerType

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration not found"* ]]
}

@test "config get trackerType with Linear tracker config" {
    # Setup: create Linear config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get trackerType

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"Linear"* ]]
}

@test "config get team with Linear tracker returns team value" {
    # Setup: create Linear config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = linear
  team = IWLE
}
project {
  name = test-project
}
EOF

    # Run command
    run "$PROJECT_ROOT/iw" config get team

    # Assert
    [ "$status" -eq 0 ]
    [[ "$output" == *"IWLE"* ]]
}
