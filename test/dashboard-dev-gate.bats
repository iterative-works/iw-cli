#!/usr/bin/env bats
# PURPOSE: Integration tests for the double-gated dev mode on iw dashboard
# PURPOSE: Verifies that dev routing requires both --dev flag and VITE_DEV_URL env var

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

# Pre-build the dashboard jar once before any test runs.
# Tests 1 and 2 invoke ./iw dashboard which triggers ensure_dashboard_jar;
# building ahead of time avoids cold-build timeouts in per-test runs.
setup_file() {
    export IW_SERVER_DISABLED=1
    "$PROJECT_ROOT/iw" dashboard --help >/dev/null 2>&1 || true
}

setup() {
    # Do NOT set IW_SERVER_DISABLED — these tests need the server to actually start
    # (or fail to start) to verify the dev-mode gate behavior.
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"
}

# GATE_SERVER_PID tracks any background server process started during a test.
GATE_SERVER_PID=""

teardown() {
    if [[ -n "$GATE_SERVER_PID" ]]; then
        kill "$GATE_SERVER_PID" 2>/dev/null || true
        wait "$GATE_SERVER_PID" 2>/dev/null || true
        GATE_SERVER_PID=""
    fi
    rm -rf "$TEST_DIR"
    rm -rf /tmp/iw-dev-gate-* 2>/dev/null || true
}

@test "non-loopback VITE_DEV_URL refuses to start with loopback error" {
    # The server JVM validates the URL and exits 1 with an error message.
    # Use --state-path with a temp file so the server would start if the URL were valid.
    STATE="$TEST_DIR/gate-test-state.json"
    # Run in foreground with short timeout; server should exit immediately on bad URL.
    VITE_DEV_URL=http://example.com:5173 \
        timeout 30 "$PROJECT_ROOT/iw" dashboard --dev \
        --state-path "$STATE" >"$TEST_DIR/out.txt" 2>"$TEST_DIR/err.txt" || true

    STDERR=$(cat "$TEST_DIR/err.txt")
    [[ "$STDERR" == *"loopback"* ]] || [[ "$STDERR" == *"host"* ]]
}

@test "https scheme VITE_DEV_URL refuses to start with scheme error" {
    STATE="$TEST_DIR/gate-test-state.json"
    VITE_DEV_URL=https://localhost:5173 \
        timeout 30 "$PROJECT_ROOT/iw" dashboard --dev \
        --state-path "$STATE" >"$TEST_DIR/out.txt" 2>"$TEST_DIR/err.txt" || true

    STDERR=$(cat "$TEST_DIR/err.txt")
    [[ "$STDERR" == *"scheme"* ]] || [[ "$STDERR" == *"http"* ]]
}

@test "--help exits 0 and lists expected flags regardless of VITE_DEV_URL" {
    export IW_SERVER_DISABLED=1
    VITE_DEV_URL=http://localhost:5173 run "$PROJECT_ROOT/iw" dashboard --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--state-path"* ]]
    [[ "$output" == *"--sample-data"* ]]
    [[ "$output" == *"--dev"* ]]
}
