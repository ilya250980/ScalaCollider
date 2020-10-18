/*
 *  Server.scala
 *  (ScalaCollider)
 *
 *  Copyright (c) 2008-2019 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU Lesser General Public License v2.1+
 *
 *
 *  For further information, please contact Hanns Holger Rutz at
 *  contact@sciss.de
 */

package de.sciss.synth

import java.io.File
import java.net.{DatagramSocket, InetAddress, InetSocketAddress, ServerSocket}

import de.sciss.model.Model
import de.sciss.osc
import de.sciss.osc.{TCP, UDP}
import de.sciss.processor.Processor
import de.sciss.synth.impl.ServerImpl
import de.sciss.synth.io.{AudioFileType, SampleFormat}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.Try

object Server {
  def default: Server = ServerImpl.default

  /** The default file path to `scsynth`. If the runtime (system) property `"SC_HOME"` is provided,
    * this specifies the directory of `scsynth`. Otherwise, an environment (shell) variable named
    * `"SC_HOME"` is checked. If neither exists, this returns `scsynth` in the current working directory.
    */
  def defaultProgram: String = sys.props.get("SC_HOME").orElse(sys.env.get("SC_HOME")).fold {
    "scsynth"
  } {
    home => new File(home, "scsynth").getPath
  }

  /** The base trait for `Config` and `ConfigBuilder` describes the settings used to boot scsynth in
    * realtime or non-realtime mode, as well as its server address and port.
    *
    * You obtain a `ConfigBuilder` by calling `Server.Config()`. This builder can then be mutated and
    * will be implicitly converted to an immutable `Config` when required.
    *
    * See `ConfigBuilder` for its default values.
    *
    * @see [[de.sciss.synth.Server.ConfigBuilder]]
    * @see [[de.sciss.synth.Server.Config]]
    */
  trait ConfigLike {
    /** The path to `scsynth`, used when booting a server. This can be either a relative path
      * (relating to the JVM's working directory), or an absolute path.
      *
      * @see [[de.sciss.synth.Server#defaultProgram]]
      */
    def program: String

    /** The maximum number of control bus channels. */
    def controlBusChannels: Int

    /** The maximum number of audio bus channels. This includes the channels connected
      * to hardware (`outputBusChannels`) as well as all channels for internal routing.
      */
    def audioBusChannels: Int

    /** The number of connected audio hardware output channels. This does not need to
      * correspond to the actual number of channels your sound card provides, but can
      * be lower or higher, although a higher value doesn't have any effect as channel
      * indices above the number of channels of the sound card will be treated as
      * internal channels.
      */
    def outputBusChannels: Int

    /** The calculation block size. That is, the number of audio samples calculated en-bloc.
      * This corresponds with the control rate, such that
      * `controlRate := audioRate / blockSize`. It should be a power of two.
      */
    def blockSize: Int

    /** The audio hardware sampling rate to use. A value of `0` indicates that scsynth
      * should use the current sampling rate of the audio hardware. An explicit setting
      * will make scsynth try to switch the sound card's sample rate if necessary.
      */
    def sampleRate: Int

    /** The maximum number of audio buffers (for the `Buffer` class). */
    def audioBuffers: Int

    /** The maximum number of concurrent nodes (synths and groups). */
    def maxNodes: Int

    /** The maximum number of synth defs. */
    def maxSynthDefs: Int

    /** The maximum number of pre-allocated realtime memory in bytes. This memory
      * is used for many UGens such as `Limiter`, `DelayN` etc. It does not
      * affect dynamically allocated memory such as audio buffers.
      */
    def memorySize: Int

    /** The maximum number of concurrent connections between UGens in a single synth.
      * ScalaCollider performs a depth-first topological sorting of the synth defs,
      * so you should not worry too much about this value. It can become important
      * in very heavy channel expansions and mix-down.
      *
      * This value will be automatically increased if a more complex def is loaded
      * at startup, but it cannot be increased thereafter without rebooting.
      */
    def wireBuffers: Int

    /** The number of individual random number generators allocated. */
    def randomSeeds: Int

    /** Whether scsynth should load synth definitions stored on the hard-disk when booted. */
    def loadSynthDefs: Boolean

    /** ? */
    def machPortName: Option[(String, String)]

    /** The verbosity level of scsynth. The standard value is `0`, while
      * `-1` suppresses informational messages, `-2` also suppresses many error messages.
      */
    def verbosity: Int

