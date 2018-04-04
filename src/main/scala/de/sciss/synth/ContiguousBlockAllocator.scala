/*
 *  ContiguousBlockAllocator.scala
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

private[synth] final class ContiguousBlockAllocator(size: Int, pos: Int = 0) {
  private[this] val array = new Array[Block](size)
  private[this] var freed = Map[Int, Set[Block]]()
  private[this] var top   = pos
  private[this] val sync  = new AnyRef

  // constructor
  array(pos) = new Block(pos, size - pos)

  def alloc: Int = alloc(1)

  def alloc(n: Int): Int =
    sync.synchronized {
      val b = findAvailable(n)
      if (b != null) reserve(b.start, n, b, null).start else -1
    }

  def free(address: Int): Unit =
    sync.synchronized {
      var b = array(address)
      if ((b != null) && b.used) {
        b.used = false
        addToFreed(b)
        val prev = findPrevious(address)
        if ((prev != null) && !prev.used) {
          val temp = prev.join(b)
          if (temp != null) {
            if (b.start == top) {
              top = temp.start
            }

            array(temp.start) = temp
            array(b.start) = null
            removeFromFreed(prev)
            removeFromFreed(b)
            if (top > temp.start) {
              addToFreed(temp)
            }

            b = temp
          }
        }

        val next = findNext(b.start)
        if ((next != null) && !next.used) {
          val temp = next.join(b)
          if (temp != null) {
            if (next.start == top) {
              top = temp.start
            }

            array(temp.start) = temp
            array(next.start) = null
            removeFromFreed(next)
            removeFromFreed(b)
          }

          if (top > temp.start) {
            addToFreed(temp)
          }
        }
      }
    }

  private def findAvailable(n: Int): Block = {
    freed.get(n).foreach(set => if (set.nonEmpty) return set.head)
    freed.foreach {
      entry =>
        if ((entry._1 >= n) && entry._2.nonEmpty) return entry._2.head
    }

    if ((top + n > size) || array(top).used) return null
    array(top)
  }

  private def addToFreed(b: Block): Unit = {
    val setO = freed.get(b.size)
    freed += (b.size -> (if (setO.isDefined) setO.get + b else Set(b)))
  }

  private def removeFromFreed(b: Block): Unit =
    freed.get(b.size).foreach { set =>
      val newSet = set - b
      if (newSet.isEmpty) {
        freed -= b.size
      } else {
        freed += (b.size -> newSet)
      }
    }

  private def findPrevious(address: Int): Block = {
    var i = address - 1
    while (i >= pos) {
      if (array(i) != null) return array(i)
      i -= 1
    }
    null
  }

  private def findNext(address: Int): Block = {
    val temp = array(address)
    if (temp != null) return array(temp.start + temp.size)

    var i = address + 1
    while (i <= top) {
      if (array(i) != null) return array(i)
      i += 1
    }
    null
  }

  private def reserve(address: Int, size: Int, availBlock: Block, prevBlock: Block): Block = {
    var b = if (availBlock != null) availBlock
    else {
      if (prevBlock != null) prevBlock else findPrevious(address)
    }

    if (b.start < address) {
      b = split(b, address - b.start, used = false)._2
    }

    split(b, size, used = true)._1
  }

  private def split(availBlock: Block, n: Int, used: Boolean): (Block, Block) = {
    val result    = availBlock.split(n)
    val newB      = result._1
    val leftOver  = result._2
    newB.used     = used
    removeFromFreed(availBlock)
    if (!used) addToFreed(newB)

    array(newB.start) = newB
    if (leftOver != null) {
      array(leftOver.start) = leftOver
      if (top > leftOver.start) {
        addToFreed(leftOver)
      } else {
        top = leftOver.start
      }
    }
    result
  }

  private final class Block(val start: Int, val size: Int) {
    var used = false

    def adjoins(b: Block): Boolean =
      ((start < b.start) && (start   + size   >= b.start)) ||
      ((start > b.start) && (b.start + b.size >= start  ))

    def join(b: Block): Block =
      if (adjoins(b)) {
        val newStart = math.min(start, b.start)
        val newSize = math.max(start + size, b.start + b.size) - newStart
        new Block(newStart, newSize)
      } else null

    def split(len: Int): (Block, Block) =
      if (len < size) {
        (new Block(start, len), new Block(start + len, size - len))
      } else if (len == size) {
        (this, null)
      } else {
        (null, null)
      }
  }
}