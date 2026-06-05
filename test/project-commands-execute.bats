#!/usr/bin/env bats
# PURPOSE: E2E smoke tests for iw-run → scala-cli pipeline on project commands (./ prefix)
# PURPOSE: Pure validation/error paths are in project-commands-validate.bats (lightweight setup)
# PURPOSE: scala-cli surface is pinned by test/contract/scala_cli_contract.bats

setup() {
    export IW_SERVER_DISABLED=1

    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init --quiet
    git config user.email "test@test.com"
    git config user.name "Test User"

    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
    chmod +x "$TEST_DIR/iw-run"

    # Copy shared commands (excluding hooks - hooks depend on env-specific config) and core
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
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "execute project command end-to-end through scala-cli" {
    mkdir -p .iw/commands
    cat > .iw/commands/test-cmd.scala <<'EOF'
// PURPOSE: Test command that echoes a message
// USAGE: ./test-cmd

@main def testCmd(args: String*): Unit = {
  println("PROJECT_COMMAND_OUTPUT")
}
EOF

    run ./iw-run ./test-cmd
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJECT_COMMAND_OUTPUT"* ]]
}

@test "same name in both namespaces dispatches to the right one based on ./ prefix" {
    cat > .iw-install/commands/dualname.scala <<'EOF'
// PURPOSE: Shared command
// USAGE: dualname

@main def dualname(args: String*): Unit = {
  println("SHARED_COMMAND")
}
EOF

    mkdir -p .iw/commands
    cat > .iw/commands/dualname.scala <<'EOF'
// PURPOSE: Project command
// USAGE: ./dualname

@main def dualname(args: String*): Unit = {
  println("PROJECT_COMMAND")
}
EOF

    run ./iw-run dualname
    [ "$status" -eq 0 ]
    [[ "$output" == *"SHARED_COMMAND"* ]]
    [[ "$output" != *"PROJECT_COMMAND"* ]]

    run ./iw-run ./dualname
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJECT_COMMAND"* ]]
    [[ "$output" != *"SHARED_COMMAND"* ]]
}

@test "shared command discovers hooks from project directory" {
    mkdir -p .iw/commands
    cat > .iw/commands/project.hook-doctor.scala <<'EOF'
// PURPOSE: Project-specific hook for doctor command
import iw.core.model.*

object ProjectHookDoctor:
  def checkProjectHealth(config: ProjectConfiguration): CheckResult =
    CheckResult.Success("Project hook executed")

  val check: Check = Check("Project Health", checkProjectHealth)
EOF

    run ./iw-run doctor --env
    [ "$status" -eq 0 ]
    [[ "$output" == *"Project Health"* ]]
    [[ "$output" == *"Project hook executed"* ]]
}
