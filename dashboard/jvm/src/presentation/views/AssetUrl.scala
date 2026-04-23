// PURPOSE: Asset URL helper for dev/prod routing of frontend bundle assets
// PURPOSE: In dev mode routes to Vite dev server; in prod mode routes to /assets/

package iw.dashboard.presentation.views

import iw.dashboard.DevModeConfig

/** Encapsulates per-server asset routing configuration.
  *
  * Set once at server startup from the resolved DevModeConfig. Passed
  * explicitly as a constructor parameter — no implicits.
  */
final case class AssetContext(devMode: DevModeConfig):
  val isDevMode: Boolean = devMode match
    case DevModeConfig.Off   => false
    case DevModeConfig.On(_) => true

object AssetContext:
  /** Production context: serves assets from bundled /assets/ classpath
    * resources.
    */
  val prod: AssetContext = AssetContext(DevModeConfig.Off)

object AssetUrl:
  /** Resolve the URL for a frontend asset by filename.
    *
    * In prod mode returns `/assets/<path>`. In dev mode returns the Vite dev
    * server URL with a `/src/` prefix.
    *
    * The `/src/` prefix is pinned to match Vite's current entry `src/main.js`
    * in `dashboard/frontend/vite.config.js`. If the Vite entry moves, update
    * this helper in lockstep.
    *
    * @param path
    *   Asset path relative to the asset root (e.g., "main.js")
    * @param ctx
    *   Asset context holding the resolved dev mode configuration
    * @return
    *   Absolute URL string suitable for use in an HTML src attribute
    */
  def apply(path: String, ctx: AssetContext): String =
    ctx.devMode match
      case DevModeConfig.Off =>
        s"/assets/$path"
      case DevModeConfig.On(baseUrl) =>
        s"${baseUrl.stripSuffix("/")}/src/$path"
