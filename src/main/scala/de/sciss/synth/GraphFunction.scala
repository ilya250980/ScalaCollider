/*
 *  GraphFunction.scala
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

import ugen.WrapOut
import de.sciss.osc.{Bundle, Message}

object GraphFunction {
  private var uniqueIDCnt = 0
  private final val uniqueSync = new AnyRef

  private def uniqueID(): Int = uniqueSync.synchronized {
    uniqueIDCnt += 1
    val result = uniqueIDCnt
    result
  }

  object Result {
    implicit def in[T](implicit view: T => GE): In[T] = In(view)
    implicit case object Out  extends Result[UGenSource.ZeroOut]
    implicit case object Unit extends Result[scala.Unit]
    /* implicit */ final case class In[T](view: T => GE) extends Result[T]  // Scala doesn't allow case class and implicit
  }
  sealed trait Result[-T]
}

private[synth] final class GraphFunction[T](thunk: => T)(implicit res: GraphFunction.Result[T]) {
  import GraphFunction._

  def play(target: Node = Server.default.defaultGroup, outBus: Int = 0,
           fadeTime: Option[Float] = Some(0.02f),
           addAction: AddAction = addToHead): Synth = {

    val server    = target.server
    val defName   = s"temp_${uniqueID()}"   // more clear than using hashCode
    val synthDef  = SynthDef(defName) {
      val r = thunk
      res match {
        case Result.In(view) => WrapOut(view(r), fadeTime)
        case _ =>
      }
    }
    val synth       = Synth(server)
    val bytes       = synthDef.toBytes()
    val synthMsg    = synth.newMsg(synthDef.name, target, Seq("i_out" -> outBus, "out" -> outBus), addAction)
    val defFreeMsg  = synthDef.freeMsg
    val completion  = Bundle.now(synthMsg, defFreeMsg)
    // synth.onEnd { server ! synthDef.freeMsg } // why would we want to accumulate the defs?
    if (bytes.remaining > (65535 / 4)) {
      // "preliminary fix until full size works" (?)
      if (server.isLocal) {
        import Ops._
        synthDef.load(server, completion = completion)
      } else {
        println("WARNING: SynthDef may have been too large to send to remote server")
        server ! Message("/d_recv", bytes, completion)
      }
    } else {
      server ! Message("/d_recv", bytes, completion)
    }
    synth
  }
}