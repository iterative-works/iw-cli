#!/usr/bin/env bats
# PURPOSE: E2E tests for the iw test command
# PURPOSE: Verifies test runner orchestration for unit and E2E tests

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

    # Copy the iw infrastructure
    cp -r "$BATS_TEST_DIRNAME/../.." "$TEST_DIR/.iw-src"
    cp "$BATS_TEST_DIRNAME/../../iw" "$TEST_DIR/iw"
    cp "$BATS_TEST_DIRNAME/../../iw-run" "$TEST_DIR/iw-run"
    mkdir -p .iw
    cp -r "$BATS_TEST_DIRNAME/../commands" .iw/
    cp -r "$BATS_TEST_DIRNAME/../core" .iw/

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

# Helper: run command with clean PATH for nested bats invocations.
# BATS prepends its libexec dir to PATH, which causes nested bats to find
# the inner bats-core/bats (missing bats_readlinkf) instead of the launcher.
run_with_clean_path() {
    run env PATH="${BATS_SAVED_PATH:-$PATH}" "$@"
}

@test "test command shows usage when invoked with --help" {
    run ./iw test --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"unit"* ]]
    [[ "$output" == *"e2e"* ]]
}

@test "test command runs unit tests with 'unit' argument" {
    # Create a simple test file that will pass
    mkdir -p .iw/core/test
    cat > .iw/core/test/SimpleTest.scala << 'EOF'
package iw.tests
class SimpleTest extends munit.FunSuite:
  test("simple test passes"):
    assertEquals(1 + 1, 2)
EOF

    run ./iw test unit
    [ "$status" -eq 0 ]
    [[ "$output" == *"simple test passes"* ]] || [[ "$output" == *"SimpleTest"* ]]
}

@test "test command returns non-zero on unit test failure" {
    # Create a test file that will fail
    mkdir -p .iw/core/test
    cat > .iw/core/test/FailingTest.scala << 'EOF'
package iw.tests
class FailingTest extends munit.FunSuite:
  test("this test fails"):
    assertEquals(1, 2)
EOF

    run ./iw test unit
    [ "$status" -ne 0 ]
}

@test "test command runs e2e tests with 'e2e' argument" {
    # Create a simple bats test that will pass
    mkdir -p .iw/test
    cat > .iw/test/simple.bats << 'EOF'
@test "simple e2e test passes" {
    [ 1 -eq 1 ]
}
EOF

    run_with_clean_path ./iw test e2e
    [ "$status" -eq 0 ]
    [[ "$output" == *"simple e2e test passes"* ]]
}

@test "test command returns non-zero on e2e test failure" {
    # Create a bats test that will fail
    mkdir -p .iw/test
    cat > .iw/test/failing.bats << 'EOF'
@test "this test fails" {
    [ 1 -eq 2 ]
}
EOF

    run_with_clean_path ./iw test e2e
    [ "$status" -ne 0 ]
}

@test "test command runs both unit and e2e with no argument" {
    # Create passing tests for both
    mkdir -p .iw/core/test
    cat > .iw/core/test/UnitTest.scala << 'EOF'
package iw.tests
class UnitTest extends munit.FunSuite:
  test("unit test passes"):
    assertEquals(2 + 2, 4)
EOF

    mkdir -p .iw/test
    cat > .iw/test/e2e.bats << 'EOF'
@test "e2e test passes" {
    [ 2 -eq 2 ]
}
EOF

    # Remove all commands except test.scala so the compile check is fast —
    # this test only verifies that unit and E2E tests both run together.
    find .iw/commands -name '*.scala' ! -name 'test.scala' -delete

    run_with_clean_path ./iw test
    [ "$status" -eq 0 ]
    [[ "$output" == *"unit test passes"* ]] || [[ "$output" == *"UnitTest"* ]]
    [[ "$output" == *"e2e test passes"* ]]
}
