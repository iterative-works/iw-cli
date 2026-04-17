#!/usr/bin/env bats
# PURPOSE: Tests for plugin hook discovery and class extraction in iw-run
# PURPOSE: Verifies hooks from plugin directories and project commands are discovered

load helpers/bloop-cleanup

setup() {
    export IW_SERVER_DISABLED=1

    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
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
    stop_test_bloop
    cd /
    rm -rf "$TEST_DIR"
}

# --- Plugin hook discovery for shared commands ---

@test "discover_plugins finds plugin with hooks directory" {
    local plugins
    plugins=$(discover_plugins)
    [[ "$plugins" == *"testplugin"* ]]
}

@test "extract_hook_classes extracts class name from doctor hook file" {
    local classes
    classes=$(extract_hook_classes "$PLUGIN_BASE/testplugin/hooks/testplugin.hook-doctor.scala")
    [ "$classes" = "TestpluginHookDoctor" ]
}

@test "extract_hook_classes extracts class from non-Doctor hook file" {
    # Hook targeting a non-doctor command (e.g., hello)
    cat > "$PLUGIN_BASE/testplugin/hooks/myhook.hook-hello.scala" << 'EOF'
// PURPOSE: Hook for hello command

object MyHookHello:
  val name = "MyHookHello"
EOF

    local classes
    classes=$(extract_hook_classes "$PLUGIN_BASE/testplugin/hooks/myhook.hook-hello.scala")
    [ "$classes" = "MyHookHello" ]
}

@test "extract_hook_classes handles multiple hook files" {
    cat > "$PLUGIN_BASE/testplugin/hooks/alpha.hook-doctor.scala" << 'EOF'
// PURPOSE: Alpha hook

object AlphaHookDoctor:
  val name = "alpha"
EOF

    cat > "$PLUGIN_BASE/testplugin/hooks/beta.hook-doctor.scala" << 'EOF'
// PURPOSE: Beta hook

object BetaHookDoctor:
  val name = "beta"
EOF

    local classes
    classes=$(extract_hook_classes "$PLUGIN_BASE/testplugin/hooks/alpha.hook-doctor.scala $PLUGIN_BASE/testplugin/hooks/beta.hook-doctor.scala")
    [[ "$classes" == *"AlphaHookDoctor"* ]]
    [[ "$classes" == *"BetaHookDoctor"* ]]
}

@test "plugin without hooks/ directory is silently skipped during discovery" {
    mkdir -p "$PLUGIN_BASE/nohooks/commands"
    cat > "$PLUGIN_BASE/nohooks/commands/cmd.scala" << 'EOF'
// PURPOSE: Command in plugin without hooks
// USAGE: nohooks/cmd

@main def cmd(args: String*): Unit = println("OK")
EOF

    # discover_plugins finds both plugins
    local plugins
    plugins=$(discover_plugins)
    [[ "$plugins" == *"nohooks"* ]]
    [[ "$plugins" == *"testplugin"* ]]

    # Scanning nohooks for hooks yields nothing (directory doesn't exist)
    local hook_files=""
    if [ -d "$PLUGIN_BASE/nohooks/hooks" ]; then
        hook_files=$(find "$PLUGIN_BASE/nohooks/hooks" -maxdepth 1 -name "*.hook-doctor.scala" 2>/dev/null || true)
    fi
    [ -z "$hook_files" ]
}

@test "shared command hook discovery finds plugin hooks via discover_plugins" {
    # Simulate the shared command hook discovery logic from execute_command
    local actual_name="doctor"
    local hook_files=""

    # Scan plugin hooks (same logic as execute_command)
    local plugin_entries
    plugin_entries=$(discover_plugins)
    [ -n "$plugin_entries" ]

    while IFS=: read -r _plugin_name plugin_dir; do
        if [ -d "$plugin_dir/hooks" ]; then
            local plugin_hooks
            plugin_hooks=$(find "$plugin_dir/hooks" -maxdepth 1 -name "*.hook-${actual_name}.scala" 2>/dev/null || true)
            if [ -n "$plugin_hooks" ]; then
                if [ -n "$hook_files" ]; then
                    hook_files="$hook_files $plugin_hooks"
                else
                    hook_files="$plugin_hooks"
                fi
            fi
        fi
    done <<< "$plugin_entries"

    [ -n "$hook_files" ]
    [[ "$hook_files" == *"hook-doctor.scala"* ]]

    # Extract classes from discovered hooks
    local hook_classes
    hook_classes=$(extract_hook_classes "$hook_files")
    [ -n "$hook_classes" ]
    [[ "$hook_classes" == *"TestpluginHookDoctor"* ]]
}

# --- Project hook discovery for plugin commands ---

@test "project hook discovery finds hooks for plugin commands" {
    # Create a project hook targeting the plugin's hello command
    local hook_file="$TEST_DIR/.iw/commands/project.hook-hello.scala"
    cat > "$hook_file" << 'EOF'
// PURPOSE: Project hook for plugin hello command

object ProjectHookHello:
  val name = "ProjectHookHello"
EOF

    # Simulate plugin command hook discovery logic from execute_command
    local plugin_cmd_name="hello"
    local project_hooks
    project_hooks=$(find "$TEST_DIR/.iw/commands" -maxdepth 1 -name "*.hook-${plugin_cmd_name}.scala" 2>/dev/null || true)

    [ -n "$project_hooks" ]
    [[ "$project_hooks" == *"hook-hello.scala"* ]]

    # Extract classes
    local hook_classes
    hook_classes=$(extract_hook_classes "$project_hooks")
    [ "$hook_classes" = "ProjectHookHello" ]
}

@test "extract_hook_classes returns empty string for file with no object declaration" {
    local hook_file="$TEST_DIR/empty-hook.scala"
    cat > "$hook_file" << 'EOF'
// PURPOSE: Malformed hook with no object
val x = 42
EOF

    local classes
    classes=$(extract_hook_classes "$hook_file")
    [ -z "$classes" ]
}
