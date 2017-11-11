package de.sciss.synth

import org.scalatest.FunSpec

/*
  To run only this test:

  test-only de.sciss.synth.LocalBufSuite

 */
class LocalBufSuite extends FunSpec {
  describe("LocalBuf and MaxLocalBufs UGens") {
    it("should properly expand") {
      val df = SynthDef("test") {
        import ugen._
        val buffers = Seq.fill(2)(LocalBuf(2048))
        Out.kr(0, buffers)
      }

      val out   = df.graph.ugens.map { ru =>
        ru.ugen.name -> ru.inputSpecs.map {
          case (-1, cIdx)   => df.graph.constants(cIdx)
          case (uIdx, oIdx) => "U"
        }
      }

      assert(out === Vector(
        "MaxLocalBufs" -> Vector(2),
        "LocalBuf"     -> Vector(1, 2048, 0),
        "LocalBuf"     -> Vector(1, 2048, 1),
        "Out"          -> Vector(0, "U", "U")
      ))
    }
  }
}