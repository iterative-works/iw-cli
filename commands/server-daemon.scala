// PURPOSE: Run CaskServer in daemon mode (background)
// PURPOSE: Entry point for background server process

//> using dep com.lihaoyi::cask:0.11.3
//> using dep com.lihaoyi::scalatags:0.13.1
//> using dep com.vladsch.flexmark:flexmark-all:0.64.8

package iw.dashboard

object ServerDaemon:
  def main(args: Array[String]): Unit =
    if args.length < 3 then
      System.err.println("Usage: server-daemon <statePath> <port> <hosts>")
      System.exit(1)

    val statePath = args(0)
    val port = args(1).toInt
    val hosts = args(2).split(",").toSeq

    // Start server (blocks)
    CaskServer.start(statePath, port, hosts)
