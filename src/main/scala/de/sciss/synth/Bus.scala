/*
 *  Bus.scala
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

import de.sciss.synth

import scala.collection.{IndexedSeq => SIndexedSeq}

object Bus {
  def apply(rate: Rate.Bus, server: Server = Server.default, numChannels: Int = 1): Bus =
    rate match {
      case synth.control => control(server, numChannels = numChannels)
      case synth.audio   => audio  (server, numChannels = numChannels)
    }

  /** Allocates a new control bus.
    * If there are no more available buses, an [[AllocatorExhausted]] exception is thrown.
    *
    * @param server       the server on which the bus resides
    * @param numChannels  the number of channels to allocate
    */
  def control(server: Server = Server.default, numChannels: Int = 1): ControlBus = {
    val id = server.allocControlBus(numChannels)
    if (id == -1) {
      throw AllocatorExhausted("Bus.control: failed to get a bus allocated (" +
        numChannels + " channels on " + server.name + ")")
    }
    ControlBus(server, id, numChannels)
  }

  /** Allocates a new audio bus.
    * If there are no more available buses, an [[AllocatorExhausted]] exception is thrown.
    *
    * @param server       the server on which the bus resides
    * @param numChannels  the number of channels to allocate
    */
  def audio(server: Server = Server.default, numChannels: Int = 1): AudioBus = {
    val id = server.allocAudioBus(numChannels)
    if (id == -1) {
      throw AllocatorExhausted("Bus.audio: failed to get a bus allocated (" +
        numChannels + " channels on " + server.name + ")")
    }
    AudioBus(server, id, numChannels)
  }
}

sealed trait Bus {
  /** The rate at which the bus operates. Either of `control` or `audio`. */
  def rate: Rate
  /** The logical index of the bus in the server architecture. */
  def index: Int
  /** The number of channels for this bus. */
  def numChannels: Int
  /** The server to which this bus belongs. */
  def server: Server

  /** Frees the bus. This is a client-side only operation which makes the `index` available
    * again for re-allocation.
    */
  def free(): Unit

  protected final var released  = false
  protected final val sync      = new AnyRef
}

final case class ControlBus(server: Server, index: Int, numChannels: Int) extends Bus {
  /** Control buses always run at `control` rate. */
  def rate: Rate = control

  def free(): Unit = sync.synchronized {
    if (released) sys.error(s"$this : has already been freed")
    server.freeControlBus(index)
    released = true
  }

  /** A convenience method that sets the control bus to one value.
    * It requires that the bus has exactly one channel, otherwise
    * an exception is thrown.
    *
    * @param value  the value to set the bus to
    */
  def setMsg(value: Float): message.ControlBusSet = {
    require(numChannels == 1)
    message.ControlBusSet((index, value))
  }

  /** Creates a `ControlBusSet` message using relative offsets.
    *
    * @param pairs    pairs of offsets and values. the offsets are relative to the index of this bus.
    *                 All offsets must be >= 0 and less than the number
    *                 of channels, otherwise an exception is thrown
    *
    * @return the `ControlBusSet` message with absolute indices
    */
  def setMsg(pairs: FillValue*): message.ControlBusSet = {
    require(pairs.forall(tup => tup.index >= 0 && tup.index < numChannels))
    message.ControlBusSet(pairs.map(tup => tup.copy(index = tup.index + index)): _*)
  }

  /** A convenience method that creates a `ControlBusSetn` message for setting the control bus to a
    * sequence of values. It requires that the bus's number of channels is equal to the argument's
    * size, otherwise an exception is thrown.
    *
    * @param values  the vector of values to set the bus to
    */
  def setnMsg(values: SIndexedSeq[Float]): message.ControlBusSetn = {
    require(values.size == numChannels)
    message.ControlBusSetn((index, values))
  }

  /** Creates a `ControlBusSetn` message using relative offsets.
    *
    * @param pairs    pairs of offsets and values. the offsets are relative to the index of this bus.
    *                 All offsets must be >= 0 and less than the number
    *                 of channels, otherwise an exception is thrown
    *
    * @return the `ControlBusSetn` message with absolute indices
    */
  def setnMsg(pairs: (Int, SIndexedSeq[Float])*): message.ControlBusSetn = {
    require(pairs.forall(tup => tup._1 >= 0 && (tup._1 + tup._2.size) <= numChannels))
    val iPairs = pairs.map(tup => (tup._1 + index, tup._2))
    message.ControlBusSetn(iPairs: _*)
  }

  /** A convenience method that gets the control bus value.
    * It requires that the bus has exactly one channel, otherwise
    * an exception is thrown.
    */
  def getMsg: message.ControlBusGet = {
    require(numChannels == 1)
    message.ControlBusGet(index)
  }

  /** Creates a `ControlBusGet` message using relative offsets.
    *
    * @param offsets  the offsets are relative to the index of this bus.
    *                 All offsets must be >= 0 and less than the number
    *                 of channels, otherwise an exception is thrown
    *
    * @return the `ControlBusGet` message with absolute indices
    */
  def getMsg(offsets: Int*): message.ControlBusGet = {
    require(offsets.forall(o => o >= 0 && o < numChannels))
    message.ControlBusGet(offsets.map(_ + index): _*)
  }

  /** A convenience method that queries all channels of the control bus. */
  def getnMsg = message.ControlBusGetn(index until (index + numChannels))

  /** Creates a `ControlBusGetn` message using relative offsets.
    *
    * @param ranges   ranges of offsets and number of consecutive channels to read.
    *                 The offsets are relative to the index of this bus.
    *                 All offsets must be >= 0 and less than the number
    *                 of channels, otherwise an exception is thrown
    *
    * @return the `ControlBusGetn` message with absolute indices
    */
  def getnMsg(ranges: Range*): message.ControlBusGetn = {
    require(ranges.forall(r => r.start >= 0 && r.last <= numChannels))
    message.ControlBusGetn(ranges.map(_.shift(index)): _*)
  }

  /** A convenience method that fills all channels of the control bus with one value. */
  def fillMsg(value: Float): message.ControlBusFill = {
    val data = FillRange(index = index, num = numChannels, value = value)
    message.ControlBusFill(data)
  }

  /** Creates a `ControlBusFill` message using relative offsets.
    *
    * @param data     tuples of offsets, number of consecutive channels and fill values.
    *                 The offsets are relative to the index of this bus.
    *                 All offsets must be >= 0 and less than the number
    *                 of channels, otherwise an exception is thrown
    *
    * @return the `ControlBusFill` message with absolute indices
    */
  def fillMsg(data: FillRange*): message.ControlBusFill = {
    require(data.forall(d => d.index >= 0 && (d.index + d.num) < numChannels))
    message.ControlBusFill(data.map(d => d.copy(index = d.index + index)): _*)
  }
}

final case class AudioBus(server: Server, index: Int, numChannels: Int) extends Bus {
  /** Audio buses always run at `audio` rate. */
  def rate: Rate = audio

  def free(): Unit = sync.synchronized {
    if (released) sys.error(s"$this : has already been freed")
    server.freeAudioBus(index)
    released = true
  }
}