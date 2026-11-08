package com.aura.music.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.aura.music.data.PlaylistEntity
import com.aura.music.data.Song

sealed interface Screen {
    data object Home : Screen
    data object Search : Screen
    data object Settings : Screen
    data object Favorites : Screen
    data class Album(val albumId: Long) : Screen
    data class Artist(val name: String) : Screen
    data class Playlist(val id: Long) : Screen
}

@Immutable
class Env(
    val vm: MainViewModel,
    val favs: Set<Long>,
    val playlists: List<PlaylistEntity>,
    val currentId: Long?,
    val isPlaying: Boolean,
    val openPlayer: () -> Unit,
    val nav: (Screen) -> Unit,
    val back: () -> Unit
)
val LocalEnv = staticCompositionLocalOf<Env> { error("Env missing") }

fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun SongRow(song: Song, queue: List<Song>, index: Int, onRemove: (() -> Unit)? = null, removeLabel: String = "Remove") {
    val env = LocalEnv.current
    val a = LocalAura.current
    val current = env.currentId == song.id
    val fav = song.id in env.favs
    var menu by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var newPl by remember { mutableStateOf(false) }

    Row(
        Modifier.fillMaxWidth()
            .clickable {
                if (current) { if (!env.isPlaying) env.vm.conn.toggle(); env.openPlayer() } else env.vm.play(queue, index)
            }
            .padding(start = 20.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SongArt(song, Modifier.size(54.dp), 128, RoundedCornerShape(16.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current) Icon(Icons.Rounded.GraphicEq, null, tint = a.accent, modifier = Modifier.size(18.dp).padding(end = 4.dp))
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = a.textDim)
        }
        Text(fmt(song.durationMs), fontSize = 12.sp, color = a.textDim)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
            DropdownMenu(menu, { menu = false }, modifier = Modifier.background(a.surface)) {
                DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                    onClick = { menu = false; env.vm.play(queue, index) })
                DropdownMenuItem(text = { Text(if (fav) "Remove from favorites" else "Add to favorites") },
                    leadingIcon = { Icon(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) },
                    onClick = { menu = false; env.vm.toggleFavorite(song) })
                DropdownMenuItem(text = { Text("Add to playlist") }, leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, null) },
                    onClick = { menu = false; addDialog = true })
                DropdownMenuItem(text = { Text("Song info") }, leadingIcon = { Icon(Icons.Rounded.Info, null) },
                    onClick = { menu = false; info = true })
                if (onRemove != null) DropdownMenuItem(text = { Text(removeLabel) }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                    onClick = { menu = false; onRemove() })
                DropdownMenuItem(text = { Text("Remove from library") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                    onClick = { menu = false; env.vm.hide(song) })
            }
        }
    }

    if (info) AlertDialog(
        onDismissRequest = { info = false }, containerColor = a.surface,
        confirmButton = { TextButton(onClick = { info = false }) { Text("Close") } },
        title = { Text("Song info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("Title", song.title); InfoLine("Artist", song.artist); InfoLine("Album", song.album)
                InfoLine("Duration", fmt(song.durationMs)); InfoLine("Size", "%.2f MB".format(song.size / 1048576.0))
                InfoLine("Location", song.path)
            }
        }
    )
    if (addDialog) AlertDialog(
        onDismissRequest = { addDialog = false }, containerColor = a.surface,
        confirmButton = { TextButton(onClick = { addDialog = false; newPl = true }) { Text("New playlist") } },
        dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Cancel") } },
        title = { Text("Add to playlist") },
        text = {
            if (env.playlists.isEmpty()) Text("You have no playlists yet.")
            else LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(env.playlists) { p ->
                    Text(p.name, Modifier.fillMaxWidth().clickable { env.vm.addToPlaylist(p.id, listOf(song)); addDialog = false }.padding(vertical = 12.dp), fontSize = 16.sp)
                }
            }
        }
    )
    if (newPl) TextPromptDialog("New playlist", "", "Create", { newPl = false }) { env.vm.createPlaylist(it, listOf(song)); newPl = false }
}

@Composable
private fun InfoLine(k: String, v: String) {
    Column { Text(k, fontSize = 11.sp, color = LocalAura.current.textDim); Text(v, fontSize = 14.sp) }
}

@Composable
fun TextPromptDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = LocalAura.current.surface,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CollectionCard(title: String, subtitle: String, song: Song?, icon: ImageVector = Icons.Rounded.QueueMusic, onClick: () -> Unit) {
    val a = LocalAura.current
    Column(Modifier.width(132.dp).clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick)) {
        Box(Modifier.size(132.dp).glass(RoundedCornerShape(26.dp)).padding(10.dp)) {
            if (song != null) SongArt(song, Modifier.fillMaxSize(), 256, RoundedCornerShape(18.dp))
            else Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(a.accent.copy(alpha = 0.9f), Color(0xFF817BE5)))),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp)) }
        }
        Text(title, Modifier.padding(start = 6.dp, top = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, Modifier.padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = a.textDim)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp).glass(RoundedCornerShape(32.dp)).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, null, Modifier.size(48.dp), tint = LocalAura.current.accent)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = LocalAura.current.textDim, fontSize = 14.sp, lineHeight = 20.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) AccentButton(action, onAction)
    }
}

@Composable
fun AccentButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val a = LocalAura.current
    Box(
        modifier.clip(CircleShape).background(Brush.verticalGradient(listOf(a.accent, a.accent.copy(alpha = 0.82f))))
            .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontWeight = FontWeight.SemiBold) }
}
