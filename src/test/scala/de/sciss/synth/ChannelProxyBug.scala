//package de.sciss.synth
//
//import at.iem.scalacollider.ScalaColliderDOT
//import de.sciss.synth.ugen.{GESeq, UGenInGroup, UGenInSeq}
//
//final case class MyChannelProxy(elem: GE, index: Int) extends GE.Lazy {
//  def rate = elem.rate
//
//  override def toString = s"$elem.\\($index)"
//
//  // XXX TODO --- make `protected` in next major revision
//  def makeUGens: UGenInLike = {
//    val test: UGenInLike = elem match {
//      case in: UGenIn => in
//      case GESeq    (xs) => xs(index % xs.length)
//      case UGenInSeq(xs) => xs(index % xs.length)
//      case other =>
//        val _elem = elem.expand
//        val out = _elem.outputs
//        val map = out.map(_.expand)
//        val outF = _elem.flatOutputs
//        UGenInGroup(Vector.tabulate(outF.size / out.size)(i => outF((index + i * out.size) % outF.size)))
//    }
//
//    val _elem = elem.expand
//    val out = _elem.outputs
//    val map = out.map(_.expand)
//    val outF = _elem.flatOutputs
//    val un = _elem.unbubble
//    val res1 = out(index % out.length)
//    val res = _elem.unwrap(index)
//    val b = res == res1
//    res
//  }
//}
//
//object ChannelProxyBug extends App {
//  val df = SynthDef("bug") {
//    import ugen._
//    val in  = In.ar(0, 2)
//    val hlb = Hilbert.ar(in)
//    val hlb2 = Seq(SinOsc.ar(Seq(100, 200)), LFSaw.ar(Seq(300, 400))) // hlb * Seq(1.1, 1.1)
//    val out = MyChannelProxy(in, 1) + MyChannelProxy(hlb, 1) + MyChannelProxy(hlb2, 1) +
//      MyChannelProxy(Flatten(hlb2), 1)
////    val out = MyChannelProxy(in, 1)
//    Out.ar(0, Flatten(out))
//  }
//  df.debugDump()
//  val cfg = ScalaColliderDOT.Config()
//  cfg.input = df.graph
//  val dot = ScalaColliderDOT(cfg)
//  println(dot)
//}
