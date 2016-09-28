/*
 *  HelperElements.scala
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

import de.sciss.synth.Ops.stringToControl

import scala.annotation.{switch, tailrec}
import scala.collection.breakOut
import UGenSource._

/** A graph element that flattens the channels from a nested multi-channel structure.
  *
  * @param elem the element to flatten
  */
final case class Flatten(elem: GE) extends GE.Lazy {
  def rate: MaybeRate = elem.rate

  override def toString = s"$elem.flatten"

  protected def makeUGens: UGenInLike = UGenInGroup(elem.expand.flatOutputs)
}

/** Contains several helper methods to produce mixed (summed) signals. */
object Mix {
  /** A mixing idiom that corresponds to `Seq.tabulate` and to `Array.fill` in sclang. */
  def tabulate(n: Int)(fun: Int => GE): GE = Mix(GESeq(Vector.tabulate(n)(i => fun(i))))

  /** A mixing idiom that corresponds to `Seq.fill`. */
  def fill(n: Int)(thunk: => GE): GE = Mix(GESeq(Vector.fill(n)(thunk)))

  def seq(elems: GE*): GE = Mix(GESeq(elems.toIndexedSeq))

  /** A special mix that flattens all input elements before summing them,
    * guaranteeing that result is monophonic.
    */
  def mono(elem: GE): GE = Mono(elem)

  /** A mixing idiom that is not actually adding elements, but recursively folding them.
    *
    * Calling this method is equivalent to
    * {{{
    * (1 to n).foldLeft(elem) { (res, _) => fun(res) }
    * }}}
    *
    * It is often used in the SuperCollider examples to apply a filtering process such as reverberation
    * several times. For cases where the iteration index is needed, the full form as shown above
    * can be used instead.
    *
    * @param elem the input element
    * @param n    the number of iterations
    * @param fun  a function that is recursively applied to produce the output
    */
  def fold(elem: GE, n: Int)(fun: GE => GE): GE = (elem /: (1 to n)){ (res, _) => fun(res) }

  final case class Mono(elem: GE) extends GE.Lazy {
    def numOutputs  = 1
    def rate: MaybeRate = elem.rate
    override def productPrefix = "Mix$Mono"

    override def toString = s"$productPrefix($elem)"

    protected def makeUGens: UGenInLike = {
      val flat = elem.expand.flatOutputs
      Mix.makeUGen(flat)
    }
  }

  @tailrec private def makeUGen(args: Vec[UGenIn]): UGenInLike = {
    val sz = args.size
    if (sz == 0) UGenInGroup.empty else if (sz <= 4) make1(args) else {
      val mod = sz % 4
      if (mod == 0) {
        makeUGen(args.grouped(4).map(make1).toIndexedSeq)
      } else {
        // we keep the 1 to 3 tail elements, because
        // then the nested `makeUGen` call may again
        // call `make1` with the `Sum4` instances included.
        // E.g., if args.size is 6, we get `Sum3(Sum4(_,_,_,_),_,_)`,
        // whereas we would get `Plus(Plus(Sum4(_,_,_,_),_),_)`
        // if we just used the mod-0 branch.
        val (init, tail) = args.splitAt(sz - mod)
        val quad = init.grouped(4).map(make1).toIndexedSeq
        makeUGen(quad ++ tail)
      }
    }
  }

  private def make1(args: Vec[UGenIn]): UGenIn = {
    import BinaryOpUGen.Plus
    val sz = args.size
    (sz: @switch) match {
      case 1 => args.head
      case 2 => Plus.make1(args(0), args(1))
      case 3 => Sum3.make1(args)
      case 4 => Sum4.make1(args)
    }
  }
}

