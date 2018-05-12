package snippets

trait Comparison {

import de.sciss.synth._
import ugen._
import Ops._

// #comparison
play {
  val o = LFSaw.kr(Seq(8, 7.23)).mulAdd(3, 80)
  val f = LFSaw.kr(0.4).mulAdd(24, o)
  val s = SinOsc.ar(f.midiCps) * 0.04
  CombN.ar(s, 0.2, 0.2, 4)
}
// #comparison

}
