# ScalaCollider

ScalaCollider is a real-time sound synthesis and signal processing system, based on [SuperCollider](http://supercollider.sf.net) and the [Scala programming language](http://scala-lang.org/). It can be used as a _library_ in a Scala application, but there is also a small stand-alone _prototyping environment_ called ScalaCollider-Swing.

SuperCollider is one of the most elaborate open source sound synthesis frameworks. It comes with its own language, sclang, that controls the sound synthesis processes on a server, scsynth. ScalaCollider is an alternative to sclang, giving you the (perhaps) familiar Scala language to express these sound synthesis processes, and letting you hook up any other Scala, Java or JVM-based libraries.

ScalaCollider
:   @@snip [Comparison.scala](/../../snippets/src/main/scala/Comparison.scala) { #comparison }

sclang
:   @@snip [Comparison.scd](/../../snippets/src/main/supercollider/Comparison.scd) { #comparison }

ScalaCollider's function is more reduced than sclang's, focusing on UGen graphs and server-side resources such as buses and buffers. Other functionality is part of the standard Scala library, e.g. collections and GUI. Other functionality, such as plotting, MIDI, client-side sequencing (Pdefs, Routines, etc.) must be added through dedicated libraries. The documentation on this site assumes some familiarity with SuperCollider, and will do its best to help users coming from SuperCollider to understand the basic concepts of Scala and ScalaCollider.

## download

To start hacking straight away, download the latest version @var[swingversion] of ScalaCollider-Swing:

- [All Platforms](https://github.com/Sciss/ScalaColliderSwing/releases/download/v@var:[scalacollider-swing.version]/scalacolliderswing-1.31.0-universal.zip)
- [Debian Package](https://github.com/Sciss/ScalaColliderSwing/releases/download/v1.31.0/ScalaCollider_1.31.0_all.deb)

If you want to build from the source code, go to [github.com/Sciss/ScalaCollider](http://github.com/Sciss/ScalaCollider).

## resources

The best way to ask questions, no matter if newbie or expert, is to use the mailing list at [groups.google.com/group/scalacollider](http://groups.google.com/group/scalacollider). To subscribe, simply send a mail to `ScalaCollider+subscribe@googlegroups.com` (you will receive a mail asking for confirmation).

The early architectural design of ScalaCollider is documented in the SuperCollider 2010 symposium proceedings: [H.H.Rutz, Rethinking the SuperCollider Client...](http://cmr.soc.plymouth.ac.uk/publications/Rutz_SuperCollider2010.pdf). However, many design decisions have been revised or refined in the meantime.

The file [ExampleCmd.sc](https://raw.githubusercontent.com/Sciss/ScalaCollider/master/ExampleCmd.sc) is a good starting point for understanding how UGen graphs are written in ScalaCollider. You can directly copy and paste these examples into the ScalaCollider-Swing application's interpreter window.

See the section 'starting a SuperCollider server' below, for another simple example of running a server (possibly from your own application code).

## starting a SuperCollider server

The following short example illustrates how a server can be launched and a synth played:

```scala
import de.sciss.synth._
import ugen._
import Ops._

val cfg = Server.Config()
cfg.program = "/path/to/scsynth"
// runs a server and executes the function
// when the server is booted, with the
// server as its argument 
Server.run(cfg) { s =>
  // play is imported from package de.sciss.synth.
  // it provides a convenience method for wrapping
  // a synth graph function in an `Out` element
  // and playing it back.
  play {
    val f = LFSaw.kr(0.4).madd(24, LFSaw.kr(Seq(8, 7.23)).madd(3, 80)).midicps
    CombN.ar(SinOsc.ar(f) * 0.04, 0.2, 0.2, 4)
  }
}    
```

For more sound examples, see `ExampleCmd.sc`. There is also an introductory video for the [Swing frontend](http://github.com/Sciss/ScalaColliderSwing) at [www.screencast.com/t/YjUwNDZjMT](http://www.screencast.com/t/YjUwNDZjMT).

