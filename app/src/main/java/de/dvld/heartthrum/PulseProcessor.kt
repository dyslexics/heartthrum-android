package de.dvld.heartthrum

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp

/** Timestamped RGB → uniform 120 Hz bandpass → sub-sample peaks → robust mean IBI.
 * Call process/reset on the same thread. See README for timing and validity limits. */
class PulseProcessor {

    data class Output(
        val fingerDetected: Boolean,
        val bpm: Double?,
        val quality: Double,
        val waveform: List<Double>,
        val discontinuity: Boolean = false
    )

    private var green = ChannelChain()
    private var red = ChannelChain()
    private var previousTime: Double? = null
    private var origin = 0.0
    private var gridIndex = 0L
    private var previousGreen = 0.0
    private var previousRed = 0.0
    private var smoothGreen = InputLowpass()
    private var smoothRed = InputLowpass()

    fun reset() {
        green = ChannelChain()
        red = ChannelChain()
        previousTime = null
        gridIndex = 0
        smoothGreen = InputLowpass()
        smoothRed = InputLowpass()
    }

    fun process(time: Double, r: Double, g: Double, b: Double): Output {
        if (!time.isFinite() || time < 0 || listOf(r, g, b).any { !it.isFinite() || it !in 0.0..1.0 }) {
            reset()
            return Output(false, null, 0.0, emptyList(), discontinuity = true)
        }
        // Red-dominance ratio: works in darkness AND daylight (ambient light
        // leaking around the fingertip adds green/blue in bright rooms).
        val dominance = r / max(0.01, max(g, b))
        val fingerDetected = r > 0.18 && dominance > 1.35
        if (!fingerDetected) {
            reset()
            return Output(false, null, 0.0, emptyList())
        }
        val previous = previousTime
        if (previous != null && time <= previous) {
            reset()
            return Output(true, null, 0.0, emptyList(), discontinuity = true)
        }
        val discontinuity = previous != null && time - previous > MAX_INPUT_GAP
        if (discontinuity) reset()
        val start = previousTime
        if (start == null) {
            origin = time
            previousTime = time
            previousGreen = smoothGreen.seed(g)
            previousRed = smoothRed.seed(r)
            green.add(0.0, g)
            red.add(0.0, r)
            gridIndex = 1
        } else {
            val dt = time - start
            val nextGreen = smoothGreen.step(g, dt)
            val nextRed = smoothRed.step(r, dt)
            val relativeTime = time - origin
            val previousRelative = start - origin
            while (gridIndex * SAMPLE_PERIOD <= relativeTime + 1e-9) {
                val gridTime = gridIndex * SAMPLE_PERIOD
                val fraction = ((gridTime - previousRelative) / dt).coerceIn(0.0, 1.0)
                green.add(gridTime, previousGreen + fraction * (nextGreen - previousGreen))
                red.add(gridTime, previousRed + fraction * (nextRed - previousRed))
                gridIndex++
            }
            previousTime = time
            previousGreen = nextGreen
            previousRed = nextRed
        }
        val best = listOf(green, red).maxBy {
            if (it.bpm != null) it.score * it.quality else 0.0
        }.let { candidate ->
            if (candidate.bpm != null) candidate else if (green.score >= red.score) green else red
        }
        return Output(
            fingerDetected = true,
            bpm = best.bpm,
            quality = best.quality,
            waveform = best.displayWaveform(),
            discontinuity = discontinuity
        )
    }

    companion object {
        internal const val SAMPLE_RATE = 120.0
        internal const val SAMPLE_PERIOD = 1.0 / SAMPLE_RATE
        internal const val MAX_INPUT_GAP = 0.25
        internal const val ESTIMATE_EXPIRY = 3.0
    }
}

/** Two time-aware RC poles ahead of resampling, attenuating out-of-band input.
 * The 120 Hz grid avoids decimation for the supported 15–60 FPS cameras. */
private class InputLowpass {
    private var first = 0.0
    private var second = 0.0

    fun seed(value: Double): Double {
        first = value
        second = value
        return value
    }

    fun step(value: Double, dt: Double): Double {
        val alpha = 1 - exp(-2 * PI * 5.0 * dt)
        first += alpha * (value - first)
        second += alpha * (first - second)
        return second
    }
}

