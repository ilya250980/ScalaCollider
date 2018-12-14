/*
 *  ServerMessages.scala
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
package message

import java.nio.ByteBuffer

import de.sciss.osc
import de.sciss.osc.{Message, Packet}

import scala.collection.{IndexedSeq => SIndexedSeq}
import scala.language.implicitConversions

/** Identifies messages received or sent by the SuperCollider server. */
sealed trait ServerMessage

/** Identifies messages sent to the SuperCollider server. */
sealed trait Send extends ServerMessage {
  def isSynchronous: Boolean
}

/** Identifies messages sent to the server which are executed synchronously. */
sealed trait SyncSend extends Send {
  final def isSynchronous = true
}

/** Identifies command messages sent to the server which are executed synchronously and do not return a message. */
trait SyncCmd extends SyncSend

/** Identifies query messages sent to the server which are executed synchronously and produce a reply message. */
trait SyncQuery extends SyncSend

/** Identifies messages sent to the server which are executed asynchronously and reply with a form of done-message. */
sealed trait AsyncSend extends Send {
  final def isSynchronous = false
}

/** Identifies messages returned by SuperCollider server. */
trait Receive extends ServerMessage

/** Represents a `/synced` message, a reply from the server acknowledging that
  * all asynchronous operations up to the corresponding `/sync` message (i.e. with
  * the same id) have been completed
  */
final case class Synced(id: Int) extends Message("/synced", id) with Receive

/** Represents a `/sync` message, which is queued with the asynchronous messages
  * on the server, and which, when executed, triggers a corresponding `/synced` reply
  * message (i.e. with the same id)
  *
  * @param   id    an arbitrary identifier which can be used to match the corresponding
  *                reply message. typically the id is incremented by 1 for each
  *                `/sync` message sent out.
  */
final case class Sync(id: Int) extends Message("/sync", id) with AsyncSend {
  def reply = Synced(id)
}

final case class StatusReply(numUGens: Int, numSynths: Int, numGroups: Int, numDefs: Int, avgCPU: Float,
                             peakCPU: Float, sampleRate: Double, actualSampleRate: Double)
  extends Message("/status.reply", 1, numUGens, numSynths, numGroups, numDefs, avgCPU, peakCPU,
                                      sampleRate, actualSampleRate)
  with Receive {

  override def toString(): String = {
    val s1 = f"numUGens = $numUGens, numSynths = $numSynths, numGroups = $numGroups"
    val s2 = f"cpu = $avgCPU%1.1f%%/$peakCPU%1.1f%%, sampleRate = $sampleRate%1.0f/$actualSampleRate%1.2f"
    s"$productPrefix($s1, $s2)"
  }
}

/** The `/status` message that queries the current statistics from the server. */
case object Status extends Message("/status") with SyncQuery

/** The `/dumpOSC` message that selects how the server reports incoming OSC packets.
  *
  * __Note:__ The OSC dump behavior of scsynth has long time been broken. It is recommended
  * to use client-side only reporting, provided by the `dumpOSC` method of the `Server` class.
  *
  * @see [[Server#dumpOSC]]
  */
final case class DumpOSC(mode: osc.Dump) extends Message("/dumpOSC", mode.id) with SyncCmd

case object ClearSched extends Message("/clearSched") with SyncCmd

object Error {
  val Off      : Error = apply(0)
  val On       : Error = apply(1)
  val BundleOff: Error = apply(-1)
  val BundleOn : Error = apply(-2)
}
/** Produces an `/error` message that selects how the server will report errors to the console. */
final case class Error(mode: Int) extends Message("/error", mode) with SyncCmd

sealed trait NodeChange extends Receive {
  def nodeId: Int
  def info: NodeInfo.Data
}

private[synth] sealed trait NodeMessageFactory {
  def apply(nodeId: Int, info: NodeInfo.Data): Message
}

/** The `/n_go` message is received from the server when a node has been newly created.
  *
  * @see [[SynthNew]]
  * @see [[GroupNew]]
  */
object NodeGo extends NodeMessageFactory
final case class NodeGo(nodeId: Int, info: NodeInfo.Data)
  extends Message("/n_go", info.toList(nodeId): _*) with NodeChange

/** The `/n_end` message is received from the server when a node has been freed.
  *
  * @see [[NodeFree]]
  */
object NodeEnd extends NodeMessageFactory
final case class NodeEnd(nodeId: Int, info: NodeInfo.Data)
  extends Message("/n_end", info.toList(nodeId): _*) with NodeChange

/** The `/n_on` message is received from the server when a node has resumed.
  *
  * @see [[NodeRun]]
  */
object NodeOn extends NodeMessageFactory
final case class NodeOn(nodeId: Int, info: NodeInfo.Data)
  extends Message("/n_on", info.toList(nodeId): _*) with NodeChange

