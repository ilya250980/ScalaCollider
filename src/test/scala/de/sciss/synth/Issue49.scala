package de.sciss.synth

trait Issue49 {
  import ugen._
  import Ops._

  def s: Server = Server.default

  play { SinOsc.ar }  // ok
  playWith(target = s, addAction = addToTail) { SinOsc.ar } // ok

  playWith(s) { SinOsc.ar } // ok
  playWith(target = s) { SinOsc.ar } // ok
}
