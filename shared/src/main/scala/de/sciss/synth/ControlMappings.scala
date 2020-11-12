/*
 *  ControlMappings.scala
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

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.collection.{IndexedSeq => SIndexedSeq, Seq => SSeq}
import scala.language.implicitConversions

object ControlSet {
  implicit def intFloatControlSet    (tup: (Int   , Float )): ControlSet.Value = ControlSet.Value(tup._1, tup._2)
  implicit def intIntControlSet      (tup: (Int   , Int   )): ControlSet.Value = ControlSet.Value(tup._1, tup._2.toFloat)
  implicit def intDoubleControlSet   (tup: (Int   , Double)): ControlSet.Value = ControlSet.Value(tup._1, tup._2.toFloat)
  implicit def stringFloatControlSet (tup: (String, Float )): ControlSet.Value = ControlSet.Value(tup._1, tup._2)
  implicit def stringIntControlSet   (tup: (String, Int   )): ControlSet.Value = ControlSet.Value(tup._1, tup._2.toFloat)
  implicit def stringDoubleControlSet(tup: (String, Double)): ControlSet.Value = ControlSet.Value(tup._1, tup._2.toFloat)

  // leaves stuff like ArrayWrapper untouched
  private[this] def carefulIndexed(xs: SSeq[Float]): SIndexedSeq[Float] = xs match {
    case indexed: SIndexedSeq[Float]  => indexed
    case _                            => xs.toIndexedSeq
  }

  implicit def intFloatsControlSet   (tup: (Int   , SSeq[Float])): ControlSet.Vector = ControlSet.Vector(tup._1, carefulIndexed(tup._2))
  implicit def stringFloatsControlSet(tup: (String, SSeq[Float])): ControlSet.Vector = ControlSet.Vector(tup._1, carefulIndexed(tup._2))

  object Value {
    def apply(key: String, value: Float): Value = new Value(key, value)
    def apply(key: Int   , value: Float): Value = new Value(key, value)
  }
  final case class Value private(key: Any, value: Float)
    extends ControlSet {

    private[sciss] def toSetSeq : SIndexedSeq[Any] = scala.Vector(key,    value)
    private[sciss] def toSetnSeq: SIndexedSeq[Any] = scala.Vector(key, 1, value)

    def numChannels: Int = 1
  }

  object Vector {
    def apply(key: String, values: SIndexedSeq[Float]): Vector = new Vector(key, values)
    def apply(key: Int   , values: SIndexedSeq[Float]): Vector = new Vector(key, values)
  }
  final case class Vector private(key: Any, values: SIndexedSeq[Float])
    extends ControlSet {

    private[sciss] def toSetSeq : SIndexedSeq[Any] = scala.Vector(key, values)
    private[sciss] def toSetnSeq: SIndexedSeq[Any] = key +: values.size +: values

    def numChannels: Int = values.size
  }
}

sealed trait ControlSet {
  def key: Any

  private[sciss] def toSetSeq : SIndexedSeq[Any]
  private[sciss] def toSetnSeq: SIndexedSeq[Any]

  def numChannels: Int
}

object ControlKBusMap {
  implicit def intIntControlKBus   (tup: (Int   , Int)): Single = Single(tup._1, tup._2)
  implicit def stringIntControlKBus(tup: (String, Int)): Single = Single(tup._1, tup._2)

  /** A mapping from an mono-channel control-rate bus to a synth control. */
  final case class Single(key: Any, index: Int)
    extends ControlKBusMap {

    def toMapSeq : Vec[Any] = Vector(key, index)
    def toMapnSeq: Vec[Any] = Vector(key, index, 1)
  }

  implicit def intKBusControlKBus   (tup: (Int   , ControlBus)): Multi = Multi(tup._1, tup._2.index, tup._2.numChannels)
  implicit def stringKBusControlKBus(tup: (String, ControlBus)): Multi = Multi(tup._1, tup._2.index, tup._2.numChannels)

  /** A mapping from an mono- or multi-channel control-rate bus to a synth control. */
  final case class Multi(key: Any, index: Int, numChannels: Int)
    extends ControlKBusMap {

    def toMapnSeq: Vec[Any] = Vector(key, index, numChannels)
  }
}

/** A mapping from a control-rate bus to a synth control. */
sealed trait ControlKBusMap {
  def key: Any

  def toMapnSeq: Vec[Any]
}

object ControlABusMap {
  implicit def intIntControlABus   (tup: (Int   , Int)): Single = Single(tup._1, tup._2)
  implicit def stringIntControlABus(tup: (String, Int)): Single = Single(tup._1, tup._2)

  /** A mapping from an mono-channel audio bus to a synth control. */
  final case class Single(key: Any, index: Int)
    extends ControlABusMap {

    private[sciss] def toMapaSeq : Vec[Any] = Vector(key, index)
    private[sciss] def toMapanSeq: Vec[Any] = Vector(key, index, 1)
  }

  implicit def intABusControlABus   (tup: (Int   , AudioBus)): Multi = Multi(tup._1, tup._2.index, tup._2.numChannels)
  implicit def stringABusControlABus(tup: (String, AudioBus)): Multi = Multi(tup._1, tup._2.index, tup._2.numChannels)

  /** A mapping from an mono- or multi-channel audio bus to a synth control. */
  final case class Multi(key: Any, index: Int, numChannels: Int)
    extends ControlABusMap {

    private[sciss] def toMapanSeq: Vec[Any] = Vector(key, index, numChannels)
  }
}

/** A mapping from an audio bus to a synth control.
  *
  * Note that a mapped control acts similar to an `InFeedback` UGen in that it does not matter
  * whether the audio bus was written before the execution of the synth whose control is mapped or not.
  * If it was written before, no delay is introduced, otherwise a delay of one control block is introduced.
  *
  * @see  [[de.sciss.synth.ugen.InFeedback]]
  */
sealed trait ControlABusMap {
  def key: Any

  private[sciss] def toMapanSeq: Vec[Any]
}

object ControlFillRange {
  def apply(key: String, numChannels: Int, value: Float) = new ControlFillRange(key, numChannels, value)
  def apply(key: Int   , numChannels: Int, value: Float) = new ControlFillRange(key, numChannels, value)

  implicit def intFloatControlFill    (tup: (Int   , Int, Float )): ControlFillRange = apply(tup._1, tup._2, tup._3)
  implicit def intIntControlFill      (tup: (Int   , Int, Int   )): ControlFillRange = apply(tup._1, tup._2, tup._3.toFloat)
  implicit def intDoubleControlFill   (tup: (Int   , Int, Double)): ControlFillRange = apply(tup._1, tup._2, tup._3.toFloat)
  implicit def stringFloatControlFill (tup: (String, Int, Float )): ControlFillRange = apply(tup._1, tup._2, tup._3)
  implicit def stringIntControlFill   (tup: (String, Int, Int   )): ControlFillRange = apply(tup._1, tup._2, tup._3.toFloat)
  implicit def stringDoubleControlFill(tup: (String, Int, Double)): ControlFillRange = apply(tup._1, tup._2, tup._3.toFloat)
}
final case class ControlFillRange private(key: Any, numChannels: Int, value: Float) {
  private[sciss] def toList: List[Any] = key :: numChannels :: value :: Nil
}