/** The `/n_off` message is received from the server when a node has been paused.
  *
  * @see [[NodeRun]]
  */
object NodeOff extends NodeMessageFactory
final case class NodeOff(nodeId: Int, info: NodeInfo.Data)
  extends Message("/n_off", info.toList(nodeId): _*) with NodeChange

/** The `/n_move` message is received from the server when a node has changed its position in the tree.
  *
  * @see [[NodeBefore]]
  * @see [[NodeAfter]]
  * @see [[GroupHead]]
  * @see [[GroupTail]]
  */
object NodeMove extends NodeMessageFactory
final case class NodeMove(nodeId: Int, info: NodeInfo.Data)
  extends Message("/n_move", info.toList(nodeId): _*) with NodeChange

object NodeInfo extends NodeMessageFactory {
  /** @see [[NodeInfo]]
    * @see [[NodeGo]]
    * @see [[NodeEnd]]
    * @see [[NodeOn]]
    * @see [[NodeOff]]
    * @see [[NodeMove]]
    */
  abstract sealed class Data {
    /** The identifier of the node's parent group. */
    def parentId: Int
    /** The identifier of the node preceding this node within the same group,
      * or `-1` if there is no predecessor.
      */
    def predId: Int
    /** The identifier of the node following this node within the same group,
      * or `-1` if there is no successor.
      */
    def succId: Int

    /** The serial presentation of the information within an OSC message.
      * This method is used internally and probably not useful in other ways.
      */
    def toList(nodeId: Int): List[Any]
  }

  /** Information about a `Synth` node. */
  final case class SynthData(parentId: Int, predId: Int, succId: Int) extends Data {
    def toList(nodeId: Int): List[Any] = nodeId :: parentId :: predId :: succId :: 0 :: Nil
  }

  /** Information about a `Group` node. */
  final case class GroupData(parentId: Int, predId: Int, succId: Int, headId: Int, tailId: Int) extends Data {
    def toList(nodeId: Int): List[Any] = nodeId :: parentId :: predId :: succId :: 1 :: headId :: tailId :: Nil
  }
}

/** An `/n_info` message is received as a reply to an `/n_query` message.
  *
  * @param nodeId the identifier of the node for which information has been received
  * @param info   the information object describing the topological position of the node
  *
  * @see [[NodeQuery]]
  */
final case class NodeInfo(nodeId: Int, info: NodeInfo.Data)
  extends Message("/n_info", info.toList(nodeId): _*) with NodeChange

// we need List[Any] as scala would otherwise expand to List[Float]!
object BufferInfo {
  /** @see [[BufferInfo]] */
  final case class Data(bufId: Int, numFrames: Int, numChannels: Int, sampleRate: Float)
}

/** A `/b_info` message is received in reply to a `/b_query` message.
  *
  * @see [[BufferQuery]]
  */
final case class BufferInfo(data: BufferInfo.Data*)
  extends Message("/b_info", data.flatMap(info =>
    List[Any](info.bufId, info.numFrames, info.numChannels, info.sampleRate)): _*)
  with Receive

// ---- messages to the server ----

/** The `/notify` messages registers or de-registers a client with respect
  * to receiving reply messages from the server.
  *
  * Booting or connecting a server through the regular API automatically handles and does
  * not require the explicit use of this message.
  *
  * @param  on    if `true`, the client is registered, if `false` it is de-registered.
  */
final case class ServerNotify(on: Boolean)
  extends Message("/notify", on)
  with AsyncSend

/** The `/quit` message tells the server to shut down.
  *
  * @see [[Server#quit]]
  */
case object ServerQuit extends Message("/quit") with AsyncSend

sealed trait HasCompletion extends AsyncSend {
  def completion: Option[Packet]
  def updateCompletion(completion: Option[Packet]): Message with AsyncSend with HasCompletion
}

/** The `/b_query` messages requests a `/b_info` reply message from the server, providing
  * information about the size and sample-rate of the specified buffers.
  *
  * @param ids  a sequence of buffer identifiers to query
  *
  * @see [[Buffer#queryMsg]]
  */
final case class BufferQuery(ids: Int*) extends Message("/b_query", ids: _*) with SyncQuery

/** The `/b_free` message frees a buffer on the server side. The client side
  * typically maintains a logical list of allocated buffer identifiers as well,
  * so one should normally rely on the specific client side API to correctly
  * free a buffer.
  *
  * @see [[Buffer#freeMsg]]
  * @see [[BufferClose]]
  * @see [[BufferAlloc]]
  */
final case class BufferFree(id: Int, completion: Option[Packet])
  extends Message("/b_free", (id: Any) :: (completion.toList: List[Any]): _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferFree = copy(completion = completion)
}

