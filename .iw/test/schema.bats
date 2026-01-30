#!/usr/bin/env bats
# PURPOSE: E2E tests for JSON Schema files
# PURPOSE: Verifies schema files exist and are valid JSON

# Get the project root directory (parent of .iw)
PROJECT_ROOT="$(cd "$(dirname "$BATS_TEST_FILENAME")/../.." && pwd)"

@test "review-state schema file exists" {
    [ -f "$PROJECT_ROOT/schemas/review-state.schema.json" ]
}

@test "review-state schema is valid JSON" {
    run jq . "$PROJECT_ROOT/schemas/review-state.schema.json"
    [ "$status" -eq 0 ]
}

@test "review-state schema declares Draft-07" {
    schema_ref=$(jq -r '.["$schema"]' "$PROJECT_ROOT/schemas/review-state.schema.json")
    [ "$schema_ref" = "http://json-schema.org/draft-07/schema#" ]
}

@test "review-state schema requires version, issue_id, artifacts, last_updated" {
    required=$(jq -c '.required | sort' "$PROJECT_ROOT/schemas/review-state.schema.json")
    [ "$required" = '["artifacts","issue_id","last_updated","version"]' ]
}

@test "review-state schema defines all expected properties" {
    # Check required properties exist
    run jq '[.properties | has("version", "issue_id", "artifacts", "last_updated")] | all' \
        "$PROJECT_ROOT/schemas/review-state.schema.json"
    [ "$status" -eq 0 ]
    [ "$output" = "true" ]

    # Check optional properties exist (v2 schema)
    run jq '[.properties | has("status", "display", "badges", "task_lists", "needs_attention", "message", "pr_url", "git_sha", "phase_checkpoints", "available_actions")] | all' \
        "$PROJECT_ROOT/schemas/review-state.schema.json"
    [ "$status" -eq 0 ]
    [ "$output" = "true" ]
}

@test "review-state test fixtures are valid JSON" {
    for fixture in "$PROJECT_ROOT/.iw/core/test/resources/review-state/"*.json; do
        run jq . "$fixture"
        [ "$status" -eq 0 ]
    done
}
