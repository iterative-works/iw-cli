#!/usr/bin/env bats
# PURPOSE: Tests for version file reading and version requirement checking in iw-run
# PURPOSE: Covers read_iw_version(), compare_versions(), and check_version_requirement()

setup() {
    export IW_SERVER_DISABLED=1

    export TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Copy iw-run for sourcing
    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/iw-run"
    chmod +x "$TEST_DIR/iw-run"

    # Create install directories mirroring real layout
    mkdir -p "$TEST_DIR/.iw-install/commands"
    mkdir -p "$TEST_DIR/.iw-install/core"

    # Set env vars so sourcing iw-run resolves to our temp install
    export IW_COMMANDS_DIR="$TEST_DIR/.iw-install/commands"
    export IW_CORE_DIR="$TEST_DIR/.iw-install/core"
    export IW_PROJECT_DIR="$TEST_DIR"
    export XDG_DATA_HOME="$TEST_DIR/xdg-data"
    unset IW_PLUGIN_DIRS

    # Write a VERSION file in the install dir
    echo "0.3.7" > "$TEST_DIR/.iw-install/VERSION"

    # Override INSTALL_DIR to point at our temp install by pre-setting it
    # (iw-run sources INSTALL_DIR from its own location; we copy it there)
    cp "$BATS_TEST_DIRNAME/../iw-run" "$TEST_DIR/.iw-install/iw-run"
    chmod +x "$TEST_DIR/.iw-install/iw-run"

    # Source iw-run to get access to bash functions directly
    # The guard in iw-run prevents main() from running on source
    source "$TEST_DIR/iw-run"
    # Override INSTALL_DIR to point at the place with the VERSION file
    INSTALL_DIR="$TEST_DIR/.iw-install"
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

# --- read_iw_version() ---

@test "read_iw_version returns the version string from VERSION file" {
    result=$(read_iw_version)
    [ "$result" = "0.3.7" ]
}

@test "read_iw_version returns 0.0.0 when VERSION file is missing" {
    rm -f "$INSTALL_DIR/VERSION"
    result=$(read_iw_version)
    [ "$result" = "0.0.0" ]
}

@test "read_iw_version returns 0.0.0 when VERSION file has malformed content" {
    echo "not-a-version" > "$INSTALL_DIR/VERSION"
    result=$(read_iw_version)
    [ "$result" = "0.0.0" ]
}

@test "read_iw_version trims trailing newline from VERSION file" {
    printf "0.3.7\n\n" > "$INSTALL_DIR/VERSION"
    result=$(read_iw_version)
    [ "$result" = "0.3.7" ]
}

# --- compare_versions() ---

@test "compare_versions equal versions returns 0 (pass)" {
    run compare_versions "0.3.7" "0.3.7"
    [ "$status" -eq 0 ]
}

@test "compare_versions higher patch returns 0 (pass)" {
    run compare_versions "0.3.8" "0.3.7"
    [ "$status" -eq 0 ]
}

@test "compare_versions higher minor returns 0 (pass)" {
    run compare_versions "0.4.0" "0.3.7"
    [ "$status" -eq 0 ]
}

@test "compare_versions higher major returns 0 (pass)" {
    run compare_versions "1.0.0" "0.3.7"
    [ "$status" -eq 0 ]
}

@test "compare_versions lower patch returns 1 (fail)" {
    run compare_versions "0.3.6" "0.3.7"
    [ "$status" -eq 1 ]
}

@test "compare_versions lower minor returns 1 (fail)" {
    run compare_versions "0.2.9" "0.3.7"
    [ "$status" -eq 1 ]
}

@test "compare_versions lower major returns 1 (fail)" {
    run compare_versions "0.3.7" "1.0.0"
    [ "$status" -eq 1 ]
}

# --- check_version_requirement() ---

@test "check_version_requirement with satisfied REQUIRES header passes silently" {
    cat > "$TEST_DIR/test-cmd.scala" << 'EOF'
// PURPOSE: Test command
// REQUIRES: iw-cli >= 0.1.0
EOF
    run check_version_requirement "$TEST_DIR/test-cmd.scala"
    [ "$status" -eq 0 ]
    [ -z "$output" ]
}

@test "check_version_requirement with unsatisfied REQUIRES header fails with upgrade hint" {
    cat > "$TEST_DIR/test-cmd.scala" << 'EOF'
// PURPOSE: Test command
// REQUIRES: iw-cli >= 99.0.0
EOF
    run check_version_requirement "$TEST_DIR/test-cmd.scala"
    [ "$status" -eq 1 ]
    [[ "$output" == *"99.0.0"* ]]
    [[ "$output" == *"upgrade"* || "$output" == *"Upgrade"* || "$output" == *"update"* || "$output" == *"Update"* ]]
}

@test "check_version_requirement with no REQUIRES header passes silently" {
    cat > "$TEST_DIR/test-cmd.scala" << 'EOF'
// PURPOSE: Test command
// USAGE: test-cmd
EOF
    run check_version_requirement "$TEST_DIR/test-cmd.scala"
    [ "$status" -eq 0 ]
    [ -z "$output" ]
}

@test "check_version_requirement with malformed REQUIRES header warns but does not fail" {
    cat > "$TEST_DIR/test-cmd.scala" << 'EOF'
// PURPOSE: Test command
// REQUIRES: garbage
EOF
    run check_version_requirement "$TEST_DIR/test-cmd.scala"
    [ "$status" -eq 0 ]
    [[ "$output" == *"warn"* || "$output" == *"Warn"* || "$output" == *"WARNING"* || "$output" == *"malformed"* || "$output" == *"invalid"* || "$output" == *"Invalid"* ]]
}
