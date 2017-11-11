/*---
  various examples for ScalaCollider;
  some are taken from SC2-examples_1.scd

 notes:
 - static random operations have been
   replaced by scalar ugens (Rand, IRand)
   due to lack of random methods in RichInt, RichFloat.
   the effect is that the reported number of UGens is
   slightly higher than the equivalent sclang code.
 - mul and add arguments have been usually dropped from
   the ugen constructors for simplicity. hence you
   find frequent use of the `madd` method.
 - careful with number divisions, e.g. 2/6 yields
   0.333... in sclang, and 0 in scala. Use 2.0/6 instead
*/

////////////////////
// analog bubbles //
////////////////////

val x = play {
  val f = LFSaw.kr(0.4).madd(24, LFSaw.kr(Seq(8, 7.23)).madd(3, 80)).midicps // glissando function
  CombN.ar(SinOsc.ar(f)*0.04, 0.2, 0.2, 4) // echoing sine wave
}

x.free()

val df = SynthDef("AnalogBubbles") {
  val f1 = "freq1".kr(0.4)
  val f2 = "freq2".kr(8)
  val d  = "detune".kr(0.90375)
  val f  = LFSaw.ar(f1).madd(24, LFSaw.ar(Seq(f2, f2 * d)).madd(3, 80)).midicps // glissando function
  val x  = CombN.ar(SinOsc.ar(f) * 0.04, 0.2, 0.2, 4) // echoing sine wave
  Out.ar(0, x)
}
val x = df.play()
x.set("freq1" -> 0.1)
x.set("freq2" -> 222.2)
x.set("detune" -> 0.44)

s.freeAll()

////////////////////////////////////////////////////////
// LFO modulation of Pulse waves and resonant filters //
////////////////////////////////////////////////////////

val x = play {
  CombL.ar(
    RLPF.ar(LFPulse.ar(FSinOsc.kr(0.05).madd(80, 160), 0, 0.4) * 0.05, 
            FSinOsc.kr(Seq(0.6, 0.7)).madd(3600, 4000), 0.2),
    0.3, Seq(0.2, 0.25), 2)
}

//////////////
// moto rev //
//////////////

val x = play {
  RLPF.ar(LFPulse.ar(SinOsc.kr(0.2).madd(10, 21), 0.1), 100, 0.1).clip2(0.4) 
}

//////////////
// scratchy //
//////////////

val x = play { RHPF.ar((BrownNoise.ar(Seq(0.5, 0.5)) - 0.49).max(0) * 20, 5000, 1) }

///////////////
// sprinkler //
///////////////

val x = play {
  BPZ2.ar(WhiteNoise.ar(LFPulse.kr(LFPulse.kr(0.09, 0, 0.16).madd(10, 7), 0, 0.25) * 0.1))
}

val x = play {
  BPZ2.ar(WhiteNoise.ar(LFPulse.kr(MouseX.kr(0.2,50), 0, 0.25) * 0.1))
}

///////////////////////
// harmonic swimming //
///////////////////////

val x = play {
  val f = 50       // fundamental frequency
  val p = 20       // number of partials per channel
  val offset = Line.kr(0, -0.02, 60, doneAction = freeSelf) // causes sound to separate and fade
  Mix.tabulate(p) { i =>
    FSinOsc.ar(f * (i+1)) * // freq of partial
      LFNoise1.kr(Seq(Rand(2, 10), Rand(2, 10)))  // amplitude rate
      .madd(  
        0.02,     // amplitude scale
        offset    // amplitude offset
      ).max(0)    // clip negative amplitudes to zero
  }
}

///////////////////////
// harmonic tumbling //
///////////////////////

val x = play {
  val f = 80       // fundamental frequency
  val p = 10       // number of partials per channel
  val trig = XLine.kr(Seq(10, 10), 0.1, 60, doneAction = freeSelf) // trigger probability decreases over time
  Mix.tabulate(p){ i =>
    FSinOsc.ar(f * (i+1)) *    // freq of partial
      Decay2.kr(
        Dust.kr(trig) // trigger rate
        * 0.02,       // trigger amplitude
        0.005,        // grain attack time
        Rand(0,0.5)   // grain decay time
      )
  }
}

