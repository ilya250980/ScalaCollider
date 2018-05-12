package de.sciss.synth

import de.sciss.serial.impl.{ByteArrayInputStream, ByteArrayOutputStream}
import org.scalatest.{FlatSpec, Matchers}

/** Could not reproduce */
class Issue75 extends FlatSpec with Matchers {
  "SynthDef g1_51_4456" should "be serializable" in {
    testGraph {
      SynthDef("g1_51_4456") {
        g1_51_4456()
      }
    }
  }

  "SynthDef g1_51_4533" should "be serializable" in {
    testGraph {
      SynthDef("g1_51_4533") {
        g1_51_4533()
      }
    }
  }

  def testGraph(mk: => SynthDef): Unit = {
    val dfIn = mk

    val os = new ByteArrayOutputStream()
    SynthDef.write(os, dfIn :: Nil, version = 1)
    testGraphBytes(dfIn, os.toByteArray)
    val rm = dfIn.recvMsg
    val arr = new Array[Byte](rm.bytes.limit())
    rm.bytes.get(arr)
    testGraphBytes(dfIn, arr)
  }

  def testGraphBytes(dfIn: SynthDef, bytes: Array[Byte]): Unit = {
    val is = new ByteArrayInputStream(bytes)
    val dfOut :: Nil = SynthDef.read(is)
    val gIn   = dfIn  .graph
    val gOut  = dfOut .graph
    val uIn   = gIn   .ugens
    val uOut  = gOut  .ugens
    assert(gIn.constants      === gOut.constants)
    assert(gIn.controlNames   === gOut.controlNames)
    assert(gIn.controlValues  === gOut.controlValues)
    assert(uIn.size           === uOut.size)
    (uIn zip uOut).foreach { case (u1, u2) =>
      assert(u1.inputSpecs        === u2.inputSpecs)
      assert(u1.ugen.name         === u2.ugen.name )
      assert(u1.ugen.numInputs    === u2.ugen.numInputs)
      assert(u1.ugen.numOutputs   === u2.ugen.numOutputs)
      assert(u1.ugen.outputRates  === u2.ugen.outputRates)
      assert(u1.ugen.specialIndex === u2.ugen.specialIndex)
    }
//    assert(dfIn === dfOut)
  }

  import ugen._

  def Protect(in: GE, low: Double, high: Double, leakDC: Boolean): GE = {
    val x = in.max(low).min(high)
    if (leakDC) LeakDC.ar(x) else x
  }

  def NegatumIn(): Unit = RandSeed.ir()

  def NegatumOut(in: GE): Unit = {
    import Ops.stringToControl

    val sig0  = Mix.mono(in)
    val isOk  = CheckBadValues.ar(sig0, post = 0) sig_== 0
    val sig1  = Gate.ar(sig0, isOk)
    val sig2  = sig1.clip2(1)
    val sig3  = LeakDC.ar(sig2) * 0.47
    val sig4  = Limiter.ar(sig3, -0.2.dbAmp)
    val sig5  = HPF.ar(sig4, 20)
    val sig6  = {
      val env = EnvGen.ar(Env.asr, gate = "gate".kr(1f), doneAction = doNothing /* freeSelf */)
      val doneEnv = Done.kr(env)
      val normDur = 2.0
      val tFree = TDelay.kr(doneEnv, normDur)
      FreeSelf.kr(tFree)
      Normalizer.ar(sig5, level = -0.2.dbAmp, dur = normDur) * DelayN.ar(env, normDur, normDur)
    }
    val bus = "out".kr(0f)
    val sig7 = Pan2.ar(sig6)
    val sig8 = sig7 * "amp".kr(1f)
    val sig  = {
      val ln0 = Line.ar(start = 0, end = 1, dur = 0.05)
      val ln  = DelayN.ar(ln0, 0.1, 0.1)
      sig8 * ln
    }
    Out.ar(bus, sig)
  }