/** The `/b_close` message ensures that a buffer closes an associated audio-file.
  * This is a no-op if the buffer is not associated with an audio-file or if that
  * file is already closed.
  *
  * @see [[Buffer#closeMsg]]
  * @see [[BufferFree]]
  * @see [[BufferAlloc]]
  */
final case class BufferClose(id: Int, completion: Option[Packet])
  extends Message("/b_close", (id: Any) :: (completion.toList: List[Any]): _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferClose = copy(completion = completion)
}

/** The `/b_alloc` message tells the server to allocate memory for a buffer associated
  * with its logical identifier.
  *
  * @see [[Buffer#allocMsg]]
  * @see [[BufferFree]]
  * @see [[BufferAllocRead]]
  * @see [[BufferAllocReadChannel]]
  */
final case class BufferAlloc(id: Int, numFrames: Int, numChannels: Int, completion: Option[Packet])
  extends Message("/b_alloc", id :: numFrames :: numChannels :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferAlloc = copy(completion = completion)
}

/** The `/b_allocRead` message tells the server to allocate memory for a buffer and read
  * in a portion of an audio-file. The number of channels
  * and the sample-rate of the buffer are determined by that audio-file.
  *
  * @param  id          the identifier to use for the buffer. It must denote a currently un-allocated buffer
  *                     and be greater than or equal to zero and less than the maximum number of buffers.
  * @param  path        the path of the audio-file to read. Since the server is an independent process, this must
  *                     resolve with respect to the server's current working directory. If the server is running on
  *                     a remote node, the path will be resolved in the server's local file system.
  * @param  startFrame  the offset in frames into the audio-file to begin reading from
  * @param  numFrames   the number of frames to read which will be the size of the allocated buffer. The special
  *                     value less than or equal to zero denotes that the number of frames available in the file
  *                     from the given offset is used (the entire file will be read).
  *
  * @see [[Buffer#allocReadMsg]]
  * @see [[BufferFree]]
  * @see [[BufferAlloc]]
  * @see [[BufferAllocReadChannel]]
  * @see [[BufferRead]]
  */
final case class BufferAllocRead(id: Int, path: String, startFrame: Int, numFrames: Int, completion: Option[Packet])
  extends Message("/b_allocRead", id :: path :: startFrame :: numFrames :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferAllocRead = copy(completion = completion)
}

/** The `/b_allocReadChannel` message tells the server to allocate memory for a buffer and read
  * in a portion of an audio-file, selecting a subset of its channels. The number of channels
  * is given by the size of the `channels` argument, and the sample-rate of the buffer is determined
  * by the audio-file.
  *
  * @param  id          the identifier to use for the buffer. It must denote a currently un-allocated buffer
  *                     and be greater than or equal to zero and less than the maximum number of buffers.
  * @param  path        the path of the audio-file to read. Since the server is an independent process, this must
  *                     resolve with respect to the server's current working directory. If the server is running on
  *                     a remote node, the path will be resolved in the server's local file system.
  * @param  startFrame  the offset in frames into the audio-file to begin reading from
  * @param  numFrames   the number of frames to read which will be the size of the allocated buffer. The special
  *                     value of `-1` denotes that the number of frames available in the file
  *                     from the given offset is used (the entire file will be read).
  * @param  channels    a sequence of channel indices to read. Zero corresponds to the first channel of the file.
  *
  * @see [[Buffer#allocReadChannelMsg]]
  * @see [[BufferFree]]
  * @see [[BufferAlloc]]
  * @see [[BufferAllocRead]]
  * @see [[BufferReadChannel]]
  */
final case class BufferAllocReadChannel(id: Int, path: String, startFrame: Int, numFrames: Int,
                                        channels: List[Int], completion: Option[Packet])
  extends Message("/b_allocReadChannel", id :: path :: startFrame :: numFrames :: channels ::: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferAllocReadChannel = copy(completion = completion)
}

/** The `/b_read` message tells the server to read a portion of an audio-file into an existing buffer.
  *
  * @param  id              the identifier of the buffer to read into.
  * @param  path            the path of the audio-file to read.
  * @param  fileStartFrame  the offset in frames into the audio-file to begin reading from
  * @param  numFrames       the number of frames to read which will be the size of the allocated buffer. The special
  *                         value of `-1` denotes that as many frames are read as are available in the file or
  *                         fit into the buffer.
  * @param  bufStartFrame   the frame offset in the buffer to begin writing to.
  * @param  leaveOpen       if `true`, leaves the file open for streaming with the
  *                         [[de.sciss.synth.ugen.DiskIn DiskIn]] UGen.
  *
  * @see [[Buffer#readMsg]]
  * @see [[BufferAllocRead]]
  * @see [[BufferReadChannel]]
  */
