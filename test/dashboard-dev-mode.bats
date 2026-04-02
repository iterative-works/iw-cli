#!/usr/bin/env bats
# PURPOSE: Integration tests for dashboard --dev mode isolation
# PURPOSE: Validates that dev mode provides complete isolation from production data

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

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
    timeout 60 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for server to start (poll for Port line in output, which appears after dev mode setup)
    PORT=""
    for i in $(seq 1 40); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" /tmp/test-output.txt 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Kill the server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    # Verify we got the port (server started successfully)
    [ -n "$PORT" ] || { cat /tmp/test-output.txt; echo "Server did not produce Port output"; rm -f /tmp/test-output.txt; return 1; }

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
    timeout 60 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for server to start (poll for Port line in output)
    PORT=""
    for i in $(seq 1 40); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" /tmp/test-output.txt 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    [ -n "$PORT" ] || { cat /tmp/test-output.txt; echo "Server did not produce Port output"; rm -f /tmp/test-output.txt; return 1; }

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
    timeout 60 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for server to start (poll for Port line in output)
    PORT=""
    for i in $(seq 1 40); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" /tmp/test-output.txt 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    [ -n "$PORT" ] || { cat /tmp/test-output.txt; echo "Server did not produce Port output"; rm -f /tmp/test-output.txt; return 1; }

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
    timeout 60 "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    PID=$!

    # Wait for server to start (poll for Port line in output)
    PORT=""
    for i in $(seq 1 40); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" /tmp/test-output.txt 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Kill server
    kill $PID 2>/dev/null || true
    wait $PID 2>/dev/null || true

    [ -n "$PORT" ] || { cat /tmp/test-output.txt; echo "Server did not produce Port output"; rm -f /tmp/test-output.txt; return 1; }

    # Read captured output
    OUTPUT=$(cat /tmp/test-output.txt)

    # Verify output includes sample data messages
    [[ "$OUTPUT" == *"Sample data: enabled"* ]]
    [[ "$OUTPUT" == *"Generating sample data"* ]]

    # Cleanup
    rm -f /tmp/test-output.txt
}

@test "GET /worktrees/NONEXISTENT-999 returns not-found page" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" /tmp/test-output.txt 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; rm -f /tmp/test-output.txt; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; rm -f /tmp/test-output.txt; echo "Server did not start in time"; return 1; }

    # Fetch a worktree detail page for an issue ID that is not registered
    HTTP_STATUS=$(curl -s -o "$TEST_DIR/test-response.txt" -w "%{http_code}" "http://localhost:$PORT/worktrees/NONEXISTENT-999")
    RESPONSE=$(cat "$TEST_DIR/test-response.txt")

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
    rm -f /tmp/test-output.txt

    # Assert 404 status code
    [ "$HTTP_STATUS" -eq 404 ]

    # Assert not-found page content
    [[ "$RESPONSE" == *"not registered"* ]]

    # Assert link back to overview is present
    [[ "$RESPONSE" == *'href="/"'* ]]

    # Assert the issue ID appears in the response
    [[ "$RESPONSE" == *"NONEXISTENT-999"* ]]
}

@test "GET /worktrees/:issueId returns breadcrumb navigation" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > /tmp/test-output.txt 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" /tmp/test-output.txt 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; rm -f /tmp/test-output.txt; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; rm -f /tmp/test-output.txt; echo "Server did not start in time"; return 1; }

    # Register a worktree via the API and verify it succeeded
    REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://localhost:$PORT/api/v1/worktrees/TEST-1" \
        -H "Content-Type: application/json" \
        -d '{"path":"/tmp/test-project-TEST-1","trackerType":"github","team":"test-org/test-project"}')
    [[ "$REG_STATUS" == "200" || "$REG_STATUS" == "201" ]] || {
        kill "$SERVER_PID" 2>/dev/null; rm -f /tmp/test-output.txt
        echo "Worktree registration failed with status $REG_STATUS"; return 1
    }

    # Fetch the worktree detail page
    RESPONSE=$(curl -s "http://localhost:$PORT/worktrees/TEST-1")

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
    rm -f /tmp/test-output.txt

    # Assert breadcrumb is present
    [[ "$RESPONSE" == *"breadcrumb"* ]]
    [[ "$RESPONSE" == *"Projects"* ]]
    [[ "$RESPONSE" == *"TEST-1"* ]]
}

