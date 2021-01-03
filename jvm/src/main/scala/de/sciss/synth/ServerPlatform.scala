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
import de.sciss.osc.{TCP, UDP}

import java.io.File
import java.net.{InetAddress, InetSocketAddress}

trait ServerPlatform {
  /** The default file path to `scsynth`. If the runtime (system) property `"SC_HOME"` is provided,
    * this specifies the directory of `scsynth`. Otherwise, an environment (shell) variable named
    * `"SC_HOME"` is checked. If neither exists, this returns `scsynth` in the current working directory.
    */
  def defaultProgram: String = sys.props.get("SC_HOME").orElse(sys.env.get("SC_HOME")).fold {
    "scsynth"
  } {
    home => new File(home, "scsynth").getPath
  }

  type Address = InetSocketAddress

  private[synth] def mkAddress(config: Server.Config): Address =
    new InetSocketAddress(config.host, config.port)

  def isLocal(addr: Address): Boolean = {
    val host = addr.getAddress
    host.isLoopbackAddress || host.isSiteLocalAddress
  }

  protected def prepareConnection(
                                   serverConfig : Server.Config,
                                   clientConfig : Client.Config,
                            ): (Address, osc.Client) = {
    val serverAddr  = new InetSocketAddress(serverConfig.host, serverConfig.port)
    val clientAddr  = clientConfig.addr.getOrElse {
      val a = serverAddr.getAddress
      if (a.isLoopbackAddress || a.isAnyLocalAddress)
        new InetSocketAddress("127.0.0.1", 0)
      else
        new InetSocketAddress(InetAddress.getLocalHost, 0)
    }

    val client: osc.Client = serverConfig.transport match {
      case UDP =>
        val cfg = UDP.Config()
        cfg.localSocketAddress  = clientAddr
        cfg.codec               = message.ServerCodec
        cfg.bufferSize          = 0x10000
        UDP.Client(serverAddr, cfg)
      case TCP =>
        val cfg                 = TCP.Config()
        cfg.codec               = message.ServerCodec
        cfg.localSocketAddress  = clientAddr
        cfg.bufferSize          = 0x10000
        TCP.Client(serverAddr, cfg)
      case other =>
        throw new IllegalArgumentException(s"Unsupported OSC transport $other")
    }
    (serverAddr, client)
  }
}