final case class BufferRead(id: Int, path: String, fileStartFrame: Int, numFrames: Int, bufStartFrame: Int,
                            leaveOpen: Boolean, completion: Option[Packet])
  extends Message("/b_read",
    id :: path :: fileStartFrame :: numFrames :: bufStartFrame :: leaveOpen :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferRead = copy(completion = completion)
}

/** The `/b_readChannel` message tells the server to read a portion of an audio-file into an existing buffer,
  * selecting a subset of the file's channels.
  *
  * @param  id              the identifier of the buffer to read into.
  * @param  path            the path of the audio-file to read.
  * @param  fileStartFrame  the offset in frames into the audio-file to begin reading from
  * @param  numFrames       the number of frames to read which will be the size of the allocated buffer. The special
  *                         value of `-1` denotes that as many frames are read as are available in the file or
  *                         fit into the buffer.
  * @param  bufStartFrame   the frame offset in the buffer to begin writing to.
  * @param  leaveOpen       if `true`, leaves the file open for streaming with the
  *                         [[de.sciss.synth.ugen.DiskIn DiskIn]] UGen.
  * @param  channels        a sequence of channel indices to read. Zero corresponds to the first channel of the file.
  *
  * @see [[Buffer#readChannelMsg]]
  * @see [[BufferAllocReadChannel]]
  * @see [[BufferRead]]
  */
final case class BufferReadChannel(id: Int, path: String, fileStartFrame: Int, numFrames: Int,
                                   bufStartFrame: Int, leaveOpen: Boolean, channels: List[Int],
                                   completion: Option[Packet])
  extends Message("/b_readChannel",
    id :: path :: fileStartFrame :: numFrames :: bufStartFrame :: leaveOpen :: channels ::: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferReadChannel = copy(completion = completion)
}

/** The `/b_zero` message clears the contents of a buffer (all samples will be zero).
  *
  * @see [[Buffer#zeroMsg]]
  * @see [[BufferFill]]
  * @see [[BufferSet]]
  * @see [[BufferSetn]]
  * @see [[BufferGen]]
  */
final case class BufferZero(id: Int, completion: Option[Packet])
  extends Message("/b_zero", (id: Any) :: (completion.toList: List[Any]): _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferZero = copy(completion = completion)
}

/** The `/b_write` message writes a portion of the buffer contents to an audio-file.
  *
  * @param  id              the identifier of the buffer whose contents to write.
  * @param  path            the path of the audio-file to write to.
  * @param  fileType        the header format of the audio-file
  * @param  sampleFormat    the sample resolution of the audio-file
  * @param  numFrames       the number of frames to write. The special
  *                         value of `-1` denotes that the whole buffer content (or the remainder
  *                         after the `startFrame`) is written out.
  * @param  startFrame      the frame offset in the buffer to begin reading from
  * @param  leaveOpen       if `true`, leaves the file open for streaming with
  *                         the [[de.sciss.synth.ugen.DiskOut DiskOut]] UGen.
  *
  * @see [[Buffer#writeMsg]]
  * @see [[BufferRead]]
  */
final case class BufferWrite(id: Int, path: String, fileType: io.AudioFileType, sampleFormat: io.SampleFormat,
                             numFrames: Int, startFrame: Int, leaveOpen: Boolean,
                             completion: Option[Packet])
  extends Message("/b_write",
    id :: path :: fileType.id :: sampleFormat.id :: numFrames :: startFrame :: leaveOpen :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): BufferWrite = copy(completion = completion)
}

/** The `/b_set` message sets individual samples of the buffer to given values.
  *
  * @param  id      the identifier of the buffer whose contents to write.
  * @param  pairs   pairs of sample offsets and sample values. The offsets are de-interleaved samples,
  *                 so for multi-channel buffers, to address a particular frame, the frame index must
  *                 be multiplied by the number of channels and offset by the channel to write into.
  *
  * @see [[Buffer#setMsg]]
  * @see [[BufferSetn]]
  * @see [[BufferFill]]
  * @see [[BufferZero]]
  * @see [[BufferGen]]
  */
final case class BufferSet(id: Int, pairs: FillValue*)
  extends Message("/b_set", id +: pairs.flatMap(_.toList): _*)
  with SyncCmd {

  def copy(id: Int = id, pairs: Seq[FillValue] = pairs): BufferSet =
    BufferSet(id = id, pairs = pairs: _*)
}

/** The `/b_setn` message sets individual ranges of samples of the buffer to given values.
  *
  * @param  id                the identifier of the buffer whose contents to write.
  * @param  indicesAndValues  pairs of sample offsets and sequences of sample values.
  *                           The offsets are de-interleaved samples,
  *                           so for multi-channel buffers, to address a particular frame, the frame index must
  *                           be multiplied by the number of channels and offset by the channel to write into.
  *
  * @see [[Buffer#setnMsg]]
  * @see [[BufferSet]]
  * @see [[BufferFill]]
  * @see [[BufferZero]]
  * @see [[BufferGen]]
  */