    /** An explicit list of paths where DSP plugins are found. Usually this is not
      * specified, and scsynth looks for plugins in their default location.
      */
    def plugInsPaths: List[String]

    /** An option to restrict access to files (e.g. for loading and saving buffers) to
      * a particular directory. This is a security measure, preventing malicious clients from
      * accessing parts of the hard-disk which they shouldn't.
      */
    def restrictedPath: Option[String]

    // ---- realtime only ----

    /** (Realtime) Host address of scsynth, when trying to `connect` to an already running server on the net. */
    def host: String

    /** (Realtime) UDP or TCP port used by scsynth. */
    def port: Int

    /** (Realtime) Open Sound Control transport used by scsynth. (Either of `UDP` and `TCP`). */
    def transport: osc.Transport.Net

    /** (Realtime) An option to enable particular input 'streams' or 'bundles' of a sound card.
      * This is a 'binary' String made of `'0'` and `'1'` characters.
      * If the string is `"01100"`, for example, then only the second and third input streams on
      * the device will be enabled.
      */
    def inputStreamsEnabled: Option[String]

    /** (Realtime) An option to enable particular output 'streams' or 'bundles' of a sound card.
      * This is a 'binary' String made of `'0'` and `'1'` characters.
      * If the string is `"01100"`, for example, then only the second and third output streams on
      * the device will be enabled.
      */
    def outputStreamsEnabled: Option[String]

    /** (Realtime) An option denoting the name of the sound card to use. On systems which distinguish
      * input and output devices (OS X), this implies that both are the same. Otherwise, you can
      * use the `deviceNames` method instead.
      *
      * @see deviceNames
      */
    def deviceName: Option[String]

    /** (Realtime) An option denoting the name of the input and output sound device to use. This is for
      * systems which distinguish input and output devices (OS X). If you use a single device both for
      * input and output (applies to most professional audio interfaces), you can simply use the
      * single string method `deviceName`.
      *
      * @see deviceName
      */
    def deviceNames: Option[(String, String)]

    /** (Realtime) The number of connected audio hardware input channels. This does not need to
      * correspond to the actual number of channels your sound card provides, but can
      * be lower or higher, although a higher value doesn't have any effect as channel
      * indices above the number of channels of the sound card will be treated as
      * internal channels.
      */
    def inputBusChannels: Int

    /** (Realtime) A value to adjust the sound card's hardware block size. Typically you will leave
      * this to `0` which means that the current block size is used. The block sizes supported depend
      * on the particular sound card. Lower values decrease latency but may increase CPU load.
      */
    def hardwareBlockSize: Int

    /** (Realtime) Whether to announce scsynth's OSC service via zero conf. See
      * [[http://en.wikipedia.org/wiki/Zero_configuration_networking Wikipedia]] for more details.
      */
    def zeroConf: Boolean

    /** (Realtime) The maximum number of client connections when using TCP transport. */
    def maxLogins: Int

    /** (Realtime) A requires session password when using TCP transport. When using TCP and the password option
      * is set, each client must send the correct password as the first command to the server, otherwise it is
      * rejected.
      */
    def sessionPassword: Option[String]

    // ---- non-realtime only ----

    /** (Non-Realtime) Path to the binary OSC file. */
    def nrtCommandPath: String

    /** (Non-Realtime) Path to the audio input file used as audio input bus supplement. */
    def nrtInputPath: Option[String]

    /** (Non-Realtime) Path to the audio output file used as audio output bus supplement. */
    def nrtOutputPath: String

    /** (Non-Realtime) Audio file format for writing the output. */
    def nrtHeaderFormat: AudioFileType

    /** (Non-Realtime) Audio sample format for writing the output. */
    def nrtSampleFormat: SampleFormat

    /** Produces a command line for booting scsynth in realtime mode. */
    final def toRealtimeArgs: List[String] = Config.toRealtimeArgs(this)

    /** Produces a command line for booting scsynth in non-realtime mode. */
    final def toNonRealtimeArgs: List[String] = Config.toNonRealtimeArgs(this)

    /** A utility method providing the audio bus offset for the start of
      * the internal channels. (simply the sum of `outputBusChannels` and `inputBusChannels`).
      */
    final def internalBusIndex: Int = outputBusChannels + inputBusChannels
  }

  object Config {
    /** Creates a new configuration builder with default settings */
    def apply(): ConfigBuilder = new ConfigBuilder()

    /** Implicit conversion which allows you to use a `ConfigBuilder`
      * wherever a `Config` is required.
      */
    implicit def build(cb: ConfigBuilder): Config = cb.build

