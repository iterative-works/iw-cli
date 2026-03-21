#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-merge command
# PURPOSE: Tests branch validation, GitHub and GitLab forge support, missing PR URL errors, and configurable timeout/polling

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a git repo
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    # Create feature branch and phase sub-branch
    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

    # Initialize iw config
    mkdir -p .iw
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = github
  repository = "test-org/test-repo"
  teamPrefix = "TEST"
}
EOF
}

teardown() {
    cd /
    rm -rf "$TEST_DIR"
}

@test "phase-merge when not on a phase branch exits with error" {
    git checkout -q TEST-100

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-merge without config file exits with error about missing config" {
    rm -f .iw/config.conf

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"config"* ]] || [[ "$output" == *"init"* ]]
}

@test "phase-merge with GitLab forge type happy path merges MR and updates review-state to phase_merged" {
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = gitlab
  repository = "test-group/test-repo"
  teamPrefix = "TEST"
}
EOF

    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://gitlab.com/test-group/test-repo/-/merge_requests/42",
  "artifacts": []
}
EOF

    # Create a fake remote to allow checkout/fetch operations
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Create mock glab script dispatching on $1 and $2
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/glab" << GLABEOF
#!/usr/bin/env bash
echo "\$*" >> "$TEST_DIR/glab-calls.log"
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
fi
if [[ "\$1" == "api" ]]; then
    if [[ "\$2" == *"jobs"* ]]; then
        echo '[{"id":1,"name":"test","status":"success","web_url":"https://gitlab.com/-/jobs/1"}]'
        exit 0
    fi
    if [[ "\$2" == *"pipelines"* ]]; then
        echo '[{"id":100,"status":"success","ref":"TEST-100-phase-01","sha":"abc123"}]'
        exit 0
    fi
fi
if [[ "\$1" == "mr" && "\$2" == "merge" ]]; then
    exit 0
fi
echo "Unexpected glab call: \$*" >&2
exit 1
GLABEOF
    chmod +x "$TEST_DIR/mock-bin/glab"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 0 ]

    # Verify review-state was updated to phase_merged
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"phase_merged"* ]]

    # Verify JSON output contains expected fields
    [[ "$output" == *"TEST-100"* ]]

    # Verify mock glab was actually called
    [ -f "$TEST_DIR/glab-calls.log" ]

    # Verify the merge was called with the MR URL
    grep -q "mr merge.*merge_requests/42" "$TEST_DIR/glab-calls.log"
}

@test "phase-merge with GitLab forge type CI failure exits non-zero and reports failed job name" {
    cat > .iw/config.conf << 'EOF'
project {
  name = testproject
}

tracker {
  type = gitlab
  repository = "test-group/test-repo"
  teamPrefix = "TEST"
}
EOF

    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://gitlab.com/test-group/test-repo/-/merge_requests/42",
  "artifacts": []
}
EOF

    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/glab" << GLABEOF
#!/usr/bin/env bash
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to gitlab.com"
    exit 0
fi
if [[ "\$1" == "api" ]]; then
    if [[ "\$2" == *"jobs"* ]]; then
        echo '[{"id":2,"name":"lint","status":"failed","web_url":"https://gitlab.com/-/jobs/2"}]'
        exit 0
    fi
    if [[ "\$2" == *"pipelines"* ]]; then
        echo '[{"id":100,"status":"failed","ref":"TEST-100-phase-01","sha":"abc123"}]'
        exit 0
    fi
fi
echo "Unexpected glab call: \$*" >&2
exit 1
GLABEOF
    chmod +x "$TEST_DIR/mock-bin/glab"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge --max-retries 0

    [ "$status" -eq 1 ]
    [[ "$output" == *"lint"* ]]
}

@test "phase-merge with missing review-state.json exits with error" {
    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"review-state"* ]]
}

@test "phase-merge with review-state.json missing pr_url exits with error" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review"
}
EOF

    run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"pr_url"* ]] || [[ "$output" == *"phase-pr"* ]]
}

@test "phase-merge happy path: review-state.json is committed and working tree is clean after merge" {
    # Create a committed review-state.json on the feature branch (TEST-100) and push it.
    # fetchAndReset resets to origin/TEST-100, so the file must be there to survive the reset.
    git checkout -q TEST-100
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF
    git add "project-management/issues/TEST-100/review-state.json"
    git commit -q -m "Add review-state"
    # Switch back to phase sub-branch (phase-merge expects to run from here)
    git checkout -q TEST-100-phase-01
    git merge -q TEST-100 2>/dev/null

    # Create a fake remote to allow checkout/fetch operations
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Create a mock gh that handles checks and merge, logging calls for verification
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
echo "\$1 \$2" >> "$TEST_DIR/gh-calls.log"
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"test","state":"SUCCESS"}]'
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "merge" ]]; then
    exit 0
