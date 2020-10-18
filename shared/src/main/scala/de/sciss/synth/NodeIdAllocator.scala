/*
 *  NodeIdAllocator.scala
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

final class NodeIdAllocator(user: Int, initTemp: Int) {
  private[this] var temp = initTemp
  private[this] val mask = user << 26
  private[this] val sync = new AnyRef

  // equivalent to Integer:wrap (_WrapInt)
  private def wrap(x: Int, min: Int, max: Int): Int = {
    val width   = max - min
    val widthP  = width + 1
    val off     = x - min
    val add     = (width - off) / widthP * widthP
    (off + add) % widthP + min
  }

  def alloc(): Int =
    sync.synchronized {
      val x = temp
      temp = wrap(x + 1, initTemp, 0x03FFFFFF)
      x | mask
    }
}