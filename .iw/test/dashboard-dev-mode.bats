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
    # Start server with --dev, capture output
    OUTPUT=$("$PROJECT_ROOT/iw" dashboard --dev 2>&1 &)
    PID=$!

    # Wait for initialization
    sleep 2

    # Extract temp directory from typical output pattern
    # Look for line like "  - State file: /tmp/iw-dev-<timestamp>/state.json"
    TEMP_DIR=$(echo "$OUTPUT" | grep -o "/tmp/iw-dev-[0-9]*" | head -1)

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Verify temp directory was created
    [ -n "$TEMP_DIR" ]

    # Verify state.json exists in temp directory
    [ -f "$TEMP_DIR/state.json" ]
}

@test "dev mode creates config.json in temp directory" {
    # Start server with --dev, capture output
    OUTPUT=$("$PROJECT_ROOT/iw" dashboard --dev 2>&1 &)
    PID=$!

    # Wait for initialization
    sleep 2

    # Extract temp directory
    TEMP_DIR=$(echo "$OUTPUT" | grep -o "/tmp/iw-dev-[0-9]*" | head -1)

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Verify config.json exists in temp directory
    [ -n "$TEMP_DIR" ]
    [ -f "$TEMP_DIR/config.json" ]
}

@test "production state file unchanged after dev mode" {
    # Setup: Create production state file with known content
    PROD_DIR="$HOME/.local/share/iw/server"
    PROD_STATE="$PROD_DIR/state.json"

    mkdir -p "$PROD_DIR"
    echo '{"worktrees":[{"id":"prod-1","path":"/prod/path","issueId":"PROD-1"}]}' > "$PROD_STATE"

    # Record baseline hash
    BASELINE_HASH=$(sha256sum "$PROD_STATE" | cut -d' ' -f1)

    # Start server with --dev
    "$PROJECT_ROOT/iw" dashboard --dev 2>&1 &
    PID=$!

    # Wait for server to initialize
    sleep 2

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Verify production state file hash unchanged
    AFTER_HASH=$(sha256sum "$PROD_STATE" | cut -d' ' -f1)
    [ "$BASELINE_HASH" = "$AFTER_HASH" ]

    # Cleanup
    rm -f "$PROD_STATE"
    rmdir "$PROD_DIR" 2>/dev/null || true
}

@test "production config file unchanged after dev mode" {
    # Setup: Create production config file with known content
    PROD_DIR="$HOME/.local/share/iw/server"
    PROD_CONFIG="$PROD_DIR/config.json"

    mkdir -p "$PROD_DIR"
    echo '{"port":9999,"hosts":["production.example.com"]}' > "$PROD_CONFIG"

    # Record baseline hash
    BASELINE_HASH=$(sha256sum "$PROD_CONFIG" | cut -d' ' -f1)

    # Start server with --dev
    "$PROJECT_ROOT/iw" dashboard --dev 2>&1 &
    PID=$!

    # Wait for server to initialize
    sleep 2

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Verify production config file hash unchanged
    AFTER_HASH=$(sha256sum "$PROD_CONFIG" | cut -d' ' -f1)
    [ "$BASELINE_HASH" = "$AFTER_HASH" ]

    # Cleanup
    rm -f "$PROD_CONFIG"
    rmdir "$PROD_DIR" 2>/dev/null || true
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
