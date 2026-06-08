#!/usr/bin/env bats
# PURPOSE: E2E smoke for iw phase-merge (full round-trip through scala-cli + live adapters)
# PURPOSE: Detailed scenarios live in core/test/PhaseMergeHarnessTest.scala

PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/.." && pwd)"

setup() {
    export IW_SERVER_DISABLED=1

    TEST_DIR="$(mktemp -d)"
    cd "$TEST_DIR"

    git init -q 2>/dev/null
    git config user.email "test@example.com"
    git config user.name "Test User"
    echo "initial" > README.md
    git add README.md 2>/dev/null
    git commit -q -m "Initial commit" 2>/dev/null

    git checkout -q -b TEST-100
    git checkout -q -b TEST-100-phase-01

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

@test "phase-merge happy path: real git + mock gh, merges PR and prints JSON" {
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

    git checkout -q TEST-100-phase-01
    git merge -q TEST-100 2>/dev/null

    git init -q --bare "$TEST_DIR/remote.git" 2>/dev/null
    git remote add origin "$TEST_DIR/remote.git"
    git push -q origin TEST-100 TEST-100-phase-01 2>/dev/null

    mkdir -p "$TEST_DIR/mock-bin"
    cat > "$TEST_DIR/mock-bin/gh" << 'GHEOF'
#!/usr/bin/env bash
if [[ "$1" == "auth" && "$2" == "status" ]]; then
    echo "Logged in to github.com"; exit 0
fi
if [[ "$1" == "pr" && "$2" == "checks" ]]; then
    echo '[{"link":"https://ci.example.com/1","name":"test","state":"SUCCESS"}]'
    exit 0
fi
if [[ "$1" == "pr" && "$2" == "merge" ]]; then exit 0; fi
echo "Unexpected gh call: $*" >&2; exit 1
GHEOF
    chmod +x "$TEST_DIR/mock-bin/gh"

    PATH="$TEST_DIR/mock-bin:$PATH" run "$PROJECT_ROOT/iw" phase-merge

    [ "$status" -eq 0 ]
    [ -z "$(git status --porcelain -- "project-management/issues/TEST-100/review-state.json")" ]
    grep -q "phase_merged" "project-management/issues/TEST-100/review-state.json"
}
