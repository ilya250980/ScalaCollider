/*
 *  Client.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2018 Hanns Holger Rutz. All rights reserved.
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
    def clientId        : Int
    def nodeIdOffset    : Int
    def addr            : Option[InetSocketAddress]
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

  final class Config private[Client](val clientId: Int, val nodeIdOffset: Int, val addr: Option[InetSocketAddress])
                                     (implicit val executionContext: ExecutionContext)
    extends ConfigLike {
    override def toString = "ClientOptions"
  }

  final class ConfigBuilder private[Client]() extends ConfigLike {
    var clientId        : Int                       = 0
    var nodeIdOffset    : Int                       = 1000
    var addr            : Option[InetSocketAddress] = None
    var executionContext: ExecutionContext          = ExecutionContext.global

    def build: Config = new Config(clientId, nodeIdOffset, addr)(executionContext)
  }
}