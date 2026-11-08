package com.aura.music.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Size
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.music.MainActivity
import com.aura.music.R
import com.aura.music.data.Song
import com.aura.music.playback.PlaybackService
import java.util.concurrent.Executors

object WidgetActions {
    const val PLAY_PAUSE = "com.aura.music.action.PLAY_PAUSE"
    const val NEXT = "com.aura.music.action.NEXT"
    const val PREV = "com.aura.music.action.PREV"
}

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetUpdater.refreshAll(context)
    }
}
class MiniWidget : BaseWidget()
class StandardWidget : BaseWidget()
class LargeWidget : BaseWidget()

object WidgetUpdater {
    private val exec = Executors.newSingleThreadExecutor()
    private var cachedId = -2L
    private var cachedArt: Bitmap? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("widget_state", Context.MODE_PRIVATE)

    fun save(ctx: Context, title: String, artist: String, songId: Long, playing: Boolean, pos: Long, dur: Long, shuffle: Boolean) {
        prefs(ctx).edit().putString("title", title).putString("artist", artist).putLong("id", songId)
            .putBoolean("playing", playing).putLong("pos", pos).putLong("dur", dur).putBoolean("shuffle", shuffle).apply()
    }

    /** Re-renders every placed widget from the last saved snapshot (off the main thread). */
    fun refreshAll(ctx: Context) {
        val app = ctx.applicationContext
        exec.execute { try { render(app) } catch (_: Exception) { } }
    }

    private fun artFor(ctx: Context, id: Long): Bitmap? {
        if (id < 0) return null
        if (id == cachedId) return cachedArt
        cachedId = id
        cachedArt = try {
            val uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
            ctx.contentResolver.loadThumbnail(uri, Size(256, 256), null)
        } catch (e: Exception) { null }
        return cachedArt
    }

    private fun render(ctx: Context) {
        val p = prefs(ctx)
        val title = p.getString("title", "").orEmpty()
        val artist = p.getString("artist", "").orEmpty()
        val playing = p.getBoolean("playing", false)
        val pos = p.getLong("pos", 0); val dur = p.getLong("dur", 0)
        val shuffle = p.getBoolean("shuffle", false)
        val art = artFor(ctx, p.getLong("id", -1))
        val mgr = AppWidgetManager.getInstance(ctx)

        val kinds = listOf(
            Triple(MiniWidget::class.java, R.layout.widget_mini, 0),
            Triple(StandardWidget::class.java, R.layout.widget_standard, 1),
            Triple(LargeWidget::class.java, R.layout.widget_large, 2)
        )
        for ((cls, layout, kind) in kinds) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isEmpty()) continue
            val rv = RemoteViews(ctx.packageName, layout)
            rv.setTextViewText(R.id.w_title, title.ifBlank { "AURA MUSIC" })
            rv.setTextViewText(R.id.w_artist, if (title.isBlank()) "Tap to open your music" else artist)
            if (art != null) rv.setImageViewBitmap(R.id.w_art, art) else rv.setImageViewResource(R.id.w_art, R.drawable.ic_art_fallback)
            rv.setImageViewResource(R.id.w_play, if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            rv.setOnClickPendingIntent(R.id.w_play, action(ctx, WidgetActions.PLAY_PAUSE))
            rv.setOnClickPendingIntent(R.id.w_next, action(ctx, WidgetActions.NEXT))
            if (kind >= 1) {
                rv.setOnClickPendingIntent(R.id.w_prev, action(ctx, WidgetActions.PREV))
                rv.setProgressBar(R.id.w_progress, 1000, if (dur > 0) ((pos * 1000) / dur).toInt().coerceIn(0, 1000) else 0, false)
            }
            if (kind == 2) rv.setInt(R.id.w_shuffle, "setImageAlpha", if (shuffle) 255 else 90)
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)
            rv.setOnClickPendingIntent(R.id.w_art, open)
            mgr.updateAppWidget(ids, rv)
        }
    }

    private fun action(ctx: Context, a: String): PendingIntent = PendingIntent.getBroadcast(
        ctx, a.hashCode(),
        Intent(ctx, WidgetActionReceiver::class.java).setAction(a),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

/** Widget buttons land here and are forwarded to the MediaSessionService as real playback commands. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = context.applicationContext
        val pending = goAsync()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                when (action) {
                    WidgetActions.PLAY_PAUSE -> if (c.isPlaying) c.pause() else { c.prepare(); c.play() }
                    WidgetActions.NEXT -> c.seekToNextMediaItem()
                    WidgetActions.PREV -> if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
                }
                // give the command time to be delivered before disconnecting
                Handler(Looper.getMainLooper()).postDelayed({ c.release(); pending.finish() }, 1500)
            } catch (e: Exception) {
                pending.finish()
            }
        }, ContextCompat.getMainExecutor(app))
    }
}
