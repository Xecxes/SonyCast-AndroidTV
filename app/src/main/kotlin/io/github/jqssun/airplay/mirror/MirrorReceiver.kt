package io.github.jqssun.airplay.mirror

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import android.view.Surface
import io.github.jqssun.airplay.renderer.VideoRenderer
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Receives an in-app screen-mirror stream from another device running this app and decodes
 * it through the same proven H.264 pipeline used for AirPlay. Advertised over mDNS so the
 * sender can discover it. One active mirror at a time.
 */
class MirrorReceiver(private val context: Context, private val deviceName: String) {

    private val tag = "MirrorReceiver"
    private var server: ServerSocket? = null
    private var nsd: NsdManager? = null
    private var regListener: NsdManager.RegistrationListener? = null
    @Volatile private var running = false
    @Volatile private var client: Socket? = null

    val videoRenderer = VideoRenderer()

    fun setSurface(s: Surface) = videoRenderer.setSurface(s)
    fun clearSurface(s: Surface) = videoRenderer.clearSurface(s)

    fun start() {
        if (running) return
        running = true
        MirrorBridge.receiver = this
        val srv = ServerSocket(0)
        server = srv
        registerNsd(srv.localPort)
        thread(name = "mirror-accept", isDaemon = true) {
            while (running) {
                val sock = try {
                    srv.accept()
                } catch (e: Exception) {
                    if (running) Log.w(tag, "accept: ${e.message}"); break
                }
                client?.let { try { it.close() } catch (_: Exception) {} } // one mirror at a time
                client = sock
                thread(isDaemon = true) { handleClient(sock) }
            }
        }
        Log.i(tag, "Mirror receiver listening on ${srv.localPort}")
    }

    private fun handleClient(sock: Socket) {
        try {
            sock.tcpNoDelay = true
            val din = DataInputStream(BufferedInputStream(sock.getInputStream(), 1 shl 16))
            if (din.readInt() != MirrorProtocol.MAGIC) { Log.w(tag, "bad magic"); return }
            val w = din.readInt()
            val h = din.readInt()
            if (w <= 0 || h <= 0 || w > 7680 || h > 4320) { Log.w(tag, "bad dims ${w}x$h"); return }
            Log.i(tag, "Mirror stream ${w}x$h")
            videoRenderer.setResolution(w, h)
            MirrorBridge.launchDisplay?.invoke()
            while (running && !sock.isClosed) {
                val len = din.readInt()
                /* flags */ din.readByte()
                if (len <= 0 || len > (8 shl 20)) { Log.w(tag, "bad frame len $len"); break }
                val buf = ByteArray(len)
                din.readFully(buf)
                videoRenderer.feedFrame(buf, System.nanoTime(), false)
            }
        } catch (e: Exception) {
            Log.i(tag, "client ended: ${e.message}")
        } finally {
            try { sock.close() } catch (_: Exception) {}
            if (client === sock) {
                client = null
                videoRenderer.release()
                MirrorBridge.finishDisplay?.invoke()
            }
        }
    }

    private fun registerNsd(port: Int) {
        val mgr = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = mgr
        val info = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = MirrorProtocol.SERVICE_TYPE
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(s: NsdServiceInfo) { Log.i(tag, "NSD registered: ${s.serviceName}") }
            override fun onRegistrationFailed(s: NsdServiceInfo, code: Int) { Log.w(tag, "NSD reg failed: $code") }
            override fun onServiceUnregistered(s: NsdServiceInfo) {}
            override fun onUnregistrationFailed(s: NsdServiceInfo, code: Int) {}
        }
        regListener = listener
        try { mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) } catch (e: Exception) {
            Log.w(tag, "NSD register failed: ${e.message}")
        }
    }

    fun stop() {
        if (!running) return
        running = false
        regListener?.let { try { nsd?.unregisterService(it) } catch (_: Exception) {} }
        regListener = null
        try { client?.close() } catch (_: Exception) {}
        client = null
        try { server?.close() } catch (_: Exception) {}
        server = null
        videoRenderer.release()
        if (MirrorBridge.receiver === this) MirrorBridge.receiver = null
        MirrorBridge.finishDisplay?.invoke()
        Log.i(tag, "Mirror receiver stopped")
    }
}
