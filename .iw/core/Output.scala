// PURPOSE: Output formatting utilities for consistent command output
// PURPOSE: Provides helpers for structured output that LLMs can parse

package iw.core

object Output:
  def info(message: String): Unit =
    System.out.println(message)

  def error(message: String): Unit =
    System.err.println(s"Error: $message")

  def success(message: String): Unit =
    System.out.println(s"✓ $message")

  def section(title: String): Unit =
    System.out.println(s"\n=== $title ===")

  def keyValue(key: String, value: String): Unit =
    System.out.println(f"$key%-20s $value")
