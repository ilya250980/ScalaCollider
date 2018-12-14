/*
 *  ServerCodec.scala
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

import java.io.PrintStream
import java.nio.ByteBuffer

import de.sciss.osc.{Bundle, Message, Packet, PacketCodec}

object ServerCodec extends PacketCodec {
  import Packet._

  private final val superCodec = PacketCodec().scsynth().build

  private final val decodeStatusReply: (String, ByteBuffer) => Message = (name, b) => {
    // ",iiiiiffdd"
    if ((b.getLong != 0x2C69696969696666L) || (b.getShort != 0x6464)) decodeFail(name)
    skipToValues(b)

    //		if( b.getInt() != 1) decodeFail  // was probably intended as a version number...
    b.getInt()
    val numUGens          = b.getInt()
    val numSynths         = b.getInt()
    val numGroups         = b.getInt()
    val numDefs           = b.getInt()
    val avgCPU            = b.getFloat()
    val peakCPU           = b.getFloat()
    val sampleRate        = b.getDouble()
    val actualSampleRate  = b.getDouble()

    StatusReply(numUGens = numUGens, numSynths = numSynths, numGroups = numGroups, numDefs = numDefs,
      avgCPU = avgCPU, peakCPU = peakCPU, sampleRate = sampleRate, actualSampleRate = actualSampleRate)
  }

  private final val decodeSynced: (String, ByteBuffer) => Message = { (name, b) =>
    // ",i"
    if (b.getShort() != 0x2C69) decodeFail(name)
    skipToValues(b)
    val id = b.getInt()
    Synced(id)
  }

  private final val decodeTrigger: (String, ByteBuffer) => Message = { (name, b) =>
    // ",iif"
    if (b.getInt() != 0x2C696966) decodeFail(name)
    skipToValues(b)

    val nodeId  = b.getInt()
    val trig    = b.getInt()
    val value   = b.getFloat()

    Trigger(nodeId = nodeId, trig = trig, value = value)
  }

  private def decodeSetNum(b: ByteBuffer, name: String): Int = {
    // "if" * N
    var num = 0
    var tt  = b.get()
    while (tt != 0) {
      if (tt != 0x69) decodeFail(name)
      tt   = b.get()
      if (tt != 0x66) decodeFail(name)
      num += 1
      tt   = b.get()
    }
    skipToAlign(b)
    num
  }

  private def decodeSetnNum(b: ByteBuffer, name: String): Int = {
    // ["ii" ++ "f" * M] * N
    var num = 0
    var tt  = b.get()
    while (tt != 0) {
      // "ii"
      if (tt != 0x69) decodeFail(name)
      tt = b.get()
      if (tt != 0x69) decodeFail(name)
      num += 1
      tt   = b.get()
      while (tt == 0x66) tt = b.get() // "f" * M
    }
    skipToAlign(b)
    num
  }

  private def decodeSetPairs(b: ByteBuffer, num: Int): Vector[FillValue] = Vector.fill(num) {
    val index = b.getInt()
    val value = b.getFloat()
    FillValue(index, value)
  }

  private def decodeSetnPairs(b: ByteBuffer, num: Int): Vector[(Int, Vector[Float])] = Vector.fill(num) {
    val index   = b.getInt()
    val num     = b.getInt()
    val values  = Vector.fill(num)(b.getFloat())
    (index, values)
  }

  private final val decodeBufferSet: (String, ByteBuffer) => Message = { (name, b) =>
    // ",i"
    if (b.getShort() != 0x2C69) decodeFail(name)
    val num   = decodeSetNum  (b, name)
    val id    = b.getInt()
    val pairs = decodeSetPairs(b, num )

    BufferSet(id, pairs: _*)
  }

  private final val decodeBufferSetn: (String, ByteBuffer) => Message = { (name, b) =>
    // ",i"
    if (b.getShort() != 0x2C69) decodeFail(name)
    val num   = decodeSetnNum  (b, name)
    val id    = b.getInt()
    val pairs = decodeSetnPairs(b, num )

    BufferSetn(id, pairs: _*)
  }

  private final val decodeControlBusSet: (String, ByteBuffer) => Message = { (name, b) =>
    // ","
    if (b.get() != 0x2C) decodeFail(name)
    val num   = decodeSetNum  (b, name)
    val pairs = decodeSetPairs(b, num )

    ControlBusSet(pairs: _*)
  }

  private final val decodeControlBusSetn: (String, ByteBuffer) => Message = { (name, b) =>
    // ","
    if (b.get() != 0x2C) decodeFail(name)
    val num   = decodeSetnNum  (b, name)
    val pairs = decodeSetnPairs(b, num )

    ControlBusSetn(pairs: _*)
  }

  private def decodeNodeChange(factory: NodeMessageFactory): (String, ByteBuffer) => Message = { (name, b) =>
    // ",iiiii[ii]"
    if ((b.getInt() != 0x2C696969) || (b.getShort() != 0x6969)) decodeFail(name)
    val extTags = b.getShort()
    if ((extTags & 0xFF) == 0x00) {
      skipToAlign(b)
    } else {
      skipToValues(b)
    }
    val nodeId    = b.getInt()
    val parentId  = b.getInt()
    val predId    = b.getInt()
    val succId    = b.getInt()
    val nodeType  = b.getInt()

    if (nodeType == 0) {
      factory.apply(nodeId, NodeInfo.SynthData(parentId, predId, succId))
    } else if ((nodeType == 1) && (extTags == 0x6969)) {
      // group
      val headId  = b.getInt()
      val tailId  = b.getInt()
      factory.apply(nodeId, NodeInfo.GroupData(parentId, predId, succId, headId, tailId))
    } else decodeFail(name)
  }

  private val decodeBufferInfo: (String, ByteBuffer) => Message = { (name, b) =>
    // ",[iiif]*N"
    if (b.get() != 0x2C) decodeFail(name)
    var cnt = 0
    var tag = b.getShort()
    while (tag != 0x0000) {
      if ((tag != 0x6969) || (b.getShort() != 0x6966)) decodeFail(name)
      cnt += 1
      tag = b.getShort()
    }
    skipToAlign(b)
    val info = Seq.newBuilder[BufferInfo.Data]
    while (cnt > 0) {
      info += BufferInfo.Data(b.getInt(), b.getInt(), b.getInt(), b.getFloat())
      cnt -= 1
    }
    BufferInfo(info.result(): _*)
  }

  private final val msgDecoders = Map[String, (String, ByteBuffer) => Message](
    "/status.reply" -> decodeStatusReply,
    "/n_go"         -> decodeNodeChange(NodeGo  ),
    "/n_end"        -> decodeNodeChange(NodeEnd ),
    "/n_off"        -> decodeNodeChange(NodeOff ),
    "/n_on"         -> decodeNodeChange(NodeOn  ),
    "/n_move"       -> decodeNodeChange(NodeMove),
    "/n_info"       -> decodeNodeChange(NodeInfo),
    "/synced"       -> decodeSynced,
    "/b_set"        -> decodeBufferSet,
    "/b_setn"       -> decodeBufferSetn,
    "/c_set"        -> decodeControlBusSet,
    "/c_setn"       -> decodeControlBusSetn,
    "/b_info"       -> decodeBufferInfo,
    "/tr"           -> decodeTrigger,
    "status.reply"  -> decodeStatusReply  // old SC versions dropped the slash
  )

  private final val superDecoder: (String, ByteBuffer) => Message =
    (name, b) => superCodec.decodeMessage(name, b) // super.decodeMessage( name, b )

  override /* protected */ def decodeMessage(name: String, b: ByteBuffer): Message = {
    msgDecoders.getOrElse(name, superDecoder).apply(name, b)
    /*		val dec = msgDecoders.get( name )
        if( dec.isDefined ) {
          dec.get.apply( name, b )
        } else {
          super.decodeMessage( name, b )
        }
    */
  }

  def encodeMessage(msg: Message, b: ByteBuffer): Unit = superCodec.encodeMessage(msg, b)

  def encodedMessageSize(msg: Message): Int = superCodec.encodedMessageSize(msg)

  def encodeBundle(bndl: Bundle, b: ByteBuffer): Unit = superCodec.encodeBundle(bndl, b)

  def printAtom(value: Any, stream: PrintStream, nestCount: Int): Unit =
    superCodec.printAtom(value, stream, nestCount)

  final val charsetName: String = superCodec.charsetName

  private def decodeFail(name: String): Nothing = throw PacketCodec.MalformedPacket(name)
}