/** Mixes the channels of a signal together. Works exactly like the sclang counterpart.
  *
  * Here are some examples:
  *
  * {{{
  * Mix(SinOsc.ar(440 :: 660 :: Nil)) --> SinOsc.ar(440) + SinOsc.ar(660)
  * Mix(SinOsc.ar(440)) --> SinOsc.ar(440)
  * Mix(Pan2.ar(SinOsc.ar)) --> left + right
  * Mix(Pan2.ar(SinOsc.ar(440 :: 660 :: Nil))) --> [left(440) + left(660), right(440) + right(660)]
  * }}}
  */
final case class Mix(elem: GE) extends UGenSource.SingleOut {  // XXX TODO: should not be UGenSource

  def rate: MaybeRate = elem.rate

  protected def makeUGens: UGenInLike = unwrap(this, elem.expand.outputs)

  private[synth] def makeUGen(args: Vec[UGenIn]): UGenInLike = Mix.makeUGen(args)

  override def toString: String = elem match {
    case GESeq(elems) => elems.mkString(s"$productPrefix.seq(", ", ", ")")
    case _            => s"$productPrefix($elem)"
  }
}

final case class Zip(elems: GE*) extends GE.Lazy {
  def rate: MaybeRate = MaybeRate.reduce(elems.map(_.rate): _*)

  protected def makeUGens: UGenInLike = {
    val exp: Vec[UGenInLike] = elems.map(_.expand)(breakOut)
    val sz    = exp.map(_.outputs.size) // exp.view.map ?
    val minSz = sz.min
    UGenInGroup((0 until minSz).flatMap(i => exp.map(_.unwrap(i))))
  }
}

object Reduce {
  import BinaryOpUGen.{BitAnd, BitOr, BitXor, Max, Min, Plus, Times}
  /** Same result as `Mix( _ )` */
  def +  (elem: GE) = apply(elem, Plus  )
  def *  (elem: GE) = apply(elem, Times )
  //   def all_sig_==( elem: GE ) = ...
  //   def all_sig_!=( elem: GE ) = ...
  def min(elem: GE) = apply(elem, Min   )
  def max(elem: GE) = apply(elem, Max   )
  def &  (elem: GE) = apply(elem, BitAnd)
  def |  (elem: GE) = apply(elem, BitOr )
  def ^  (elem: GE) = apply(elem, BitXor)
}

final case class Reduce(elem: GE, op: BinaryOpUGen.Op) extends UGenSource.SingleOut {
  // XXX TODO: should not be UGenSource

  def rate: MaybeRate = elem.rate

  protected def makeUGens: UGenInLike = unwrap(this, elem.expand.outputs)

  private[synth] def makeUGen(args: Vec[UGenIn]): UGenInLike = args match {
    case head +: tail => (head /: tail)(op.make1)
    case _            => UGenInGroup.empty
  }
}

/** An element which writes an input signal to a bus, optionally applying a short fade-in.
  * This is automatically added when using the `play { ... }` syntax. If the fade time is
  * given, an envelope is added with a control named `"gate"` which can be used to release
  * the synth. The bus is given by a control named `"out"` and defaults to zero.
  */
object WrapOut {
  private def makeFadeEnv(fadeTime: Float): UGenIn = {
    val cFadeTime = "fadeTime".kr(fadeTime)
    val cGate     = "gate".kr(1f)
    val startVal  = cFadeTime <= 0
    val env       = Env(startVal, List(Env.Segment(1, 1, Curve.parametric(-4)), Env.Segment(1, 0, Curve.sine)), 1)
    val res       = EnvGen.kr(env, gate = cGate, timeScale = cFadeTime, doneAction = freeSelf)
    res.expand.flatOutputs.head
  }
}

// XXX TODO: This should not be a UGenSource.ZeroOut but just a Lazy.Expander[Unit] !
/** An element which writes an input signal to a bus, optionally applying a short fade-in.
  * This is automatically added when using the `play { ... }` syntax. If the fade time is
  * given, an envelope is added with a control named `"gate"` which can be used to release
  * the synth. The bus is given by a control named `"out"` and defaults to zero.
  */
