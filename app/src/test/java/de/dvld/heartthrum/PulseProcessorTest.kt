package de.dvld.heartthrum

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Test

class PulseProcessorTest {
    private data class Reading(val time: Double, val output: PulseProcessor.Output)

    private fun sample(processor: PulseProcessor, time: Double, phase: Double,
                       noise: Double = 0.0, artifact: Double = 0.0): PulseProcessor.Output {
        val pulse = cos(phase) + 0.25 * cos(2 * phase + 0.5) + 0.08 * cos(3 * phase + 0.8)
        return processor.process(time, 0.72 + 0.008 * pulse + noise * 0.5 + artifact,
            0.28 + 0.016 * pulse + noise + artifact, 0.12)
    }

    private fun runSignal(
        bpm: Double,
        fps: Double = 30.0,
        seconds: Double = 40.0,
        processor: PulseProcessor = PulseProcessor(),
        start: Double = 0.0,
        jitter: Boolean = false,
        drops: Boolean = false,
        noise: Boolean = false,
        artifacts: Boolean = false,
        rate: (Double) -> Double = { bpm }
    ): List<Reading> {
        val random = Random(137)
        val readings = ArrayList<Reading>()
        var time = start
        var phase = 0.0
        var index = 0
        while (time < start + seconds) {
            val dt = (if (jitter) 0.8 + random.nextDouble() * 0.4 else 1.0) / fps
            time += dt
            phase += 2 * PI * rate(time - start) / 60 * dt
            val skip = drops && index % 17 == 9
            val n = if (noise) random.nextGaussian() * 0.002 else 0.0
            val artifact = if (artifacts && index % (fps * 9).toInt() in 0..1) 0.07 else 0.0
            if (!skip) readings.add(Reading(time, sample(processor, time, phase, n, artifact)))
            index++
        }
        return readings
    }

    private fun assertAccurate(readings: List<Reading>, expected: Double, label: String,
                               after: Double = 16.0) {
        val settled = readings.filter { it.time >= after }
        val estimates = settled.mapNotNull { it.output.bpm }
        assertTrue("$label availability ${estimates.size}/${settled.size}",
            estimates.size >= settled.size * 0.95)
        val mean = estimates.average()
        val worst = estimates.maxOf { abs(it - expected) }
        assertTrue("$label mean=$mean, worst error=$worst", worst <= 1.0)
        assertTrue("$label display=${mean.roundToInt()}", abs(mean.roundToInt() - expected) <= 1)
        assertTrue("$label quality", settled.map { it.output.quality }.average() > 0.5)
        println("$label: mean=%.5f displayed=%d maxError=%.5f".format(mean, mean.roundToInt(), worst))
    }

    @Test fun cleanRatesAtEverySupportedCameraRate() {
        for (fps in listOf(15.0, 24.0, 30.0, 60.0)) {
            for (bpm in listOf(42.0, 48.0, 60.0, 68.0, 70.0, 73.0, 74.0, 76.0, 80.0, 100.0, 150.0, 180.0, 210.0)) {
                assertAccurate(runSignal(bpm, fps), bpm, "$fps FPS / $bpm BPM")
            }
        }
    }

    @Test fun nearbyRatesDoNotCollapseToFramePlateaus() {
        val estimates = (68..80).map { bpm ->
            val readings = runSignal(bpm.toDouble()).filter { it.time >= 16 }
            val estimate = readings.mapNotNull { it.output.bpm }.average()
            assertEquals(bpm.toDouble(), estimate, 0.25)
            assertEquals(bpm, estimate.roundToInt())
            val source = readings.filter { it.output.quality > 0.3 }
                .map { it.time to it.output.bpm!! }
            val final = aggregateMeasurementBpm(source, 0.0, readings.last().time, 40.0)
            assertNotNull(final)
            assertEquals(bpm.toDouble(), final!!, 0.25)
            assertEquals(bpm, final.roundToInt())
            estimate
        }
        estimates.zipWithNext().forEach { (a, b) ->
            assertTrue("Adjacent rates must remain distinguishable: $a, $b", b - a > 0.7)
        }
    }

    @Test fun jitterAndDroppedFramesUseActualTimestamps() {
        for (fps in listOf(15.0, 24.0, 30.0, 60.0)) {
            for (bpm in listOf(68.0, 74.0, 80.0)) {
                assertAccurate(runSignal(bpm, fps, jitter = true, drops = true),
                    bpm, "jitter/drops $fps FPS / $bpm BPM")
            }
        }
    }

