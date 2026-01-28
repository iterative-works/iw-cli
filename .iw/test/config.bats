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

@test "config --json outputs valid JSON with GitHub config" {
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
    run "$PROJECT_ROOT/iw" config --json

    # Assert
    [ "$status" -eq 0 ]
    # Validate JSON using jq (extract last line which is the JSON output)
    echo "$output" | tail -1 | jq . > /dev/null 2>&1
    [ $? -eq 0 ]
}

@test "config --json includes trackerType field with correct value" {
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
    run "$PROJECT_ROOT/iw" config --json

    # Assert
    [ "$status" -eq 0 ]
    # Check that trackerType field exists in nested structure (extract last line)
    trackerType=$(echo "$output" | tail -1 | jq -r '.tracker.trackerType')
    [ "$trackerType" = "GitHub" ]
}

@test "config --json includes repository field" {
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
    run "$PROJECT_ROOT/iw" config --json

    # Assert
    [ "$status" -eq 0 ]
    # Check that repository field exists in nested structure (extract last line)
    repository=$(echo "$output" | tail -1 | jq -r '.tracker.repository')
    [ "$repository" = "iterative-works/iw-cli" ]
}

@test "config --json without config file returns error" {
    # No config file created - directory is empty

    # Run command
    run "$PROJECT_ROOT/iw" config --json

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration not found"* ]]
}

@test "config --json with Linear config outputs valid JSON" {
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
    run "$PROJECT_ROOT/iw" config --json

    # Assert
    [ "$status" -eq 0 ]
    # Validate JSON using jq (extract last line)
    echo "$output" | tail -1 | jq . > /dev/null 2>&1
    [ $? -eq 0 ]
    # Check that trackerType is Linear in nested structure
    trackerType=$(echo "$output" | tail -1 | jq -r '.tracker.trackerType')
    [ "$trackerType" = "Linear" ]
}

@test "config with no arguments shows usage" {
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

    # Run command with no args
    run "$PROJECT_ROOT/iw" config

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"iw config get <field>"* ]]
    [[ "$output" == *"iw config --json"* ]]
}

@test "config get without field shows missing argument error" {
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

    # Run command with get but no field
    run "$PROJECT_ROOT/iw" config get

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Missing required argument: <field>"* ]]
    [[ "$output" == *"Usage:"* ]]
}

@test "config --invalid shows unknown option error" {
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

    # Run command with invalid option
    run "$PROJECT_ROOT/iw" config --invalid

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Unknown option: --invalid"* ]]
    [[ "$output" == *"Usage:"* ]]
}

@test "config usage includes command descriptions" {
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

    # Run command with no args to trigger usage
    run "$PROJECT_ROOT/iw" config

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"iw config - Query project configuration"* ]]
    [[ "$output" == *"Get a specific configuration field"* ]]
    [[ "$output" == *"Export full configuration as JSON"* ]]
}

@test "config usage includes available fields list" {
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

    # Run command with no args to trigger usage
    run "$PROJECT_ROOT/iw" config

    # Assert
    [ "$status" -eq 1 ]
    [[ "$output" == *"Available fields"* ]]
    # Check for nested field names
    [[ "$output" == *"tracker.trackerType"* ]]
    [[ "$output" == *"tracker.team"* ]]
    [[ "$output" == *"project.name"* ]]
    [[ "$output" == *"tracker.repository"* ]]
    [[ "$output" == *"tracker.teamPrefix"* ]]
    [[ "$output" == *"version"* ]]
    [[ "$output" == *"tracker.baseUrl"* ]]
    # Check that aliases are mentioned
    [[ "$output" == *"Aliases"* ]]
}
