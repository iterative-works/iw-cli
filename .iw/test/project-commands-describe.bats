#!/usr/bin/env bats
# PURPOSE: E2E tests for project command description with ./ prefix
# PURPOSE: Verifies --describe works for both shared and project namespaces

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
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "describe project command shows full metadata (PURPOSE, USAGE, ARGS, EXAMPLES)" {
    # Create a project command with full metadata
    mkdir -p .iw/commands
    cat > .iw/commands/deploy.scala << 'EOF'
// PURPOSE: Deploy application to production environment
// USAGE: ./deploy <environment> [options]
// ARGS:
//   environment  Target environment (staging, production)
//   --dry-run    Show what would be deployed without deploying
// EXAMPLE: ./deploy staging
// EXAMPLE: ./deploy production --dry-run

@main def deploy(args: String*): Unit = {
  println("Deploying...")
}
EOF

    run ./iw-run --describe ./deploy
    [ "$status" -eq 0 ]

    # Check header shows project command with ./ prefix
    [[ "$output" == *"Command: ./deploy"* ]]

    # Check all metadata sections are shown
    [[ "$output" == *"Purpose:"* ]]
    [[ "$output" == *"Deploy application to production environment"* ]]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"./deploy <environment> [options]"* ]]
    [[ "$output" == *"Arguments:"* ]]
    [[ "$output" == *"environment"* ]]
    [[ "$output" == *"--dry-run"* ]]
    [[ "$output" == *"Examples:"* ]]
    [[ "$output" == *"./deploy staging"* ]]
    [[ "$output" == *"./deploy production --dry-run"* ]]
}

@test "describe project command with minimal metadata shows what's available" {
    # Create a project command with only PURPOSE
    mkdir -p .iw/commands
    cat > .iw/commands/simple.scala << 'EOF'
// PURPOSE: Simple test command
// USAGE: ./simple

@main def simple(args: String*): Unit = {
  println("Simple output")
}
EOF

    run ./iw-run --describe ./simple
    [ "$status" -eq 0 ]

    # Check header shows project command with ./ prefix
    [[ "$output" == *"Command: ./simple"* ]]

    # Check PURPOSE and USAGE are shown
    [[ "$output" == *"Purpose:"* ]]
    [[ "$output" == *"Simple test command"* ]]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"./simple"* ]]

    # ARGS and EXAMPLES sections may be omitted or empty (depending on implementation)
    # We just verify the command succeeds and shows basic metadata
}

@test "describe project command not found shows clear error" {
    run ./iw-run --describe ./nonexistent
    [ "$status" -eq 1 ]

    # Error should mention it's a project command and where it looked
    [[ "$output" == *"Project command 'nonexistent' not found"* ]]
    [[ "$output" == *".iw/commands/"* ]]
}

@test "describe shared command (no prefix) works normally" {
    # Test that describe still works for shared commands
    run ./iw-run --describe version
    [ "$status" -eq 0 ]

    # Check header shows command without ./ prefix
    [[ "$output" == *"Command: version"* ]]
    [[ "$output" != *"Command: ./version"* ]]

    # Check metadata is shown
    [[ "$output" == *"Purpose:"* ]]
    [[ "$output" == *"Usage:"* ]]
}

@test "describe invalid project command syntax ./ alone shows error" {
    run ./iw-run --describe ./
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]]
}

@test "describe invalid project command syntax with special chars shows error" {
    run ./iw-run --describe './invalid$name'
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]]
}

@test "describe project command with dashes in name works" {
    # Create a project command with dashes in the name
    mkdir -p .iw/commands
    cat > .iw/commands/test-deploy.scala << 'EOF'
// PURPOSE: Test deployment command
// USAGE: ./test-deploy

@main def testDeploy(args: String*): Unit = {
  println("Test deploy")
}
EOF

    run ./iw-run --describe ./test-deploy
    [ "$status" -eq 0 ]
    [[ "$output" == *"Command: ./test-deploy"* ]]
    [[ "$output" == *"Test deployment command"* ]]
}
