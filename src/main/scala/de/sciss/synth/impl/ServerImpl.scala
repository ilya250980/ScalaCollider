/*
 *  ServerImpl.scala
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
package impl

import java.io.{File, IOException}
import java.net.InetSocketAddress
import java.util.{Timer, TimerTask}

import de.sciss.model.impl.ModelImpl
import de.sciss.osc
import de.sciss.processor.Processor
import de.sciss.processor.impl.ProcessorImpl

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise, TimeoutException}
import scala.sys.process.{Process, ProcessLogger}
import scala.util.control.NonFatal

private[synth] object ServerImpl {
  @volatile private var _default: Server = null

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

private[synth] final class OnlineServerImpl(val name: String, c: osc.Client, val addr: InetSocketAddress,
                                            val config: Server.Config, val clientConfig: Client.Config,
                                            var countsVar: message.StatusReply)
  extends ServerImpl { server =>

  import clientConfig.executionContext

  private val condSync = new AnyRef
  @volatile private var _condition: Server.Condition = Server.Running
  private var pendingCondition    : Server.Condition = Server.NoPending
  private var aliveThread: Option[StatusWatcher] = None

  // ---- constructor ----
  //   OSCReceiverActor.start()
  c.action = OSCReceiverActor ! _
  ServerImpl.add(server)

  def isConnected = c.isConnected

  def condition = _condition

  def !(p: osc.Packet): Unit = c ! p

  def !![A](p: osc.Packet, timeout: Duration)(handler: PartialFunction[osc.Message, A]): Future[A] = {
    val promise  = Promise[A]()
    val res      = promise.future
    val oh       = new OSCTimeOutHandler(handler, promise)
    OSCReceiverActor.addHandler(oh)
    server ! p // only after addHandler!
    Future {
      try {
        Await.ready(res, timeout)
      } catch {
        case _: TimeoutException => promise.tryFailure(message.Timeout())
      }
    }
    res
  }

  def serverOffline(): Unit =
    condSync.synchronized {
      stopAliveThread()
      condition_=(Server.Offline)
    }

  def counts = countsVar

  private[synth] def counts_=(newCounts: message.StatusReply): Unit = {
    countsVar = newCounts
    dispatch(Server.Counts(newCounts))
  }

  def dumpInOSC(mode: osc.Dump, filter: osc.Packet => Boolean): Unit =
    c.dumpIn(mode, filter = {
      case m: message.StatusReply => false
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

  def isRunning   = _condition == Server.Running
  def isOffline   = _condition == Server.Offline

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


  private object OSCReceiverActor /* extends DaemonActor */ {
    import scala.concurrent._

    //    private case object Clear
    //
    //    private case object Dispose
    //
    //    private case class AddHandler(h: message.Handler)
    //
    //    private case class RemoveHandler(h: message.Handler)

    //      private case class  TimeOutHandler( h: OSCTimeOutHandler )

    private val sync = new AnyRef
    @volatile private var handlers = Set.empty[message.Handler]

    def !(p: osc.Packet): Unit = Future {
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

    def removed() = ()
  }

  // -------- internal class StatusWatcher --------

  private final class StatusWatcher(delay: Float, period: Float, deathBounces: Int)
    extends Runnable {
    watcher =>

    private var	alive			          = deathBounces
    private val delayMillis         = (delay  * 1000).toInt
    private val periodMillis        = (period * 1000).toInt
    //      private val	timer			   = new SwingTimer( periodMillis, this )
    private var timer               = Option.empty[Timer]
    private var callServerContacted = true
    private val sync                = new AnyRef

    //      // ---- constructor ----
    //      timer.setInitialDelay( delayMillis )

    def start(): Unit = {
      stop()
      val t = new Timer("StatusWatcher", true)
      t.schedule(new TimerTask {
        def run(): Unit = watcher.run()
      }, delayMillis, periodMillis)
      timer = Some(t)
    }

    def stop(): Unit = {
      //         timer.stop
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

  def addr: InetSocketAddress = new InetSocketAddress(config.host, config.port)
}

private[synth] abstract class ServerImpl
  extends Server with ModelImpl[Server.Update] {

  server =>

  val rootNode      = Group(this, 0)
  val defaultGroup  = Group(this, 1)
  val nodeManager   = new NodeManager  (this)
  val bufManager    = new BufferManager(this)

  private val nodeAllocator        = new NodeIDAllocator(clientConfig.clientID, clientConfig.nodeIDOffset)
  private val controlBusAllocator  = new ContiguousBlockAllocator(config.controlBusChannels)
  private val audioBusAllocator    = new ContiguousBlockAllocator(config.audioBusChannels, config.internalBusIndex)
  private val bufferAllocator      = new ContiguousBlockAllocator(config.audioBuffers)
  private var uniqueID             = 0
  private val uniqueSync           = new AnyRef

  def isLocal: Boolean = {
    val host = addr.getAddress
    host.isLoopbackAddress || host.isSiteLocalAddress
  }

  def nextNodeID(): Int = nodeAllocator.alloc()

  def allocControlBus(numChannels: Int): Int = controlBusAllocator.alloc(numChannels)
  def allocAudioBus  (numChannels: Int): Int = audioBusAllocator  .alloc(numChannels)
  def allocBuffer    (numChannels: Int): Int = bufferAllocator    .alloc(numChannels)

  def freeControlBus(index: Int): Unit = controlBusAllocator.free(index)
  def freeAudioBus  (index: Int): Unit = audioBusAllocator  .free(index)
  def freeBuffer    (index: Int): Unit = bufferAllocator    .free(index)

  def nextSyncID(): Int = uniqueSync.synchronized {
    val res = uniqueID; uniqueID += 1; res
  }

  def sampleRate = counts.sampleRate

  def dumpTree(controls: Boolean = false): Unit = {
    import de.sciss.synth.Ops._
    rootNode.dumpTree(controls)
  }

  def queryCounts(): Unit = this ! message.Status

  def dumpOSC(mode: osc.Dump, filter: osc.Packet => Boolean): Unit = {
    dumpInOSC (mode, filter)
    dumpOutOSC(mode, filter)
  }

  def syncMsg(): message.Sync = message.Sync(nextSyncID())
  def quitMsg = message.ServerQuit
}