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

# Helper: run command with BATS_ test-state vars cleared to prevent nested bats interference.
# Keeps BATS_ROOT/BATS_LIBEXEC/BATS_LIB_PATH which bats needs to find its own scripts.
run_without_bats_env() {
    run env -u BATS_TEST_NAME -u BATS_TEST_NUMBER -u BATS_SUITE_TEST_NUMBER \
        -u BATS_TEST_DESCRIPTION -u BATS_TEST_FILENAME -u BATS_TEST_SOURCE \
        -u BATS_ROOT_PID -u BATS_RUN_TMPDIR -u BATS_FILE_TMPDIR \
        -u BATS_SUITE_TMPDIR -u BATS_TEST_TMPDIR -u BATS_OUT \
        -u BATS_RUNLOG_FILE -u BATS_WARNING_FILE "$@"
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

    # Debug: check prerequisites
    echo "# cwd=$(pwd)" >&3
    echo "# iw executable: $(test -x ./iw && echo yes || echo no)" >&3
    echo "# iw-run executable: $(test -x ./iw-run && echo yes || echo no)" >&3
    echo "# scala-cli: $(command -v scala-cli 2>&1)" >&3
    echo "# test dir: $(ls .iw/test/ 2>&1)" >&3
    echo "# commands/test.scala: $(test -f .iw/commands/test.scala && echo exists || echo missing)" >&3
    echo "# core files count: $(find .iw/core -name '*.scala' -not -path '*/test/*' 2>/dev/null | wc -l)" >&3

    # Capture stdout and stderr separately for debugging
    local out_file="$TEST_DIR/e2e-stdout.txt"
    local err_file="$TEST_DIR/e2e-stderr.txt"
    local exit_code=0
    ./iw test e2e >"$out_file" 2>"$err_file" || exit_code=$?
    echo "# e2e exit_code=$exit_code" >&3
    echo "# e2e stdout=$(cat "$out_file" | head -20)" >&3
    echo "# e2e stderr=$(cat "$err_file" | head -20)" >&3
    [ "$exit_code" -eq 0 ]
    [[ "$(cat "$out_file")" == *"simple e2e test passes"* ]]
}

@test "test command returns non-zero on e2e test failure" {
    # Create a bats test that will fail
    mkdir -p .iw/test
    cat > .iw/test/failing.bats << 'EOF'
@test "this test fails" {
    [ 1 -eq 2 ]
}
EOF

    run ./iw test e2e
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

    run ./iw test
    echo "# both status=$status" >&3
    echo "# both output=${output:0:500}" >&3
    [ "$status" -eq 0 ]
    [[ "$output" == *"unit test passes"* ]] || [[ "$output" == *"UnitTest"* ]]
    [[ "$output" == *"e2e test passes"* ]]
}