final case class WrapOut(in: GE, fadeTime: Option[Float] = Some(0.02f)) extends UGenSource.ZeroOut with WritesBus {
  import WrapOut._

  protected def makeUGens: Unit = unwrap(this, in.expand.outputs)

  private[synth] def makeUGen(ins: Vec[UGenIn]): Unit = {
    if (ins.isEmpty) return
    val rate = ins.map(_.rate).max
    if ((rate == audio) || (rate == control)) {
      val ins3 = fadeTime match {
        case Some(fdt) =>
          val env = makeFadeEnv(fdt)
          ins.map(BinaryOpUGen.Times.make1(_, env))
        case None => ins
      }
      val cOut = "out".kr(0f)
      Out.ar(cOut, ins3)
    }
  }
}

/** A graph element that spreads a sequence of input channels across a ring of output channels.
  * This works by feeding each input channel through a dedicated `PanAz` UGen, and mixing the
  * results together.
  *
  * The panning position of each input channel with index `ch` is calculated by the formula:
  * {{{
  * val pf = 2.0 / (number-of-input-channels - 1) * (number-of-output-channels - 1) / number-of-output-channels
  * ch * pf + center
  * }}}
  */
object SplayAz {
  /** @param numChannels  the number of output channels
    * @param in           the input signal
    * @param spread       the spacing between input channels with respect to the output panning
    * @param center       the position of the first channel (see `PanAz`)
    * @param level        a global gain factor (see `PanAz`)
    * @param width        the `width` parameter for each `PanAz`
    * @param orient       the `orient` parameter for each `PanAz`
    *
    * @see  [[de.sciss.synth.ugen.PanAz]]
    */
  def ar(numChannels: Int, in: GE, spread: GE = 1f, center: GE = 0f, level: GE = 1f, width: GE = 2f, orient: GE = 0f) =
    apply(audio, numChannels, in, spread, center, level, width, orient)
}
final case class SplayAz(rate: Rate, numChannels: Int, in: GE, spread: GE, center: GE, level: GE, width: GE, orient: GE)
  extends GE.Lazy {

  def numOutputs = numChannels

  protected def makeUGens: UGenInLike = {
    val _in     = in.expand
    val numIn   = _in.outputs.size
    // last channel must have position (2 * i / (numIn - 1) * (numOut - 1) / numOut)
    val pf  = if (numIn < 2 || numOutputs == 0) 0.0 else 2.0 / (numIn - 1) * (numOutputs - 1) / numOutputs
    val pos = Seq.tabulate(numIn)(center + _ * pf)
    val mix = Mix(PanAz(rate, numChannels, _in, pos, level, width, orient))
    mix
  }
}

/** A graph element which maps a linear range to another linear range.
  * The equivalent formula is `(in - srcLo) / (srcHi - srcLo) * (dstHi - dstLo) + dstLo`.
  *
  * '''Note''': No clipping is performed. If the input signal exceeds the input range, the output will also exceed its range.
  *
  * @param in              The input signal to convert.
  * @param srcLo           The lower limit of input range.
  * @param srcHi           The upper limit of input range.
  * @param dstLo           The lower limit of output range.
  * @param dstHi           The upper limit of output range.
  *
  * @see [[de.sciss.synth.ugen.LinExp]]
  * @see [[de.sciss.synth.ugen.Clip]]
  */
final case class LinLin(/* rate: MaybeRate, */ in: GE, srcLo: GE = 0f, srcHi: GE = 1f, dstLo: GE = 0f, dstHi: GE = 1f)
  extends GE.Lazy {

  def rate: MaybeRate = in.rate // XXX correct?

  protected def makeUGens: UGenInLike = {
    val scale  = (dstHi - dstLo) / (srcHi - srcLo)
    val offset = dstLo - (scale * srcLo)
    MulAdd(in, scale, offset)
  }
}

object Silent {
  def ar: Silent = ar()

  def ar(numChannels: Int = 1) = apply(numChannels)
}

