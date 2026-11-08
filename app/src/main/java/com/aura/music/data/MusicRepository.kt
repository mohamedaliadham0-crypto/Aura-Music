package com.aura.music.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext

/** MediaStore is the single source of truth for discoverable audio. */
class MusicRepository(private val ctx: Context) {
    private val collection: Uri = Media.EXTERNAL_CONTENT_URI
    private val projection = arrayOf(
        Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM, Media.ALBUM_ID,
        Media.DURATION, Media.SIZE, Media.DATE_ADDED, Media.DISPLAY_NAME, Media.RELATIVE_PATH
    )
    private val musicFilter =
        "${Media.IS_RINGTONE}=0 AND ${Media.IS_NOTIFICATION}=0 AND ${Media.IS_ALARM}=0 AND " +
            "${Media.SIZE}>0 AND ${Media.DURATION}>0 AND (${Media.IS_MUSIC}!=0 OR ${Media.MIME_TYPE}='audio/mpeg')"

    suspend fun scan(): List<Song> = withContext(Dispatchers.IO) {
        try { query(musicFilter) } catch (e: Exception) { emptyList() }
    }

    fun byIds(ids: List<Long>): List<Song> =
        ids.chunked(500).flatMap { query("${Media._ID} IN (${it.joinToString(",")})") }

    /** Emits whenever MediaStore audio changes (e.g. a new MP3 finished downloading). */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        ctx.contentResolver.registerContentObserver(collection, true, observer)
        awaitClose { ctx.contentResolver.unregisterContentObserver(observer) }
    }

    private fun query(selection: String): List<Song> {
        val out = ArrayList<Song>()
        ctx.contentResolver.query(collection, projection, selection, null, "${Media.DATE_ADDED} DESC")?.use { c ->
            val iId = c.getColumnIndexOrThrow(Media._ID)
            val iTitle = c.getColumnIndexOrThrow(Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(Media.DURATION)
            val iSize = c.getColumnIndexOrThrow(Media.SIZE)
            val iAdded = c.getColumnIndexOrThrow(Media.DATE_ADDED)
            val iName = c.getColumnIndexOrThrow(Media.DISPLAY_NAME)
            val iPath = c.getColumnIndexOrThrow(Media.RELATIVE_PATH)
            while (c.moveToNext()) {
                val name = c.getString(iName).orEmpty()
                val fileTitle = name.substringBeforeLast('.').replace('_', ' ').trim().ifBlank { "Unknown track" }
                out += Song(
                    id = c.getLong(iId),
                    title = clean(c.getString(iTitle)) ?: fileTitle,
                    artist = clean(c.getString(iArtist)) ?: "Unknown artist",
                    album = clean(c.getString(iAlbum)) ?: "Unknown album",
                    albumId = c.getLong(iAlbumId),
                    durationMs = c.getLong(iDur),
                    size = c.getLong(iSize),
                    path = c.getString(iPath).orEmpty() + name,
                    dateAdded = c.getLong(iAdded)
                )
            }
        }
        return out
    }

    private fun clean(s: String?): String? = s?.trim()?.takeIf { it.isNotEmpty() && it != "<unknown>" }
}
