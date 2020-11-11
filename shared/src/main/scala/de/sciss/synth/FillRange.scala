/*
 *  FillRange.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import language.implicitConversions

object FillRange {
  implicit def fromFloatTuple (tup: (Int, Int, Float )): FillRange = apply(tup._1, tup._2, tup._3)
  implicit def fromDoubleTuple(tup: (Int, Int, Double)): FillRange = apply(tup._1, tup._2, tup._3.toFloat)
}
/** A fill range for control buses or buffers.
  *
  * @param index  sample offset into the buffer or channel offset into the bus.
  *               for multi channel buffers, multiply the frame offset by the number of channels
  * @param num    the number of samples to fill. for multi channel buffers,
  *               multiple the number of frames by the number of channels
  * @param value  the value to write to the bus or buffer in the given range
  */
final case class FillRange(index: Int, num: Int, value: Float) {
  def toList: List[Any] = index :: num :: value :: Nil
}

object FillValue {
  implicit def fromFloatTuple (tup: (Int, Float )): FillValue = apply(tup._1, tup._2)
  implicit def fromDoubleTuple(tup: (Int, Double)): FillValue = apply(tup._1, tup._2.toFloat)
}
/** A tuple consisting of an index and value for that index.
  *
  * @param index  sample offset into the buffer or channel offset into the bus.
  *               for multi channel buffers, multiply the frame offset by the number of channels
  * @param value  the value to write to the bus or buffer in the given range
  */
final case class FillValue(index: Int, value: Float) {
  def toList: List[Any] = index :: value :: Nil
}

