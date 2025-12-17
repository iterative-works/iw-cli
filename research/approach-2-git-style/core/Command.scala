// PURPOSE: Shared library code for iw commands
// PURPOSE: Provides common utilities and types for command implementations

package iw.core

/** Base trait for command implementations */
trait Command:
  /** Execute the command with given arguments */
  def run(args: Array[String]): Int

/** Helper functions for commands */
object CommandHelpers:
  /** Print error message to stderr and return error code */
  def error(msg: String): Int =
    System.err.println(s"Error: $msg")
    1

  /** Print success message and return success code */
  def success(msg: String): Int =
    println(msg)
    0

  /** Format a list of arguments for display */
  def formatArgs(args: Array[String]): String =
    if args.isEmpty then "(no arguments)"
    else args.mkString(", ")
