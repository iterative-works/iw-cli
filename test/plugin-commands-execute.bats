#!/usr/bin/env bats
# PURPOSE: E2E tests for plugin command execution with <plugin>/<command> syntax
# PURPOSE: Verifies plugin commands are found, compiled, and run with correct classpath

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

    # Copy shared commands (excluding hooks) and core
    mkdir -p .iw-install/commands
    for f in "$BATS_TEST_DIRNAME/../commands"/*.scala; do
        if [[ ! "$(basename "$f")" =~ \.hook- ]]; then
            cp "$f" .iw-install/commands/
        fi
    done
    cp -r "$BATS_TEST_DIRNAME/../core" .iw-install/
    rm -rf .iw-install/core/test

    # Set environment variables to point to our test installation
    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
    export IW_PROJECT_DIR="$TEST_DIR"

    # Point at the shared, pre-built core jar from the repo root and sync the
    # copied sources' mtimes to it so core_jar_stale stays false (otherwise the
    # copy's fresh mtimes would trigger a rebuild into the shared jar path,
    # clobbering it for other tests in the suite).
    if [ -f "$BATS_TEST_DIRNAME/../build/iw-core.jar" ]; then
        export IW_CORE_JAR="$BATS_TEST_DIRNAME/../build/iw-core.jar"
        find "$TEST_DIR/.iw-install/core" -name '*.scala' \
            -exec touch -r "$IW_CORE_JAR" {} +
    fi

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

    # Isolate XDG from real system and set up plugin dirs
    export XDG_DATA_HOME="$TEST_DIR/xdg-data"
    export PLUGIN_BASE="$TEST_DIR/plugins"
    export IW_PLUGIN_DIRS="$PLUGIN_BASE"

    # Create a standard test plugin with a simple command
    mkdir -p "$PLUGIN_BASE/testplugin/commands"
    cat > "$PLUGIN_BASE/testplugin/commands/hello.scala" << 'EOF'
// PURPOSE: Test command that prints a greeting
// USAGE: testplugin/hello

@main def hello(args: String*): Unit = {
  println("PLUGIN_HELLO_OUTPUT")
}
EOF
}

teardown() {
    stop_test_bloop
    cd /
    rm -rf "$TEST_DIR"
}

@test "execute plugin command successfully" {
    run ./iw-run testplugin/hello
    [ "$status" -eq 0 ]
    [[ "$output" == *"PLUGIN_HELLO_OUTPUT"* ]]
}

@test "plugin command receives CLI arguments correctly" {
    cat > "$PLUGIN_BASE/testplugin/commands/echo-args.scala" << 'EOF'
// PURPOSE: Echo command arguments
// USAGE: testplugin/echo-args <args...>

@main def echoArgs(args: String*): Unit = {
  args.foreach(arg => println(s"ARG:$arg"))
}
EOF

    run ./iw-run testplugin/echo-args foo bar baz
    [ "$status" -eq 0 ]
    [[ "$output" == *"ARG:foo"* ]]
    [[ "$output" == *"ARG:bar"* ]]
    [[ "$output" == *"ARG:baz"* ]]
}

@test "plugin command can import core library" {
    cat > "$PLUGIN_BASE/testplugin/commands/use-core.scala" << 'EOF'
// PURPOSE: Test command that uses core library
// USAGE: testplugin/use-core

import iw.core.adapters.ConfigFileRepository
import iw.core.model.ProjectConfiguration

@main def useCore(args: String*): Unit = {
  val configPath = os.pwd / ".iw" / "config.conf"
  val config = ConfigFileRepository.read(configPath)
  config match {
    case Some(cfg) => println(s"PROJECT_NAME:${cfg.projectName}")
    case None => println("NO_CONFIG")
  }
}
EOF

    run ./iw-run testplugin/use-core
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJECT_NAME:test-project"* ]]
}

@test "plugin command can import from plugin lib directory" {
    mkdir -p "$PLUGIN_BASE/testplugin/lib"
    cat > "$PLUGIN_BASE/testplugin/lib/PluginHelper.scala" << 'EOF'
// PURPOSE: Helper library for testplugin
// PURPOSE: Provides utility functions for plugin commands

object PluginHelper:
  def greet(name: String): String = s"HELLO_FROM_LIB:$name"
EOF

    cat > "$PLUGIN_BASE/testplugin/commands/use-lib.scala" << 'EOF'
// PURPOSE: Test command that uses plugin lib
// USAGE: testplugin/use-lib

@main def useLib(args: String*): Unit = {
  println(PluginHelper.greet("world"))
}
EOF

    run ./iw-run testplugin/use-lib
    [ "$status" -eq 0 ]
    [[ "$output" == *"HELLO_FROM_LIB:world"* ]]
}

@test "unknown plugin name shows clear error" {
    run ./iw-run nonexistent/hello
    [ "$status" -eq 1 ]
    [[ "$output" == *"Plugin 'nonexistent' not found"* ]]
    [[ "$output" == *"Run 'iw --list'"* ]]
}

@test "unknown command in known plugin shows clear error" {
    run ./iw-run testplugin/nonexistent
    [ "$status" -eq 1 ]
    [[ "$output" == *"Command 'nonexistent' not found in plugin 'testplugin'"* ]]
    [[ "$output" == *"Run 'iw --list'"* ]]
}

@test "invalid syntax with multiple slashes shows clear error" {
    run ./iw-run foo/bar/baz
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid plugin command syntax"* ]]
    [[ "$output" == *"<plugin>/<command>"* ]]
}

@test "invalid syntax with empty plugin name shows clear error" {
    run ./iw-run /hello
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid plugin command syntax"* ]]
}

@test "invalid syntax with empty command name shows clear error" {
    run ./iw-run testplugin/
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid plugin command syntax"* ]]
}