////////////////////////////////////////////////////
// Klank - bank of resonators excited by impulses //
////////////////////////////////////////////////////

val x = play {
  val p = 15    // number of partials
  val z =       // filter bank specification :
    KlangSpec.fill(p) {
      (Rand(80, 10080),    // frequencies
       Rand(-1, 1),        // amplitudes
       Rand(0.2, 8.2))     // ring times
    }
  Pan2.ar(
    Klank.ar(z, Dust.ar(0.7) * 0.04),
    Rand(-1,1)
  )
}

////////////////////////////////////////////////////
// Klank - bank of resonators excited by impulses //
////////////////////////////////////////////////////

val x = play {
  val p = 8    // number of partials
  val exciter = Decay.ar(Dust.ar(0.6) * 0.001, 3.1) * WhiteNoise.ar
  for (i <- 1 to 2) yield {
    val spec = KlangSpec.fill(p) {
      (Rand(80, 10080), 1, Rand(0.2, 8.2))
    }
    Klank.ar(spec, exciter)
  }
}

//////////////////////////
// what was I thinking? //
//////////////////////////

val x = play {
  val z = RLPF.ar(
    Pulse.ar(
      SinOsc.kr(4).madd(1, 80).max(
        Decay.ar(LFPulse.ar(0.1, 0, 0.05) * Impulse.ar(8) * 500, 2)
      ), 
      LFNoise1.kr(0.157).madd(0.4, 0.5)
    ) * 0.04,
    LFNoise1.kr(0.2).madd(2000, 2400),
    0.2
  )
  val y = z * 0.6
  z + Seq(
    CombL.ar(y, 0.06, LFNoise1.kr(Rand(0, 0.3)).madd(0.025, 0.035), 1) 
  + CombL.ar(y, 0.06, LFNoise1.kr(Rand(0, 0.3)).madd(0.025, 0.035), 1),
    CombL.ar(y, 0.06, LFNoise1.kr(Rand(0, 0.3)).madd(0.025, 0.035), 1)
  + CombL.ar(y, 0.06, LFNoise1.kr(Rand(0, 0.3)).madd(0.025, 0.035), 1)
  )
}

//////////////////
// police state //
//////////////////

val x = play {
  val n = 4   // number of sirens
  CombL.ar(
    Mix.fill(n) {
      Pan2.ar(
        SinOsc.ar(
          SinOsc.kr(Rand(0.02, 0.12), Rand(0, 2*Pi)).madd(IRand(0, 599), IRand(700, 1299))
        ) * LFNoise2.ar(Rand(80, 120)) * 0.1,
        Rand(-1, 1)
      )
    }
    + LFNoise2.ar(
        LFNoise2.kr(Seq(0.4, 0.4)).madd(90, 620)) * 
        LFNoise2.kr(Seq(0.3, 0.3)).madd(0.15, 0.18),
        0.3, 0.3, 3
  )
}

///////////////
// cymbalism //
///////////////

val x = play {
  val p = 15   // number of partials per channel per 'cymbal'.
  val f1 = Rand(500, 2500)
  val f2 = Rand(0, 8000)
  for (i <- 1 to 2) yield {
    val z = KlangSpec.fill(p) {
      // sine oscillator bank specification :
      (f1 + Rand(0, f2),  // frequencies
       1,                 // amplitudes
       Rand(1, 5))        // ring times
    }
    Klank.ar(z, Decay.ar(Impulse.ar(Rand(0.5, 3.5)), 0.004) * WhiteNoise.ar(0.03))
  }
}

/////////////////////
// synthetic piano //
/////////////////////

