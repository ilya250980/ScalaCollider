/*
 *  Ops.scala
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

import de.sciss.optional.Optional
import de.sciss.osc
import de.sciss.osc.Packet
import de.sciss.synth.message.BufferGen
import de.sciss.synth.ugen.Env

import scala.collection.immutable.{IndexedSeq => Vec}
import scala.collection.{IndexedSeq => SIndexedSeq}
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

/** Importing the contents of this object adds imperative (side-effect) functions to resources such as
  * synths, buses, buffers. In general these reflect the OSC messages defined for each object, and send
  * them straight to the server. For example, a `Synth` has function `newMsg` which returns an OSC message
  * to instantiate the synth of the server. After importing `Ops`, you will be able to directly launch
  * a synth using `SynthDef.play` or `Synth.play`. You will be able to directly allocate and read buffers,
  * and so forth.
  *
  * The reason why these functions are separated from the rest of the API is to allow other frameworks
  * such as SoundProcesses to avoid side-effects which they handle differently (e.g., using STM).
  */
object Ops {
  /** Wraps the body of the thunk argument in a `SynthGraph`, adds an output UGen, and plays the graph
    * on the default group of the default server.
    *
    * @param  thunk   the thunk which produces the UGens to play
    * @return         a reference to the spawned Synth
    */
  def play[A: GraphFunction.Result](thunk: => A): Synth = playWith()(thunk)

  /** Constructs a `GraphFunction`, on which then for example `play` can be called. */
  def graph[A](thunk: => A)(implicit result: GraphFunction.Result[A]): GraphFunction[A] =
    new GraphFunction(() => thunk)(result)

  /** Wraps the body of the thunk argument in a `SynthGraph`, adds an output UGen, and plays the graph
    * in a synth attached to a given target.
    *
    * @param  target      the target with respect to which to place the synth
    * @param  addAction   the relation between the new synth and the target
    * @param  out         audio bus index which is used for the synthetically generated `Out` UGen.
    * @param  fadeTime    if `&gt;= 0`, specifies the fade-in time for a synthetically added amplitude envelope.
    *                     if negative, avoids building an envelope.
    * @param  thunk       the thunk which produces the UGens to play
    * @return             a reference to the spawned Synth
    */
  def playWith[A](target: Node = Server.default, out: Int = 0,
                  fadeTime: Double = 0.02,
                  addAction: AddAction = addToHead)(thunk: => A)
                 (implicit result: GraphFunction.Result[A]): Synth = {
    val fun = new GraphFunction(() => thunk)(result)
    fun.play(target = target, outBus = out, fadeTime = fadeTime, addAction = addAction)
  }

  /** Allows the construction or named controls, for example via `"freq".kr`. */
  implicit def stringToControl(name: String): ugen.ControlProxyFactory =
    new ugen.ControlProxyFactory(name)

  /** This allows conversions to Group so that something like Server.default.freeAll becomes possible. */
  implicit def groupOps[G](g: G)(implicit view: G => Group): GroupOps = new GroupOps(g)

  //   implicit def bufferOps( b: Buffer ) : BufferOps = new BufferOps( b )
  //   implicit def controlBusOps( b: ControlBus ) : ControlBusOps = new ControlBusOps( b )

  final implicit class SynthDefConstructors(private val `this`: SynthDef.type) extends AnyVal {
    import SynthDef.{Completion => _, _}

    def recv(name: String, server: Server = Server.default, completion: SynthDef.Completion = Completion.None)
            (thunk: => Unit): SynthDef = {
      val d = apply(name)(thunk)
      d.recv(server, completion)
      d
    }

    def load(path: String, server: Server = Server.default, completion: Completion[Unit] = Completion.None): Unit =
      sendWithAction((), server, SynthDef.loadMsg(path, _), completion, "SynthDef.load")

    def loadDir(path: String, server: Server = Server.default, completion: Completion[Unit] = Completion.None): Unit =
      sendWithAction((), server, SynthDef.loadDirMsg(path, _), completion, "SynthDef.loadDir")
  }

