/*
 *  ServerImpl.scala
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
package impl

import de.sciss.model.impl.ModelImpl
import de.sciss.osc
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl
import de.sciss.synth.message.StatusReply

import java.io.{File, IOException}
import java.util.{Timer, TimerTask}
import scala.concurrent.duration.Duration
import scala.concurrent.{Future, Promise}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.NonFatal

private[synth] object ServerImpl {
  @volatile private var _default: Server = _

  def default: Server = {
    val res = _default
    if (res == null) throw new IllegalStateException("There is no default Server yet")
    res
  }

  private[impl] def add(s: Server): Unit =
    this.synchronized {
      if (_default == null) _default = s
    }

  private[impl] def remove(s: Server): Unit =
    this.synchronized {
      if (_default == s) _default = null
    }
}

private[synth] final class NRTImpl(dur: Double, sCfg: Server.Config)
  extends ProcessorImpl[Int, Processor[Int]] with Processor[Int] {

  protected def body(): Int = {
    val procArgs    = sCfg.toNonRealtimeArgs
    val procBuilder = Process(procArgs, Some(new File(sCfg.program).getParentFile))

    val log: ProcessLogger = new ProcessLogger {
      def buffer[A](f: => A): A = f

      def out(lineL: => String): Unit = {
        val line: String = lineL
        if (line.startsWith("nextOSCPacket")) {
          val time = line.substring(14).toFloat
          val prog = time / dur
          progress = prog
        } else {
          // ignore the 'start time <num>' message, and also the 'Found <num> LADSPA plugins' on Linux
          if (!line.startsWith("start time ") && !line.endsWith(" LADSPA plugins")) {
            Console.out.println(line)
          }
        }
      }

      def err(line: => String): Unit = Console.err.println(line)
    }

    val _proc = procBuilder.run(log)
    checkAborted()
    await(Processor.fromProcess("scsynth -N", _proc))
  }
}

private[synth] final class OnlineServerImpl(val name: String, c: osc.Client, val addr: Server.Address,
                                            val config: Server.Config, val clientConfig: Client.Config,
                                            var countsVar: message.StatusReply, timeOutTimer: java.util.Timer)
  extends ServerImpl { server =>

  import clientConfig.executionContext

  private[this] val condSync = new AnyRef

  @volatile
  private[this] var _condition      : Server.Condition      = Server.Running
  private[this] var pendingCondition: Server.Condition      = Server.NoPending
  private[this] var aliveThread     : Option[StatusWatcher] = None

  // ---- constructor ----
  //   OSCReceiverActor.start()
  c.action = OSCReceiverActor ! _
  ServerImpl.add(server)

  def isConnected: Boolean = c.isConnected

  def condition: Server.Condition = _condition

  def !(p: osc.Packet): Unit = c ! p

  def !![A](p: osc.Packet, timeout: Duration)(handler: PartialFunction[osc.Message, A]): Future[A] = {
    val promise  = Promise[A]()
    val res      = promise.future
    val oh       = new OSCTimeOutHandler(handler, promise)
    OSCReceiverActor.addHandler(oh)
    server ! p // only after addHandler!
    val tt = new TimerTask {
      override def run(): Unit =
        promise.tryFailure(message.Timeout())
    }
    if (timeout.isFinite) {
      timeOutTimer.schedule(tt, timeout.toMillis)
      res.andThen {
        case _ => tt.cancel()
      }
    } else {
      res
    }
  }

  def serverOffline(): Unit =
    condSync.synchronized {
      stopAliveThread()
      condition_=(Server.Offline)
    }

  def counts: StatusReply = countsVar

  private[synth] def counts_=(newCounts: message.StatusReply): Unit = {
    countsVar = newCounts
    dispatch(Server.Counts(newCounts))
  }

  def dumpInOSC(mode: osc.Dump, filter: osc.Packet => Boolean): Unit =
    c.dumpIn(mode, filter = {
      case _: message.StatusReply => false
      case p => filter(p)
    })

  def dumpOutOSC(mode: osc.Dump, filter: osc.Packet => Boolean): Unit =
    c.dumpOut(mode, filter = {
      case message.Status => false
      case p => filter(p)
    })

  private def disposeImpl(): Unit = {
    nodeManager .clear()
    bufManager  .clear()
    // OSCReceiverActor.clear()
    ServerImpl.remove(this)
    OSCReceiverActor.dispose()
    try {
      c.close()
    } catch {
      case NonFatal(e) => e.printStackTrace()
    }
  }

  def isRunning: Boolean = _condition == Server.Running
  def isOffline: Boolean = _condition == Server.Offline

  def addResponder   (resp: message.Responder): Unit = OSCReceiverActor.addHandler   (resp)
  def removeResponder(resp: message.Responder): Unit = OSCReceiverActor.removeHandler(resp)

  def quit(): Unit = {
    this ! quitMsg
    dispose()
  }

  def dispose(): Unit =
    condSync.synchronized {
      serverOffline()
    }

  def initTree(): Unit = {
    nodeManager.register(defaultGroup)
    server ! defaultGroup.newMsg(rootNode, addToHead)
  }

  private[synth] def condition_=(newCondition: Server.Condition): Unit =
    condSync.synchronized {
      if (newCondition != _condition) {
        _condition = newCondition
        if (newCondition == Server.Offline) {
          pendingCondition = Server.NoPending
          disposeImpl()
        }
        //            else if( newCondition == Running ) {
        //               if( pendingCondition == Booting ) {
        //                  pendingCondition = NoPending
        //                  collBootCompletion.foreach( action => try {
        //                        action.apply( this )
        //                     }
        //                     catch { case e => e.printStackTrace() }
        //                  )
        //                  collBootCompletion = Queue.empty
        //               }
        //            }
        dispatch(newCondition)
      }
    }

  def startAliveThread(delay: Float = 0.25f, period: Float = 0.25f, deathBounces: Int = 25): Unit =
    condSync.synchronized {
      if (aliveThread.isEmpty) {
        val statusWatcher = new StatusWatcher(delay, period, deathBounces)
        aliveThread = Some(statusWatcher)
        statusWatcher.start()
      }
    }

  def stopAliveThread(): Unit =
    condSync.synchronized {
      aliveThread.foreach(_.stop())
      aliveThread = None
    }


  private object OSCReceiverActor {
    import scala.concurrent._

    private val sync = new AnyRef
    @volatile private var handlers = Set.empty[message.Handler]

    def ! (p: osc.Packet): Unit = {
      Future {
        blocking {
          p match {
            case nodeMsg        : message.NodeChange  =>
              // println(s"---- NodeChange: $nodeMsg")
              nodeManager.nodeChange(nodeMsg)
            case bufInfoMsg     : message.BufferInfo  => bufManager.bufferInfo(bufInfoMsg)
            case statusReplyMsg : message.StatusReply => aliveThread.foreach(_.statusReply(statusReplyMsg))
            case _ =>
          }
          p match {
            case m: osc.Message =>
              handlers.foreach { h =>
                if (h.handle(m)) sync.synchronized(handlers -= h)
              }
            case _ => // ignore bundles send from scsynth
          }
        }
      }
      ()
    }

    def clear(): Unit = {
      val h = sync.synchronized {
        val res = handlers
        handlers = Set.empty
        res
      }
      h.foreach(_.removed())
    }

    def dispose(): Unit = clear()

    def addHandler(h: message.Handler): Unit =
      sync.synchronized(handlers += h)

    def removeHandler(h: message.Handler): Unit = {
      val seen = sync.synchronized {
        val res = handlers.contains(h)
        if (res) handlers -= h
        res
      }
      if (seen) h.removed()
    }
  }

  private final class OSCTimeOutHandler[A](fun: PartialFunction[osc.Message, A], promise: Promise[A])
    extends message.Handler {

    def handle(msg: osc.Message): Boolean = {
      if (promise.isCompleted) return true

      val handled = fun.isDefinedAt(msg)
      if (handled) try {
        promise.trySuccess(fun(msg))
      } catch {
        case NonFatal(e) =>
          promise.tryFailure(e)
      }
      handled
    }

    def removed(): Unit = ()
  }

  // -------- internal class StatusWatcher --------

  private final class StatusWatcher(delay: Float, period: Float, deathBounces: Int)
    extends Runnable {
    watcher =>

    private[this] var	alive			          = deathBounces
    private[this] val delayMillis         = (delay  * 1000).toInt
    private[this] val periodMillis        = (period * 1000).toInt
    private[this] var timer               = Option.empty[Timer]
    private[this] var callServerContacted = true
    private[this] val sync                = new AnyRef

    def start(): Unit = {
      stop()
      val t = new Timer("StatusWatcher", true)
      t.schedule(new TimerTask {
        def run(): Unit = watcher.run()
      }, delayMillis, periodMillis)
      timer = Some(t)
    }

    def stop(): Unit = {
      timer.foreach { t =>
        t.cancel()
        timer = None
      }
    }

    def run(): Unit = {
      sync.synchronized {
        alive -= 1
        if (alive < 0) {
          callServerContacted = true
          condition = Server.Offline
        }
      }
      try {
        queryCounts()
      }
      catch {
        case e: IOException => Server.printError("Server.status", e)
      }
    }

    def statusReply(msg: message.StatusReply): Unit =
      sync.synchronized {
        alive = deathBounces
        // note: put the counts before running
        // because that way e.g. the sampleRate
        // is instantly available
        counts = msg
        if (!isRunning && callServerContacted) {
          callServerContacted = false
          //               serverContacted
          condition = Server.Running
        }
      }
  }
}

private[synth] final class OfflineServerImpl(val name: String, val config: Server.Config,
                                             val clientConfig: Client.Config,
                                             val counts: message.StatusReply) extends ServerImpl {
  def isConnected = false
  def isRunning   = true
  def isOffline   = false

  private def offlineException() = new Exception("Server is not connected")

  def !(p: osc.Packet): Unit = throw offlineException()

  def !![A](packet: osc.Packet, timeout: Duration)(handler: PartialFunction[osc.Message, A]): Future[A] =
    Future.failed(offlineException())

  def condition: Server.Condition = Server.Running

  def startAliveThread(delay: Float, period: Float, deathBounces: Int): Unit = ()
  def stopAliveThread(): Unit = ()

  def dumpInOSC (mode: osc.Dump, filter: osc.Packet => Boolean): Unit = ()
  def dumpOutOSC(mode: osc.Dump, filter: osc.Packet => Boolean): Unit = ()

  private[synth] def addResponder   (resp: message.Responder): Unit = ()
  private[synth] def removeResponder(resp: message.Responder): Unit = ()

  def dispose(): Unit = ()
  def quit   (): Unit = ()

  def addr: Server.Address = Server.mkAddress(config)
}

private[synth] abstract class ServerImpl
  extends Server with ModelImpl[Server.Update] {

  server =>

  final val rootNode      = Group(this, 0)
  final val defaultGroup  = Group(this, 1)
  final val nodeManager   = new NodeManager  (this)
  final val bufManager    = new BufferManager(this)

  private[this] val nodeAllocator        = new NodeIdAllocator(clientConfig.clientId, clientConfig.nodeIdOffset)
  private[this] val controlBusAllocator  = new ContiguousBlockAllocator(config.controlBusChannels)
  private[this] val audioBusAllocator    = new ContiguousBlockAllocator(config.audioBusChannels, config.internalBusIndex)
  private[this] val bufferAllocator      = new ContiguousBlockAllocator(config.audioBuffers)
  private[this] var uniqueId             = 0
  private[this] val uniqueSync           = new AnyRef

  final def isLocal: Boolean = Server.isLocal(addr)

  final def nextNodeId(): Int = nodeAllocator.alloc()

  final def allocControlBus(numChannels: Int): Int = controlBusAllocator.alloc(numChannels)
  final def allocAudioBus  (numChannels: Int): Int = audioBusAllocator  .alloc(numChannels)
  final def allocBuffer    (numChannels: Int): Int = bufferAllocator    .alloc(numChannels)

  final def freeControlBus(index: Int): Unit = controlBusAllocator.free(index)
  final def freeAudioBus  (index: Int): Unit = audioBusAllocator  .free(index)
  final def freeBuffer    (index: Int): Unit = bufferAllocator    .free(index)

  final def nextSyncId(): Int = uniqueSync.synchronized {
    val res = uniqueId; uniqueId += 1; res
  }

  final def sampleRate: Double = counts.sampleRate

  final def queryCounts(): Unit = this ! message.Status

  final def dumpOSC(mode: osc.Dump, filter: osc.Packet => Boolean): Unit = {
    dumpInOSC (mode, filter)
    dumpOutOSC(mode, filter)
  }

  final def syncMsg(): message.Sync = message.Sync(nextSyncId())
  final def quitMsg: message.ServerQuit.type = message.ServerQuit
}