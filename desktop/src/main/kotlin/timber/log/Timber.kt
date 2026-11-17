package timber.log

import java.util.logging.Level
import java.util.logging.Logger

/**
 * Small JVM logging facade for the vendored InnerTube sources.
 *
 * The Android source module uses Timber. The desktop build only needs the
 * debug/warning/error calls used by those sources, so this bridge routes them
 * to java.util.logging without adding an Android dependency.
 */
object Timber {
    private val logger = Logger.getLogger("com.metrolist.innertube")

    @JvmStatic
    fun d(message: String) {
        logger.fine(message)
    }

    @JvmStatic
    fun w(message: String) {
        logger.warning(message)
    }

    @JvmStatic
    fun e(throwable: Throwable, message: String) {
        logger.log(Level.SEVERE, message, throwable)
    }
}
