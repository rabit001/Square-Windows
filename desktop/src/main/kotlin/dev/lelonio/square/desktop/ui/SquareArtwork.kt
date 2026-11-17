package dev.lelonio.square.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.toComposeImageBitmap
import java.util.concurrent.TimeUnit

private val ArtworkHttp = OkHttpClient.Builder()
    .connectTimeout(8, TimeUnit.SECONDS)
    .readTimeout(12, TimeUnit.SECONDS)
    .callTimeout(15, TimeUnit.SECONDS)
    .build()

@Composable
fun SquareArtwork(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val bitmapState = produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = null
        if (url.isNullOrBlank()) return@produceState

        value = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Square-Windows/0.1")
                    .build()

                ArtworkHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body ?: return@use null
                    if (body.contentLength() > MAX_ARTWORK_BYTES) return@use null
                    val bytes = body.bytes()
                    if (bytes.isEmpty() || bytes.size > MAX_ARTWORK_BYTES) return@use null
                    SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color(0xFF65518C),
                        Color(0xFF27293B),
                        Color(0xFF13151D),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = bitmapState.value
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Text(
                "S",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 36.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
            )
        }
    }
}

private const val MAX_ARTWORK_BYTES = 4 * 1024 * 1024
