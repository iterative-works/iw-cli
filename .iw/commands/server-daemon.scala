#!/usr/bin/env -S scala-cli shebang
//> using scala 3.3.7
//> using dep com.lihaoyi::os-lib:0.11.4-M3
//> using dep com.lihaoyi::cask:0.9.4
//> using dep com.lihaoyi::upickle:4.0.2

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
