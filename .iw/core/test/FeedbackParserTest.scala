// PURPOSE: Unit tests for feedback command argument parser
// PURPOSE: Tests parseFeedbackArgs with various flag combinations and edge cases

package iw.tests

import iw.core.*

class FeedbackParserTest extends munit.FunSuite:

  test("parseFeedbackArgs_TitleOnly"):
    val args = Seq("Bug", "in", "command")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Bug in command")
    assertEquals(request.description, "")
    assertEquals(request.issueType, FeedbackParser.IssueType.Feature)

  test("parseFeedbackArgs_WithDescription"):
    val args = Seq("Title", "--description", "Long description")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Title")
    assertEquals(request.description, "Long description")
    assertEquals(request.issueType, FeedbackParser.IssueType.Feature)

  test("parseFeedbackArgs_WithType"):
    val args = Seq("Title", "--type", "bug")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Title")
    assertEquals(request.description, "")
    assertEquals(request.issueType, FeedbackParser.IssueType.Bug)

  test("parseFeedbackArgs_EmptyTitle"):
    val args = Seq("--description", "Only desc")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isLeft, "Expected Left for empty title")
    val error = result.left.getOrElse("")
    assert(error.contains("Title is required"), s"Expected 'Title is required', got: $error")

  test("parseFeedbackArgs_InvalidType"):
    val args = Seq("Title", "--type", "invalid")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isLeft, "Expected Left for invalid type")
    val error = result.left.getOrElse("")
    assert(error.contains("Type must be"), s"Expected error about type, got: $error")

  test("parseFeedbackArgs_CombinedFlags"):
    val args = Seq("Multi", "word", "title", "--type", "bug", "--description", "Some desc")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Multi word title")
    assertEquals(request.description, "Some desc")
    assertEquals(request.issueType, FeedbackParser.IssueType.Bug)

  test("parseFeedbackArgs_MultiWordDescription"):
    val args = Seq("Title", "--description", "Long", "multi", "word", "description")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Title")
    assertEquals(request.description, "Long multi word description")

  test("parseFeedbackArgs_ReversedFlagOrder"):
    val args = Seq("Title", "--description", "Desc text", "--type", "bug")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right but got Left")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Title")
    assertEquals(request.description, "Desc text")
    assertEquals(request.issueType, FeedbackParser.IssueType.Bug)

  test("parseFeedbackArgs_TypeCaseInsensitive"):
    val args = Seq("Title", "--type", "BUG")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right for uppercase type")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.issueType, FeedbackParser.IssueType.Bug)

  test("parseFeedbackArgs_EmptyDescriptionFlag"):
    val args = Seq("Title", "--description")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right with empty description")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.title, "Title")
    assertEquals(request.description, "")

  test("parseFeedbackArgs_EmptyTypeFlag"):
    val args = Seq("Title", "--type")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right with default type when flag has no value")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.issueType, FeedbackParser.IssueType.Feature)

  test("parseFeedbackArgs_DescriptionFlagAtEndWithType"):
    val args = Seq("Title", "--type", "bug", "--description")
    val result = FeedbackParser.parseFeedbackArgs(args)
    assert(result.isRight, "Expected Right with empty description")
    val request = result.getOrElse(fail("Expected FeedbackRequest"))
    assertEquals(request.description, "")
    assertEquals(request.issueType, FeedbackParser.IssueType.Bug)
