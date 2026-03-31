#!/usr/bin/env bats
# PURPOSE: E2E tests for plugin command description with --describe
# PURPOSE: Verifies --describe works for plugin/command syntax with source attribution

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

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

    # Isolate XDG from real system
    export XDG_DATA_HOME="$TEST_DIR/xdg-data"
    unset IW_PLUGIN_DIRS

    # Create a test plugin with a full-featured command
    mkdir -p "$XDG_DATA_HOME/iw/plugins/testplugin/commands"
    cat > "$XDG_DATA_HOME/iw/plugins/testplugin/commands/implement.scala" << 'EOF'
// PURPOSE: Implement a feature using AI assistance
// USAGE: testplugin/implement <issue-id> [options]
// ARGS:
//   issue-id     The issue identifier to implement
//   --dry-run    Show plan without executing
// EXAMPLE: testplugin/implement IW-123
// EXAMPLE: testplugin/implement IW-123 --dry-run

@main def implement(args: String*): Unit = println("Implementing")
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "describe plugin command shows purpose, usage, and source attribution" {
    run ./iw-run --describe testplugin/implement
    [ "$status" -eq 0 ]

    # Check header
    [[ "$output" == *"Command: testplugin/implement"* ]]

    # Check metadata
    [[ "$output" == *"Purpose:"* ]]
    [[ "$output" == *"Implement a feature using AI assistance"* ]]
    [[ "$output" == *"Usage:"* ]]
    [[ "$output" == *"testplugin/implement <issue-id> [options]"* ]]
    [[ "$output" == *"Arguments:"* ]]
    [[ "$output" == *"issue-id"* ]]
    [[ "$output" == *"--dry-run"* ]]
    [[ "$output" == *"Examples:"* ]]
    [[ "$output" == *"testplugin/implement IW-123"* ]]

    # Check source attribution
    [[ "$output" == *"Source: plugin (testplugin)"* ]]
}

@test "unknown plugin name gives clear error message and non-zero exit" {
    run ./iw-run --describe nosuchplugin/somecommand
    [ "$status" -eq 1 ]
    [[ "$output" == *"nosuchplugin"* ]]
    [[ "$output" == *"not found"* ]]
}

@test "unknown command within known plugin gives clear error message and non-zero exit" {
    run ./iw-run --describe testplugin/nonexistent
    [ "$status" -eq 1 ]
    [[ "$output" == *"nonexistent"* ]]
    [[ "$output" == *"not found"* ]]
    [[ "$output" == *"testplugin"* ]]
}

@test "invalid plugin/command syntax with multiple slashes gives clear error" {
    run ./iw-run --describe foo/bar/baz
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]]
}
