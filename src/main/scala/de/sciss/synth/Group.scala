/*
 *  Group.scala
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

object Group {
  def apply(server: Server): Group = apply(server, server.nextNodeID())

  def apply(): Group = apply(Server.default)
}

final case class Group(server: Server, id: Int)
  extends Node {

//  def this(server: Server) = this(server, server.nextNodeID())
//
//  def this() = this(Server.default)

  def newMsg(target: Node, addAction: AddAction) =
    message.GroupNew(message.GroupNew.Data(id, addAction.id, target.id))

  def dumpTreeMsg: message.GroupDumpTree = dumpTreeMsg(postControls = false)

  def dumpTreeMsg(postControls: Boolean) = message.GroupDumpTree(id -> postControls)

  def queryTreeMsg(postControls: Boolean) = message.GroupQueryTree(id -> postControls)

  def freeAllMsg = message.GroupFreeAll(id)

  def deepFreeMsg = message.GroupDeepFree(id)

  def moveNodeToHeadMsg(node: Node) = message.GroupHead(id -> node.id)

  def moveNodeToTailMsg(node: Node) = message.GroupTail(id -> node.id)
}