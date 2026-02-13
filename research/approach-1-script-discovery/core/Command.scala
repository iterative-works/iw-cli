// PURPOSE: Core command trait that all commands must implement
// PURPOSE: Provides standard interface for command name, description, and execution

package iwcli.core

trait Command {
  def name: String
  def description: String
  def run(args: List[String]): Int
}

object Command {

  /** Helper to print to stdout */
  def println(msg: String): Unit = scala.Console.println(msg)

  /** Helper to print to stderr */
  def eprintln(msg: String): Unit = scala.Console.err.println(msg)
}