    private[Server] def toNonRealtimeArgs(o: ConfigLike): List[String] = {
      val b = List.newBuilder[String]

      // -N <cmd-filename> <input-filename> <output-filename> <sample-rate> <header-format> <sample-format> <...other scsynth arguments>
      b += o.program
      b += "-N"
      b += o.nrtCommandPath
      b += o.nrtInputPath.getOrElse("_")
      b += o.nrtOutputPath
      b += o.sampleRate.toString
      b += o.nrtHeaderFormat.id
      b += o.nrtSampleFormat.id

      addCommonArgs( o, b )
      b.result()
    }

    private[Server] def toRealtimeArgs(o: ConfigLike): List[String] = {
      val b = List.newBuilder[String]

      b += o.program
      o.transport match {
        case TCP =>
          b += "-t"
          b += o.port.toString
        case UDP =>
          b += "-u"
          b += o.port.toString
      }
      if (o.host != "0.0.0.0") {
        b += "-B"
        b += o.host
      }

      addCommonArgs(o, b)

      if (o.hardwareBlockSize != 0) {
        b += "-Z"
        b += o.hardwareBlockSize.toString
      }
      if (o.sampleRate != 0) {
        b += "-S"
        b += o.sampleRate.toString
      }
      if (o.maxLogins != 64) {
        b += "-l"
        b += o.maxLogins.toString
      }
      o.sessionPassword.foreach { pwd =>
        b += "-p"
        b += pwd
      }
      o.inputStreamsEnabled.foreach { stream =>
        b += "-I"
        b += stream
      }
      o.outputStreamsEnabled.foreach { stream =>
        b += "-O"
        b += stream
      }
      if (!o.zeroConf) {
        b += "-R"
        b += "0"
      }
      o.deviceNames.foreach { case (inDev, outDev) =>
        b += "-H"
        b += inDev
        b += outDev
      }
      o.deviceName.foreach { n =>
        b += "-H"
        b += n
      }
      o.restrictedPath.foreach { path =>
        b += "-P"
        b += path
      }

      b.result()
    }

    private[Server] def addCommonArgs(o: ConfigLike, b: mutable.Builder[String, Any]): Unit = {
      // some dude is going around changing scsynth
      // defaults without thinking about the consequences.
      // we now pessimistically pass all options and do
      // not assume any longer defaults.

      // if (o.controlBusChannels != 4096) {
      b += "-c"
      b += o.controlBusChannels.toString
      // }
      // if (o.audioBusChannels != 128) {
      b += "-a"
      b += o.audioBusChannels.toString
      // }
      // if (o.inputBusChannels != 8) {
      b += "-i"
      b += o.inputBusChannels.toString
      // }
      // if (o.outputBusChannels != 8) {
      b += "-o"
      b += o.outputBusChannels.toString
      // }
      // if (o.blockSize != 64) {
      b += "-z"
      b += o.blockSize.toString
      // }
      // if (o.audioBuffers != 1024) {
      b += "-b"
      b += o.audioBuffers.toString
      // }
      // if (o.maxNodes != 1024) {
      b += "-n"
      b += o.maxNodes.toString
      // }
      // if (o.maxSynthDefs != 1024) {
      b += "-d"
      b += o.maxSynthDefs.toString
      // }
      // if (o.memorySize != 8192) {
      b += "-m"
      b += o.memorySize.toString
      // }
      // if (o.wireBuffers != 64) {
      b += "-w"
      b += o.wireBuffers.toString
      // }
      // if (o.randomSeeds != 64) {
      b += "-r"
      b += o.randomSeeds.toString
      // }
      if (!o.loadSynthDefs) {
        b += "-D"
        b += "0"
      }
      o.machPortName.foreach {
        case (send, reply) =>
          b += "-M"
          b += send
          b += reply
      }
      if (o.verbosity != 0) {
        b += "-v"
        b += o.verbosity.toString
      }
      if (o.plugInsPaths.nonEmpty) {
        b += "-U"
        b += o.plugInsPaths.mkString(":")
      }
    }
  }

