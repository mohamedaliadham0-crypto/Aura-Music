package com.aura.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.data.Song
import com.aura.music.playback.PlayMode
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(song: Song, onClose: () -> Unit) {
    val env = LocalEnv.current
    val a = LocalAura.current
    val vm = env.vm
    val st by vm.playerState.collectAsState()
    val settings by vm.settings.collectAsState()
    val view = LocalView.current
    DisposableEffect(settings.keepAwake) { view.keepScreenOn = settings.keepAwake; onDispose { view.keepScreenOn = false } }

    val duration = if (st.durationMs > 0) st.durationMs else song.durationMs
    var pos by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(st.isPlaying, song.id) {
        while (true) { pos = vm.conn.position(); delay(if (st.isPlaying) 200 else 500) }
    }
    val fraction = dragging ?: if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.025f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "p"
    )

    AuraBackground {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.KeyboardArrowDown, "Close player", onClose)
                Text("Listen now", Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, letterSpacing = 1.sp)
                val fav = song.id in env.favs
                GlassIconButton(if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Favorite", { vm.toggleFavorite(song) },
                    tint = if (fav) a.accent else null)
            }
            Spacer(Modifier.weight(0.6f))
            Box(
                Modifier.fillMaxWidth(0.84f).aspectRatio(1f)
                    .graphicsLayer { val s = if (st.isPlaying) pulse else 1f; scaleX = s; scaleY = s }
                    .glass(RoundedCornerShape(48.dp)).padding(18.dp)
            ) {
                AnimatedContent(
                    targetState = song,
                    transitionSpec = {
                        (fadeIn(tween(380)) + scaleIn(initialScale = 0.88f, animationSpec = tween(380))) togetherWith
                            (fadeOut(tween(250)) + scaleOut(targetScale = 1.08f, animationSpec = tween(250)))
                    },
                    label = "art"
                ) { s -> SongArt(s, Modifier.fillMaxSize(), 640, RoundedCornerShape(34.dp)) }
            }
            Spacer(Modifier.weight(0.5f))
            Text(song.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Text(song.artist, fontSize = 16.sp, color = a.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(18.dp))

            Slider(
                value = fraction,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    dragging?.let { f -> val target = (f * duration).toLong(); vm.conn.seekTo(target); pos = target }
                    dragging = null
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White, activeTrackColor = a.accent,
                    inactiveTrackColor = Color.White.copy(alpha = if (a.dark) 0.25f else 0.6f)
                )
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                Text(fmt((fraction * duration).toLong()), fontSize = 12.sp, color = a.textDim, modifier = Modifier.weight(1f))
                Text("-" + fmt(duration - (fraction * duration).toLong()), fontSize = 12.sp, color = a.textDim)
            }
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.SkipPrevious, "Previous", { vm.conn.previous() }, size = 66.dp)
                GlassIconButton(
                    if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (st.isPlaying) "Pause" else "Play", { vm.conn.toggle() }, size = 92.dp, accentFill = true
                )
                GlassIconButton(Icons.Rounded.SkipNext, "Next", { vm.conn.next() }, size = 66.dp)
            }
            Spacer(Modifier.weight(0.4f))

            Row(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ModeButton(Icons.Rounded.Shuffle, "Shuffle", st.mode == PlayMode.SHUFFLE) { vm.conn.setMode(PlayMode.SHUFFLE) }
                ModeButton(Icons.Rounded.PlaylistPlay, "In order", st.mode == PlayMode.NORMAL) { vm.conn.setMode(PlayMode.NORMAL) }
                ModeButton(Icons.Rounded.RepeatOne, "Repeat one", st.mode == PlayMode.REPEAT_ONE) { vm.conn.setMode(PlayMode.REPEAT_ONE) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ModeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp)) {
        GlassIconButton(icon, label, onClick, size = 46.dp, selected = selected)
        Text(label, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun MiniPlayer(song: Song, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val env = LocalEnv.current
    val a = LocalAura.current
    val st by env.vm.playerState.collectAsState()
    var frac by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(st.isPlaying, song.id) {
        while (true) {
            val d = if (st.durationMs > 0) st.durationMs else song.durationMs
            frac = if (d > 0) (env.vm.conn.position().toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(if (st.isPlaying) 500 else 1000)
        }
    }
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp)).clickable(onClick = onOpen)) {
        Row(Modifier.padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SongArt(song, Modifier.size(50.dp), 128, CircleShape)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp, color = a.textDim)
            }
            GlassIconButton(Icons.Rounded.SkipPrevious, "Previous", { env.vm.conn.previous() }, size = 38.dp)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(if (st.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play or pause", { env.vm.conn.toggle() }, size = 46.dp, accentFill = true)
            Spacer(Modifier.width(8.dp))
            GlassIconButton(Icons.Rounded.SkipNext, "Next", { env.vm.conn.next() }, size = 38.dp)
        }
        Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(a.accent))
        }
    }
}
