/*
 *  BufferManager.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2021 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import de.sciss.model.impl.ModelImpl

object BufferManager {
  final case class BufferInfo(buffer: Buffer, info: message.BufferInfo.Data)
}

final class BufferManager(server: Server) extends ModelImpl[BufferManager.BufferInfo] {
  import BufferManager._

  private[this] var buffers: Map[Int, Buffer] = _
  private[this] val sync = new AnyRef

  // ---- constructor ----
  clear()

  def bufferInfo(msg: message.BufferInfo): Unit =
    sync.synchronized {
      msg.data.foreach { info =>
        buffers.get(info.bufId).foreach { buf =>
          // this is the only safe way: automatically unregister,
          // since unlike nodes whose id is steadily increasing
          // and which fire identifiable n_end messages, we
          // would run into trouble. putting unregister in
          // freeMsg like sclang does is not very elegant, as
          // that message might not be sent immediately or not
          // at all.
          buffers -= buf.id
          val change = BufferInfo(buf, info)
          dispatch(change)
          buf.updated(change)
        }
      }
    }

  // eventually this should be done automatically
  // by the message dispatch management
  def register(buf: Buffer): Unit =
    sync.synchronized {
      buffers += buf.id -> buf
    }

  def unregister(buf: Buffer): Unit =
    sync.synchronized {
      buffers -= buf.id
    }

  def clear(): Unit =
    sync.synchronized {
      buffers = Map.empty  // dispatch(Cleared)
    }
}