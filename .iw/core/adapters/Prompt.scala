// PURPOSE: Interactive console prompt utilities for user input
// PURPOSE: Provides helpers for yes/no questions and text input with defaults

package iw.core.adapters

import scala.io.StdIn

object Prompt:
  def ask(question: String, default: Option[String] = None): String =
    val prompt = default match
      case Some(d) => s"$question [$d]: "
      case None => s"$question: "

    print(prompt)
    val input = StdIn.readLine()

    Option(input) match
      case None =>
        default.getOrElse("") // Non-interactive mode with no default yields empty string
      case Some(i) if i.trim.isEmpty && default.isDefined => default.get
      case Some(i) => i.trim

  def confirm(question: String, default: Boolean = false): Boolean =
    val suffix = if default then "[Y/n]" else "[y/N]"
    val prompt = s"$question $suffix: "

    print(prompt)
    val input = StdIn.readLine().trim.toLowerCase

    input match
      case "" => default
      case "y" | "yes" => true
      case "n" | "no" => false
      case _ => confirm(question, default) // Ask again for invalid input