  // cannot occur inside value class at the moment
  private[this] def sendWithAction[A](res: A, server: Server, msgFun: Option[Packet] => osc.Message,
                                           completion: Completion[A], name: String): Unit =
    completion.action.fold {
      server ! msgFun(completion.message.map(_.apply(res)))
    } { action =>
      val syncMsg = server.syncMsg()
      val syncId  = syncMsg.id
      val compPacket: Packet = completion.message match {
        case Some(msgFun2) => osc.Bundle.now(msgFun2(res), syncMsg)
        case None => syncMsg
      }
      val p   = msgFun(Some(compPacket))
      val fut = server.!!(p) {
        case message.Synced(`syncId`) => action(res)
        //        case message.TIMEOUT => println("ERROR: " + d + "." + name + " : timeout!")
      }
      val cfg = server.clientConfig
      import cfg.executionContext
      fut.onComplete {
        case Failure(message.Timeout()) => println(s"ERROR: $name : timeout!")
        case _ =>
      }
    }

  final implicit class SynthDefOps(private val d: SynthDef) extends AnyVal {
    import SynthDef.defaultDir
    import d._

    def recv(server: Server = Server.default, completion: SynthDef.Completion = Completion.None): Unit =
      sendWithAction(d, server, recvMsg(_), completion, "SynthDef.recv")

    def load(server: Server = Server.default, dir: String = defaultDir,
             completion: SynthDef.Completion = Completion.None): Unit = {
      write(dir)
      sendWithAction(d, server, loadMsg(dir, _), completion, "SynthDef.load")
    }

    def play(target: Node = Server.default, args: Seq[ControlSet] = Nil, addAction: AddAction = addToHead): Synth = {
      val synth   = Synth(target.server)
      val newMsg  = synth.newMsg(name, target, args, addAction)
      target.server ! recvMsg(newMsg)
      synth
    }

    def free(server: Server = Server.default): Unit = server ! freeMsg
  }

  final implicit class SynthOps(private val n: Synth) extends AnyVal {
    import n._

    def play(defName: String, target: Node = server.defaultGroup, args: Seq[ControlSet] = Nil,
               addAction: AddAction = addToHead): Unit = {
      server ! newMsg(defName = defName, target = target, args = args, addAction = addAction)
    }
  }

