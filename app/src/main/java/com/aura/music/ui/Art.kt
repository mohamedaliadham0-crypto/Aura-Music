package com.aura.music.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aura.music.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Embedded artwork via MediaStore thumbnails, memory-cached; failures are remembered so we never retry per scroll. */
object ArtworkCache {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun peek(song: Song, px: Int): Bitmap? = cache.get("${song.id}_$px")

    suspend fun load(ctx: Context, song: Song, px: Int): Bitmap? {
        val key = "${song.id}_$px"
        cache.get(key)?.let { return it }
        if (key in misses) return null
        val bmp = withContext(Dispatchers.IO) {
            try { ctx.contentResolver.loadThumbnail(song.uri, Size(px, px), null) } catch (e: Exception) { null }
        }
        if (bmp != null) cache.put(key, bmp) else misses.add(key)
        return bmp
    }
}

private val fallbackPalette = listOf(
    listOf(Color(0xFFED9CD0), Color(0xFF817BE5)),
    listOf(Color(0xFF4169E1), Color(0xFFA8A1E8)),
    listOf(Color(0xFF817BE5), Color(0xFFED9CD0)),
    listOf(Color(0xFF192B72), Color(0xFF4169E1))
)

@Composable
fun SongArt(song: Song?, modifier: Modifier, px: Int = 256, shape: Shape = RoundedCornerShape(16.dp)) {
    val ctx = LocalContext.current.applicationContext
    val bmp by produceState<Bitmap?>(song?.let { ArtworkCache.peek(it, px) }, song?.id, px) {
        value = song?.let { ArtworkCache.peek(it, px) ?: ArtworkCache.load(ctx, it, px) }
    }
    val colors = fallbackPalette[((song?.id ?: 0L) % fallbackPalette.size).toInt().coerceAtLeast(0)]
    Box(modifier.clip(shape).background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Rounded.MusicNote, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.fillMaxSize(0.45f))
    }
}

