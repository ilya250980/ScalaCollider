package de.sciss.synth

object TopoSort extends App {
  import Ops._
  import ugen._

  val m = 2
  val n = 2

  val df = SynthDef("scala") {
    Out.ar(0,
      GVerb.ar(
        Mix.tabulate(n) { k =>
          Mix.tabulate(m) { i =>
            val x = Impulse.kr(0.5.pow(i) / k)
            SinOsc.ar(i, SinOsc.ar((i + k).pow(i)) / (Decay.kr(x, Seq(i, i + 1)) * k))
          }
        },
        roomSize = 1) / 512
    )
  }

  val df2 = SynthDef("error") {
    val x = "x".ar
    Out.ar(0, x)
  }

  println(df2)
}