final case class BufferSetn(id: Int, indicesAndValues: (Int, SIndexedSeq[Float])*)
  extends Message("/b_setn", id +: indicesAndValues.flatMap(iv => iv._1 +: iv._2.size +: iv._2): _*)
  with SyncCmd {

  def copy(id: Int = id, indicesAndValues: Seq[(Int, SIndexedSeq[Float])] = indicesAndValues): BufferSetn =
    BufferSetn(id = id, indicesAndValues = indicesAndValues: _*)
}

/** The `/b_fill` message sets individual ranges of samples of the buffer to given values.
  *
  * @param  id      the identifier of the buffer whose contents to write.
  * @param  ranges  tuples which specify the offset into the buffer, the number of samples to overwrite and the
  *                 value with which to overwrite.
  *
  * @see [[Buffer#fillMsg]]
  * @see [[BufferSet]]
  * @see [[BufferSetn]]
  * @see [[BufferZero]]
  * @see [[BufferGen]]
  */
final case class BufferFill(id: Int, ranges: FillRange*)
  extends Message("/b_fill", id +: (ranges.flatMap(_.toList)): _*)
  with SyncCmd {

  def copy(id: Int = id, ranges: Seq[FillRange] = ranges): BufferFill =
    BufferFill(id = id, ranges = ranges: _*)
}

object BufferGen {
  trait Command {
    def name: String
    def args: Seq[Any]
  }

  sealed trait WaveFill extends Command {
    def normalize : Boolean
    def wavetable : Boolean
    def clear     : Boolean

    protected final def flags: Int = (if (normalize) 1 else 0) | (if (wavetable) 2 else 0) | (if (clear) 4 else 0)
  }

  /** OSC message for filling a buffer with a series of sine wave harmonics using specified amplitudes.
    *
    * @param partials   amplitudes for the harmonics. The first value specifies the amplitude of the first
    *                   partial, the second float value specifies the amplitude of the second partial, and so on.
    * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
    * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
    *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
    * @param clear      if set, the previous content is erased, otherwise the new waveform is added
    *                   to the existing content
    */
  final case class Sine1(partials: Seq[Float], normalize: Boolean, wavetable: Boolean, clear: Boolean)
    extends WaveFill {

    override def productPrefix = s"BufferGen$$Sine1"

    def name = "sine1"
    def args: Seq[Any] = flags +: partials
  }

  /** OSC message for filling a buffer with a series of sine waves using specified frequencies and amplitudes.
    *
    * @param partials   pairs of frequencies and amplitudes for the partials.
    *                   Frequencies are given as in cycles per buffer.
    * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
    * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
    *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
    * @param clear      if set, the previous content is erased, otherwise the new waveform is added
    *                   to the existing content
    */
  final case class Sine2(partials: Seq[(Float, Float)], normalize: Boolean, wavetable: Boolean, clear: Boolean)
    extends WaveFill {

      override def productPrefix = s"BufferGen$$Sine2"

      def name = "sine2"
      def args: Seq[Any] = flags +: partials.flatMap(tup => tup._1 :: tup._2 :: Nil)
  }

  object Sine3 {
    final case class Data(freq: Float, amp: Float, phase: Float)
    implicit def data(tup: (Float,  Float, Float)): Data = Data(tup._1, tup._2, tup._3)
  }

  /** OSC message for filling a buffer with a series of sine waves using specified frequencies, amplitudes,
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
  final case class Sine3(partials: Seq[(Float, Float, Float)], normalize: Boolean, wavetable: Boolean, clear: Boolean)
    extends WaveFill {

    override def productPrefix = s"BufferGen$$Sine3"

    def name = "sine3"
    def args: Seq[Any] = flags +: partials.flatMap(d => d._1 :: d._2 :: d._3 :: Nil)
  }

  /** OSC message for filling a buffer with a series of Chebyshev polynomials.
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
  final case class Cheby(amps: Seq[Float], normalize: Boolean, wavetable: Boolean, clear: Boolean)
    extends WaveFill {

    override def productPrefix = s"BufferGen$$Cheby"

    def name = "cheby"
    def args: Seq[Any] = flags +: amps
  }

  /** Copies samples from the source buffer to the destination buffer specified in the `b_gen` message.
    * If the number of samples to copy is negative, the maximum number of samples possible is copied.
    */
  final case class Copy(targetOffset: Int, source: Int, sourceOffset: Int, num: Int)
    extends Command {

    override def productPrefix = s"BufferGen$$Copy"

    def name = "copy"
    def args: Seq[Any] = List(targetOffset, source, sourceOffset, num)

    def isSynchronous = false
  }
}
/** The `/b_gen` message uses a dedicated command to generate or manipulate the buffer content.
  *
  * @param  id      the identifier of the buffer whose contents to write.
  * @param  command the operation to carry out on the buffer, such as generating a waveform or copying the content
  *
  * @see [[Buffer#genMsg]]
  * @see [[BufferSet]]
  * @see [[BufferSetn]]
  * @see [[BufferZero]]
  * @see [[BufferFill]]
  */
