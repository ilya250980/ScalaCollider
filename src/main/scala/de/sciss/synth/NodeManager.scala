/*
 *  NodeManager.scala
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

import de.sciss.model.impl.ModelImpl
import de.sciss.model.Model

object NodeManager {
  type Listener = Model.Listener[NodeManager.Update]

  sealed trait Update

  abstract sealed class NodeChange extends Update {
    def node: Node
    def info: message.NodeInfo.Data
  }

  final case class NodeGo   (node: Node, info: message.NodeInfo.Data) extends NodeChange
  final case class NodeEnd  (node: Node, info: message.NodeInfo.Data) extends NodeChange
  final case class NodeOn   (node: Node, info: message.NodeInfo.Data) extends NodeChange
  final case class NodeOff  (node: Node, info: message.NodeInfo.Data) extends NodeChange
  final case class NodeMove (node: Node, info: message.NodeInfo.Data) extends NodeChange

  case object Cleared extends Update
}

final class NodeManager(val server: Server) extends ModelImpl[NodeManager.Update] {
  import NodeManager._

  private[this] var nodes: Map[Int, Node] = _
  private[this] val sync     = new AnyRef

  // ---- constructor ----
  clear()

  //      if( server.isRunning ) {
  //         val defaultGroup = server.defaultGroup
  //         nodes += defaultGroup.id -> defaultGroup
  //      }

  def nodeChange(e: message.NodeChange): Unit =
    e match {
      case message.NodeGo(nodeId, info) =>
        nodes.get(nodeId) match {
          case Some(node) =>
            dispatchBoth(NodeGo(node, info))

          case None =>
            if ( /* autoAdd && */ nodes.contains(info.parentId)) {
              val created = info match {
                case _: message.NodeInfo.SynthData => Synth(server, nodeId)
                case _: message.NodeInfo.GroupData => Group(server, nodeId)
              }
              register(created)
              dispatchBoth(NodeGo(created, info))
            }
          }

      case message.NodeEnd(nodeId, info) =>
        // println(s"---- NodeEnd: ${nodes.get(nodeId)}")
        nodes.get(nodeId) match {
          case Some(node) =>
            unregister(node)
            dispatchBoth(NodeEnd(node, info))
          case None =>
        }

      case message.NodeOff(nodeId, info) =>
        nodes.get(nodeId) match {
          case Some(node) =>
            dispatchBoth(NodeOff(node, info))
          case None =>
        }

      case message.NodeOn(nodeId, info) =>
        nodes.get(nodeId) match {
          case Some(node) =>
            dispatchBoth(NodeOn(node, info))
          case None =>
        }

      case message.NodeMove(nodeId, info) =>
        nodes.get(nodeId) match {
          case Some(node) =>
            dispatchBoth(NodeMove(node, info))
          case None =>
        }

      case _ =>
	}

  private def dispatchBoth(change: NodeChange): Unit = {
    dispatch(change)
    change.node.updated(change)
  }

  // eventually this should be done automatically
  // by the message dispatch management
  def register(node: Node): Unit =
    sync.synchronized {
      // println(s"---- register node: $node")
      nodes += node.id -> node
    }

  def unregister(node: Node): Unit =
    sync.synchronized {
      // println(s"---- unregister node: $node")
      nodes -= node.id
    }

  def getNode(id: Int): Option[Node] = nodes.get(id)  // sync.synchronized { }

  def clear(): Unit = {
    val rootNode = server.rootNode // new Group( server, 0 )
    sync.synchronized {
      nodes = Map(rootNode.id -> rootNode)
    }
    dispatch(Cleared)
  }
}