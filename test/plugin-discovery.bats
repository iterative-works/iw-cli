#!/usr/bin/env bats
# PURPOSE: E2E tests for plugin directory discovery in iw-run
# PURPOSE: Verifies plugins are found via XDG_DATA_HOME and IW_PLUGIN_DIRS

load helpers/bloop-cleanup

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
    # Clear IW_PLUGIN_DIRS to avoid interference
    unset IW_PLUGIN_DIRS
}

teardown() {
    stop_test_bloop
    cd /
    rm -rf "$TEST_DIR"
}

@test "plugin found via XDG auto-discovery" {
    # Create a plugin under XDG_DATA_HOME
    mkdir -p "$XDG_DATA_HOME/iw/plugins/testplugin/commands"
    cat > "$XDG_DATA_HOME/iw/plugins/testplugin/commands/greet.scala" << 'EOF'
// PURPOSE: Greet the user
// USAGE: testplugin/greet <name>

@main def greet(args: String*): Unit = println("Hello")
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Plugin commands (testplugin):"* ]]
    [[ "$output" == *"testplugin/greet"* ]]
}

@test "plugin found via IW_PLUGIN_DIRS env var" {
    # Create a plugin in a custom directory
    local plugin_parent="$TEST_DIR/custom-plugins"
    mkdir -p "$plugin_parent/myplugin/commands"
    cat > "$plugin_parent/myplugin/commands/deploy.scala" << 'EOF'
// PURPOSE: Deploy the app
// USAGE: myplugin/deploy

@main def deploy(args: String*): Unit = println("Deploying")
EOF

    export IW_PLUGIN_DIRS="$plugin_parent"

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Plugin commands (myplugin):"* ]]
    [[ "$output" == *"myplugin/deploy"* ]]
}

@test "non-existent plugin dir in IW_PLUGIN_DIRS is skipped without error" {
    export IW_PLUGIN_DIRS="/nonexistent/path/to/plugins"

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" != *"Plugin commands"* ]]
}

@test "plugin without commands/ subdirectory is skipped" {
    # Create a plugin directory without commands/ subdir
    mkdir -p "$XDG_DATA_HOME/iw/plugins/incomplete"
    mkdir -p "$XDG_DATA_HOME/iw/plugins/incomplete/lib"
    cat > "$XDG_DATA_HOME/iw/plugins/incomplete/lib/Helper.scala" << 'EOF'
object Helper
EOF

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" != *"Plugin commands (incomplete):"* ]]
}

@test "empty plugins directory produces no plugin sections" {
    # Create the plugins dir but with no plugins inside
    mkdir -p "$XDG_DATA_HOME/iw/plugins"

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" != *"Plugin commands"* ]]
}

@test "IW_PLUGIN_DIRS takes precedence over XDG for same plugin name" {
    # Create same-named plugin in XDG with one command
    mkdir -p "$XDG_DATA_HOME/iw/plugins/dualplugin/commands"
    cat > "$XDG_DATA_HOME/iw/plugins/dualplugin/commands/xdg-cmd.scala" << 'EOF'
// PURPOSE: Command from XDG location
// USAGE: dualplugin/xdg-cmd

@main def xdgCmd(args: String*): Unit = println("XDG")
EOF

    # Create same-named plugin in IW_PLUGIN_DIRS with a different command
    local plugin_parent="$TEST_DIR/override-plugins"
    mkdir -p "$plugin_parent/dualplugin/commands"
    cat > "$plugin_parent/dualplugin/commands/env-cmd.scala" << 'EOF'
// PURPOSE: Command from env var location
// USAGE: dualplugin/env-cmd

@main def envCmd(args: String*): Unit = println("ENV")
EOF

    export IW_PLUGIN_DIRS="$plugin_parent"

    run ./iw-run --list
    [ "$status" -eq 0 ]
    # Should show the IW_PLUGIN_DIRS version (env-cmd), not the XDG version (xdg-cmd)
    [[ "$output" == *"dualplugin/env-cmd"* ]]
    [[ "$output" != *"dualplugin/xdg-cmd"* ]]
    # Plugin section should appear exactly once (not duplicated)
    local count=$(echo "$output" | grep -c "Plugin commands (dualplugin):")
    [ "$count" -eq 1 ]
}
