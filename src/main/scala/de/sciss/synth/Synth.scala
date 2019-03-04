/*
 *  Synth.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

object Synth {
  def apply(server: Server = Server.default): Synth = apply(server, server.nextNodeId())
}
final case class Synth(server: Server, id: Int)
  extends Node {

  private[this] var defNameVar = ""

  def newMsg(defName: String, target: Node = server.defaultGroup, args: Seq[ControlSet] = Nil,
             addAction: AddAction = addToHead): message.SynthNew = {
    defNameVar = defName
    message.SynthNew(defName, id, addAction.id, target.id, args: _*)
  }

  def defName: String = defNameVar

  override def toString: String = {
    val df = if (defNameVar != "") s") : <$defNameVar>" else ")"
    s"Synth($server,$id$df"
  }
}