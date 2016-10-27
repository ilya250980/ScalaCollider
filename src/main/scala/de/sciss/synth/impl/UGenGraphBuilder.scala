/*
 *  UGenGraphBuilder.scala
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

package de.sciss
package synth
package impl

import de.sciss.synth.UGenGraph.IndexedUGen
import de.sciss.synth.ugen.{Constant, ControlProxyLike, ControlUGenOutProxy, UGenProxy}

import scala.annotation.{elidable, tailrec}
import scala.collection.{breakOut, mutable}
import scala.collection.immutable.{IndexedSeq => Vec, Set => ISet}
import scala.collection.mutable.{Buffer => MBuffer, Map => MMap, Stack => MStack}

object DefaultUGenGraphBuilderFactory extends UGenGraph.BuilderFactory {
  def build(graph: SynthGraph): UGenGraph = {
    val b = new DefaultUGenGraphBuilder
    UGenGraph.use(b) {
      val proxies = buildWith(graph, b)
      b.build(proxies)
    }
  }

  /** Recursively expands the synth graph until no elements are left.
    * The caller should in most cases make sure that the builder is
    * actually installed as the current one, wrapping the call in
    * `UGenGraph.use(builder)`!
    * The method returns the control proxies for further processing
    * in the builder.
    *
    * @param g0       the graph to expand
    * @param builder  the builder that will assembly the ugens
    */
  def buildWith(g0: SynthGraph, builder: UGenGraph.Builder): ISet[ControlProxyLike] = {
    var g = g0
    var controlProxies = ISet.empty[ControlProxyLike]
    while (g.nonEmpty) {
      // XXX these two lines could be more efficient eventually -- using a 'clearable' SynthGraph
      controlProxies ++= g.controlProxies
      g = SynthGraph(g.sources.foreach(_.force(builder))) // allow for further graphs being created
    }
    controlProxies
  }
}

final class DefaultUGenGraphBuilder extends BasicUGenGraphBuilder {
  builder =>

  override def toString = s"UGenGraph.Builder@${hashCode.toHexString}"
}

object UGenGraphBuilderLike {
  var DEBUG = false

  // ---- IndexedUGen ----
  private final class IndexedUGenBuilder(val ugen: UGen, var index: Int, var effective: Boolean) {
    val parents       = MBuffer.empty[IndexedUGenBuilder]
    var children      = MBuffer.empty[IndexedUGenBuilder]
    var inputIndices  = List   .empty[UGenInIndex]

    override def toString = s"IndexedUGen($ugen, $index, $effective) : richInputs = $inputIndices"
  }

  private trait UGenInIndex {
    def create: (Int, Int)  // XXX TODO --- replace with specialized tuple to avoid boxing?
    def makeEffective(): Int
  }

  private final class ConstantIndex(constIdx: Int) extends UGenInIndex {
    def create          = (-1, constIdx)
    def makeEffective() = 0

    override def toString = s"ConstantIndex($constIdx)"
  }

  private final class UGenProxyIndex(iu: IndexedUGenBuilder, outIdx: Int) extends UGenInIndex {
    def create: (Int, Int) = (iu.index, outIdx)

    def makeEffective(): Int = {
      if (!iu.effective) {
        iu.effective = true
        var numEff   = 1
        iu.inputIndices.foreach(numEff += _.makeEffective())
        numEff
      } else 0
    }

    override def toString = s"UGenProxyIndex($iu, $outIdx)"
  }
}

trait BasicUGenGraphBuilder extends UGenGraphBuilderLike {
  protected var ugens         = Vector.empty: Vec[UGen]
  protected var controlValues = Vector.empty: Vec[Float]
  protected var controlNames  = Vector.empty: Vec[(String, Int)]
  protected var sourceMap     = Map.empty[AnyRef, Any]
}

/** Complete implementation of a ugen graph builder, except for the actual code that
  * calls `force` on the sources of a `SynthGraph`. Implementations should call
  * the `build` method passing in the control proxies for all involved synth graphs.
  */
trait UGenGraphBuilderLike extends UGenGraph.Builder {
  builder =>

  import UGenGraphBuilderLike._

  // ---- abstract ----

  // updated during build
  protected var ugens         : Vec[UGen]
  protected var controlValues : Vec[Float]
  protected var controlNames  : Vec[(String, Int)]
  protected var sourceMap     : Map[AnyRef, Any]

