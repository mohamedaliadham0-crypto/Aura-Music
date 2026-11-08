# AURA MUSIC — native Android MP3 player (Kotlin · Compose · Media3)

## Build & install (Android 11+ phone)
1. Install **Android Studio Koala (2024.1) or newer** (bundles JDK 17).
2. `File > Open` this folder. Let Gradle sync (needs internet once, to download dependencies).
   If Studio asks about the Gradle wrapper, accept it (project pins Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24).
3. Enable *Developer options > USB debugging* on the phone, plug it in, press **Run ▶**.
   Or build an APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)` -> `app/build/outputs/apk/debug/app-debug.apk`,
   copy it to the phone and open it (allow "install unknown apps").
4. First launch: allow music access (and notifications on Android 13+).

## Structure
- `data/`      MediaStore scan + change observer, Room (favorites, playlists, hidden, recents), DataStore settings
- `playback/`  `PlaybackService` (MediaSessionService + ExoPlayer, owns playback), `PlayerConnection` (UI-side MediaController)
- `widget/`    3 AppWidgetProviders + `WidgetUpdater` (RemoteViews) + `WidgetActionReceiver` (buttons -> service)
- `ui/`        Compose screens, glass components, theme, view model

## Known limits (honest list)
- Real-time blur isn't used (Android 11 can't, and it costs battery): glass = translucent gradients + rim light + animated colour blobs.
- Widgets are RemoteViews. The progress bar updates on song/play/pause changes, not every second.
- Widget buttons when the app process is fully dead rely on Android letting the tap start the playback service;
  Android 12+ may refuse background service starts on some devices/launchers.
- A force-stopped app or OS-killed process cannot keep playing (Android rule). Swiping from recents does not stop playback.
- Song ids come from MediaStore; if Android rebuilds its media database, favorites/playlists could lose matches.
