/*
 *  GE.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2018 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import de.sciss.synth.ugen.{BinaryOpUGen, ChannelProxy, ChannelRangeProxy, Clip, Constant, Flatten, Fold, Impulse, LinExp, LinLin, MulAdd, Poll, UnaryOpUGen, Wrap}

object GEOps {
  private def getRate(g: GE, name: String): Rate =
    g.rate.getOrElse(throw new UnsupportedOperationException(s"`$name` input rate must be defined"))
}

/** `GEOps` are operations for graph elements (`GE`). Instead of having these operations directly defined
  * in each UGen, which is a huge list, they appear here as extension methods. Therefore, you can
  * write `SinOsc.ar.linLin ...` as if `linLin` was defined for `GE` or `SinOsc`.
  *
  * Many of these operations are defined for constant numbers, as well, for example you can
  * write `0.5.linLin ...`. These operations are defined separately.
  *
  * @see [[GE]]
  */
final class GEOps(private val g: GE) extends AnyVal {
  import GEOps.getRate

  /** Creates a proxy that represents a specific output channel of the element.
    *
    * @param index  channel-index, zero-based. Indices which are greater than or equal
    *               to the number of outputs are wrapped around.
    *
    * @return a monophonic element that represents the given channel of the receiver
    *
    * @see [[ChannelProxy]]
    */
  def out(index: Int): GE = ChannelProxy(g, index)

  /** Creates a proxy that represents a specific range of output channels of the element.
    *
    * @param range  a range of channels, zero-based. Indices which are greater than or equal
    *               to the number of outputs are wrapped around.
    *
    * @return a new element that represents the given channels of the receiver
    *
    * @see [[ChannelRangeProxy]]
    */
  def out(range: Range): GE = {
    val until = if (range.isInclusive) range.end + range.step else range.end
    ChannelRangeProxy(g, from = range.start, until = until, step = range.step)
  }

  /** Creates a `MulAdd` UGen, by first multiplying the receiver with `mul`, then adding the `add` parameter.
    * It can be used to change the value range of the receiver. For example,
    * `SinOsc` has a nominal range of `-1` to `+1`. Using `SinOsc.ar.mulAdd(3, 4)` that
    * range would become `(-1 * 3) + 4 = 1` to `(+1 * 3) + 4 = 7`.
    *
    * @see [[MulAdd]]
    */
  def mulAdd(mul: GE, add: GE): GE = MulAdd(g, mul, add)

  /** Creates a `Flatten` graph element that "flattens" the channels from a nested multi-channel structure,
    * into a one-dimensional multi-channel structure.
    *
    * For example `Pan.ar(SinOsc.ar(Seq(400, 600)))` is a nested multi-channel structure, which appears
    * as two channels (nested) during expansion. Instead, `Pan.ar(SinOsc.ar(Seq(400, 600))).flatten` appears
    * as a flat four-channel signal.
    *
    * @see [[Flatten]]
    */
  def flatten: GE = Flatten(g)

  /** Creates a `Poll` UGen, printing the receiver's values to the console, ten times a second.
    *
    * @see [[Poll]]
    */
  def poll: Poll = poll()

  /** Polls the output values of this graph element, and prints the result to the console.
    * This is a convenient method for wrapping this graph element in a `Poll` UGen.
    *
    * @param   trig     a signal to trigger the printing. If this is a constant, it is
    *                   interpreted as a frequency value and an `Impulse` generator of that frequency
    *                   is used instead.
    * @param   label    a string to print along with the values, in order to identify
    *                   different polls. Using the special label `"$auto"` (default) will generated
    *                   automatic useful labels using information from the polled graph element
    * @param   trigId   if greater then 0, a `"/tr"` OSC message is sent back to the client
    *                   (similar to `SendTrig`)
    *
    * @see  [[de.sciss.synth.ugen.Poll]]
    */
  def poll(trig: GE = 10, label: String = "$auto", trigId: GE = -1): Poll = {
    val trig1 = trig match {
      case Constant(freq) => Impulse((g.rate getOrElse audio) max control, freq, 0) // XXX good? or throw an error? should have a maxRate?
      case other          => other
    }
    val label1 = if (label != "$auto") label else {
      val str = g.toString
      val i   = str.indexOf('(')
      if (i >= 0) str.substring(0, i)
      else {
        val j = str.indexOf('@')
        if (j >= 0) str.substring(0, j)
        else str
      }
    }
    Poll(trig1.rate, trig = trig1, in = g, label = label1, trigId = trigId)
  }