final case class Silent(numChannels: Int) extends GE.Lazy with AudioRated {

  protected def makeUGens: UGenInLike = {
    val dc = DC.ar(0)
    val ge: GE = if (numChannels == 1) dc else Seq.fill(numChannels)(dc)
    ge
  }
}

/** A graph element which reads from a connected sound driver input. This is a convenience
  * element for accessing physical input signals, e.g. from a microphone connected to your
  * audio interface. It expands to a regular `In` UGen offset by `NumOutputBuses.ir`.
  *
  * For example, consider an audio interface with channels 1 to 8 being analog line inputs,
  * channels 9 and 10 being AES/EBU and channels 11 to 18 being ADAT inputs. To read a combination
  * of the analog and ADAT inputs, either of the following statement can be used:
  *
  * {{{
  * PhysicalIn(Seq(0, 8), Seq(8, 8))
  * PhysicalIn(Seq(0, 8), Seq(8))      // numChannels wraps!
  * }}}
  */
object PhysicalIn {
  /** Short cut for reading a mono signal from the first physical input. */
  def ar: PhysicalIn = ar()

  /** @param indices       the physical index to read from (beginning at zero which corresponds to
    *                      the first channel of the audio interface or sound driver). Maybe be a
    *                      multichannel element to specify discrete indices.
    * @param numChannels   the number of consecutive channels to read. For discrete indices this
    *                      applies to each index!
    */
  def ar(indices: GE = 0, numChannels: Int = 1): PhysicalIn = apply(indices, Seq(numChannels))

  /** @param indices       the physical index to read from (beginning at zero which corresponds to
    *                      the first channel of the audio interface or sound driver). Maybe be a
    *                      multichannel element to specify discrete indices.
    * @param numChannels   the number of consecutive channels to read for each index. Wraps around
    *                      if the sequence has less elements than indices has channels.
    */
  def ar(indices: GE, numChannels: Seq[Int]): PhysicalIn = apply(indices, numChannels)

  //   def apply( index: GE, moreIndices: GE* ) : PhysicalIn = apply( (index +: moreIndices).map( (_, 1) ): _* )
}

/** A graph element which reads from a connected sound driver input. This is a convenience
  * element for accessing physical input signals, e.g. from a microphone connected to your
  * audio interface. It expands to a regular `In` UGen offset by `NumOutputBuses.ir`.
  *
  * For example, consider an audio interface with channels 1 to 8 being analog line inputs,
  * channels 9 and 10 being AES/EBU and channels 11 to 18 being ADAT inputs. To read a combination
  * of the analog and ADAT inputs, either of the following statement can be used:
  *
  * {{{
  * PhysicalIn(Seq(0, 8), Seq(8, 8))
  * PhysicalIn(Seq(0, 8), Seq(8))      // numChannels wraps!
  * }}}
  *
  * @param indices       the physical index to read from (beginning at zero which corresponds to
  *                      the first channel of the audio interface or sound driver). Maybe be a
  *                      multichannel element to specify discrete indices.
  * @param numChannels   the number of consecutive channels to read for each index. Wraps around
  *                      if the sequence has less elements than indices has channels.
  */
final case class PhysicalIn(indices: GE, numChannels: Seq[Int]) extends GE.Lazy with AudioRated {

  protected def makeUGens: UGenInLike = {
    val offset       = NumOutputBuses.ir
    val _indices     = indices.expand.outputs
    val iNumCh       = numChannels.toIndexedSeq
    val _numChannels = if (_indices.size <= iNumCh.size) iNumCh
    else {
      Vector.tabulate(_indices.size)(ch => iNumCh(ch % iNumCh.size))
    }

    Flatten((_indices zip _numChannels).map {
      case (index, num) => In.ar(index + offset, num)
    })
  }
}

