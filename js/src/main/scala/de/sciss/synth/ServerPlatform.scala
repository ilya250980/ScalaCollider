/*
 *  ServerPlatform.scala
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

import de.sciss.osc
import de.sciss.osc.Browser

trait ServerPlatform {
  /** The default file path to `scsynth`. */
  def defaultProgram: String = "scsynth"

  type Address = Browser.Address

  private[synth] def mkAddress(config: Server.Config): Address = Browser.Address(config.port)

  def isLocal(addr: Address): Boolean = true

  protected def prepareConnection(
                                   serverConfig : Server.Config,
                                   clientConfig : Client.Config,
                                 ): (Address, osc.Client) = {
    val serverAddr = Browser.Address(serverConfig.port)
    val client: osc.Client = serverConfig.transport match {
      case Browser =>
        val cfg = Browser.Config()
        clientConfig.addr.foreach(cfg.localAddress = _)
        cfg.codec               = message.ServerCodec
        cfg.bufferSize          = 0x10000
        Browser.Client(serverAddr, cfg)
      case other =>
        throw new IllegalArgumentException(s"Unsupported OSC transport $other")
    }
    (serverAddr, client)
  }
}
