package io.github.jqssun.airplay.dlna

import java.io.ByteArrayInputStream
import java.io.EOFException
import org.junit.Assert.assertEquals
import org.junit.Test

class DlnaHttpRequestReaderTest {
    private fun packet(body: String): ByteArray {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return ("POST /ctrl/AVTransport HTTP/1.1\r\nContent-Length: ${bytes.size}\r\n" +
            "Content-Type: text/xml; charset=utf-8\r\nSOAPAction: play\r\n\r\n").toByteArray() + bytes
    }

    @Test fun chineseMetadataConsumesExactlyItsUtf8Bytes() {
        val body = "<CurrentURIMetaData>中文标题与表情 🎬</CurrentURIMetaData>"
        val input = ByteArrayInputStream(packet(body) + "NEXT".toByteArray())
        val request = DlnaHttpRequestReader.read(input)!!
        assertEquals(body, request.body)
        assertEquals("play", request.headers["soapaction"])
        assertEquals("NEXT", input.readBytes().toString(Charsets.UTF_8))
    }

    @Test fun fragmentedNetworkReadsPreserveMultibyteCharacters() {
        val body = "<title>哔哩哔哩投屏</title>"
        val input = object : ByteArrayInputStream(packet(body)) {
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                super.read(buffer, offset, minOf(length, 2))
        }
        assertEquals(body, DlnaHttpRequestReader.read(input)!!.body)
    }

    @Test(expected = EOFException::class) fun truncatedBodyIsRejected() {
        DlnaHttpRequestReader.read(ByteArrayInputStream(packet("中文").dropLast(1).toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class) fun oversizedBodyIsRejectedBeforeAllocation() {
        DlnaHttpRequestReader.read(ByteArrayInputStream(
            "POST /ctrl/AVTransport HTTP/1.1\r\nContent-Length: 2147483647\r\n\r\n".toByteArray()))
    }
}
