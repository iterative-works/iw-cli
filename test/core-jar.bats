#!/usr/bin/env bats
# PURPOSE: E2E tests for the core jar lifecycle managed by iw-run
# PURPOSE: Covers missing/stale jar auto-rebuild, IW_CORE_JAR override, and overwrite regression

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    # Per-test isolated temp directory
    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Copy the iw-run script
    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
    chmod +x "$TEST_DIR/iw-run"

    # Copy shared commands and core (excluding test/)
    mkdir -p .iw-install/commands
    cp -r "$BATS_TEST_DIRNAME/../commands"/*.scala .iw-install/commands/
    cp -r "$BATS_TEST_DIRNAME/../core" .iw-install/
    rm -rf .iw-install/core/test

    # Use a per-test jar path so tests that mutate the jar do not contaminate
    # the shared repo-root jar used by other BATS suites.
    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
    export IW_CORE_JAR="$TEST_DIR/.iw-install/build/iw-core.jar"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
    # Clean up custom jar from scenario 4
    rm -f /tmp/custom-iw-core.jar
}

@test "missing jar triggers auto-rebuild on command execution" {
    # Ensure the jar does not exist before running a command
    rm -f "$IW_CORE_JAR"
    [ ! -f "$IW_CORE_JAR" ]

    run bash -c '"$TEST_DIR/iw-run" version 2>&1'
    [ "$status" -eq 0 ]
    [[ "$output" == *"Rebuilding core jar..."* ]]
    [ -f "$IW_CORE_JAR" ]
}

@test "stale jar triggers auto-rebuild" {
    # Build a fresh jar first
    run bash -c '"$TEST_DIR/iw-run" --bootstrap 2>&1'
    [ "$status" -eq 0 ]
    [ -f "$IW_CORE_JAR" ]

    # Record old mtime, then make a source file newer than the jar
    old_mtime=$(stat -c %Y "$IW_CORE_JAR")
    sleep 1
    # Touch any .scala file under core to make it newer than the jar
    find "$TEST_DIR/.iw-install/core" -name '*.scala' -print -quit | xargs touch

    run bash -c '"$TEST_DIR/iw-run" version 2>&1'
    [ "$status" -eq 0 ]
    [[ "$output" == *"Rebuilding core jar..."* ]]

    new_mtime=$(stat -c %Y "$IW_CORE_JAR")
    [ "$new_mtime" -gt "$old_mtime" ]
}

@test "fresh jar is silent (no rebuild on second invocation)" {
    # Build jar on first run
    run bash -c '"$TEST_DIR/iw-run" version 2>&1'
    [ "$status" -eq 0 ]
    [ -f "$IW_CORE_JAR" ]

    # Second run must not rebuild
    run bash -c '"$TEST_DIR/iw-run" version 2>&1'
    [ "$status" -eq 0 ]
    [[ "$output" != *"Rebuilding core jar..."* ]]
}

@test "IW_CORE_JAR override is honored by --bootstrap" {
    local custom_jar="/tmp/custom-iw-core.jar"
    rm -f "$custom_jar"

    IW_CORE_JAR="$custom_jar" run bash -c 'IW_CORE_JAR="'"$custom_jar"'" "$TEST_DIR/iw-run" --bootstrap 2>&1'
    [ "$status" -eq 0 ]
    [ -f "$custom_jar" ]
    # The default per-test jar must NOT have been created
    [ ! -f "$TEST_DIR/.iw-install/build/iw-core.jar" ]
}

@test "iw-run --bootstrap produces the jar at the default location" {
    rm -f "$IW_CORE_JAR"

    run bash -c '"$TEST_DIR/iw-run" --bootstrap 2>&1'
    [ "$status" -eq 0 ]
    [ -f "$IW_CORE_JAR" ]
}

@test "build_core_jar overwrites existing jar without error (Phase-2 -f regression guard)" {
    # Build initial jar
    run bash -c '"$TEST_DIR/iw-run" --bootstrap 2>&1'
    [ "$status" -eq 0 ]
    [ -f "$IW_CORE_JAR" ]

    # Capture old mtime
    old_mtime=$(stat -c %Y "$IW_CORE_JAR")
    sleep 1

    # Touch a source file to make it newer than the jar
    find "$TEST_DIR/.iw-install/core" -name '*.scala' -print -quit | xargs touch

    # Run any command — this must trigger an overwrite rebuild
    run bash -c '"$TEST_DIR/iw-run" version 2>&1'
    [ "$status" -eq 0 ]

    # Jar still exists at same path
    [ -f "$IW_CORE_JAR" ]

    # Jar is newer than before
    new_mtime=$(stat -c %Y "$IW_CORE_JAR")
    [ "$new_mtime" -gt "$old_mtime" ]

    # Rebuild marker appeared
    [[ "$output" == *"Rebuilding core jar..."* ]]

    # No "file exists" / "already exists" error from scala-cli
    [[ "$output" != *"file exists"* ]]
    [[ "$output" != *"already exists"* ]]
}
