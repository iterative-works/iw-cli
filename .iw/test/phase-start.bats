#!/usr/bin/env bats
# PURPOSE: End-to-end tests for iw phase-start command
# PURPOSE: Tests phase sub-branch creation, JSON output, push behavior, and error cases

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

setup() {
    # Disable dashboard server communication during tests
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    BARE_REMOTE="$(mktemp -d)"
    cd "$TEST_DIR"

    # Create a bare remote for push operations
    git init -q --bare "$BARE_REMOTE" 2>/dev/null

    # Create a git repo on a feature branch
    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    git remote add origin "$BARE_REMOTE"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    # Push main branch to the bare remote
    git push -q -u origin HEAD 2>/dev/null

    # Create and switch to a feature branch
    git checkout -q -b TEST-100
    git push -q -u origin TEST-100 2>/dev/null

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
    rm -rf "$TEST_DIR" "$BARE_REMOTE"
}

@test "phase-start 1 creates sub-branch and outputs valid JSON" {
    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 0 ]

    # Output should be valid JSON parseable by jq
    echo "$output" | jq . > /dev/null

    # Check required JSON fields
    local issueId
    issueId="$(echo "$output" | jq -r '.issueId')"
    [ "$issueId" = "TEST-100" ]

    local phaseNumber
    phaseNumber="$(echo "$output" | jq -r '.phaseNumber')"
    [ "$phaseNumber" = "01" ]

    local branch
    branch="$(echo "$output" | jq -r '.branch')"
    [ "$branch" = "TEST-100-phase-01" ]

    # baselineSha should be non-empty
    local baselineSha
    baselineSha="$(echo "$output" | jq -r '.baselineSha')"
    [ -n "$baselineSha" ]
}

@test "phase-start 1 creates branch named {issue}-phase-01" {
    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 0 ]

    # Verify we're now on the phase branch
    local currentBranch
    currentBranch="$(git branch --show-current)"
    [ "$currentBranch" = "TEST-100-phase-01" ]
}

@test "phase-start with no args exits with error and usage message" {
    run "$PROJECT_ROOT/iw" phase-start

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase-number"* ]] || [[ "$output" == *"Usage"* ]] || [[ "$output" == *"phase number"* ]]
}

@test "phase-start abc exits with error for invalid phase number" {
    run "$PROJECT_ROOT/iw" phase-start abc

    [ "$status" -eq 1 ]
    [[ "$output" == *"Invalid phase number"* ]] || [[ "$output" == *"invalid"* ]]
}

@test "phase-start 1 when already on a phase sub-branch exits with error" {
    # Create and switch to a phase branch
    git checkout -q -b TEST-100-phase-01

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 1 ]
    [[ "$output" == *"phase"* ]]
}

@test "phase-start 1 when phase branch already exists exits with error" {
    # Create the phase branch already
    git branch TEST-100-phase-01

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 1 ]
}

@test "phase-start 2 with --issue-id TEST-999 uses provided issue ID in output" {
    run "$PROJECT_ROOT/iw" phase-start 2 --issue-id TEST-999

    [ "$status" -eq 0 ]

    local issueId
    issueId="$(echo "$output" | jq -r '.issueId')"
    [ "$issueId" = "TEST-999" ]

    local branch
    branch="$(echo "$output" | jq -r '.branch')"
    [ "$branch" = "TEST-100-phase-02" ]
}

@test "phase-start commits review-state.json and leaves clean working tree" {
    # Set up review-state.json on the feature branch
    mkdir -p "project-management/issues/TEST-100"
    cat > "project-management/issues/TEST-100/review-state.json" << 'EOF'
{
  "version": 2,
  "issue_id": "TEST-100",
  "status": "tasks_ready",
  "artifacts": []
}
EOF
    git add "project-management/issues/TEST-100/review-state.json"
    git commit -q -m "Add review-state"

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 0 ]

    # Working tree must be clean — review-state.json must be committed, not dirty
    [ -z "$(git status --porcelain -- "project-management/issues/TEST-100/review-state.json")" ]

    # The committed blob must contain "implementing"
    local committed_content
    committed_content="$(git show HEAD:project-management/issues/TEST-100/review-state.json)"
    [[ "$committed_content" == *"implementing"* ]]

    # A commit with "update review-state" message must exist
    local commit_sha
    commit_sha="$(git log --oneline --grep="update review-state" -1 --format="%H")"
    [ -n "$commit_sha" ]
    git show "$commit_sha" --name-only | grep -q "review-state.json"
}

@test "phase-start pushes feature branch to origin before creating sub-branch" {
    # Add a local commit that hasn't been pushed
    echo "unpushed content" > unpushed-file.txt
    git add unpushed-file.txt
    git commit -q -m "Unpushed local commit"

    local localSha
    localSha="$(git rev-parse HEAD)"

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 0 ]

    # The remote feature branch must have the local commit
    local remoteSha
    remoteSha="$(git -C "$BARE_REMOTE" rev-parse TEST-100)"
    [ "$remoteSha" = "$localSha" ]
}

@test "phase-start fails when push fails due to no remote" {
    # Remove the remote so push will fail
    git remote remove origin

    run "$PROJECT_ROOT/iw" phase-start 1

    [ "$status" -eq 1 ]

    # Should NOT have created the phase branch
    local currentBranch
    currentBranch="$(git branch --show-current)"
    [ "$currentBranch" = "TEST-100" ]
}
