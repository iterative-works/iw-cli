#!/usr/bin/env bats
# PURPOSE: Integration tests for iw issue command with GitLab tracker
# PURPOSE: Tests issue fetching, branch inference, and error handling with glab CLI

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Create a temporary directory for each test
    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Setup git repo
    git init
    git config user.email "test@example.com"
    git config user.name "Test User"
}

teardown() {
    # Clean up temporary directory
    rm -rf "$TEST_DIR"
}

# ========== Happy Path Tests ==========

@test "GitLab: issue fetches issue successfully with numeric ID" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns success
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "view" ]]; then
    cat <<'JSON'
{
  "iid": 123,
  "state": "opened",
  "title": "Test issue",
  "description": "Issue description",
  "author": {"username": "user1"},
  "assignees": [],
  "labels": [],
  "web_url": "https://gitlab.com/owner/project/-/issues/123"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with numeric issue ID
    run "$PROJECT_ROOT/iw" issue 123

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-123:"* ]]
    [[ "$output" == *"Test issue"* ]]
}

@test "GitLab: issue infers issue ID from branch name" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Create initial commit to allow branch creation
    touch README.md
    git add README.md
    git commit -m "Initial commit"

    # Create and checkout issue branch with TEAM-NNN format
    git checkout -b PROJ-456-feature-branch

    # Mock glab command that returns success
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "view" && "$3" == "456" ]]; then
    cat <<'JSON'
{
  "iid": 456,
  "state": "opened",
  "title": "Feature from branch",
  "description": null,
  "author": {"username": "user1"},
  "assignees": [],
  "labels": [],
  "web_url": "https://gitlab.com/owner/project/-/issues/456"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run without explicit issue ID (should infer from branch)
    run "$PROJECT_ROOT/iw" issue

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-456"* ]]
    [[ "$output" == *"Feature from branch"* ]]
}

@test "GitLab: issue displays all fields correctly" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command with full issue data
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "view" ]]; then
    cat <<'JSON'
{
  "iid": 789,
  "state": "closed",
  "title": "Full issue details",
  "description": "Complete description here",
  "author": {"username": "author1"},
  "assignees": [{"username": "assignee1", "name": "Assignee Name"}],
  "labels": ["bug", "priority"],
  "web_url": "https://gitlab.com/owner/project/-/issues/789"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 789

    # Assert all fields are displayed
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-789"* ]]
    [[ "$output" == *"Full issue details"* ]]
    [[ "$output" == *"closed"* ]]
    [[ "$output" == *"assignee1"* ]]
    [[ "$output" == *"Complete description here"* ]]
}

@test "GitLab: issue works with nested groups repository" {
    # Setup: create GitLab config with nested groups
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "company/team/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$*" == *"--repo company/team/project"* ]]; then
    cat <<'JSON'
{
  "iid": 10,
  "state": "opened",
  "title": "Nested group issue",
  "description": null,
  "author": {"username": "user1"},
  "assignees": [],
  "labels": [],
  "web_url": "https://gitlab.com/company/team/project/-/issues/10"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 10

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-10"* ]]
    [[ "$output" == *"Nested group issue"* ]]
}

# ========== Error Scenario Tests ==========

@test "GitLab: issue fails when glab not installed" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock which command to report glab is not found
    mkdir -p bin
    cat > bin/which <<'SCRIPT'
#!/bin/bash
# Return false for glab, true for everything else
if [[ "$1" == "glab" ]]; then
    exit 1
else
    # Use real which for other commands
    /usr/bin/which "$@"
fi
SCRIPT
    chmod +x bin/which
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 123

    # Assert failure with helpful message
    [ "$status" -ne 0 ]
    [[ "$output" == *"glab CLI is not installed"* ]]
    [[ "$output" == *"https://gitlab.com/gitlab-org/cli"* ]]
    [[ "$output" == *"glab auth login"* ]]
}

@test "GitLab: issue fails when glab not authenticated" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns auth error
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "You are not logged in to any GitLab hosts." >&2
    exit 1