  final implicit class NodeOps(private val n: Node) extends AnyVal {
    import n._

    def free(): Unit = server ! freeMsg

    /** Pauses or resumes the node.
      *
      * @param flag if `true` the node is resumed, if `false` it is paused.
      */
    def run(flag: Boolean = true): Unit = server ! runMsg(flag)

    def set(pairs: ControlSet*): Unit = server ! setMsg(pairs: _*)

    def setn(pairs: ControlSet*): Unit = server ! setnMsg(pairs: _*)

    def trace(): Unit = server ! traceMsg

    def release(releaseTime: Double = -1.0): Unit = server ! releaseMsg(releaseTime)

    def map(pairs: ControlKBusMap.Single*): Unit = server ! mapMsg(pairs: _*)

    def mapn(mappings: ControlKBusMap*): Unit = server ! mapnMsg(mappings: _*)

    /** Creates a mapping from a mono-channel audio bus to one of the node's controls.
      *
      * Note that a mapped control acts similar to an `InFeedback` UGen in that it does not matter
      * whether the audio bus was written before the execution of the synth whose control is mapped or not.
      * If it was written before, no delay is introduced, otherwise a delay of one control block is introduced.
      *
      * @see  [[de.sciss.synth.ugen.InFeedback]]
      */
    def mapa(pairs: ControlABusMap.Single*): Unit = server ! mapaMsg(pairs: _*)

    /** Creates a mapping from a mono- or multi-channel audio bus to one of the node's controls.
      *
      * Note that a mapped control acts similar to an `InFeedback` UGen in that it does not matter
      * whether the audio bus was written before the execution of the synth whose control is mapped or not.
      * If it was written before, no delay is introduced, otherwise a delay of one control block is introduced.
      *
      * @see  [[de.sciss.synth.ugen.InFeedback]]
      */
    def mapan(mappings: ControlABusMap*): Unit = server ! mapanMsg(mappings: _*)

    // def fill(control: Any, numChannels: Int, value: Float): Unit = server ! fillMsg(control, numChannels, value)

    def fill(data: ControlFillRange*): Unit = server ! fillMsg(data: _*)

    /** Moves this node before another node
      *
      * @param   node  the node before which to move this node
      *
      * @see  [[de.sciss.synth.message.NodeBefore]]
      */
    def moveBefore(node: Node): Unit = server ! moveBeforeMsg(node)

    /** Moves this node after another node
      *
      * @param   node  the node after which to move this node
      *
      * @see  [[de.sciss.synth.message.NodeAfter]]
      */
    def moveAfter (node : Node ): Unit = server ! moveAfterMsg (node )

    /** Moves this node to the head of a given group
      *
      * @param   group  the target group
      *
      * @see  [[de.sciss.synth.message.GroupHead]]
      */
    def moveToHead(group: Group): Unit = server ! moveToHeadMsg(group)

    /** Moves this node to the tail of a given group
      *
      * @param   group  the target group
      *
      * @see  [[de.sciss.synth.message.GroupTail]]
      */
    def moveToTail(group: Group): Unit = server ! moveToTailMsg(group)
  }

  implicit final class GroupConstructors(private val `this`: Group.type) extends AnyVal {
    import Group._

    def play(): Group = head(Server.default.defaultGroup)

    def play(target: Node = Server.default.defaultGroup, addAction: AddAction = addToHead): Group = {
      val group = apply(target.server)
      group.server ! group.newMsg(target, addAction)
      group
    }

    def after  (target: Node ): Group = play(target, addAfter  )
    def before (target: Node ): Group = play(target, addBefore )
    def head   (target: Group): Group = play(target, addToHead )
    def tail   (target: Group): Group = play(target, addToTail )
    def replace(target: Node ): Group = play(target, addReplace)
  }

  final class GroupOps(private val g: Group) extends AnyVal {
    import g._

    def freeAll (): Unit = server ! freeAllMsg
    def deepFree(): Unit = server ! deepFreeMsg

    def dumpTree (postControls: Boolean = false): Unit = server ! dumpTreeMsg (postControls)
    def queryTree(postControls: Boolean = false): Unit = server ! queryTreeMsg(postControls)
  }

  implicit final class SynthConstructors(private val `this`: Synth.type) extends AnyVal {
    import Synth._

    def play(defName: String, args: Seq[ControlSet] = Nil, target: Node = Server.default.defaultGroup,
             addAction: AddAction = addToHead): Synth = {
      val synth = apply(target.server)
      synth.server ! synth.newMsg(defName, target, args, addAction)
      synth
    }

    def after(target: Node, defName: String, args: Seq[ControlSet] = Nil): Synth =
      play(defName, args, target, addAfter)

    def before(target: Node, defName: String, args: Seq[ControlSet] = Nil): Synth =
      play(defName, args, target, addBefore)

    def head(target: Group, defName: String, args: Seq[ControlSet] = Nil): Synth =
      play(defName, args, target, addToHead)

    def tail(target: Group, defName: String, args: Seq[ControlSet] = Nil): Synth =
      play(defName, args, target, addToTail)

    def replace(target: Node, defName: String, args: Seq[ControlSet] = Nil): Synth =
      play(defName, args, target, addReplace)
  }

