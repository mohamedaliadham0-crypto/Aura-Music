package com.aura.music.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aura.music.data.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val connected: Boolean = false,
    val mediaId: Long? = null,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeat: Int = Player.REPEAT_MODE_OFF
) {
    val mode: Int get() = when {
        shuffle -> PlayMode.SHUFFLE
        repeat == Player.REPEAT_MODE_ONE -> PlayMode.REPEAT_ONE
        else -> PlayMode.NORMAL
    }
}

/** UI-side handle to the PlaybackService (a Media3 MediaController). The UI never owns a player. */
class PlayerConnection(private val ctx: Context) {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { sync() }
    }

    init {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                sync()
            } catch (e: Exception) { /* service unavailable; UI stays usable */ }
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun sync() {
        val c = controller ?: return
        _state.value = PlayerState(
            connected = true,
            mediaId = c.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE,
            durationMs = c.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
            shuffle = c.shuffleModeEnabled,
            repeat = c.repeatMode
        )
    }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying || (c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE)) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekTo(0)
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            c.play()
        }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() {
        val c = controller ?: return
        // Standard behaviour: restart the song if we are past 3 s, otherwise go to the previous one.
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun position(): Long = controller?.currentPosition ?: 0L

    /**
     * SHUFFLE     -> shuffle on, repeat-all (random order, no repeats until the queue is exhausted, then reshuffle-loop)
     * NORMAL      -> shuffle off, repeat off (song 1 -> 2 -> 3 ...)
     * REPEAT_ONE  -> shuffle off, repeat-one (current song loops)
     */
    fun setMode(mode: Int) {
        val c = controller ?: return
        c.shuffleModeEnabled = mode == PlayMode.SHUFFLE
        c.repeatMode = when (mode) {
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }
}
