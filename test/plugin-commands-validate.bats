#!/usr/bin/env bats
# PURPOSE: E2E tests for plugin command syntax validation in iw-run
# PURPOSE: These error paths abort before scala-cli, so they use the lightweight setup

setup() {
    export IW_SERVER_DISABLED=1

    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init --quiet
    git config user.email "test@test.com"
    git config user.name "Test User"

    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
    chmod +x "$TEST_DIR/iw-run"

    # These tests abort before scala-cli runs, but iw-run's execute_command
    # path calls ensure_core_jar first. Satisfy rung 1 (IW_CORE_JAR env var
    # pointing at a readable .jar file) with an empty placeholder — no real
    # jar contents are needed.
    mkdir -p .iw-install/commands
    cp "$BATS_TEST_DIRNAME/../commands"/*.scala .iw-install/commands/
    : > "$TEST_DIR/placeholder-core.jar"
    export IW_CORE_JAR="$TEST_DIR/placeholder-core.jar"

    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_PROJECT_DIR="$TEST_DIR"

    mkdir -p .iw
    cat > .iw/config.conf <<'EOF'
tracker {
  type = linear
  team = TEST
}
project {
  name = test-project
}
EOF

    export XDG_DATA_HOME="$TEST_DIR/xdg-data"
    export PLUGIN_BASE="$TEST_DIR/plugins"
    export IW_PLUGIN_DIRS="$PLUGIN_BASE"

    # testplugin must exist for "unknown command in known plugin" to fire the
    # right error branch instead of "plugin not found".
    mkdir -p "$PLUGIN_BASE/testplugin/commands"
    cat > "$PLUGIN_BASE/testplugin/commands/hello.scala" <<'EOF'
// PURPOSE: Stub plugin command (validation tests never execute it)
// USAGE: testplugin/hello

@main def hello(args: String*): Unit = println("PLUGIN_HELLO_OUTPUT")
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
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
