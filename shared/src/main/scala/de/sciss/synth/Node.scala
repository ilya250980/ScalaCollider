/*
 *  Node.scala
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

import de.sciss.model.Model
import de.sciss.model.impl.ModelImpl

/** A representation for a node on the server's tree. A `Node` is either a `Synth` or a `Group`.
  *
  * '''Note''' that if the node is a group, all messages send to the node which are not specific to a
  * `Synth` or `Group`, i.e. all messages found in this class, will affect all child nodes of the group.
  * For example, if `release()` is called on a `Group`, the underlying `setMsg` is propagated to all
  * `Synth`s in the tree whose root is this group.
  */
object Node {
  type Listener = Model.Listener[NodeManager.NodeChange]
}
abstract class Node extends ModelImpl[NodeManager.NodeChange] {

  // ---- abstract ----
  def server: Server

  def id: Int

  final def register(): Unit = server.nodeManager.register(this)

  final def onGo(thunk: => Unit): Unit = {
    register()
    lazy val l: Node.Listener = {
      case NodeManager.NodeGo(_, _) =>
        removeListener(l)
        thunk
    }
    addListener(l)
    ()
  }

  final def onEnd(thunk: => Unit): Unit = {
    register()
    lazy val l: Node.Listener = {
      case NodeManager.NodeEnd(_, _) =>
        removeListener(l)
        thunk
    }
    addListener(l)
    ()
  }

  final protected[synth] def updated(change: NodeManager.NodeChange): Unit =
    dispatch(change) // XXX need to update isPlaying, isRunning etc.

  def freeMsg = message.NodeFree(id)

  /** Returns an OSC message to resume the node if it was paused.
    *
    * @see [[de.sciss.synth.message.NodeRun]]
    */
  def runMsg: message.NodeRun = runMsg(flag = true)

  /** Returns an OSC message to resume the node if it was paused.
    *
    * @param flag if `true` the node is resumed, if `false` it is paused.
    *
    * @see [[de.sciss.synth.message.NodeRun]]
    */
  def runMsg(flag: Boolean) = message.NodeRun(id -> flag)

  def setMsg(pairs: ControlSet*) = message.NodeSet(id, pairs: _*)

  def setnMsg(pairs: ControlSet*) = message.NodeSetn(id, pairs: _*)

  def traceMsg = message.NodeTrace(id)

  // def releaseMsg: message.NodeSet = releaseMsg(None)

  /** A utility method which calls `setMsg` assuming a control named `gate`. The release time
    * argument is modified to correspond with the interpretation of the `gate` argument in
    * an `EnvGen` UGen. This is the case for synths created with the package method `play`.
    *
    * @param   releaseTime the optional release time in seconds within which the synth should fade out,
    *                      or `-1` (default) if the envelope should be released at its nominal release time.
    *                      If the `EnvGen` has a `doneAction` of `freeSelf`, the synth will be freed after
    *                      the release phase.
    *
    * @see  [[de.sciss.synth.ugen.EnvGen]]
    * @see  [[de.sciss.synth.message.NodeSet]]
    */
  def releaseMsg(releaseTime: Double = -1.0): message.NodeSet = {
    val gate = if (releaseTime < 0) 0.0 else -1.0 - releaseTime
    setMsg("gate" -> gate)
  }

  def mapMsg(pairs: ControlKBusMap.Single*) = message.NodeMap(id, pairs: _*)

  def mapnMsg(mappings: ControlKBusMap*) = message.NodeMapn(id, mappings: _*)

  /** Returns an OSC message to map from an mono-channel audio bus to one of the node's controls.
    *
    * Note that a mapped control acts similar to an `InFeedback` UGen in that it does not matter
    * whether the audio bus was written before the execution of the synth whose control is mapped or not.
    * If it was written before, no delay is introduced, otherwise a delay of one control block is introduced.
    *
    * @see  [[de.sciss.synth.ugen.InFeedback]]
    */
  def mapaMsg(pairs: ControlABusMap.Single*) = message.NodeMapa(id, pairs: _*)

  /** Returns an OSC message to map from an mono- or multi-channel audio bus to one of the node's controls.
    *
    * Note that a mapped control acts similar to an `InFeedback` UGen in that it does not matter
    * whether the audio bus was written before the execution of the synth whose control is mapped or not.
    * If it was written before, no delay is introduced, otherwise a delay of one control block is introduced.
    *
    * @see  [[de.sciss.synth.ugen.InFeedback]]
    */
  def mapanMsg(mappings: ControlABusMap*) =
    message.NodeMapan(id, mappings: _*)

  //  def fillMsg(control: Any, numChannels: Int, value: Float) =
  //    message.NodeFill(id, message.NodeFill.Data(control, numChannels, value))

  def fillMsg(data: ControlFillRange*) = message.NodeFill(id, data: _*)

  /** Creates an OSC message to move this node before another node
    *
    * @param   node  the node before which to move this node
    *
    * @see  [[de.sciss.synth.message.NodeBefore]]
    */
  def moveBeforeMsg(node: Node) = message.NodeBefore(id -> node.id)

  /** Creates an OSC message to move this node after another node
    *
    * @param   node  the node after which to move this node
    *
    * @see  [[de.sciss.synth.message.NodeAfter]]
    */
  def moveAfterMsg(node: Node) = message.NodeAfter(id -> node.id)

  def moveToHeadMsg(group: Group): message.GroupHead = group.moveNodeToHeadMsg(this)

  def moveToTailMsg(group: Group): message.GroupTail = group.moveNodeToTailMsg(this)
}
