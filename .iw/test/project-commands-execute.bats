#!/usr/bin/env bats
# PURPOSE: E2E tests for project command execution with ./ prefix
# PURPOSE: Verifies routing between shared and project namespaces

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
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "execute project command with ./ prefix successfully" {
    # Create a simple project command
    mkdir -p .iw/commands
    cat > .iw/commands/test-cmd.scala << 'EOF'
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

@test "project command receives CLI arguments correctly" {
    # Create a project command that echoes its arguments
    mkdir -p .iw/commands
    cat > .iw/commands/echo-args.scala << 'EOF'
// PURPOSE: Echo command arguments
// USAGE: ./echo-args <args...>

@main def echoArgs(args: String*): Unit = {
  args.foreach(arg => println(s"ARG:$arg"))
}
EOF

    run ./iw-run ./echo-args foo bar baz
    [ "$status" -eq 0 ]
    [[ "$output" == *"ARG:foo"* ]]
    [[ "$output" == *"ARG:bar"* ]]
    [[ "$output" == *"ARG:baz"* ]]
}

@test "project command can import core library (Config)" {
    # Create a project command that imports and uses the core library
    mkdir -p .iw/commands
    cat > .iw/commands/use-core.scala << 'EOF'
// PURPOSE: Test command that uses core library
// USAGE: ./use-core

import iw.core.{ConfigFileRepository, ProjectConfiguration}

@main def useCore(args: String*): Unit = {
  val configPath = os.pwd / ".iw" / "config.conf"
  val config = ConfigFileRepository.read(configPath)
  config match {
    case Some(cfg) => println(s"PROJECT_NAME:${cfg.projectName}")
    case None => println("NO_CONFIG")
  }
}
EOF

    run ./iw-run ./use-core
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJECT_NAME:test-project"* ]]
}

@test "project command not found shows clear error" {
    run ./iw-run ./nonexistent
    [ "$status" -eq 1 ]
    [[ "$output" == *"Project command 'nonexistent' not found"* ]]
    [[ "$output" == *".iw/commands/"* ]]
    [[ "$output" == *"Run 'iw --list'"* ]]
}

@test "shared command without prefix executes normally" {
    # Test that a known shared command still works without ./ prefix
    run ./iw-run version
    [ "$status" -eq 0 ]
    [[ "$output" == *"iw-cli version"* ]]
}

@test "shared command not found shows clear error" {
    run ./iw-run nonexistent
    [ "$status" -eq 1 ]
    [[ "$output" == *"Command 'nonexistent' not found"* ]]
    # Should NOT mention "Project command" for shared namespace
    [[ "$output" != *"Project command"* ]]
    [[ "$output" == *"Run 'iw --list'"* ]]
}

@test "same name in both namespaces - each invoked correctly" {
    # Create a shared command
    cat > .iw-install/commands/dualname.scala << 'EOF'
// PURPOSE: Shared command
// USAGE: dualname

@main def dualname(args: String*): Unit = {
  println("SHARED_COMMAND")
}
EOF

    # Create a project command with same name
    mkdir -p .iw/commands
    cat > .iw/commands/dualname.scala << 'EOF'
// PURPOSE: Project command
// USAGE: ./dualname

@main def dualname(args: String*): Unit = {
  println("PROJECT_COMMAND")
}
EOF

    # Test shared command (no prefix)
    run ./iw-run dualname
    [ "$status" -eq 0 ]
    [[ "$output" == *"SHARED_COMMAND"* ]]
    [[ "$output" != *"PROJECT_COMMAND"* ]]

    # Test project command (with prefix)
    run ./iw-run ./dualname
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJECT_COMMAND"* ]]
    [[ "$output" != *"SHARED_COMMAND"* ]]
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

@test "shared command discovers hooks from project directory" {
    # Create a hook file in the project directory for a shared command
    mkdir -p .iw/commands
    cat > .iw/commands/project.hook-doctor.scala << 'EOF'
// PURPOSE: Project-specific hook for doctor command
import iw.core.*

object ProjectHookDoctor:
  def checkProjectHealth(config: ProjectConfiguration): CheckResult =
    CheckResult.Success("Project hook executed")

  val check: Check = Check("Project Health", checkProjectHealth)
EOF

    # Run the doctor command (shared command)
    run ./iw-run doctor
    [ "$status" -eq 0 ]
    [[ "$output" == *"Project Health"* ]]
    [[ "$output" == *"Project hook executed"* ]]
}
