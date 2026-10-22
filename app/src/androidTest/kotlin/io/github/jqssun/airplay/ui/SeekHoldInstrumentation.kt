package io.github.jqssun.airplay.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.jqssun.airplay.dlna.DlnaController
import io.github.jqssun.airplay.dlna.DlnaPlayerActivity
import java.io.File

/** Separate development test APK: injects genuine held-key events into the TV player. */
class SeekHoldInstrumentation : Instrumentation() {
    private var mediaUrl = ""
    private lateinit var activity: Activity
    private lateinit var frame: TvPlaybackView
    private val observations = mutableListOf<String>()

    override fun onCreate(arguments: Bundle?) {
        mediaUrl = arguments?.getString("mediaUrl").orEmpty()
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        try {
            check(java.net.URI(mediaUrl).let { it.scheme == "http" && !it.host.isNullOrBlank() }) { "Explicit HTTP test fixture URL required" }
            val automation = uiAutomation
            runOnMainSync { DlnaController.uri = mediaUrl }
            activity = startActivitySync(Intent(targetContext, DlnaPlayerActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("focused player") { read { activity.hasWindowFocus() && findPlayer(activity.window.decorView) != null } }
            runOnMainSync { frame = findPlayer(activity.window.decorView)!! }
            await("seekable fixture") { read { frame.video.player?.let { it.isCurrentMediaItemSeekable && it.duration > 1700000 } == true } }
            runOnMainSync { frame.video.player!!.pause() }
            await("paused fixture") { read { frame.video.player!!.playbackState == androidx.media3.common.Player.STATE_READY && !frame.video.player!!.playWhenReady } }
            val buttonsShown = read { texts(frame).any { it == "720 × 480" } || frame.childCount > 0 && visibleButtons(frame) > 0 }
            if (buttonsShown) tap(KeyEvent.KEYCODE_BACK)

            for ((name, key) in listOf("forward" to KeyEvent.KEYCODE_DPAD_RIGHT, "backward" to KeyEvent.KEYCODE_DPAD_LEFT)) {
                baseline()
                val downTime = SystemClock.uptimeMillis()
                var previous = position()
                val observed = mutableSetOf<Int>()
                for (repeat in 0..18) {
                    val due = downTime + repeat * 300L
                    SystemClock.sleep((due - SystemClock.uptimeMillis()).coerceAtLeast(0))
                    check(read { activity.hasWindowFocus() }) { "Player lost focus; refusing to inject elsewhere" }
                    val at = SystemClock.uptimeMillis()
                    val multiplier = if (at - downTime < 2000) 1 else ((at - downTime) / 1000).coerceAtMost(5).toInt()
                    val event = KeyEvent(downTime, at, KeyEvent.ACTION_DOWN, key, repeat, 0,
                        KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_DPAD)
                    check(automation.injectInputEvent(event, true)) { "Input injection failed" }
                    val after = position()
                    val expectedDelta = (if (name == "forward") 1 else -1) * 10000L * multiplier
                    check(kotlin.math.abs(after - previous - expectedDelta) < 100) {
                        "$name held=${at - downTime}ms multiplier=$multiplier delta=${after - previous} expected=$expectedDelta"
                    }
                    if (repeat > 0) check(read { texts(frame).any { it.contains(" $multiplier× · ") } }) { "Missing $multiplier× indicator" }
                    if (observed.add(multiplier)) {
                        record("$name ${multiplier}x held=${at - downTime}ms delta=${after - previous} position=$after")
                        if (multiplier == 5) {
                            val file = File(targetContext.getExternalFilesDir(null), "seek-$name-5x.png")
                            val screenshot = automation.takeScreenshot() ?: error("Screenshot failed")
                            file.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            screenshot.recycle()
                        }
                    }
                    previous = after
                }
                check(observed == setOf(1, 2, 3, 4, 5)) { "$name missed a speed tier: $observed" }
                automation.injectInputEvent(KeyEvent(downTime, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0), true)
                check(read { texts(frame).none { Regex(" [1-5]× · ").containsMatchIn(it) } }) { "Speed indicator was not cleared on release" }
                val beforeTap = position()
                tap(key)
                check(kotlin.math.abs(kotlin.math.abs(position() - beforeTap) - 10000L) < 100) { "Release did not reset to 10-second tap" }
                record("$name release resets to 1x; playback remains paused")
                check(read { !frame.video.player!!.playWhenReady }) { "Seeking resumed paused playback" }
            }
            finish(Activity.RESULT_OK, Bundle().apply {
                putString("hold_acceleration", "PASS")
                putString("stream", "\nPASS: both directions 1x→2x→3x→4x→5x, actual seek deltas, release reset and paused state\n")
            })
        } catch (error: Throwable) {
            finish(Activity.RESULT_CANCELED, Bundle().apply {
                putString("hold_acceleration", "FAIL")
                putString("stream", "\nFAIL: ${error.stackTraceToString()}\n")
            })
        } finally {
            if (::activity.isInitialized) runOnMainSync { activity.finish() }
        }
    }

    private fun baseline() {
        runOnMainSync { frame.video.player!!.seekTo(900000) }
        await("baseline") { kotlin.math.abs(position() - 900000) < 100 && read { frame.video.player!!.playbackState == androidx.media3.common.Player.STATE_READY } }
    }

    private fun tap(code: Int) {
        check(read { activity.hasWindowFocus() }) { "Player lost focus" }
        val now = SystemClock.uptimeMillis()
        sendKeySync(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        sendKeySync(KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, code, 0))
    }

    private fun position() = read { frame.video.player!!.currentPosition }
    private fun <T> read(block: () -> T): T { var result: T? = null; runOnMainSync { result = block() }; return result!! }
    private fun await(label: String, predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (!predicate()) { check(SystemClock.uptimeMillis() < deadline) { "Timeout: $label" }; SystemClock.sleep(100) }
    }
    private fun findPlayer(view: View): TvPlaybackView? {
        if (view is TvPlaybackView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun texts(view: View): List<String> {
        if (view.visibility != View.VISIBLE) return emptyList()
        val result = mutableListOf<String>()
        if (view is TextView) result += view.text.toString()
        if (view is ViewGroup) for (i in 0 until view.childCount) result += texts(view.getChildAt(i))
        return result
    }
    private fun visibleButtons(view: View): Int {
        if (view.visibility != View.VISIBLE) return 0
        if (view is android.widget.ImageButton) return 1
        if (view is ViewGroup) return (0 until view.childCount).sumOf { visibleButtons(view.getChildAt(it)) }
        return 0
    }
    private fun record(value: String) {
        observations += value
        sendStatus(0, Bundle().apply { putString("stream", "$value\n") })
    }
}
