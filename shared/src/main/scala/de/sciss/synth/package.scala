/*
 *  package.scala
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

package de.sciss

import language.implicitConversions

/** The `synth` package provides some extension methods. In particular converting numbers to
  * constant graph elements, operators on graph elements and allowing succinct creation of named controls.
  * Furthermore, it contains the `play` function to quickly test graph functions.
  */
package object synth {
  /** Positive `Float` infinity. Useful for sequence based demand UGens.
    * `-inf` gives you negative infinity.
    */
  final val inf = Float.PositiveInfinity

  // ---- implicit ----

  /* This conversion is particularly important to balance priorities,
   * as the plain pair of `intToGE` and `enrichFloat` have equal
   * priorities for an Int despite being in sub/superclass relationship,
   * probably due to the numeric widening which would be needed.
   *
   * Note that we use the same name as scala.Predef.intWrapper. That
   * way the original conversion is hidden!
   */
  implicit def intGEWrapper       (i: Int   ): synth  .RichInt    = new synth  .RichInt   (i)
  implicit def floatGEWrapper     (f: Float ): synth  .RichFloat  = new synth  .RichFloat (f)
  implicit def doubleGEWrapper    (d: Double): synth  .RichDouble = new synth  .RichDouble(d)
  implicit def intNumberWrapper   (i: Int   ): numbers.RichInt    = new numbers.RichInt   (i)
  implicit def floatNumberWrapper (f: Float ): numbers.RichFloat  = new numbers.RichFloat (f)
  implicit def doubleNumberWrapper(d: Double): numbers.RichDouble = new numbers.RichDouble(d)

  /** Provides operators for graph elements, such as `.abs`, `.linLin` or `.poll`. */
  implicit def geOps(g: GE): GEOps = new GEOps(g)

  implicit class rangeOps(private val r: Range) extends AnyVal {

    /** Creates a new `Range` shifted by the given offset. */
    def shift(n: Int): Range = {
      val start0  = r.start
      val end0    = r.end
      val start1  = start0 + n
      val end1    = end0   + n

      // overflow check
      if ((n > 0 && (start1 < start0 || end1 < end0)) ||
          (n < 0 && (start1 > start0 || end1 > end0)))
        throw new IllegalArgumentException(s"$r.shift($n) causes number overflow")

      if (r.isInclusive)
        Range.inclusive(start1, end1, r.step)
      else
        Range          (start1, end1, r.step)
    }

    private[synth] def toGetnSeq: List[Int] =
      if (r.isEmpty) Nil
      else if (r.step == 1)
        r.start :: r.length :: Nil
      else if (r.step == -1)
        (r.start - r.length + 1) :: r.length :: Nil
      else {
        r.toList.flatMap(off => off:: 1 :: Nil)
      }
  }
}