  def g1_51_4456(): Unit = {
    NegatumIn()
    val lFDNoise1_0     = LFDNoise1.ar(215.77258)
    val freq_0          = Protect(0.22157374, 10.0, 20000.0, leakDC = false)
    val width_0         = Protect(17.764212, 0.0, 1.0, leakDC = false)
    val pulse           = Pulse.ar(freq = freq_0, width = width_0)
    val wrap2_0         = -9.10621 wrap2 pulse
    val min_0           = wrap2_0 min 0.22157374
    val min_1           = min_0 min 0.22157374
    val min_2           = min_1 min 0.22157374
    val min_3           = min_2 min 0.22157374
    val min_4           = min_3 min 0.22157374
    val decayTime_0     = -260.23517 min min_4
    val in_0            = Protect(31.17988, -inf, inf, leakDC = true)
    val c_0             = AllpassN.ar(in_0, maxDelayTime = 6.700392, delayTime = 6.700392,
      decayTime = decayTime_0)
    val min_5           = c_0 min min_3
    val freq_1          = Protect(min_5, 0.01, 20000.0, leakDC = false)
    val iphase_0        = Protect(31.17988, 0.0, 1.0, leakDC = false)
    val lFCub_0         = LFCub.ar(freq = freq_1, iphase = iphase_0)
    val wrap2_1         = lFCub_0 wrap2 -4.6814137E-4
    val min_6           = 3.130919E-4 min wrap2_1
    val min_7           = min_6 min 94.63152
    val min_8           = min_6 min -260.23517
    val minus           = 0.0083515905 - min_8
    val min_9           = min_8 min -260.23517
    val min_10          = min_9 min 0.9355821
    val min_11          = min_10 min 0.9355821
    val dryLevel        = min_11 min minus
    val min_12          = dryLevel min min_11
    val min_13          = min_12 min -4.6814137E-4
    val c_1             = -4.6814137E-4 min min_13
    val a_0             = c_1 min -260.23517
    val min_14          = min_7 min -260.23517
    val min_15          = min_9 min -260.23517
    val freq_2          = Protect(min_14, 0.01, 20000.0, leakDC = false)
    val iphase_1        = Protect(a_0, 0.0, 1.0, leakDC = false)
    val width_1         = Protect(min_15, 0.0, 1.0, leakDC = false)
    val varSaw          = VarSaw.ar(freq = freq_2, iphase = iphase_1, width = width_1)
    val min_16          = min_6 min 0.9355821
    val min_17          = min_13 min -260.23517
    val min_18          = min_17 min -260.23517
    val min_19          = min_18 min -260.23517
    val min_20          = min_19 min -260.23517
    val min_21          = min_20 min -260.23517
    val min_22          = min_21 min -260.23517
    val iphase_2        = Protect(5083.3228, 0.0, 1.0, leakDC = false)
    val lFCub_1         = LFCub.ar(freq = 0.32233897, iphase = iphase_2)
    val freq_3          = Protect(min_22, -inf, inf, leakDC = false)
    val xi_0            = Protect(lFCub_1, -inf, inf, leakDC = false)
    val quadC           = QuadC.ar(freq = freq_3, a = minus, b = varSaw, c = c_1, xi = xi_0)
    val sqrsum          = min_16 sqrSum quadC
    val c_2             = sqrsum min 94.63152
    val in_1            = Protect(0.32233897, -inf, inf, leakDC = true)
    val release_0       = Protect(241.16264, 0.0, 30.0, leakDC = false)
    val decay2_0        = Decay2.ar(in_1, attack = 0.32233897, release = release_0)
    val min_23          = decay2_0 min min_12
    val min_24          = min_23 min min_5
    val freq_4          = Protect(min_24, -inf, inf, leakDC = false)
    val xi_1            = Protect(minus, -inf, inf, leakDC = false)
    val quadL           = QuadL.ar(freq = freq_4, a = a_0, b = 0.008044177, c = c_2, xi = xi_1)
    val in_2            = Protect(quadL, -inf, inf, leakDC = true)
    val lag3            = Lag3.ar(in_2, time = 0.9355821)
    val min_25          = lFCub_1 min lag3
    val min_26          = 60.299835 min min_25
    val min_27          = min_26 min min_25
    val min_28          = min_27 min min_25
    val min_29          = min_25 min 3.130919E-4
    val decayTime_1     = min_29 min min_25
    val min_30          = -260.23517 min min_28
    val min_31          = min_24 min min_30
    val k_0             = decayTime_1 min min_25
    val min_32          = min_31 min k_0
    val min_33          = min_31 min 6.700392
    val min_34          = min_33 min sqrsum
    val min_35          = min_34 min varSaw
    val gbmanL_0        = GbmanL.ar(freq = 5083.3228, xi = 5083.3228, yi = 2.1)
    val min_36          = min_35 min gbmanL_0
    val min_37          = min_36 min 5451.813
    val min_38          = k_0 min min_25
    val min_39          = min_38 min min_25
    val min_40          = min_37 min min_39
    val min_41          = min_40 min min_37
    val min_42          = lag3 min 5451.813
    val a_1             = min_42 min quadL
    val min_43          = min_41 min a_1
    val min_44          = min_43 min 318.76187
    val min_45          = min_4 min 0.22157374
    val min_46          = min_45 min 0.22157374
    val min_47          = min_46 min 0.22157374
    val min_48          = min_44 min min_47
    val min_49          = min_48 min min_47
    val min_50          = min_49 min min_47
    val min_51          = min_50 min min_47
    val in_3            = Protect(quadC, -inf, inf, leakDC = true)
    val maxDelayTime_0  = Protect(min_47, 0.0, 20.0, leakDC = false)
    val protect_0       = Protect(min_51, 0.0, inf, leakDC = false)
    val delayTime_0     = protect_0 min maxDelayTime_0
    val combL           = CombL.ar(in_3, maxDelayTime = maxDelayTime_0, delayTime = delayTime_0,
      decayTime = decayTime_1)
    val min_52          = min_16 min combL
    val freq_5          = Protect(lFCub_1, -inf, inf, leakDC = false)
    val xi_2            = Protect(min_52, -inf, inf, leakDC = false)
    val linCongL_0      = LinCongL.ar(freq = freq_5, a = 3.130919E-4, c = 0.008044177, m = minus,
      xi = xi_2)
    val freq_6          = Protect(linCongL_0, -inf, inf, leakDC = false)
    val xi_3            = Protect(combL, -inf, inf, leakDC = false)
    val linCongL_1      = LinCongL.ar(freq = freq_6, a = a_1, c = c_0, m = 754.3841, xi = xi_3)
    val yi_0            = LFDNoise0.ar(97.30713)
    val freq_7          = Protect(quadL, 0.01, 20000.0, leakDC = false)
    val lFSaw_0         = LFSaw.ar(freq = freq_7, iphase = 0.008044177)
    val freq_8          = Protect(linCongL_1, -inf, inf, leakDC = false)
    val xi_4            = Protect(lFSaw_0, -inf, inf, leakDC = false)
    val gbmanN          = GbmanN.ar(freq = freq_8, xi = xi_4, yi = yi_0)
    val min_53          = min_17 min min_52
    val min_54          = gbmanN min 35.973347
    val k_1             = min_54 min min_53
    val earlyRefLevel   = k_1 min wrap2_1
    val min_55          = decayTime_1 min -260.23517
    val min_56          = min_55 min min_55
    val min_57          = a_0 min 60.299835
    val min_58          = min_56 min c_1
    val min_59          = min_58 min earlyRefLevel
    val in_4            = Protect(min_59, -inf, inf, leakDC = true)
    val delay2          = Delay2.ar(in_4)
    val xi_5            = Protect(delay2, -inf, inf, leakDC = false)
    val yi_1            = Protect(gbmanL_0, -inf, inf, leakDC = false)
    val standardL       = StandardL.ar(freq = varSaw, k = k_1, xi = xi_5, yi = yi_1)
    val freq_9          = Protect(min_43, 0.01, 20000.0, leakDC = false)
    val iphase_3        = Protect(standardL, -1.0, 1.0, leakDC = false)
    val lFSaw_1         = LFSaw.ar(freq = freq_9, iphase = iphase_3)
    val min_60          = lFSaw_1 min 0.9355821
    val min_61          = min_60 min 0.9355821
    val min_62          = min_61 min min_45
    val freq_10         = min_19 <= min_62
    val xi_6            = Protect(min_30, -inf, inf, leakDC = false)
    val yi_2            = Protect(min_57, -inf, inf, leakDC = false)
    val tailLevel       = StandardN.ar(freq = freq_10, k = k_0, xi = xi_6, yi = yi_2)
    val min_63          = min_25 min tailLevel
    val in_5            = Protect(min_63, -inf, inf, leakDC = true)
    val lPF_0           = LPF.ar(in_5, freq = 60.299835)
    val min_64          = 6.700392 min pulse
    val min_65          = min_53 min earlyRefLevel
    val min_66          = min_13 min min_13
    val min_67          = yi_0 min 0.9355821
    val min_68          = min_67 min 0.9355821
    val min_69          = min_68 min min_66
    val min_70          = min_65 min combL
    val min_71          = min_70 min min_29
    val min_72          = min_69 min min_71
    val min_73          = min_72 min min_71
    val bitOr           = 0.22157374 | min_73
    val min_74          = min_73 min min_71
    val min_75          = min_74 min min_71
    val min_76          = min_75 min -4.6814137E-4
    val in_6            = Protect(freq_10, -inf, inf, leakDC = true)
    val timeUp_0        = Protect(min_54, 0.0, 30.0, leakDC = false)
    val timeDown_0      = Protect(minus, 0.0, 30.0, leakDC = false)
    val lagUD           = LagUD.ar(in_6, timeUp = timeUp_0, timeDown = timeDown_0)
    val min_77          = min_76 min lagUD
    val min_78          = c_2 min min_77
    val min_79          = min_78 min 94.63152
    val min_80          = 0.9355821 min min_79
    val coeff_0         = Protect(318.76187, 0.8, 0.99, leakDC = false)
    val leakDC          = LeakDC.ar(17.764212, coeff = coeff_0)
    val in_7            = Protect(leakDC, -inf, inf, leakDC = true)
    val release_1       = Protect(-9.10621, 0.0, 30.0, leakDC = false)
    val decay2_1        = Decay2.ar(in_7, attack = 0.4027743, release = release_1)
    val in_8            = Protect(min_80, -inf, inf, leakDC = true)
    val timeUp_1        = Protect(decay2_1, 0.0, 30.0, leakDC = false)
    val timeDown_1      = Protect(min_79, 0.0, 30.0, leakDC = false)
    val lag2UD          = Lag2UD.ar(in_8, timeUp = timeUp_1, timeDown = timeDown_1)
    val min_81          = lag2UD min 60.299835
    val min_82          = min_81 min min_81
    val min_83          = min_82 min min_2
    val min_84          = min_36 min min_77
    val min_85          = min_84 min min_27
    val in_9            = Protect(min_82, -inf, inf, leakDC = true)
    val freq_11         = Protect(3.7802913E-4, 10.0, 20000.0, leakDC = false)
    val a_2             = BPF.ar(in_9, freq = freq_11, rq = 97.30713)
    val min_86          = min_70 min min_61
    val b_0             = min_86 min min_25
    val freq_12         = Protect(min_6, -inf, inf, leakDC = false)
    val x0              = Protect(min_86, -inf, inf, leakDC = false)
    val henonC          = HenonC.ar(freq = freq_12, a = a_2, b = b_0, x0 = x0, x1 = 0.0)
    val loop            = henonC min min_25
    val min_87          = min_75 min min_71
    val min_88          = min_87 min min_71
    val min_89          = min_88 min min_71
    val min_90          = min_89 min min_71
    val min_91          = min_90 min min_71
    val min_92          = min_91 min min_71
    val min_93          = min_92 min min_71
    val min_94          = min_93 min freq_10
    val min_95          = min_83 min min_94
    val min_96          = min_95 min min_31
    val min_97          = min_62 min wrap2_0
    val d               = min_30 min -260.23517
    val min_98          = min_61 min 0.9355821
    val min_99          = gbmanN min min_25
    val min_100         = min_98 min min_99
    val freq_13         = Protect(min_86, -inf, inf, leakDC = false)
    val a_3             = Protect(min_75, -3.0, 3.0, leakDC = false)
    val b_1             = Protect(bitOr, 0.5, 1.5, leakDC = false)
    val c_3             = Protect(lFSaw_1, 0.5, 1.5, leakDC = false)
    val xi_7            = Protect(min_80, -inf, inf, leakDC = false)
    val yi_3            = Protect(min_100, -inf, inf, leakDC = false)
    val latoocarfianL_0 = LatoocarfianL.ar(freq = freq_13, a = a_3, b = b_1, c = c_3, d = 31.17988,
      xi = xi_7, yi = yi_3)
    val freq_14         = Protect(latoocarfianL_0, -inf, inf, leakDC = false)
    val a_4             = Protect(min_96, -3.0, 3.0, leakDC = false)
    val b_2             = Protect(tailLevel, 0.5, 1.5, leakDC = false)
    val c_4             = Protect(min_97, 0.5, 1.5, leakDC = false)
    val xi_8            = Protect(min_85, -inf, inf, leakDC = false)
    val yi_4            = Protect(min_35, -inf, inf, leakDC = false)
    val latoocarfianL_1 = LatoocarfianL.ar(freq = freq_14, a = a_4, b = b_2, c = c_4, d = d, xi = xi_8,
      yi = yi_4)
    val dur             = Protect(a_1, 5.0E-5, 100.0, leakDC = false)
    val width_2         = Protect(latoocarfianL_1, 0.0, 1.0, leakDC = false)
    val phase           = Protect(lFSaw_1, 0.0, 1.0, leakDC = false)
    val lFGauss         = LFGauss.ar(dur = dur, width = width_2, phase = phase, loop = loop,
      doneAction = doNothing)
    val in_10           = Protect(241.16264, -inf, inf, leakDC = true)
    val time_0          = Protect(min_64, 0.0, 30.0, leakDC = false)
    val decay           = Decay.ar(in_10, time = time_0)
    val min_101         = decay min min_82
    val min_102         = min_57 min min_101
    val min_103         = min_102 min min_101
    val min_104         = c_2 min min_84
    val min_105         = min_71 min min_37
    val freq_15         = Protect(min_85, -inf, inf, leakDC = false)
    val xi_9            = Protect(min_26, -inf, inf, leakDC = false)
    val yi_5            = Protect(min_103, -inf, inf, leakDC = false)
    val gbmanL_1        = GbmanL.ar(freq = freq_15, xi = xi_9, yi = yi_5)
    val min_106         = gbmanL_1 min lFGauss
    val geq             = min_105 >= min_106
    val roundTo         = geq roundTo min_104
    val min_107         = roundTo min lPF_0
    val min_108         = lPF_0 min min_107
    val min_109         = min_108 min -260.23517
    val min_110         = varSaw min min_4
    val min_111         = min_110 min min_7
    val min_112         = min_111 min min_64
    val min_113         = min_112 min min_25
    val min_114         = min_113 min min_28
    val min_115         = min_114 min bitOr
    val min_116         = min_115 min min_41
    val min_117         = min_116 min min_65
    val min_118         = min_117 min lFGauss
    val min_119         = min_118 min minus
    val min_120         = min_119 min 3.130919E-4
    val in_11           = Protect(min_120, -inf, inf, leakDC = true)
    val maxDelayTime_1  = Protect(min_56, 0.0, 20.0, leakDC = false)
    val protect_1       = Protect(c_0, 0.0, inf, leakDC = false)
    val delayTime_1     = protect_1 min maxDelayTime_1
    val delayL          = DelayL.ar(in_11, maxDelayTime = maxDelayTime_1, delayTime = delayTime_1)
    val min_121         = min_120 min lFDNoise1_0
    val in_12           = Protect(min_109, -inf, inf, leakDC = true)
    val winSize         = Protect(min_66, 0.001, 2.0, leakDC = false)
    val pitchRatio      = Protect(7.9544034, 0.0, 4.0, leakDC = false)
    val pitchDispersion = Protect(gbmanN, 0.0, 1.0, leakDC = false)
    val protect_2       = Protect(min_121, 0.0, inf, leakDC = false)
    val timeDispersion  = protect_2 min winSize
    val pitchShift      = PitchShift.ar(in_12, winSize = winSize, pitchRatio = pitchRatio,
      pitchDispersion = pitchDispersion, timeDispersion = timeDispersion)
    val min_122         = min_109 min lagUD
    val min_123         = min_3 min min_122
    val min_124         = min_123 min lFSaw_0
    val min_125         = min_79 min min_25
    val min_126         = min_125 min loop
    val min_127         = min_126 min min_67
    val min_128         = min_127 min c_2
    val in_13           = Protect(0.0, -inf, inf, leakDC = true)
    val maxDelayTime_2  = Protect(min_12, 0.0, 20.0, leakDC = false)
    val protect_3       = Protect(min_128, 0.0, inf, leakDC = false)
    val delayTime_2     = protect_3 min maxDelayTime_2
    val delayN          = DelayN.ar(in_13, maxDelayTime = maxDelayTime_2, delayTime = delayTime_2)
    val min_129         = min_116 min delayN
    val min_130         = min_129 min yi_0
    val min_131         = 3.130919E-4 min min_130
    val in_14           = Protect(35.973347, -inf, inf, leakDC = true)
    val time_1          = Protect(lFSaw_1, 0.0, 30.0, leakDC = false)
    val lag2            = Lag2.ar(in_14, time = time_1)
    val min_132         = lag2 min c_1
    val min_133         = min_131 min min_132
    val min_134         = linCongL_0 min min_133
    val min_135         = min_134 min min_44
    val min_136         = min_117 min min_133
    val min_137         = min_136 min min_83
    val in_15           = Protect(min_136, -inf, inf, leakDC = true)
    val freq_16         = Protect(min_4, 10.0, 20000.0, leakDC = false)
    val rq_0            = Protect(min_7, 0.01, 100.0, leakDC = false)
    val bPF             = BPF.ar(in_15, freq = freq_16, rq = rq_0)
    val min_138         = min_77 min min_58
    val in_16           = Protect(min_103, -inf, inf, leakDC = true)
    val bPZ2            = BPZ2.ar(in_16)
    val min_139         = linCongL_1 min bPZ2
    val min_140         = earlyRefLevel min min_58
    val min_141         = min_140 min min_132
    val min_142         = min_141 min min_7
    val min_143         = min_142 min min_83
    val min_144         = min_139 min min_143
    val min_145         = min_81 min min_4
    val in_17           = Protect(min_145, -inf, inf, leakDC = true)
    val lPF_1           = LPF.ar(in_17, freq = 97.30713)
    val freq_17         = Protect(min_104, -inf, inf, leakDC = false)
    val lFDNoise1_1     = LFDNoise1.ar(freq_17)
    val min_146         = min_145 min lFCub_0
    val in_18           = Protect(min_58, -inf, inf, leakDC = true)
    val coeff_1         = Protect(min_146, -1.0, 1.0, leakDC = false)
    val oneZero         = OneZero.ar(in_18, coeff = coeff_1)
    val min_147         = oneZero min min_146
    val min_148         = min_147 min min_146
    val min_149         = min_148 min min_146
    val min_150         = min_149 min min_146
    val in_19           = Protect(latoocarfianL_1, -inf, inf, leakDC = true)
    val protect_4       = Protect(3.7802913E-4, 0.55, inf, leakDC = false)
    val revTime         = Protect(c_2, 0.0, 100.0, leakDC = false)
    val damping         = Protect(min_64, 0.0, 1.0, leakDC = false)
    val spread          = Protect(min_144, 0.0, 43.0, leakDC = false)
    val maxRoomSize     = Protect(min_67, 0.55, 300.0, leakDC = false)
    val roomSize        = protect_4 min maxRoomSize
    val gVerb           = GVerb.ar(in_19, roomSize = roomSize, revTime = revTime, damping = damping,
      inputBW = 0.5, spread = spread, dryLevel = dryLevel,
      earlyRefLevel = earlyRefLevel, tailLevel = tailLevel, maxRoomSize = maxRoomSize)
    val mix             = Mix(
      Seq[GE](min_32, delayL, pitchShift, min_124, min_135, min_137, bPF, min_138, lPF_1, lFDNoise1_1, min_150, gVerb))
    NegatumOut(mix) // , defaultAmp = amp
  }

