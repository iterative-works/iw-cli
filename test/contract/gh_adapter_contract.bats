#!/usr/bin/env bats
# PURPOSE: Pin assumptions iw-cli's GitHubClient makes about the gh CLI surface
# PURPOSE: (--json field names on `issue view`, `issue list`, `pr checks`).

load contract_helper

# Public, stable fixture for JSON-shape tests.
GH_FIXTURE_REPO="iterative-works/iw-cli"
GH_FIXTURE_ISSUE="1"

@test "gh: binary on PATH" {
    run command -v gh
    [ "$status" -eq 0 ]
}

@test "gh --version exits 0" {
    run gh --version
    [ "$status" -eq 0 ]
    [[ "$output" == *"gh version"* ]]
}

@test "gh issue view --help advertises --json flag" {
    run gh issue view --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--json"* ]]
}

@test "gh issue list --help advertises --json and --state flags" {
    run gh issue list --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--json"* ]]
    [[ "$output" == *"--state"* ]]
}

@test "gh pr checks --help advertises --json flag" {
    run gh pr checks --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--json"* ]]
}

@test "gh issue create --help advertises --repo, --title, --body, --label" {
    run gh issue create --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--repo"* ]]
    [[ "$output" == *"--title"* ]]
    [[ "$output" == *"--body"* ]]
    [[ "$output" == *"--label"* ]]
}

# ----- Auth-gated: probe real JSON shapes -----

@test "gh issue view --json returns the keys iw-cli reads (number,title,state,assignees,body)" {
    require_gh_auth
    run gh issue view "$GH_FIXTURE_ISSUE" \
        --repo "$GH_FIXTURE_REPO" \
        --json number,title,state,assignees,body
    [ "$status" -eq 0 ]
    # Each key MUST be present in the response with the expected JSON type.
    local missing
    missing="$(jq -r '
        [
            (if has("number") and (.number | type) == "number" then empty else "number" end),
            (if has("title")  and (.title  | type) == "string" then empty else "title"  end),
            (if has("state")  and (.state  | type) == "string" then empty else "state"  end),
            (if has("assignees") and (.assignees | type) == "array" then empty else "assignees" end),
            (if has("body")   and (.body   | type) == "string" then empty else "body"   end)
        ] | join(",")
    ' <<<"$output")"
    [ -z "$missing" ] || {
        echo "missing or wrong-typed keys: $missing"
        return 1
    }
}

@test "gh issue list --json returns the keys iw-cli reads (number,title,state,updatedAt)" {
    require_gh_auth
    run gh issue list \
        --repo "$GH_FIXTURE_REPO" \
        --state open \
        --limit 1 \
        --json number,title,state,updatedAt
    [ "$status" -eq 0 ]
    # Response is an array; check first element if present, accept empty arrays.
    local count
    count="$(jq 'length' <<<"$output")"
    if [ "$count" -gt 0 ]; then
        run jq -e '.[0] | has("number") and has("title") and has("state") and has("updatedAt")' <<<"$output"
        [ "$status" -eq 0 ]
    fi
}