  implicit final class BufferConstructors(private val `this`: Buffer.type) extends AnyVal {
    import Buffer._

    private def createAsync(server: Server, allocFun: Buffer => Optional[Packet] => Future[Unit],
                            completion: Buffer.Completion): Buffer = {
      import server.clientConfig.executionContext
      val b   = apply(server)
      val fut = allocFun(b)(completion.mapMessage(b))
      completion.action.foreach { action =>
        fut.foreach(_ => action(b))
      }
      b
    }

    def alloc(server: Server = Server.default, numFrames: Int, numChannels: Int = 1,
              completion: Buffer.Completion = Completion.None): Buffer =
      createAsync(server, b => b.alloc(numFrames, numChannels, _), completion)

    def read(server: Server = Server.default, path: String, startFrame: Int = 0, numFrames: Int = -1,
             completion: Buffer.Completion = Completion.None): Buffer =
      createAsync(server, b => b.allocRead(path, startFrame, numFrames, _), completion)

    def cue(server: Server = Server.default, path: String, startFrame: Int = 0, numChannels: Int = 1,
            bufFrames: Int = 32768, completion: Buffer.Completion = Completion.None): Buffer =
      createAsync(server, b => c => b.alloc(bufFrames, numChannels, b.cueMsg(path, startFrame, c)), completion)

    def readChannel(server: Server = Server.default, path: String, startFrame: Int = 0, numFrames: Int = -1,
                    channels: Seq[Int], completion: Buffer.Completion = Completion.None): Buffer =
      createAsync(server, b => b.allocReadChannel(path, startFrame, numFrames, channels, _), completion)
  }

  // ---- the following have to be outside the value class due to a Scala 2.10 ----
  // ---- restriction that seems to extend to function arguments. After we     ----
  // ---- drop Scala 2.10 support, we can move these back into the classes     ----

  private def buf_sendAsync(b: Buffer)(msgFun: Packet => osc.Message, completion: Optional[Packet]): Future[Unit] = {
    import b._
    register()
    val p = Promise[Unit]()
    lazy val l: Buffer.Listener = {
      case BufferManager.BufferInfo(_, _) =>
        removeListener(l)
        p.complete(Success(()))
    }
    addListener(l)

    val c1 = completion.fold[Packet](queryMsg)(p => osc.Bundle.now(p, queryMsg))
    server ! msgFun(c1)
    p.future
  }

  private def buf_get(b: Buffer)(indices: Int*): Future[Vec[Float]] = {
    import b._
    val msg = getMsg(indices: _*)
    server.!!(msg) {
      case m: message.BufferSet if m.id == id && sameIndices(m.pairs, indices) =>
        val vec = m.pairs match {
          case pv: Vec[FillValue] => pv.map(_.value)
          case p => p.iterator.map(_.value).toIndexedSeq
        }
        vec
    }
  }

  private def buf_sendSyncBundle(b: Buffer)(m: osc.Message): Future[Unit] = {
    import b._
    val sync  = server.syncMsg()
    val reply = sync.reply
    val p     = osc.Bundle.now(m, sync)
    server.!!(p) { case `reply` => }
  }

  private def buf_getn(b: Buffer)(pairs: Range*): Future[Vec[Float]] = {
    import b._
    val rangeSeq = pairs.flatMap(_.toGetnSeq)  // XXX TODO: this is called again in `getnMsg`
    server.!!(getnMsg(pairs: _*)) {
      case m: message.BufferSetn if m.id == id && sameIndicesAndSizes(m.indicesAndValues, rangeSeq) =>
        val vec = m.indicesAndValues match {
          case ivv: Vec[(Int, SIndexedSeq[Float])] => ivv.flatMap(_._2)
          case iv => iv.iterator.flatMap(_._2).toIndexedSeq
        }
        vec

      //      case message.BufferSetn(`id`, sq @ _*) if {
      //        println("Missed it.")
      //        val found     = sq.map(tup => (tup._1, tup._2.size))
      //        println(s"Found   : $found")
      //        println(s"Expected: $rangeSeq")
      //        false
      //      } => ...
    }
  }

