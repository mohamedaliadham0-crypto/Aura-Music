package com.aura.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import com.aura.music.data.Song

@Composable
fun DetailScreen(
    title: String, subtitle: String, songs: List<Song>,
    onRemove: ((Song) -> Unit)? = null,
    menu: (@Composable () -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null
) {
    val env = LocalEnv.current
    val a = LocalAura.current
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", env.back)
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(subtitle, fontSize = 13.sp, color = a.textDim)
                }
                menu?.invoke()
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentButton("Play all", { env.vm.play(songs, 0) })
                Box(
                    Modifier.glass(CircleShape).clickable { env.vm.shufflePlay(songs) }.padding(horizontal = 22.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Shuffle", fontWeight = FontWeight.SemiBold) }
            }
        }
        header?.invoke(this)
        if (songs.isEmpty()) item { EmptyState(Icons.Rounded.MusicOff, "Nothing here yet", "Songs you add will show up in this list.") }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, songs, i, onRemove = onRemove?.let { f -> { f(s) } }, removeLabel = "Remove from playlist")
        }
    }
}

@Composable
fun AlbumScreen(albumId: Long) {
    val songs by LocalEnv.current.vm.songs.collectAsState()
    val l = remember(songs, albumId) { songs.filter { it.albumId == albumId } }
    DetailScreen(l.firstOrNull()?.album ?: "Album", "${l.size} songs", l)
}

@Composable
fun ArtistScreen(name: String) {
    val songs by LocalEnv.current.vm.songs.collectAsState()
    val l = remember(songs, name) { songs.filter { it.artist == name } }
    DetailScreen(name, "${l.size} songs", l)
}

@Composable
fun FavoritesScreen() {
    val env = LocalEnv.current
    val songs by env.vm.songs.collectAsState()
    val l = remember(songs, env.favs) { songs.filter { it.id in env.favs } }
    DetailScreen("Favorites", "${l.size} songs", l)
}

@Composable
fun PlaylistScreen(id: Long) {
    val env = LocalEnv.current
    val vm = env.vm
    val songs by vm.songs.collectAsState()
    val links by vm.playlistSongs.collectAsState()
    val playlist = env.playlists.firstOrNull { it.id == id }
    val list = remember(songs, links, id) {
        val m = songs.associateBy { it.id }
        links.filter { it.playlistId == id }.mapNotNull { m[it.songId] }
    }
    var menu by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf(false) }
    val a = LocalAura.current

    LaunchedEffect(playlist) { if (playlist == null) env.back() }
    if (playlist == null) return

    DetailScreen(playlist.name, "${list.size} songs", list, onRemove = { vm.removeFromPlaylist(id, it) },
        menu = {
            GlassIconButton(Icons.Rounded.Add, "Add songs", { adding = true }, size = 42.dp)
            Spacer(Modifier.width(8.dp))
            Box {
                GlassIconButton(Icons.Rounded.MoreVert, "Playlist options", { menu = true }, size = 42.dp)
                DropdownMenu(menu, { menu = false }, modifier = Modifier.background(a.surface)) {
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; rename = true })
                    DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        })

    if (rename) TextPromptDialog("Rename playlist", playlist.name, "Save", { rename = false }) { vm.renamePlaylist(id, it); rename = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = a.surface,
        title = { Text("Delete \"${playlist.name}\"?") },
        text = { Text("Your songs stay on the device; only the playlist is removed.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deletePlaylist(id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
    if (adding) {
        val existing = remember(list) { list.map { it.id }.toSet() }
        val picked = remember { mutableStateListOf<Song>() }
        AlertDialog(
            onDismissRequest = { adding = false }, containerColor = a.surface,
            title = { Text("Add songs") },
            text = {
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(songs.filter { it.id !in existing }, key = { it.id }) { s ->
                        Row(Modifier.fillMaxWidth().clickable { if (s in picked) picked.remove(s) else picked.add(s) }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(s in picked, { if (it) picked.add(s) else picked.remove(s) })
                            Column { Text(s.title, maxLines = 1, fontSize = 15.sp); Text(s.artist, maxLines = 1, fontSize = 12.sp, color = a.textDim) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.addToPlaylist(id, picked.toList()); adding = false }, enabled = picked.isNotEmpty()) { Text("Add ${picked.size}") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SearchScreen() {
    val env = LocalEnv.current
    val a = LocalAura.current
    val vm = env.vm
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val focus = remember { FocusRequester() }
    val fm = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    DisposableEffect(Unit) { onDispose { vm.setQuery("") } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Rounded.ArrowBack, "Back", env.back)
            Row(
                Modifier.weight(1f).padding(start = 12.dp).height(46.dp).glass(RoundedCornerShape(23.dp)).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, null, tint = a.textDim)
                Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    if (q.isEmpty()) Text("Songs, artists, albums", color = a.textDim, fontSize = 16.sp)
                    BasicTextField(
                        q, { vm.setQuery(it) }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = TextStyle(color = a.text, fontSize = 16.sp), cursorBrush = SolidColor(a.accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { fm.clearFocus() })
                    )
                }
                if (q.isNotEmpty()) Icon(Icons.Rounded.Clear, "Clear search", Modifier.size(22.dp).clip(CircleShape).clickable { vm.setQuery("") })
            }
        }
        when {
            q.isBlank() -> EmptyState(Icons.Rounded.Search, "Search your music", "Results update as you type.")
            results.isEmpty() -> EmptyState(Icons.Rounded.SearchOff, "No matches", "Nothing matches \"$q\".")
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 150.dp)) {
                itemsIndexed(results, key = { _, s -> s.id }) { i, s -> SongRow(s, results, i) }
            }
        }
    }
}