  // ---- impl: public ----

  var showLog = false

  final def addUGen(ugen: UGen): Unit = {
    ugens :+= ugen
    log(s"addUGen ${ugen.name} @ ${ugen.hashCode.toHexString} ${if (ugen.isIndividual) "indiv" else ""}")
  }

  final def prependUGen(ugen: UGen): Unit = {
    ugens +:= ugen
    log(s"prependUGen ${ugen.name} @ ${ugen.hashCode.toHexString} ${if (ugen.isIndividual) "indiv" else ""}")
  }

  final def addControl(values: Vec[Float], name: Option[String]): Int = {
    val specialIndex = controlValues.size
    controlValues  ++= values
    name.foreach(n => controlNames :+= n -> specialIndex)
    log(s"addControl ${name.getOrElse("<unnamed>")} num = ${values.size}, idx = $specialIndex")
    specialIndex
  }

  def visit[U](ref: AnyRef, init: => U): U = {
    log(s"visit  ${ref.hashCode.toHexString}")
    sourceMap.getOrElse(ref, {
      log(s"expand ${ref.hashCode.toHexString}...")
      val exp    = init
      log(s"...${ref.hashCode.toHexString} -> ${exp.hashCode.toHexString} ${printSmart(exp)}")
      sourceMap += ref -> exp
      exp
    }).asInstanceOf[U] // not so pretty...
  }

  // ---- impl: protected ----

  // this proxy function is useful because `elem.force` is package private.
  // so other projects implementing `UGenGraphBuilderLike` can use this function
  protected def force(elem: Lazy): Unit = elem.force(this)

  /** Finalizes the build process. It is assumed that the graph elements have been expanded at this
    * stage, having called into `addUGen` and `addControl`. The caller must collect all the control
    * proxies and pass them into this method.
    *
    * @param controlProxies   the control proxies participating in this graph
    *
    * @return  the completed `UGenGraph` build
    */
  def build(controlProxies: Iterable[ControlProxyLike]): UGenGraph = {
    val ctrlProxyMap        = buildControls(controlProxies)
    val (iUGens, constants) = indexUGens(ctrlProxyMap)
    val indexedUGens        = sortUGens(iUGens)
    val richUGens: Vec[IndexedUGen] =
      indexedUGens.map(iu => new IndexedUGen(iu.ugen, iu.inputIndices.map(_.create)))(breakOut)
    UGenGraph(constants, controlValues, controlNames, richUGens)
  }

  // ---- impl: private ----

