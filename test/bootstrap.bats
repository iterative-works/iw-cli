#!/usr/bin/env bats
# PURPOSE: Integration tests for iw-run launcher and bootstrap
# PURPOSE: Tests release package structure and offline operation

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"
VERSION="$(cat "$PROJECT_ROOT/VERSION")"
TARBALL_NAME="iw-cli-${VERSION}.tar.gz"
PACKAGE_DIR="iw-cli-${VERSION}"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Skip if release package not built
    if [ ! -f "$PROJECT_ROOT/release/$TARBALL_NAME" ]; then
        skip "Release package not found (run package-release.sh first)"
    fi

    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

@test "iw-run lists commands from installation directory" {
    # Setup: extract release package to simulate installation
    tar -xzf "$PROJECT_ROOT/release/$TARBALL_NAME"
    cd "$PACKAGE_DIR"

    # Run iw-run --list
    run ./iw-run --list

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Available commands"* ]]
    [[ "$output" == *"Command: version"* ]]
    [[ "$output" == *"Command: init"* ]]
}

@test "iw-run --bootstrap pre-compiles successfully" {
    # Setup: extract release package
    tar -xzf "$PROJECT_ROOT/release/$TARBALL_NAME"
    cd "$PACKAGE_DIR"

    # Run bootstrap
    run timeout 60 ./iw-run --bootstrap

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"Bootstrapping"* ]]
    [[ "$output" == *"pre-built jars present"* ]]
}

@test "iw-run executes commands from installation directory" {
    # Setup: extract release package and create a test project
    tar -xzf "$PROJECT_ROOT/release/$TARBALL_NAME"
    mkdir test-project
    cd test-project
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"

    # Run version command via iw-run
    run "$TEST_DIR/$PACKAGE_DIR/iw-run" version

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"iw-cli"* ]]
}

@test "release package contains required structure" {
    # Extract and verify structure
    tar -xzf "$PROJECT_ROOT/release/$TARBALL_NAME"

    # Assert directory structure
    [ -d "$PACKAGE_DIR" ]
    [ -f "$PACKAGE_DIR/iw-run" ]
    [ -d "$PACKAGE_DIR/commands" ]
    [ -d "$PACKAGE_DIR/core" ]
    [ -d "$PACKAGE_DIR/build" ]

    # Assert iw-run is executable
    [ -x "$PACKAGE_DIR/iw-run" ]

    # Assert core deps manifest exists (NOT all core sources — tarball ships only this file)
    [ -f "$PACKAGE_DIR/core/project.scala" ]

    # Assert pre-built jars are present and non-empty
    [ -f "$PACKAGE_DIR/build/iw-core.jar" ]
    [ -s "$PACKAGE_DIR/build/iw-core.jar" ]
    [ -f "$PACKAGE_DIR/build/iw-dashboard.jar" ]
    [ -s "$PACKAGE_DIR/build/iw-dashboard.jar" ]

    # Assert command files exist
    [ -f "$PACKAGE_DIR/commands/version.scala" ]
    [ -f "$PACKAGE_DIR/commands/init.scala" ]
}

@test "iw-run works without Mill on PATH (bundled jars only)" {
    tar -xzf "$PROJECT_ROOT/release/$TARBALL_NAME"
    cd "$PACKAGE_DIR"

    # Stub mill with a fail-loudly script and prepend it to PATH. The contract
    # under test is "launcher must not invoke mill when build/*.jar is present".
    # Any invocation creates a marker file we assert against, and the stub
    # itself exits non-zero so the launcher would also fail noisily.
    stub_bin="$BATS_TEST_TMPDIR/stub-bin"
    mill_marker="$BATS_TEST_TMPDIR/mill-was-called"
    mkdir -p "$stub_bin"
    cat > "$stub_bin/mill" <<EOF
#!/bin/sh
touch "$mill_marker"
echo "FAIL: launcher invoked mill — should have used build/*.jar" >&2
exit 1
EOF
    chmod +x "$stub_bin/mill"
    PATH="$stub_bin:$PATH"
    export PATH

    # Precondition: confirm our stub is the resolved mill, so the assertions
    # below verify the contract rather than coincidence.
    [ "$(command -v mill)" = "$stub_bin/mill" ]

    run ./iw-run --list
    [ "$status" -eq 0 ]
    [[ "$output" == *"Available commands"* ]]
    [ ! -f "$mill_marker" ]

    run ./iw-run --bootstrap
    [ "$status" -eq 0 ]
    [[ "$output" == *"pre-built jars present"* ]]
    [ ! -f "$mill_marker" ]
}
