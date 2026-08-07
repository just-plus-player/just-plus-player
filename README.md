# Just+ Player

[![Latest release](https://img.shields.io/github/v/release/just-plus-player/just-plus-player?logo=github&logoColor=white&cacheSeconds=3600)](https://github.com/just-plus-player/just-plus-player/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/just-plus-player/just-plus-player/total?logo=github&logoColor=white&label=downloads&cacheSeconds=3600)](https://github.com/just-plus-player/just-plus-player/releases)
[![Media3](https://img.shields.io/badge/Media3-1.11.0--beta01-007ec6?cacheSeconds=3600)](https://github.com/androidx/media/releases/tag/1.11.0-beta01)

Video player for Android phones, tablets and Android TV, built on [Media3](https://github.com/androidx/media) (formerly [ExoPlayer](https://github.com/google/ExoPlayer)). Android 6.0 or later, one APK for all form factors.

It is a fork of [Just (Video) Player](https://github.com/moneytoo/Player) by Marcel Dopita and keeps what makes it good: no ads, no tracking, barely any permissions, ExoPlayer's `ffmpeg` extension for AC3, E-AC-3, DTS, DTS-HD and TrueHD, and audio that stays in sync over Bluetooth. On top of that it adds a reworked player interface and the features below.

## What Just+ adds

**Player and controls**

 * Reworked controls: poster, title and a metadata line (container · resolution · codec · fps · bitrate · audio track) in the header, next to the clock and the time playback will end
 * Tap anywhere on the time bar to seek there
 * Hold the picture for 2× speed, let go to return to normal
 * Ten scaling modes — Fit, Crop, Fill, 16:9, 4:3, 16:10, 2:1, 2.35:1, 2.39:1, 5:4. A tap cycles the first five, a long press opens the full picker
 * Volume up to 200 % with a loudness boost, and volume and brightness gestures that report a percentage instead of an unlabelled bar
 * Lock the screen from the bottom bar, unlock with a swipe
 * Transfer rate under the loading ring, so a stalling stream is obvious
 * Optional always-on clock over the video, and a mode where the volume keys and gestures change only the player and leave the device volume alone

**Skip segments**

 * Skip intros, recaps, ad breaks and end credits — segments are drawn right on the time bar
 * Segments come from the launching app, or are looked up online in SkipDB, SkipMe.db, IntroHater, IntroDB, TheIntroDB and Aniskip
 * The sources vote: a segment several databases agree on is used, and its timing is taken from the most reliable one rather than averaged
 * Separately for the intro and the end credits: a Skip button for five seconds, a Skip button for the whole segment, or skip automatically
 * Every skip can be undone, and an automatic skip can be cancelled before it happens
 * A session offset slider for when a database is a few seconds off

**Sleep timer**

 * Off, 15/30/45/60/90 minutes, after the current file, or a custom time on a VLC-style keypad
 * Fades the volume out over the last 30 seconds instead of cutting off mid-sentence

**Tracks and quality**

 * Side panels instead of pop-up menus for audio, subtitles, quality, speed and the playlist; a button stays hidden until the media actually has something to put in it
 * Manual video quality: optimal, highest, a specific resolution, or one of the sources the launching app supplied
 * Track names built from the container's own metadata
 * A list of preferred audio languages instead of a single one — the player takes the first language a file actually carries, so a release without yours falls back to the next language you named rather than to whatever the file happens to list first
 * A language can be promoted straight from the audio panel, without a trip to Settings
 * Sturdier audio: passthrough (AC3/DTS/TrueHD) is rebuilt after a seek or a resume, a format the device mishandles is learned and avoided, and playback survives the audio output disappearing

**Android TV**

 * One focus row of controls, sized for a remote
 * Left and Right accelerate while held, and a burst of presses commits as a single seek
 * Down opens the controls on the time bar, Up dismisses them, and a stray Back no longer drops out of the player
 * An error report can be uploaded and read off the screen as a QR code — no keyboard needed

**When something breaks**

 * A full-screen error page with plain-language messages instead of ExoPlayer codes, and Copy / Share / Upload log
 * Watchdogs for a load that never starts, a stall in the middle of a film, and a live stream that keeps dropping
 * Fallbacks for Dolby Vision profile 7, for tunneled playback that freezes the picture, and for HLS playlists served without an extension

**Launcher integration**

 * Intent extras for position, title, poster, subtitles, HTTP headers, a playlist of episodes with per-episode segments and resume positions, quality variants, and IMDb/TMDB ids — as used by [LAMPA](https://github.com/lampa-app/LAMPA)/Lampac
 * Sideloaded builds check GitHub releases for updates, show the release notes and install them in place

## Screenshots

<img src="fastlane/metadata/android/en-US/images/readmeScreenshots/player.jpg" width="880">

**Playlist** — a queue of episodes with poster thumbnails and per-item resume positions. **Skip segments** — one pill for skipping, cancelling or undoing.

<img src="fastlane/metadata/android/en-US/images/readmeScreenshots/playlist.jpg" width="430"> <img src="fastlane/metadata/android/en-US/images/readmeScreenshots/skip.jpg" width="430">

**Video quality** — pick a resolution or one of the launcher's sources. **Sleep timer** — presets, or type a time.

<img src="fastlane/metadata/android/en-US/images/readmeScreenshots/quality.jpg" width="430"> <img src="fastlane/metadata/android/en-US/images/readmeScreenshots/sleep_timer.jpg" width="430">

**Lock** — one tap locks the screen against pockets and curious hands; a swipe unlocks it. **Audio languages** — order them once; the player takes the first one a file actually has.

<img src="fastlane/metadata/android/en-US/images/readmeScreenshots/unlock.jpg" width="430"> <img src="fastlane/metadata/android/en-US/images/readmeScreenshots/audio_languages.jpg" width="430">

## Supported formats

 * **Audio**: Vorbis, Opus, FLAC, ALAC, PCM/WAVE (μ-law, A-law), MP1, MP2, MP3, AMR (NB, WB), AAC (LC, ELD, HE; xHE on Android 9+), AC-3, E-AC-3, DTS, DTS-HD, TrueHD, IAMF, MPEG-H
 * **Video**: H.263, H.264 AVC (Baseline Profile; Main Profile on Android 6+), H.265 HEVC, MPEG-4 SP, VP8, VP9, AV1
 * **Containers**: MP4, MOV, WebM, MKV, AVI, Ogg, MPEG-TS, MPEG-PS, FLV
 * **Streaming**: DASH, HLS, SmoothStreaming, RTSP
 * **Subtitles**: SRT, SSA/ASS ([limited styling](https://github.com/google/ExoPlayer/issues/8435)), TTML, VTT, DVB

HDR (HDR10+ and Dolby Vision) playback on compatible hardware. AC-4 audio works on devices that ship such a system decoder (e.g. Samsung Galaxy A, S and Z series on Android 11 or later).

## Inherited from Just (Video) Player

 * Playback speed control
 * Horizontal swipe and double tap to seek
 * Vertical swipe to change brightness (left) / volume (right)
 * Pinch to zoom (Android 7+)
 * Picture-in-picture on Android 8+ (resizable on Android 11+), automatically when you leave the app
 * Auto frame rate matching on Android TV and TV boxes
 * Post-playback actions (delete the file, skip to the next one)
 * Resume where you left off, per file
 * App shortcut straight to the file chooser (Android 7.1+)
 * Third-party equalizer / audio processing support (e.g. [Wavelet](https://github.com/Pittvandewitt/Wavelet))
 * Media Session and Audio Focus support, pause when headphones are disconnected
 * No ads, no tracking, no excessive permissions

## Install

Grab the APK from [Releases](https://github.com/just-plus-player/just-plus-player/releases/latest) and install it. Sideloaded builds check for updates themselves and can install them without going through a store.

## Build

JDK 17 and the Gradle wrapper:

```bash
./gradlew assembleLatestUniversalDebug   # debug APK
./gradlew build                          # what CI runs
```

Two flavour dimensions: `targetSdk` (`latest` = targetSdk 36, `legacy` = targetSdk 29 for legacy storage access) × `distribution` (`universal` with the in-app updater, `amazon`, `accrescent`). `latestUniversal` is the one that gets released.

`app/libs/lib-*.aar` are **prebuilt binaries** — a locally built ExoPlayer core plus the ffmpeg, AV1, IAMF and MPEG-H decoder extensions. They are what makes AC3/DTS/TrueHD work, and their version has to stay in step with `media3_version` in `app/build.gradle`. See [`app/libs/README.md`](app/libs/README.md).

## Integration

### Launching the player from another app

An `ACTION_VIEW` intent addressed to `com.justplus.player`, with the video as the data URI:

```java
Intent intent = new Intent(Intent.ACTION_VIEW);
intent.setPackage("com.justplus.player");                 // or the explicit component
intent.setDataAndType(Uri.parse(url), "video/*");         // content:// also needs FLAG_GRANT_READ_URI_PERMISSION
intent.putExtra("title", "Machines");
startActivityForResult(intent, REQUEST_PLAY);             // startActivity if you do not want a result
```

Everything else is optional extras:

| Extra | Type | Meaning |
|---|---|---|
| `title` | String / CharSequence | Title in the header. HTML entities are unescaped |
| `thumbnail` | String | Poster shown next to the title |
| `position` | int, ms | Where to start |
| `return_result` | boolean | Report position and duration back on exit (see below) |
| `headers` | String[] | Flat `name, value, name, value…`, applied to every HTTP request |
| `subs` | Parcelable[] of Uri | External subtitle files |
| `subs.name` | String[] | Their labels, aligned by index with `subs` |
| `subs.enable` | Parcelable[] of Uri | Its first element is the track to pre-select |
| `segments` | String | Skip/ad segments as JSON — format below |
| `season`, `episode` | int | Episode this file belongs to |
| `imdb_id` | String | IMDb id, used to look segments up online |
| `id` | String or int | TMDB id, same purpose |
| `quality_levels` | String[] | Labels of the quality variants, e.g. `1080p` |
| `quality_urls` | String[] or Parcelable[] of Uri | Their URLs, aligned by index with `quality_levels` |

A queue is passed the same way, with everything aligned by index against `video_list`:

| Extra | Type | Meaning |
|---|---|---|
| `video_list` | Parcelable[] of Uri, or String[] | The queue. The entry equal to the intent's data URI becomes the starting item |
| `video_list.name` | String[] | Titles; `video_list.filename` is the fallback, then the last path segment |
| `video_list.thumbnail` | String[] | Posters for the playlist panel |
| `video_list.segments` | String[] | One segments JSON per item |
| `video_list.season`, `.episode`, `.imdb_id`, `.id` | String[] | Episode metadata per item |
| `video_list.subtitles` | Parcelable[] or ArrayList of Bundle | External subtitles per item. Each Bundle holds `uris` (Parcelable[] of Uri) and `names` (String[]), aligned with each other |
| `video_list.quality_levels.<i>` | String[] | Quality labels for item `<i>` (0-based index in `video_list`) |
| `video_list.quality_urls.<i>` | String[] | Matching URLs for item `<i>` |

`video_list` and every `video_list.*` string array, as well as `quality_levels` and `quality_urls`, are read leniently — `String[]`, `ArrayList<String>` and `CharSequence[]` all work, and `quality_urls` also takes a `Parcelable[]` of `Uri`. `subs`, `subs.name` and `headers` are not: they have to be exactly the types in the table above, or they are silently ignored.

**Segments JSON** — `start` and `end` are seconds, `duration_ms` is the duration those timings were measured against, so the player can rescale them to the real file. `skip` is intro/recap/credits, `ad` is advertising:

```json
{ "duration_ms": 2696000,
  "skip": [{ "start": 62, "end": 152 }],
  "ad":   [{ "start": 0,  "end": 12  }] }
```

**Session mode.** The presence of `position`, `return_result`, `subs`, `subs.enable`, `video_list` or `quality_levels` puts the player in API mode: it keeps positions for that session only and writes nothing to its own resume store, so a launcher stays the owner of the watch state. `title` alone does not — the title is used and the state is still persisted.

**Result** (only with `return_result`): `RESULT_OK` and an intent with action `com.mxtech.intent.result.VIEW` (MX Player's contract), whose data URI is the item that was playing — not necessarily the one that was launched. Extras: `end_by` is `playback_completion` or `user`, and on an early exit `position` and `duration` (both int, ms).

The player is `singleTask`: a further `ACTION_VIEW` sent to the running instance replaces the extras rather than being ignored, which is how a source or an episode is switched without a restart.

### Feeding it from a LAMPA plugin

A plugin does not build the intent — LAMPA does, from the JSON handed to `Lampa.Player.play()`. Use these keys and it maps onto the extras above by itself:

```js
Lampa.Player.play({
    url: 'https://host/s01e03-1080.mp4',        // required, and must be byte-identical to the playlist entry
    title: 'Machines',
    thumbnail: 'https://host/still.jpg',
    quality: { '1080p': 'https://host/s01e03-1080.mp4', '720p': 'https://host/s01e03-720.mp4' },
    subtitles: [{ url: 'https://host/en.srt', label: 'English', language: 'en' }],
    segments: { duration_ms: 2696000, skip: [{ start: 62, end: 152 }], ad: [] },
    season: 1, episode: 3,
    imdb_id: 'tt14688458',
    headers: { 'User-Agent': '…', Referer: '…' },
    playlist: [ /* the same objects, one per episode */ ]
})
```

| Plugin JSON | Becomes |
|---|---|
| `url` | The data URI, and the entry in `video_list` |
| `title` | `title`, `video_list.name` |
| `thumbnail` | `thumbnail`, `video_list.thumbnail` |
| `quality` (`{label: url}`) | `quality_levels` / `quality_urls`, or `video_list.quality_*.<i>` per episode |
| `subtitles` (`[{url, label, language}]`) | `subs` / `subs.name` for a single video, `video_list.subtitles` in a queue |
| `segments` | `segments` / `video_list.segments`, serialised verbatim |
| `season`, `episode` | `season`, `episode` and the per-item arrays |
| `imdb_id`, or `card.imdb_id` from the open card | `imdb_id` |
| the card's `id` | `id` (TMDB) |
| `headers` (`{name: value}`) | `headers`, flattened to pairs |

What actually decides whether it matches:

 * **`url` must be identical** to the `playlist` entry it stands for. LAMPA finds the starting index by exact string comparison, and the player then matches its data URI against `video_list` the same way. One extra token or a trailing slash and the queue opens on episode 1.
 * **`playlist` is only read when auto-next is on** in LAMPA; otherwise the payload itself is the only item. Put the episode's own metadata on the top-level object as well, not just inside `playlist`.
 * **Name quality variants by resolution.** Both sides sort them by the number in the label, so `1080p`/`720p` order correctly while `HD`/`SD` do not.
 * **`segments` is passed through untouched**, so it has to be the shape above — seconds, plus `duration_ms` for rescaling.
 * **`imdb_id` and the card's `id` are what the online lookup keys on.** Without them only the segments you supply yourself are used; there is no title search.
 * **Nothing is switched on for the viewer.** Subtitles arrive as selectable tracks and LAMPA sends no default, so one has to be picked from the subtitle panel. `language` is not forwarded either — put whatever should be shown into `label`.
 * `subtitles[]` entries need both `url` and `label`; an entry without a label takes the whole list down with it.
 * **The subtitle format is taken from the URL path**, falling back to SubRip. A WebVTT or ASS file served from an extension-less endpoint therefore arrives labelled as SRT and will not parse — keep the real extension in the URL.

## FAQ

### Where are the settings?

Long press the ⚙️ button in the bottom bar, or open **More → Settings**. The App info screen works too.

### How do I open a subtitle file (e.g. .srt)?

Long press the 📁 file open button in the bottom bar. The first time, you will be offered to pick the root video folder so that external subtitles can be loaded automatically afterwards.

Subtitles sitting next to a video on an HTTP server are found too, as long as they share the video's name (`video.mkv` → `video.srt`).

📺 Because of [limitations on Android TV](https://github.com/moneytoo/Player/issues/248#issuecomment-1019565204), the player can also take a subtitle file from an external file manager: open the video, go back, then open the subtitle file — it will be applied to the last video.

### How do I change subtitle font, size or color?

Open the system [Caption preferences](https://support.google.com/accessibility/android/answer/6006554) (usually under _Accessibility_ in _Settings_) — they control the subtitle style completely. Long pressing the subtitle button takes you straight there.

### How do I open a streaming link?

**More → Open link**, and paste an `http://` or `rtsp://` address; a link on the clipboard is offered automatically. The player is also registered for compatible links, so tapping one in another app should offer it as an option, and sharing a selected URL works as well.

### Are there any media formats it CANNOT play?

ExoPlayer does not handle some older formats such as WMV or [Theora](https://github.com/google/ExoPlayer/issues/4970), and most devices cannot decode [10-bit AVC](https://github.com/moneytoo/Player/issues/87#issuecomment-816228143). Audio-only playback is not a goal — this is a video player.

### I prefer a media library instead of a file chooser...

The system file chooser already offers two modes: **Videos**, listing only folders that contain videos, and **File browser**, for the whole file system. Settings → File access can switch to MediaStore or legacy file access instead.

Some people use the media library of [Nova Video Player](https://github.com/nova-video-player/aos-AVP) with "*Allow using another video player*" enabled, which also gives convenient access to network storage.

### How do I get to videos on network storage (SMB, WebDAV, SFTP)?

1. The system file chooser reaches any remote storage through a _Document Provider_ — [CIFS Documents Provider](https://github.com/wa2c/cifs-documents-provider) for Samba, [WebDAV Provider](https://github.com/alexbakker/webdav-provider)/[DAVx⁵](https://github.com/bitfireAT/davx5-ose) for WebDAV, [FileManagerUtils](https://github.com/rikyiso01/FileManagerUtils) for SFTP, or [rcx](https://github.com/x0b/rcx). Document providers are not available on Android TV.
2. Or open the video straight from a file explorer — _Solid Explorer_ works well, especially for automatic subtitles.

### How do I get rid of the black bars?

Pinch to zoom, or tap the resize button to cycle Fit → Crop → Fill → 16:9 → 4:3. A long press opens the full list of scaling modes. **Android TV**: long press resize to enter zoom mode, then zoom precisely with Up and Down.

### Bluetooth audio is out of sync

Pause and resume once.

### Why does it ask for "Modify system settings"?

The system file chooser always uses the current system orientation, even when the player sets its own. Granting `WRITE_SETTINGS` from the App info screen or via adb (`adb shell pm grant com.justplus.player android.permission.WRITE_SETTINGS`) lets the app temporarily enable Auto-rotate to partially work around [this imperfection](https://issuetracker.google.com/issues/141968218). Nothing else uses the permission, and the app works without it.

### The orientation button does nothing

Since Android 16 apps cannot [switch orientation](https://android-developers.googleblog.com/2025/01/orientation-and-resizability-changes-in-android-16.html) programmatically, but it can be re-enabled per app: open "Aspect ratio" in system Settings, find Just+ Player and switch it from "Full screen" to "App default".

## Credits and licence

Built on [Just (Video) Player](https://github.com/moneytoo/Player) by Marcel Dopita and on [AndroidX Media3](https://github.com/androidx/media). Translations come from upstream's [Weblate project](https://hosted.weblate.org/engage/just-player/). Released into the public domain under [the Unlicense](LICENSE), like upstream.

Other open source Android video players worth knowing: [VLC](https://code.videolan.org/videolan/vlc-android), [mpv](https://github.com/mpv-android/mpv-android), [Next Player](https://github.com/anilbeesetti/nextplayer), [Fermata](https://github.com/AndreyPavlenko/Fermata), [Nova Video Player](https://github.com/nova-video-player/aos-AVP), [Kodi](https://github.com/xbmc/xbmc) — or a [longer list on IzzyOnDroid](https://android.izzysoft.de/applists/category/named/multimedia_video_player).
