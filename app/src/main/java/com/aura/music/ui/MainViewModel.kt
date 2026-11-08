package com.aura.music.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aura.music.AuraApp
import com.aura.music.data.*
import com.aura.music.playback.PlayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(FlowPreview::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val a = app as AuraApp
    private val dao = a.db.dao()
    private val repo = a.repo
    val conn = a.player

    private val raw = MutableStateFlow<List<Song>>(emptyList())
    val loading = MutableStateFlow(true)

    val songs: StateFlow<List<Song>> = combine(raw, dao.hidden()) { all, hidden ->
        val hs = hidden.toSet()
        val seen = HashSet<String>()
        all.filter { it.id !in hs }
            .filter { seen.add("${it.title.lowercase()}|${it.artist.lowercase()}|${it.durationMs / 1000}|${it.size}") } // no duplicates
            .sortedBy { it.title.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val hiddenCount = dao.hidden().map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val favorites = dao.favorites().map { it.toSet() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val playlists = dao.playlists().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val playlistSongs = dao.playlistSongs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents = combine(dao.recents(), songs) { ids, s ->
        val m = s.associateBy { it.id }
        ids.mapNotNull { m[it] }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = a.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val playerState = conn.state
    val currentSong: StateFlow<Song?> = combine(conn.state, raw) { st, all ->
        st.mediaId?.let { id -> all.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val query = MutableStateFlow("")
    val results: StateFlow<List<Song>> = combine(query, songs) { q, s ->
        val t = q.trim()
        if (t.isEmpty()) emptyList()
        else s.filter { it.title.contains(t, true) || it.artist.contains(t, true) || it.album.contains(t, true) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var refreshJob: Job? = null

    init {
        // New downloads: MediaStore notifies us, we rescan (debounced).
        viewModelScope.launch { repo.changes().debounce(1200).collect { refresh(true) } }
        // Recently played: record a song once it actually starts playing.
        viewModelScope.launch {
            conn.state.map { if (it.isPlaying) it.mediaId else null }.filterNotNull().distinctUntilChanged().collect { id ->
                dao.addRecent(RecentEntity(id, System.currentTimeMillis()))
                dao.trimRecents()
            }
        }
    }

    fun refresh(silent: Boolean) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!silent) loading.value = true
            raw.value = repo.scan()
            loading.value = false
        }
    }

    // ---- playback
    fun play(list: List<Song>, index: Int) = conn.play(list, index)
    fun shufflePlay(list: List<Song>) {
        if (list.isEmpty()) return
        conn.setMode(PlayMode.SHUFFLE)
        conn.play(list, Random.nextInt(list.size))
    }

    // ---- library data
    fun toggleFavorite(s: Song) = viewModelScope.launch {
        if (s.id in favorites.value) dao.removeFavorite(s.id) else dao.addFavorite(FavoriteEntity(s.id))
    }
    fun hide(s: Song) = viewModelScope.launch { dao.hide(HiddenEntity(s.id)) }
    fun unhideAll() = viewModelScope.launch { dao.unhideAll() }
    fun clearRecents() = viewModelScope.launch { dao.clearRecents() }

    fun createPlaylist(name: String, initial: List<Song> = emptyList()) = viewModelScope.launch {
        val id = dao.insertPlaylist(PlaylistEntity(name = name.trim().ifBlank { "New playlist" }))
        val now = System.currentTimeMillis()
        initial.forEachIndexed { i, s -> dao.addToPlaylist(PlaylistSongEntity(id, s.id, now + i)) }
    }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { dao.renamePlaylist(id, name.trim().ifBlank { "Playlist" }) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { dao.clearPlaylist(id); dao.deletePlaylist(id) }
    fun addToPlaylist(pid: Long, list: List<Song>) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        list.forEachIndexed { i, s -> dao.addToPlaylist(PlaylistSongEntity(pid, s.id, now + i)) }
    }
    fun removeFromPlaylist(pid: Long, s: Song) = viewModelScope.launch { dao.removeFromPlaylist(pid, s.id) }

    // ---- settings
    fun setTheme(v: Int) = viewModelScope.launch { a.settings.setTheme(v) }
    fun setAccent(v: Int) = viewModelScope.launch { a.settings.setAccent(v) }
    fun setGradient(v: Int) = viewModelScope.launch { a.settings.setGradient(v) }
    fun setKeepAwake(v: Boolean) = viewModelScope.launch { a.settings.setKeepAwake(v) }
    fun setQuery(q: String) { query.value = q }
}