/** A graph element which writes to a connected sound driver output. This is a convenience
  * element for `Out` with the ability to provide a set of discrete indices to which
  * corresponding channels of the input signal are mapped, whereas multichannel expansion
  * with respect to the index argument of `Out` typically do not achieve what you expect.
  *
  * For example, to flip left and right when writing a stereo signal:
  *
  * {{{
  * // sine appears on the right channel, and noise on the left
  * play { PhysicalOut( Seq( 1, 0 ), Seq( SinOsc.ar * LFPulse.ar(4), WhiteNoise.ar ) * 0.2 )}
  * }}}
  */
object PhysicalOut {
  /** @param indices       the physical index to write to (beginning at zero which corresponds to
    *                      the first channel of the audio interface or sound driver). may be a
    *                      multichannel argument to specify discrete channels. In this case, any
    *                      remaining channels in `in` are associated with the last bus index offset.
    * @param in            the signal to write
    */
  def ar(indices: GE = 0, in: GE): PhysicalOut = apply(indices, in)
}

final case class PhysicalOut(indices: GE, in: GE) extends UGenSource.ZeroOut with AudioRated {
  // XXX TODO: should not be UGenSource

  protected def makeUGens: Unit = {
    val _in = in.expand.outputs
    val _indices = indices.expand.outputs
    _indices.dropRight(1).zip(_in).foreach {
      case (index, sig) =>
        Out.ar(index, sig)
    }
    (_indices.lastOption, _in.drop(_indices.size - 1)) match {
      case (Some(index), sig) if sig.nonEmpty =>
        Out.ar(index, sig)
      case _ =>
    }
  }

  private[synth] def makeUGen(args: Vec[UGenIn]) = () // XXX not used, ugly
}

/** A graph element that produces an integer sequence
  * from zero until the number-of-channels of the input element.
  *
  * @param in the element whose indices to produce
  */
final case class ChannelIndices(in: GE) extends UGenSource.SingleOut with ScalarRated {
  protected def makeUGens: UGenInLike = unwrap(this, in.expand.outputs)

  private[synth] def makeUGen(args: Vec[UGenIn]): UGenInLike = args.indices: GE
}

/** A graph element that produces an integer with number-of-channels of the input element.
  *
  * @param in the element whose number-of-channels to produce
  */
final case class NumChannels(in: GE) extends UGenSource.SingleOut with ScalarRated {
  protected def makeUGens: UGenInLike = unwrap(this, in.expand.outputs)

  private[synth] def makeUGen(args: Vec[UGenIn]): UGenInLike = Constant(args.size)
}

object Pad {
// Note: this is not needed any longer, because now LocalIn takes a GE argument
// Before: `Pad.LocalIn(ref)`. Now: `LocalIn(Pad(0, ref))`.
//
//  object LocalIn {
//    def ar(ref: GE): LocalIn = apply(audio  , ref)
//    def kr(ref: GE): LocalIn = apply(control, ref)
//  }
//  final case class LocalIn(rate: Rate, ref: GE) extends GE.Lazy {
//    override def productPrefix = "Pad$LocalIn"
//
//    protected def makeUGens: UGenInLike = {
//      val numChannels = ref.expand.flatOutputs.size
//      ugen.LocalIn(rate, init = GESeq(Vector.fill(numChannels)(Constant.C0)))
//    }
//  }

  /** Enforces multi-channel expansion for the input argument
    * even if it is passed into a vararg input of another UGen.
    * This is done by wrapping it in a `GESeq`.
    */
  def Split(in: GE): GE = GESeq(Vector(in))
}
/** A graph element that controls the multi-channel expansion of
  * its `in` argument to match the `to` argument by padding (extending
  * and wrapping) it.
  *
  * @param in the element to replicate
  * @param to the reference element that controls the multi-channel expansion.
  *           the signal itself is not used or output by `Pad`.
  */
final case class Pad(in: GE, to: GE) extends UGenSource.SingleOut {
  def rate: MaybeRate = in.rate

  protected def makeUGens: UGenInLike = unwrap(this, Vector(in.expand, to.expand))

  private[synth] def makeUGen(args: Vec[UGenIn]): UGenInLike = args.head
}