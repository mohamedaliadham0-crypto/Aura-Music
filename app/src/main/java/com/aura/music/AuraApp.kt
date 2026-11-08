package com.aura.music

import android.app.Application
import androidx.room.Room
import com.aura.music.data.AuraDb
import com.aura.music.data.MusicRepository
import com.aura.music.data.SettingsStore
import com.aura.music.playback.PlayerConnection

class AuraApp : Application() {
    val db by lazy { Room.databaseBuilder(this, AuraDb::class.java, "aura.db").build() }
    val repo by lazy { MusicRepository(this) }
    val settings by lazy { SettingsStore(this) }
    val player by lazy { PlayerConnection(this) }
}
