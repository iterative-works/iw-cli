// PURPOSE: Unit tests for HookDiscovery generic reflection-based value collection
// PURPOSE: Verifies discovery of typed values from Scala singleton objects
package iw.tests

import iw.core.adapters.HookDiscovery
import iw.core.model.{Check, CheckResult, SessionAction, SessionContext}
import munit.FunSuite

// Test hook objects — these are on the classpath during test runs
object TestHookWithCheck:
  val check: Check =
    Check("Test Check", _ => CheckResult.Success("from hook"))

object TestHookWithSessionAction:
  val action: SessionAction = new SessionAction:
    def run(ctx: SessionContext): Option[String] = Some("test-command")

object TestHookWithBoth:
  val check: Check =
    Check("Both Check", _ => CheckResult.Success("from both"))
  val action: SessionAction = new SessionAction:
    def run(ctx: SessionContext): Option[String] = Some("both-command")

object TestHookEmpty:
  val unrelated: String = "not a hook value"

class HookDiscoveryTest extends FunSuite:

  test("collectValuesFrom returns empty list for empty class string"):
    val result = HookDiscovery.collectValuesFrom[Check]("")
    assertEquals(result, Nil)

  test("collectValuesFrom discovers Check values from hook object"):
    val result = HookDiscovery.collectValuesFrom[Check](
      "iw.tests.TestHookWithCheck"
    )
    assertEquals(result.size, 1)
    assertEquals(result.head.name, "Test Check")

  test("collectValuesFrom discovers SessionAction values from hook object"):
    val result = HookDiscovery.collectValuesFrom[SessionAction](
      "iw.tests.TestHookWithSessionAction"
    )
    assertEquals(result.size, 1)
    val ctx = SessionContext("s", os.pwd, "IW-1", None)
    assertEquals(result.head.run(ctx), Some("test-command"))

  test(
    "collectValuesFrom filters by type — Check not found in SessionAction hook"
  ):
    val result = HookDiscovery.collectValuesFrom[Check](
      "iw.tests.TestHookWithSessionAction"
    )
    assertEquals(result, Nil)

  test("collectValuesFrom collects only matching type from multi-value hook"):
    val checks = HookDiscovery.collectValuesFrom[Check](
      "iw.tests.TestHookWithBoth"
    )
    assertEquals(checks.size, 1)
    assertEquals(checks.head.name, "Both Check")

    val actions = HookDiscovery.collectValuesFrom[SessionAction](
      "iw.tests.TestHookWithBoth"
    )
    assertEquals(actions.size, 1)

  test("collectValuesFrom handles multiple comma-separated classes"):
    val result = HookDiscovery.collectValuesFrom[Check](
      "iw.tests.TestHookWithCheck,iw.tests.TestHookWithBoth"
    )
    assertEquals(result.size, 2)

  test("collectValuesFrom skips non-existent classes gracefully"):
    val result = HookDiscovery.collectValuesFrom[Check](
      "iw.tests.NonExistentClass,iw.tests.TestHookWithCheck"
    )
    assertEquals(result.size, 1)
    assertEquals(result.head.name, "Test Check")

  test("collectValuesFrom returns empty for object with no matching values"):
    val result = HookDiscovery.collectValuesFrom[Check](
      "iw.tests.TestHookEmpty"
    )
    assertEquals(result, Nil)