  /** @see [[de.sciss.synth.Server.ConfigBuilder]]
    * @see [[de.sciss.synth.Server.ConfigLike]]
    */
  final class Config private[Server](val program: String,
                                     val controlBusChannels: Int,
                                     val audioBusChannels: Int,
                                     val outputBusChannels: Int,
                                     val blockSize: Int,
                                     val sampleRate: Int,
                                     val audioBuffers: Int,
                                     val maxNodes: Int,
                                     val maxSynthDefs: Int,
                                     val memorySize: Int,
                                     val wireBuffers: Int,
                                     val randomSeeds: Int,
                                     val loadSynthDefs: Boolean,
                                     val machPortName: Option[(String, String)],
                                     val verbosity: Int,
                                     val plugInsPaths: List[String],
                                     val restrictedPath: Option[String],
                                     /* val memoryLocking: Boolean, */
                                     val host: String,
                                     val port: Int,
                                     val transport: osc.Transport.Net,
                                     val inputStreamsEnabled: Option[String],
                                     val outputStreamsEnabled: Option[String],
                                     val deviceNames: Option[(String, String)],
                                     val deviceName: Option[String],
                                     val inputBusChannels: Int,
                                     val hardwareBlockSize: Int,
                                     val zeroConf: Boolean,
                                     val maxLogins: Int,
                                     val sessionPassword: Option[String],
                                     val nrtCommandPath: String,
                                     val nrtInputPath: Option[String],
                                     val nrtOutputPath: String,
                                     val nrtHeaderFormat: AudioFileType,
                                     val nrtSampleFormat: SampleFormat)
    extends ConfigLike {
    override def toString = "Server.Config"
  }

  object ConfigBuilder {
    def apply(config: Config): ConfigBuilder = {
      val b = new ConfigBuilder
      b.read(config)
      b
    }
  }
  /** @see [[de.sciss.synth.Server.Config]]
    * @see [[de.sciss.synth.Server.ConfigLike]]
    */
  final class ConfigBuilder private[Server]() extends ConfigLike {
    /** The default `program` is read from `defaultProgram`
      *
      * @see [[de.sciss.synth.Server#defaultProgram]]
      */
    var program: String = defaultProgram

    private[this] var controlBusChannelsVar = 4096

    /** The default number of control bus channels is `4096` (scsynth default).
      * Must be greater than zero and a power of two.
      */
    def controlBusChannels: Int = controlBusChannelsVar
    /** The default number of control bus channels is `4096` (scsynth default).
      * Must be greater than zero and a power of two.
      */
    def controlBusChannels_=(value: Int): Unit = {
      require (value > 0 && value.isPowerOfTwo)
      controlBusChannelsVar = value
    }

    private[this] var audioBusChannelsVar = 128

    /** The default number of audio bus channels is `128` (scsynth default).
      * Must be greater than zero and a power of two.
      * When the builder is converted to a `Config`, this value may be increased
      * to ensure that `audioBusChannels > inputBusChannels + outputBusChannels`.
      */
    def audioBusChannels: Int = audioBusChannelsVar
    /** The default number of audio bus channels is `128` (scsynth default).
      * Must be greater than zero and a power of two.
      * When the builder is converted to a `Config`, this value may be increased
      * to ensure that `audioBusChannels > inputBusChannels + outputBusChannels`.
      */
    def audioBusChannels_=(value: Int): Unit = {
      require (value > 0 && value.isPowerOfTwo)
      audioBusChannelsVar = value
    }

    private[this] var outputBusChannelsVar = 8

    /** The default number of output bus channels is `8` (scsynth default) */
    def outputBusChannels: Int = outputBusChannelsVar
    /** The default number of output bus channels is `8` (scsynth default) */
    def outputBusChannels_=(value: Int): Unit = {
      require (value >= 0)
      outputBusChannelsVar = value
    }

    private[this] var blockSizeVar = 64

    /** The default calculation block size is `64` (scsynth default).
      * Must be greater than zero and a power of two.
      */
    def blockSize: Int = blockSizeVar
    /** The default calculation block size is `64` (scsynth default).
      * Must be greater than zero and a power of two.
      */
    def blockSize_=(value: Int): Unit = {
      require (value > 0 && value.isPowerOfTwo)
      blockSizeVar = value
    }

    private[this] var sampleRateVar = 0

    /** The default sample rate is `0` (meaning that it is adjusted to
      * the sound card's current rate; scsynth default)
      */
    def sampleRate: Int = sampleRateVar
    /** The default sample rate is `0` (meaning that it is adjusted to
      * the sound card's current rate; scsynth default)
      */
    def sampleRate_=(value: Int): Unit = {
      require (value >= 0)
      sampleRateVar = value
    }

    private[this] var audioBuffersVar = 1024