@test "GET /worktrees/:issueId/detail-content returns HTML fragment" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > "$TEST_DIR/test-output.txt" 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" "$TEST_DIR/test-output.txt" 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; echo "Server did not start in time"; return 1; }

    # Register a worktree via the API
    REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://localhost:$PORT/api/v1/worktrees/TEST-FRAG" \
        -H "Content-Type: application/json" \
        -d '{"path":"/tmp/test-project-TEST-FRAG","trackerType":"github","team":"test-org/test-project"}')
    [[ "$REG_STATUS" == "200" || "$REG_STATUS" == "201" ]] || {
        kill "$SERVER_PID" 2>/dev/null
        echo "Worktree registration failed with status $REG_STATUS"; return 1
    }

    # Fetch the detail-content fragment
    RESPONSE=$(curl -s -w "\n%{http_code}" "http://localhost:$PORT/worktrees/TEST-FRAG/detail-content")
    HTTP_STATUS=$(echo "$RESPONSE" | tail -1)
    BODY=$(echo "$RESPONSE" | sed '$d')

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true

    # Assert 200 status code
    [ "$HTTP_STATUS" -eq 200 ]

    # Assert response is a fragment (no html or head tags)
    [[ "$BODY" != *"<html"* ]]
    [[ "$BODY" != *"<head"* ]]

    # Assert response contains content section
    [[ "$BODY" == *"worktree-detail-content"* ]] || [[ "$BODY" == *"skeleton"* ]] || [[ "$BODY" == *"Loading"* ]]
}

@test "GET /worktrees/:issueId contains HTMX polling attributes" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > "$TEST_DIR/test-output.txt" 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" "$TEST_DIR/test-output.txt" 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; echo "Server did not start in time"; return 1; }

    # Register a worktree via the API
    REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://localhost:$PORT/api/v1/worktrees/TEST-HTMX" \
        -H "Content-Type: application/json" \
        -d '{"path":"/tmp/test-project-TEST-HTMX","trackerType":"github","team":"test-org/test-project"}')
    [[ "$REG_STATUS" == "200" || "$REG_STATUS" == "201" ]] || {
        kill "$SERVER_PID" 2>/dev/null
        echo "Worktree registration failed with status $REG_STATUS"; return 1
    }

    # Fetch the worktree detail page
    RESPONSE=$(curl -s "http://localhost:$PORT/worktrees/TEST-HTMX")

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true

    # Assert HTMX polling attributes are present with correct values
    [[ "$RESPONSE" == *'hx-get="/worktrees/TEST-HTMX/detail-content"'* ]]
    [[ "$RESPONSE" == *"hx-trigger="* ]]
    [[ "$RESPONSE" == *"hx-swap="* ]]
}

@test "GET /worktrees/NONEXISTENT-999/detail-content returns 404" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > "$TEST_DIR/test-output.txt" 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" "$TEST_DIR/test-output.txt" 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; echo "Server did not start in time"; return 1; }

    # Hit the detail-content endpoint for a non-existent worktree
    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/worktrees/NONEXISTENT-999/detail-content")

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true

    # Assert 404 status code
    [ "$HTTP_STATUS" -eq 404 ]
}

@test "artifact link from worktree detail page loads artifact content" {
    # Create a temp worktree directory with an artifact file
    WORKTREE_DIR=$(mktemp -d)
    echo "# Test Artifact" > "$WORKTREE_DIR/analysis.md"

    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > "$TEST_DIR/test-output.txt" 2>&1 &
    SERVER_PID=$!

    # Extract port from server output
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" "$TEST_DIR/test-output.txt" 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; rm -rf "$WORKTREE_DIR"; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; rm -rf "$WORKTREE_DIR"; echo "Server did not start in time"; return 1; }

    # Register a worktree pointing at our temp directory
    REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://localhost:$PORT/api/v1/worktrees/TEST-ART" \
        -H "Content-Type: application/json" \
        -d "{\"path\":\"$WORKTREE_DIR\",\"trackerType\":\"github\",\"team\":\"test-org/test-project\"}")
    [[ "$REG_STATUS" == "200" || "$REG_STATUS" == "201" ]] || {
        kill "$SERVER_PID" 2>/dev/null; rm -rf "$WORKTREE_DIR"
        echo "Worktree registration failed with status $REG_STATUS"; return 1
    }

    # Fetch the artifact page, capturing both body and status code in a single request
    ARTIFACT_RESPONSE=$(curl -s -w "\n%{http_code}" \
        "http://localhost:$PORT/worktrees/TEST-ART/artifacts?path=analysis.md")
    ARTIFACT_STATUS=$(echo "$ARTIFACT_RESPONSE" | tail -1)
    ARTIFACT_BODY=$(echo "$ARTIFACT_RESPONSE" | sed '$d')

    # Kill server and cleanup
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true
    rm -rf "$WORKTREE_DIR"

    # Assert artifact page loaded successfully
    [ "$ARTIFACT_STATUS" -eq 200 ]

    # Assert artifact content is present
    [[ "$ARTIFACT_BODY" == *"Test Artifact"* ]]

    # Assert back link points to worktree detail page, not root
    [[ "$ARTIFACT_BODY" == *'href="/worktrees/TEST-ART"'* ]]

    # Assert back link text says "Back to Worktree"
    [[ "$ARTIFACT_BODY" == *"Back to Worktree"* ]]
}

