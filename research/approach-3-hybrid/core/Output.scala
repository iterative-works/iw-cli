// PURPOSE: Output formatting utilities for consistent command output
// PURPOSE: Provides helpers for structured output that LLMs can parse

package iw.core

object Output:
  def info(message: String): Unit =
    println(message)

  def error(message: String): Unit =
    System.err.println(s"Error: $message")

  def success(message: String): Unit =
    println(s"✓ $message")

  def section(title: String): Unit =
    println(s"\n=== $title ===")

  def keyValue(key: String, value: String): Unit =
    println(f"$key%-20s $value")
