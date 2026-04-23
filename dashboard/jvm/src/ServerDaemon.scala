// PURPOSE: Main entry point for the dashboard fat jar, invoked via java -jar
// PURPOSE: Parses CLI arguments and delegates to CaskServer to start the HTTP server
package iw.dashboard

/** Entry point when the dashboard fat jar is invoked with `java -jar`.
  *
  * Arguments: `<statePath> <port> <hosts> [--dev]`
  *
  * `--dev` flag activates dev-mode asset routing when VITE_DEV_URL is also set
  * in the environment (double-gate: both flag and env var required).
  */
object ServerDaemon:
  def main(args: Array[String]): Unit =
    if args.length < 3 then
      System.err.println(
        "Usage: server-daemon <statePath> <port> <hosts> [--dev]"
      )
      System.exit(1)

    val statePath = args(0)
    val port = args(1).toInt
    val hosts = args(2).split(",").toSeq
    val devMode = args.length >= 4 && args(3) == "--dev"
    val viteDevUrl = sys.env.get("VITE_DEV_URL").map(_.trim).filter(_.nonEmpty)

    CaskServer.start(statePath, port, hosts, devMode, viteDevUrl)
