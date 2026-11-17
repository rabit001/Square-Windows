package dev.lelonio.square.desktop.playback

import dev.lelonio.square.desktop.backend.youtube.ResolvedYouTubeAudio
import dev.lelonio.square.desktop.backend.youtube.audioFileSuffix
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Loopback-only HTTP bridge that reads Googlevideo streams in bounded ranges.
 *
 * YouTube may throttle a single long-lived media request after a few seconds.
 * This proxy keeps JavaFX's view as one ordinary HTTP resource while opening
 * fresh 512 KiB upstream range requests until the requested resource is read.
 */
internal class LocalAudioStreamProxy(
    private val audio: ResolvedYouTubeAudio,
    private val httpClient: OkHttpClient = createHttpClient(),
) : AutoCloseable {

    private val closed = AtomicBoolean(false)
    private val activeCalls = ConcurrentHashMap.newKeySet<okhttp3.Call>()
    private val workers: ExecutorService = Executors.newCachedThreadPool { runnable ->
        Thread(runnable, "square-audio-proxy").apply { isDaemon = true }
    }
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }
    private val endpointPath =
        "/${UUID.randomUUID().toString().replace("-", "")}${audioFileSuffix(audio.format)}"

    val mediaUri: String = "http://127.0.0.1:${server.localPort}$endpointPath"

    init {
        workers.execute(::acceptLoop)
    }

    private fun acceptLoop() {
        while (!closed.get()) {
            try {
                val client = server.accept()
                workers.execute { handleClient(client) }
            } catch (error: SocketException) {
                if (!closed.get()) {
                    System.err.println("[SquarePlayback] Audio proxy accept failed: ${error.message}")
                }
                return
            } catch (error: IOException) {
                if (!closed.get()) {
                    System.err.println("[SquarePlayback] Audio proxy accept failed: ${error.message}")
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val output = BufferedOutputStream(socket.getOutputStream())
                val requestLine = readAsciiLine(input) ?: return
                val parts = requestLine.trim().split(Regex("\\s+"), limit = 3)
                if (parts.size < 2) {
                    writeError(output, 400, "Bad Request")
                    return
                }

                val method = parts[0].uppercase(Locale.ROOT)
                val path = parts[1].substringBefore('?')
                if (path != endpointPath) {
                    writeError(output, 404, "Not Found")
                    return
                }
                if (method != "GET" && method != "HEAD") {
                    writeError(output, 405, "Method Not Allowed")
                    return
                }

                val headers = readHeaders(input) ?: run {
                    writeError(output, 400, "Bad Request")
                    return
                }
                val clientRange = try {
                    parseClientRange(headers["range"])
                } catch (error: IllegalArgumentException) {
                    writeError(output, 416, "Range Not Satisfiable")
                    return
                }

                serveMedia(method == "HEAD", clientRange, output)
            } catch (error: Exception) {
                if (!closed.get() && error !is SocketException) {
                    System.err.println("[SquarePlayback] Audio proxy request failed: ${error.message}")
                }
            }
        }
    }

    private fun serveMedia(
        headOnly: Boolean,
        clientRange: ClientRange?,
        output: BufferedOutputStream,
    ) {
        // A suffix range needs the resource length before its actual start is known.
        val initialStart = if (clientRange?.suffixLength != null) 0L else clientRange?.start ?: 0L
        val initialEnd = if (clientRange?.suffixLength != null) 0L else {
            minOf(
                clientRange?.end ?: Long.MAX_VALUE,
                safeAdd(initialStart, CHUNK_BYTES - 1),
            )
        }

        val initialResponse = executeRange(initialStart, initialEnd)
        initialResponse.use { response ->
            if (response.code == 416) {
                writeRangeNotSatisfiable(output, response.header("Content-Range"))
                return
            }

            if (response.code == 200) {
                // A fallback for hosts that ignore Range. YouTube's normal
                // Googlevideo URLs return 206; this path remains useful for
                // alternate providers and keeps the failure understandable.
                serveNonRangeResponse(response, headOnly, clientRange, output)
                return
            }

            if (response.code != 206) {
                writeUpstreamError(output, response.code, response.message)
                return
            }

            val firstRange = parseContentRange(response.header("Content-Range"))
                ?: throw IOException("YouTube returned an invalid Content-Range header.")
            val totalBytes = firstRange.total
                ?: throw IOException("YouTube omitted the total audio size from Content-Range.")
            if (totalBytes <= 0L || firstRange.end < firstRange.start) {
                throw IOException("YouTube returned an invalid audio resource length.")
            }

            val start = when {
                clientRange?.suffixLength != null ->
                    (totalBytes - clientRange.suffixLength).coerceAtLeast(0L)
                else -> clientRange?.start ?: 0L
            }
            if (start >= totalBytes) {
                writeRangeNotSatisfiable(output, "bytes */$totalBytes")
                return
            }
            val end = minOf(clientRange?.end ?: (totalBytes - 1L), totalBytes - 1L)
            if (end < start) {
                writeRangeNotSatisfiable(output, "bytes */$totalBytes")
                return
            }

            val responseLength = end - start + 1L
            val statusCode = if (clientRange == null) 200 else 206
            writeResponseHeaders(
                output = output,
                statusCode = statusCode,
                statusText = if (statusCode == 200) "OK" else "Partial Content",
                contentLength = responseLength,
                contentRange = if (statusCode == 206) "bytes $start-$end/$totalBytes" else null,
            )
            if (headOnly) return

            var cursor = start
            var firstResponseUsed = false
            while (cursor <= end && !closed.get()) {
                val currentResponse = if (!firstResponseUsed && firstRange.start == cursor) {
                    firstResponseUsed = true
                    response
                } else {
                    executeRange(cursor, minOf(end, safeAdd(cursor, CHUNK_BYTES - 1)))
                }

                if (currentResponse === response) {
                    copyRangeBody(currentResponse, cursor, end, output)
                } else {
                    currentResponse.use { next ->
                        if (next.code == 200) {
                            throw IOException("YouTube stopped honoring byte-range requests mid-stream.")
                        }
                        if (next.code != 206) {
                            throw IOException("YouTube audio range request failed with HTTP ${next.code}.")
                        }
                        copyRangeBody(next, cursor, end, output)
                    }
                }

                val servedThrough = lastServedEnd
                if (servedThrough < cursor) {
                    throw EOFException("YouTube returned an empty audio range at byte $cursor.")
                }
                cursor = servedThrough + 1L
            }
            output.flush()
        }
    }

    // Updated only by the single request worker inside serveMedia.
    private var lastServedEnd: Long = -1L

    private fun copyRangeBody(
        response: okhttp3.Response,
        requestedStart: Long,
        requestedEnd: Long,
        output: BufferedOutputStream,
    ) {
        val range = parseContentRange(response.header("Content-Range"))
            ?: throw IOException("YouTube audio response has no valid Content-Range.")
        if (range.start != requestedStart) {
            throw IOException(
                "YouTube returned bytes ${range.start}-${range.end}; expected byte $requestedStart.",
            )
        }

        val segmentEnd = minOf(range.end, requestedEnd)
        val expectedBytes = segmentEnd - requestedStart + 1L
        if (expectedBytes <= 0L) throw IOException("YouTube returned an empty byte range.")

        val body = response.body
            ?: throw IOException("YouTube returned an empty audio response.")
        var copied = 0L
        body.byteStream().use { upstream ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (copied < expectedBytes) {
                val wanted = minOf(buffer.size.toLong(), expectedBytes - copied).toInt()
                val count = upstream.read(buffer, 0, wanted)
                if (count < 0) {
                    throw EOFException(
                        "YouTube audio range was truncated at byte ${requestedStart + copied}.",
                    )
                }
                output.write(buffer, 0, count)
                copied += count
            }
        }
        output.flush()
        lastServedEnd = segmentEnd
        System.err.println(
            "[SquarePlayback] Audio chunk delivered: bytes=$requestedStart-$segmentEnd, " +
                "stream=${audio.streamId}",
        )
    }

    private fun serveNonRangeResponse(
        response: okhttp3.Response,
        headOnly: Boolean,
        clientRange: ClientRange?,
        output: BufferedOutputStream,
    ) {
        val body = response.body
            ?: throw IOException("YouTube returned an empty audio response.")
        val total = response.header("Content-Length")?.toLongOrNull()
            ?: body.contentLength().takeIf { it >= 0L }
            ?: throw IOException("YouTube did not report the audio resource length.")

        if (clientRange?.start != null && clientRange.start > 0L) {
            throw IOException("The audio host ignored range requests; seeking is unavailable for this stream.")
        }
        val start = clientRange?.start ?: 0L
        val end = minOf(clientRange?.end ?: (total - 1L), total - 1L)
        val length = end - start + 1L
        val status = if (clientRange == null) 200 else 206
        writeResponseHeaders(
            output,
            status,
            if (status == 200) "OK" else "Partial Content",
            length,
            if (status == 206) "bytes $start-$end/$total" else null,
        )
        if (headOnly) return

        var remaining = length
        body.byteStream().use { upstream ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (remaining > 0L) {
                val count = upstream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (count < 0) throw EOFException("The audio host closed the stream early.")
                output.write(buffer, 0, count)
                remaining -= count
            }
        }
        output.flush()
    }

    private fun executeRange(start: Long, end: Long): okhttp3.Response {
        val request = Request.Builder()
            .url(audio.streamUrl)
            .header("User-Agent", STREAM_USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("Range", "bytes=$start-$end")
            .get()
            .build()

        val call = httpClient.newCall(request)
        activeCalls.add(call)
        return try {
            call.execute().also { response ->
                activeCalls.remove(call)
                System.err.println(
                    "[SquarePlayback] Upstream range: request=$start-$end, " +
                        "status=${response.code}, contentRange=${response.header("Content-Range") ?: "none"}",
                )
            }
        } catch (error: Exception) {
            activeCalls.remove(call)
            throw error
        }
    }

    private fun readHeaders(input: BufferedInputStream): Map<String, String>? {
        val headers = mutableMapOf<String, String>()
        var count = 0
        while (true) {
            val line = readAsciiLine(input) ?: return if (headers.isEmpty()) null else headers
            if (line.isEmpty()) return headers
            count += 1
            if (count > MAX_HEADERS) throw IOException("Too many audio proxy request headers.")
            val separator = line.indexOf(':')
            if (separator > 0) {
                headers[line.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                    line.substring(separator + 1).trim()
            }
        }
    }

    private fun readAsciiLine(input: BufferedInputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val value = input.read()
            if (value < 0) {
                if (bytes.size() == 0) return null
                break
            }
            if (value == '\n'.code) break
            if (value != '\r'.code) {
                if (bytes.size() >= MAX_HEADER_LINE_BYTES) {
                    throw IOException("Audio proxy request header line is too long.")
                }
                bytes.write(value)
            }
        }
        return bytes.toString(StandardCharsets.ISO_8859_1.name())
    }

    private fun writeResponseHeaders(
        output: BufferedOutputStream,
        statusCode: Int,
        statusText: String,
        contentLength: Long,
        contentRange: String?,
    ) {
        writeAscii(output, "HTTP/1.1 $statusCode $statusText\r\n")
        writeAscii(output, "Content-Type: ${audio.contentType}\r\n")
        writeAscii(output, "Accept-Ranges: bytes\r\n")
        writeAscii(output, "Cache-Control: no-store\r\n")
        writeAscii(output, "Connection: close\r\n")
        writeAscii(output, "X-Content-Type-Options: nosniff\r\n")
        contentRange?.let { writeAscii(output, "Content-Range: $it\r\n") }
        writeAscii(output, "Content-Length: $contentLength\r\n\r\n")
        output.flush()
    }

    private fun writeRangeNotSatisfiable(output: BufferedOutputStream, contentRange: String?) {
        writeAscii(output, "HTTP/1.1 416 Range Not Satisfiable\r\n")
        writeAscii(output, "Content-Type: text/plain; charset=utf-8\r\n")
        contentRange?.let { writeAscii(output, "Content-Range: $it\r\n") }
        writeAscii(output, "Content-Length: 0\r\nConnection: close\r\n\r\n")
        output.flush()
    }

    private fun writeUpstreamError(output: BufferedOutputStream, status: Int, message: String) {
        val safeStatus = if (status in 400..599) status else 502
        val safeMessage = message.ifBlank { reasonFor(safeStatus) }
        writeError(output, safeStatus, safeMessage)
    }

    private fun writeError(output: BufferedOutputStream, code: Int, message: String) {
        val body = message.toByteArray(StandardCharsets.UTF_8)
        writeAscii(output, "HTTP/1.1 $code $message\r\n")
        writeAscii(output, "Content-Type: text/plain; charset=utf-8\r\n")
        writeAscii(output, "Content-Length: ${body.size}\r\n")
        writeAscii(output, "Connection: close\r\n\r\n")
        output.write(body)
        output.flush()
    }

    private fun writeAscii(output: BufferedOutputStream, value: String) {
        output.write(value.toByteArray(StandardCharsets.ISO_8859_1))
    }

    private fun parseClientRange(value: String?): ClientRange? {
        if (value.isNullOrBlank()) return null
        require(value.startsWith("bytes=", ignoreCase = true)) { "Only byte ranges are supported." }
        val parts = value.substringAfter('=').trim().split('-', limit = 2)
        require(parts.size == 2) { "Invalid byte range." }
        val start = parts[0].toLongOrNull()
        val end = parts[1].takeIf { it.isNotBlank() }?.toLongOrNull()
        require(start != null || end != null) { "Invalid byte range." }
        require(start == null || start >= 0L) { "Invalid byte range start." }
        require(end == null || end >= 0L) { "Invalid byte range end." }
        require(start == null || end == null || end >= start) { "Invalid byte range order." }
        return if (start == null) {
            ClientRange(start = null, end = null, suffixLength = end)
        } else {
            ClientRange(start = start, end = end, suffixLength = null)
        }
    }

    private fun parseContentRange(value: String?): UpstreamRange? {
        if (value == null) return null
        val match = CONTENT_RANGE_REGEX.matchEntire(value.trim()) ?: return null
        return UpstreamRange(
            start = match.groupValues[1].toLongOrNull() ?: return null,
            end = match.groupValues[2].toLongOrNull() ?: return null,
            total = match.groupValues[3].takeUnless { it == "*" }?.toLongOrNull(),
        )
    }

    private fun safeAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
        workers.shutdownNow()
        runCatching { httpClient.dispatcher.executorService.shutdownNow() }
        runCatching { httpClient.connectionPool.evictAll() }
    }

    private data class ClientRange(
        val start: Long?,
        val end: Long?,
        val suffixLength: Long?,
    )

    private data class UpstreamRange(
        val start: Long,
        val end: Long,
        val total: Long?,
    )

    companion object {
        private const val CHUNK_BYTES = 512L * 1024L
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_HEADER_LINE_BYTES = 16 * 1024
        private const val MAX_HEADERS = 64
        private const val SOCKET_TIMEOUT_MS = 90_000
        private val CONTENT_RANGE_REGEX = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private fun createHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()

        private fun reasonFor(code: Int): String = when (code) {
            200 -> "OK"
            206 -> "Partial Content"
            302 -> "Found"
            304 -> "Not Modified"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            416 -> "Range Not Satisfiable"
            429 -> "Too Many Requests"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            else -> "Upstream Response"
        }
    }
}
