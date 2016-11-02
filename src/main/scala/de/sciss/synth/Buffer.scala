/*
 *  Buffer.scala
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

import de.sciss.osc.Packet
import de.sciss.model.Model
import de.sciss.model.impl.ModelImpl
import de.sciss.optional.Optional
import de.sciss.synth

object Buffer {
  type Listener     = Model.Listener[BufferManager.BufferInfo]

  type Completion   = synth.Completion[Buffer]

  def apply(server: Server = Server.default): Buffer = apply(server, allocID(server))

  private def allocID(server: Server): Int = {
    val id = server.allocBuffer(1)
    if (id == -1) {
      throw AllocatorExhausted(s"Buffer: failed to get a buffer allocated on ${server.name}")
    }
    id
  }
}
final case class Buffer(server: Server, id: Int) extends ModelImpl[BufferManager.BufferInfo] {

  //  def this(server: Server = Server.default) = this(server, Buffer.allocID(server))

  private var released        = false
  private var numFramesVar    = -1
  private var numChannelsVar  = -1
  private var sampleRateVar   = 0f

  private val sync            = new AnyRef

  override def toString = {
    val info = if (numFramesVar >= 0) s" : <$numFramesVar,$numChannelsVar,$sampleRateVar>" else ""
    s"Buffer($server,$id)$info"
  }

  /** The number of frames allocated for this buffer, or `-1` if
    * the buffer was not allocated or its number of frames is
    * not yet known.
    */
  def numFrames   = numFramesVar

  /** The number of channels allocated for this buffer, or `-1` if
    * the buffer was not allocated or its number of channels is
    * not yet known.
    */
  def numChannels = numChannelsVar

  /** The sample-rate for this buffer, or `0` if
    * the buffer was not allocated or its sample-rate is
    * not yet known.
    */
  def sampleRate  = sampleRateVar

  /** Adds this buffer to the server's buffer manager so that
    * its information fields will be tracked and updated.
    *
    * It is safe to call this method more than once.
    */
  def register(): Unit = server.bufManager.register(this)

  private[synth] def updated(change: BufferManager.BufferInfo): Unit =
    sync.synchronized {
      val info        = change.info
      numFramesVar    = info.numFrames
      numChannelsVar  = info.numChannels
      sampleRateVar   = info.sampleRate
      dispatch(change)
    }

  def queryMsg = message.BufferQuery(id)

  def freeMsg: message.BufferFree = freeMsg(None, release = true)

  /** @param   release  whether the buffer id should be immediately returned to the id-allocator or not.
    *                   if you build a system that monitors when bundles are really sent to the server,
    *                   and you need to deal with transaction abortion, you might want to pass in
    *                   <code>false</code> here, and manually release the id, using the <code>release</code>
    *                   method
    */
  def freeMsg(completion: Optional[Packet] = None, release: Boolean = true): message.BufferFree =
    sync.synchronized {
      if (release) this.release()
      message.BufferFree(id, completion)
    }

  /** Releases the buffer id to the id-allocator pool, without sending any
    * OSC message. Use with great care.
    */
  def release(): Unit =
    sync.synchronized {
      if (released) sys.error(s"$this : has already been freed")
      server.freeBuffer(id)
      released = true
    }

  def closeMsg: message.BufferClose = closeMsg(None)

  def closeMsg(completion: Optional[Packet] = None) = message.BufferClose(id, completion)

  def allocMsg(numFrames: Int, numChannels: Int = 1, completion: Optional[Packet] = None): message.BufferAlloc =
    sync.synchronized {
      numFramesVar    = numFrames
      numChannelsVar  = numChannels
      sampleRateVar   = server.sampleRate.toFloat
      message.BufferAlloc(id, numFrames, numChannels, completion)
    }

  def allocReadMsg(path: String, startFrame: Int = 0, numFrames: Int = -1,
                   completion: Optional[Packet] = None) =
    message.BufferAllocRead(id, path, startFrame, numFrames, completion)

  def allocReadChannelMsg(path: String, startFrame: Int = 0, numFrames: Int = -1, channels: Seq[Int],
                          completion: Optional[Packet] = None) =
    message.BufferAllocReadChannel(id, path, startFrame, numFrames, channels.toList, completion)

  def cueMsg(path: String, startFrame: Int = 0, completion: Optional[Packet] = None) =
    message.BufferRead(id, path, startFrame, numFrames, 0, leaveOpen = true, completion = completion)

  def readMsg(path: String, fileStartFrame: Int = 0, numFrames: Int = -1, bufStartFrame: Int = 0,
              leaveOpen: Boolean = false, completion: Optional[Packet] = None) =
    message.BufferRead(id, path, fileStartFrame, numFrames, bufStartFrame, leaveOpen, completion)

  def readChannelMsg(path: String, fileStartFrame: Int = 0, numFrames: Int = -1, bufStartFrame: Int = 0,
                     leaveOpen: Boolean = false, channels: Seq[Int],
                     completion: Optional[Packet] = None) =
    message.BufferReadChannel(id, path, fileStartFrame, numFrames, bufStartFrame, leaveOpen, channels.toList,
      completion)

  def setMsg(pairs: FillValue*) = message.BufferSet(id, pairs: _*)

  def setnMsg(values: IndexedSeq[Float]) = message.BufferSetn(id, (0, values))

  def setnMsg(pairs: (Int, IndexedSeq[Float])*) = {
    //    val numSmp = numChannels * numFrames
    //    require(pairs.forall(tup => (tup._1 >= 0 && (tup._1 + tup._2.size) <= numSmp)))
    val iPairs = pairs.map(tup => (tup._1, tup._2))
    message.BufferSetn(id, iPairs: _*)
  }

  def getMsg(indices: Int*) = message.BufferGet(id, indices: _*)

  def getnMsg(ranges: Range*) = message.BufferGetn(id, ranges: _*)

  //  /** Convenience method for creating a fill message for one given range */
  //  def fillMsg(index: Int, num: Int, value: Float) = message.BufferFill(id, FillRange(index, num, value))

  def fillMsg(value: Float) = message.BufferFill(id, FillRange(0, numFrames * numChannels, value))

  def fillMsg(ranges: FillRange*) = message.BufferFill(id, ranges: _*)

  def zeroMsg: message.BufferZero = zeroMsg(None)

  def zeroMsg(completion: Optional[Packet]) = message.BufferZero(id, completion)

  def writeMsg(path: String, fileType: io.AudioFileType = io.AudioFileType.AIFF,
               sampleFormat: io.SampleFormat = io.SampleFormat.Float, numFrames: Int = -1, startFrame: Int = 0,
               leaveOpen: Boolean = false, completion: Optional[Packet] = None) =
    message.BufferWrite(id, path, fileType, sampleFormat, numFrames, startFrame, leaveOpen, completion)

  def genMsg(command: message.BufferGen.Command) = message.BufferGen(id, command)

  /** OSC message for filling this buffer with a series of sine wave harmonics using specified amplitudes.
    *
    * @param partials   amplitudes for the harmonics. The first value specifies the amplitude of the first
    *                   partial, the second float value specifies the amplitude of the second partial, and so on.
    * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
    * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
    *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
    * @param clear      if set, the previous content is erased, otherwise the new waveform is added
    *                   to the existing content
    */
  def sine1Msg(partials: Seq[Float], normalize: Boolean = true, wavetable: Boolean = true, clear: Boolean = true) =
    genMsg(message.BufferGen.Sine1(
      partials = partials, normalize = normalize, wavetable = wavetable, clear = clear))

  /** OSC message for filling this buffer with a series of sine waves using specified frequencies and amplitudes.
    *
    * @param partials   pairs of frequencies and amplitudes for the partials.
    *                   Frequencies are given as in cycles per buffer.
    * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
    * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
    *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
    * @param clear      if set, the previous content is erased, otherwise the new waveform is added
    *                   to the existing content
    */
  def sine2Msg(partials: Seq[(Float, Float)], normalize: Boolean = true, wavetable: Boolean = true,
               clear: Boolean = true) =
    genMsg(message.BufferGen.Sine2(
      partials = partials, normalize = normalize, wavetable = wavetable, clear = clear))

  /** OSC message for filling this buffer with a series of sine waves using specified frequencies, amplitudes,
    * and phases.
    *
    * @param partials   triplets of frequencies, amplitudes and initial phases for the partials.
    *                   Frequencies are given as in cycles per buffer. Phases are given in radians.
    * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
    * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
    *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
    * @param clear      if set, the previous content is erased, otherwise the new waveform is added
    *                   to the existing content
    */
  def sine3Msg(partials: Seq[(Float, Float, Float)], normalize: Boolean = true, wavetable: Boolean = true,
               clear: Boolean = true) =
    genMsg(message.BufferGen.Sine3(
      partials = partials, normalize = normalize, wavetable = wavetable, clear = clear))

  /** OSC message for filling this buffer with a series of Chebyshev polynomials.
    * The formula of these polynomials is
    * {{{
    * cheby(n) = amplitude  * cos(n * acos(x))
    * }}}
    * To eliminate a DC offset when used as a wave-shaper, the wavetable is offset so that the center value is zero.
    *
    * @param amps       amplitudes for the harmonics. amplitudes for the harmonics. The first value specifies
    *                   the amplitude for n = 1, the second float value specifies the amplitude for n = 2, and so on.
    * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
    * @param wavetable  if set, the format of the waveform is chosen to be usable by specific UGens
    *                   such as such as [[de.sciss.synth.ugen.Shaper Shaper]] or
    *                   [[de.sciss.synth.ugen.Osc Osc]]
    * @param clear      if set, the previous content is erased, otherwise the new waveform is added
    *                   to the existing content
    */
  def chebyMsg(amps: Seq[Float], normalize: Boolean = true, wavetable: Boolean = true,
               clear: Boolean = true) =
    genMsg(message.BufferGen.Cheby(
      amps = amps, normalize = normalize, wavetable = wavetable, clear = clear))
}