  import UnaryOpUGen._

  @inline private def unOp(op: UnaryOpUGen.Op): GE = op.make(g)

  // unary ops

  /** Negatives the signal. As if multiplying the signal by `-1`.
    *
    * ===Example===
    *
    * {{{
    * // pseudo-stereo by phase inversion
    * play {
    *   val a = LFNoise1.ar(1500) * 0.5
    *   Seq(a, -a)
    * }
    * }}}
    */
  def unary_-   : GE  = unOp(Neg       )
  /** Logically negates the signal. Outputs `1` if the signal is greater than zero, otherwise outputs `0`. */
  def unary_!   : GE  = unOp(Not       )
  /** Treats the signal as integer numbers and inverts its bits. */
  def unary_~   : GE  = unOp(BitNot    )
  /** Takes the absolute values or magnitudes of the signal (negative numbers become positive). */
  def abs       : GE  = unOp(Abs       )
  // def toFloat: GE = ...
  // def toInteger: GE = ...
  /** Rounds the signal up to the next higher integer number. */
  def ceil      : GE  = unOp(Ceil      )
  /** Rounds the signal down to the next lower integer number. */
  def floor     : GE  = unOp(Floor     )
  def frac      : GE  = unOp(Frac      )
  def signum    : GE  = unOp(Signum    )
  def squared   : GE  = unOp(Squared   )
  def cubed     : GE  = unOp(Cubed     )
  def sqrt      : GE  = unOp(Sqrt      )
  def exp       : GE  = unOp(Exp       )
  def reciprocal: GE  = unOp(Reciprocal)
  def midiCps   : GE  = unOp(Midicps   )
  def cpsMidi   : GE  = unOp(Cpsmidi   )
  def midiRatio : GE  = unOp(Midiratio )
  def ratioMidi : GE  = unOp(Ratiomidi )
  def dbAmp     : GE  = unOp(Dbamp     )
  def ampDb     : GE  = unOp(Ampdb     )
  def octCps    : GE  = unOp(Octcps    )
  def cpsOct    : GE  = unOp(Cpsoct    )
  def log       : GE  = unOp(Log       )
  def log2      : GE  = unOp(Log2      )
  def log10     : GE  = unOp(Log10     )
  def sin       : GE  = unOp(Sin       )
  def cos       : GE  = unOp(Cos       )
  def tan       : GE  = unOp(Tan       )
  def asin      : GE  = unOp(Asin      )
  def acos      : GE  = unOp(Acos      )
  def atan      : GE  = unOp(Atan      )
  def sinh      : GE  = unOp(Sinh      )
  def cosh      : GE  = unOp(Cosh      )
  def tanh      : GE  = unOp(Tanh      )
  def rand      : GE  = unOp(Rand      )
  def rand2     : GE  = unOp(Rand2     )
  def linRand   : GE  = unOp(Linrand   )
  def bilinRand : GE  = unOp(Bilinrand )
  def sum3Rand  : GE  = unOp(Sum3rand  )
  def distort   : GE  = unOp(Distort   )
  def softClip  : GE  = unOp(Softclip  )
  def coin      : GE  = unOp(Coin      )

  // def even : GE              = UnOp.make( 'even, this )
  // def odd : GE               = UnOp.make( 'odd, this )
 def rectWindow : GE  = unOp(RectWindow )
 def hannWindow : GE  = unOp(HannWindow )
 def welchWindow: GE  = unOp(WelchWindow)
 def triWindow  : GE  = unOp(TriWindow  )
 def ramp       : GE  = unOp(Ramp       )
 def sCurve     : GE  = unOp(Scurve     )

  // def isPositive : GE        = UnOp.make( 'isPositive, this )
  // def isNegative : GE        = UnOp.make( 'isNegative, this )
  // def isStrictlyPositive : GE= UnOp.make( 'isStrictlyPositive, this )
  // def rho : GE               = UnOp.make( 'rho, this )
  // def theta : GE             = UnOp.make( 'theta, this )

  import BinaryOpUGen._

  // binary ops
  @inline private def binOp(op: BinaryOpUGen.Op, b: GE): GE = op.make(g, b)

  /** Adds two signals. */
  def +       (b: GE): GE = binOp(Plus    , b)

