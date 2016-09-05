package de.sciss.synth

object Serialization extends App {
  import java.io._
  import ugen._
  import Ops._

  println("Start")
  val in = SynthGraph {
//       val f1 = "freq1".kr( 0.4 )
//       val f2 = "freq2".kr( 8 )
//       val d  = "detune".kr( 0.90375 )
//       val f = LFSaw.ar( f1 ).madd( 24, LFSaw.ar( List( f2, f2 * d )).madd( 3, 80 )).midicps
//       val x = CombN.ar( SinOsc.ar( f ) * 0.04, 0.2, 0.2, 4 )
//       Out.ar( 0, x )
    val f = "freq".kr(50f) // fundamental frequency
    val p = 20 // number of partials per channel
    val m = Mix.tabulate(p) { i =>
      FSinOsc.ar(f * (i + 1)) *
        (LFNoise1.kr(Seq(Rand(2, 10), Rand(2, 10))) * 0.02).max(0)
    }
    Out.ar(0, m)
  }
  val baos  = new ByteArrayOutputStream()
  val oos   = new ObjectOutputStream(baos)
  oos.writeObject(in)
  val bais  = new ByteArrayInputStream(baos.toByteArray)
  val ois   = new ObjectInputStream(bais)
  val out   = ois.readObject.asInstanceOf[SynthGraph]
  println("Ok")
}