@test "worktree card contains detail page link" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > "$TEST_DIR/test-output.txt" 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" "$TEST_DIR/test-output.txt" 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; echo "Server did not start in time"; return 1; }

    # Register a worktree via API
    REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://localhost:$PORT/api/v1/worktrees/TEST-LINK" \
        -H "Content-Type: application/json" \
        -d '{"path":"/tmp/test-project-TEST-LINK","trackerType":"github","team":"test-org/test-project"}')
    [[ "$REG_STATUS" == "200" || "$REG_STATUS" == "201" ]] || {
        kill "$SERVER_PID" 2>/dev/null
        echo "Worktree registration failed with status $REG_STATUS"; return 1
    }

    # Fetch the card endpoint to get card HTML
    RESPONSE=$(curl -s "http://localhost:$PORT/worktrees/TEST-LINK/card")

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true

    # Assert card title links to detail page (href="/worktrees/TEST-LINK" pattern in h3)
    [[ "$RESPONSE" == *'href="/worktrees/TEST-LINK"'* ]]
}

@test "worktree card still contains external tracker link section" {
    # Start server with --dev, capture output (includes dynamic port)
    "$PROJECT_ROOT/iw" dashboard --dev > "$TEST_DIR/test-output.txt" 2>&1 &
    SERVER_PID=$!

    # Extract port from server output (line like "  - Port: 12345")
    PORT=""
    for i in $(seq 1 20); do
        sleep 0.5
        PORT=$(grep -o "Port: [0-9]*" "$TEST_DIR/test-output.txt" 2>/dev/null | grep -o "[0-9]*" | head -1)
        [ -n "$PORT" ] && break
    done

    # Verify we found the port
    [ -n "$PORT" ] || { kill "$SERVER_PID" 2>/dev/null; echo "Could not determine server port"; return 1; }

    # Wait for server health endpoint to respond
    READY=0
    for i in $(seq 1 20); do
        sleep 0.5
        if curl -s -o /dev/null -w "%{http_code}" "http://localhost:$PORT/health" 2>/dev/null | grep -q "200"; then
            READY=1
            break
        fi
    done

    [ "$READY" -eq 1 ] || { kill "$SERVER_PID" 2>/dev/null; echo "Server did not start in time"; return 1; }

    # Register a worktree via API
    REG_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "http://localhost:$PORT/api/v1/worktrees/TEST-DUAL" \
        -H "Content-Type: application/json" \
        -d '{"path":"/tmp/test-project-TEST-DUAL","trackerType":"github","team":"test-org/test-project"}')
    [[ "$REG_STATUS" == "200" || "$REG_STATUS" == "201" ]] || {
        kill "$SERVER_PID" 2>/dev/null
        echo "Worktree registration failed with status $REG_STATUS"; return 1
    }

    # Fetch the card endpoint to get card HTML
    RESPONSE=$(curl -s "http://localhost:$PORT/worktrees/TEST-DUAL/card")

    # Kill server
    kill "$SERVER_PID" 2>/dev/null || true
    wait "$SERVER_PID" 2>/dev/null || true

    # Assert both detail page link and issue-id section are present
    [[ "$RESPONSE" == *'href="/worktrees/TEST-DUAL"'* ]]
    [[ "$RESPONSE" == *'class="issue-id"'* ]]
}