fi
echo "Unexpected gh call: \$*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 0 ]

    # Verify gh pr merge was actually called
    [ -f "$TEST_DIR/gh-calls.log" ]
    grep -q "pr merge" "$TEST_DIR/gh-calls.log"

    # Working tree must be clean — review-state.json must be committed, not dirty
    [ -z "$(git status --porcelain -- "project-management/issues/TEST-100/review-state.json")" ]

    # The committed blob must contain phase_merged (not just the working-tree file)
    local committed_content
    committed_content="$(git show HEAD:project-management/issues/TEST-100/review-state.json)"
    [[ "$committed_content" == *"phase_merged"* ]]

    # The review-state commit must be findable by its message
    local commit_sha
    commit_sha="$(git log --oneline --grep="update review-state" -1 --format="%H")"
    [ -n "$commit_sha" ]
    git show "$commit_sha" --name-only | grep -q "review-state.json"
}

@test "phase-merge happy path merges PR and updates review-state to phase_merged" {
    # Create review state with pr_url and required artifacts field
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Create a fake remote to allow checkout/fetch operations
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Create a mock gh script that simulates all-passing checks and successful merge
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
echo "\$1 \$2" >> "$TEST_DIR/gh-calls.log"
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"test","state":"SUCCESS"}]'
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "merge" ]]; then
    exit 0
fi
echo "Unexpected gh call: \$*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 0 ]

    # Verify review-state was updated to phase_merged
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"phase_merged"* ]]

    # Verify JSON output contains expected fields
    [[ "$output" == *"TEST-100"* ]]
    [[ "$output" == *"phase_merged"* ]] || [[ "$output" == *"featureBranch"* ]]

    # Verify mock gh was actually called
    [ -f "$TEST_DIR/gh-calls.log" ]
}

@test "phase-merge with failing CI checks exits non-zero and reports failed checks" {
    # Create review state with pr_url and required artifacts field
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Create a mock gh script that simulates failing checks and logs calls
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
echo "\$1 \$2" >> "$TEST_DIR/gh-calls.log"
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"lint","state":"FAILURE"}]'
    exit 0
fi
echo "Unexpected gh call: \$*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 1 ]
    [[ "$output" == *"lint"* ]]

    # Verify mock gh was actually called
    [ -f "$TEST_DIR/gh-calls.log" ]
}

@test "phase-merge --timeout triggers timeout and sets review-state activity to waiting" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Mock gh always returns pending checks so timeout fires
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"build","state":"PENDING"}]'
    exit 0
fi
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge --timeout 2s --poll-interval 1s

    [ "$status" -eq 1 ]
    [[ "$output" == *"Timed out"* ]] || [[ "$output" == *"timed out"* ]]

    # Review-state should have activity: "waiting"
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"waiting"* ]]
}

@test "phase-merge --poll-interval succeeds with pending then passing checks" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Create a fake remote to allow checkout/fetch operations
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    # Mock gh: first pr checks call returns pending, second returns success
    # Note: double-quoted heredoc so $TEST_DIR is expanded now; $1/$2 are escaped
    mkdir -p "$TEST_DIR/mock-bin"
    local call_count_file="$TEST_DIR/checks-calls.txt"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "checks" ]]; then
    count=0
    [ -f "$call_count_file" ] && count="\$(cat "$call_count_file")"
    count=\$((count + 1))
    echo "\$count" > "$call_count_file"
    if [ "\$count" -eq 1 ]; then
        echo '[{"link":"https://ci.example.com/1","name":"build","state":"PENDING"}]'
    else
        echo '[{"link":"https://ci.example.com/1","name":"build","state":"SUCCESS"}]'
    fi
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "merge" ]]; then
    exit 0
fi
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge --poll-interval 1s --timeout 10s

    [ "$status" -eq 0 ]

    # Verify review-state updated to phase_merged
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"phase_merged"* ]]
}

@test "phase-merge --timeout with invalid duration exits non-zero with parse error" {
    run "$PROJECT_ROOT/iw" phase-merge --timeout abc

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]] || [[ "$output" == *"invalid"* ]]
}

@test "phase-merge --max-retries with invalid value exits non-zero with parse error" {
    run "$PROJECT_ROOT/iw" phase-merge --max-retries abc

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]] || [[ "$output" == *"invalid"* ]]
}

