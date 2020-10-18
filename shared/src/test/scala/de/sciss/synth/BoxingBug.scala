package de.sciss.synth

object BoxingBug extends App {
  val s   = Server.dummy()
  val msg = s.syncMsg()
  val id  = msg.id
  println(s"Result: $id")
}