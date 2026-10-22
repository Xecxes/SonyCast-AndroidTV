package io.github.jqssun.airplay.ui

/** Monotonic key-event timing, independent of the remote's repeat frequency. */
internal class SeekAcceleration {
    data class Step(val multiplier: Int, val repeated: Boolean)

    private var key: Int? = null
    private var downTime = 0L
    private var startedAt = 0L
    private var lastStepAt = 0L
    private var lastStep: Step? = null

    fun press(keyCode: Int, downTimeMs: Long, eventTimeMs: Long): Step? {
        if (eventTimeMs < downTimeMs) return null
        val first = key != keyCode || downTime != downTimeMs
        if (first) {
            key = keyCode
            downTime = downTimeMs
            startedAt = eventTimeMs
        } else if (eventTimeMs - lastStepAt < 250L) {
            return null
        }
        lastStepAt = eventTimeMs
        val elapsed = (eventTimeMs - startedAt).coerceAtLeast(0)
        val multiplier = if (elapsed < 2000) 1 else (elapsed / 1000).coerceAtMost(5).toInt()
        return Step(multiplier, repeated = !first).also { lastStep = it }
    }

    fun release(keyCode: Int, downTimeMs: Long): Step? {
        if (key != keyCode || downTime != downTimeMs) return null
        return lastStep.also { reset() }
    }

    fun reset() {
        key = null
        lastStep = null
    }
}
