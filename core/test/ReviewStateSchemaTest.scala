// PURPOSE: Verifies schemas/review-state.schema.json is well-formed Draft-07 JSON with the expected shape
// PURPOSE: Absorbs schema.bats scenarios that validated the schema file and review-state JSON fixtures

package iw.core.test

class ReviewStateSchemaTest extends munit.FunSuite:

  // Mill runs tests in out/<module>/test/.../sandbox. Find the repo root by
  // walking up to the directory containing build.mill so the test can read
  // the published schema and shared fixtures regardless of cwd.
  private val repoRoot =
    Iterator
      .iterate(os.pwd)(_ / os.up)
      .takeWhile(p => p != os.root)
      .find(p => os.exists(p / "build.mill"))
      .getOrElse(sys.error(s"repo root (build.mill) not found above ${os.pwd}"))

  private val schemaPath = repoRoot / "schemas" / "review-state.schema.json"
  private val fixturesDir =
    repoRoot / "core" / "test" / "resources" / "review-state"

  test("review-state schema file exists on disk"):
    assert(os.exists(schemaPath), s"missing $schemaPath")

  test("review-state schema parses as JSON"):
    val parsed = ujson.read(os.read(schemaPath))
    assert(parsed.objOpt.isDefined, "top-level is not a JSON object")

  test("review-state schema declares JSON Schema Draft-07"):
    val parsed = ujson.read(os.read(schemaPath))
    assertEquals(
      parsed("$schema").str,
      "http://json-schema.org/draft-07/schema#"
    )

  test(
    "review-state schema requires version, issue_id, artifacts, last_updated"
  ):
    val parsed = ujson.read(os.read(schemaPath))
    val required = parsed("required").arr.map(_.str).toSet
    assertEquals(
      required,
      Set("version", "issue_id", "artifacts", "last_updated")
    )

  test("review-state schema defines all required + v2 optional properties"):
    val parsed = ujson.read(os.read(schemaPath))
    val props = parsed("properties").obj.keySet
    val expected = Set(
      "version",
      "issue_id",
      "artifacts",
      "last_updated",
      "status",
      "display",
      "badges",
      "task_lists",
      "needs_attention",
      "message",
      "pr_url",
      "git_sha",
      "phase_checkpoints",
      "available_actions"
    )
    val missing = expected -- props
    assert(missing.isEmpty, s"properties missing from schema: $missing")

  test("review-state test fixtures parse as JSON"):
    assert(os.exists(fixturesDir), s"missing $fixturesDir")
    val fixtures = os.list(fixturesDir).filter(_.ext == "json")
    assert(fixtures.nonEmpty, s"no JSON fixtures found in $fixturesDir")
    fixtures.foreach { f =>
      // ujson.read throws on malformed JSON; munit surfaces it with the path
      val _ = ujson.read(os.read(f))
    }
