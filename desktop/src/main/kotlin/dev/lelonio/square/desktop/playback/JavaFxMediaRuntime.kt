package dev.lelonio.square.desktop.playback

import javafx.application.Platform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Starts JavaFX lazily and confines MediaPlayer work to the JavaFX thread.
 * Compose Desktop keeps its own window/event loop; JavaFX is used as an audio
 * decoder/output runtime only.
 */
internal object JavaFxMediaRuntime {
    private val started = CompletableDeferred<Unit>()
    @Volatile private var startupFailed = false

    init {
        try {
            Platform.startup {
                Platform.setImplicitExit(false)
                started.complete(Unit)
            }
        } catch (alreadyStarted: IllegalStateException) {
            started.complete(Unit)
        } catch (error: Throwable) {
            startupFailed = true
            started.completeExceptionally(error)
        }
    }

    suspend fun <T> onFxThread(block: () -> T): T {
        started.await()
        if (Platform.isFxApplicationThread()) return block()

        return suspendCancellableCoroutine { continuation ->
            Platform.runLater {
                if (!continuation.isActive) return@runLater
                try {
                    continuation.resume(block())
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }

    fun shutdown(cleanup: () -> Unit) {
        if (!started.isCompleted || started.isCancelled || startupFailed) return

        Platform.runLater {
            try {
                cleanup()
            } finally {
                Platform.exit()
            }
        }
    }
}
