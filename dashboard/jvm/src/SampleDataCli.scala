// PURPOSE: CLI entry point for writing sample data to a state file
// PURPOSE: Used by `iw dashboard --sample-data` via java -cp to seed dev/demo state

package iw.dashboard

import iw.dashboard.domain.SampleDataGenerator

/** Writes sample state to the given state file path and exits.
  *
  * Invoked via
  * `java -cp "$IW_DASHBOARD_JAR" iw.dashboard.SampleDataCli <statePath>`. Keeps
  * sample-data seeding out of `ServerDaemon`'s production startup path.
  */
object SampleDataCli:
  def main(args: Array[String]): Unit =
    if args.length < 1 then
      System.err.println("Usage: SampleDataCli <statePath>")
      System.exit(1)

    val statePath = args(0)
    val sampleState = SampleDataGenerator.generateSampleState()
    val repository = StateRepository(statePath)

    repository.write(sampleState) match
      case Right(_) =>
        System.out.println(
          s"Sample data written: ${sampleState.worktrees.size} worktrees to $statePath"
        )
        System.exit(0)
      case Left(err) =>
        System.err.println(s"Failed to write sample data: $err")
        System.exit(1)