val x = play {
  val n = 6        // number of keys playing
  Mix.fill(n) {    // mix an array of notes
    // calculate delay based on a random note
    val pitch  = IRand(36, 89)
    val strike = Impulse.ar(Rand(0.1, 0.5), Rand(0, 2*Pi)) * 0.05    // random period for each key
    val hammerEnv = Decay2.ar(strike, 0.008, 0.04)    // excitation envelope
    Pan2.ar(
      // array of 3 strings per note
      Mix.tabulate(3) { i =>
        // detune strings, calculate delay time :
        val detune = Array(-0.05, 0, 0.04)(i)
        val delayTime = 1 / (pitch + detune).midicps
        // each string gets own exciter :
        val hammer = LFNoise2.ar(3000) * hammerEnv   // 3000 Hz was chosen by ear..
        CombL.ar(hammer,   // used as a string resonator
                 delayTime,     // max delay time
                 delayTime,     // actual delay time
                 6              // decay time of string
        )
      },
      (pitch - 36) / 27 - 1    // pan position: lo notes left, hi notes right
    )
  }
}

//////////////////////////////////
// reverberated sine percussion //
//////////////////////////////////

val x = play {
  val d = 6    // number of percolators
  val c = 5    // number of comb delays
  val a = 4    // number of allpass delays

  // sine percolation sound :
  val s = Mix.fill(d) { Resonz.ar(Dust.ar(2.0 / d) * 50, Rand(200, 3200), 0.003) }

  // reverb pre-delay time :
  val z = DelayN.ar(s, 0.048)

  // 'c' length modulated comb delays in parallel :
  val y = Mix(CombL.ar(z, 0.1, LFNoise1.kr(Seq.fill(c)(Rand(0, 0.1))).madd(0.04, 0.05), 15))

  // chain of 'a' allpass delays on each of two channels (2 times 'a' total) :
  val x = Mix.fold(y, a) { in =>
    AllpassN.ar(in, 0.050, Seq(Rand(0, 0.050), Rand(0, 0.050)), 1)
  }

  // add original sound to reverb and play it :
  s + 0.2 * x
}

///////////////////////////////
// reverberated noise bursts //
///////////////////////////////

val x = play {
  // pink noise percussion sound :
  val s = Decay.ar(Dust.ar(0.6) * 0.2, 0.15) * PinkNoise.ar

  // reverb pre-delay time :
  val z = DelayN.ar(s, 0.048, 0.048)

  // 6 modulated comb delays in parallel :
  val y = Mix(CombL.ar(z, 0.1, LFNoise1.kr(Seq.fill(6)(Rand(0, 0.1))).madd(0.04, 0.05), 15))

  // chain of 4 allpass delays on each of two channels (8 total) :
  val x = Mix.fold(y, 4) { in => 
    AllpassN.ar(in, 0.050, Seq(Rand(0, 0.050), Rand(0, 0.050)), 1)
  }

  // add original sound to reverb and play it :
  s + x
}

/////////////////////////////////
// sample and hold liquidities //
/////////////////////////////////

// mouse x controls clock rate, mouse y controls center frequency
val x = play {
  val clockRate  = MouseX.kr(1, 200, 1)
  val clockTime  = clockRate.reciprocal
  val clock      = Impulse.kr(clockRate, 0.4)

  val centerFreq = MouseY.kr(100, 8000, 1)
  val freq       = Latch.kr(WhiteNoise.kr(centerFreq * 0.5) + centerFreq, clock)
  val panPos     = Latch.kr(WhiteNoise.kr, clock)
  CombN.ar(
    Pan2.ar(
      SinOsc.ar(freq) *
        Decay2.kr(clock, 0.1 * clockTime, 0.9 * clockTime),
      panPos
    ),
    0.3, 0.3, 2
  )
}

///////////////////////////////////////
// sweepy noise - mouse controls LFO //
///////////////////////////////////////

val x = play {
  val lfoDepth = MouseY.kr(200, 8000, 1)
  val lfoRate  = MouseX.kr(4, 60, 1)
  val freq     = LFSaw.kr(lfoRate).madd(lfoDepth, lfoDepth * 1.2)
  val filtered = RLPF.ar(WhiteNoise.ar(Seq(0.03, 0.03)), freq, 0.1)
  CombN.ar(filtered, 0.3, 0.3, 2) + filtered
}

///////////////////////
// aleatoric quartet //
///////////////////////

