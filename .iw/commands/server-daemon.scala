#!/usr/bin/env -S scala-cli shebang
// PURPOSE: Run CaskServer in daemon mode (background)
// PURPOSE: Entry point for background server process

import iw.core.infrastructure.CaskServer

@main
def main(args: String*): Unit =
  if args.length < 2 then
    System.err.println("Usage: server-daemon <statePath> <port>")
    System.exit(1)

  val statePath = args(0)
  val port = args(1).toInt

  // Start server (blocks)
  CaskServer.start(statePath, port)
