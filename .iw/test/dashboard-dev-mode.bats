#!/usr/bin/env bats
# PURPOSE: Integration tests for dashboard --dev mode isolation
# PURPOSE: Validates that dev mode provides complete isolation from production data

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create minimal git repo (some commands may require it)
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Kill any lingering dashboard processes
    pkill -f "dashboard --dev" || true

    # Clean up temporary directory
    rm -rf "$TEST_DIR"

    # Clean up any dev temp directories created during test
    rm -rf /tmp/iw-dev-* 2>/dev/null || true
}

@test "dev mode creates temp directory" {
    # Start server with --dev in background, capture output
    timeout 3 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait a bit for server to start and print output
    sleep 2

    # Kill the server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Read captured output
    OUTPUT=$(cat /tmp/test-output.txt)

    # Check output contains temp directory path
    [[ "$OUTPUT" == *"/tmp/iw-dev-"* ]]
    [[ "$OUTPUT" == *"Dev mode enabled"* ]]
    [[ "$OUTPUT" == *"Temp directory: /tmp/iw-dev-"* ]]

    # Cleanup
    rm -f /tmp/test-output.txt
}

@test "dev mode creates state.json in temp directory" {
    # Start server with --dev, capture output to file
    timeout 3 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for initialization
    sleep 2

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Extract temp directory from output
    TEMP_DIR=$(grep -o "/tmp/iw-dev-[0-9]*" /tmp/test-output.txt | head -1)

    # Verify temp directory was found
    [ -n "$TEMP_DIR" ]

    # Verify state.json exists in temp directory
    [ -f "$TEMP_DIR/state.json" ]

    # Cleanup
    rm -f /tmp/test-output.txt
}

@test "dev mode creates config.json in temp directory" {
    # Start server with --dev, capture output to file
    timeout 3 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for initialization
    sleep 2

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Extract temp directory from output
    TEMP_DIR=$(grep -o "/tmp/iw-dev-[0-9]*" /tmp/test-output.txt | head -1)

    # Verify config.json exists in temp directory
    [ -n "$TEMP_DIR" ]
    [ -f "$TEMP_DIR/config.json" ]

    # Cleanup
    rm -f /tmp/test-output.txt
}

@test "dev mode enables sample data by default" {
    # Start server with --dev, capture output
    timeout 3 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for initialization
    sleep 2

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Read captured output
    OUTPUT=$(cat /tmp/test-output.txt)

    # Verify output includes sample data messages
    [[ "$OUTPUT" == *"Sample data: enabled"* ]]
    [[ "$OUTPUT" == *"Generating sample data"* ]]

    # Cleanup
    rm -f /tmp/test-output.txt
}
