/*
 *  Client.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

object Client {
  sealed trait ConfigLike {
    def clientId        : Int
    def nodeIdOffset    : Int
    def addr            : Option[Address]
    def executionContext: ExecutionContext

    /** Nominal expected latency in seconds.
      * This is not interpreted by ScalaCollider directly,
      * but can be used by code based on ScalaCollider.
      */
    def latency         : Double
  }

  object Config {
    /** Creates a new configuration builder with default settings. */
    def apply(): ConfigBuilder = new ConfigBuilder()

    /** Implicit conversion which allows you to use a `ConfigBuilder`
      * wherever a `Config` is required.
      */
    implicit def build(cb: ConfigBuilder): Config = cb.build
  }

  type Address = Server.Address

  final class Config private[Client](val clientId: Int, val nodeIdOffset: Int, val addr: Option[Address],
                                     val latency: Double)
                                    (implicit val executionContext: ExecutionContext)
    extends ConfigLike {
    override def toString = "Client.Config"
  }

  object ConfigBuilder {
    def apply(config: Config): ConfigBuilder = {
      val b = new ConfigBuilder
      b.read(config)
      b
    }
  }
  final class ConfigBuilder private[Client]() extends ConfigLike {
    var clientId        : Int                 = 0
    var nodeIdOffset    : Int                 = 1000
    var addr            : Option[Address]     = None
    var executionContext: ExecutionContext    = ExecutionContext.global
    var latency         : Double              = 0.2

    def read(config: Config): Unit = {
      clientId          = config.clientId
      nodeIdOffset      = config.nodeIdOffset
      addr              = config.addr
      executionContext  = config.executionContext
      latency           = config.latency
    }

    def build: Config =
      new Config(clientId = clientId, nodeIdOffset = nodeIdOffset, addr = addr, latency = latency)(executionContext)
  }
}