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

  // Tests for hosts field
  test("ServerConfig has hosts field with default value Seq(\"localhost\")"):
    val config = ServerConfig(9876)
    assertEquals(config.hosts, Seq("localhost"))

  // validateHost tests
  test("validateHost accepts localhost"):
    val result = ServerConfig.validateHost("localhost")
    assert(result.isRight)
    assertEquals(result.toOption.get, "localhost")

  test("validateHost accepts 127.0.0.1"):
    val result = ServerConfig.validateHost("127.0.0.1")
    assert(result.isRight)
    assertEquals(result.toOption.get, "127.0.0.1")

  test("validateHost accepts ::1"):
    val result = ServerConfig.validateHost("::1")
    assert(result.isRight)
    assertEquals(result.toOption.get, "::1")

  test("validateHost accepts 0.0.0.0"):
    val result = ServerConfig.validateHost("0.0.0.0")
    assert(result.isRight)
    assertEquals(result.toOption.get, "0.0.0.0")

  test("validateHost accepts ::"):
    val result = ServerConfig.validateHost("::")
    assert(result.isRight)
    assertEquals(result.toOption.get, "::")

  test("validateHost accepts valid IPv4 address 192.168.1.1"):
    val result = ServerConfig.validateHost("192.168.1.1")
    assert(result.isRight)
    assertEquals(result.toOption.get, "192.168.1.1")

  test("validateHost accepts valid IPv6 address 2001:db8::1"):
    val result = ServerConfig.validateHost("2001:db8::1")
    assert(result.isRight)
    assertEquals(result.toOption.get, "2001:db8::1")

  test("validateHost rejects invalid value not-a-host"):
    val result = ServerConfig.validateHost("not-a-host")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid host")))

  test("validateHost rejects empty string"):
    val result = ServerConfig.validateHost("")
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid host")))

  // validateHosts tests
  test("validateHosts accepts non-empty array of valid hosts"):
    val result = ServerConfig.validateHosts(Seq("localhost", "127.0.0.1"))
    assert(result.isRight)
    assertEquals(result.toOption.get, Seq("localhost", "127.0.0.1"))

  test("validateHosts rejects empty array"):
    val result = ServerConfig.validateHosts(Seq.empty)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("at least one host")))

  test("validateHosts rejects array containing invalid host"):
    val result = ServerConfig.validateHosts(Seq("localhost", "not-a-host"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid host")))

  // create() method tests with hosts
  test("create() validates hosts and returns error for invalid hosts"):
    val result = ServerConfig.create(9876, Seq("not-a-host"))
    assert(result.isLeft)
    assert(result.left.exists(_.contains("Invalid host")))

  test("create() validates hosts and returns error for empty array"):
    val result = ServerConfig.create(9876, Seq.empty)
    assert(result.isLeft)
    assert(result.left.exists(_.contains("at least one host")))

  test("create() accepts valid port and valid hosts"):
    val result = ServerConfig.create(9876, Seq("localhost", "127.0.0.1"))
    assert(result.isRight)
    val config = result.toOption.get
    assertEquals(config.port, 9876)
    assertEquals(config.hosts, Seq("localhost", "127.0.0.1"))
