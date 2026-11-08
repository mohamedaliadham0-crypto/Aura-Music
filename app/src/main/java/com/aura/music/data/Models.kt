package com.aura.music.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val size: Long,
    val path: String,
    val dateAdded: Long
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    val artUri: Uri get() = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)
}

@Entity(tableName = "favorites") data class FavoriteEntity(@PrimaryKey val songId: Long)
@Entity(tableName = "playlists") data class PlaylistEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)
@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(val playlistId: Long, val songId: Long, val addedAt: Long)
@Entity(tableName = "hidden") data class HiddenEntity(@PrimaryKey val songId: Long)
@Entity(tableName = "recents") data class RecentEntity(@PrimaryKey val songId: Long, val playedAt: Long)

@Dao
interface AuraDao {
    @Query("SELECT songId FROM favorites") fun favorites(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(e: FavoriteEntity)
    @Query("DELETE FROM favorites WHERE songId = :id") suspend fun removeFavorite(id: Long)

    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") fun playlists(): Flow<List<PlaylistEntity>>
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylist(id: Long)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :id") suspend fun clearPlaylist(id: Long)
    @Query("SELECT * FROM playlist_songs ORDER BY addedAt") fun playlistSongs(): Flow<List<PlaylistSongEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToPlaylist(e: PlaylistSongEntity)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid") suspend fun removeFromPlaylist(pid: Long, sid: Long)

    @Query("SELECT songId FROM hidden") fun hidden(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun hide(e: HiddenEntity)
    @Query("DELETE FROM hidden") suspend fun unhideAll()

    @Query("SELECT songId FROM recents ORDER BY playedAt DESC LIMIT 30") fun recents(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addRecent(e: RecentEntity)
    @Query("DELETE FROM recents") suspend fun clearRecents()
    @Query("DELETE FROM recents WHERE songId NOT IN (SELECT songId FROM recents ORDER BY playedAt DESC LIMIT 50)")
    suspend fun trimRecents()
}

@Database(
    entities = [FavoriteEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, HiddenEntity::class, RecentEntity::class],
    version = 1, exportSchema = false
)
abstract class AuraDb : RoomDatabase() { abstract fun dao(): AuraDao }
