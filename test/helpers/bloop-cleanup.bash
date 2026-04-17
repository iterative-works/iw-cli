# PURPOSE: Shared helper to stop orphan bloop daemons spawned by scala-cli during tests
# PURPOSE: Source this from .bats files that set XDG_DATA_HOME to a temp directory

# Kill any bloop daemon whose XDG_DATA_HOME points at our test directory.
# Must be called BEFORE rm -rf "$TEST_DIR" so /proc/$pid/environ is still readable.
stop_test_bloop() {
    local test_xdg="${XDG_DATA_HOME:-}"
    [ -n "$test_xdg" ] || return 0

    local pids=""
    for pid_dir in /proc/[0-9]*/environ; do
        local pid="${pid_dir#/proc/}"
        pid="${pid%/environ}"
        # Read environ (NUL-delimited); match our XDG_DATA_HOME exactly
        if tr '\0' '\n' < "$pid_dir" 2>/dev/null \
                | grep -qx "XDG_DATA_HOME=${test_xdg}"; then
            # Confirm it's actually a bloop daemon, not some unrelated process
            if grep -q "bloop-frontend" "/proc/${pid}/cmdline" 2>/dev/null; then
                pids="$pids $pid"
            fi
        fi
    done

    [ -n "$pids" ] || return 0

    # Graceful TERM first
    kill -TERM $pids 2>/dev/null || true
    # Brief wait for JVM to exit
    local retries=10
    while [ "$retries" -gt 0 ]; do
        local still_alive=""
        for pid in $pids; do
            kill -0 "$pid" 2>/dev/null && still_alive="$still_alive $pid"
        done
        [ -n "$still_alive" ] || return 0
        pids="$still_alive"
        sleep 0.1
        retries=$((retries - 1))
    done

    # Force kill survivors
    kill -KILL $pids 2>/dev/null || true
}
