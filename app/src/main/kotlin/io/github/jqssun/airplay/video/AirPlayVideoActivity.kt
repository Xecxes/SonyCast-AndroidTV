package io.github.jqssun.airplay.video

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.KeyEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.jqssun.airplay.ui.TvPlaybackView
import io.github.jqssun.airplay.ui.isTvDevice

/** Native protocol threads only read an immutable snapshot; ExoPlayer stays on Main. */
object AirPlayVideoSession {
    @Volatile var uri: String? = null
    @Volatile var startSeconds: Float = 0f
    @Volatile var snapshot = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 1.0)
    @Volatile var control: Control? = null
    interface Control {
        fun seek(seconds: Float)
        fun rate(speed: Float)
        fun stop()
    }
}

/** Full-screen receiver for AirPlay app-level URL video, separate from DLNA playback. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class AirPlayVideoActivity : Activity(), AirPlayVideoSession.Control {
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var tvControls: TvPlaybackView? = null
    private val tick = object : Runnable {
        override fun run() { report(); main.postDelayed(this, 250) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        tvControls = if (isTvDevice()) TvPlaybackView(this, "AirPlay") { finish() } else null
        val view = tvControls?.video ?: PlayerView(this)
        setContentView(tvControls ?: view)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, view).hide(WindowInsetsCompat.Type.systemBars())
        player = ExoPlayer.Builder(this).build().also { p ->
            if (tvControls != null) tvControls?.bind(p) else view.player = p
            p.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { report() }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Log.e("AirPlayVideo", "Media playback failed: ${error.errorCodeName}", error)
                    AirPlayVideoSession.snapshot = doubleArrayOf(0.0, -1.0, 0.0, 0.0, 0.0)
                }
            })
        }
        AirPlayVideoSession.control = this
        load()
        main.post(tick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        load()
    }

    private fun load() {
        val uri = AirPlayVideoSession.uri ?: return
        val p = player ?: return
        p.setMediaItem(MediaItem.fromUri(uri), (AirPlayVideoSession.startSeconds * 1000).toLong().coerceAtLeast(0))
        p.playWhenReady = true
        p.prepare()
    }

    private fun report() {
        val p = player ?: return
        val ended = p.playbackState == Player.STATE_ENDED
        val known = p.duration != C.TIME_UNSET
        AirPlayVideoSession.snapshot = doubleArrayOf(
            p.currentPosition.coerceAtLeast(0) / 1000.0,
            if (ended) -1.0 else if (known) p.duration / 1000.0 else 0.0,
            if (p.isPlaying) p.playbackParameters.speed.toDouble() else 0.0,
            if (known || p.playbackState == Player.STATE_READY) 1.0 else 0.0,
            if (p.playbackState == Player.STATE_BUFFERING) 1.0 else 0.0,
        )
    }

    override fun seek(seconds: Float) { main.post { player?.seekTo((seconds * 1000).toLong().coerceAtLeast(0)) } }
    override fun rate(speed: Float) { main.post {
        player?.let { p ->
            p.playWhenReady = speed > 0
            if (speed > 0) p.playbackParameters = PlaybackParameters(speed.coerceIn(0.1f, 4f))
        }
    } }
    override fun stop() { main.post { finish() } }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (tvControls?.handleKeyEvent(event) == true) true else super.dispatchKeyEvent(event)

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        if (AirPlayVideoSession.control === this) AirPlayVideoSession.control = null
        tvControls?.release()
        player?.release()
        player = null
        AirPlayVideoSession.snapshot = doubleArrayOf(0.0, -1.0, 0.0, 0.0, 0.0)
        super.onDestroy()
    }
}
