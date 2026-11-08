package com.aura.music.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
private fun <T> HRow(items: List<T>, content: @Composable (T) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(items) { content(it) }
    }
}

@Composable
fun HomeScreen() {
    val env = LocalEnv.current
    val a = LocalAura.current
    val vm = env.vm
    val songs by vm.songs.collectAsState()
    val recents by vm.recents.collectAsState()
    val playlistSongs by vm.playlistSongs.collectAsState()
    val albums = remember(songs) { songs.groupBy { it.albumId }.values.sortedBy { it.first().album.lowercase() } }
    val artists = remember(songs) { songs.groupBy { it.artist }.values.sortedBy { it.first().artist.lowercase() } }
    val newest = remember(songs) { songs.sortedByDescending { it.dateAdded }.take(15) }
    val favSongs = remember(songs, env.favs) { songs.filter { it.id in env.favs } }
    var newPlaylist by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFED9CD0), Color(0xFF4169E1)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
                Text("AURA MUSIC", Modifier.weight(1f).padding(start = 12.dp), fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                GlassIconButton(Icons.Rounded.Search, "Search", { env.nav(Screen.Search) })
                Spacer(Modifier.width(10.dp))
                GlassIconButton(Icons.Rounded.Settings, "Settings", { env.nav(Screen.Settings) })
            }
        }
        if (songs.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.LibraryMusic, "No music found",
                    "Put MP3 files in your Music or Download folder (or download some) and AURA MUSIC will find them automatically.",
                    "Scan again") { vm.refresh(false) }
            }
        } else {
            if (recents.isNotEmpty()) {
                item { SectionHeader("Recently Played") }
                item { HRow(recents) { s -> CollectionCard(s.title, s.artist, s, onClick = { env.vm.play(recents, recents.indexOf(s)) }) } }
            }
            item { SectionHeader("Newly Added") }
            item { HRow(newest) { s -> CollectionCard(s.title, s.artist, s, onClick = { env.vm.play(newest, newest.indexOf(s)) }) } }
            item { SectionHeader("Albums") }
            item { HRow(albums) { l -> CollectionCard(l.first().album, "${l.size} songs", l.first(), onClick = { env.nav(Screen.Album(l.first().albumId)) }) } }
            item { SectionHeader("Artists") }
            item { HRow(artists) { l -> CollectionCard(l.first().artist, "${l.size} songs", l.first(), onClick = { env.nav(Screen.Artist(l.first().artist)) }) } }
            item { SectionHeader("Favorites", onClick = { env.nav(Screen.Favorites) }) }
            item {
                if (favSongs.isEmpty()) Text("Tap the heart in the player to add favorites.", Modifier.padding(horizontal = 20.dp), fontSize = 13.sp, color = a.textDim)
                else HRow(favSongs) { s -> CollectionCard(s.title, s.artist, s, onClick = { env.vm.play(favSongs, favSongs.indexOf(s)) }) }
            }
            item { SectionHeader("Playlists") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    item { CollectionCard("New playlist", "Create", null, Icons.Rounded.Add) { newPlaylist = true } }
                    items(env.playlists) { p ->
                        val count = playlistSongs.count { it.playlistId == p.id }
                        val first = playlistSongs.firstOrNull { it.playlistId == p.id }?.let { ps -> songs.firstOrNull { it.id == ps.songId } }
                        CollectionCard(p.name, "$count songs", first) { env.nav(Screen.Playlist(p.id)) }
                    }
                }
            }
            item {
                SectionHeader("All Songs", trailing = {
                    Text("${songs.size}", color = a.textDim, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                    GlassIconButton(Icons.Rounded.Refresh, "Refresh library", { vm.refresh(false) }, size = 38.dp)
                })
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { i, s -> SongRow(s, songs, i) }
        }
    }
    if (newPlaylist) TextPromptDialog("New playlist", "", "Create", { newPlaylist = false }) { vm.createPlaylist(it); newPlaylist = false }
}
