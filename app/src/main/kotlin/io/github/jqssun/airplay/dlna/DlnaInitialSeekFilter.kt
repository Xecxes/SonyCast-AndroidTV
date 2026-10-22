package io.github.jqssun.airplay.dlna

/** A few controllers send their initial Seek(0) only after playback has started. */
internal class DlnaInitialSeekFilter {
    private var firstSeek = true
    private var playbackStartedAtMs: Long? = null

    fun onPlaybackStarted(nowMs: Long) {
        if (playbackStartedAtMs == null) playbackStartedAtMs = nowMs
    }

    fun shouldApply(targetMs: Long, positionMs: Long, nowMs: Long, isPlaying: Boolean): Boolean {
        val initial = firstSeek
        firstSeek = false
        val elapsed = playbackStartedAtMs?.let { nowMs - it }
        // Limit the exception to one startup command. A later rewind, a paused
        // seek, or a nonzero resume position must always reach the player.
        return !(initial && targetMs == 0L && isPlaying && positionMs in 0L..3000L &&
            elapsed != null && elapsed in 0L..5000L)
    }
}
