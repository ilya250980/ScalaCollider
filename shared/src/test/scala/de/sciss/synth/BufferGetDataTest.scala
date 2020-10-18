package de.sciss.synth

import java.io.File

object BufferGetDataTest extends App {
  import Ops._

  Server.run() { s =>
    import s.clientConfig.executionContext
    val pre1  = "/usr/share/SuperCollider"
    val pre2  = "/usr/local/share/SuperCollider"
    val pre   = if (new File(pre1).isDirectory) pre1 else pre2
    val path  = pre + "/sounds/a11wlk01-44_1.aiff"
    val b     = Buffer(s)
    // s.dumpOSC()
    for {
      _ <- b.allocRead(path)
      _ <- b.getData()
    } {
      println("Data transfer OK.")
      s.quit()
    }
  }
}
