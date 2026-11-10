package de.dvld.heartthrum

import kotlin.math.roundToInt

internal class LiveBpmDisplay {
    private var lastUpdateAt: Long? = null
    private var displayedBpm: Int? = null

    fun update(elapsedRealtimeMillis: Long, bpm: Double?): Int? {
        if (bpm == null) {
            reset()
            return null
        }
        val last = lastUpdateAt
        if (last == null || elapsedRealtimeMillis - last >= 1_000) {
            displayedBpm = bpm.roundToInt()
            lastUpdateAt = elapsedRealtimeMillis
        }
        return displayedBpm
    }

    fun reset() {
        lastUpdateAt = null
        displayedBpm = null
    }
}
