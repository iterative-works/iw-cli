// PURPOSE: Unit tests for issue create command argument parser
// PURPOSE: Tests IssueCreateParser.parse with various flag combinations and edge cases

package iw.tests

import iw.core.*

class IssueCreateParserTest extends munit.FunSuite:

  test("parse_TitleOnly"):
    val args = Seq("--title", "Test issue")
    val result = IssueCreateParser.parse(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected IssueCreateRequest"))
    assertEquals(request.title, "Test issue")
    assertEquals(request.description, None)

  test("parse_TitleWithDescription"):
    val args = Seq("--title", "Test issue", "--description", "Test body")
    val result = IssueCreateParser.parse(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected IssueCreateRequest"))
    assertEquals(request.title, "Test issue")
    assertEquals(request.description, Some("Test body"))

  test("parse_MissingTitle"):
    val args = Seq("--description", "Only description")
    val result = IssueCreateParser.parse(args)
    assert(result.isLeft, "Expected Left for missing title")
    val error = result.left.getOrElse("")
    assert(error.contains("--title"), s"Expected error about --title, got: $error")

  test("parse_EmptyArgs"):
    val args = Seq.empty[String]
    val result = IssueCreateParser.parse(args)
    assert(result.isLeft, "Expected Left for empty args")
    val error = result.left.getOrElse("")
    assert(error.contains("--title"), s"Expected error about --title, got: $error")

  test("parse_MultiWordTitle"):
    val args = Seq("--title", "Fix", "login", "bug")
    val result = IssueCreateParser.parse(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected IssueCreateRequest"))
    assertEquals(request.title, "Fix login bug")

  test("parse_MultiWordDescription"):
    val args = Seq("--title", "Test", "--description", "Long", "multi", "word", "description")
    val result = IssueCreateParser.parse(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected IssueCreateRequest"))
    assertEquals(request.title, "Test")
    assertEquals(request.description, Some("Long multi word description"))

  test("parse_ReversedFlagOrder"):
    val args = Seq("--description", "Desc text", "--title", "Test issue")
    val result = IssueCreateParser.parse(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected IssueCreateRequest"))
    assertEquals(request.title, "Test issue")
    assertEquals(request.description, Some("Desc text"))

  test("parse_EmptyDescriptionFlag"):
    val args = Seq("--title", "Test", "--description")
    val result = IssueCreateParser.parse(args)
    assert(result.isRight, "Expected Right with empty description")
    val request = result.getOrElse(fail("Expected IssueCreateRequest"))
    assertEquals(request.title, "Test")
    assertEquals(request.description, Some(""))
