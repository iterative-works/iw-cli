// PURPOSE: Pure commit message construction from a title and optional items
// PURPOSE: Produces structured multi-line commit messages for phase workflow commands

package iw.core.model

object CommitMessage:
  /** Build a commit message from a title and optional list of items.
    *
    * Format (title only): {title}
    *
    * Format (with items): {title}
    *
    *   - {item1}
    *   - {item2}
    */
  def build(title: String, items: List[String] = Nil): String =
    val trimmedTitle = title.trim
    if items.isEmpty then trimmedTitle
    else
      val bullets = items.map(item => s"- ${item.trim}").mkString("\n")
      s"$trimmedTitle\n\n$bullets"
