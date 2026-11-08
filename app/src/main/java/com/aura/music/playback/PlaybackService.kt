package com.aura.music.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aura.music.AuraApp
import com.aura.music.MainActivity
import com.aura.music.widget.WidgetUpdater

/**
 * Owns the ExoPlayer, independent of any Activity/Compose UI.
 * Media3 provides the foreground notification, lock-screen controls and Bluetooth/headset buttons.
 */
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val prefs by lazy { getSharedPreferences("playback_state", Context.MODE_PRIVATE) }
    private var errorStreak = 0

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true) // pause when headphones unplug / BT disconnects
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player).setSessionActivity(open).build()

        restoreQueue()

        player.addListener(object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                if (p.isPlaying) errorStreak = 0
                persist()
                publishWidgetState()
            }

            override fun onPlayerError(error: PlaybackException) {
                // Missing / corrupted file: skip to the next track instead of dying.
                errorStreak++
                if (errorStreak <= 3 && player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away keeps playback alive; if nothing is playing we shut the service down. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        persist()
        WidgetUpdater.save(this, player.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty(),
            player.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty(),
            player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L, false, player.currentPosition,
            duration(), player.shuffleModeEnabled)
        WidgetUpdater.refreshAll(this)
        session?.release()
        session = null
        player.release()
        super.onDestroy()
    }

    private fun duration(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L

    private fun publishWidgetState() {
        val item = player.currentMediaItem
        WidgetUpdater.save(
            this,
            item?.mediaMetadata?.title?.toString().orEmpty(),
            item?.mediaMetadata?.artist?.toString().orEmpty(),
            item?.mediaId?.toLongOrNull() ?: -1L,
            player.playWhenReady && player.playbackState != Player.STATE_ENDED && player.playbackState != Player.STATE_IDLE,
            player.currentPosition, duration(), player.shuffleModeEnabled
        )
        WidgetUpdater.refreshAll(this)
    }

    private fun persist() {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).joinToString(",") { player.getMediaItemAt(it).mediaId }
        prefs.edit()
            .putString("queue", ids)
            .putLong("current", player.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L)
            .putLong("pos", player.currentPosition)
            .putBoolean("shuffle", player.shuffleModeEnabled)
            .putInt("repeat", player.repeatMode)
            .apply()
    }

    private fun restoreQueue() {
        try {
            val ids = prefs.getString("queue", "").orEmpty().split(",").mapNotNull { it.toLongOrNull() }
            if (ids.isEmpty()) return
            val byId = (application as AuraApp).repo.byIds(ids).associateBy { it.id }
            val ordered = ids.mapNotNull { byId[it] }
            if (ordered.isEmpty()) return
            val current = prefs.getLong("current", -1L)
            val index = ordered.indexOfFirst { it.id == current }.coerceAtLeast(0)
            player.shuffleModeEnabled = prefs.getBoolean("shuffle", false)
            player.repeatMode = prefs.getInt("repeat", Player.REPEAT_MODE_OFF)
            player.setMediaItems(ordered.map { it.toMediaItem() }, index, prefs.getLong("pos", 0L))
            player.prepare() // stays paused until the user presses play
        } catch (e: Exception) { /* a bad saved queue must never crash the service */ }
    }
}
