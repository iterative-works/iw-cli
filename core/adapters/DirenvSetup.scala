// PURPOSE: Built-in SessionSetup hook that runs "direnv allow" when .envrc exists
// PURPOSE: Ensures direnv environments are activated in new tmux sessions

package iw.core.adapters

import iw.core.model.{SessionContext, SessionSetup}

object DirenvSetup extends SessionSetup:
  def run(ctx: SessionContext): Option[String] =
    if os.exists(ctx.worktreePath / ".envrc") then Some("direnv allow")
    else None