// mouse x controls density
val x = play {
  val amp = 0.07
  val density = MouseX.kr(0.01, 1)   // mouse determines density of excitation

  // calculate multiply and add for excitation probability
  val dmul = density.reciprocal * 0.5 * amp
  val dadd = -dmul + amp

  val signal = Mix.fill(4) {   // mix an array of 4 instruments
    val excitation = PinkNoise.ar(
      // if amplitude is below zero it is clipped
      // density determines the probability of being above zero
      LFNoise1.kr(8).madd(dmul, dadd).max(0)
    )

    val freq = Lag.kr(           // lag the pitch so it makes glissandi between pitches
      LFNoise0.kr(               // use low freq step noise as a pitch control
        Vector(1, 0.5, 0.25)(    // choose a frequency of pitch change
          util.Random.nextInt(3)
        )
      ).madd(
        7,                       // +/- 7 semitones
        IRand(36, 96)            // random center note
      ).roundTo(1),              // round to nearest semitone
      0.2                        // glissando time
    ).midicps                    // convert to hertz

    Pan2.ar(    // pan each instrument
      CombL.ar(excitation, 0.02, freq.reciprocal, 3),    // comb delay simulates string
      Rand(-1, 1)    // random pan position
    )
  }

  // add some reverb via allpass delays
  val x = Mix.fold(signal, 5) { in =>
    AllpassN.ar(in, 0.05, Seq(Rand(0, 0.05), Rand(0, 0.05)), 1)
  }
  LeakDC.ar(x, 0.995)    // delays build up a lot of DC, so leak it out here.
}

/////////////////////
// harmonic zither //
/////////////////////

// use mouse to strum strings

val x = play {
  // harmonic series
  val pitch  = Vector(50, 53.86, 57.02, 59.69, 62, 64.04, 65.86, 67.51, 69.02, 71.69, 72.88, 74)
  val mousex = MouseX.kr
  val mousey = MouseY.kr
  val triggerSpacing = 0.5 / (pitch.size - 1)
  val panSpacing     = 1.5 / (pitch.size - 1)
  val out = Mix.tabulate(pitch.size) { i =>
    // place trigger points from 0.25 to 0.75
    val trigger = HPZ1.kr(mousex > (0.25 + (i * triggerSpacing))).abs
    val pluck   = PinkNoise.ar(Decay.kr(trigger, 0.05))
    val period  = pitch(i).midicps.reciprocal
    val string  = CombL.ar(pluck, period, period, 8)
    Pan2.ar(string, i * panSpacing - 0.75)
  }
  LeakDC.ar(out)
}

////////////////////////////////////////////////////////
// based on record scratcher by Josh Parmenter (2007) //
////////////////////////////////////////////////////////

// path to a mono sound-file here
val buf = Buffer.read(s, "sounds/a11wlk01.wav")
val x = play {
  val speed0 = MouseX.kr(-10, 10)
  val speed1 = speed0 - DelayN.kr(speed0, 0.1, 0.1)
  val speed  = MouseButton.kr(1, 0, 0.3) + speed1
  val sig    = PlayBuf.ar(1, buf.id, speed * BufRateScale.kr(buf.id), loop = 1)
  Seq(sig, sig)
}

// move mouse to scrub the record.
// press mouse button to 'stop the record', you can scrub while it is stopped.

// stop the synth
x.release()
// free the Buffer
buf.free()

/////////////////////////////////
// trigger and lagged controls //
/////////////////////////////////

val x = play {
  val trig = "trig".tr             // trigger control
// val freq = "freq".kr(440 -> 4.0) // lag control (lag time 4 seconds)
  val freq = "freq".kr(440) // lag control not yet implemented :-(
  SinOsc.ar(freq + Seq(0, 1)) * Decay2.kr(trig, 0.005, 1.0)
}

x.set("trig" -> 1)
x.set("trig" -> 1, "freq" -> 220)
x.set("trig" -> 1, "freq" -> 880)

//////////////////////////////////
// waiting for SendTrig replies //
//////////////////////////////////

val x = play {
  SendTrig.kr(MouseButton.kr(lag = 0), MouseX.kr(lag = 0)) // warning: different arg order!
}
val r = message.Responder.add() {
  case message.Trigger(x.id, _, mouseX) =>
    // alternative:
    // case osc.Message("/tr", x.id, _, mouseX) =>
    println("Dang! " + mouseX)
}
r.remove()
x.free()

// using SendReply

