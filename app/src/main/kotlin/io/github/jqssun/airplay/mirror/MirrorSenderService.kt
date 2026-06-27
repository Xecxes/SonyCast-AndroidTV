package io.github.jqssun.airplay.mirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.github.jqssun.airplay.R
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Sender side of in-app mirroring: captures this device's screen via MediaProjection,
 * H.264-encodes it, and streams it to a MirrorReceiver over TCP. Runs as a
 * mediaProjection foreground service (required on Android 10+).
 */
class MirrorSenderService : Service() {

    private val tag = "MirrorSender"
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encoder: MediaCodec? = null
    private var socket: Socket? = null
    @Volatile private var running = false
    private var csd: ByteArray? = null // SPS+PPS

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (intent.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        val host = intent.getStringExtra(EXTRA_HOST)
        val port = intent.getIntExtra(EXTRA_PORT, 0)
        if (data == null || host == null || port == 0) { stopSelf(); return START_NOT_STICKY }

        startForegroundNotification(host)

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(resultCode, data).also {
            it.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, Handler(Looper.getMainLooper()))
        }
        running = true
        thread(name = "mirror-stream", isDaemon = true) { stream(host, port) }
        MirrorSenderState.active = true
        MirrorSenderState.target = host
        return START_NOT_STICKY
    }

    private fun stream(host: String, port: Int) {
        try {
            // capture size: real display, capped to 1080p, aligned to 16
            val dm = resources.displayMetrics
            val maxDim = 1920
            val scale = minOf(1f, maxDim.toFloat() / maxOf(dm.widthPixels, dm.heightPixels))
            val w = ((dm.widthPixels * scale).toInt() / 16) * 16
            val h = ((dm.heightPixels * scale).toInt() / 16) * 16
            val dpi = dm.densityDpi

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, MirrorProtocol.DEFAULT_BITRATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, MirrorProtocol.DEFAULT_FPS)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, MirrorProtocol.KEYFRAME_INTERVAL_S)
                if (Build.VERSION.SDK_INT >= 21) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                }
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            encoder = enc
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = enc.createInputSurface()
            enc.start()

            virtualDisplay = projection!!.createVirtualDisplay(
                "airplay-mirror", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            )

            val sock = Socket()
            sock.tcpNoDelay = true
            sock.connect(InetSocketAddress(host, port), 8000)
            socket = sock
            val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream(), 1 shl 16))
            out.writeInt(MirrorProtocol.MAGIC)
            out.writeInt(w)
            out.writeInt(h)
            out.flush()
            Log.i(tag, "Mirroring ${w}x$h -> $host:$port")

            val info = MediaCodec.BufferInfo()
            while (running) {
                val idx = enc.dequeueOutputBuffer(info, 100_000)
                if (idx < 0) continue
                val buf = enc.getOutputBuffer(idx)
                if (buf != null && info.size > 0) {
                    buf.position(info.offset)
                    buf.limit(info.offset + info.size)
                    val bytes = ByteArray(info.size)
                    buf.get(bytes)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        csd = bytes // SPS+PPS; prepended to keyframes
                    } else {
                        val keyframe = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val payload = if (keyframe && csd != null) csd!! + bytes else bytes
                        out.writeInt(payload.size)
                        out.writeByte(if (keyframe) MirrorProtocol.FLAG_KEYFRAME else 0)
                        out.write(payload)
                        out.flush()
                    }
                }
                enc.releaseOutputBuffer(idx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        } catch (e: Exception) {
            Log.w(tag, "stream ended: ${e.message}")
        } finally {
            stopSelf()
        }
    }

    private fun startForegroundNotification(host: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, getString(R.string.mirror_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentTitle(getString(R.string.mirror_notif_title))
            .setContentText(getString(R.string.mirror_notif_text, host))
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
        ServiceCompat.startForeground(this, NOTIF_ID, n, type)
    }

    override fun onDestroy() {
        running = false
        MirrorSenderState.active = false
        MirrorSenderState.target = null
        try { encoder?.signalEndOfInputStream() } catch (_: Exception) {}
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { encoder?.stop(); encoder?.release() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        encoder = null; virtualDisplay = null; socket = null; projection = null
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "mirror_sender"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "io.github.jqssun.airplay.MIRROR_STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val EXTRA_HOST = "host"
        const val EXTRA_PORT = "port"
    }
}

/** Lightweight observable state for the sender UI. */
object MirrorSenderState {
    @Volatile var active: Boolean = false
    @Volatile var target: String? = null
}