@test "phase-merge --max-retries with negative value exits non-zero with parse error" {
    run "$PROJECT_ROOT/iw" phase-merge --max-retries -1

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid"* ]] || [[ "$output" == *"invalid"* ]]
}

@test "phase-merge --max-retries 0 exits non-zero immediately without invoking agent" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"lint","state":"FAILURE"}]'
    exit 0
fi
echo "Unexpected gh call: $*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    cat > "$TEST_DIR/mock-bin/claude" << CLAUDEOF
#!/usr/bin/env bash
echo "CLAUDE_INVOKED" >> "$TEST_DIR/claude-calls.log"
exit 0
CLAUDEOF
    chmod +x "$TEST_DIR/mock-bin/claude"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge --max-retries 0

    [ "$status" -eq 1 ]

    # Verify output contains giving-up message and failed check name
    [[ "$output" == *"Giving up"* ]] || [[ "$output" == *"still failing"* ]]
    [[ "$output" == *"lint"* ]]

    # Verify review-state has activity: "waiting"
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"waiting"* ]]

    # Verify agent was NOT invoked
    [ ! -f "$TEST_DIR/claude-calls.log" ]
}

@test "phase-merge agent recovery succeeds: CI fails then passes after agent runs" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    # Create a fake remote to allow checkout/fetch operations
    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    local checks_call_count_file="$TEST_DIR/checks-calls.txt"
    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << GHEOF
#!/usr/bin/env bash
if [[ "\$1" == "auth" && "\$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "checks" ]]; then
    count=0
    [ -f "$checks_call_count_file" ] && count="\$(cat "$checks_call_count_file")"
    count=\$((count + 1))
    echo "\$count" > "$checks_call_count_file"
    if [ "\$count" -eq 1 ]; then
        echo '[{"link":"https://ci.example.com/1","name":"lint","state":"FAILURE"}]'
    else
        echo '[{"link":"https://ci.example.com/1","name":"lint","state":"SUCCESS"}]'
    fi
    exit 0
fi
if [[ "\$1" == "pr" && "\$2" == "merge" ]]; then
    exit 0
fi
echo "Unexpected gh call: \$*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    cat > "$TEST_DIR/mock-bin/claude" << CLAUDEOF
#!/usr/bin/env bash
echo "CLAUDE_INVOKED" >> "$TEST_DIR/claude-calls.log"
exit 0
CLAUDEOF
    chmod +x "$TEST_DIR/mock-bin/claude"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge --timeout 30s --poll-interval 1s --max-retries 1

    [ "$status" -eq 0 ]

    # Verify agent was invoked exactly once
    [ -f "$TEST_DIR/claude-calls.log" ]
    local invocation_count
    invocation_count="$(wc -l < "$TEST_DIR/claude-calls.log")"
    [ "$invocation_count" -eq 1 ]

    # Verify output contains recovery message
    [[ "$output" == *"CI Fixing"* ]] || [[ "$output" == *"recovery agent"* ]]

    # Verify review-state updated to phase_merged
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"phase_merged"* ]]
}

@test "phase-merge retries exhausted: review-state has activity waiting and exits non-zero" {
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "awaiting_review",
  "pr_url": "https://github.com/test-org/test-repo/pull/42",
  "artifacts": []
}
EOF

    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"lint","state":"FAILURE"}]'
    exit 0
fi
echo "Unexpected gh call: $*" >&2
exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    cat > "$TEST_DIR/mock-bin/claude" << CLAUDEOF
#!/usr/bin/env bash
echo "CLAUDE_INVOKED" >> "$TEST_DIR/claude-calls.log"
exit 0
CLAUDEOF
    chmod +x "$TEST_DIR/mock-bin/claude"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge --timeout 30s --poll-interval 1s --max-retries 1

    [ "$status" -eq 1 ]

    # Verify agent was invoked exactly once
    [ -f "$TEST_DIR/claude-calls.log" ]
    local invocation_count
    invocation_count="$(wc -l < "$TEST_DIR/claude-calls.log")"
    [ "$invocation_count" -eq 1 ]

    # Verify output contains exhaustion message
    [[ "$output" == *"Giving up"* ]] || [[ "$output" == *"exhausted"* ]] || [[ "$output" == *"still failing"* ]]

    # Verify review-state has activity: "waiting"
    local state
    state="$(cat "project-management/issues/TEST-100/review-state.json")"
    [[ "$state" == *"waiting"* ]]
}
