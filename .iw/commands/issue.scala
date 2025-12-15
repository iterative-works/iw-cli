// PURPOSE: Fetch and display issue information from the tracker
// USAGE: iw issue <issue-id>
// ARGS:
//   <issue-id>: The issue identifier to fetch
// EXAMPLE: iw issue IWLE-123

//> using scala 3.3.1
//> using file "../core/Output.scala"

import iw.core.Output

@main def issue(args: String*): Unit =
  Output.info("Not implemented yet")
  Output.info("This command will fetch and display issue information")
