// PURPOSE: Orchestrates session hook execution (setup + action) for start/open commands
// PURPOSE: Runs all SessionSetup commands first, then at most one SessionAction command

package iw.core.adapters

import iw.core.model.{SessionAction, SessionContext, SessionSetup}
import iw.core.output.Output

/** Result of running session hooks. */
enum SessionHookResult:
  /** No hooks produced any commands. */
  case NoHooks

  /** Setup commands ran but no action hook took over the session. */
  case SetupOnly

  /** An action hook sent a command — caller should not attach to session. */
  case ActionHandled

  /** An error occurred during hook execution. */
  case Error(message: String)

object SessionHooks:

  /** Built-in setup hooks that always run before plugin-discovered hooks. */
  val builtInSetups: List[SessionSetup] = List(
    DirenvSetup
  )

  /** Run all session hooks: setup commands first, then the main action.
    *
    * @return
    *   result indicating whether the session was taken over by an action hook
    */
  def run(ctx: SessionContext): SessionHookResult =
    val setupResult = runSetupHooks(ctx)
    setupResult match
      case SessionHookResult.Error(_) => setupResult
      case _                          =>
        runActionHook(
          ctx,
          hasSetups = setupResult == SessionHookResult.SetupOnly
        )

  private def runSetupHooks(ctx: SessionContext): SessionHookResult =
    val setups = builtInSetups ++ HookDiscovery.collectValues[SessionSetup]
    val commands = setups.flatMap(_.run(ctx))

    if commands.isEmpty then SessionHookResult.NoHooks
    else
      val errors = commands.flatMap { command =>
        TmuxAdapter.sendKeys(ctx.sessionName, command) match
          case Left(error) => Some(error)
          case Right(_)    => None
      }
      if errors.nonEmpty then
        SessionHookResult.Error(
          s"Failed to run setup commands: ${errors.mkString(", ")}"
        )
      else SessionHookResult.SetupOnly

  private def runActionHook(
      ctx: SessionContext,
      hasSetups: Boolean
  ): SessionHookResult =
    val actions = HookDiscovery.collectValues[SessionAction]

    if actions.isEmpty then
      if hasSetups then SessionHookResult.SetupOnly
      else SessionHookResult.NoHooks
    else
      val results = actions.flatMap(_.run(ctx))

      if results.size > 1 then
        SessionHookResult.Error(
          "Multiple session action hooks returned commands. Only one hook may provide a session command."
        )
      else
        results.headOption match
          case Some(command) =>
            TmuxAdapter.sendKeys(ctx.sessionName, command) match
              case Left(error) =>
                SessionHookResult.Error(
                  s"Failed to send session command: $error"
                )
              case Right(_) =>
                SessionHookResult.ActionHandled
          case None =>
            if hasSetups then SessionHookResult.SetupOnly
            else SessionHookResult.NoHooks
