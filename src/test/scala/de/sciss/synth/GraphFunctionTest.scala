package de.sciss.synth

trait GraphFunctionTest {
  import Ops._
  import ugen._

  val x = graph {
    val f = LFSaw.kr(0.4).mulAdd(24, LFSaw.kr(List(8.0, 7.23)).mulAdd(3, 80)).midiCps
    CombN.ar(SinOsc.ar(f)*0.04, 0.2, 0.2, 4)
  }
}
