#!/usr/bin/env bats
# PURPOSE: E2E tests for claude-sync command template path resolution
# PURPOSE: Verifies IW_COMMANDS_DIR and fallback to os.pwd work correctly

setup() {
    # Create a temp directory for test
    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Initialize git repo
    git init --quiet
    git config user.email "test@test.com"
    git config user.name "Test User"

    # Copy the iw-run script (this is the actual script we're testing)
    cp "$BATS_TEST_DIRNAME/../../iw-run" "$TEST_DIR/iw-run"
    chmod +x "$TEST_DIR/iw-run"

    # Copy shared commands and core (including subdirectories, excluding test/)
    mkdir -p .iw-install/commands
    cp -r "$BATS_TEST_DIRNAME/../commands"/*.scala .iw-install/commands/
    cp -r "$BATS_TEST_DIRNAME/../core" .iw-install/
    rm -rf .iw-install/core/test

    # Copy scripts directory with template
    mkdir -p .iw-install/scripts
    cp -r "$BATS_TEST_DIRNAME/../scripts"/* .iw-install/scripts/

    # Set environment variables to point to our test installation
    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
    export IW_PROJECT_DIR="$TEST_DIR"

    # Create minimal config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
tracker {
  type = linear
  team = TEST
}
project {
  name = test-project
}
EOF

    # Mock claude command to verify the script runs correctly
    cat > "$TEST_DIR/claude" << 'EOF'
#!/bin/bash
# Mock claude command that succeeds
echo "Mock Claude running..."
exit 0
EOF
    chmod +x "$TEST_DIR/claude"
    export PATH="$TEST_DIR:$PATH"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "claude-sync finds template from IW_COMMANDS_DIR when set" {
    # This simulates running from an external project that has iw-cli installed
    # IW_COMMANDS_DIR is already set to the installation directory

    run ./iw-run claude-sync
    [ "$status" -eq 0 ]
    # Should succeed without "Prompt file not found" error
    [[ "$output" != *"Prompt file not found"* ]]
}

@test "claude-sync works from iw-cli repository (os.pwd fallback)" {
    # This simulates running from the iw-cli repository itself
    # The iw-cli repo has the structure:
    #   iw-cli/
    #     .iw/
    #       commands/
    #       core/
    #       scripts/

    # Create a mock iw-cli repo structure
    mkdir -p mock-iw-cli/.iw/commands
    mkdir -p mock-iw-cli/.iw/core
    mkdir -p mock-iw-cli/.iw/scripts

    # Copy files to the mock structure
    cp .iw-install/commands/claude-sync.scala mock-iw-cli/.iw/commands/
    cp -r .iw-install/core/* mock-iw-cli/.iw/core/
    rm -rf mock-iw-cli/.iw/core/test
    cp .iw-install/scripts/claude-skill-prompt.md mock-iw-cli/.iw/scripts/

    # Run the command script directly via scala-cli from iw-cli root
    # to test fallback behavior when IW_COMMANDS_DIR is not set
    unset IW_COMMANDS_DIR
    cd mock-iw-cli

    # Find core files
    core_files=$(find "$TEST_DIR/mock-iw-cli/.iw/core" -name "*.scala" -not -path "*/test/*" | tr '\n' ' ')

    run bash -c "cd '$TEST_DIR/mock-iw-cli' && scala-cli run .iw/commands/claude-sync.scala $core_files -- 2>&1 || true"
    # Should find template via os.pwd fallback
    [[ "$output" != *"Prompt file not found"* ]]
}

@test "claude-sync fails gracefully when template not found in either location" {
    # Remove the template from the installation directory
    rm -f .iw-install/scripts/claude-skill-prompt.md

    run ./iw-run claude-sync
    [ "$status" -eq 1 ]
    [[ "$output" == *"Prompt file not found"* ]]
}
