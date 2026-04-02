#!/usr/bin/env bats
# PURPOSE: E2E tests for plugin command listing in iw --list output
# PURPOSE: Verifies plugin commands are displayed with correct format and filtering

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
    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
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

    # Create a standard test plugin
    mkdir -p "$XDG_DATA_HOME/iw/plugins/testplugin/commands"
    cat > "$XDG_DATA_HOME/iw/plugins/testplugin/commands/implement.scala" << 'EOF'
// PURPOSE: Implement a feature
// USAGE: testplugin/implement <issue>

@main def implement(args: String*): Unit = println("Implementing")
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "plugin commands shown in separate section with header" {
    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Plugin commands (testplugin):"* ]]
}

@test "commands listed as plugin/command format" {
    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Command: testplugin/implement"* ]]
    [[ "$output" == *"Implement a feature"* ]]
}

@test "plugin name derived from directory basename" {
    # Create a plugin with a specific directory name
    mkdir -p "$XDG_DATA_HOME/iw/plugins/my-cool-plugin/commands"
    cat > "$XDG_DATA_HOME/iw/plugins/my-cool-plugin/commands/run.scala" << 'EOF'
// PURPOSE: Run something cool
// USAGE: my-cool-plugin/run

@main def run(args: String*): Unit = println("Cool")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Plugin commands (my-cool-plugin):"* ]]
    [[ "$output" == *"Command: my-cool-plugin/run"* ]]
}

@test "hook files excluded from plugin command listing" {
    # Add a hook file to the plugin
    cat > "$XDG_DATA_HOME/iw/plugins/testplugin/commands/claude.hook-doctor.scala" << 'EOF'
// PURPOSE: Hook for doctor command
// USAGE: internal hook
object ClaudeHookDoctor
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    # Regular command should appear
    [[ "$output" == *"testplugin/implement"* ]]
    # Hook file should NOT appear
    [[ "$output" != *"claude.hook-doctor"* ]]
    [[ "$output" != *"Hook for doctor"* ]]
}

@test "lib/ directory files excluded from plugin command listing" {
    # Add lib files to the plugin (in a sibling directory, not in commands/)
    mkdir -p "$XDG_DATA_HOME/iw/plugins/testplugin/lib"
    cat > "$XDG_DATA_HOME/iw/plugins/testplugin/lib/Helper.scala" << 'EOF'
// PURPOSE: Helper library
// USAGE: internal

object Helper
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    # lib files should NOT appear (they are not in commands/)
    [[ "$output" != *"Helper"* ]]
    # Regular command should still appear
    [[ "$output" == *"testplugin/implement"* ]]
}

@test "multiple plugins shown in separate sections" {
    # Create a second plugin
    mkdir -p "$XDG_DATA_HOME/iw/plugins/anotherplugin/commands"
    cat > "$XDG_DATA_HOME/iw/plugins/anotherplugin/commands/analyze.scala" << 'EOF'
// PURPOSE: Analyze code quality
// USAGE: anotherplugin/analyze

@main def analyze(args: String*): Unit = println("Analyzing")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Plugin commands (testplugin):"* ]]
    [[ "$output" == *"Plugin commands (anotherplugin):"* ]]
    [[ "$output" == *"testplugin/implement"* ]]
    [[ "$output" == *"anotherplugin/analyze"* ]]
}

@test "plugin commands appear between shared and project command sections" {
    # Create a project command
    mkdir -p .iw/commands
    cat > .iw/commands/local-task.scala << 'EOF'
// PURPOSE: A local project task
// USAGE: ./local-task

@main def localTask(args: String*): Unit = println("Local")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]

    # Get line numbers for each section
    local shared_line=$(echo "$output" | grep -n "Available commands:" | head -1 | cut -d: -f1)
    local plugin_line=$(echo "$output" | grep -n "Plugin commands (testplugin):" | head -1 | cut -d: -f1)
    local project_line=$(echo "$output" | grep -n "Project commands (use ./name):" | head -1 | cut -d: -f1)

    # Plugin section should be between shared and project sections
    [ -n "$shared_line" ]
    [ -n "$plugin_line" ]
    [ -n "$project_line" ]
    [ "$shared_line" -lt "$plugin_line" ]
    [ "$plugin_line" -lt "$project_line" ]
}
