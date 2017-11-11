/*
 *  Responder.scala
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
package message

import de.sciss.osc.Message
import util.control.NonFatal

trait Handler {
  // returns `true` if the handler wishes to be removed
  private[synth] def handle(msg: Message): Boolean

  private[synth] def removed(): Unit
}

object Responder {
  def add(server: Server = Server.default)(handler: PartialFunction[Message, Unit]): Responder =
    new Impl(server, handler, false).add()

  def once(server: Server = Server.default)(handler: PartialFunction[Message, Unit]): Responder =
    new Impl(server, handler, true).add()

  def apply(server: Server = Server.default)(handler: PartialFunction[Message, Unit]): Responder =
    new Impl(server, handler, false)

  private final class Impl(val server: Server, handler: PartialFunction[Message, Unit], once: Boolean)
    extends Responder {

    def add(): this.type = {
      server.addResponder(this); this
    }

    def remove(): this.type = {
      server.removeResponder(this); this
    }

    private[synth] def handle(msg: Message): Boolean = {
      val handled = handler.isDefinedAt(msg)
      if (handled) try {
        handler(msg)
      } catch {
        case NonFatal(e) => e.printStackTrace()
      }
      once && handled
    }

    private[synth] def removed(): Unit = ()

    override def toString = s"Responder($server${if (once) ", once = true" else ""})@${hashCode().toHexString}"
  }
}

trait Responder extends Handler {
  def server: Server

  def add   (): this.type
  def remove(): this.type
}