package io.github.jqssun.airplay.dlna

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.util.Locale

internal data class DlnaHttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
)

/** Content-Length counts bytes on the wire, even when SOAP metadata contains Chinese. */
internal object DlnaHttpRequestReader {
    private const val MAX_LINE_BYTES = 8192
    private const val MAX_HEADER_COUNT = 64
    private const val MAX_BODY_BYTES = 1024 * 1024

    fun read(input: InputStream): DlnaHttpRequest? {
        val parts = (line(input) ?: return null).split(' ', limit = 3)
        require(parts.size == 3) { "Invalid HTTP request line" }
        val headers = linkedMapOf<String, String>()
        var count = 0
        while (true) {
            val header = line(input) ?: throw EOFException("Incomplete HTTP headers")
            if (header.isEmpty()) break
            require(++count <= MAX_HEADER_COUNT) { "Too many HTTP headers" }
            val separator = header.indexOf(':')
            require(separator > 0) { "Invalid HTTP header" }
            headers[header.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                header.substring(separator + 1).trim()
        }
        val length = headers["content-length"]?.let {
            it.toIntOrNull() ?: throw IllegalArgumentException("Invalid Content-Length")
        } ?: 0
        require(length in 0..MAX_BODY_BYTES) { "HTTP body exceeds permitted size" }
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val received = input.read(bytes, offset, length - offset)
            if (received < 0) throw EOFException("Incomplete HTTP body")
            if (received > 0) offset += received
        }
        return DlnaHttpRequest(parts[0].uppercase(Locale.ROOT), parts[1], headers, bytes.toString(Charsets.UTF_8))
    }

    private fun line(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) {
                if (bytes.size() == 0) return null
                throw EOFException("Incomplete HTTP line")
            }
            if (next == 10) return bytes.toString("ISO-8859-1").removeSuffix("\r")
            require(bytes.size() < MAX_LINE_BYTES) { "HTTP line too long" }
            bytes.write(next)
        }
    }
}