    @Test fun reproducibleNoiseAndOccasionalArtifacts() {
        for (fps in listOf(15.0, 30.0, 60.0)) {
            val readings = runSignal(74.0, fps, seconds = 55.0, jitter = true,
                drops = true, noise = true, artifacts = true).filter { it.time > 16 }
            val usable = readings.filter { it.output.bpm != null && it.output.quality > 0.3 }
            assertTrue("usable at $fps FPS: ${usable.size}/${readings.size}",
                usable.size > readings.size * 0.7)
            val error = usable.map { abs(it.output.bpm!! - 74.0) }.average()
            assertTrue("noisy/artifact error=$error at $fps FPS", error < 1.0)
            assertTrue("artifacts must reduce confidence", readings.any { it.output.quality < 0.8 })
            println("noise/artifacts $fps FPS: mean absolute error=%.3f availability=%.3f".format(
                error, usable.size.toDouble() / readings.size))
        }
    }

    @Test fun rateStepSettlesWithinTenSeconds() {
        val readings = runSignal(70.0, seconds = 50.0, rate = { if (it < 24) 70.0 else 90.0 })
        assertAccurate(readings.filter { it.time < 24 }, 70.0, "before step")
        assertAccurate(readings, 90.0, "after step", after = 34.0)
        val tracking = readings.first { it.time > 24 && it.output.bpm?.let { b -> abs(b - 90) < 1 } == true }
        assertTrue("step latency=${tracking.time - 24}", tracking.time - 24 <= 10.0)
        println("70->90 BPM step latency: %.3f seconds".format(tracking.time - 24))
    }

    @Test fun startupResetAndFingerRemovalCannotReuseAnEstimate() {
        val processor = PulseProcessor()
        val readings = runSignal(74.0, processor = processor)
        assertTrue(readings.filter { it.time < 3.5 }.all { it.output.bpm == null && it.output.quality == 0.0 })
        assertNotNull(readings.last().output.bpm)
        val removed = processor.process(40.1, 0.1, 0.1, 0.1)
        assertFalse(removed.fingerDetected)
        assertNull(removed.bpm)
        assertEquals(0.0, removed.quality, 0.0)
        assertTrue(removed.waveform.isEmpty())
        assertTrue(runSignal(80.0, seconds = 3.0, processor = processor, start = 40.2)
            .all { it.output.bpm == null })
        processor.reset()
        assertTrue(runSignal(68.0, seconds = 3.0, processor = processor).all { it.output.bpm == null })
        assertAccurate(runSignal(80.0, processor = processor, start = 50.0),
            80.0, "after reset", after = 66.0)
    }

    @Test fun longGapsAndClockDiscontinuitiesRestartWarmup() {
        for (jump in listOf(0.5, 5.0, -20.0, 0.0)) {
            val processor = PulseProcessor()
            val last = runSignal(74.0, processor = processor).last()
            val time = last.time + jump
            val output = sample(processor, time, 0.0)
            assertNull("jump=$jump", output.bpm)
            assertEquals(0.0, output.quality, 0.0)
            assertTrue(output.discontinuity)
            assertTrue("no interpolation across gap", output.waveform.isEmpty())
            val recovered = runSignal(80.0, processor = processor, start = time + 0.01)
            assertTrue(recovered.filter { it.time < time + 3 }.all { it.output.bpm == null })
            assertAccurate(recovered, 80.0, "recovery jump=$jump", after = time + 16)
        }
    }

    @Test fun noPeaksExpireEvenWithFingerStillPresent() {
        val processor = PulseProcessor()
        val last = runSignal(74.0, processor = processor).last()
        assertNotNull(last.output.bpm)
        val loss = (1..360).map { i ->
            Reading(last.time + i / 30.0,
                processor.process(last.time + i / 30.0, 0.72, 0.28, 0.12))
        }
        // Allow bandpass ringing to decay, then the explicit 3-second peak expiry.
        assertTrue(loss.filter { it.time > last.time + 6 }.all {
            it.output.fingerDetected && it.output.bpm == null && it.output.quality == 0.0
        })
    }

    @Test fun constantOrTinySignalsDoNotClaimValidity() {
        for (amplitude in listOf(0.0, 1e-8)) {
            val processor = PulseProcessor()
            val outputs = (0..1200).map { i ->
                val value = amplitude * cos(i * 2 * PI / 24)
                processor.process(i / 30.0, 0.72 + value, 0.28 + value, 0.12)
            }
            assertTrue(outputs.all { it.bpm == null && it.quality == 0.0 })
            assertTrue(outputs.flatMap { it.waveform }.all { it.isFinite() && abs(it) <= 1.0 })
        }
    }

