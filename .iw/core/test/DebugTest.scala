// PURPOSE: Debug test to understand stdout capture issue

//> using scala 3.3.1
//> using file "../Output.scala"

import iw.core.Output
import java.io.ByteArrayOutputStream
import java.io.PrintStream

object DebugTest:
  def main(args: Array[String]): Unit =
    println("Testing direct println...")
    println("✓ test")

    val baos = ByteArrayOutputStream()
    val ps = PrintStream(baos)
    val oldOut = System.out
    System.setOut(ps)

    println("✓ operation completed")

    ps.flush()
    System.setOut(oldOut)
    ps.close()

    val result = baos.toString()
    println(s"Captured: '$result'")
    println(s"Length: ${result.length}")
