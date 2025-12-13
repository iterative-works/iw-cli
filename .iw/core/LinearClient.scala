// PURPOSE: Linear API client for token validation and issue operations
// PURPOSE: Provides validateToken to check if LINEAR_API_TOKEN is valid

//> using scala 3.3.1
//> using dep com.softwaremill.sttp.client4::core:4.0.0-M18

package iw.core

import sttp.client4.quick.*
import sttp.model.StatusCode

object LinearClient:
  private val apiUrl = "https://api.linear.app/graphql"

  def validateToken(token: String): Boolean =
    if token.isEmpty then
      return false

    try
      // Simple GraphQL query to validate token - just fetch viewer info
      val query = """{"query":"{ viewer { id } }"}"""

      val response = quickRequest
        .post(uri"$apiUrl")
        .header("Authorization", token)
        .header("Content-Type", "application/json")
        .body(query)
        .send()

      // Valid token returns 200, invalid returns 401
      response.code == StatusCode.Ok
    catch
      case _: Exception => false
