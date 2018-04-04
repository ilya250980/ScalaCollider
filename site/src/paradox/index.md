# ScalaCollider

ScalaCollider is a real-time sound synthesis and signal processing system, based
on [SuperCollider](http://supercollider.sf.net) and the [Scala programming language](http://scala-lang.org/). It can
be used as a _library_ in a Scala application, but there is also a small stand-alone _prototyping environment_ called
ScalaCollider-Swing.

SuperCollider is one of the most elaborate open source sound synthesis frameworks. It comes with its own language,
sclang, that controls the sound synthesis processes on a server, scsynth. ScalaCollider is an alternative to sclang,
giving you the (perhaps) familiar Scala language to express these sound synthesis processes, and letting you hook up
any other Scala, Java or JVM-based libraries.

ScalaCollider
:   @@snip [Comparison.scala](/../../snippets/src/main/scala/Comparison.scala) { #comparison }

sclang
:   @@snip [Comparison.scd](/../../snippets/src/main/supercollider/Comparison.scd) { #comparison }

ScalaCollider's function is more reduced than sclang's, focusing on UGen graphs and server-side resources such as
buses and buffers. Other functionality is part of the standard Scala library, e.g. collections and GUI. Other
functionality, such as plotting, MIDI, client-side sequencing (Pdefs, Routines, etc.) must be added through
dedicated libraries. The documentation on this site assumes some familiarity with SuperCollider, and will do its
best to help users coming from SuperCollider to understand the basic concepts of Scala and ScalaCollider.

## download

To start hacking straight away, download
the [latest version](https://github.com/Sciss/ScalaColliderSwing/releases/latest) of ScalaCollider-Swing:

- @extref[All Platforms](swingdl:_universal.zip)
- @extref[Debian Package](swingdl:_all.deb)

If you want to build from the source code, go
to [github.com/Sciss/ScalaCollider](http://github.com/Sciss/ScalaCollider).

In order to run ScalaCollider, you also need to have installed on your computer:

- [Java](https://www.java.com/download/) (Java 8 is recommended, but Java 6 should suffice) 
- [SuperCollider](https://supercollider.github.io/download) (version 3.9.1 is recommended, but 3.6.x and up
  should work, too)

## resources

The best way to ask questions, no matter if newbie or expert, is to use one of the following channels:

- the mailing list at [groups.google.com/group/scalacollider](http://groups.google.com/group/scalacollider). To
  subscribe, simply send a mail to `ScalaCollider+subscribe@googlegroups.com` (you will receive a mail asking for
  confirmation).
- the chat channel at [gitter.im/Sciss/ScalaCollider](https://gitter.im/Sciss/ScalaCollider). You need a GitHub
  or Twitter account to sign in (you can create a free GitHub account).

The file [ExampleCmd.sc](https://raw.githubusercontent.com/Sciss/ScalaCollider/master/ExampleCmd.sc) is a good
starting point for understanding how UGen graphs are written in ScalaCollider. You can directly copy and paste
these examples into the ScalaCollider-Swing prototyping environment. If you start it for the first time, you may
have to adjust the location of the `scsynth` or `scsynth.exe` program in the preferences. Press the boot button to
fire up the SuperCollider server. Then, select an example and press <kbd>Shift</kbd>+<kbd>Return</kbd> to execute.
Hover over a UGen name (e.g. `Mix` or `SinOsc`) and press <kbd>Ctrl</kbd>+<kbd>D</kbd> to open its help file.