private class ChannelChain {
    private val hp = Biquad.highpass(0.7, PulseProcessor.SAMPLE_RATE)
    private val lp = Biquad.lowpass(3.5, PulseProcessor.SAMPLE_RATE)

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
        if (warmupCount < 2 * PulseProcessor.SAMPLE_RATE) return

        times.addLast(time)
        filtered.addLast(v)
        while (times.isNotEmpty() && time - times.first() > 4) {
            times.removeFirst(); filtered.removeFirst()
        }
        detectPeak()
        updateEstimate(time)
    }

    private fun detectPeak() {
        val n = filtered.size
        if (n < 3) return
        val list = filtered.toList()
        val a = list[n - 3]; val b = list[n - 2]; val c = list[n - 1]
        if (!(b > a && b >= c)) return

        val recent = list.takeLast((3 * PulseProcessor.SAMPLE_RATE).toInt())
        val rms = sqrt(recent.sumOf { it * it } / recent.size)
        if (rms <= 1e-4 || b <= max(2e-4, 0.5 * rms)) return

        val denominator = a - 2 * b + c
        val offset = if (abs(denominator) > 1e-12) 0.5 * (a - c) / denominator else 0.0
        val safeOffset = if (offset.isFinite() && abs(offset) <= 0.5) offset else 0.0
        val peakTime = times.toList()[n - 2] + safeOffset * PulseProcessor.SAMPLE_PERIOD
        if (peakTimes.isNotEmpty() && peakTime - peakTimes.last() < 0.25) return
        peakTimes.addLast(peakTime)
        while (peakTimes.isNotEmpty() && peakTime - peakTimes.first() > 15) peakTimes.removeFirst()
    }

    private fun updateEstimate(now: Double) {
        val recent = filtered.toList().takeLast((4 * PulseProcessor.SAMPLE_RATE).toInt())
        if (recent.size > PulseProcessor.SAMPLE_RATE) {
            val mean = recent.average()
            score = sqrt(recent.sumOf { (it - mean) * (it - mean) } / recent.size)
        }
        val peaks = peakTimes.filter { now - it <= 8 }
        if (peaks.size < 4) { bpm = null; quality = 0.0; return }
        val intervals = ArrayList<Pair<Double, Double>>()
        for (i in 1 until peaks.size) {
            val d = peaks[i] - peaks[i - 1]
            intervals.add(d to peaks[i])
        }
        // Small guard bands avoid selectively rejecting timing error at 42/210 BPM.
        val plausible = intervals.filter { it.first in (60.0 / 215.0)..(60.0 / 40.0) }
        val sorted = plausible.map { it.first }.sorted()
        val median = sorted.getOrNull(sorted.size / 2)
        // Keep original adjacent intervals, never bridge across a rejected beat.
        val accepted = if (median == null) emptyList() else plausible.filter {
            abs(it.first - median) <= median * 0.25
        }
        if (accepted.size < 3 || now - accepted.last().second > PulseProcessor.ESTIMATE_EXPIRY ||
            score < 1e-4) {
            bpm = null; quality = 0.0; return
        }
        val ibis = accepted.map { it.first }
        val mean = ibis.average()
        bpm = 60.0 / mean
        val sd = sqrt(ibis.sumOf { (it - mean) * (it - mean) } / ibis.size)
        val regularity = (1.0 - (sd / mean) * 4).coerceIn(0.0, 1.0)
        val coverage = accepted.size.toDouble() / intervals.size
        val confidence = min(1.0, accepted.size / 5.0)
        val freshness = ((PulseProcessor.ESTIMATE_EXPIRY - (now - accepted.last().second)) /
            (PulseProcessor.ESTIMATE_EXPIRY - 1.5)).coerceIn(0.0, 1.0)
        quality = regularity * coverage * confidence * freshness
    }

    fun displayWaveform(): List<Double> {
        val recent = filtered.toList().takeLast((4 * PulseProcessor.SAMPLE_RATE).toInt())
        val mx = recent.maxOfOrNull { abs(it) } ?: return emptyList()
        if (mx < 1e-9) return emptyList()
        // Keep the existing ~30 points/second display, independent of the DSP grid.
        return recent.filterIndexed { index, _ -> index % 4 == 0 }.map { it / mx }
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