    /** The default number of audio buffers is `1024` (scsynth default).
      * Must be greater than zero and a power of two.
      */
    def audioBuffers: Int = audioBuffersVar
    /** The default number of audio buffers is `1024` (scsynth default).
      * Must be greater than zero and a power of two.
      */
    def audioBuffers_=(value: Int): Unit = {
      require (value > 0 && value.isPowerOfTwo)
      audioBuffersVar = value
    }

    /** The default maximum number of nodes is `1024` (scsynth default) */
    var maxNodes: Int = 1024

    /** The default maximum number of synth defs is `1024` (scsynth default) */
    var maxSynthDefs: Int = 1024

    /** The default memory size is `65536` (64 KB) (higher than scsynth's default of 8 KB) */
    var memorySize: Int = 65536 // 8192

    /** The default number of wire buffers is `256` (higher than scsynth's default of `64`). */
    var wireBuffers: Int = 256 // 64

    /** The default number of random number generators is `64` (scsynth default) */
    var randomSeeds: Int = 64

    /** The default setting for loading synth defs is `false` (this is not the scsynth default!) */
    var loadSynthDefs: Boolean = false

    /** The default settings for mach port name is `None` (scsynth default) */
    var machPortName: Option[(String, String)] = None

    /** The default verbosity level is `0` (scsynth default) */
    var verbosity: Int = 0

    /** The default setting for plugin path redirection is `Nil`
      * (use standard paths; scsynth default)
      */
    var plugInsPaths: List[String] = Nil

    /** The default setting for restricting file access is `None` (scsynth default) */
    var restrictedPath: Option[String] = None

    // ---- realtime only ----

    /** (Realtime) The default host name is `127.0.0.1`. When booting, this is used
      * to force scsynth to bind to a particular address (`-B` switch). To avoid the `-B`
      * switch, you can use `"0.0.0.0"` (server will be reachable via network).
      */
    var host: String = "127.0.0.1"

    /** (Realtime) The default port is `57110`. */
    var port: Int = 57110

    /** (Realtime) The default transport is `UDP`. */
    var transport: osc.Transport.Net = UDP

    /** (Realtime) The default settings for enabled input streams is `None` */
    var inputStreamsEnabled: Option[String] = None

    /** (Realtime) The default settings for enabled output streams is `None` */
    var outputStreamsEnabled: Option[String] = None

    private[this] var deviceNameVar  = Option.empty[String]
    private[this] var deviceNamesVar = Option.empty[(String, String)]

    /** (Realtime) The default input/output device names is `None` (scsynth default; it will
      * use the system default sound card)
      */
    def deviceName: Option[String] = deviceNameVar
    /** (Realtime) The default input/output device names is `None` (scsynth default; it will
      * use the system default sound card)
      */
    def deviceName_=(value: Option[String]): Unit = {
      deviceNameVar = value
      if (value.isDefined) deviceNamesVar = None
    }

    /** (Realtime) The default input/output device names is `None` (scsynth default; it will
      * use the system default sound card)
      */
    def deviceNames: Option[(String, String)] = deviceNamesVar
    /** (Realtime) The default input/output device names is `None` (scsynth default; it will
      * use the system default sound card)
      */
    def deviceNames_=(value: Option[(String, String)]): Unit = {
      deviceNamesVar = value
      if (value.isDefined) deviceNameVar = None
    }

    private[this] var inputBusChannelsVar = 8

    /** (Realtime) The default number of input bus channels is `8` (scsynth default) */
    def inputBusChannels: Int = inputBusChannelsVar
    /** (Realtime) The default number of input bus channels is `8` (scsynth default) */
    def inputBusChannels_=(value: Int): Unit = {
      require (value >= 0)
      inputBusChannelsVar = value
    }

    /** (Realtime) The default setting for hardware block size is `0` (meaning that
      * scsynth uses the hardware's current block size; scsynth default)
      */
    var hardwareBlockSize: Int = 0

    /** (Realtime) The default setting for zero-conf is `false` (other than
      * scsynth's default which is `true`)
      */
    var zeroConf: Boolean = false

    /** (Realtime) The maximum number of TCP clients is `64` (scsynth default) */
    var maxLogins: Int = 64

    /** (Realtime) The default TCP session password is `None` */
    var sessionPassword: Option[String] = None

    // ---- non-realtime only ----

    var nrtCommandPath  : String          = ""
    var nrtInputPath    : Option[String]  = None
    var nrtOutputPath   : String          = ""
    var nrtHeaderFormat : AudioFileType   = AudioFileType.AIFF
    var nrtSampleFormat : SampleFormat    = SampleFormat.Float

