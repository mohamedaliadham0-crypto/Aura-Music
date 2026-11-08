package com.aura.music.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val audioPermission =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
fun AuraRoot(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    AuraTheme(settings) {
        val a = LocalAura.current
        val view = LocalView.current
        val activity = LocalContext.current as ComponentActivity
        SideEffect {
            val c = WindowCompat.getInsetsController(activity.window, view)
            c.isAppearanceLightStatusBars = !a.dark
            c.isAppearanceLightNavigationBars = !a.dark
        }
        AuraBackground { Gate(vm, activity) }
    }
}

@Composable
private fun Gate(vm: MainViewModel, activity: ComponentActivity) {
    fun granted() = ContextCompat.checkSelfPermission(activity, audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    var hasPerm by remember { mutableStateOf(granted()) }
    var asked by remember { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        hasPerm = ok; asked = true
        if (ok) {
            vm.refresh(false)
            if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) { hasPerm = granted(); if (hasPerm) vm.refresh(true) }
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val songs by vm.songs.collectAsState()
    val loading by vm.loading.collectAsState()
    val stage = when { !hasPerm -> 0; loading && songs.isEmpty() -> 1; else -> 2 }
    val permanentlyDenied = asked && !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, audioPermission)

    AnimatedContent(stage, transitionSpec = { fadeIn(tween(350)) togetherWith fadeOut(tween(250)) }, label = "gate") { s ->
        when (s) {
            0 -> PermissionScreen {
                if (permanentlyDenied) activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", activity.packageName, null)))
                else launcher.launch(audioPermission)
            }
            1 -> ScanningScreen()
            else -> MainShell(vm)
        }
    }
}

@Composable
private fun PermissionScreen(onGrant: () -> Unit) {
    val a = LocalAura.current
    Box(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(40.dp)).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(Modifier.size(84.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFED9CD0), Color(0xFF4169E1)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(44.dp))
            }
            Text("AURA MUSIC", fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
            Text("Your music. Your atmosphere.", color = a.textDim)
            Text(
                "To find the songs on your phone, AURA MUSIC needs permission to read your audio files. " +
                    "Everything stays on your device — no account, no uploads, no ads.",
                textAlign = TextAlign.Center, fontSize = 14.sp, lineHeight = 21.sp, color = a.textDim
            )
            AccentButton("Allow music access", onGrant)
        }
    }
}

@Composable
private fun ScanningScreen() {
    val a = LocalAura.current
    val t = rememberInfiniteTransition(label = "scan")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "ring")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp)) {
            for (i in 0..2) {
                val f = (p + i / 3f) % 1f
                drawCircle(Color.White.copy(alpha = (1f - f) * 0.7f), radius = size.minDimension / 2 * f, center = Offset(size.width / 2, size.height / 2), style = Stroke(3.dp.toPx()))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(80.dp).clip(CircleShape).background(a.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
            Text("Scanning your music…", Modifier.padding(top = 140.dp), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MainShell(vm: MainViewModel) {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    var nowPlaying by remember { mutableStateOf(false) }
    val favs by vm.favorites.collectAsState()
    val playlists by vm.playlists.collectAsState()
    val st by vm.playerState.collectAsState()
    val current by vm.currentSong.collectAsState()

    val env = Env(
        vm, favs, playlists, st.mediaId, st.isPlaying,
        openPlayer = { nowPlaying = true },
        nav = { s -> if (s != stack.last()) stack.add(s) },
        back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    )
    BackHandler(nowPlaying) { nowPlaying = false }
    BackHandler(!nowPlaying && stack.size > 1) { env.back() }

    CompositionLocalProvider(LocalEnv provides env) {
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                stack.last(),
                transitionSpec = { (fadeIn(tween(280)) + slideInHorizontally(tween(280)) { it / 12 }) togetherWith fadeOut(tween(160)) },
                label = "screens"
            ) { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen()
                    Screen.Search -> SearchScreen()
                    Screen.Settings -> SettingsScreen()
                    Screen.Favorites -> FavoritesScreen()
                    is Screen.Album -> AlbumScreen(screen.albumId)
                    is Screen.Artist -> ArtistScreen(screen.name)
                    is Screen.Playlist -> PlaylistScreen(screen.id)
                }
            }
            val song = current
            if (song != null) {
                AnimatedVisibility(
                    !nowPlaying, Modifier.align(Alignment.BottomCenter),
                    enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()
                ) {
                    MiniPlayer(song, { nowPlaying = true }, Modifier.navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp))
                }
                AnimatedVisibility(
                    nowPlaying, enter = slideInVertically(tween(380)) { it } + fadeIn(tween(380)),
                    exit = slideOutVertically(tween(300)) { it } + fadeOut(tween(300))
                ) {
                    NowPlayingScreen(song) { nowPlaying = false }
                }
            }
        }
    }
}