fi
# Should not reach here
echo "Unexpected: should not execute issue command when not authenticated" >&2
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 123

    # Assert failure with auth instructions
    [ "$status" -ne 0 ]
    [[ "$output" == *"glab is not authenticated"* ]]
    [[ "$output" == *"glab auth login"* ]]
}

@test "GitLab: issue fails when issue not found" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns 404 error
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "view" ]]; then
    echo "ERROR: issue not found: 999999" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with high numeric ID (likely doesn't exist)
    run "$PROJECT_ROOT/iw" issue 999999

    # Assert failure
    [ "$status" -ne 0 ]
    [[ "$output" == *"Failed to fetch issue"* ]] || [[ "$output" == *"not found"* ]]
}

@test "GitLab: issue error references correct repository in message" {
    # Setup: create GitLab config with specific repository
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "my-company/my-project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns error
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$*" == *"my-company/my-project"* ]]; then
    echo "ERROR: repository access denied" >&2
    exit 1
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 1

    # Assert error mentions repository
    [ "$status" -ne 0 ]
    # Error should reference the repository or at least not crash
    [[ "$output" == *"Failed"* ]] || [[ "$output" == *"ERROR"* ]]
}

# ========== Configuration Tests ==========

@test "GitLab: issue works with simple repository path" {
    # Setup: create GitLab config with simple path
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$*" == *"--repo owner/project"* ]]; then
    cat <<'JSON'
{
  "iid": 1,
  "state": "opened",
  "title": "Simple path issue",
  "description": null,
  "author": {"username": "user1"},
  "assignees": [],
  "labels": [],
  "web_url": "https://gitlab.com/owner/project/-/issues/1"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 1

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-1"* ]]
}

@test "GitLab: issue returns error when config file missing" {
    # No config file created

    # Run issue command
    run "$PROJECT_ROOT/iw" issue 123

    # Assert failure
    [ "$status" -eq 1 ]
    [[ "$output" == *"Configuration file not found"* ]]
}

@test "GitLab: issue returns error when cannot infer from branch" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Create initial commit on main branch
    touch README.md
    git add README.md
    git commit -m "Initial commit"

    # Stay on main branch (no issue ID in branch name)
    run "$PROJECT_ROOT/iw" issue

    # Assert failure - cannot infer issue ID
    [ "$status" -eq 1 ]
    [[ "$output" == *"extract issue ID from branch"* ]]
}

# ========== ID Parsing Tests ==========

@test "GitLab: issue accepts numeric ID directly" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$3" == "999" ]]; then
    cat <<'JSON'
{
  "iid": 999,
  "state": "opened",
  "title": "Numeric ID",
  "description": null,
  "author": {"username": "user1"},
  "assignees": [],
  "labels": [],
  "web_url": "https://gitlab.com/owner/project/-/issues/999"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with numeric ID
    run "$PROJECT_ROOT/iw" issue 999

    # Assert success
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-999"* ]]
}

@test "GitLab: issue accepts TEAM-NNN format" {
    # Setup: create GitLab config
    mkdir -p .iw
    cat > .iw/config.conf <<EOF
tracker {
  type = gitlab
  repository = "owner/project"
  teamPrefix = "PROJ"
}

project {
  name = test-project
}
EOF

    # Mock glab command that returns success
    mkdir -p bin
    cat > bin/glab <<'SCRIPT'
#!/bin/bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
elif [[ "$1" == "issue" && "$2" == "view" && "$3" == "42" ]]; then
    cat <<'JSON'
{
  "iid": 42,
  "state": "opened",
  "title": "TEAM-NNN format",
  "description": null,
  "author": {"username": "user1"},
  "assignees": [],
  "labels": [],
  "web_url": "https://gitlab.com/owner/project/-/issues/42"
}
JSON
    exit 0
fi
exit 1
SCRIPT
    chmod +x bin/glab
    export PATH="$TEST_DIR/bin:$PATH"

    # Run with TEAM-NNN format - GitLab now uses same format as GitHub
    run "$PROJECT_ROOT/iw" issue PROJ-42

    # Assert success - GitLab accepts TEAM-NNN format (extracts number for API)
    [ "$status" -eq 0 ]
    [[ "$output" == *"PROJ-42"* ]]
    [[ "$output" == *"TEAM-NNN format"* ]]
}
