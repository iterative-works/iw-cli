#!/usr/bin/env bats
# PURPOSE: Pin assumptions iw-cli's GitLabClient makes about the glab CLI surface
# PURPOSE: (--output json on `issue view`, expected JSON keys: iid,title,state,assignees,description).

load contract_helper

@test "glab: binary on PATH" {
    run command -v glab
    [ "$status" -eq 0 ]
}

@test "glab --version exits 0" {
    run glab --version
    [ "$status" -eq 0 ]
    [[ "$output" == *"glab"* ]]
}

@test "glab issue view --help advertises --output and --repo flags" {
    run glab issue view --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--output"* ]]
    [[ "$output" == *"--repo"* ]]
}

@test "glab issue list --help advertises --output flag" {
    run glab issue list --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--output"* ]]
}

@test "glab issue create --help advertises --repo, --title, --description, --label" {
    run glab issue create --help
    [ "$status" -eq 0 ]
    [[ "$output" == *"--repo"* ]]
    [[ "$output" == *"--title"* ]]
    [[ "$output" == *"--description"* ]]
    [[ "$output" == *"--label"* ]]
}

# ----- Auth- and fixture-gated: probe real JSON shapes -----

@test "glab issue view --output json returns the keys iw-cli reads (iid,title,state,assignees,description)" {
    require_glab_auth
    require_glab_project
    local fixture="${IW_CONTRACT_GLAB_PROJECT}"
    local issue="${IW_CONTRACT_GLAB_ISSUE:-1}"
    run glab issue view "$issue" --repo "$fixture" --output json
    [ "$status" -eq 0 ]
    local missing
    missing="$(jq -r '
        [
            (if has("iid")   and (.iid   | type) == "number" then empty else "iid"   end),
            (if has("title") and (.title | type) == "string" then empty else "title" end),
            (if has("state") and (.state | type) == "string" then empty else "state" end),
            (if has("assignees")   and (.assignees   | type) == "array"  then empty else "assignees"   end),
            (if has("description") and (.description | type) == "string" then empty else "description" end)
        ] | join(",")
    ' <<<"$output")"
    [ -z "$missing" ] || {
        echo "missing or wrong-typed keys: $missing"
        return 1
    }
}
