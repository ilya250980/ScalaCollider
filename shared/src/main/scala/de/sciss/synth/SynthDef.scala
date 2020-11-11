/*
 *  SynthDef.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Affero General Public License v3+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import java.io.File.{separator => sep}
import java.io.{ByteArrayOutputStream, DataInputStream, DataOutputStream, File, FileInputStream, FileOutputStream, InputStream, OutputStream}
import java.nio.ByteBuffer

import de.sciss.optional.Optional
import de.sciss.osc.Packet
import de.sciss.synth

object SynthDef {
  type Completion = synth.Completion[SynthDef]

  private final val COOKIE  = 0x53436766  // 'SCgf'

  /** The default directory for writing synth defs is the
    * temporary directory given by the system property `"java.io.tmpdir"`
    */
  var defaultDir: String  = sys.props("java.io.tmpdir")

  /** The `SynthDef` file name extension (without leading period). */
  final val extension     = "scsyndef"

  /** Creates a new `SynthDef` with a given name and a thunk parameter that
    * creates the expanded UGen graph structure.
    */
  def apply(name: String)(thunk: => Unit)
           (implicit factory: UGenGraph.BuilderFactory = impl.DefaultUGenGraphBuilderFactory): SynthDef =
    SynthDef(name, SynthGraph(thunk).expand)

  /** Writes a sequence of synth-definitions to a file specified by its `path`.
    *
    * @param path     path to the file that will be (over)written by this method
    * @param defs     sequences of synth-definitions to write
    * @param version  format version which must be 1 or 2. The default is
    *                 format 1 which is more compact. Format 2 allows
    *                 larger synth-defs but is basically an overkill,
    *                 unless you want to build a definition with more
    *                 than 65536 UGens.
    */
  def write(path: String, defs: Seq[SynthDef], version: Int = 1): Unit = {
    if (version != 1 && version != 2) throw new IllegalArgumentException(s"Illegal SynthDef version $version")

    val os = new FileOutputStream(path)
    try {
      write(os, defs, version = version)
    }
    finally {
      os.close()
    }
  }

  /** Writes a sequence of synth-definitions to a file specified by its `path`. */
  def write(os: OutputStream, defs: Seq[SynthDef], version: Int): Unit = {
    if (version != 1 && version != 2) throw new IllegalArgumentException(s"Illegal SynthDef version $version")

    val dos = new DataOutputStream(os) // new BufferedOutputStream(os))

    dos.writeInt(COOKIE)        // magic cookie
    dos.writeInt(version)       // version
    dos.writeShort(defs.size)   // number of defs in file.
    defs.foreach(_.write(dos, version = version))
    dos.flush()
  }

  def loadMsg   (path: String, completion: Optional[Packet] = None) = message.SynthDefLoad   (path, completion)
  def loadDirMsg(path: String, completion: Optional[Packet] = None) = message.SynthDefLoadDir(path, completion)

  /** Reads all synth-definitions contained in a file with standard binary format. */
  def read(path: String): List[SynthDef] = {
    val is = new FileInputStream(path)
    try {
      read(is)
    } finally {
      is.close()
    }
  }

  /** Reads all synth-definitions from an input stream with standard binary format. */
  def read(is: InputStream): List[SynthDef] = {
    val dis = new DataInputStream(is) // new BufferedInputStream(is))
    val cookie  = dis.readInt()
    require (cookie == COOKIE, s"SynthDef must begin with cookie word 0x${COOKIE.toHexString}")
    val version = dis.readInt()
    require (version == 1 || version == 2, s"SynthDef has unsupported version $version, required 1 or 2")
    val numDefs = dis.readShort()
    List.fill(numDefs)(read(dis, version = version))
  }

  @inline private[this] def readPascalString(dis: DataInputStream): String = {
    val len   = dis.readUnsignedByte()
    val arr   = new Array[Byte](len)
    dis.read(arr)
    new String(arr)
  }

  private[this] def read(dis: DataInputStream, version: Int): SynthDef = {
    val name  = readPascalString(dis)
    val graph = UGenGraph.read(dis, version = version)
    new SynthDef(name, graph)
  }
}
final case class SynthDef(name: String, graph: UGenGraph) {
  import SynthDef._

  override def toString = s"SynthDef($name)"

  /** A `d_free` message. */
  def freeMsg = message.SynthDefFree(name)

  /** A `d_recv` message without completion package. */
  def recvMsg: message.SynthDefRecv = recvMsg(None)

  /** A `d_recv` message.
    *
    * @param completion   optional completion package
    * @param version      file version, must be 1 or 2
    */
  def recvMsg(completion: Optional[Packet], version: Int = 1) =
    message.SynthDefRecv(toBytes(version), completion)

  /** Encodes the synth-definition into a byte-buffer
    * ready to be sent to the server or written to
    * a file.
    *
    * @return A newly allocated buffer filled with
    *         a fully contained synth-definition file
    *         (magic cookie header, following by this
    *         one definition). The buffer is read-only
    *         with position zero and limit set to the
    *         size of the buffer.
    */
  def toBytes(version: Int = 1): ByteBuffer = {
    val bos = new ByteArrayOutputStream
    val dos = new DataOutputStream(bos)

    import SynthDef.COOKIE

    dos.writeInt(COOKIE)        // magic cookie 'SCgf'
    dos.writeInt(version)       // version
    dos.writeShort(1)           // number of defs in file.
    write(dos, version = version)
    dos.close()

    ByteBuffer.wrap(bos.toByteArray).asReadOnlyBuffer()
  }

  private def write(dos: DataOutputStream, version: Int): Unit = {
    writePascalString(dos, name)
    graph.write(dos, version = version)
  }

  /** A `d_load` message with default directory location and without completion package.
    *
    * @see [[de.sciss.synth.SynthDef.defaultDir]]
    */
  def loadMsg: message.SynthDefLoad = loadMsg()

  /** A `d_load` message with an optional completion package.
    *
    * @param dir        the directory in which the synth-definition is found. The synth-definition
    *                   is assumed to be in a file with `name` and `extension`.
    *                   this must match with the `dir` parameter of the `write` method call.
    */
  def loadMsg(dir: String = defaultDir, completion: Optional[Packet] = None) =
    message.SynthDefLoad(s"$dir$sep$name.$extension", completion)

  /** Writes this synth-definition into a file. The file name
    * is the `name` of the definition followed by the default
    * `extension`.
    *
    * @param dir        the path of the directory in which the file will be created
    * @param overwrite  if `true` (default), an existing file will be overwritten,
    *                   if `false` if the file already exists, this method exists
    *                   without writing anything.
    */
  def write(dir: String = defaultDir, overwrite: Boolean = true): Unit = {
    val file = new File(dir, s"$name.$extension")
    if (file.exists) {
      if (overwrite) file.delete() else return
    }
    SynthDef.write(file.getAbsolutePath, this :: Nil)
  }

  @inline private def writePascalString(dos: DataOutputStream, str: String): Unit = {
    dos.writeByte(str.length)
    dos.write(str.getBytes)
  }

  /** Prints a hexadecimal dump of the encoded
    * synth def to the console output for debugging purposes.
    */
  def hexDump(version: Int = 1): Unit = Packet.printHexOn(toBytes(version), Console.out)

  private[synth] def testTopologicalSort(): Unit = {
    graph.ugens.zipWithIndex.foreach { case (ru, i) =>
      ru.inputSpecs.toList.zipWithIndex.foreach { case ((ref, _), j) =>
        if ((ref >= 0) && (ref <= i)) {
          sys.error(s"Test failed : ugen $i = ${ru.ugen} -> input $j = $ref")
        }
      }
    }
    println("Test succeeded.")
  }

  /** Prints a string representation of this definition to
    * the console output for debugging purposes.
    */
  def debugDump(): Unit = {
     graph.ugens.zipWithIndex.foreach { case (ru, i) =>
       val special  = if (ru.ugen.specialIndex != 0) s"-${ru.ugen.specialIndex}" else ""
       val specs    = ru.inputSpecs.map {
         case (-1, idx) => graph.constants(idx).toString
         case (uIdx, oIdx) =>
           val ru   = graph.ugens(uIdx)
           val oStr = if (oIdx > 0) "@" + oIdx else ""
           s"#$uIdx : ${ru.ugen.name}$oStr"
       } .mkString("( ", ", ", " )")
       println(s"#$i : ${ru.ugen.name}$special$specs")
     }
   }
}