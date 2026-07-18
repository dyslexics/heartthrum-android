package de.dvld.heartthrum

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
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

    private var measureStart: Double? = null
    private var lastFingerSeen: Double? = null
    private var exposureLocked = false
    private val readings = ArrayList<Pair<Double, Double>>()

    init {
        camera.onSample = { t, r, g, b -> handle(t, r, g, b) }
    }

    fun start(owner: LifecycleOwner) {
        processor.reset()
        readings.clear()
        measureStart = null
        exposureLocked = false
        progress = 0.0
        resultBPM = null
        phase = Phase.NO_FINGER
        camera.start(owner)
    }

    fun cancel() {
        camera.stop()
        phase = Phase.IDLE
        progress = 0.0
        liveBPM = null
        waveform = emptyList()
    }

    private fun handle(now: Double, r: Double, g: Double, b: Double) {
        val out = processor.process(now, r, g, b)
        viewModelScope.launch(Dispatchers.Main) {
            if (phase != Phase.NO_FINGER && phase != Phase.MEASURING) return@launch
            waveform = out.waveform
            quality = out.quality
            liveBPM = out.bpm?.roundToInt()

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
                if (now - start >= MEASURE_DURATION) finish()
            } else if (phase == Phase.MEASURING) {
                val seen = lastFingerSeen
                if (seen != null && now - seen > 1.5) {
                    phase = Phase.NO_FINGER
                    measureStart = null
                    progress = 0.0
                    readings.clear()
                    processor.reset()
                    exposureLocked = false
                }
            }
        }
    }

    private fun finish() {
        camera.stop()
        val start = measureStart ?: return
        val stable = readings.filter { it.first - start > MEASURE_DURATION * 0.4 }.map { it.second }
        val source = if (stable.size >= 5) stable else readings.map { it.second }
        if (source.isEmpty()) {
            phase = Phase.IDLE
            return
        }
        val sorted = source.sorted()
        resultBPM = sorted[sorted.size / 2].roundToInt()
        phase = Phase.DONE
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
