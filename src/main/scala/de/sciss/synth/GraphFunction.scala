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

import de.sciss.osc.{Bundle, Message}
import de.sciss.synth.ugen.WrapOut

object GraphFunction {
  private[this] final var uniqueIDCnt = 0
  private[this] final val uniqueSync  = new AnyRef

  private[this] def uniqueID(): Int = uniqueSync.synchronized {
    uniqueIDCnt += 1
    val result = uniqueIDCnt
    result
  }

  object Result {
    implicit def in[A](implicit view: A => GE): In[A] = In(view)
    implicit case object Out  extends Result[UGenSource.ZeroOut] {
      def close(in: UGenSource.ZeroOut, fadeTime: Double): Unit = ()
    }
    implicit case object Unit extends Result[scala.Unit] {
      def close(in: scala.Unit, fadeTime: Double): Unit = ()
    }
    final case class In[A](view: A => GE) extends Result[A] {
      def close(in: A, fadeTime: Double): Unit = WrapOut(view(in), fadeTime)
    }
  }
  sealed trait Result[-A] {
    def close(in: A, fadeTime: Double): Unit
  }

  def mkSynthDef[A](fun: GraphFunction[A], fadeTime: Double = -1): SynthDef = {
    val defName = s"temp_${uniqueID()}"   // more clear than using hashCode
    SynthDef(defName) {
      fun.result.close(fun.peer(), fadeTime)
    }
  }
}

final class GraphFunction[A](val peer: () => A)(implicit val result: GraphFunction.Result[A]) {

  def play(target: Node = Server.default.defaultGroup, outBus: Int = 0,
           fadeTime: Double = 0.02, addAction: AddAction = addToHead): Synth = {

    val server      = target.server
    val synthDef    = GraphFunction.mkSynthDef(this, fadeTime = fadeTime)
    val synth       = Synth(server)
    val bytes       = synthDef.toBytes()
    val synthMsg    = synth.newMsg(synthDef.name, target, Seq("i_out" -> outBus, "out" -> outBus), addAction)
    val defFreeMsg  = synthDef.freeMsg
    val completion  = Bundle.now(synthMsg, defFreeMsg)
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