  implicit final class BufferOps(private val b: Buffer) extends AnyVal {
    import b._

    //    def alloc(numFrames: Int, numChannels: Int = 1, completion: Buffer.Completion = Completion.None): Unit =
    //      server ! allocMsg(numFrames, numChannels, makePacket(completion))

    @inline private def sendAsync(msgFun: Packet => osc.Message, completion: Optional[Packet]): Future[Unit] =
      buf_sendAsync(b)(msgFun, completion)

    def alloc(numFrames: Int, numChannels: Int = 1, completion: Optional[Packet] = None): Future[Unit] =
      sendAsync(allocMsg(numFrames, numChannels, _), completion)

    def free (completion: Optional[Packet] = None): Unit = server ! freeMsg (completion, release = true)
    def close(completion: Optional[Packet] = None): Unit = server ! closeMsg(completion)

    def allocRead(path: String, startFrame: Int = 0, numFrames: Int = -1,
                  completion: Optional[Packet] = None): Future[Unit] =
      sendAsync(allocReadMsg(path, startFrame, numFrames, _), completion)

    def allocReadChannel(path: String, startFrame: Int = 0, numFrames: Int = -1, channels: Seq[Int],
                         completion: Optional[Packet] = None): Future[Unit] =
      sendAsync(allocReadChannelMsg(path, startFrame, numFrames, channels, _), completion)

    def read(path: String, fileStartFrame: Int = 0, numFrames: Int = -1, bufStartFrame: Int = 0,
             leaveOpen: Boolean = false, completion: Optional[Packet] = None): Future[Unit] =
      sendAsync(readMsg(path, fileStartFrame, numFrames, bufStartFrame, leaveOpen, _), completion)

    def readChannel(path: String, fileStartFrame: Int = 0, numFrames: Int = -1, bufStartFrame: Int = 0,
                    leaveOpen: Boolean = false, channels: Seq[Int],
                    completion: Optional[Packet] = None): Future[Unit] =
      sendAsync(readChannelMsg(path, fileStartFrame, numFrames, bufStartFrame, leaveOpen, channels, _), completion)

    /** Sets the contents of the buffer by replacing
      * individual sample values. An error is thrown if any of the given
      * offsets is out of range.
      *
      * @param   pairs a list of modifications to the buffer contents, each element
      *                being a sample offset and the sample value. The sample offset ranges
      *                from zero to the number of samples in the buffer (exclusive), i.e.
      *                `numChannels * numFrames`. For instance, in a stereo-buffer, the offset
      *                for the right channel's fifth frame is `(5-1) * 2 + 1 = 9`.
      */
    def set(pairs: FillValue*): Unit = server ! setMsg(pairs: _*)

    /** Sets the entire contents of the buffer.
      * An error is thrown if the number of given values does not match the number
      * of samples in the buffer.
      *
      * @param   v  the new content of the buffer. the size of the sequence must be
      *             exactly the number of samples in the buffer, i.e.
      *             `numChannels * numFrames`. Values are channel-interleaved, that is
      *             for a stereo-buffer the first element specifies the value of the
      *             first frame of the left channel, the second element specifies the value
      *             of the first frame of the right channel, followed by the second frame
      *             of the left channel, etc.
      */
    def setn(v: SIndexedSeq[Float]): Unit = server ! setnMsg(v)

    /** Sets the contents of the buffer by replacing
      * individual contiguous chunks of data. An error is thrown if any of the given
      * ranges lies outside the valid range of the entire buffer.
      *
      * @param   pairs a list of modifications to the buffer contents, each element
      *                being a sample offset and a chunk of values. The data is channel-interleaved,
      *                e.g. for a stereo-buffer, the offset for the right channel's fifth frame
      *                is `(5-1) * 2 + 1 = 9`. Accordingly, values in the float-sequences are
      *                considered channel-interleaved, i.e. for a stereo buffer and an even offset,
      *                the first element of the sequence refers to frame `offset / 2` of the
      *                left channel, the second element to frame `offset / 2` of the right channel,
      *                followed by frame `offset / 2 + 1` of the left channel, and so on.
      */
    def setn(pairs: (Int, SIndexedSeq[Float])*): Unit = server ! setnMsg(pairs: _*)

    // def fill(index: Int, num: Int, value: Float): Unit = server ! fillMsg(index, num, value)

    def fill(value: Double): Unit = server ! fillMsg(value.toFloat)

    def fill(data: FillRange*): Unit = server ! fillMsg(data: _*)

    def zero(completion: Optional[Packet] = None): Future[Unit] = sendAsync(zeroMsg(_), completion)

    def write(path: String, fileType: io.AudioFileType = io.AudioFileType.AIFF,
              sampleFormat: io.SampleFormat = io.SampleFormat.Float, numFrames: Int = -1, startFrame: Int = 0,
              leaveOpen: Boolean = false, completion: Optional[Packet] = None): Future[Unit] =
      sendAsync(writeMsg(path, fileType, sampleFormat, numFrames, startFrame, leaveOpen, _), completion)

    def get(indices: Int*): Future[Vec[Float]] = buf_get(b)(indices: _*)

    /** Retrieves the entire buffer contents. This is similar to `getToFloatArray` in sclang.
      * If multiple packets must be sent due to the size, they will be scheduled strictly sequentially.
      * This is safe but potentially slow for large buffers.
      *
      * @param  offset  offset into the buffer in samples; for multi-channel buffers to indicate a specific
      *                 frame the frame index must be multiplied by the number of channels
      * @param  num     the number of samples to get; for multi-channel buffers to indicate a specific
      *                 number of frames, the number must be multiplied by the number of channels.
      *                 The special value `-1` means that all samples should be retrieved
      */
    def getData(offset: Int = 0, num: Int = -1): Future[Vec[Float]] = {
      val num1 = if (num >= 0) num else {
        if (numFrames < 0) sys.error(s"Buffer size not known: $b")
        numFrames * numChannels - offset
      }

      def loop(off: Int, rem: Int): Future[Vec[Float]] = {
        import server.clientConfig.executionContext
        val lim   = math.min(rem, 1633)
        val fut   = getn(off until (off + lim))
        val rem1  = rem - lim
        if (rem1 == 0) fut else fut.flatMap { pred =>
          // is this good? should we internally produce Future[Unit] and a Vector.newBuilder?
          // ...because we can easily end up with hundreds of chunks at this size
          loop(off + lim, rem1).map(pred ++ _)
        }
      }

      loop(offset, num1)
    }

    /** Transmits a collection to fill the entire buffer contents. This is similar to `sendCollection` in sclang,
      * If multiple packets must be sent due to the size, they will be scheduled strictly sequentially.
      * This is safe but potentially slow for large buffers.
      *
      * @param  values  the collection to copy into the buffer; values are assumed to be de-interleaved if
      *                 the buffer has multiple channels.
      * @param  offset  offset into the buffer in samples; for multi-channel buffers to indicate a specific
      *                 frame the frame index must be multiplied by the number of channels
      */
    def setData(values: IndexedSeq[Float], offset: Int = 0): Future[Unit] = {
      def loop(off: Int, rem: IndexedSeq[Float]): Future[Unit] = {
        import server.clientConfig.executionContext
        val remSz     = rem.size
        val lim       = math.min(remSz, 1600) // 1626 minus space for bundle and sync-message
        val (c1, c2)  = rem.splitAt(lim)
        val msg       = setnMsg(off -> c1)
        val fut       = sendSyncBundle(msg)
        val rem1      = remSz - lim
        if (rem1 == 0) fut else fut.flatMap { _ => loop(off + lim, c2) }
      }

      loop(offset, values)
    }

    /** Gets ranges of the buffer content and returns them as a future flattened collection. */
    def getn(pairs: Range*): Future[Vec[Float]] = buf_getn(b)(pairs: _*)

    def gen(command: BufferGen.Command): Future[Unit] = {
      val m = genMsg(command)
//      if (command.isSynchronous) {
//        server ! m
//        Future.successful(())
//      } else {
        sendSyncBundle(m)
//      }
    }

    /** Fills the buffer with a series of sine wave harmonics using specified amplitudes.
      *
      * @param partials   amplitudes for the harmonics. The first value specifies the amplitude of the first
      *                   partial, the second float value specifies the amplitude of the second partial, and so on.
      * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
      * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
      *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
      * @param clear      if set, the previous content is erased, otherwise the new waveform is added
      *                   to the existing content
      */
    def sine1(partials: Seq[Float], normalize: Boolean = true, wavetable: Boolean = true,
              clear: Boolean = true): Future[Unit] = sendSyncBundle(sine1Msg(
      partials = partials, normalize = normalize, wavetable = wavetable, clear = clear))

    /** Fills the buffer with a series of sine waves using specified frequencies and amplitudes.
      *
      * @param partials   pairs of frequencies and amplitudes for the partials.
      *                   Frequencies are given as in cycles per buffer.
      * @param normalize  if set, the peak amplitude of the generated waveform is normalized to `1.0`
      * @param wavetable  if set, the format of the waveform is chosen to be usable by interpolating
      *                   oscillators such as [[de.sciss.synth.ugen.Osc Osc]] or [[de.sciss.synth.ugen.VOsc VOsc]]
      * @param clear      if set, the previous content is erased, otherwise the new waveform is added
      *                   to the existing content
      */
    def sine2(partials: Seq[(Float, Float)], normalize: Boolean = true, wavetable: Boolean = true,
              clear: Boolean = true): Future[Unit] = sendSyncBundle(sine2Msg(
      partials = partials, normalize = normalize, wavetable = wavetable, clear = clear))

    /** Fills the buffer with a series of sine waves using specified frequencies, amplitudes,
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
    def sine3(partials: Seq[(Float, Float, Float)], normalize: Boolean = true, wavetable: Boolean = true,
              clear: Boolean = true): Future[Unit] = sendSyncBundle(sine3Msg(
      partials = partials, normalize = normalize, wavetable = wavetable, clear = clear))

    /** Fills the buffer with a series of Chebyshev polynomials.
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
    def cheby(amps: Seq[Float], normalize: Boolean = true, wavetable: Boolean = true,
              clear: Boolean = true): Future[Unit] = sendSyncBundle(chebyMsg(
      amps = amps, normalize = normalize, wavetable = wavetable, clear = clear))

    @inline private def sendSyncBundle(m: osc.Message): Future[Unit] = buf_sendSyncBundle(b)(m)

    // ---- utility methods ----
    //    def play: Synth = play()

    def play(loop: Boolean = false, amp: Double = 1.0, out: Int = 0): Synth = {
      import ugen._
      Ops.playWith(target = server, out = out) {
        // working around nasty compiler bug
        val ply = PlayBuf.ar(numChannels, id, BufRateScale.kr(id), loop = if (loop) 1 else 0)
        if (!loop) FreeSelfWhenDone.kr(ply)
        val ampCtl = "amp".kr(amp) // note: scalac 2.10.0 inference bug
        ply * ampCtl
      }
    }
  }

  // ---- the following have to be outside the value class due to a Scala 2.10 ----
  // ---- restriction that seems to extend to function arguments. After we     ----
  // ---- drop Scala 2.10 support, we can move these back into the classes     ----

  private def cbus_get1(b: ControlBus)(): Future[Float] = {
    import b._
    val msg = getMsg
    server.!!(msg) {
      case message.ControlBusSet(FillValue(`index`, value)) => value: Float
    }
  }

  private def sameIndices(a: Seq[FillValue], b: Seq[Int]): Boolean = {
    val ai = a.iterator
    val bi = b.iterator
    while (ai.hasNext && bi.hasNext) {
      val an = ai.next().index
      val bn = bi.next()
      if (an != bn) return false
    }
    ai.isEmpty && bi.isEmpty
  }

  private def sameIndicesAndSizes(a: Seq[(Int, SIndexedSeq[Float])], b: Seq[Int]): Boolean = {
    val ai = a.iterator
    val bi = b.iterator
    while (ai.hasNext && bi.hasNext) {
      val an  = ai.next()
      val bn0 = bi.next()
      val bn1 = bi.next()
      if (an._1 != bn0 || an._2.size != bn1) return false
    }
    ai.isEmpty && bi.isEmpty
  }

  private def cbus_get(b: ControlBus)(indices: Int*): Future[Vec[Float]] = {
    import b._
    val msg = getMsg(indices: _*)
    server.!!(msg) {
      case m: message.ControlBusSet if sameIndices(m.pairs, msg.index) =>
        val vec = m.pairs match {
          case pv: Vec[FillValue] => pv.map(_.value)
          case p => p.iterator.map(_.value).toIndexedSeq
        }
        vec
    }
  }

  private def cbus_getn(b: ControlBus)(pairs: Range*): Future[Vec[Float]] = {
    import b._
    val rangeSeq  = pairs.flatMap(_.toGetnSeq)  // XXX TODO: this is called again in `getnMsg`
    val msg       = getnMsg(pairs: _*)
    server.!!(msg) {
      case m: message.ControlBusSetn if sameIndicesAndSizes(m.indicesAndValues, rangeSeq) =>
        val vec = m.indicesAndValues match {
          case ivv: Vec[(Int, SIndexedSeq[Float])] => ivv.flatMap(_._2)
          case iv => iv.iterator.flatMap(_._2).toIndexedSeq
        }
        vec
    }
  }

  implicit final class ControlBusOps(private val b: ControlBus) extends AnyVal {
    import b._

    /** Convenience method that sets a single bus value.
      * The bus must have exactly one channel, otherwise an exception is thrown.
      */
    def set(value: Double): Unit = server ! setMsg(value.toFloat)

    def set(pairs: FillValue*): Unit = server ! setMsg(pairs: _*)

    def setData(values: SIndexedSeq[Float]): Unit = server ! setnMsg(values)

    def setn(pairs: (Int, SIndexedSeq[Float])*): Unit = server ! setnMsg(pairs: _*)

    /** Convenience method that gets a single bus value.
      * The bus must have exactly one channel, otherwise an exception is thrown.
      */
    def get(): Future[Float] = cbus_get1(b)()

    /** Gets multiple bus values specified as relative channel offsets. */
    def get(indices: Int*): Future[Vec[Float]] = cbus_get(b)(indices: _*)

    def getData(): Future[Vec[Float]] = getn(0 until numChannels)

    def getn(pairs: Range*): Future[Vec[Float]] = cbus_getn(b)(pairs: _*)

    def fill(value: Float): Unit = server ! b.fillMsg(value)

    def fill(data: FillRange*): Unit = server ! b.fillMsg(data: _*)
  }

  implicit final class EnvOps(private val env: Env) extends AnyVal {
    def test(releaseTime: Double = 3.0): Synth =
      play {
        import ugen._
        val gate0 = "gate".kr(1f)
        val gate  = if (!env.isSustained) gate0 else {
          EnvGen.kr(Env.step(Seq(1.0f, 0.0f), Seq(releaseTime, 0f))) * gate0
        }
        SinOsc.ar(800, math.Pi/2) * EnvGen.ar(env, gate = gate, levelScale = 0.3, doneAction = freeSelf)
      }
  }
}