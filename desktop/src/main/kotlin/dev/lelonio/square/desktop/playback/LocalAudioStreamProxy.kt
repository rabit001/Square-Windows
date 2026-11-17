package dev.lelonio.square.desktop.playback

import dev.lelonio.square.desktop.backend.youtube.ResolvedYouTubeAudio
import dev.lelonio.square.desktop.backend.youtube.audioFileSuffix
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
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
 * A small loopback-only HTTP proxy for one YouTube audio stream.
 *
 * JavaFX receives a normal URL with an explicit audio MIME type. Range requests
 * from JavaFX are forwarded upstream, so it can begin playback while the rest
 * of the track is still arriving instead of waiting for a complete temp file.
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
    private val endpointPath = "/${UUID.randomUUID().toString().replace("-", "")}${audioFileSuffix(audio.format)}"

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
                socket.soTimeout = 90_000
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

                val incomingHeaders = mutableMapOf<String, String>()
                var headerCount = 0
                while (true) {
                    val line = readAsciiLine(input) ?: break
                    if (line.isEmpty()) break
                    headerCount += 1
                    if (headerCount > 64) {
                        writeError(output, 431, "Request Header Fields Too Large")
                        return
                    }
                    val separator = line.indexOf(':')
                    if (separator > 0) {
                        incomingHeaders[line.substring(0, separator).trim().lowercase(Locale.ROOT)] =
                            line.substring(separator + 1).trim()
                    }
                }

                val requestBuilder = Request.Builder()
                    .url(audio.streamUrl)
                    .header("User-Agent", STREAM_USER_AGENT)
                    .header("Accept", "*/*")
                    .header("Accept-Encoding", "identity")

                incomingHeaders["range"]?.let { requestBuilder.header("Range", it) }
                incomingHeaders["if-range"]?.let { requestBuilder.header("If-Range", it) }

                val upstreamRequest = if (method == "HEAD") {
                    requestBuilder.head().build()
                } else {
                    requestBuilder.get().build()
                }

                val call = httpClient.newCall(upstreamRequest)
                activeCalls.add(call)
                try {
                    call.execute().use { response ->
                        val body = response.body
                        val contentLength = response.header("Content-Length")
                            ?: body?.contentLength()?.takeIf { it >= 0 }?.toString()
                        val statusText = response.message.ifBlank { reasonFor(response.code) }

                        writeAscii(output, "HTTP/1.1 ${response.code} $statusText\r\n")
                        writeAscii(output, "Content-Type: ${audio.contentType}\r\n")
                        writeAscii(output, "Accept-Ranges: bytes\r\n")
                        writeAscii(output, "Cache-Control: no-store\r\n")
                        writeAscii(output, "Connection: close\r\n")
                        writeAscii(output, "X-Content-Type-Options: nosniff\r\n")
                        response.header("Content-Range")?.let {
                            writeAscii(output, "Content-Range: $it\r\n")
                        }
                        contentLength?.let {
                            writeAscii(output, "Content-Length: $it\r\n")
                        }
                        writeAscii(output, "\r\n")
                        output.flush()

                        if (method != "HEAD" && body != null) {
                            body.byteStream().use { upstream ->
                                val buffer = ByteArray(COPY_BUFFER_BYTES)
                                while (true) {
                                    if (closed.get() || socket.isClosed) break
                                    val count = upstream.read(buffer)
                                    if (count < 0) break
                                    output.write(buffer, 0, count)
                                    output.flush()
                                }
                            }
                        }
                    }
                } finally {
                    activeCalls.remove(call)
                }
            } catch (error: Exception) {
                if (!closed.get() && error !is SocketException) {
                    System.err.println("[SquarePlayback] Audio proxy request failed: ${error.message}")
                }
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

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { server.close() }
        activeCalls.forEach { it.cancel() }
        activeCalls.clear()
        workers.shutdownNow()
        runCatching { httpClient.dispatcher.executorService.shutdownNow() }
        runCatching { httpClient.connectionPool.evictAll() }
    }

    companion object {
        private const val COPY_BUFFER_BYTES = 64 * 1024
        private const val MAX_HEADER_LINE_BYTES = 16 * 1024
        private const val STREAM_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"

        private fun createHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
    }
}