    /** Picks and assigns a random free port for the server. This implies that
      * the server will be running on the local machine.
      *
      * As a result, this method will change this config builder's `port` value.
      * The caller must ensure that the `host` and `transport` fields have been
      * decided on before calling this method. Later changes of either of these
      * will render the result invalid.
      *
      * This method will fail with runtime exception if the host is not local.
      */
    def pickPort(): Unit = {
      require(isLocal)
      transport match {
        case UDP =>
          val tmp = new DatagramSocket()
          port = tmp.getLocalPort
          tmp.close()
        case TCP =>
          val tmp = new ServerSocket(0)
          port = tmp.getLocalPort
          tmp.close()
      }
    }

    /** Checks if the currently set `host` is located on the local machine. */
    def isLocal: Boolean = {
      val hostAddr = InetAddress.getByName(host)
      hostAddr.isLoopbackAddress || hostAddr.isSiteLocalAddress || hostAddr.isAnyLocalAddress
    }

    def build: Config = {
      val minAudioBuses     = inputBusChannels + outputBusChannels + 1
      val audioBusesAdjust  = if (audioBusChannels >= minAudioBuses) audioBusChannels else {
        minAudioBuses.nextPowerOfTwo
      }

      new Config(
        program               = program,
        controlBusChannels    = controlBusChannels,
        audioBusChannels      = audioBusesAdjust, /*audioBusChannels,*/
        outputBusChannels     = outputBusChannels,
        blockSize             = blockSize,
        sampleRate            = sampleRate,
        audioBuffers          = audioBuffers,
        maxNodes              = maxNodes,
        maxSynthDefs          = maxSynthDefs,
        memorySize            = memorySize,
        wireBuffers           = wireBuffers,
        randomSeeds           = randomSeeds,
        loadSynthDefs         = loadSynthDefs,
        machPortName          = machPortName,
        verbosity             = verbosity,
        plugInsPaths          = plugInsPaths,
        restrictedPath        = restrictedPath,
        /* memoryLocking, */
        host                  = host,
        port                  = port,
        transport             = transport,
        inputStreamsEnabled   = inputStreamsEnabled,
        outputStreamsEnabled  = outputStreamsEnabled,
        deviceNames           = deviceNames,
        deviceName            = deviceName,
        inputBusChannels      = inputBusChannels,
        hardwareBlockSize     = hardwareBlockSize,
        zeroConf              = zeroConf,
        maxLogins             = maxLogins,
        sessionPassword       = sessionPassword,
        nrtCommandPath        = nrtCommandPath,
        nrtInputPath          = nrtInputPath,
        nrtOutputPath         = nrtOutputPath,
        nrtHeaderFormat       = nrtHeaderFormat,
        nrtSampleFormat       = nrtSampleFormat
      )
    }

    def read(config: Config): Unit = {
      program             = config.program
      controlBusChannels  = config.controlBusChannels
      audioBusChannels    = config.audioBusChannels
      outputBusChannels   = config.outputBusChannels
      blockSize           = config.blockSize
      sampleRate          = config.sampleRate
      audioBuffers        = config.audioBuffers
      maxNodes            = config.maxNodes
      maxSynthDefs        = config.maxSynthDefs
      memorySize          = config.memorySize
      wireBuffers         = config.wireBuffers
      randomSeeds         = config.randomSeeds
      loadSynthDefs       = config.loadSynthDefs
      machPortName        = config.machPortName
      verbosity           = config.verbosity
      plugInsPaths        = config.plugInsPaths
      restrictedPath      = config.restrictedPath
      host                = config.host
      port                = config.port
      transport           = config.transport
      inputStreamsEnabled = config.inputStreamsEnabled
      outputStreamsEnabled= config.outputStreamsEnabled
      deviceNames         = config.deviceNames
      deviceName          = config.deviceName
      inputBusChannels    = config.inputBusChannels
      hardwareBlockSize   = config.hardwareBlockSize
      zeroConf            = config.zeroConf
      maxLogins           = config.maxLogins
      sessionPassword     = config.sessionPassword
      nrtCommandPath      = config.nrtCommandPath
      nrtInputPath        = config.nrtInputPath
      nrtOutputPath       = config.nrtOutputPath
      nrtHeaderFormat     = config.nrtHeaderFormat
      nrtSampleFormat     = config.nrtSampleFormat
    }
  }

  def boot: ServerConnection = boot()()

