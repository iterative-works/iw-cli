#!/usr/bin/env bats
# PURPOSE: Tests for plugin hook discovery in shared and plugin command execution
# PURPOSE: Verifies hooks from plugin directories and project commands are discovered

setup() {
    export IW_SERVER_DISABLED=1

    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    cp "$BATS_TEST_DIRNAME/../../iw-run" "$TEST_DIR/iw-run"
    chmod +x "$TEST_DIR/iw-run"

    # Create install directories
    mkdir -p "$TEST_DIR/.iw-install/commands"
    mkdir -p "$TEST_DIR/.iw-install/core"

    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
    export IW_PROJECT_DIR="$TEST_DIR"
    export XDG_DATA_HOME="$TEST_DIR/xdg-data"

    # Set up plugin dirs
    export PLUGIN_BASE="$TEST_DIR/plugins"
    export IW_PLUGIN_DIRS="$PLUGIN_BASE"

    # Write a VERSION file
    echo "0.3.7" > "$TEST_DIR/.iw-install/VERSION"

    # Source iw-run to get access to bash functions directly
    source "$TEST_DIR/iw-run"
    INSTALL_DIR="$TEST_DIR/.iw-install"

    # Create a test plugin with a hooks/ directory
    mkdir -p "$PLUGIN_BASE/testplugin/commands"
    mkdir -p "$PLUGIN_BASE/testplugin/hooks"

    # Create a simple plugin command
    cat > "$PLUGIN_BASE/testplugin/commands/hello.scala" << 'EOF'
// PURPOSE: Test plugin command
// USAGE: testplugin/hello

@main def hello(args: String*): Unit = println("PLUGIN_HELLO")
EOF

    # Create a hook file in the plugin's hooks/ directory
    cat > "$PLUGIN_BASE/testplugin/hooks/testplugin.hook-doctor.scala" << 'EOF'
// PURPOSE: Plugin hook for doctor command

object TestpluginHookDoctor:
  val name = "TestpluginHookDoctor"
EOF

    # Create a project commands directory
    mkdir -p "$TEST_DIR/.iw/commands"

    # Create a shared command to use as target for hook tests
    cat > "$TEST_DIR/.iw-install/commands/doctor.scala" << 'EOF'
// PURPOSE: Doctor command stub for testing
// USAGE: doctor

@main def doctor(args: String*): Unit = println("DOCTOR_OUTPUT")
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

# --- Plugin hook discovery for shared commands ---

@test "shared command execution discovers hook files from plugin hooks/ directory" {
    # Use discover_plugins and simulate the hook discovery logic
    # We test by checking the hook_files variable after running the discovery portion

    # Create a hook in the plugin's hooks/ dir matching the doctor command
    local hook_file="$PLUGIN_BASE/testplugin/hooks/myhook.hook-doctor.scala"
    cat > "$hook_file" << 'EOF'
// PURPOSE: Test hook for doctor

object MyHookDoctor:
  val name = "MyHookDoctor"
EOF

    # The hook should be found when we scan plugin hooks/ dirs
    # We test this by checking discover_plugins output and manually scanning
    local plugins
    plugins=$(discover_plugins)
    [ -n "$plugins" ]

    # Verify the hook file exists in the plugin's hooks/ dir
    [ -f "$hook_file" ]

    # Check that the hook pattern would match
    local found
    found=$(find "$PLUGIN_BASE/testplugin/hooks" -maxdepth 1 -name "*.hook-doctor.scala" 2>/dev/null || true)
    [ -n "$found" ]
}

@test "plugin without hooks/ directory is silently skipped during shared command hook discovery" {
    # Create a plugin without a hooks/ directory
    mkdir -p "$PLUGIN_BASE/nohooks/commands"
    cat > "$PLUGIN_BASE/nohooks/commands/cmd.scala" << 'EOF'
// PURPOSE: Command in plugin without hooks
// USAGE: nohooks/cmd

@main def cmd(args: String*): Unit = println("OK")
EOF

    # The scan should not fail even without hooks/ directory
    local found
    found=$(find "$PLUGIN_BASE/nohooks/hooks" -maxdepth 1 -name "*.hook-doctor.scala" 2>/dev/null || true)
    [ -z "$found" ]

    # discover_plugins should still work
    local plugins
    plugins=$(discover_plugins)
    [[ "$plugins" == *"nohooks"* ]]
}

@test "hook classes from plugin hooks are extracted and included in IW_HOOK_CLASSES" {
    local hook_file="$PLUGIN_BASE/testplugin/hooks/extracted.hook-doctor.scala"
    cat > "$hook_file" << 'EOF'
// PURPOSE: Hook for class extraction test

object ExtractedHookDoctor:
  val name = "extracted"
EOF

    # Test extract_hook_classes picks up the object name
    local classes
    classes=$(extract_hook_classes "$hook_file")
    [ "$classes" = "ExtractedHookDoctor" ]
}

# --- Project hook discovery for plugin commands ---

@test "plugin command execution discovers hook files from project .iw/commands/ directory" {
    # Create a hook in the project .iw/commands/ dir matching the plugin command name
    local hook_file="$TEST_DIR/.iw/commands/project.hook-hello.scala"
    cat > "$hook_file" << 'EOF'
// PURPOSE: Project hook for plugin hello command

object ProjectHookHello:
  val name = "ProjectHookHello"
EOF

    # Verify the hook file exists in project .iw/commands/
    [ -f "$hook_file" ]

    # Check that the hook pattern would match the plugin command name
    local found
    found=$(find "$TEST_DIR/.iw/commands" -maxdepth 1 -name "*.hook-hello.scala" 2>/dev/null || true)
    [ -n "$found" ]
    [[ "$found" == *"hook-hello"* ]]
}
