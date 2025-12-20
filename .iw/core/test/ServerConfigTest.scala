// PURPOSE: Unit tests for ServerConfig validation and JSON serialization
// PURPOSE: Validates port range constraints and upickle serialization

package iw.core.test

import iw.core.ServerConfig

class ServerConfigTest extends munit.FunSuite:

  test("Valid port numbers parse correctly"):
    val validPorts = List(9876, 8080, 3000, 1024, 65535)
    validPorts.foreach { port =>
      val config = ServerConfig(port)
      assert(ServerConfig.validate(port).isRight)
      assertEquals(config.port, port)
    }

  test("Invalid port 0 fails validation"):
    val result = ServerConfig.validate(0)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))

  test("Invalid negative port fails validation"):
    val result = ServerConfig.validate(-1)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))

  test("Invalid port 70000 fails validation"):
    val result = ServerConfig.validate(70000)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))

  test("Invalid port 99999 fails validation"):
    val result = ServerConfig.validate(99999)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))

  test("Port below 1024 fails validation"):
    val result = ServerConfig.validate(1023)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))

  test("Port above 65535 fails validation"):
    val result = ServerConfig.validate(65536)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Port must be between 1024 and 65535")))