  def boot(name: String = "localhost", config: Config = Config().build,
           clientConfig: Client.Config = Client.Config().build)
          (listener: ServerConnection.Listener = PartialFunction.empty): ServerConnection = {
    val sc = initBoot(name, config, clientConfig)
    if (!(listener eq PartialFunction.empty)) sc.addListener(listener)
    sc.start()
    sc
  }

  private def initBoot(name: String = "localhost", config: Config,
                       clientConfig: Client.Config = Client.Config().build) = {
    val (addr, c) = prepareConnection(config, clientConfig)
    new impl.Booting(name, c, addr, config, clientConfig, true)
  }

  def connect: ServerConnection = connect()()

  def connect(name: String = "localhost", config: Config = Config().build,
              clientConfig: Client.Config = Client.Config().build)
             (listener: ServerConnection.Listener = PartialFunction.empty): ServerConnection = {
    val (addr, c) = prepareConnection(config, clientConfig)
    val sc = new impl.Connection(name, c, addr, config, clientConfig, true)
    if (!(listener eq PartialFunction.empty)) sc.addListener(listener)
    sc.start()
    sc
  }

  def run(code: Server => Unit): Unit = run()(code)

  /** Utility method to test code quickly with a running server. This boots a
    * server and executes the passed in code when the server is up. A shutdown
    * hook is registered to make sure the server is destroyed when the VM exits.
    */
  def run(config: Config = Config().build)(code: Server => Unit): Unit = {
    //      val b = boot( config = config )
    val sync = new AnyRef
    var s: Server = null
    val sc = initBoot(config = config)
    val li: ServerConnection.Listener = {
      case ServerConnection.Running(srv) => sync.synchronized {
        s = srv
      }; code(srv)
    }
    sc.addListener(li)
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit =
        sync.synchronized {
          if (s != null) {
            if (s.condition != Server.Offline) s.quit()
          } else sc.abort()
        }
    })
    sc.start()
  }

  /** Creates an unconnected server proxy. This may be useful for creating NRT command files.
    * Any attempt to try to send messages to the server will fail.
    */
  def dummy(name: String = "dummy", config: Config = Config().build,
            clientConfig: Client.Config = Client.Config().build): Server = {
    // val (addr, c) = prepareConnection(config, clientConfig)
    val sr        = config.sampleRate
    val status    = message.StatusReply(numUGens = 0, numSynths = 0, numGroups = 0, numDefs = 0,
      avgCPU = 0f, peakCPU = 0f, sampleRate = sr, actualSampleRate = sr)
    new impl.OfflineServerImpl(name, /* c, addr, */ config, clientConfig, status)
  }

  private def prepareConnection(config: Config, clientConfig: Client.Config): (InetSocketAddress, osc.Client) = {
    val sa = new InetSocketAddress(config.host, config.port)
    val clientAddr = clientConfig.addr getOrElse {
      val a = sa.getAddress
      if (a.isLoopbackAddress || a.isAnyLocalAddress)
        new InetSocketAddress("127.0.0.1", 0)
      else
        new InetSocketAddress(InetAddress.getLocalHost, 0)
    }
    val c = createClient(config.transport, sa, clientAddr)
    (sa, c)
  }

  def allocPort(transport: osc.Transport): Int = {
    transport match {
      case TCP =>
        val ss = new ServerSocket(0)
        try {
          ss.getLocalPort
        } finally {
          ss.close()
        }

      case UDP =>
        val ds = new DatagramSocket()
        try {
          ds.getLocalPort
        } finally {
          ds.close()
        }

      case other => sys.error(s"Unsupported transport : ${other.name}")
    }
  }

  def printError(name: String, t: Throwable): Unit = {
    println(s"$name :")
    t.printStackTrace()
  }

  implicit def defaultGroup(s: Server): Group = s.defaultGroup

  type Listener = Model.Listener[Update]

  sealed trait Update
  sealed trait Condition extends Update
  case object  Running   extends Condition
  case object  Offline   extends Condition
  private[synth] case object NoPending extends Condition

  final case class Counts(c: message.StatusReply) extends Update

  /** Starts an NRT rendering process based on the NRT parameters of the configuration argument.
    *
    * '''Note:''' The returned process must be explicitly started by calling `start()`
    *
    * @param dur      the duration of the bounce, used to emit process updates
    * @param config   the server configuration in which `nrtCommandPath` must be set
    *
    * @return   the process whose return value is the process exit code of scsynth (0 indicating success)
    */
  def renderNRT(dur: Double, config: Server.Config): Processor[Int] with Processor.Prepared =
    new impl.NRTImpl(dur, config)

  private def createClient(transport: osc.Transport.Net, serverAddr: InetSocketAddress,
                           clientAddr: InetSocketAddress): osc.Client = {
    val client: osc.Client = transport match {
      case UDP =>
        val cfg = UDP.Config()
        cfg.localSocketAddress  = clientAddr
        cfg.codec               = message.ServerCodec
        cfg.bufferSize          = 0x10000
        UDP.Client(serverAddr, cfg)
      case TCP =>
        val cfg                 = TCP.Config()
        cfg.codec               = message.ServerCodec
        cfg.localSocketAddress  = clientAddr
        cfg.bufferSize          = 0x10000
        TCP.Client(serverAddr, cfg)
    }
    client
  }

  def version: Try[(String, String)] = version()

  def version(config: Config = Config().build): Try[(String, String)] = Try {
    import scala.sys.process._
    val output  = Seq(config.program, "-v").!!
    val i       = output.indexOf(' ') + 1
    val j0      = output.indexOf(' ', i)
    val j1      = if (j0 > i) j0 else output.indexOf('\n', i)
    val j       = if (j1 > i) j1 else output.length
    val k       = output.indexOf('(', j) + 1
    val m       = output.indexOf(')', k)
    val version = output.substring(i, j)
    val build   = if (m > k) output.substring(k, m) else ""
    (version, build)
  }
}

