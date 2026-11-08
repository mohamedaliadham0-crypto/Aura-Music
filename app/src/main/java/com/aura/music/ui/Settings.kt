package com.aura.music.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aura.music.widget.LargeWidget
import com.aura.music.widget.MiniWidget
import com.aura.music.widget.StandardWidget

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp).glass(RoundedCornerShape(28.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        content()
    }
}

@Composable
private fun Pill(label: String, selected: Boolean, onClick: () -> Unit) {
    val a = LocalAura.current
    Box(
        Modifier.clip(CircleShape)
            .background(if (selected) a.accent else Color.Transparent)
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.6f) else a.rim, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { Text(label, color = if (selected) Color.White else a.text, fontWeight = FontWeight.Medium, fontSize = 14.sp) }
}

@Composable
private fun ActionRow(label: String, detail: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 6.dp)) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        if (detail != null) Text(detail, fontSize = 12.sp, color = LocalAura.current.textDim)
    }
}

private fun pin(ctx: Context, cls: Class<*>) {
    val m = AppWidgetManager.getInstance(ctx)
    if (m.isRequestPinAppWidgetSupported) m.requestPinAppWidget(ComponentName(ctx, cls), null, null)
    else Toast.makeText(ctx, "Your launcher can't pin widgets from apps. Long-press the home screen > Widgets > AURA MUSIC.", Toast.LENGTH_LONG).show()
}

@Composable
fun SettingsScreen() {
    val env = LocalEnv.current
    val a = LocalAura.current
    val vm = env.vm
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val songs by vm.songs.collectAsState()
    val hidden by vm.hiddenCount.collectAsState()
    val perm = if (Build.VERSION.SDK_INT >= 33) android.Manifest.permission.READ_MEDIA_AUDIO else android.Manifest.permission.READ_EXTERNAL_STORAGE
    val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(bottom = 150.dp)) {
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Icons.Rounded.ArrowBack, "Back", env.back)
                Text("Settings", Modifier.padding(start = 14.dp), fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Card("Appearance") {
                Text("Theme", fontSize = 13.sp, color = a.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Light", "Dark").forEachIndexed { i, l -> Pill(l, s.theme == i) { vm.setTheme(i) } }
                }
                Text("Accent color", fontSize = 13.sp, color = a.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Accents.forEachIndexed { i, (name, c) ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(c)
                                .border(if (s.accent == i) 3.dp else 1.dp, if (s.accent == i) a.text else a.rim, CircleShape)
                                .clickable { vm.setAccent(i) }
                        )
                    }
                }
                Text("Background gradient", fontSize = 13.sp, color = a.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Gradients.forEachIndexed { i, g ->
                        Box(
                            Modifier.size(width = 62.dp, height = 44.dp).clip(RoundedCornerShape(14.dp))
                                .background(Brush.verticalGradient(if (a.dark) g.dark else g.light))
                                .border(if (s.gradient == i) 3.dp else 1.dp, if (s.gradient == i) a.text else a.rim, RoundedCornerShape(14.dp))
                                .clickable { vm.setGradient(i) }
                        )
                    }
                }
            }
        }
        item {
            Card("Library") {
                Text("${songs.size} songs found" + if (hidden > 0) " · $hidden removed from library" else "", color = a.textDim, fontSize = 14.sp)
                ActionRow("Refresh audio library", "Rescan MediaStore now. New downloads are also detected automatically.") { vm.refresh(false) }
                if (hidden > 0) ActionRow("Restore removed songs", "Bring back songs you removed from the library") { vm.unhideAll() }
                ActionRow("Clear recently played") { vm.clearRecents() }
                ActionRow(
                    "Audio permission: " + if (granted) "granted" else "not granted",
                    "AURA MUSIC only reads audio files through Android's media library. It never gets all-files access and never deletes your files."
                ) { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }
            }
        }
        item {
            Card("Playback") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep screen on in player", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text("Prevents the display from sleeping on the Now Playing screen", fontSize = 12.sp, color = a.textDim)
                    }
                    Switch(s.keepAwake, { vm.setKeepAwake(it) }, colors = SwitchDefaults.colors(checkedTrackColor = a.accent, checkedThumbColor = Color.White))
                }
                Text("Shuffle, in-order and repeat-one are chosen on the Now Playing screen and remembered between sessions.", fontSize = 12.sp, color = a.textDim)
            }
        }
        item {
            Card("Home-screen widgets") {
                Text("Add a widget directly (if your launcher supports it), or long-press the home screen > Widgets > AURA MUSIC.", fontSize = 13.sp, color = a.textDim)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Mini", false) { pin(ctx, MiniWidget::class.java) }
                    Pill("Standard", false) { pin(ctx, StandardWidget::class.java) }
                    Pill("Large", false) { pin(ctx, LargeWidget::class.java) }
                }
            }
        }
        item {
            Card("About AURA MUSIC") {
                Text("Your music. Your atmosphere.", fontWeight = FontWeight.SemiBold)
                Text("Version 1.0 · Fully offline · No ads · No account · Nothing leaves your phone.", fontSize = 13.sp, color = a.textDim)
            }
        }
    }
}
