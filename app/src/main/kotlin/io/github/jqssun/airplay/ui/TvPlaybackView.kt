package io.github.jqssun.airplay.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.PlayerView
import io.github.jqssun.airplay.R
import java.util.Locale

/** Compact TV transport overlay; horizontal D-pad keys always seek immediately. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class TvPlaybackView(context: Context, private val protocol: String, private val onExit: () -> Unit) : FrameLayout(context) {
    constructor(context: Context) : this(context, "DLNA", { (context as? android.app.Activity)?.finish() })

    private val main = Handler(Looper.getMainLooper())
    private var player: Player? = null
    val video = PlayerView(context).apply { useController = false; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS) }
    private val header = LinearLayout(context)
    private val footer = LinearLayout(context)
    private val actions = LinearLayout(context)
    private val status = text(16f, white)
    private val resolution = text(15f, muted)
    private val elapsed = text(15f, white)
    private val duration = text(15f, white)
    private val hint = text(14f, muted)
    private val feedback = text(20f, white)
    private val timeline = SeekBar(context)
    private val rewind = icon(android.R.drawable.ic_media_rew, R.string.tv_rewind) { seekBy(-SEEK_STEP_MS) }
    private val toggle = icon(android.R.drawable.ic_media_pause, R.string.tv_pause) { togglePlayback() }
    private val forward = icon(android.R.drawable.ic_media_ff, R.string.tv_forward) { seekBy(SEEK_STEP_MS) }
    private val end = icon(android.R.drawable.ic_menu_close_clear_cancel, R.string.tv_end_cast, onExit)
    private var controlsShown = true
    private var previewMs: Long? = null
    private var wakeKey: Int? = null
    private val seekAcceleration = SeekAcceleration()
    private var displayedPlayIcon: Int? = null
    private val hideFeedback = Runnable { feedback.visibility = View.GONE }
    private val hide = Runnable { if (player?.isPlaying == true && previewMs == null) hideControls() }
    private val tick = object : Runnable {
        override fun run() { update(); main.postDelayed(this, 250) }
    }
    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { cancelSeekHold() }
        override fun onPlaybackStateChanged(playbackState: Int) { update() }
        override fun onVideoSizeChanged(videoSize: VideoSize) { update() }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) scheduleHide() else main.removeCallbacks(hide)
            update()
        }
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) { showControls() }
    }

    init {
        setBackgroundColor(Color.BLACK)
        isFocusable = true
        isFocusableInTouchMode = true
        addView(video, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        timeline.id = View.generateViewId()

        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(dp(48), dp(22), dp(48), dp(14))
        header.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0xE60A101B.toInt(), Color.TRANSPARENT))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, dp(100), Gravity.TOP))
        header.addView(status, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(resolution, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)).apply { rightMargin = dp(20) })
        header.addView(end, LinearLayout.LayoutParams(dp(44), dp(44)))

        actions.gravity = Gravity.CENTER
        addView(actions, LayoutParams(LayoutParams.WRAP_CONTENT, dp(72), Gravity.CENTER))
        listOf(rewind, toggle, forward).forEachIndexed { index, b ->
            val size = if (index == 1) 64 else 48
            actions.addView(b, LinearLayout.LayoutParams(dp(size), dp(size)).apply {
                if (index > 0) leftMargin = dp(24)
            })
            b.nextFocusUpId = end.id
            b.nextFocusDownId = timeline.id
            b.nextFocusLeftId = b.id
            b.nextFocusRightId = b.id
        }

        footer.orientation = LinearLayout.VERTICAL
        footer.gravity = Gravity.BOTTOM
        footer.setPadding(dp(48), dp(22), dp(48), dp(27))
        footer.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.TRANSPARENT, 0xE60A101B.toInt()))
        addView(footer, LayoutParams(LayoutParams.MATCH_PARENT, dp(120), Gravity.BOTTOM))
        val progressRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        progressRow.addView(elapsed, LinearLayout.LayoutParams(dp(70), dp(40)))
        timeline.contentDescription = context.getString(R.string.tv_timeline)
        timeline.isFocusable = true
        timeline.progressTintList = ColorStateList.valueOf(accent)
        timeline.progressBackgroundTintList = ColorStateList.valueOf(0xFF66778F.toInt())
        timeline.thumbTintList = ColorStateList.valueOf(white)
        timeline.background = focusBackground(transparent = true)
        timeline.setPadding(dp(14), 0, dp(14), 0)
        timeline.nextFocusUpId = toggle.id
        timeline.nextFocusDownId = timeline.id
        progressRow.addView(timeline, LinearLayout.LayoutParams(0, dp(40), 1f))
        duration.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        progressRow.addView(duration, LinearLayout.LayoutParams(dp(70), dp(40)))
        footer.addView(progressRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
        hint.text = context.getString(R.string.tv_player_hint)
        footer.addView(hint, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(26)))
        timeline.setOnFocusChangeListener { _, focused ->
            hint.text = context.getString(if (focused) R.string.tv_timeline_hint else R.string.tv_player_hint)
            scheduleHide()
        }
        timeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) { previewMs = progress * 1000L; update() }
            }
            override fun onStartTrackingTouch(bar: SeekBar) { main.removeCallbacks(hide) }
            override fun onStopTrackingTouch(bar: SeekBar) {
                previewMs?.let { player?.seekTo(it) }; previewMs = null; scheduleHide()
            }
        })
        end.nextFocusDownId = toggle.id
        end.nextFocusUpId = end.id
        end.nextFocusLeftId = end.id
        end.nextFocusRightId = end.id

        feedback.gravity = Gravity.CENTER
        feedback.setPadding(dp(20), dp(12), dp(20), dp(12))
        feedback.background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xE60A101B.toInt()) }
        feedback.visibility = View.GONE
        feedback.translationY = -dp(72).toFloat()
        feedback.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(feedback, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        video.setOnClickListener { if (controlsShown) hideControls() else showControls() }
    }

    fun bind(p: Player) {
        player?.removeListener(listener)
        player = p
        video.player = p
        p.addListener(listener)
        showControls()
        update()
    }

    fun release() {
        cancelSeekHold()
        main.removeCallbacksAndMessages(null)
        player?.removeListener(listener)
        video.player = null
        player = null
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); main.post(tick) }
    override fun onDetachedFromWindow() { cancelSeekHold(); main.removeCallbacksAndMessages(null); super.onDetachedFromWindow() }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelSeekHold()
    }

    /** Intercept before focus dispatch so even hidden controls never swallow a seek. */
    fun handleKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
            code == KeyEvent.KEYCODE_MEDIA_REWIND || code == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (!isSeekable()) {
                    seekAcceleration.reset()
                } else {
                    seekAcceleration.press(code, event.downTime, event.eventTime)?.let { step ->
                        val direction = if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_MEDIA_REWIND) -1 else 1
                        seekBy(direction * SEEK_STEP_MS * step.multiplier, step.multiplier, step.repeated)
                    }
                }
            } else if (event.action == KeyEvent.ACTION_UP) {
                seekAcceleration.release(code, event.downTime)?.let { step ->
                    if (step.repeated) {
                        feedback.text = context.getString(R.string.tv_seek_position, time(player?.currentPosition ?: 0))
                        main.removeCallbacks(hideFeedback)
                        main.postDelayed(hideFeedback, 1000)
                    }
                }
            }
            return true
        }
        if (code == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (controlsShown) hideControls() else onExit()
            }
            return true
        }
        if (code in listOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (code) {
                    KeyEvent.KEYCODE_MEDIA_PLAY -> player?.play()
                    KeyEvent.KEYCODE_MEDIA_PAUSE -> player?.pause()
                    else -> togglePlayback()
                }
                showControls()
            }
            return true
        }
        if (wakeKey == code) {
            if (event.action == KeyEvent.ACTION_UP) wakeKey = null
            return true
        }
        if (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN)) {
            if (!controlsShown) {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    wakeKey = code
                    if (code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER) togglePlayback()
                    showControls()
                }
                return true
            }
            scheduleHide()
        }
        return false
    }

    private fun showControls() {
        val wasHidden = !controlsShown
        controlsShown = true
        header.visibility = View.VISIBLE
        actions.visibility = View.VISIBLE
        footer.visibility = View.VISIBLE
        if (wasHidden || !(header.hasFocus() || actions.hasFocus() || footer.hasFocus())) post { toggle.requestFocus() }
        scheduleHide()
    }

    private fun hideControls() {
        main.removeCallbacks(hide)
        previewMs = null
        controlsShown = false
        header.visibility = View.GONE
        actions.visibility = View.GONE
        footer.visibility = View.GONE
        feedback.visibility = View.GONE
        requestFocus()
    }

    private fun scheduleHide() {
        main.removeCallbacks(hide)
        if (player?.isPlaying == true) main.postDelayed(hide, 6000)
    }

    private fun togglePlayback() {
        player?.let {
            if (it.playbackState == Player.STATE_ENDED) { it.seekTo(0); it.play() }
            else if (it.playWhenReady) it.pause() else it.play()
        }
        update()
        scheduleHide()
    }

    private fun cancelSeekHold() {
        seekAcceleration.reset()
        main.removeCallbacks(hideFeedback)
        feedback.visibility = View.GONE
    }

    private fun seekBy(delta: Long, multiplier: Int = 1, repeated: Boolean = false) {
        val p = player ?: return
        if (isSeekable()) {
            previewMs = null
            val target = (p.currentPosition + delta).coerceIn(0, p.duration.coerceAtLeast(0))
            p.seekTo(target)
            feedback.text = if (repeated) context.getString(R.string.tv_seek_speed,
                context.getString(if (delta > 0) R.string.tv_seek_forward else R.string.tv_seek_backward), multiplier, time(target))
            else context.getString(R.string.tv_seek_feedback,
                (if (delta > 0) "+" else "−") + (kotlin.math.abs(delta) / 1000), time(target))
            feedback.visibility = View.VISIBLE
            main.removeCallbacks(hideFeedback)
            main.postDelayed(hideFeedback, 1500)
            update()
        }
        scheduleHide()
    }

    private fun isSeekable(): Boolean = player?.let { it.isCurrentMediaItemSeekable && it.duration != C.TIME_UNSET && it.duration > 0 } == true

    private fun update() {
        val p = player ?: return
        val position = previewMs ?: p.currentPosition.coerceAtLeast(0)
        elapsed.setIfChanged(time(position))
        duration.setIfChanged(if (p.duration > 0) time(p.duration)
            else if (p.isCurrentMediaItemLive) context.getString(R.string.tv_live) else "—:—")
        if (previewMs == null) timeline.max = (p.duration.coerceAtLeast(0) / 1000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        timeline.progress = (position / 1000).toInt()
        val seekable = isSeekable()
        timeline.isEnabled = seekable
        rewind.isEnabled = seekable
        forward.isEnabled = seekable
        rewind.alpha = if (seekable) 1f else 0.45f
        forward.alpha = if (seekable) 1f else 0.45f
        val playing = p.playWhenReady && p.playbackState != Player.STATE_ENDED
        val playIcon = if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        if (displayedPlayIcon != playIcon) {
            toggle.setImageResource(playIcon)
            displayedPlayIcon = playIcon
            toggle.contentDescription = context.getString(if (playing) R.string.tv_pause else R.string.tv_play)
        }
        val state = when {
            p.playerError != null -> R.string.tv_playback_error
            p.playbackState == Player.STATE_BUFFERING || p.playbackState == Player.STATE_IDLE -> R.string.tv_buffering
            p.playbackState == Player.STATE_ENDED -> R.string.tv_ended
            p.playWhenReady -> R.string.tv_playing
            else -> R.string.tv_paused
        }
        status.setIfChanged(context.getString(R.string.tv_protocol_status, protocol, context.getString(state)))
        val size = p.videoSize
        resolution.setIfChanged(if (size.width > 0 && size.height > 0) context.getString(R.string.tv_video_resolution, size.width, size.height)
            else context.getString(R.string.tv_video_resolution_pending))
    }

    private fun TextView.setIfChanged(value: String) { if (text.toString() != value) text = value }

    private fun text(size: Float, color: Int) = TextView(context).apply {
        textSize = size; setTextColor(color); gravity = Gravity.CENTER_VERTICAL
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
    }

    private fun icon(image: Int, label: Int, action: () -> Unit): ImageButton = ImageButton(context).apply {
        id = View.generateViewId()
        setImageResource(image)
        contentDescription = context.getString(label)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        isFocusable = true; isClickable = true
        background = focusBackground()
        imageTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf(android.R.attr.state_pressed), intArrayOf()),
            intArrayOf(ink, ink, white))
        setOnClickListener { action() }
        setOnFocusChangeListener { v, focused ->
            v.animate().scaleX(if (focused) 1.06f else 1f).scaleY(if (focused) 1.06f else 1f).setDuration(100).start()
            scheduleHide()
        }
    }

    private fun focusBackground(transparent: Boolean = false): StateListDrawable {
        fun shape(fill: Int, border: Int, width: Int) = GradientDrawable().apply {
            cornerRadius = dp(if (transparent) 12 else 40).toFloat(); setColor(fill); setStroke(dp(width), border)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(accent, white, 2))
            addState(intArrayOf(android.R.attr.state_focused), shape(if (transparent) 0xFF25453F.toInt() else white, if (transparent) accent else white, 2))
            addState(intArrayOf(), shape(if (transparent) Color.TRANSPARENT else 0xD926364A.toInt(), if (transparent) Color.TRANSPARENT else 0xFF71849D.toInt(), 1))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun time(ms: Long): String {
        val seconds = ms.coerceAtLeast(0) / 1000
        return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
    }

    companion object {
        private const val SEEK_STEP_MS = 10000L
        private const val white = 0xFFF4F7FC.toInt()
        private const val muted = 0xFFBAC8DC.toInt()
        private const val accent = 0xFF83F2D1.toInt()
        private const val ink = 0xFF101B2C.toInt()
    }
}
