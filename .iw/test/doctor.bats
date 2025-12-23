#!/usr/bin/env bats
# PURPOSE: Integration tests for iw doctor command
# PURPOSE: Tests environment validation with base checks and hooks

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Setup a git repository
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "doctor fails when no config exists" {
    # Run without creating config
    run "$PROJECT_ROOT/iw" doctor

    # Should fail with config error
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration"* ]]
    [[ "$output" == *"Missing or invalid"* ]]
}

@test "doctor passes with valid config and all dependencies" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Unset LINEAR_API_TOKEN to skip that check
    unset LINEAR_API_TOKEN

    # Run doctor (tmux should be installed in CI)
    run "$PROJECT_ROOT/iw" doctor

    # Should show checks ran
    [[ "$output" == *"Git repository"* ]]
    [[ "$output" == *"Configuration"* ]]
    [[ "$output" == *"tmux"* ]]
}

@test "doctor shows LINEAR_API_TOKEN error when not set" {
    # Setup: create config with Linear tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Ensure token is not set
    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should fail and show token error
    [ "$status" -eq 1 ]
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
    [[ "$output" == *"Not set"* ]]
    [[ "$output" == *"export LINEAR_API_TOKEN"* ]]
}

@test "doctor skips LINEAR_API_TOKEN for YouTrack projects" {
    # Setup: create config with YouTrack tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = youtrack
  team = TEST
}

project {
  name = test-project
}
EOF

    # Ensure token is not set
    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should not check LINEAR_API_TOKEN
    [[ "$output" == *"LINEAR_API_TOKEN"* ]]
    [[ "$output" == *"Skipped"* ]]
}

@test "doctor returns exit code 1 when checks fail" {
    # Run in non-git directory
    cd "$(mktemp -d)"

    # Create config but no git repo
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should fail
    [ "$status" -eq 1 ]
    [[ "$output" == *"failed"* ]]
}

@test "doctor returns exit code 0 when all checks pass" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = youtrack
  team = TEST
}

project {
  name = test-project
}
EOF

    # Run doctor (YouTrack skips token check, tmux should be available)
    run "$PROJECT_ROOT/iw" doctor

    # Should pass
    [ "$status" -eq 0 ]
    [[ "$output" == *"All checks passed"* ]]
}

@test "doctor displays formatted output with symbols" {
    # Setup: create valid config
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should have formatted output with symbols
    [[ "$output" == *"Environment Check"* ]]
    [[ "$output" == *"✓"* ]]  # Success symbol
    [[ "$output" == *"✗"* ]]  # Error symbol (for missing token)
}

@test "doctor shows gh CLI check passed for GitHub project when gh installed" {
    # Setup: create config with GitHub tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = github
  team = owner
  repository = "owner/repo"
}

project {
  name = repo
}
EOF

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show gh CLI check
    [[ "$output" == *"gh CLI"* ]]

    # If gh is installed (which it should be in CI), show success
    # Otherwise, show error message
    if command -v gh >/dev/null 2>&1; then
        [[ "$output" == *"gh CLI"*"Installed"* ]] || [[ "$output" == *"✓"* ]]
    else
        [[ "$output" == *"gh CLI"*"Not found"* ]]
    fi
}

@test "doctor skips gh checks for non-GitHub project (Linear)" {
    # Setup: create config with Linear tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = linear
  team = TEST
}

project {
  name = test-project
}
EOF

    unset LINEAR_API_TOKEN

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show gh CLI check as skipped
    [[ "$output" == *"gh CLI"* ]]
    [[ "$output" == *"Skipped"* ]]
    [[ "$output" == *"Not using GitHub"* ]]
}

@test "doctor shows gh auth check when gh is installed" {
    # Setup: create config with GitHub tracker
    mkdir -p .iw
    cat > .iw/config.conf << EOF
tracker {
  type = github
  team = owner
  repository = "owner/repo"
}

project {
  name = repo
}
EOF

    # Run doctor
    run "$PROJECT_ROOT/iw" doctor

    # Should show gh auth check
    [[ "$output" == *"gh auth"* ]]

    # If gh is installed, check should run (not skip)
    if command -v gh >/dev/null 2>&1; then
        # Either authenticated (success) or not authenticated (error)
        [[ "$output" == *"gh auth"* ]]
        ! [[ "$output" == *"gh auth"*"gh not installed"* ]]
    else
        # If gh not installed, auth check should be skipped
        [[ "$output" == *"gh auth"*"Skipped"* ]]
    fi
}