  /** Subtracts a signal from the receiver. */
  def -       (b: GE): GE = binOp(Minus   , b)

  /** Multiplies two signals. */
  def *       (b: GE): GE = binOp(Times   , b)
  // def div(b: GE): GE = ...

  /** Divides the receiver by another signal. */
  def /       (b: GE): GE = binOp(Div     , b)

  /** Take the modulus of the signal. Negative input values are
    * wrapped to positive ones, e.g. `(DC.kr(-4) % 5) sig_== DC.kr(1)`.
    * If the second operand is zero, the output is zero.
    *
    * An alias for mod.
    */
  def %       (b: GE): GE = binOp(Mod     , b)

  /** Take the modulus of the signal. Negative input values are
    * wrapped to positive ones, e.g. `DC.kr(-4).mod(5) sig_== DC.kr(1)`
    * If the second operand is zero, the output is zero.
    */
  def mod     (b: GE): GE = binOp(Mod     , b)

  /** Compares two signals and outputs one if they are identical, otherwise zero.
    *
    * Note that this can be surprising if the signals are not integer, because
    * due to floating point noise two signals may be "almost identical" but not quite,
    * thus resulting in an output of zero.
    */
  def sig_==  (b: GE): GE = binOp(Eq      , b)

  /** Compares two signals and outputs one if they are different, otherwise zero.
    *
    * Note that this can be surprising if the signals are not integer, because
    * due to floating point noise two signals may be "almost identical" but not quite,
    * thus resulting in an output of one.
    */
  def sig_!=  (b: GE): GE = binOp(Neq     , b)

  /** Compares two signals and outputs one if the receiver is less than the argument. */
  def <       (b: GE): GE = binOp(Lt      , b)

  /** Compares two signals and outputs one if the receiver is greater than the argument. */

  def >       (b: GE): GE = binOp(Gt      , b)

  /** Compares two signals and outputs one if the receiver is less than or equal to the argument.
    *
    * Note that this can be surprising if the signals are not integer, because
    * due to floating point noise two signals may be "almost identical" but not quite.
    */
  def <=      (b: GE): GE = binOp(Leq     , b)

  /** Compares two signals and outputs one if the receiver is greater than or equal to the argument.
    *
    * Note that this can be surprising if the signals are not integer, because
    * due to floating point noise two signals may be "almost identical" but not quite.
    */
  def >=      (b: GE): GE = binOp(Geq     , b)

  /** Outputs the smaller of two signals. */
  def min     (b: GE): GE = binOp(Min     , b)

  /** Outputs the larger of two signals. */
  def max     (b: GE): GE = binOp(Max     , b)

  /** Treats the signals as integer numbers and combines their bit representations through `AND`. */
  def &       (b: GE): GE = binOp(BitAnd  , b)

  /** Treats the signals as integer numbers and combines their bit representations through `OR`. */
  def |       (b: GE): GE = binOp(BitOr   , b)

  /** Treats the signals as integer numbers and combines their bit representations through `XOR`. */
  def ^       (b: GE): GE = binOp(BitXor  , b)

  /** Treats the signals as integer numbers and outputs the least common multiplier of both. */
  def lcm     (b: GE): GE = binOp(Lcm     , b)

  /** Treats the signals as integer numbers and outputs the greatest common denominator of both. */
  def gcd     (b: GE): GE = binOp(Gcd     , b)

  def roundTo (b: GE): GE = binOp(RoundTo , b)
  def roundUpTo (b: GE): GE = binOp(RoundUpTo, b)
  def trunc   (b: GE): GE = binOp(Trunc   , b)
  def atan2   (b: GE): GE = binOp(Atan2   , b)

  /** Calculates the hypotenuse of both signals, or the square root of the sum of the squares of both. */
  def hypot   (b: GE): GE = binOp(Hypot   , b)

  /** An approximate and thus faster version of `hypot` to calculate the hypotenuse of both signals,
    * or the square root of the sum of the squares of both.
    */
  def hypotApx(b: GE): GE = binOp(Hypotx  , b)

  /** Takes the power of the signal.
    *
    * '''Warning:''' Unlike a normal power operation, the signum of the
    * left operand is always preserved. I.e. `DC.kr(-0.5).pow(2)` will
    * not output `0.25` but `-0.25`. This is to avoid problems with
    * floating point noise and negative input numbers, so
    * `DC.kr(-0.5).pow(2.001)` does not result in a `NaN`, for example.
    */
  def pow     (b: GE): GE = binOp(Pow     , b)

