# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Bolap3 ("HiFi Player") is a local-music-player Android app (`com.nexbytes.bolap3`). Single Gradle module (`:app`), written in **Java** (not Kotlin), classic View/XML layouts (no Compose, no ViewBinding — `findViewById`). UI text is Spanish and lives in `res/values/strings.xml` (use string resources/plurals, never hardcode text).

Toolchain: AGP 9.3.3, Gradle 9.5.0 wrapper, compileSdk/targetSdk 37, **minSdk 34**, Java 11 source/target, Gradle daemon pinned to JDK 25 (`gradle/gradle-daemon-jvm.properties`, auto-provisioned). All dependency versions are in `gradle/libs.versions.toml`. AGP 9.4+ needs Gradle 9.6 (and a newer Android Studio), which is why AGP stays at 9.3.3. `kotlinx-serialization-core` is declared only to align versions for `room-testing` (see comment in `app/build.gradle`).

`app/google-services.json` must exist (google-services plugin). Firebase (Analytics/Auth/Firestore) is initialized in `Bolap3App` and `auth/FirebaseAuthManager` exists, but no feature uses Firebase yet.

## Commands

Run from the repo root (PowerShell: `.\gradlew.bat`; bash: `./gradlew`).

```
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease                 # R8 enabled (optimization { enable true })
.\gradlew.bat testDebugUnitTest               # JVM unit tests (app/src/test)
.\gradlew.bat testDebugUnitTest --tests "com.nexbytes.bolap3.tags.LrcParserTest"
.\gradlew.bat connectedDebugAndroidTest       # Room DAO + migration tests, needs a device/emulator
.\gradlew.bat lintDebug                       # fails the build on lint errors
```

JVM unit tests can only touch pure-Java classes: anything whose static initializer calls Android APIs (e.g. `Uri.parse`, `LruCache`) crashes under the stubbed android.jar. Keep testable logic in plain classes (`FolderPaths`, `FileNames`, `SongSort`, `LibraryGrouping`, `SearchFilter`, `LrcParser`, `TagReader`, `ReplayGain`, `Formatters`). CI: `.github/workflows/android.yml`.

## Architecture

### Playback (package `playback`)

`PlaybackService` (a Media3 `MediaLibraryService`) owns the only `ExoPlayer` and the media session. Everything controls playback through it: screens via `MediaController` (`PlayerConnection`: connect in `onStart`, disconnect in `onStop`; the controller is null until connected), the system notification/lock screen, headset/Bluetooth buttons, Android Auto (`BrowseTree`), the widget and quick-settings tile (`RemoteCommands`, one-shot controllers).