val x = play {
  // note that Pitch has two outputs, so feeding it with two input channels
  // produces two instances. In order to prevent the multi-channel-expansion
  // to create two SendReply objects and instead to concatenate both pitch
  // data, we can use Flatten(_)
  SendReply.kr(Impulse.kr(10), Pitch.kr(PhysicalIn.ar(numChannels = 2)).flatten)
}
val resp = message.Responder.add() {
  case osc.Message("/reply", x.id, _, freqL: Float, hasFreqL: Float, freqR: Float, hasFreqR: Float) =>
    if (hasFreqL > 0 || hasFreqR > 0) {
      val sl = if (hasFreqL > 0) f"${freqL.cpsmidi}%1.2f" else "?"
      val sr = if (hasFreqR > 0) f"${freqR.cpsmidi}%1.2f" else "?"
      println(s"Pitch : $sl / $sr")
    }
}
x.onEnd { resp.remove() }

x.free()

//////////////////////////////////////////////////////////////////
// exporting a synth graph diagram as PDF.                      //
// requires that the iTextPDF v5 jar is in the system classpath //
//////////////////////////////////////////////////////////////////

val df = SynthDef("AnalogBubbles" ) {
  val f = LFSaw.kr(0.4).madd(24, LFSaw.kr(Seq(8, 7.23)).madd(3, 80)).midicps // glissando function
  val x = CombN.ar(SinOsc.ar(f) * 0.04, 0.2, 0.2, 4) // echoing sine wave
  WrapOut(x, None)
}
val f = viewDef(df)
f.display.setBackground(java.awt.Color.white)

val width    = f.display.getWidth
val height   = f.display.getHeight
val pageSize = new com.itextpdf.text.Rectangle(0, 0, width, height)
val doc      = new com.itextpdf.text.Document(pageSize, 0, 0, 0, 0)
val fileName = "/Users/rutz/Desktop/output.pdf"
val stream   = new java.io.FileOutputStream(fileName)
val writer   = com.itextpdf.text.pdf.PdfWriter.getInstance(doc, stream)
doc.open()
val cb       = writer.getDirectContent
val tp       = cb.createTemplate(width, height)
val g2       = tp.createGraphics(width, height)
f.display.paintDisplay(g2, new java.awt.Dimension(width, height))
g2.dispose()
cb.addTemplate(tp, 0, 0)
doc.close()
stream.close()

/////////////////
// FFT example //
/////////////////

val b = Buffer.alloc(s, 2048)  // see also LocalBuf further down
val df = SynthDef("mag-above") {
  val in   = WhiteNoise.ar(0.2)
  val fft  = FFT("buf".kr, in)
  val flt  = PV_MagAbove(fft, MouseX.kr(0, 10))
  val ifft = IFFT.ar(flt) * Seq(0.5, 0.5)
  Out.ar("out" kr 0, ifft)
}
df.recv(s)
val x = Synth.play(df.name, Seq("buf" -> b.id))
x.free(); b.free()

////////////////////
// Demand example //
////////////////////

val x = play {
  val freq = DemandEnvGen.ar(
    Dseq(Seq(204, 400, 201, 502, 300, 200), inf),
    Drand(Seq(1.01, 0.2, 0.1, 2), inf) * MouseY.kr(0.01, 3, 1),
    Curve.cubed.id
  )
  SinOsc.ar(freq * Seq(1, 1.01)) * 0.1
}

val x = play {
  // notice argument order for Duty and TDuty being different from sclang!
  val freq = Duty.kr(
    Drand(Seq(0.01, 0.2, 0.4), inf),  // demand ugen as durations
    Dseq(Seq(204, 400, 201, 502, 300, 200), inf)
  )
  SinOsc.ar(freq * Seq(1, 1.01)) * 0.1
}

/////////////////////////////////////////
//  Output channel separation example: //
/////////////////////////////////////////

// In the current version `numOutputs` and `outputs` are disabled. You still have the
// backslash operator, hence instead of

val Seq(freq, hasFreq) = Pitch.kr(...)

// you can do

val pch     = Pitch.kr(...)
val freq    = pch \ 0
val hasFreq = pch \ 1

