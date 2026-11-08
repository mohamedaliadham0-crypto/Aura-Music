package com.aura.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.aura.music.data.Song

fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setMediaId(id.toString())
    .setUri(uri)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artUri)
            .build()
    )
    .build()

object PlayMode { const val NORMAL = 0; const val SHUFFLE = 1; const val REPEAT_ONE = 2 }
