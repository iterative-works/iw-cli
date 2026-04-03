// PURPOSE: Unit tests for SessionAction trait and SessionContext case class
// PURPOSE: Verifies context construction and trait contract
package iw.tests

import iw.core.model.{SessionAction, SessionContext}
import munit.FunSuite

class SessionActionTest extends FunSuite:

  test("SessionContext holds all fields"):
    val ctx = SessionContext(
      sessionName = "test-session",
      worktreePath = os.pwd,
      issueId = "IW-100",
      prompt = Some("do something")
    )
    assertEquals(ctx.sessionName, "test-session")
    assertEquals(ctx.worktreePath, os.pwd)
    assertEquals(ctx.issueId, "IW-100")
    assertEquals(ctx.prompt, Some("do something"))

  test("SessionContext with no prompt"):
    val ctx = SessionContext("s", os.pwd, "IW-1", None)
    assertEquals(ctx.prompt, None)

  test("SessionAction implementation returning Some command"):
    val action = new SessionAction:
      def run(ctx: SessionContext): Option[String] =
        ctx.prompt.map(p => s"tool $p")

    val ctx = SessionContext("s", os.pwd, "IW-1", Some("hello"))
    assertEquals(action.run(ctx), Some("tool hello"))

  test("SessionAction implementation returning None"):
    val action = new SessionAction:
      def run(ctx: SessionContext): Option[String] = None

    val ctx = SessionContext("s", os.pwd, "IW-1", Some("hello"))
    assertEquals(action.run(ctx), None)