  /** Treats the signals as integer numbers and bit-shifts the receiver by the argument to the left. */
  def <<      (b: GE): GE = binOp(LeftShift , b)

  /** Treats the signals as integer numbers and bit-shifts the receiver by the argument to the right. */
  def >>      (b: GE): GE = binOp(RightShift, b)

  // def unsgnRghtShift(b: GE): GE = ...
  // def fill(b: GE): GE = ...

  /** An optimized operation on the signals corresponding to the formula `a * b + a`. */
  def ring1   (b: GE): GE = binOp(Ring1   , b)

  /** An optimized operation on the signals corresponding to the formula `a * b + a + b`. */
  def ring2   (b: GE): GE = binOp(Ring2   , b)

  /** An optimized operation on the signals corresponding to the formula `a * a * b`. */
  def ring3   (b: GE): GE = binOp(Ring3   , b)

  /** An optimized operation on the signals corresponding to the formula `a * a * b - b * b * a`. */
  def ring4   (b: GE): GE = binOp(Ring4   , b)

  def difSqr  (b: GE): GE = binOp(Difsqr  , b)
  def sumSqr  (b: GE): GE = binOp(Sumsqr  , b)
  def sqrSum  (b: GE): GE = binOp(Sqrsum  , b)
  def sqrDif  (b: GE): GE = binOp(Sqrdif  , b)
  def absDif  (b: GE): GE = binOp(Absdif  , b)
  def thresh  (b: GE): GE = binOp(Thresh  , b)
  def amClip  (b: GE): GE = binOp(Amclip  , b)
  def scaleNeg(b: GE): GE = binOp(Scaleneg, b)
  def clip2   (b: GE): GE = binOp(Clip2   , b)
  def excess  (b: GE): GE = binOp(Excess  , b)
  def fold2   (b: GE): GE = binOp(Fold2   , b)
  def wrap2   (b: GE): GE = binOp(Wrap2   , b)

  /** A dummy operation that ensures the topological order of the receiver UGen and the argument UGen.
    * It ensures that the receiver is placed before the argument. Rarely used.
    */
  def firstArg(b: GE): GE = binOp(Firstarg, b)

  /** Outputs random values linearly distributed between the two signals. */
 def rangeRand(b: GE): GE = binOp(Rrand   , b)

  /** Outputs random values exponentially distributed between the two signals. */
  def expRand  (b: GE): GE = binOp(Exprand , b)

  /** Clips the receiver to a range defined by `low` and `high`.
    * Outputs `low` if the receiver is smaller than `low`, and
    * outputs `high` if the receiver is larger than `high`.
    */
  def clip(low: GE, high: GE): GE = {
    val r = getRate(g, "clip")
    if (r == demand) g.max(low).min(high) else Clip(r, g, low, high)
  }

  def fold(low: GE, high: GE): GE = {
    val r = getRate(g, "fold")
    if (r == demand) throw new UnsupportedOperationException("`fold` not supported for demand rate UGens")
    Fold(r, g, low, high)
  }

  def wrap(low: GE, high: GE): GE = {
    val r = getRate(g, "wrap")
    if (r == demand) throw new UnsupportedOperationException("`wrap` not supported for demand rate UGens")
    Wrap(r, g, low, high)
  }

  def linLin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
    val r = getRate(g, "linLin")
    if (r == demand) {
      (g - inLow) / (inHigh - inLow) * (outHigh - outLow) + outLow
    } else {
      LinLin(/* rate, */ g, inLow, inHigh, outLow, outHigh)
    }
  }

  def linExp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE = {
    val r = getRate(g, "linExp")
    if (r == demand) {
      (outHigh / outLow).pow((g - inLow) / (inHigh - inLow)) * outLow
    } else {
      LinExp(g.rate, g, inLow, inHigh, outLow, outHigh) // should be highest rate of all inputs? XXX
    }
  }

  def expLin(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE =
    (g / inLow).log / (inHigh / inLow).log * (outHigh - outLow) + outLow

  def expExp(inLow: GE, inHigh: GE, outLow: GE, outHigh: GE): GE =
    (outHigh / outLow).pow((g / inLow).log / (inHigh / inLow).log) * outLow
}