- The queue is ExoPlayer's playlist. Next/previous/shuffle/repeat/auto-advance are ExoPlayer's own logic — do not reimplement them. `PlaybackActions` has the shared queue operations. Tapping a song makes the **visible list** (album, playlist, search results…) the queue.
- Queue items use the song URI as `mediaId`; `MediaItemFactory.toSong` maps back through `MusicRepository.findByUri`.
- ExoPlayer's random shuffle doesn't start at the chosen song, so `PlaybackService.ensureShuffleStartsAtCurrent()` rebuilds the shuffle order when a new list starts or shuffle is turned on.
- State persistence: queue in Room (`queue_items`), index/position/modes in `PlaybackStateStore` prefs. Restored on service start and via `onPlaybackResumption`. `restored` guards against overwriting the saved queue with an empty one.
- In-process singletons attached by the service: `SleepTimer` (uses `setPauseAtEndOfMediaItems`), `audio/EqualizerManager` (Equalizer + BassBoost on the player's audio session), `PlaybackInfo` (snapshot read by widget/tile).
- Unplayable files are skipped by the service (`onPlayerError`, up to 5 in a row).
- `AudioIntegrations` (owned by the service): announces the audio session to third-party equalizer apps (`ACTION_OPEN/CLOSE_AUDIO_EFFECT_CONTROL_SESSION`, implicit + explicit per manifest receiver, hence the `<queries>` entry), and resumes playback when headphones reconnect after a "becoming noisy" pause, within Media3's 10-minute post-pause foreground window (`HeadsetResumePolicy`, opt-in setting). Starting playback from the background outside that window can be refused by Android.
- Android Auto browse ids: `root`, `songs`, `albums`, `artists`, `playlists`, `album|<key>`, `artist|<key>`, `playlist|<id>`, `favorites`; playable browse items are `parent##songUri` without a URI so `onSetMediaItems` expands them to the whole parent list. Voice search (`MEDIA_PLAY_FROM_SEARCH` on `LauncherActivity`, or `requestMetadata.searchQuery` in the session) uses `PlaybackActions.searchSongs`.

### Library data (packages `data`, `data/db`, `scan`, `library`)

- `MusicRepository`: Room is the source of truth; an immutable in-memory snapshot (swapped atomically) plus `LiveData<List<Song>>`. `Song` and `SongEntity` are separate classes (`SongEntity.fromSong/toSong`). `getUri()` is the identity everywhere.
- `PlaylistRepository`: playlists, favorites, play stats (Recientes / Más escuchadas). Work on `AppExecutors.db()` (single thread, ordered), callbacks on main; `changes()` LiveData ticks after every write. Playlist/favorite queries JOIN `songs`, so entries for missing files are hidden, not deleted.
- `AppDatabase` v3 with exported schemas in `app/schemas` (2.json was reconstructed by hand; the original app never exported it). **Changing an entity requires bumping the version and adding a `Migration`** (+ a test in `androidTest/.../MigrationTest`); only v1 falls back to destructive migration.
- `scan/MusicScanner`: MediaStore scan (folderPath = `RELATIVE_PATH`, e.g. `Music/Rock/`) or SAF folder scan via `DocumentsContract` queries (folderPath = folder document URI; tags read with `MediaMetadataRetriever`; `.nomedia` skips the subtree). Respects excluded folders and minimum duration. Run by `ScanViewModel` (survives rotation; cancel/empty never touches the existing library). `LibraryAutoRefresh` re-scans MediaStore libraries on MediaStore changes.
- Always compare folder paths with `FolderPaths.isWithin` (handles both path formats and the `Rock` vs `Rock2` prefix trap), never `startsWith`.
- `library/LibraryViewModel` drives the library screen (tab, opened group, search, sort). Groups are keyed by `LibraryGrouping.keyFor` (albums by MediaStore album id, else name+artist; folders by full path).

### UI

- Every screen extends `ui/BaseActivity` (edge-to-edge + system bar/IME insets padding on the root view, toolbar helper). Theme is a single DayNight theme: light/dark differ only via `values/colors.xml` vs `values-night/colors.xml` (semantic `app_*` colors). Accent colors are referenced as `?attr/colorPrimary` / `?attr/colorOnPrimary` so Material You (`DynamicColors`, opt-in in Settings) can replace them. In Java use `androidx.appcompat.R.attr.colorPrimary` (R classes are non-transitive). Theme mode is applied with `SettingsPrefs.applyTheme` (AppCompat + `UiModeManager`, so the splash matches).
- Theme attributes cannot be used as an item drawable inside a `<selector>` (crashes at inflate) — use `android:foreground` for ripples, as in `item_song.xml`.
- Shared song menu: `ui/SongActionsSheet` (+ `SongActionsSheet.Host`), deletion via `ui/SongDeleteHelper` (must be created in `onCreate`; MediaStore → `createDeleteRequest`, SAF → `DocumentsContract.deleteDocument`), "use as ringtone" via `ui/RingtoneSetter` (also created in `onCreate`; needs the special `WRITE_SETTINGS` permission; SAF songs are copied into MediaStore `Ringtones/Notifications/Alarms` because the system can't read the app's SAF grant).
- Cover art: `media/AlbumArtLoader` (async, downsampled, byte-bounded LRU, `bind()` for recycled views). Never load art on the main thread.
- `StatusStateActivity` renders every error/empty state from `EXTRA_STATE_TYPE`. Intent extra keys for scanning are `LibraryPrefs.EXTRA_SCAN_MODE` / `EXTRA_FOLDER_URI`.
- Lyrics: `tags/LyricsLoader` (sidecar `.lrc` for SAF libraries, then embedded ID3 USLT / FLAC Vorbis via `TagReader`), parsed by `LrcParser`.

Backup rules (`res/xml/*backup*`) exclude the database and library/playback prefs because they hold device-specific content URIs.