  private def indexUGens(ctrlProxyMap: Map[ControlProxyLike, (UGen, Int)]): (Vec[IndexedUGenBuilder], Vec[Float]) = {
    val constantMap     = MMap.empty[Float, ConstantIndex]
    val constants       = Vector.newBuilder[Float]
    var numConstants    = 0
    var numIneffective  = ugens.size
    val indexedUGens    = ugens.zipWithIndex.map { case (ugen, idx) =>
      val eff = ugen.hasSideEffect
      if (eff) numIneffective -= 1
      new IndexedUGenBuilder(ugen, idx, eff)
    }

    import UGenGraphBuilderLike.DEBUG
    if (DEBUG) {
      log(s"indexUGens. size = ${indexedUGens.size}")
      indexedUGens.foreach { iu =>
        println(s"${iu.ugen.name} @ ${iu.ugen.hashCode.toHexString}")
      }
    }

    val ugenMap: collection.Map[UGen, IndexedUGenBuilder] =
      if (DEBUG) {
        val ugenSeen = mutable.Set.empty[UGen]
        val ugenMapB = mutable.Map.newBuilder[UGen, IndexedUGenBuilder]
        ugenMapB.sizeHint(indexedUGens.size)
        indexedUGens.foreach { iu =>
          val ugen = iu.ugen
          if (ugenSeen.contains(ugen)) {
            def calcDepth(x: UGen, res: Int): Int = {
              var res1 = res
              x.inputs.foreach {
                case up: UGenProxy =>
                  val res2 = calcDepth(up.source, res + 1)
                  if (res2 > res1) res1 = res2
                case _ =>
              }
              res1
            }
            println(s"---- duplicate UGen: ${ugen.name} @ ${ugen.hashCode.toHexString} - depth = ${calcDepth(ugen, 0)}")
//            println(ugen.toString)
          } else {
            ugenSeen += ugen
            val tup = (ugen, iu)
            ugenMapB += tup
          }
        }
        ugenMapB.result()

//        val ugenSeen = mutable.Set.empty[UGen]
//        val ugenMapB = new java.util.HashMap[UGen, IndexedUGenBuilder](indexedUGens.size)
//        indexedUGens.foreach { iu =>
//          val ugen = iu.ugen
//          if (ugenSeen.contains(ugen)) {
//            println(s"\n---- duplicate UGen: ${ugen.name} @ ${ugen.hashCode.toHexString}")
//            println(ugen.toString)
//          }
//          ugenMapB.put(ugen, iu)
//        }
//        import collection.JavaConverters._
//        ugenMapB.asScala

      } else {
        indexedUGens.map { iu =>
          val tup = (iu.ugen, iu)
          tup
        } (breakOut)
      }

    indexedUGens.foreach { iu =>
      // XXX Warning: match not exhaustive -- "missing combination UGenOutProxy"
      // this is clearly a nasty scala bug, as UGenProxy does catch UGenOutProxy;
      // might be http://lampsvn.epfl.ch/trac/scala/ticket/4020
      iu.inputIndices = iu.ugen.inputs.map {
        // don't worry -- the match _is_ exhaustive
        case Constant(value) => constantMap.getOrElse(value, {
          val rc        = new ConstantIndex(numConstants)
          constantMap  += value -> rc
          constants    += value
          numConstants += 1
          rc
        })

        case up: UGenProxy =>
          val iui       = ugenMap(up.source)
          iu.parents   += iui
          iui.children += iu
          new UGenProxyIndex(iui, up.outputIndex)

        case ControlUGenOutProxy(proxy, outputIndex) =>
          val (ugen, off) = ctrlProxyMap(proxy)
          val iui         = ugenMap(ugen)
          iu.parents     += iui
          iui.children   += iu
          new UGenProxyIndex(iui, off + outputIndex)

      } (breakOut)
      if (iu.effective) iu.inputIndices.foreach(numIneffective -= _.makeEffective())
    }
    val filtered: Vec[IndexedUGenBuilder] = if (numIneffective == 0) indexedUGens
    else indexedUGens.collect {
      case iu if iu.effective =>
        iu.children = iu.children.filter(_.effective)
        iu
    }
    (filtered, constants.result())
  }

  /*
   *    Note that in Scala like probably in most other languages,
   *    the UGens _can only_ be added in right topological order,
   *    as that is the only way they can refer to their inputs.
   *    However, the Synth-Definition-File-Format help documents
   *    states that depth-first order is preferable performance-
   *    wise. Truth is, performance is probably the same,
   *    mNumWireBufs might be different, so it's a space not a
   *    time issue.
   */
  private def sortUGens(indexedUGens: Vec[IndexedUGenBuilder]): Array[IndexedUGenBuilder] = {
    indexedUGens.foreach(iu => iu.children = iu.children.sortWith((a, b) => a.index > b.index))
    val sorted = new Array[IndexedUGenBuilder](indexedUGens.size)
    //      val avail   = MStack( indexedUGens.filter( _.parents.isEmpty ) : _* )
    val avail: MStack[IndexedUGenBuilder] = indexedUGens.collect {
      case iu if iu.parents.isEmpty => iu
    } (breakOut)

    var cnt = 0
    while (avail.nonEmpty) {
      val iu      = avail.pop()
      iu.index    = cnt
      sorted(cnt) = iu
      cnt        += 1
      iu.children foreach { iuc =>
        iuc.parents.remove(iuc.parents.indexOf(iu))
        if (iuc.parents.isEmpty) /* avail =*/ avail.push(iuc)
      }
    }
    sorted
  }

  private def printSmart(x: Any): String = x match {
    case u: UGen  => u.name
    case _        => x.toString
  }

  @elidable(elidable.CONFIG) private def log(what: => String): Unit =
    if (showLog) println(s"ScalaCollider <ugen-graph> $what")

  private def buildControls(p: Iterable[ControlProxyLike]): Map[ControlProxyLike, (UGen, Int)] =
    p.groupBy(_.factory).flatMap { case (factory, proxies) =>
      factory.build(builder, proxies.toIndexedSeq)
    } (breakOut)
}