    @Test fun invalidInputsResetWithoutPoisoningLaterSamples() {
        val invalid = listOf(
            doubleArrayOf(Double.NaN, 0.72, 0.28, 0.12),
            doubleArrayOf(Double.POSITIVE_INFINITY, 0.72, 0.28, 0.12),
            doubleArrayOf(-1.0, 0.72, 0.28, 0.12),
            doubleArrayOf(41.0, Double.NaN, 0.28, 0.12),
            doubleArrayOf(41.0, 0.72, Double.NEGATIVE_INFINITY, 0.12),
            doubleArrayOf(41.0, 1.01, 0.28, 0.12),
            doubleArrayOf(41.0, 0.72, 0.28, -0.01)
        )
        val processor = PulseProcessor()
        runSignal(74.0, processor = processor)
        for (input in invalid) {
            val out = processor.process(input[0], input[1], input[2], input[3])
            assertFalse(out.fingerDetected)
            assertNull(out.bpm)
            assertEquals(0.0, out.quality, 0.0)
        }
        assertAccurate(runSignal(74.0, processor = processor, start = 100.0),
            74.0, "invalid input recovery", after = 116.0)
    }

    @Test fun largeTimestampOriginsDoNotChangeRate() {
        assertAccurate(runSignal(74.0, start = 9_000_000.0, jitter = true),
            74.0, "large clock origin", after = 9_000_016.0)
    }

    @Test fun finalSummaryKeepsPrecisionAndRejectsStaleReadings() {
        val readings = (0..400).map { it / 10.0 to if (it < 160) 90.0 else 73.8 }
        assertEquals(73.8, aggregateMeasurementBpm(readings, 0.0, 40.0, 40.0)!!, 0.0)
        assertNull(aggregateMeasurementBpm(emptyList(), 0.0, 40.0, 40.0))
        assertNull(aggregateMeasurementBpm(readings.filter { it.first < 37 }, 0.0, 40.0, 40.0))
        assertNull(aggregateMeasurementBpm(readings, 0.0, 39.0, 40.0))
        assertEquals(73.8, aggregateMeasurementBpm(listOf(39.0 to 73.8), 0.0, 40.0, 40.0)!!, 0.0)
    }

    @Test fun missedBeatsAreNotBridgedOrCountedAsSlowBeats() {
        val processor = PulseProcessor()
        val readings = (0..1800).map { i ->
            val time = i / 30.0
            val phase = time * 2 * PI * 74 / 60
            val missing = (time * 74 / 60).toInt() % 9 == 4
            val out = if (missing) processor.process(time, 0.72, 0.28, 0.12)
                else sample(processor, time, phase)
            Reading(time, out)
        }.filter { it.time > 16 }
        val usable = readings.filter { it.output.bpm != null && it.output.quality > 0.3 }
        assertTrue(usable.size > readings.size * 0.7)
        assertTrue("missing beat bias", abs(usable.map { it.output.bpm!! }.average() - 74) < 1)
        assertTrue("missing beats lower confidence", readings.any { it.output.quality < 0.8 })
    }

    @Test fun eitherColorChannelCanSupplyTheEstimate() {
        for (useRed in listOf(false, true)) {
            val processor = PulseProcessor()
            val readings = (0..1200).map { i ->
                val t = i / 30.0
                val pulse = 0.016 * cos(t * 2 * PI * 74 / 60)
                Reading(t, processor.process(t, 0.72 + if (useRed) pulse else 0.0,
                    0.28 + if (useRed) 0.0 else pulse, 0.12))
            }
            assertAccurate(readings, 74.0, "red=$useRed")
        }
    }

    @Test fun samplingRateCanChangeWithoutResettingPhysiologicalHistory() {
        val processor = PulseProcessor()
        val readings = ArrayList<Reading>()
        var time = 0.0
        while (time < 45) {
            val fps = if (time < 20) 15.0 else 60.0
            time += 1.0 / fps
            readings.add(Reading(time, sample(processor, time, time * 2 * PI * 74 / 60)))
        }
        assertAccurate(readings, 74.0, "15->60 FPS")
        assertFalse(readings.any { it.output.discontinuity })
    }
}
