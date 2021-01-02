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

import java.net.SocketAddress

trait ServerPlatform {
  protected def createBrowserClient(target: SocketAddress, cfg: Browser.Config): osc.Client =
    Browser.Client(target, cfg)
}