sealed trait ServerLike {
  def name  : String
  def config: Server.Config
  def addr  : InetSocketAddress
}

object ServerConnection {
  type Listener = Model.Listener[Condition]

  sealed abstract class Condition
  case class  Preparing(server: Server) extends Condition
  case class  Running  (server: Server) extends Condition
  case object Aborted extends Condition
}

trait ServerConnection extends ServerLike with Model[ServerConnection.Condition] {
  def abort(): Unit
}

trait Server extends ServerLike with Model[Server.Update] {
  server =>

  import de.sciss.synth.Server._

  val clientConfig : Client.Config

  def rootNode     : Group
  def defaultGroup : Group
  def nodeManager  : NodeManager
  def bufManager   : BufferManager

  def isLocal      : Boolean
  def isConnected  : Boolean
  def isRunning    : Boolean
  def isOffline    : Boolean

  def nextNodeId(): Int
  def nextSyncId(): Int

  def allocControlBus(numChannels: Int): Int
  def allocAudioBus  (numChannels: Int): Int

  def freeControlBus(index: Int): Unit
  def freeAudioBus  (index: Int): Unit

  def allocBuffer(numChannels: Int): Int
  def freeBuffer (index      : Int): Unit

  def ! (p: osc.Packet): Unit

  /** Sends out an OSC packet that generates some kind of reply, and
    * returns immediately. It registers a handler to parse that reply.
    * The handler is tested for each incoming OSC message (using its
    * `isDefinedAt` method) and invoked and removed in case of a
    * match, completing the returned future.
    *
    * If the handler does not match in the given timeout period,
    * the future fails with a `Timeout` exception, and the handler is removed.
    *
    * @param   packet    the packet to send out
    * @param   timeout   the timeout duration
    * @param   handler   the handler to match against incoming messages
    * @return   a future of the successfully completed handler or timeout exception
    *
    * @see  [[de.sciss.synth.message.Timeout]]
    */
  def !! [A](packet: osc.Packet, timeout: Duration = 6.seconds)(handler: PartialFunction[osc.Message, A]): Future[A]

  def counts: message.StatusReply

  def sampleRate: Double

  def dumpTree(controls: Boolean = false): Unit

  def condition: Condition

  def startAliveThread(delay: Float = 0.25f, period: Float = 0.25f, deathBounces: Int = 25): Unit

  def stopAliveThread(): Unit

  def queryCounts(): Unit

  def syncMsg(): message.Sync

  def dumpOSC   (mode: osc.Dump = osc.Dump.Text, filter: osc.Packet => Boolean = _ => true): Unit
  def dumpInOSC (mode: osc.Dump = osc.Dump.Text, filter: osc.Packet => Boolean = _ => true): Unit
  def dumpOutOSC(mode: osc.Dump = osc.Dump.Text, filter: osc.Packet => Boolean = _ => true): Unit

  def quit(): Unit

  def quitMsg: message.ServerQuit.type

  def dispose(): Unit

  private[synth] def addResponder   (resp: message.Responder): Unit
  private[synth] def removeResponder(resp: message.Responder): Unit

  override def toString = s"<$name>"
}