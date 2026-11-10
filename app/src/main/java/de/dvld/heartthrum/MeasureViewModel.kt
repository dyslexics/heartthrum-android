package de.dvld.heartthrum

import android.app.Application
import android.os.SystemClock
import androidx.annotation.MainThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

class MeasureViewModel(app: Application) : AndroidViewModel(app) {

    enum class Phase { IDLE, NO_FINGER, MEASURING, DONE }

    companion object { const val MEASURE_DURATION = 40.0 }

    var phase by mutableStateOf(Phase.IDLE); private set
    var liveBPM by mutableStateOf<Int?>(null); private set
    var quality by mutableStateOf(0.0); private set
    var progress by mutableStateOf(0.0); private set
    var waveform by mutableStateOf<List<Double>>(emptyList()); private set
    var resultBPM by mutableStateOf<Int?>(null); private set

    private val camera = CameraController(app)
    private val processor = PulseProcessor()
    private val liveBpmDisplay = LiveBpmDisplay()

    private var measureStart: Double? = null
    private var lastFingerSeen: Double? = null
    private var exposureLocked = false
    private val readings = ArrayList<Pair<Double, Double>>()
    @Volatile private var generation = 0L
    private var frameWatchdog: Job? = null
    private var lastFrameAt = 0L

    init {
        camera.onSample = { t, r, g, b -> handle(t, r, g, b) }
    }

    @MainThread
    fun start(owner: LifecycleOwner) {
        generation++
        frameWatchdog?.cancel()
        processor.reset()
        liveBpmDisplay.reset()
        readings.clear()
        measureStart = null
        lastFingerSeen = null
        exposureLocked = false
        progress = 0.0
        liveBPM = null
        quality = 0.0
        waveform = emptyList()
        resultBPM = null
        phase = Phase.NO_FINGER
        lastFrameAt = SystemClock.elapsedRealtime()
        frameWatchdog = viewModelScope.launch(Dispatchers.Main) {
            while (true) {
                delay(250)
                if (SystemClock.elapsedRealtime() - lastFrameAt > PulseProcessor.ESTIMATE_EXPIRY * 1000) {
                    clearSignal()
                }
            }
        }
        camera.start(owner)
    }

    @MainThread
    fun cancel() {
        generation++
        frameWatchdog?.cancel()
        camera.stop()
        processor.reset()
        liveBpmDisplay.reset()
        phase = Phase.IDLE
        progress = 0.0
        liveBPM = null
        quality = 0.0
        waveform = emptyList()
    }

    private fun handle(now: Double, r: Double, g: Double, b: Double) {
        val session = generation
        viewModelScope.launch(Dispatchers.Main) {
            if (session != generation) return@launch
            if (phase != Phase.NO_FINGER && phase != Phase.MEASURING) return@launch
            // Processing, resets, and measurement state are all main-thread confined.
            lastFrameAt = SystemClock.elapsedRealtime()
            val out = processor.process(now, r, g, b)
            if (out.discontinuity) clearSignal()
            waveform = out.waveform
            quality = out.quality
            liveBPM = liveBpmDisplay.update(SystemClock.elapsedRealtime(), out.bpm)

            if (out.fingerDetected) {
                lastFingerSeen = now
                if (phase == Phase.NO_FINGER) {
                    phase = Phase.MEASURING
                    measureStart = now
                    readings.clear()
                }
                val start = measureStart ?: return@launch
                if (!exposureLocked && now - start > 1.5) {
                    exposureLocked = true
                    camera.lockExposure()
                }
                val bpm = out.bpm
                if (bpm != null && out.quality > 0.3) readings.add(now to bpm)
                progress = min(1.0, (now - start) / MEASURE_DURATION)
                if (now - start >= MEASURE_DURATION) finish(now)
            } else if (phase == Phase.MEASURING) {
                val seen = lastFingerSeen
                if (seen != null && now - seen > 1.5) {
                    clearSignal()
                }
            }
        }
    }

    private fun clearSignal() {
        processor.reset()
        liveBpmDisplay.reset()
        phase = Phase.NO_FINGER
        measureStart = null
        lastFingerSeen = null
        progress = 0.0
        readings.clear()
        liveBPM = null
        quality = 0.0
        waveform = emptyList()
        exposureLocked = false
    }

    private fun finish(now: Double) {
        generation++
        frameWatchdog?.cancel()
        camera.stop()
        liveBpmDisplay.reset()
        val start = measureStart ?: return
        val estimate = aggregateMeasurementBpm(readings, start, now, MEASURE_DURATION)
        if (estimate == null) {
            phase = Phase.IDLE
            liveBPM = null
            quality = 0.0
            return
        }
        resultBPM = estimate.roundToInt()
        phase = Phase.DONE
    }

    override fun onCleared() {
        camera.stop()
        super.onCleared()
    }

    fun save(tag: MeasureTag?, mood: Int?, onDone: () -> Unit) {
        val bpm = resultBPM ?: return
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.get(getApplication()).pulseDao()
                .insert(PulseRecord(date = System.currentTimeMillis(), bpm = bpm, tag = tag?.key, mood = mood))
            launch(Dispatchers.Main) { onDone() }
        }
    }
}

internal fun aggregateMeasurementBpm(
    readings: List<Pair<Double, Double>>,
    start: Double,
    now: Double,
    duration: Double
): Double? {
    val last = readings.lastOrNull() ?: return null
    if (now - last.first !in 0.0..PulseProcessor.ESTIMATE_EXPIRY) return null
    val stable = readings.filter { it.first - start > duration * 0.4 }.map { it.second }
    val source = if (stable.size >= 5) stable else readings.map { it.second }
    // Preserve the measurement's robust summary, rounding only once for display/storage.
    val sorted = source.sorted()
    return sorted[sorted.size / 2]
}
