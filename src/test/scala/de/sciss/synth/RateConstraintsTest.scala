package de.sciss.synth

object RateConstraintsTest extends App {
  Server.run { s =>
    import Ops._
    import ugen._
    val df = SynthDef("AnalogBubbles") {
      val f  = LFSaw.ar(0.4).mulAdd(24, LFSaw.ar(Seq(8.0, 7.9)).mulAdd(3, 80)).midiCps
      val x  = CombN.kr(SinOsc.ar(f) * 0.04, 0.2, 0.2, 4)
      Out.ar(0, x)  // should automatically insert K2A and not crash server
    }
    df.play()
    Thread.sleep(4000)
    s.quit()
    sys.exit()
  }
}