final case class BufferGen(id: Int, command: BufferGen.Command)
  extends Message("/b_gen", id +: command.name +: command.args: _*)
  with AsyncSend {

  // def isSynchronous: Boolean = command.isSynchronous
}

/** The `/b_get` message.
  *
  * @see [[Buffer#getMsg]]
  * @see [[BufferGetn]]
  * @see [[BufferSet]]
  */
final case class BufferGet(id: Int, index: Int*) // `indices` is taken by SeqLike
  extends Message("/b_get", id +: index: _*)
  with SyncQuery {

  def copy(id: Int = id, index: Seq[Int] = index): BufferGet =
    BufferGet(id = id, index = index: _*)
}

/** The `/b_getn` message.
  *
  * @see [[Buffer#getnMsg]]
  * @see [[BufferGet]]
  * @see [[BufferSetn]]
  */
final case class BufferGetn(id: Int, ranges: Range*)
  extends Message("/b_getn", id +: ranges.flatMap(_.toGetnSeq): _*)
  with SyncQuery {

  def copy(id: Int = id, ranges: Seq[Range] = ranges): BufferGetn =
    BufferGetn(id = id, ranges = ranges: _*)
}

/** The `/c_set` message.
  *
  * @see [[ControlBus#setMsg]]
  * @see [[ControlBusSetn]]
  * @see [[ControlBusGet]]
  * @see [[ControlBusFill]]
  */
final case class ControlBusSet(pairs: FillValue*)
  extends Message("/c_set", pairs.flatMap(_.toList): _*)
  with SyncCmd

/** The `/c_setn` message.
  *
  * @see [[ControlBus#setnMsg]]
  * @see [[ControlBusSet]]
  * @see [[ControlBusGetn]]
  * @see [[ControlBusFill]]
  */
final case class ControlBusSetn(indicesAndValues: (Int, SIndexedSeq[Float])*)
  extends Message("/c_setn", indicesAndValues.flatMap(iv => iv._1 +: iv._2.size +: iv._2): _*)
  with SyncCmd

/** The `/c_get` message.
  *
  * @see [[ControlBus#getMsg]]
  * @see [[ControlBusGetn]]
  * @see [[ControlBusSet]]
  */
final case class ControlBusGet(index: Int*) // `indices` is taken by SeqLike
  extends Message("/c_get", index: _*)
  with SyncQuery

/** The `/c_getn` message.
  *
  * @see [[ControlBus#getnMsg]]
  * @see [[ControlBusGet]]
  * @see [[ControlBusSetn]]
  */
final case class ControlBusGetn(ranges: Range*)
  extends Message("/c_getn", ranges.flatMap(_.toGetnSeq): _*)
  with SyncQuery

/** The `/c_fill` message.
  *
  * @see [[ControlBus#fillMsg]]
  * @see [[ControlBusSet]]
  * @see [[ControlBusSetn]]
  */
final case class ControlBusFill(ranges: FillRange*)
  extends Message("/c_fill", ranges.flatMap(_.toList): _*)
  with SyncCmd

object GroupNew {
  final case class Data(groupId: Int, addAction: Int, targetId: Int) {
    def toList: List[Int] = groupId :: addAction :: targetId :: Nil
  }
}
/** The `/g_new` message. */
final case class GroupNew(groups: GroupNew.Data*)
  extends Message("/g_new", groups.flatMap(_.toList): _*)
  with SyncCmd

/** The `/g_dumpTree` message. */
final case class GroupDumpTree(groups: (Int, Boolean)*)
  extends Message("/g_dumpTree", groups.flatMap(g => (g._1: Any) :: (g._2: Any) :: Nil): _*)
  with SyncCmd

/** The `/g_queryTree` message. */
final case class GroupQueryTree(groups: (Int, Boolean)*)
  extends Message("/g_queryTree", groups.flatMap(g => (g._1: Any) :: (g._2: Any) :: Nil): _*)
  with SyncQuery

/** The `/g_head` message pair-wise places nodes at the head of groups.
  * {{{
  * /g_head
  *   [
  *     Int - the id of the group at which head a node is to be placed (B)
  *     int - the id of the node to place (A)
  *   ] * N
  * }}}
  * So that for each pair, node A is moved to the head of group B.
  */