// but even better, use the named outputs:

val pch     = Pitch.kr(...)
val freq    = pch.freq
val hasFreq = pch.hasFreq

// for example:

// WARNING: can produce feedback like tones. turn down the volume first !!!

val x = play {
  val in      = Mix(PhysicalIn.ar(0, 2))
  val amp     = Amplitude.kr(in, 0.05, 0.05) * 0.3
  val p       = Pitch.kr(in, ampThresh = 0.02, median = 7)
  p.hasFreq.poll(1)
  val syn     = Mix(VarSaw.ar(p.freq * Seq(0.5, 1.0, 2.0), 0, LFNoise1.kr(0.3).madd(0.1, 0.1)) * amp)
  Mix.fold(syn, 6) { sig =>
    AllpassN.ar(sig, 0.040, Seq(Rand(0, 0.040), Rand(0, 0.040)), 2)
  }
}

///////////////////////////////////
// Multichannel controls example //
///////////////////////////////////

// a synth def that has 4 partials
SynthDef.recv("multi-con") {
  val harm  = "harm"  ir Seq(1,    2,    3,    4   )  // harmonics
  val amp   = "amp"   ir Seq(0.05, 0.05, 0.05, 0.05)  // amplitudes
  val ring  = "ring"  ir Seq(1,    1,    1,    1   )  // ring times
  val klank = Klank.ar(Zip(harm, amp, ring), ClipNoise.ar(Seq(0.01, 0.01)), "freq" ir 300)
  Out.ar("out" kr 0, klank)
}

val x = Synth.play("multi-con", Seq("harm" -> Vector(1f, 3.3f, 4.5f, 7.8f)))
x.free()
val x = Synth.play("multi-con", Seq("harm" -> Vector(2f, 3f, 4f, 5f)))
x.free()

//////////////
// LocalBuf //
//////////////

val x = play {
  val in  = WhiteNoise.ar(Seq(0.1, 0.1))
  val buf = Seq.fill(2)(LocalBuf(2048))
  val fft = FFT(buf, in)
  val z   = PV_BrickWall(fft, SinOsc.kr(Seq(0.1, 0.11)))
  IFFT.ar(z) // inverse FFT
}

///////////////////////////////
// Some translated SC tweets //
///////////////////////////////

// quite loud, so let's use a limiter first:
val lim = play(s, addAction = addToTail) { ReplaceOut.ar(0, Limiter.ar(In.ar(0, 2), 0.3)) }
// some noise by fredrik olofsson
val x = play {
  RHPF.ar(
    GbmanN.ar(Seq(2300, 1150)),
    LFSaw.ar(Pulse.ar(4, Seq(0.125, 0.25)) + LFPulse.ar(0.125) / 5 + 1) + 2
  )
}

x.free()
lim.free()

// ambient by tim walters (added a LeakDC)
val x = play {
  GVerb.ar(LeakDC.ar(
    Mix.tabulate(16) { k =>
      Mix.tabulate(6) { i =>
        val x = Impulse.kr((0.5 pow i) / k)
        SinOsc.ar(i, SinOsc.ar((i + k) pow i) / (Decay.kr(x, Seq(i, i+1)) * k))
      }
    }
  ), roomSize = 1) / 384
}

x release 10

////////////////////////////////////////////////////////////
// transferring buffer contents between client and server //
////////////////////////////////////////////////////////////

val path = "/usr/share/SuperCollider/sounds/a11wlk01-44_1.aiff"
val b    = Buffer.read(s, path)
// note: the `plot` method is defined in ScalaCollider-Swing
for (d <- b.getData()) defer(d.plot())

val af    = io.AudioFile.openRead(path)
val numFr = af.numFrames.toInt
val arr   = af.buffer(numFr)
af.read(arr)
af.close()

val b2 = Buffer(s)
for {
  _ <- b2.alloc(numFrames = numFr, numChannels = af.numChannels)
  _ <- b2.setData(arr.flatten)
} {
  println("Data ready.")
  b2.play(loop = true)
}

// and for control-buses
val c = Bus.control(s, 32)
c.setData((1 to 32).map(_.sqrt))
for (d <- c.getData()) defer(d.plot())
