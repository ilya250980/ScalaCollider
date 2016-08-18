/*
 *  Env.scala
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
package ugen

import collection.immutable.{IndexedSeq => Vec}
import de.sciss.synth.Curve.{sine => sin, _}
import language.implicitConversions

sealed trait EnvFactory[V] {
  import Env.{Segment => Seg}

  protected def create(startLevel: GE, segments: Vec[Seg]): V

  // fixed duration envelopes
  def triangle: V = triangle()

  def triangle(dur: GE = 1, level: GE = 1): V = {
    val durH = dur * 0.5f
    create(0, Vector[Seg](durH -> level, durH -> 0))
  }

  def sine: V = sine()

  def sine(dur: GE = 1, level: GE = 1): V = {
    val durH = dur * 0.5f
    create(0, Vector[Seg]((durH, level, sin), (durH, 0, sin)))
  }

  def perc: V = perc()

  def perc(attack: GE = 0.01, release: GE = 1, level: GE = 1,
           curve: Env.Curve = parametric(-4)): V =
    create(0, Vector[Seg]((attack, level, curve), (release, 0, curve)))

  def linen: V = linen()

  def linen(attack: GE = 0.01f, sustain: GE = 1, release: GE = 1,
            level: GE = 1, curve: Env.Curve = linear): V =
    create(0, Vector[Seg]((attack, level, curve), (sustain, level, curve), (release, 0, curve)))
}

object Env extends EnvFactory[Env] {
  object Curve {
    implicit def const(peer: de.sciss.synth.Curve): Curve = Const(peer)

    final case class Const(peer: de.sciss.synth.Curve) extends Curve {
      override def productPrefix = "Env$Curve$Const"

      override def toString = s"Env.Curve.Const($peer)"

      def id       : GE = Constant(peer.id)
      def curvature: GE = peer match {
        case parametric(c)  => Constant(c)
        case _              => Constant(0)
      }
    }

    def apply(id: GE, curvature: GE = 0): Curve = new Apply(id, curvature)
    def unapply(s: Curve): Option[(GE, GE)] = Some(s.id, s.curvature)

    private final case class Apply(id: GE, curvature: GE) extends Curve {
      override def productPrefix = "Env$Curve$Apply"

      override def toString = s"Env.Curve($id, $curvature)"
    }
  }
  sealed trait Curve {
    def id: GE
    def curvature: GE
  }

  object Segment {
    implicit def fromTuple3[D, L, S](tup: (D, L, S))
                                    (implicit durView: D => GE, levelView: L => GE, curveView: S => Curve): Segment =
      Segment(tup._1, tup._2, tup._3)

    implicit def fromTuple2[D, L](tup: (D, L))(implicit durView: D => GE, levelView: L => GE): Segment =
      Segment(tup._1, tup._2, linear)
  }
  final case class Segment(dur: GE, targetLevel: GE, curve: Curve = linear) {
    override def productPrefix = "Env$Segment"

    override def toString = s"Env.Segment($dur, $targetLevel, $curve)"
  }

  protected def create(startLevel: GE, segments: Vec[Segment]) = new Env(startLevel, segments)

  // ---- envelopes with sustain ----

  def cutoff: Env = cutoff()

  def cutoff(release: GE = 0.1f, level: GE = 1, curve: Curve = linear): Env = {
    val releaseLevel: GE = curve match {
      case Curve.Const(`exponential`) => 1e-05f // dbamp( -100 )
      case _ => 0
    }
    new Env(level, (release, releaseLevel, curve) :: Nil, 0)
  }

  def dadsr: Env = dadsr()

  def dadsr(delay: GE = 0.1f, attack: GE = 0.01f, decay: GE = 0.3f, sustainLevel: GE = 0.5f, release: GE = 1,
            peakLevel: GE = 1, curve: Curve = parametric(-4), bias: GE = 0): Env =
    new Env(bias, Vector[Segment](
      (delay, bias, curve),
      (attack, peakLevel + bias, curve),
      (decay, peakLevel * sustainLevel + bias, curve),
      (release, bias, curve)), 3)

  def adsr: Env = adsr()

  def adsr(attack: GE = 0.01f, decay: GE = 0.3f, sustainLevel: GE = 0.5f, release: GE = 1, peakLevel: GE = 1,
           curve: Curve = parametric(-4), bias: GE = 0): Env =
    new Env(bias, Vector[Segment](
      (attack, bias, curve),
      (decay, peakLevel * sustainLevel + bias, curve),
      (release, bias, curve)), 2)

  def asr: Env = asr()

  def asr(attack: GE = 0.01f, level: GE = 1, release: GE = 1, curve: Curve = parametric(-4)): Env =
    new Env(0, Vector[Segment]((attack, level, curve), (release, 0, curve)), 1)
}

sealed trait EnvLike extends GE {
  def startLevel: GE
  def segments: Seq[Env.Segment]
  def isSustained: Boolean
}

final case class Env(startLevel: GE, segments: Seq[Env.Segment],
                     releaseNode: GE = -99, loopNode: GE = -99)
  extends EnvLike {

  private[synth] def expand: UGenInLike = toGE

  private def toGE: GE = {
    val segmIdx = segments.toIndexedSeq
    val sizeGE: GE = segmIdx.size
    val res: Vec[GE] = startLevel +: sizeGE +: releaseNode +: loopNode +: segmIdx.flatMap(seg =>
      Vector[GE](seg.targetLevel, seg.dur, seg.curve.id, seg.curve.curvature))
    res
  }

  def rate: MaybeRate = toGE.rate

  def isSustained = releaseNode != Constant(-99)
}

object IEnv extends EnvFactory[IEnv] {
  protected def create(startLevel: GE, segments: Vec[Env.Segment]) = new IEnv(startLevel, segments)
}

final case class IEnv(startLevel: GE, segments: Seq[Env.Segment], offset: GE = 0)
  extends EnvLike {

  private[synth] def expand: UGenInLike = toGE

  private def toGE: GE = {
    val segmIdx     = segments.toIndexedSeq
    val sizeGE: GE  = segmIdx.size
    val totalDur    = segmIdx.foldLeft[GE](0)((sum, next) => sum + next.dur)
    val res: Vec[GE] = offset +: startLevel +: sizeGE +: totalDur +: segmIdx.flatMap(seg =>
      Vector[GE](seg.dur, seg.curve.id, seg.curve.curvature, seg.targetLevel))
    res
  }

  def rate: MaybeRate = toGE.rate

  def isSustained = false
}