final case class GroupHead(groups: (Int, Int)*)
  extends Message("/g_head", groups.flatMap(g => g._1 :: g._2 :: Nil): _*)
  with SyncCmd

/** The `/g_tail` message pair-wise places nodes at the tail of groups.
  * {{{
  * /g_tail
  *   [
  *     Int - the id of the group at which tail a node is to be placed (B)
  *     int - the id of the node to place (A)
  *   ] * N
  * }}}
  * So that for each pair, node A is moved to the tail of group B.
  */
final case class GroupTail(groups: (Int, Int)*)
  extends Message("/g_tail", groups.flatMap(g => g._1 :: g._2 :: Nil): _*)
  with SyncCmd

/** The `/g_freeAll` message. */
final case class GroupFreeAll(ids: Int*)
  extends Message("/g_freeAll", ids: _*)
  with SyncCmd

/** The `/g_deepFree` message. */
final case class GroupDeepFree(ids: Int*)
  extends Message("/g_deepFree", ids: _*)
  with SyncCmd

/** The `/p_new` message. */
final case class ParGroupNew(groups: GroupNew.Data*)
  extends Message("/p_new", groups.flatMap(_.toList): _*)
  with SyncCmd

/** The `/s_new` message. */
final case class SynthNew(defName: String, id: Int, addAction: Int, targetId: Int, controls: ControlSet*)
  extends Message("/s_new",
    defName +: id +: addAction +: targetId +: controls.flatMap(_.toSetSeq): _*)
  with SyncCmd {

  def copy(defName: String = defName, id: Int = id, addAction: Int = addAction,
           targetId: Int = targetId, controls: Seq[ControlSet] = controls): SynthNew =
    SynthNew(defName = defName, id = id, addAction = addAction, targetId = targetId, controls = controls: _*)
}

/** The `/s_get` message. */
final case class SynthGet(id: Int, controls: Any*)
  extends Message("/s_get", id +: controls: _*)
  with SyncQuery {

  def copy(id: Int = id, controls: Seq[Any] = controls): SynthGet =
    SynthGet(id = id, controls = controls: _*)
}

/** The `/s_getn` message. */
final case class SynthGetn(id: Int, controls: (Any, Int)*)
  extends Message("/s_getn", id +: controls.flatMap(tup => tup._1 :: tup._2 :: Nil): _*)
  with SyncQuery {

  def copy(id: Int = id, controls: Seq[(Any, Int)] = controls): SynthGetn =
    SynthGetn(id = id, controls = controls: _*)
}

/** The `/n_run` message. */
final case class NodeRun(nodes: (Int, Boolean)*)
  extends Message("/n_run", nodes.flatMap(n => (n._1: Any) :: (n._2: Any) :: Nil): _*)
  with SyncCmd

/** The `/n_set` message. */
final case class NodeSet(id: Int, pairs: ControlSet*)
  extends Message("/n_set", id +: pairs.flatMap(_.toSetSeq): _*)
  with SyncCmd {

  def copy(id: Int = id, pairs: Seq[ControlSet] = pairs): NodeSet =
    NodeSet(id = id, pairs = pairs: _*)
}

/** The `/n_setn` message. */
final case class NodeSetn(id: Int, pairs: ControlSet*)
  extends Message("/n_setn", id +: pairs.flatMap(_.toSetnSeq): _*)
  with SyncCmd {

  def copy(id: Int = id, pairs: Seq[ControlSet] = pairs): NodeSetn =
    NodeSetn(id = id, pairs = pairs: _*)
}

/** The `/n_trace` message. */
final case class NodeTrace(ids: Int*)
  extends Message("/n_trace", ids: _*)
  with SyncCmd

/** The `/n_noid` message. */
final case class NodeNoId(ids: Int*)
  extends Message("/n_noid", ids: _*)
  with SyncCmd

/** The `/n_free` message. */
final case class NodeFree(ids: Int*)
  extends Message("/n_free", ids: _*)
  with SyncCmd

/** The `/n_map` message. */
final case class NodeMap(id: Int, mappings: ControlKBusMap.Single*)
  extends Message("/n_map", id +: mappings.flatMap(_.toMapSeq): _*)
  with SyncCmd {

  def copy(id: Int = id, mappings: Seq[ControlKBusMap.Single] = mappings): NodeMap =
    NodeMap(id = id, mappings = mappings: _*)
}

/** The `/n_mapn` message. */
final case class NodeMapn(id: Int, mappings: ControlKBusMap*)
  extends Message("/n_mapn", id +: mappings.flatMap(_.toMapnSeq): _*)
  with SyncCmd {

  def copy(id: Int = id, mappings: Seq[ControlKBusMap] = mappings): NodeMapn =
    NodeMapn(id = id, mappings = mappings: _*)
}

