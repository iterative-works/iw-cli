#!/usr/bin/env bats
# PURPOSE: E2E smoke tests for the iw-run → scala-cli pipeline on plugin commands
# PURPOSE: Pure validation/error paths are in plugin-commands-validate.bats (lightweight setup)
# PURPOSE: scala-cli surface (--jar, run -q) is pinned by test/contract/scala_cli_contract.bats

load helpers/bloop-cleanup

setup() {
    export IW_SERVER_DISABLED=1

    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init --quiet
    git config user.email "test@test.com"
    git config user.name "Test User"

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

    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
    export IW_PROJECT_DIR="$TEST_DIR"

    # Resolve the Mill-built core jar so iw-run skips invoking Mill in the temp dir.
    local repo_root
    repo_root="$(cd "$BATS_TEST_DIRNAME/.." && pwd)"
    local core_jar
    core_jar="$(cd "$repo_root" && ./mill --ticker false show core.jar 2>/dev/null \
        | jq -r '.' | sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##')"
    if [ -n "$core_jar" ] && [ -f "$core_jar" ]; then
        export IW_CORE_JAR="$core_jar"
    fi

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

    mkdir -p "$PLUGIN_BASE/testplugin/commands"
    cat > "$PLUGIN_BASE/testplugin/commands/hello.scala" <<'EOF'
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

@test "execute plugin command end-to-end through scala-cli" {
    run ./iw-run testplugin/hello
    [ "$status" -eq 0 ]
    [[ "$output" == *"PLUGIN_HELLO_OUTPUT"* ]]
}

@test "plugin command can import from plugin lib directory" {
    mkdir -p "$PLUGIN_BASE/testplugin/lib"
    cat > "$PLUGIN_BASE/testplugin/lib/PluginHelper.scala" <<'EOF'
// PURPOSE: Helper library for testplugin
// PURPOSE: Provides utility functions for plugin commands

object PluginHelper:
  def greet(name: String): String = s"HELLO_FROM_LIB:$name"
EOF

    cat > "$PLUGIN_BASE/testplugin/commands/use-lib.scala" <<'EOF'
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
