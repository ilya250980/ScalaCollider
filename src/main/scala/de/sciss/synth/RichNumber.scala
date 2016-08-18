/*
 *  RichNumber.scala
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

package de.sciss
package synth

import de.sciss.synth.ugen.Constant

object RichNumber {
  // ---- Constant / GE ----

  sealed trait NAryGEOps extends Any {
    protected def cn: Constant

    // binary ops
    def -       (b: GE): GE = cn.-        (b)
    def *       (b: GE): GE = cn.*        (b)
    def /       (b: GE): GE = cn./        (b)
    def %       (b: GE): GE = cn.%        (b)
    def sig_==  (b: GE): GE = cn.sig_==   (b)
    def sig_!=  (b: GE): GE = cn.sig_!=   (b)
    def <       (b: GE): GE = cn.<        (b)
    def >       (b: GE): GE = cn.>        (b)
    def <=      (b: GE): GE = cn.<=       (b)
    def >=      (b: GE): GE = cn.>=       (b)
    def &       (b: GE): GE = cn.&        (b)
    def |       (b: GE): GE = cn.|        (b)
    def ^       (b: GE): GE = cn.^        (b)
    def roundTo  (b: GE): GE = cn.roundTo  (b)
    def roundUpTo(b: GE): GE = cn.roundUpTo(b)
    def trunc   (b: GE): GE = cn.trunc    (b)
    def atan2   (b: GE): GE = cn.atan2    (b)
    def hypot   (b: GE): GE = cn.hypot    (b)
    def hypotx  (b: GE): GE = cn.hypotx   (b)
    def pow     (b: GE): GE = cn.pow      (b)
    def ring1   (b: GE): GE = cn.ring1    (b)
    def ring2   (b: GE): GE = cn.ring2    (b)
    def ring3   (b: GE): GE = cn.ring3    (b)
    def ring4   (b: GE): GE = cn.ring4    (b)
    def difsqr  (b: GE): GE = cn.difsqr   (b)
    def sumsqr  (b: GE): GE = cn.sumsqr   (b)
    def sqrsum  (b: GE): GE = cn.sqrsum   (b)
    def sqrdif  (b: GE): GE = cn.sqrdif   (b)
    def absdif  (b: GE): GE = cn.absdif   (b)
    def thresh  (b: GE): GE = cn.thresh   (b)
    def amclip  (b: GE): GE = cn.amclip   (b)
    def scaleneg(b: GE): GE = cn.scaleneg (b)
    def clip2   (b: GE): GE = cn.clip2    (b)
    def excess  (b: GE): GE = cn.excess   (b)
    def fold2   (b: GE): GE = cn.fold2    (b)
    def wrap2   (b: GE): GE = cn.wrap2    (b)

    //   error( "CURRENTLY DISABLED IN SYNTHETIC UGENS BRANCH" )
    //   def firstarg( b: GE ) : GE          = cn.firstarg( b )

    def clip(low: GE, high: GE): GE = cn.clip(low, high)
    def fold(low: GE, high: GE): GE = cn.fold(low, high)
    def wrap(low: GE, high: GE): GE = cn.wrap(low, high)

    def linlin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE) =
      cn.linlin(inLow, inHigh, outLow, outHigh)

    def linexp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE) =
      cn.linexp(inLow, inHigh, outLow, outHigh)

    def explin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE) =
      cn.explin(inLow, inHigh, outLow, outHigh)

    def expexp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE) =
      cn.expexp(inLow, inHigh, outLow, outHigh)
  }
}

// ---------------------------- Int ----------------------------

final class RichInt private[synth](val `this`: Int)
  extends AnyVal with RichNumber.NAryGEOps { me =>

  import me.{`this` => i}

  protected def f   = i.toFloat
  protected def d   = i.toDouble
  protected def cn  = Constant(i.toFloat)

  def reciprocal: Float = 1.0f / i
}

// ---------------------------- Float ----------------------------

final class RichFloat private[synth](val `this`: Float)
  extends AnyVal with RichNumber.NAryGEOps { me =>

  import me.{`this` => f}

  protected def d  = f.toDouble
  protected def cn = Constant(f)

  def reciprocal: Float = 1.0f / f
}

// ---------------------------- Double ----------------------------

final class RichDouble private[synth](val `this`: Double)
  extends AnyVal with RichNumber.NAryGEOps { me =>

  import me.{`this` => d}

  protected def cn = Constant(d.toFloat)

  def reciprocal: Double = 1.0 / d
}