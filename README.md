# ScalaCollider

[![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Sciss/ScalaCollider?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/Sciss/ScalaCollider.svg?branch=main)](https://travis-ci.org/Sciss/ScalaCollider)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/de.sciss/scalacollider_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/de.sciss/scalacollider_2.13)
<a href="https://liberapay.com/sciss/donate"><img alt="Donate using Liberapay" src="https://liberapay.com/assets/widgets/donate.svg" height="24"></a>

## statement

ScalaCollider is a [SuperCollider](https://supercollider.github.io/) client for the Scala programming language. 
It is (C)opyright 2008&ndash;2020 by Hanns Holger Rutz. All rights reserved. ScalaCollider is released under
the [GNU Lesser General Public License](https://git.iem.at/sciss/ScalaCollider/raw/main/LICENSE) v2.1+ and comes
with absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`

SuperCollider is one of the most elaborate open source sound synthesis frameworks. It comes with its own language
'SCLang' that controls the sound synthesis processes on a server, 'scsynth'. ScalaCollider is an alternative to
'SCLang', giving you the (perhaps) familiar Scala language to express these sound synthesis processes, and letting
you hook up any other Scala, Java or JVM-based libraries. ScalaCollider's function is more reduced than 'SCLang',
focusing on UGen graphs and server-side resources such as buses and buffers. Other functionality is part of the 
standard Scala library, e.g. collections and GUI. Other functionality, such as plotting, MIDI, client-side 
sequencing (Pdefs, Routines, etc.) must be added through dedicated libraries (see section 'packages' below).

While ScalaCollider itself is in the form of a _library_ (although you can use it from the REPL with `sbt console`),
you may want to have a look at the [ScalaCollider-Swing](https://git.iem.at/sciss/ScalaColliderSwing) project that 
adds an easy-to-use standalone application or mini-IDE. On the ScalaCollider-Swing page, you'll find a link to
download a readily compiled binary for this standalone version.

__Note:__ An even more elaborate way to use ScalaCollider, is through [SoundProcesses](https://git.iem.at/sciss/SoundProcesses)
and its graphical front-end [Mellite](https://sciss.de/mellite).

Please consider supporting this project through Liberapay (see badge above) – thank you!

## download and resources

The current version of ScalaCollider (the library) can be downloaded
from [git.iem.at/sciss/ScalaCollider](https://git.iem.at/Sciss/ScalaCollider).

More information is available from the wiki
at [git.iem.at/sciss/ScalaCollider/wikis/](https://git.iem.at/sciss/ScalaCollider/wikis/). The API documentation is
available at [sciss.github.io/ScalaCollider/latest/api](http://sciss.github.io/ScalaCollider/latest/api/de/sciss/synth/index.html).

The best way to ask questions, no matter if newbie or expert, is to use the Gitter channel (see badge above)
or the mailing list
at [groups.google.com/group/scalacollider](http://groups.google.com/group/scalacollider). To subscribe, simply
send a mail to `ScalaCollider+subscribe@googlegroups.com` (you will receive a mail asking for confirmation).

The early architectural design of ScalaCollider is documented in the SuperCollider 2010 symposium proceedings:
[H.H.Rutz, Rethinking the SuperCollider Client...](http://cmr.soc.plymouth.ac.uk/publications/Rutz_SuperCollider2010.pdf).
However, many design decisions have been revised or refined in the meantime.

The file [ExampleCmd.sc](https://git.iem.at/sciss/ScalaCollider/blob/main/ExampleCmd.sc) is a good
starting point for understanding how UGen graphs are written in ScalaCollider. You can directly copy and paste these
examples into the ScalaCollider-Swing application's interpreter window.

See the section 'starting a SuperCollider server' below, for another simple example of running a server (possibly
from your own application code).

## building

ScalaCollider builds with [sbt](http://scala-sbt.org/) against Scala 2.13, 2.12. The last version to support
Scala 2.11 was 1.28.4.
ScalaCollider requires SuperCollider server to be installed and/or running. The recommended version as of
this writing is 3.10. Note that the UGens are provided by the
separate [ScalaColliderUGens](https://git.iem.at/sciss/ScalaColliderUGens) project. A simple Swing front end is
provided by the [ScalaColliderSwing](https://git.iem.at/sciss/ScalaColliderSwing) project.

Targets for sbt:

* `clean` &ndash; removes previous build artefacts
* `compile` &ndash; compiles classes into target/scala-version/classes
* `doc` &ndash; generates api in target/scala-version/api/index.html
* `package` &ndash; packages jar in target/scala-version
* `console` &ndash; opens a Scala REPL with ScalaCollider on the classpath

## linking

To use this project as a library, use the following artifact:

    libraryDependencies += "de.sciss" %% "scalacollider" % v

The current version `v` is `"1.28.6"`

## contributing

Please see the file [CONTRIBUTING.md](CONTRIBUTING.md)

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
  s.dumpOSC()
  // play is imported from package de.sciss.synth.
  // it provides a convenience method for wrapping
  // a synth graph function in an `Out` element
  // and playing it back.
  play {
    val f = LFSaw.kr(0.4).mulAdd(24, LFSaw.kr(Seq(8, 7.23)).mulAdd(3, 80)).midiCps
    CombN.ar(SinOsc.ar(f) * 0.04, 0.2, 0.2, 4)
  }
}    
```

For more sound examples, see `ExampleCmd.sc`. There is also an introductory video for
the [Swing frontend](https://git.iem.at/sciss/ScalaColliderSwing)
at [www.screencast.com/t/YjUwNDZjMT](http://www.screencast.com/t/YjUwNDZjMT), and some of
the [Mellite tutorials](https://www.sciss.de/mellite/tutorials.html) also introduce ScalaCollider concepts.

__Troubleshooting:__ If the above boots the server, but on Linux you do not 
hear any sound, probably the Jack audio server does not establish connections between
SuperCollider and your sound card. The easiest is to use a program such as QJackCtl
to automatically wire them up. Alternatively, you can set environment variables
`SC_JACK_DEFAULT_INPUTS` and `SC_JACK_DEFAULT_OUTPUTS` before starting Scala, e.g.

```bash
export SC_JACK_DEFAULT_INPUTS="system:capture_1,system:capture_2"
export SC_JACK_DEFAULT_OUTPUTS="system:playback_1,system:playback_2"
```

### Specifying SC_HOME

__Note__: This section is mostly irrelevant on Linux, where `scsynth` is normally found on `$PATH`, and thus no
further customisation is needed.

You might omit to set the `program` of the server's configuration, as ScalaCollider will by default read the
system property `SC_HOME`, and if that is not set, the environment variable `SC_HOME`. Environment variables are
stored depending on your operating system. On OS X, if you use the app-bundle of ScalaCollider-Swing, you can
access them from the terminal:

    $ mkdir ~/.MacOSX
    $ touch ~/.MacOSX/environment.plist
    $ open ~/.MacOSX/environment.plist

Here, `open` should launch the PropertyEditor. Otherwise you can edit this file using a text editor. The content
will be like this:

    {
      "SC_HOME" = "/Applications/SuperCollider_3.6.5/SuperCollider.app/Contents/Resources/";
    }

On the other hand, if you run ScalaCollider from a Bash terminal, you edit `~/.bash_profile` instead. The entry
is something like:

    export SC_HOME=/path/to/folder-of-scsynth

On linux, the environment variables probably go in `~/.profile` or `~/.bashrc`.

## packages

ScalaCollider's core functionality may be extended by other libraries I or other people wrote. The following three
libraries are dependencies and therefore always available in ScalaCollider:

- UGens are defined by the [ScalaCollider-UGens](https://git.iem.at/sciss/ScalaColliderUGens) library.
- Audio file functionality is provided by the [AudioFile](https://git.iem.at/sciss/AudioFile) library.
- Open Sound Control functionality is provided by the [ScalaOSC](https://git.iem.at/sciss/ScalaOSC) library.

Here are some examples for libraries not included:

- Patterns functionality is becoming available through the [Patterns](https://git.iem.at/sciss/Patterns) library.
  This is currently in experimental phase, with focus clearly on support in
  [SoundProcesses](https://git.iem.at/sciss/SoundProcesses) rather than vanilla ScalaCollider.
- MIDI functionality can be added with the [ScalaMIDI](https://git.iem.at/sciss/ScalaMIDI) library.
- Plotting is most easily achieved through [Scala-Chart](https://git.iem.at/sciss/scala-chart), which is 
  conveniently included in ScalaCollider-Swing.
