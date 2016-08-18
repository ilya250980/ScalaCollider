/*
 *  Client.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import java.net.InetSocketAddress
import language.implicitConversions
import concurrent.ExecutionContext

object Client {
  sealed trait ConfigLike {
    def clientID: Int
    def nodeIDOffset: Int
    def addr: Option[InetSocketAddress]
    def executionContext: ExecutionContext
  }

  object Config {
    /** Creates a new configuration builder with default settings. */
    def apply(): ConfigBuilder = new ConfigBuilder()

    /** Implicit conversion which allows you to use a `ConfigBuilder`
      * wherever a `Config` is required.
      */
    implicit def build(cb: ConfigBuilder): Config = cb.build
  }

  final class Config private[Client](val clientID: Int, val nodeIDOffset: Int, val addr: Option[InetSocketAddress])
                                     (implicit val executionContext: ExecutionContext)
    extends ConfigLike {
    override def toString = "ClientOptions"
  }

  final class ConfigBuilder private[Client]() extends ConfigLike {
    var clientID: Int = 0
    var nodeIDOffset: Int = 1000
    var addr: Option[InetSocketAddress] = None
    var executionContext: ExecutionContext = ExecutionContext.global

    def build: Config = new Config(clientID, nodeIDOffset, addr)(executionContext)
  }
}