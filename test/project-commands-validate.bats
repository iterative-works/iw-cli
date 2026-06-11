#!/usr/bin/env bats
# PURPOSE: E2E tests for project command syntax validation in iw-run
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
    # pointing at a readable .jar file) with an empty placeholder.
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
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "project command not found shows clear error" {
    run ./iw-run ./nonexistent
    [ "$status" -eq 1 ]
    [[ "$output" == *"Project command 'nonexistent' not found"* ]]
    [[ "$output" == *".iw/commands/"* ]]
    [[ "$output" == *"Run 'iw --list'"* ]]
}

@test "shared command not found shows clear error" {
    run ./iw-run nonexistent
    [ "$status" -eq 1 ]
    [[ "$output" == *"Command 'nonexistent' not found"* ]]
    [[ "$output" != *"Project command"* ]]
    [[ "$output" == *"Run 'iw --list'"* ]]
}

@test "invalid project command syntax ./ alone shows error" {
    run ./iw-run ./
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]]
}

@test "invalid project command syntax with special chars shows error" {
    run ./iw-run './invalid$name'
    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]]
}
