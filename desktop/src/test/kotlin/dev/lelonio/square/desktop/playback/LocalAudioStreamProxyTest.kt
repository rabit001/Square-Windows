package dev.lelonio.square.desktop.playback

import com.sun.net.httpserver.HttpServer
import dev.lelonio.square.desktop.backend.youtube.ResolvedYouTubeAudio
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalAudioStreamProxyTest {

    @Test
    fun forwardsByteRangeAndReturnsJavaFxAudioMimeType() {
        val sourceBytes = ByteArray(4_096) { (it % 251).toByte() }
        val upstream = rangeServer(sourceBytes)
        try {
            LocalAudioStreamProxy(audio(upstream)).use { proxy ->
                val connection = URL(proxy.mediaUri).openConnection() as HttpURLConnection
                connection.setRequestProperty("Range", "bytes=120-219")
                try {
                    assertEquals(206, connection.responseCode)
                    assertEquals("audio/x-m4a", connection.contentType.substringBefore(';'))
                    assertEquals("bytes 120-219/4096", connection.getHeaderField("Content-Range"))
                    assertContentEquals(
                        sourceBytes.copyOfRange(120, 220),
                        connection.inputStream.use { it.readBytes() },
                    )
                } finally {
                    connection.disconnect()
                }
            }
        } finally {
            upstream.stop(0)
        }
    }

    @Test
    fun downloadsWholeTrackAsMultipleShortUpstreamRequests() {
        val sourceBytes = ByteArray(1_350_000) { (it % 239).toByte() }
        val rangeRequests = AtomicInteger()
        val upstream = rangeServer(sourceBytes, rangeRequests)

        try {
            LocalAudioStreamProxy(audio(upstream)).use { proxy ->
                val connection = URL(proxy.mediaUri).openConnection() as HttpURLConnection
                try {
                    assertEquals(200, connection.responseCode)
                    assertEquals(sourceBytes.size.toLong(), connection.contentLengthLong)
                    assertContentEquals(sourceBytes, connection.inputStream.use { it.readBytes() })
                    assertTrue(rangeRequests.get() >= 3, "Expected at least three short upstream requests")
                } finally {
                    connection.disconnect()
                }
            }
        } finally {
            upstream.stop(0)
        }
    }

    private fun audio(upstream: HttpServer) = ResolvedYouTubeAudio(
        streamUrl = "http://127.0.0.1:${upstream.address.port}/audio",
        format = org.schabi.newpipe.extractor.MediaFormat.M4A,
        contentType = "audio/x-m4a",
        streamId = "test-stream",
        streamBitrate = 128_000,
        deliveryMethod = "PROGRESSIVE_HTTP",
        expectedDurationMs = 30_000,
    )

    private fun rangeServer(
        sourceBytes: ByteArray,
        requestCounter: AtomicInteger? = null,
    ): HttpServer {
        val server = HttpServer.create(
            InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
            0,
        )
        server.createContext("/audio") { exchange ->
            requestCounter?.incrementAndGet()
            val rangeHeader = exchange.requestHeaders.getFirst("Range")
            val match = rangeHeader?.let { Regex("bytes=(\\d+)-(\\d*)").matchEntire(it) }
            if (match == null) {
                exchange.sendResponseHeaders(400, -1)
                exchange.close()
            } else {
                val start = match.groupValues[1].toInt()
                val end = match.groupValues[2].toIntOrNull()
                    ?.coerceAtMost(sourceBytes.lastIndex)
                    ?: sourceBytes.lastIndex
                if (start > sourceBytes.lastIndex) {
                    exchange.responseHeaders.add("Content-Range", "bytes */${sourceBytes.size}")
                    exchange.sendResponseHeaders(416, -1)
                    exchange.close()
                } else {
                    val bytes = sourceBytes.copyOfRange(start, end + 1)
                    exchange.responseHeaders.add("Accept-Ranges", "bytes")
                    exchange.responseHeaders.add(
                        "Content-Range",
                        "bytes $start-$end/${sourceBytes.size}",
                    )
                    exchange.sendResponseHeaders(206, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
        }
        server.start()
        return server
    }
}
