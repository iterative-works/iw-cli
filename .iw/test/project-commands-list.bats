#!/usr/bin/env bats
# PURPOSE: E2E tests for project command listing in iw --list
# PURPOSE: Verifies project commands are discovered and displayed separately from shared commands

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

@test "list commands shows project commands section when project commands exist" {
    # Create a project command
    mkdir -p .iw/commands
    cat > .iw/commands/deploy.scala << 'EOF'
// PURPOSE: Deploy application to environment
// USAGE: ./deploy <environment>

@main def deploy(args: String*): Unit =
  println("Deploying...")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Project commands (use ./name):"* ]]
}

@test "list commands shows ./prefix for project commands" {
    # Create a project command
    mkdir -p .iw/commands
    cat > .iw/commands/migrate.scala << 'EOF'
// PURPOSE: Run database migrations
// USAGE: ./migrate

@main def migrate(args: String*): Unit =
  println("Migrating...")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"./migrate"* ]]
}

@test "list commands shows no project section when .iw/commands directory missing" {
    # Don't create .iw/commands directory at all

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" != *"Project commands"* ]]
}

@test "list commands shows no project section when .iw/commands directory empty" {
    # Create empty .iw/commands directory
    mkdir -p .iw/commands

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" != *"Project commands"* ]]
}

@test "project command PURPOSE metadata displayed correctly" {
    # Create a project command with PURPOSE comment
    mkdir -p .iw/commands
    cat > .iw/commands/backup.scala << 'EOF'
// PURPOSE: Backup project database to S3
// USAGE: ./backup [--bucket <name>]

@main def backup(args: String*): Unit =
  println("Backing up...")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"./backup"* ]]
    [[ "$output" == *"Backup project database to S3"* ]]
}

# Critical issue fix: Test hook file exclusion in project commands
@test "list commands excludes hook files from project commands" {
    mkdir -p .iw/commands
    # Create a regular project command
    cat > .iw/commands/mytask.scala << 'EOF'
// PURPOSE: Run a custom task
// USAGE: ./mytask

@main def mytask(args: String*): Unit =
  println("Running task...")
EOF

    # Create a hook file that should NOT appear in listing
    cat > .iw/commands/custom.hook-mytask.scala << 'EOF'
// PURPOSE: Hook for mytask - should not appear
// USAGE: internal hook
object CustomHookMytask
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    # Regular command should appear
    [[ "$output" == *"./mytask"* ]]
    [[ "$output" == *"Run a custom task"* ]]
    # Hook file should NOT appear
    [[ "$output" != *"custom.hook-mytask"* ]]
    [[ "$output" != *"Hook for mytask"* ]]
}

# Critical issue fix: Test missing PURPOSE header handling
@test "list commands handles missing PURPOSE header gracefully" {
    mkdir -p .iw/commands
    # Create a command file without PURPOSE header
    cat > .iw/commands/nopurpose.scala << 'EOF'
// USAGE: ./nopurpose
@main def nopurpose(args: String*): Unit = println("test")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    # Command should still appear, just with empty purpose
    [[ "$output" == *"./nopurpose"* ]]
}

# Critical issue fix: Test special characters in metadata
@test "list commands handles special characters in metadata safely" {
    mkdir -p .iw/commands
    cat > .iw/commands/special.scala << 'EOF'
// PURPOSE: Uses $variables and `backticks` safely
// USAGE: ./special <arg>
@main def special(args: String*): Unit = println("test")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    # Should display without executing shell commands
    [[ "$output" == *"./special"* ]]
    # The purpose should be displayed (shell shouldn't expand $variables)
    [[ "$output" == *"Uses"* ]]
}
