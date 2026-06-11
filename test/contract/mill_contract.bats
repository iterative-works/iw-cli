#!/usr/bin/env bats
# PURPOSE: Pin assumptions iw-run makes about Mill (./mill show returns a JSON-quoted
# PURPOSE: "ref:vN:<hash>:<absolute jar path>" string that resolves to a readable jar).

load contract_helper

@test "mill: wrapper script is present and executable in repo root" {
    [ -x "$CONTRACT_PROJECT_ROOT/mill" ]
}

@test "mill --version exits 0 and reports a 1.x version" {
    run "$CONTRACT_PROJECT_ROOT/mill" --version
    [ "$status" -eq 0 ]
    [[ "$output" =~ 1\.[0-9]+\.[0-9]+ ]]
}

@test "mill show core.jar returns a ref:vN:<hash>:<path>.jar string" {
    run bash -c "cd '$CONTRACT_PROJECT_ROOT' && ./mill --ticker false show core.jar"
    [ "$status" -eq 0 ]
    # Output is JSON-quoted; strip quotes via jq and check the prefix shape.
    local decoded
    decoded="$(jq -r '.' <<<"$output")"
    [[ "$decoded" =~ ^ref:v[0-9]+:[a-f0-9]+:/.+\.jar$ ]]
    # Strip the prefix exactly the way iw-run does.
    local resolved
    resolved="$(sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##' <<<"$decoded")"
    [ -f "$resolved" ]
}

@test "mill show dashboard.assembly returns a ref-prefixed jar path that exists" {
    run bash -c "cd '$CONTRACT_PROJECT_ROOT' && ./mill --ticker false show dashboard.assembly"
    [ "$status" -eq 0 ]
    local decoded
    decoded="$(jq -r '.' <<<"$output")"
    [[ "$decoded" =~ ^ref:v[0-9]+:[a-f0-9]+:/.+\.jar$ ]]
    local resolved
    resolved="$(sed -E 's#^ref:v[0-9]+:[a-f0-9]+:##' <<<"$decoded")"
    [ -f "$resolved" ]
}

@test "mill show __.scoverage.xmlReport emits per-module XML targets" {
    # CI's coverage step depends on this target existing for core and dashboard.
    run bash -c "cd '$CONTRACT_PROJECT_ROOT' && ./mill --ticker false show __.scoverage.xmlReport"
    [ "$status" -eq 0 ]
    [[ "$output" == *"core/scoverage"* ]]
    [[ "$output" == *"dashboard/scoverage"* ]]
}