  def g1_51_4533(): Unit = {
    NegatumIn()
    val width_0         = Protect(23.773453, 0.0, 1.0, leakDC = false)
    val pulse           = Pulse.ar(freq = 23.773453, width = width_0)
    val freq_0          = Protect(pulse, -inf, inf, leakDC = false)
    val lFDNoise1_0     = LFDNoise1.ar(freq_0)
    val in_0            = Protect(lFDNoise1_0, -inf, inf, leakDC = true)
    val lPZ2            = LPZ2.ar(in_0)
    val min_0           = lPZ2 min -0.526761
    val min_1           = 6.700392 min min_0
    val in_1            = Protect(min_1, -inf, inf, leakDC = true)
    val c_0             = HPZ2.ar(in_1)
    val min_2           = 5083.3228 min min_1
    val min_3           = min_2 min min_1
    val min_4           = min_3 min min_1
    val min_5           = min_4 min min_1
    val min_6           = min_1 min min_5
    val min_7           = min_6 min min_1
    val min_8           = min_7 min min_1
    val absdif          = min_8 absDif min_1
    val min_9           = absdif min min_1
    val min_10          = min_9 min min_1
    val min_11          = min_10 min min_1
    val min_12          = min_11 min min_1
    val min_13          = min_12 min min_1
    val freq_1          = min_13 min min_1
    val min_14          = freq_1 min min_1
    val min_15          = min_14 min min_1
    val min_16          = min_15 min min_1
    val in_2            = Protect(pulse, -inf, inf, leakDC = true)
    val a_0             = Decay.ar(in_2, time = 23.773453)
    val in_3            = Protect(a_0, -inf, inf, leakDC = true)
    val maxDelayTime_0  = Protect(min_1, 0.0, 20.0, leakDC = false)
    val protect_0       = Protect(min_16, 0.0, inf, leakDC = false)
    val delayTime_0     = protect_0 min maxDelayTime_0
    val delayC          = DelayC.ar(in_3, maxDelayTime = maxDelayTime_0, delayTime = delayTime_0)
    val neq             = delayC sig_!= -151.92877
    val dur_0           = Protect(neq, 5.0E-5, 100.0, leakDC = false)
    val width_1         = Protect(a_0, 0.0, 1.0, leakDC = false)
    val phase           = Protect(pulse, 0.0, 1.0, leakDC = false)
    val lFGauss         = LFGauss.ar(dur = dur_0, width = width_1, phase = phase, loop = 6.700392,
      doneAction = doNothing)
    val yi_0            = Protect(lFGauss, -inf, inf, leakDC = false)
    val standardL_0     = StandardL.ar(freq = -0.0014333398, k = -260.23517, xi = 0.2771094, yi = yi_0)
    val in_4            = Protect(-0.0014333398, -inf, inf, leakDC = true)
    val delay2          = Delay2.ar(in_4)
    val min_17          = standardL_0 min delay2
    val in_5            = Protect(min_17, -inf, inf, leakDC = true)
    val lPZ1_0          = LPZ1.ar(in_5)
    val in_6            = Protect(lPZ1_0, -inf, inf, leakDC = true)
    val hPZ1_0          = HPZ1.ar(in_6)
    val min_18          = hPZ1_0 min 23.773453
    val freq_2          = Protect(min_18, -inf, inf, leakDC = false)
    val lFDNoise1_1     = LFDNoise1.ar(freq_2)
    val min_19          = lFDNoise1_1 min 23.773453
    val min_20          = min_19 min 23.773453
    val min_21          = min_20 min 23.773453
    val min_22          = min_21 min 23.773453
    val standardL_1     = StandardL.ar(freq = freq_1, k = -7595.3853, xi = 13.775431, yi = 0.108262725)
    val min_23          = min_22 min standardL_1
    val decayTime_0     = min_17 min lFGauss
    val min_24          = decayTime_0 min min_23
    val min_25          = min_24 min min_23
    val b_0             = min_25 min min_23
    val min_26          = b_0 min min_23
    val min_27          = min_26 min min_23
    val min_28          = min_27 min min_23
    val min_29          = min_10 min min_28
    val min_30          = min_29 min lFDNoise1_1
    val min_31          = min_30 min min_22
    val min_32          = min_31 min neq
    val lFDNoise0       = LFDNoise0.ar(8541.102)
    val min_33          = min_32 min lFDNoise0
    val min_34          = min_22 min 23.773453
    val min_35          = min_34 min 23.773453
    val min_36          = min_35 min 23.773453
    val in_7            = min_36 min 23.773453
    val freq_3          = Protect(b_0, -inf, inf, leakDC = false)
    val freq_4          = LFDClipNoise.ar(freq_3)
    val length          = Protect(freq_4, 1.0, 44100.0, leakDC = false)
    val decayTime_1     = RunningSum.ar(in_7, length = length)
    val min_37          = min_33 min decayTime_1
    val min_38          = min_37 min min_2
    val min_39          = lFDNoise0 min min_21
    val in_8            = Protect(8541.102, -inf, inf, leakDC = true)
    val freq_5          = Protect(min_39, 10.0, 20000.0, leakDC = false)
    val radius_0        = Protect(min_25, 0.0, 1.0, leakDC = false)
    val twoPole_0       = TwoPole.ar(in_8, freq = freq_5, radius = radius_0)
    val fold2           = min_29 fold2 twoPole_0
    val min_40          = min_38 min fold2
    val min_41          = min_40 min min_21
    val in_9            = Protect(min_15, -inf, inf, leakDC = true)
    val maxDelayTime_1  = Protect(min_14, 0.0, 20.0, leakDC = false)
    val protect_1       = Protect(min_41, 0.0, inf, leakDC = false)
    val delayTime_1     = protect_1 min maxDelayTime_1
    val a_1             = CombN.ar(in_9, maxDelayTime = maxDelayTime_1, delayTime = delayTime_1,
      decayTime = decayTime_0)
    val in_10           = Protect(min_28, -inf, inf, leakDC = true)
    val freq_6          = Protect(min_23, 10.0, 20000.0, leakDC = false)
    val radius_1        = Protect(min_24, 0.0, 1.0, leakDC = false)
    val twoPole_1       = TwoPole.ar(in_10, freq = freq_6, radius = radius_1)
    val min_42          = twoPole_1 min min_23
    val min_43          = min_42 min min_23
    val min_44          = min_43 min min_23
    val min_45          = min_44 min min_23
    val min_46          = min_45 min min_23
    val in_11           = Protect(-151.92877, -inf, inf, leakDC = true)
    val maxDelayTime_2  = Protect(min_23, 0.0, 20.0, leakDC = false)
    val protect_2       = Protect(min_46, 0.0, inf, leakDC = false)
    val delayTime_2     = protect_2 min maxDelayTime_2
    val a_2             = DelayN.ar(in_11, maxDelayTime = maxDelayTime_2, delayTime = delayTime_2)
    val freq_7          = Protect(min_1, -inf, inf, leakDC = false)
    val x0              = Protect(min_20, -inf, inf, leakDC = false)
    val x1_0            = Protect(min_14, -inf, inf, leakDC = false)
    val henonL          = HenonL.ar(freq = freq_7, a = a_2, b = b_0, x0 = x0, x1 = x1_0)
    val decayTime_2     = min_43 min henonL
    val min_47          = decayTime_2 min henonL
    val m               = min_47 min henonL
    val min_48          = m min henonL
    val min_49          = min_48 min henonL
    val min_50          = min_49 min henonL
    val min_51          = min_50 min henonL
    val min_52          = min_51 min henonL
    val min_53          = min_52 min henonL
    val min_54          = min_53 min henonL
    val min_55          = min_54 min henonL
    val min_56          = min_55 min henonL
    val min_57          = min_56 min henonL
    val min_58          = min_57 min henonL
    val min_59          = min_58 min henonL
    val min_60          = decayTime_0 min min_59
    val c_1             = min_60 min min_10
    val b_1             = c_1 min a_1
    val a_3             = min_13 min b_1
    val xi_0            = Protect(min_45, -inf, inf, leakDC = false)
    val c_2             = LinCongL.ar(freq = Nyquist() /* could not parse! */, a = a_3, c = c_0,
      m = -7595.3853, xi = xi_0)
    val xi_1            = Protect(a_3, -inf, inf, leakDC = false)
    val quadC           = QuadC.ar(freq = 8406.5205, a = a_1, b = b_1, c = c_2, xi = xi_1)
    val hypot           = min_7 hypot quadC
    val in_12           = Protect(min_45, -inf, inf, leakDC = true)
    val dur_1           = Protect(hypot, 0.0, 30.0, leakDC = false)
    val ramp            = Ramp.ar(in_12, dur = dur_1)
    val min_61          = min_45 min ramp
    val min_62          = min_42 min henonL
    val min_63          = min_62 min henonL
    val in_13           = Protect(0.0012950874, -inf, inf, leakDC = true)
    val freq_8          = Protect(min_61, 10.0, 20000.0, leakDC = false)
    val rq_0            = Protect(min_63, 0.01, 100.0, leakDC = false)
    val rHPF            = RHPF.ar(in_13, freq = freq_8, rq = rq_0)
    val min_64          = b_1 min min_7
    val in_14           = Protect(-9136.164, -inf, inf, leakDC = true)
    val hPZ1_1          = HPZ1.ar(in_14)
    val min_65          = min_64 min hPZ1_1
    val in_15           = Protect(min_63, -inf, inf, leakDC = true)
    val delay1          = Delay1.ar(in_15)
    val min_66          = henonL min delay1
    val min_67          = min_66 min a_0
    val min_68          = min_67 min henonL
    val freq_9          = Protect(min_52, 0.01, 20000.0, leakDC = false)
    val iphase_0        = Protect(min_68, 0.0, 1.0, leakDC = false)
    val width_2         = Protect(a_0, 0.0, 1.0, leakDC = false)
    val varSaw          = VarSaw.ar(freq = freq_9, iphase = iphase_0, width = width_2)
    val min_69          = min_65 min varSaw
    val freq_10         = Protect(min_69, 10.0, 20000.0, leakDC = false)
    val saw_0           = Saw.ar(freq_10)
    val earlyRefLevel   = 0.029109515 min min_56
    val min_70          = min_66 min min_21
    val min_71          = -0.0014333398 min min_70
    val min_72          = decayTime_0 min c_1
    val min_73          = min_72 min min_38
    val min_74          = min_71 min min_73
    val min_75          = min_74 min min_36
    val in_16           = Protect(0.108262725, -inf, inf, leakDC = true)
    val maxDelayTime_3  = Protect(min_75, 0.0, 20.0, leakDC = false)
    val protect_3       = Protect(earlyRefLevel, 0.0, inf, leakDC = false)
    val delayTime_3     = protect_3 min maxDelayTime_3
    val combN           = CombN.ar(in_16, maxDelayTime = maxDelayTime_3, delayTime = delayTime_3,
      decayTime = decayTime_1)
    val min_76          = min_69 min combN
    val min_77          = min_76 min min_57
    val min_78          = min_77 min min_11
    val min_79          = min_63 min min_77
    val min_80          = min_79 min min_77
    val min_81          = min_80 min min_77
    val min_82          = min_81 min min_77
    val geq             = min_82 >= hPZ1_1
    val min_83          = min_82 min min_77
    val min_84          = min_83 min min_77
    val min_85          = min_84 min min_77
    val in_17           = Protect(0.029109515, -inf, inf, leakDC = true)
    val maxDelayTime_4  = Protect(min_77, 0.0, 20.0, leakDC = false)
    val protect_4       = Protect(min_85, 0.0, inf, leakDC = false)
    val delayTime_4     = protect_4 min maxDelayTime_4
    val combC           = CombC.ar(in_17, maxDelayTime = maxDelayTime_4, delayTime = delayTime_4,
      decayTime = decayTime_2)
    val sumsqr          = decayTime_1 sumSqr varSaw
    val min_86          = sumsqr min min_0
    val min_87          = min_86 min 0.0012950874
    val bitOr           = min_74 | hypot
    val freq_11         = Protect(min_51, -inf, inf, leakDC = false)
    val lFDNoise1_2     = LFDNoise1.ar(freq_11)
    val in_18           = Protect(henonL, -inf, inf, leakDC = true)
    val coeff_0         = Protect(13.775431, -0.999, 0.999, leakDC = false)
    val onePole         = OnePole.ar(in_18, coeff = coeff_0)
    val min_88          = min_39 min min_79
    val min_89          = min_88 min min_24
    val in_19           = Protect(combN, -inf, inf, leakDC = true)
    val bRZ2            = BRZ2.ar(in_19)
    val min_90          = min_68 min henonL
    val in_20           = Protect(henonL, -inf, inf, leakDC = true)
    val freq_12         = Protect(min_90, 10.0, 20000.0, leakDC = false)
    val rq_1            = Protect(min_6, 0.01, 100.0, leakDC = false)
    val bRF             = BRF.ar(in_20, freq = freq_12, rq = rq_1)
    val min_91          = min_61 min min_81
    val min_92          = min_91 min freq_4
    val roundTo         = bRF roundTo min_92
    val a_4             = bRZ2 min roundTo
    val in_21           = Protect(a_4, -inf, inf, leakDC = true)
    val freq_13         = Protect(min_60, 10.0, 20000.0, leakDC = false)
    val rLPF            = RLPF.ar(in_21, freq = freq_13, rq = 1.0)
    val min_93          = rLPF min min_69
    val clip2           = -7595.3853 clip2 b_1
    val in_22           = Protect(-7595.3853, -inf, inf, leakDC = true)
    val coeff_1         = Protect(ramp, -1.0, 1.0, leakDC = false)
    val tailLevel       = OneZero.ar(in_22, coeff = coeff_1)
    val in_23           = Protect(min_13, -inf, inf, leakDC = true)
    val protect_5       = Protect(min_93, 0.55, inf, leakDC = false)
    val revTime         = Protect(-260.23517, 0.0, 100.0, leakDC = false)
    val damping         = Protect(combN, 0.0, 1.0, leakDC = false)
    val spread          = Protect(1225.153, 0.0, 43.0, leakDC = false)
    val maxRoomSize     = Protect(min_51, 0.55, 300.0, leakDC = false)
    val roomSize        = protect_5 min maxRoomSize
    val gVerb           = GVerb.ar(in_23, roomSize = roomSize, revTime = revTime, damping = damping,
      inputBW = 0.029109515, spread = spread, dryLevel = 13.775431,
      earlyRefLevel = earlyRefLevel, tailLevel = tailLevel, maxRoomSize = maxRoomSize)
    val min_94          = a_3 min min_77
    val min_95          = min_59 min henonL
    val in_24           = Protect(min_14, -inf, inf, leakDC = true)
    val winSize         = Protect(henonL, 0.001, 2.0, leakDC = false)
    val pitchRatio      = Protect(onePole, 0.0, 4.0, leakDC = false)
    val protect_6       = Protect(min_95, 0.0, inf, leakDC = false)
    val timeDispersion  = protect_6 min winSize
    val b_2             = PitchShift.ar(in_24, winSize = winSize, pitchRatio = pitchRatio,
      pitchDispersion = 0.2771094, timeDispersion = timeDispersion)
    val freq_14         = Protect(min_78, -inf, inf, leakDC = false)
    val x1_1            = Protect(min_94, -inf, inf, leakDC = false)
    val henonC          = HenonC.ar(freq = freq_14, a = a_0, b = b_2, x0 = -0.526761, x1 = x1_1)
    val min_96          = henonC min min_43
    val min_97          = min_96 min lFDNoise1_0
    val xi_2            = Protect(b_0, -inf, inf, leakDC = false)
    val linCongN        = LinCongN.ar(freq = freq_4, a = a_4, c = c_1, m = m, xi = xi_2)
    val freq_15         = Protect(min_74, -inf, inf, leakDC = false)
    val xi_3            = Protect(linCongN, -inf, inf, leakDC = false)
    val yi_1            = Protect(min_12, -inf, inf, leakDC = false)
    val gbmanL          = GbmanL.ar(freq = freq_15, xi = xi_3, yi = yi_1)
    val in_25           = Protect(gbmanL, -inf, inf, leakDC = true)
    val lPZ1_1          = LPZ1.ar(in_25)
    val in_26           = Protect(standardL_1, -inf, inf, leakDC = true)
    val radius_2        = Protect(bRF, 0.0, 1.0, leakDC = false)
    val twoPole_2       = TwoPole.ar(in_26, freq = 8541.102, radius = radius_2)
    val min_98          = absdif min henonL
    val min_99          = min_98 min henonL
    val min_100         = min_99 min henonL
    val freq_16         = Protect(min_51, 0.01, 20000.0, leakDC = false)
    val iphase_1        = Protect(varSaw, 0.0, 1.0, leakDC = false)
    val lFPar           = LFPar.ar(freq = freq_16, iphase = iphase_1)
    val hypotx          = lFPar hypotApx min_76
    val min_101         = min_73 min min_29
    val min_102         = min_75 min min_20
    val freq_17         = Protect(min_38, 10.0, 20000.0, leakDC = false)
    val saw_1           = Saw.ar(freq_17)
    val min_103         = min_89 min sumsqr
    val iphase_2        = Protect(pulse, 0.0, 1.0, leakDC = false)
    val lFCub           = LFCub.ar(freq = 23.773453, iphase = iphase_2)
    val min_104         = in_7 min 23.773453
    val in_27           = Protect(-9136.164, -inf, inf, leakDC = true)
    val hPZ2            = HPZ2.ar(in_27)
    val mix             = Mix(
      Seq[GE](rHPF, saw_0, geq, combC, min_87, bitOr, lFDNoise1_2, clip2, gVerb, min_97, lPZ1_1, twoPole_2, min_100, hypotx, min_101, min_102, saw_1, min_103, lFCub, min_104, hPZ2))
    NegatumOut(mix) // , defaultAmp = amp
  }
}
