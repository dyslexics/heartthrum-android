package de.dvld.heartthrum

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.abs

/** Direct port of the iOS PPG pipeline: bandpass 0.7–3.5 Hz → peak
 *  detection → median BPM from inter-beat intervals. Red and green channel
 *  run in parallel; the more pulsatile channel wins. */
class PulseProcessor {

    data class Output(
        val fingerDetected: Boolean,
        val bpm: Double?,
        val quality: Double,
        val waveform: List<Double>
    )

    private var green = ChannelChain()
    private var red = ChannelChain()

    fun reset() {
        green = ChannelChain()
        red = ChannelChain()
    }

    fun process(time: Double, r: Double, g: Double, b: Double): Output {
        val fingerDetected = r > 0.4 && b < 0.35 && r > b * 1.8
        green.add(time, g)
        red.add(time, r)
        val best = if (green.score >= red.score) green else red
        return Output(
            fingerDetected = fingerDetected,
            bpm = if (fingerDetected) best.bpm else null,
            quality = if (fingerDetected) best.quality else 0.0,
            waveform = best.displayWaveform()
        )
    }
}

private class ChannelChain {
    private val hp = Biquad.highpass(0.7, 30.0)
    private val lp = Biquad.lowpass(3.5, 30.0)

    private val times = ArrayDeque<Double>()
    private val filtered = ArrayDeque<Double>()
    private val peakTimes = ArrayDeque<Double>()
    private var warmupCount = 0

    var bpm: Double? = null; private set
    var quality = 0.0; private set
    var score = 0.0; private set

    fun add(time: Double, value: Double) {
        var v = hp.step(value)
        v = lp.step(v)
        warmupCount++
        if (warmupCount < 45) return

        times.addLast(time)
        filtered.addLast(v)
        while (times.isNotEmpty() && time - times.first() > 12) {
            times.removeFirst(); filtered.removeFirst()
        }
        detectPeak(time)
        updateEstimate(time)
    }

    private fun detectPeak(now: Double) {
        val n = filtered.size
        if (n < 3) return
        val list = filtered.toList()
        val a = list[n - 3]; val b = list[n - 2]; val c = list[n - 1]
        if (!(b > a && b >= c)) return

        val recent = list.takeLast(90)
        val rms = sqrt(recent.sumOf { it * it } / recent.size)
        if (rms <= 1e-6 || b <= 0.5 * rms) return

        val peakTime = times.toList()[n - 2]
        if (peakTimes.isNotEmpty() && peakTime - peakTimes.last() < 0.30) return
        peakTimes.addLast(peakTime)
        while (peakTimes.isNotEmpty() && peakTime - peakTimes.first() > 15) peakTimes.removeFirst()
    }

    private fun updateEstimate(now: Double) {
        val recent = filtered.toList().takeLast(120)
        if (recent.size > 30) {
            val mean = recent.average()
            score = sqrt(recent.sumOf { (it - mean) * (it - mean) } / recent.size)
        }
        val peaks = peakTimes.filter { now - it <= 12 }
        if (peaks.size < 4) { bpm = null; quality = 0.0; return }
        val ibis = ArrayList<Double>()
        for (i in 1 until peaks.size) {
            val d = peaks[i] - peaks[i - 1]
            if (d in (60.0 / 210.0)..(60.0 / 42.0)) ibis.add(d)
        }
        if (ibis.size < 3) { bpm = null; quality = 0.0; return }
        val sorted = ibis.sorted()
        bpm = 60.0 / sorted[sorted.size / 2]
        val mean = ibis.average()
        val sd = sqrt(ibis.sumOf { (it - mean) * (it - mean) } / ibis.size)
        quality = max(0.0, min(1.0, 1.2 - (sd / mean) * 4))
    }

    fun displayWaveform(): List<Double> {
        val recent = filtered.toList().takeLast(120)
        val mx = recent.maxOfOrNull { abs(it) } ?: return emptyList()
        if (mx < 1e-9) return emptyList()
        return recent.map { it / mx }
    }
}

private class Biquad private constructor(
    private val b0: Double, private val b1: Double, private val b2: Double,
    private val a1: Double, private val a2: Double
) {
    private var x1 = 0.0; private var x2 = 0.0
    private var y1 = 0.0; private var y2 = 0.0

    fun step(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x
        y2 = y1; y1 = y
        return y
    }

    companion object {
        fun lowpass(fc: Double, fs: Double, q: Double = 0.707): Biquad {
            val w = 2 * PI * fc / fs
            val alpha = sin(w) / (2 * q); val cw = cos(w); val a0 = 1 + alpha
            return Biquad(((1 - cw) / 2) / a0, (1 - cw) / a0, ((1 - cw) / 2) / a0,
                (-2 * cw) / a0, (1 - alpha) / a0)
        }
        fun highpass(fc: Double, fs: Double, q: Double = 0.707): Biquad {
            val w = 2 * PI * fc / fs
            val alpha = sin(w) / (2 * q); val cw = cos(w); val a0 = 1 + alpha
            return Biquad(((1 + cw) / 2) / a0, (-(1 + cw)) / a0, ((1 + cw) / 2) / a0,
                (-2 * cw) / a0, (1 - alpha) / a0)
        }
    }
}
