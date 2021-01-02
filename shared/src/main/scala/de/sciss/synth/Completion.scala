/*
 *  Completion.scala
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

import de.sciss.osc.Packet

import scala.language.implicitConversions

object Completion {
  implicit def fromPacket  [A](p: Packet     ): Completion[A] = Completion[A](Some((_: A) => p), scala.None)
  implicit def fromFunction[A](fun: A => Unit): Completion[A] = Completion[A](scala.None, Some(fun))

  val None: Completion[Any] = Completion(scala.None, scala.None)
}
final case class Completion[-A](message: Option[A => Packet], action: Option[A => Unit]) {
  def mapMessage(t: A): Option[Packet] = message.map(_.apply(t))
}
