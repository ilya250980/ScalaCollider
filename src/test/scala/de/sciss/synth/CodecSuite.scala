package de.sciss.synth

import de.sciss.synth.message.ServerCodec
import java.nio.ByteBuffer

import de.sciss.osc
import org.scalatest.funspec.AnyFunSpec

/* To run only this test:

  test-only de.sciss.synth.CodecSuite

  */
class CodecSuite extends AnyFunSpec {
  describe("ServerCodec") {
    it("should encode and decode standard messages") {
      val b     = ByteBuffer.allocate(8192)
      val codec = ServerCodec

      def perform(p: osc.Packet): Unit = {
        b.clear()
        p match {
          case msg : osc.Message => codec.encodeMessage(msg , b)
          case bndl: osc.Bundle  => codec.encodeBundle (bndl, b)
        }
        b.flip()
        val trip = codec.decode(b)
        assert(trip === p)
      }

      // plain message without conversions (Double -> Float etc.) should come out 1:1
      perform(osc.Message("/test", 1, 2.3f))
      // specially decoded messages
      perform(message.NodeGo  (1234, message.NodeInfo.SynthData(56, 78, 90)))
      perform(message.NodeEnd (1234, message.NodeInfo.GroupData(56, 78, 90, 321, 653)))
      perform(message.NodeOn  (1234, message.NodeInfo.GroupData(56, 78, 90, 321, 653)))
      perform(message.NodeOff (1234, message.NodeInfo.SynthData(56, 78, 90)))
      perform(message.NodeMove(1234, message.NodeInfo.GroupData(56, 78, 90, 321, 653)))
      perform(message.NodeInfo(1234, message.NodeInfo.SynthData(56, 78, 90)))

      perform(message.Synced(-123))
      perform(message.Trigger(0x12345678, 0x9ABCDEF, 1625243f))

      perform(message.BufferSet (666, (1, 2f), (3, 4f), (5, -6f)))
      perform(message.BufferSetn(666, (1, Vector(2f, 3f)), (3, Vector(4f, 5f, 6f)), (5, Vector(-6f, -7f, -8f))))

      perform(message.ControlBusSet ((1, 2f), (3, 4f), (5, -6f)))
      perform(message.ControlBusSetn((1, Vector(2f, 3f)), (3, Vector(4f, 5f, 6f)), (5, Vector(-6f, -7f, -8f))))

      // this doesn't work because doubles are converted to floats in encoding
      // perform(message.StatusReply(1, 2, 3, 4, 5f, 6f, 7.0, 8.9))

      perform(message.BufferInfo(message.BufferInfo.Data(1, 2, 3, 4f), message.BufferInfo.Data(5, 6, -7, -8f)))
    }
  }
}