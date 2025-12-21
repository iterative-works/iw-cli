#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Run CaskServer in daemon mode (background)
// PURPOSE: Entry point for background server process

import iw.core.infrastructure.CaskServer

@main
def main(args: String*): Unit =
  if args.length < 3 then
    System.err.println("Usage: server-daemon <statePath> <port> <hosts>")
    System.exit(1)

  val statePath = args(0)
  val port = args(1).toInt
  val hosts = args(2).split(",").toSeq

  // Start server (blocks)
  CaskServer.start(statePath, port, hosts)