/** The `/n_mapa` message. */
final case class NodeMapa(id: Int, mappings: ControlABusMap.Single*)
  extends Message("/n_mapa", id +: mappings.flatMap(_.toMapaSeq): _*)
  with SyncCmd {

  def copy(id: Int = id, mappings: Seq[ControlABusMap.Single] = mappings): NodeMapa =
    NodeMapa(id = id, mappings = mappings: _*)
}

/** The `/n_mapan` message. */
final case class NodeMapan(id: Int, mappings: ControlABusMap*)
  extends Message("/n_mapan", id +: mappings.flatMap(_.toMapanSeq): _*)
  with SyncCmd {

  def copy(id: Int = id, mappings: Seq[ControlABusMap] = mappings): NodeMapan =
    NodeMapan(id = id, mappings = mappings: _*)
}

/** The `/n_fill` message. */
final case class NodeFill(id: Int, data: ControlFillRange*)
  extends Message("/n_fill", id +: data.flatMap(_.toList): _*)
  with SyncCmd {

  def copy(id: Int = id, data: Seq[ControlFillRange] = data): NodeFill =
    NodeFill(id = id, data = data: _*)
}

/** The `/n_before` message pair-wise places nodes before other nodes.
  * {{{
  * /n_before
  *   [
  *     Int - the id of the node to place (A)
  *     int - the id of the node before which the above is placed (B)
  *   ] * N
  * }}}
  * So that for each pair, node A in the same group as node B, to execute immediately before node B.
  */
final case class NodeBefore(groups: (Int, Int)*)
  extends Message("/n_before", groups.flatMap(g => g._1 :: g._2 :: Nil): _*)
  with SyncCmd

/** The `/n_after` message pair-wise places nodes after other nodes.
  * {{{
  * /n_after
  *   [
  *     Int - the id of the node to place (A)
  *     int - the id of the node after which the above is placed (B)
  *   ] * N
  * }}}
  * So that for each pair, node A in the same group as node B, to execute immediately after node B.
  */
final case class NodeAfter(groups: (Int, Int)*)
  extends Message("/n_after", groups.flatMap(g => g._1 :: g._2 :: Nil): _*)
  with SyncCmd

/** The `/n_query` message. */
final case class NodeQuery(ids: Int*) extends Message("/n_query", ids: _*) with SyncQuery

/** The `/n_order` message. */
final case class NodeOrder(addAction: Int, targetId: Int, ids: Int*)
  extends Message("/n_order", addAction +: targetId +: ids: _*)
  with SyncCmd {

  def copy(addAction: Int = addAction, targetId: Int = targetId, ids: Seq[Int] = ids): NodeOrder =
    NodeOrder(addAction = addAction, targetId = targetId, ids = ids: _*)
}

/** The `/d_recv` message. */
final case class SynthDefRecv(bytes: ByteBuffer, completion: Option[Packet])
  extends Message("/d_recv", bytes :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): SynthDefRecv = copy(completion = completion)
}

/** The `/d_free` message. */
final case class SynthDefFree(names: String*)
  extends Message("/d_free", names: _*)
  with SyncCmd

/** The `/d_load` message.
  *
  * @param path   the path to the file that stores the definition. This can be a pattern
  *               like `"synthdefs/perc-*"`
  *
  * @see [[SynthDef.loadMsg]]
  */
final case class SynthDefLoad(path: String, completion: Option[Packet])
  extends Message("/d_load", path :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): SynthDefLoad = copy(completion = completion)
}

/** The `/d_loadDir` message tells the server to load all synth definitions within a directory.
  *
  * @see [[SynthDef.loadDirMsg]]
  */
final case class SynthDefLoadDir(path: String, completion: Option[Packet])
  extends Message("/d_loadDir", path :: completion.toList: _*)
  with HasCompletion {

  def updateCompletion(completion: Option[Packet]): SynthDefLoadDir = copy(completion = completion)
}

/** The `/u_cmd` message allows one to send UGen specific commands. */
final case class UGenCommand(nodeId: Int, ugenIdx: Int, command: String, rest: Any*)
  extends Message("/u_cmd", nodeId +: ugenIdx +: command +: rest)
  with SyncCmd {

  def copy(nodeId: Int = nodeId, ugenIdx: Int = ugenIdx, command: String = command, rest: Seq[Any] = rest): UGenCommand =
    UGenCommand(nodeId = nodeId, ugenIdx = ugenIdx, command = command, rest = rest: _*)
}

/** The `/tr` message send from a [[de.sciss.synth.ugen.SendTrig SendTrig]] UGen. */
final case class Trigger(nodeId: Int, trig: Int, value: Float)
  extends Message("/tr", nodeId, trig, value) with Receive