package io.github.jqssun.airplay.dlna

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.KeyEvent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.jqssun.airplay.ui.TvPlaybackView
import io.github.jqssun.airplay.ui.isTvDevice

/** Full-screen ExoPlayer that renders media URLs pushed to the DLNA renderer. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DlnaPlayerActivity : Activity(), DlnaController.PlayerControl {

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private var tvControls: TvPlaybackView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var initialSeekFilter = DlnaInitialSeekFilter()

    private val progressTick = object : Runnable {
        override fun run() {
            player?.let {
                DlnaController.positionMs = it.currentPosition.coerceAtLeast(0)
                DlnaController.durationMs = if (it.duration > 0) it.duration else 0
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tvControls = if (isTvDevice()) TvPlaybackView(this, "DLNA") { finish() } else null
        val view = tvControls?.video ?: PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
        }
        playerView = view
        setContentView(tvControls ?: view)
        // Sony Android 12 has no DecorView before setContentView; querying its
        // InsetsController earlier crashes while launching DLNA playback.
        goFullscreen()

        val exo = ExoPlayer.Builder(this).build()
        player = exo
        if (tvControls != null) tvControls?.bind(exo) else view.player = exo
        exo.setVolume(DlnaController.volume / 100f)
        exo.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) = syncState()
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = syncState()
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                DlnaController.transportState = DlnaController.STATE_STOPPED
            }
        })

        DlnaController.player = this
        playUri(DlnaController.uri ?: intent?.getStringExtra(EXTRA_URI))
        handler.post(progressTick)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i("DlnaPlayer", "onNewIntent positionMs=${player?.currentPosition}")
        playUri(DlnaController.uri ?: intent?.getStringExtra(EXTRA_URI))
    }

    private fun playUri(uri: String?) {
        val p = player ?: return
        if (uri.isNullOrBlank()) return
        Log.i("DlnaPlayer", "load media sameUri=${uri == p.currentMediaItem?.localConfiguration?.uri?.toString()} positionMs=${p.currentPosition}")
        if (uri == p.currentMediaItem?.localConfiguration?.uri?.toString()) {
            play()
            return
        }
        initialSeekFilter = DlnaInitialSeekFilter()
        DlnaController.positionMs = 0
        DlnaController.durationMs = 0
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
        p.playWhenReady = true
    }

    private fun syncState() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_READY && p.playWhenReady) {
            initialSeekFilter.onPlaybackStarted(SystemClock.elapsedRealtime())
        }
        DlnaController.transportState = when (p.playbackState) {
            Player.STATE_BUFFERING -> DlnaController.STATE_TRANSITIONING
            Player.STATE_READY -> if (p.playWhenReady) DlnaController.STATE_PLAYING else DlnaController.STATE_PAUSED
            Player.STATE_ENDED -> DlnaController.STATE_STOPPED
            else -> DlnaController.STATE_STOPPED
        }
    }

    // ---- DlnaController.PlayerControl (called from renderer threads) ----
    override fun play() = handler.post {
        val requestedUri = DlnaController.uri
        val loadedUri = player?.currentMediaItem?.localConfiguration?.uri?.toString()
        if (!requestedUri.isNullOrBlank() && requestedUri != loadedUri) {
            // A new SetAVTransportURI must replace the old item, not resume it.
            playUri(requestedUri)
        } else {
            player?.play()
            // Repeated Play may produce no player event; still acknowledge the
            // actual state after the renderer has reported TRANSITIONING.
            syncState()
        }
    }.let {}
    override fun pause() = handler.post { player?.pause() }.let {}
    override fun stop() = handler.post { finish() }.let {}
    override fun seekTo(ms: Long) = handler.post {
        val p = player ?: return@post
        if (!initialSeekFilter.shouldApply(ms, p.currentPosition, SystemClock.elapsedRealtime(), p.isPlaying)) {
            Log.i("DlnaPlayer", "ignored initial Seek(0) positionMs=${p.currentPosition}")
            return@post
        }
        Log.i("DlnaPlayer", "apply seek targetMs=$ms positionMs=${p.currentPosition}")
        p.seekTo(ms)
        DlnaController.positionMs = ms
    }.let {}
    override fun setVolume(percent: Int) = handler.post { player?.setVolume(percent / 100f) }.let {}

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (tvControls?.handleKeyEvent(event) == true) true else super.dispatchKeyEvent(event)

    private fun goFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (DlnaController.player === this) DlnaController.player = null
        DlnaController.transportState = DlnaController.STATE_STOPPED
        tvControls?.release()
        player?.release()
        player = null
    }

    companion object {
        const val EXTRA_URI = "dlna_uri"
    }
}
