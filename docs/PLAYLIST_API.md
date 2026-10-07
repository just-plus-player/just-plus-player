# Just+ Player — Playlist API

Reference for apps that launch the player with a playlist and read back what was watched: every input
key, how the voice and the tracks are chosen, every output key, the result callback, and the rules for
malformed input.

**Available from Just+ Player 2.1.1.**

---

## Conventions

**Terms.** Used in one sense throughout:

| Term | Meaning |
|---|---|
| **voice** | One entry of an item's `voices[]`: a separate stream of the same episode, one per dub (section 4.2). |
| **audio track** | A track inside the stream that plays. A stream may carry several. |
| **dub** | What the viewer hears as "the dub": a voice or an audio track, named by its studio ("LostFilm") or its kind ("Dubbing"). The audio menu is titled "Dub". |
| **launch** | One `startActivity` with a `playlist`. Everything "in this launch" is forgotten at the next one. |

**Types.** `String`, `int`, `long`, `double`, `boolean` are the `Bundle` value types of the same name.
`String[]` also accepts an `ArrayList<String>`. `Bundle[]` is an array of Bundles: put it with
`putParcelableArray` (an `ArrayList<Bundle>` is accepted too); read one from the result with
`getParcelableArray`, as it arrives as `Parcelable[]`. `A | B` means either type.

**Times.** Every time key comes in two units: `<name>_ms` in milliseconds and `<name>_sec` in seconds.
On input send either; when both are sent, `_ms` wins. The result always carries both: `_ms` as `long`
(`long[]` for `positions_ms`), `_sec` as whole seconds in `int` (`int[]`), rounded down. This reference
names the `_sec` key; everything said of it holds for its `_ms` pair.

---

## Quick start

Launch with one item:

```kotlin
val playlist = Bundle().apply {
    putParcelableArray("items", arrayOf(
        Bundle().apply {
            putString("uri", "https://host/film.mkv")
            putString("title", "Film")
        },
    ))
}
val intent = Intent(Intent.ACTION_VIEW)
    .setComponent(ComponentName("com.justplus.player", "com.brouken.player.PlayerActivity"))
    .putExtra("playlist", playlist)
try {
    startActivityForResult(intent, REQUEST_PLAY)
} catch (e: ActivityNotFoundException) {
    // the player is not installed
}
```

Read what was watched:

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)
    if (requestCode != REQUEST_PLAY) return
    val result = data?.extras ?: return          // none: launched into a running player, section 1
    val index = result.getInt("index", -1)        // -1: the request was refused, section 6.3
    val positionSec = result.getInt("position_sec")
    val endBy = result.getString("end_by")        // completion | user | cancelled | error
}
```

For a result that survives Home, a killed app and a second launch, add a `result_callback`
(section 8). A full series example is in section 11.

---

## Key index

**Input** — on the `playlist` Bundle (P), on an item (I), on a quality (Q), voice (V) or subtitle (S)
entry:

| Key | Type | On | Section |
|---|---|---|---|
| `items` | `Bundle[]` | P | [2](#2-input-the-playlist-bundle), [3](#3-input-an-item) |
| `start_index` | `int` | P | [2](#2-input-the-playlist-bundle) |
| `result_callback` | `PendingIntent` | P | [8](#8-the-result-callback) |
| `report_interval_sec` / `_ms` | `int` / `long` | P | [2](#2-input-the-playlist-bundle) |
| `ask_resume` | `boolean` | P | [2](#2-input-the-playlist-bundle) |
| `title`, `logo`, `background` | `String` | P, I | [2](#2-input-the-playlist-bundle), [3](#3-input-an-item) |
| `headers` | `String[]` | P, I | [2](#2-input-the-playlist-bundle) |
| `uri` | `String` | I, Q, V, S | [3](#3-input-an-item), [4](#4-qualities-voices-subtitles) |
| `episode_title`, `thumbnail`, `imdb_id`, `tmdb_id`, `segments` | `String` | I | [3](#3-input-an-item) |
| `season`, `episode` | `int` | I | [3](#3-input-an-item) |
| `position_sec` / `_ms` | `int` / `long` | I | [3](#3-input-an-item) |
| `clip_start_sec`, `clip_end_sec` / `_ms` | `double` / `long` | I | [3](#3-input-an-item) |
| `qualities` | `Bundle[]` | I, V | [4.1](#41-qualities) |
| `voices` | `Bundle[]` | I | [4.2](#42-voices--one-dub-per-stream) |
| `subtitles` | `Bundle[]` | I, V | [4.3](#43-external-subtitles) |
| `label` | `String` | Q, V, S | [4](#4-qualities-voices-subtitles) |
| `selected` | `boolean` | Q, V, S | [4](#4-qualities-voices-subtitles) |
| `mime`, `language` | `String` | S | [4.3](#43-external-subtitles) |
| `audio_index`, `subtitle_index` | `int` | P, I | [5.1](#51-the-keys), [5.2](#52-index) |
| `audio_label`, `subtitle_label` | `String` | P, I | [5.3](#53-label) |
| `audio_language_ordinal`, `subtitle_language_ordinal` | `int` | P, I | [5.4](#54-language-ordinal-and-count) |
| `audio_language_count`, `subtitle_language_count` | `int` | P, I | [5.4](#54-language-ordinal-and-count) |
| `audio_languages`, `subtitle_languages` | `String[] \| String` | P, I | [5.5](#55-languages) |
| `audio_language`, `subtitle_language` | `String` | P, I | [5.5](#55-languages) |

**Output** — the result's extras:

| Key | Type | Section |
|---|---|---|
| `uri` | `String` | [7.2](#72-extras) |
| `index` | `int` | [7.2](#72-extras) |
| `position_sec`, `duration_sec` / `_ms` | `int` / `long` | [7.2](#72-extras) |
| `positions_sec` / `_ms` | `int[]` / `long[]` | [7.2](#72-extras) |
| `end_by`, `error_message` | `String` | [7.2](#72-extras) |
| `history` | `Bundle[]` | [7.4](#74-history) |
| `audio_language`, `audio_label`, `subtitle_language`, `subtitle_label` | `String` | [7.2](#72-extras) |
| `audio_language_ordinal`, `audio_language_count`, `subtitle_language_ordinal`, `subtitle_language_count` | `int` | [7.2](#72-extras) |
| `audio_index`, `subtitle_index` | `int` | [7.2](#72-extras) |
| `audio_chosen_by`, `subtitle_chosen_by` | `String` | [7.3](#73-audio_chosen_by--subtitle_chosen_by) |
| `voice_label` | `String` | [7.2](#72-extras) |
| `warnings` | `String[]` | [7.2](#72-extras) |

---

## Contents

1. [Launching the player](#1-launching-the-player)
2. [Input: the playlist Bundle](#2-input-the-playlist-bundle)
3. [Input: an item](#3-input-an-item)
4. [Qualities, voices, subtitles](#4-qualities-voices-subtitles)
5. [Choosing the voice, the audio track and the subtitle](#5-choosing-the-voice-the-audio-track-and-the-subtitle)
6. [Types and malformed input](#6-types-and-malformed-input)
7. [Output: the result](#7-output-the-result)
8. [The result callback](#8-the-result-callback)
9. [Passing a result back](#9-passing-a-result-back)
10. [Limits and platform notes](#10-limits-and-platform-notes)
11. [Complete example (Kotlin)](#11-complete-example-kotlin)

---

## 1. Launching the player

| | |
|---|---|
| Package | `com.justplus.player` |
| Activity | `com.brouken.player.PlayerActivity` (exported, `singleTask`) |
| Action | `android.intent.action.VIEW` |
| Data | optional, informational: the start item's uri. What plays is `items[start_index]` |
| Type | `video/*` when data is set |
| Extra | `playlist` — a `Bundle`, described below |

```kotlin
val intent = Intent(Intent.ACTION_VIEW).apply {
    component = ComponentName("com.justplus.player", "com.brouken.player.PlayerActivity")
    setDataAndType(Uri.parse(firstUri), "video/*")   // may be left out
    putExtra("playlist", playlist)
}
startActivityForResult(intent, REQUEST_PLAY)          // or registerForActivityResult
```

`playlist` must be put as a `Bundle`. There is no version field.

**singleTask.** The player runs as one instance. A second launch while it is open goes to the running
player: the old session is closed (its final report goes to the **old** `result_callback`), the new one
starts, and the second `startActivityForResult` receives `RESULT_CANCELED` at once from the system — use
`result_callback` if you launch into a running player.

**Session mode.** A `playlist` launch never writes to the player's own resume store: positions belong to
the caller. Two things the viewer does are kept anyway, as they are the viewer's, not the session's: the
remembered dubs (section 5.6), and a "prefer this language" answer, which changes the player's audio
language setting.

---

## 2. Input: the playlist Bundle

```
playlist : Bundle
├─ title               String          series / film name; fallback for an item without one
├─ logo                String (URL)    series logo; fallback for an item without one
├─ background          String (URL)    backdrop image; fallback for an item without one
├─ start_index         int             item to start on; default 0
├─ headers             String[]        HTTP headers as name, value pairs, for every item
├─ result_callback     PendingIntent   optional; section 8
├─ report_interval_sec int             optional; a report every N s while playing, N ≥ 30 (or _ms)
├─ ask_resume          boolean         optional; ask before resuming an item
├─ (track keys)                        optional; section 5 — an item's own key beats these
└─ items               Bundle[]        at least one; section 3
```

| Key | Type | Default | Notes |
|---|---|---|---|
| `title` | `String` | the file name | The header's bold line. An item's `title` beats it. |
| `logo` | `String` (URL) | none | Shown in place of the title (the viewer can turn this off). A logo narrower than 3:2, or one that fails to load, shows the title instead. A wide transparent PNG reads best. |
| `background` | `String` (URL) | none | Backdrop of the loading screen. |
| `start_index` | `int` | 0 | Out of range → bad input (section 6.3). |
| `headers` | `String[]` | none | `{"User-Agent", "x", "Referer", "y"}`. An item's `headers` override these name by name, case-insensitive. With an odd count the last name is dropped silently; a pair with a `null` is skipped. Without them every request carries `Accept: */*`, `Accept-Language` of the device and `User-Agent: JustPlusPlayer/<version> (Linux;Android <n>) AndroidXMedia3/<version>`; a header of the same name sent here replaces the default. Cookies a server sets are sent back to it for the rest of the session, unless a `Cookie` header is given. |
| `result_callback` | `PendingIntent` | none | Section 8. |
| `report_interval_sec` | `int` | off | ≤ 0 or absent = off; below 30 counts as 30. Reports only while something plays. |
| `ask_resume` | `boolean` | `false` | `true`: an item opening with a position asks "Resume / Start over" first — see below. |
| `items` | `Bundle[]` | — | Required, at least one. |

**`ask_resume`** decides what happens when an item with a position opens: the start item, with its
`position_sec` or the player's own saved position, and an item the viewer jumps to from the playlist
panel. The viewer's "Resume playback" setting does not apply to a playlist. Moving on to the next item
by itself always starts at its beginning.

`ask_resume` is read from Just+ Player 2.2.1. It replaces `resume_mode` (2.1.1–2.1.4), which is no
longer read: a caller that still sends it gets no question and no error, the item resumes.

| `ask_resume` | The start item | A jump in the playlist panel |
|---|---|---|
| absent or `false` | resumes without asking | resumes without asking |
| `true` | asks "Resume / Start over" | asks |

When it asks, a position under 30 s is not worth a question: it starts at 0. Without `ask_resume` a
`position_sec` is taken as it is. Any other position that counts as watched starts at 0: one in the last 5 % of the file, or one past the start of the end credits in the item's
`segments`. Credits count only when they reach into that last 5 % and take at most 15 % of the file —
anything else is a wrong entry and is ignored. The length is the one the player saw the file play
with, else the one this session saw, else the `duration_ms` of the item's `segments`; with none of
them known, the position is used as it is.

A "continue watching" card that opens episode 5 at 12:30 and lets the viewer choose:

```kotlin
playlist.putInt("start_index", 4)
items[4].putInt("position_sec", 750)
playlist.putBoolean("ask_resume", true)    // "Resume from 12:30 / Start over"; a jump asks too
```

---

## 3. Input: an item

```
items[i] : Bundle
├─ uri              String          the stream; required unless qualities or voices are given
├─ title            String          series / film name; beats playlist.title
├─ episode_title    String          the episode's own name
├─ logo             String (URL)    beats playlist.logo
├─ background       String (URL)    beats playlist.background
├─ thumbnail        String (URL)    a picture for the playlist panel / header; never a Bitmap
├─ imdb_id          String          "tt0944947"
├─ tmdb_id          String          "1399"
├─ season           int
├─ episode          int
├─ headers          String[]        name, value pairs; override the playlist's per name
├─ position_sec     int             where the item starts, in seconds (or position_ms)
├─ clip_start_sec   double          play only a part of the file as this item (or _ms)
├─ clip_end_sec     double
├─ segments         String          JSON: skippable intros, recaps, credits, ads
├─ (track keys)                     section 5
├─ qualities        Bundle[]        section 4.1
├─ voices           Bundle[]        section 4.2 — instead of uri / qualities
└─ subtitles        Bundle[]        section 4.3
```

| Key | Type | Notes |
|---|---|---|
| `uri` | `String` | http(s) (progressive, HLS, DASH), `content://`, `file://`, `rtsp://` — the player finds the container itself. Required unless `qualities` or `voices` are given. |
| `title` | `String` | The header's bold line is the series; the episode line goes under it. |
| `episode_title` | `String` | Shown as "Season N · Episode N · name". A name that only repeats the number ("Episode 3", "Серія 3", "E03") is left out. |
| `logo`, `background`, `thumbnail` | `String` (URL) | Images by URL only — a Bitmap would blow the Binder limit. |
| `imdb_id`, `tmdb_id` | `String` | Used for subtitle search and for remembering the viewer's dub per title (section 5.6). Without either, nothing is remembered per title. |
| `season`, `episode` | `int` | No key = no value. `0` or a negative number is accepted but not shown; never send `""` as "none". |
| `headers` | `String[]` | Name, value pairs, as on the playlist. |
| `position_sec` | `int` | For the start item: where playback starts. For any other item: where a jump to it from the playlist panel starts; moving on to it by itself starts at its beginning. With a clip, relative to the clip and clamped to it. |
| `clip_start_sec`, `clip_end_sec` | `double` | Fractional seconds, so a boundary between two episodes in one file lands on the exact frame. No key = start / end of the file. An end at or before the start is ignored. Positions in and out are relative to the clip. |
| `segments` | `String` | JSON, see below. The file's own chapters outrank it: a chapter named as an opening ("Opening", "Intro", "OP", "Заставка") or as the credits ("Credits", "Ending", "ED", "Титры") replaces what `segments` says about that stretch, and a credits chapter replaces the credits in `segments` wherever they are. Ads and everything else stay. |

**Segments JSON.** `start` / `end` in seconds; `duration_ms` is the length those timings were measured
on, so the player can rescale them to the real file. `skip` = intro / recap / credits, `ad` = advertising.

```json
{ "duration_ms": 2696000,
  "skip": [{ "start": 62, "end": 152 }],
  "ad":   [{ "start": 0,  "end": 12  }] }
```

---

## 4. Qualities, voices, subtitles

### 4.1 Qualities

One stream in several resolutions.

| Key | Type | Notes |
|---|---|---|
| `label` | `String` | "1080p", "720p", "4K" … — the menu sorts them from the highest down by the number in it. |
| `uri` | `String` | |
| `selected` | `boolean` | The quality to start with. Only the first entry marked counts. |

An entry without `label` or `uri`, or one that is not a Bundle, is skipped silently. What plays first:
the first `selected` entry, else the item's `uri` (played even if no entry has it), else the first entry.
A quality the viewer picked earlier in this launch is then carried over by its resolution number (1080,
720 …): when the item has it, the player switches to it right after opening.

### 4.2 Voices — one dub per stream

For sources where every dub is a separate file. An item gives **either** `uri` / `qualities` **or**
`voices`; both is bad input.

| Key | Type | Notes |
|---|---|---|
| `label` | `String` | Required: "LostFilm", "MVO \| HDrezka". |
| `uri` | `String` | Required unless `qualities` are given. |
| `qualities` | `Bundle[]` | As on an item. |
| `subtitles` | `Bundle[]` | Replace the item's subtitles for this voice; without them the item's are shared by every voice. |
| `selected` | `boolean` | The voice to start with. Only the first voice marked counts. |

Which voice plays is decided before anything loads, and differently from audio tracks: section 5.0.
The audio menu lists one row per voice; when the playing stream has two or more audio tracks they follow
under "In the file". Switching voice keeps the position and, where the voice has it, the quality. The
result reports the voice as `voice_label`.

### 4.3 External subtitles

| Key | Type | Notes |
|---|---|---|
| `uri` | `String` | Required (see below). |
| `mime` | `String` | `application/x-subrip`, `text/vtt`, `text/x-ssa` … — absent = from the extension. |
| `language` | `String` | BCP 47 or ISO 639 (`uk`, `ukr`, `en-US`). |
| `label` | `String` | Shown in the menu. |
| `selected` | `boolean` | Turn this one on (section 5.0, step 3). Only the first entry marked is tried. |

An entry without `uri` (or one that is not a Bundle) is **skipped with a warning** — unless the item or
the playlist numbers subtitles (`subtitle_index` ≥ 0): then it is bad input (section 6.3), because a
skipped entry would shift the number of every subtitle after it. Off (`-1`) numbers nothing.

When the item or the playlist numbers subtitles (`subtitle_index`) or turns them off
(`subtitle_index = -1`, `subtitle_languages = []`), the player does not look for more (no sidecar guess
next to the file, no online search), so the numbering cannot shift.

---

## 5. Choosing the voice, the audio track and the subtitle

### 5.0 How the voice and subtitle are applied, and when a choice is ignored

**When.** The player chooses the audio track and the subtitle of an item once per item per launch, the
moment the item first reports a playable track of that type. It does not choose again when the viewer
switches quality (the track playing is kept), when the viewer comes back to an item already played in
this launch, or when an HLS / DASH rendition appears later. It does choose again when the viewer
switches voice: the audio track always, the subtitle only if one of the two voices has its own
`subtitles[]`. A stream with no playable track of a type gets no choice for it, and its `*_chosen_by`
is `null`.

**Voices are not tracks.** `voices[]` are separate streams, and the voice is chosen before anything
loads, first match wins:

1. the voice the viewer picked earlier in this launch, matched by label (`LostFilm` matches
   `MVO | LostFilm`) — applied right after the item opens, at the cost of one more source switch;
2. the first voice with `selected = true`;
3. the voice remembered for this title (needs `imdb_id` or `tmdb_id`);
4. the viewer's habit, in the order of the player's audio language setting (not the caller's
   `audio_languages`);
5. the first voice.

Steps 3 and 4 run only for an item with two or more voices and none `selected`. `audio_*` keys never
choose a voice: they choose among the audio tracks inside the voice that plays. Voices have no
`chosen_by`; `voice_label` says which voice played, not why.

**The order for tracks.** Per item, audio and subtitles separately. First match wins; a step that names
nothing falls through to the next — never to "track 0".

| Step | Audio track | Subtitle | `*_chosen_by` |
|---|---|---|---|
| 1 | The viewer's pick earlier in this launch (by label, else by language and ordinal) | The same, "Off" included | `viewer` |
| 2 | The item's `audio_index` — only if in range, decodable and fitting the `audio_label` beside it | The item's `subtitle_index` (same checks), or off by `subtitle_index = -1` / `subtitle_languages = []` | `index` (off by `[]`: `languages`) |
| 3 | — | The item's first `selected` subtitle, if decodable | `selected` |
| 4 | The item's `audio_label`, in the item's languages in order (in any language if it has none) | The item's `subtitle_label`, the same way | `label` |
| 5 | The item's ordinal in its first language — only if the count matches, when sent | The same | `language_ordinal` |
| 6 | Steps 2, 4, 5 with the playlist's keys | Off / index, label, ordinal with the playlist's keys | as above |
| 7 | The dub remembered for this title (needs `imdb_id` or `tmdb_id`; not for a live stream). If the file lacks it, its language is kept for step 8 | — | `remembered` |
| 8 | The first language of the item's, else the playlist's `audio_languages` the file has; within it the viewer's habitual dub, else the track already playing if it is in that language, else the first non-commentary track | The first language of the item's, else the playlist's `subtitle_languages` the file has; full subtitles before forced or SDH | `languages` |
| 9 | What the player's selector took (the playlist's `audio_languages` if sent, else the player's audio language setting), refined by the habit within that language | The same with subtitle languages; a subtitle the file merely flags as default is never turned on by itself | `player` |

So caller keys outrank both memories for the audio track; only the viewer's pick in this launch
outranks the caller. Subtitles have no per-title memory.

**When a key is ignored.**

| Case | What happens | Visible as |
|---|---|---|
| Wrong type or value (`"two"`, `2.5`, `audio_index < 0`, `subtitle_index < -1`, a code that is no language) | Dropped when the request is read; the rest plays | `warnings`: `items[3].audio_index: "two" is not a whole number` |
| `*_language_ordinal` with no language on the same level | Dropped | `warnings`: `…_language_ordinal: ignored: no language to count in` |
| `*_language` together with `*_languages` | The list wins | `warnings`: `…_language: ignored: …_languages is given` |
| Index out of range, not decodable, or contradicting the label beside it | The index is skipped; the label, then the ordinal decide | `*_chosen_by` names the step that chose; the reason is only in the device log (section 6.1) |
| Label not in the file | Falls through | `*_chosen_by` ≠ `label` |
| Ordinal with a `*_language_count` other than the file's | Falls through | `*_chosen_by` ≠ `language_ordinal` |
| Language list names nothing the file has | Falls through to the player | `*_chosen_by` = `player` |
| `selected` subtitle cannot be decoded | Falls through to the label step | `subtitle_chosen_by` ≠ `selected` |
| The viewer picked a track earlier in this launch | Beats every caller key on the following items | `viewer` |
| The item has voices and you sent `audio_label` | Matched only among the audio tracks of the voice that plays | `voice_label` shows the voice, `audio_chosen_by` the track |
| A subtitle entry without `uri` | Skipped — unless `subtitle_index` ≥ 0 is sent on the item or the playlist; then the whole request is refused | `warnings`: `…subtitles[2] has no uri; skipped`, or `end_by = error` |
| A rendition that appears after the choice (HLS / DASH) | Not considered | — |

Well-formed keys that miss go to the device log only; malformed keys go to `warnings`.

### 5.1 The keys

The same set for audio (`audio_`) and subtitles (`subtitle_`), on the playlist and on an item. Every key
is optional; an item's key beats the playlist's.

| Key | Type | Meaning |
|---|---|---|
| `audio_index` | `int` | The audio track's number in the audio menu, from 0. |
| `audio_label` | `String` | The dub's name: a studio ("LostFilm") or a track title. |
| `audio_language_ordinal` | `int`, ≥ 0 | "The n-th track of the language", from 0, counted in the first language of the same level. |
| `audio_language_count` | `int`, ≥ 1 | How many tracks of that language there were where the ordinal was taken. Guards the ordinal. |
| `audio_languages` | `String[] \| String` | Languages in order of preference: `{"uk","ru"}` or `"uk,ru"`. |
| `audio_language` | `String` | One language — the same as `audio_languages = [x]`. |
| `subtitle_index` | `int` | As audio; **`-1` = subtitles off**. |
| `subtitle_label` | `String` | "Full", "Forced", "SDH", a translator's name … |
| `subtitle_language_ordinal` | `int`, ≥ 0 | |
| `subtitle_language_count` | `int`, ≥ 1 | |
| `subtitle_languages` | `String[] \| String` | **An empty array = subtitles off.** |
| `subtitle_language` | `String` | |

### 5.2 Index

- From 0, in the order the menu lists the tracks. Audio: the tracks of the stream that plays (with
  `voices`, the list under "In the file"; voices themselves have no number). Subtitles: the file's own
  tracks first, then `subtitles[]` in array order (a voice's own list when it has one).
- A track this device cannot decode keeps its number but an index on it does not apply.
- A phantom closed-caption channel (an empty CEA-608 track some streams declare) has no number; "Off"
  has no number.
- Counted over the tracks known when the item's choice is made; an HLS rendition that appears later is
  not counted.
- **An index with a label beside it** (same level) applies only if that track also fits the label. If it
  does not, the index is dropped and the label decides — a list that changed between episodes must not
  play the wrong dub.

Prefer labels and languages for a series: track 2 of one episode is not track 2 of the next.

### 5.3 Label

- Several dubs of one language differ only by name, so the label is matched by the **studio** it names,
  from a dictionary of ~900 studios and their spellings: `MVO | LostFilm`, `LostFilm [AC3]` and
  `LostFilm` are the same dub; `HDrezka Studio`, `Rezka`, `RezkaStudio` are one studio.
- With no studio on either side, the words are compared after dropping codec, channel, bitrate, language
  and dub-kind words: two labels match when every word of the shorter appears in the longer, or when
  their words joined without spaces are equal (`Voice Project` = `VoiceProject`). So `LostFilm` also
  matches `LostFilm Extra`. A label that says only the kind ("Дубляж", "MVO", "Dub") matches a track of
  the same kind.
- Whole words, case-insensitive; no loose substrings (`Studio` does not match `NewStudio`), no
  edit-distance guessing. A miss falls through safely; a wrong match would silently play the wrong dub.
- Kind flags must agree: a forced or SDH subtitle is not a full one, a commentary is not a dub. An 18+
  cut ("HDrezka Studio 18+") is told apart from the plain one where both are present.
- Looked for in the languages of the same level, in their order (`audio_languages = ["uk","ru"]` +
  `audio_label = "LostFilm"`: Ukrainian LostFilm first, then Russian). With no language on the level, in
  any language.

### 5.4 Language ordinal and count

- `audio_language_ordinal = 1` = the second track of the first language on the same level. Without a
  language on that level the ordinal is dropped (with a warning).
- With `audio_language_count`, the ordinal applies only when the file has exactly that many tracks of the
  language — an extra track in between would shift it. Whenever the result reports an ordinal it also
  reports the count; send both back.

### 5.5 Languages

- Accepted: BCP 47 (`uk`, `en-US`, `pt_BR`) or ISO 639-1 / 639-2/T / 639-2/B (`uk`, `ukr`, `ger` =
  `deu`), any case. Normalised to ISO 639-2/T before comparing: `ru` = `rus` = `RU-ru`.
- Compared by the language only: `pt-BR` = `pt-PT`, `zh-Hans` = `zh-Hant` (a known limit).
- A code that is no language (`russian`, `zz`, `und`) is dropped with a warning. A list emptied that way
  is "not sent", never "off".
- A track tagged `und` but named after its language ("rus", "Ukrainian") counts as that language when
  matching.
- The playlist's `audio_languages` / `subtitle_languages` also set the player's selector for the launch,
  in place of the player's own language settings, so the right language plays from the first frame where
  possible; an item's lists act through the steps of section 5.0 once its tracks are known.

### 5.6 What the player remembers on its own

- **The viewer's pick** — section 5.0, step 1 for tracks and step 1 for voices; reset by a new launch.
- **This title's dub** — per `imdb_id` (else `tmdb_id`): the dub the viewer watched it in, kept across
  launches (300 titles). Written when the viewer picks a dub by hand, and when the habit chose one for a
  title that had no record yet (a start voice, or a track within a decided language); never when the
  caller's index, label or ordinal chose. Keyed by the id alone, so it is shared by every app that
  launches the player with that id.
- **The habit** — which dub of a language the viewer usually picks; chooses only among tracks of a
  language already decided, never the language itself. For voices it is ranked by the player's audio
  language setting.
- **"Prefer this language?"** — when the viewer picks a dub by hand while the file has one in a language
  ranked higher, the player may ask. A yes moves that language to the top of the player's audio language
  setting, which steps 9 and the voice habit use; the playlist's `audio_languages` still outrank it for
  the launch.

### 5.7 Subtitles off

`subtitle_index = -1` or `subtitle_languages = []` (an empty array; `""` is "not sent"). On one level,
off beats that level's label and ordinal. The item's off beats its own `selected` subtitle; the item's
`selected` beats the playlist's off. Audio cannot be turned off: an empty `audio_languages` is ignored,
a negative `audio_index` is dropped.

---

## 6. Types and malformed input

### 6.1 Track keys — strict, never fatal

A track key of the wrong type or value is **dropped**, named in the result's `warnings`, and the choice
goes on. A malformed track key never stops the request from playing.

| Key | Accepted | Dropped (with a warning) |
|---|---|---|
| `*_index`, `*_language_ordinal`, `*_language_count` | `int`, `long`, `short`, `byte`; a `float` / `double` with a whole value (`2.0`); a `String` of a whole number (`"2"`, `" 2 "`, `"2.0"`) | a fraction (`2.5`), a word (`"two"`), out of `int` range, NaN, a `boolean`, any other type; `audio_index < 0`; `subtitle_index < -1`; ordinal `< 0`; count `< 1` |
| `*_label` | a `String`, trimmed | a number or any non-text type |
| `*_languages` | `String[]`, or one `String` split on commas and spaces (`"uk,ru"`, `"uk ru"`, `"uk, ru"`); blank items skipped, repeats folded (`ru`, `rus` → one) | another type (`int[]` …); an item that is no language code |
| `*_language` | a `String` | with `*_languages` beside it (the list wins); not a language code |

Empty or blank strings are "not sent", silently. Keys that do not exist are ignored.

A key that is well formed but names nothing in a given file — an index out of range, a label the file
does not have — is **not** a warning: a series varies from episode to episode. The result's
`audio_chosen_by` / `subtitle_chosen_by` tell which step chose (section 7.3), and the player's log says
why the caller's key did not apply — to see it, ask the viewer to send a report from Settings:
`audio_index 7 (item): out of range 0..2`, `… not supported`, `… contradicts the label`,
`audio_label "LostFilm" (playlist): not in the file`.

### 6.2 Other keys — lenient

| Kind | Read as |
|---|---|
| `String` keys | any value's text, trimmed; blank = absent |
| `int` keys (`start_index`, `season`, `episode`) | any number or numeric `String`, rounded down; anything else = absent |
| time keys (`position`, `report_interval`, `clip_start`, `clip_end` with `_sec` or `_ms`) | any number or numeric `String`; `position` and `report_interval` rounded down to a millisecond; anything else = absent, and then the other unit is read |
| `boolean` keys (`selected`) | a `boolean`, or the `String` `"true"` exactly (case-sensitive) |
| `Bundle[]` keys (`items`, `qualities`, `voices`, `subtitles`) | `Parcelable[]` or `ArrayList<Bundle>` |
| `headers` | `String[]`, name / value pairs |

### 6.3 Bad input — nothing plays

The player shows a short message, plays nothing and answers at once with `end_by = error`, `index = -1`
and `error_message`:

| Case | `error_message` |
|---|---|
| no `items`, an empty array, or another type | `playlist has no items` |
| an `items[i]` that is not a Bundle | `items[i] is not a Bundle` |
| an item with neither `uri` nor a usable quality nor `voices` | `items[i] has neither uri nor qualities` |
| an item with `voices` and `uri` / `qualities` | `items[i] has both voices and uri or qualities` |
| a voice that is not a Bundle | `items[i] voices[j] is not a Bundle` |
| a voice without `label` | `items[i] voices[j] has no label` |
| a voice with neither `uri` nor a usable quality | `items[i] voices[j] has neither uri nor qualities` |
| a `subtitles[]` entry that is not a Bundle or has no `uri`, while `subtitle_index` ≥ 0 is sent (section 4.3) | `items[i] subtitles[k] is not a Bundle, and subtitle_index counts on it`, `items[i] subtitles[k] has no uri, and subtitle_index counts on it`; in a voice's list `items[i] voices[j] subtitles[k] …` |
| `start_index` out of range | `start_index N is out of range 0..M` |

These messages are English and meant for logs; decide by `end_by` and `index`, not by the text. A
refused request reports no `warnings`.

---

## 7. Output: the result

### 7.1 Delivery

`setResult(RESULT_OK, intent)` when the player closes (Back, end of the playlist, a refused request),
and — when `result_callback` is given — the same extras to the callback on every leave (section 8).

| | |
|---|---|
| Action | `com.justplus.player.result` |
| Data | the uri of the item playback ended on (the quality / voice that actually played) |

### 7.2 Extras

| Key | Type | Meaning |
|---|---|---|
| `uri` | `String` | Same as data. In a callback the caller's data stays, so read this. `null` when the request was refused. |
| `index` | `int` | Item playback ended on; `-1` when the request was refused. |
| `position_sec` | `int` | Position in that item, seconds (relative to its clip). |
| `duration_sec` | `int` | That item's duration, seconds; **`0` = not known**. |
| `positions_sec` | `int[]` | One per item: `-1` = never opened (also in `positions_ms`); equal to the item's duration = watched to the end; otherwise the last position. Empty when the request was refused. |
| `end_by` | `String` | `completion` — the playlist played to its end; `user` — the viewer left; `cancelled` — closed before anything played; `error` — see below. |
| `error_message` | `String` | Only with `error`. |
| `history` | `Bundle[]` | The session journal, section 7.4. Empty when the request was refused. |
| `audio_language` | `String` | The audio track playing, its language as the media states it: `ru`, `rus`, `en-US` … — normalise before comparing (section 5.5). `null` when the track names none — also for a track tagged `und` that the player matched by its name. |
| `audio_label` | `String` | Its name; `null` when it has none (a packager's positional name like `rus0` counts as none). |
| `audio_language_ordinal` | `int` | Its place among the tracks of its language, from 0. For a track with no language: among the tracks with none. |
| `audio_language_count` | `int` | How many tracks of that language the file has. |
| `audio_index` | `int` | Its number in the audio menu, from 0 — the number `audio_index` takes on input (section 5.2). |
| `audio_chosen_by` | `String` | Which step chose it — section 7.3. |
| `subtitle_language` | `String` | As audio; `null` = the track names no language, subtitles are off, or the subtitle is a file the player found itself (online search). |
| `subtitle_label` | `String` | As audio. For a subtitle file the player found itself (online search), the file's label, with no language, ordinal or count. |
| `subtitle_language_ordinal` | `int` | As audio. |
| `subtitle_language_count` | `int` | As audio. |
| `subtitle_index` | `int` | As audio, numbered as section 5.2 numbers subtitles; `-1` when subtitles are off. Absent for a subtitle file the player found itself (online search). |
| `subtitle_chosen_by` | `String` | Section 7.3. |
| `voice_label` | `String` | The voice that played, when the item has `voices`. Says which, not why. |
| `warnings` | `String[]` | Present only when something was dropped while reading the request: a track key (section 6.1) or a skipped subtitle entry (section 4.3). Each reads `<path>: <problem>` — `items[3].audio_index: "two" is not a whole number`, `items[0]: subtitles[2] has no uri; skipped`. At most 20; the last one then reads `… N more`. For humans and logs: the wording may change. |

Absent keys: `*_language_ordinal` and `*_language_count` are left out when not known; they come
together.

**`end_by = error`** has two causes:

- **The request was refused** (section 6.3): `uri` is `null`, `index` is `-1`, `positions_sec` and
  `history` are empty, `error_message` is one of the English messages of section 6.3.
- **Playback failed** (a stream that cannot be opened or decoded): `index`, `uri`, `positions_sec` and
  `history` are real; `error_message` is the sentence the viewer was shown, in the viewer's language —
  not for parsing. The error clears as soon as something plays again (the viewer retries or moves to
  another item): the next report then has `user` or `completion`.

### 7.3 `audio_chosen_by` / `subtitle_chosen_by`

| Value | The track was chosen by |
|---|---|
| `viewer` | the viewer, in the menu (now or earlier in this launch) |
| `index` | a caller's `*_index` (for subtitles, also `-1` = off) |
| `selected` | a caller's `selected` external subtitle |
| `label` | a caller's `*_label` |
| `language_ordinal` | a caller's ordinal |
| `remembered` | the dub remembered for this title (audio) — also when only its language decided and the track within it came from the habit or was the first of that language |
| `languages` | a caller's `*_languages` decided the language (for subtitles, also `[]` = off); the dub within it may come from the viewer's habit |
| `player` | the player's own choice: its language settings, the habit within a language the caller did not name, the file's defaults |
| `null` | nothing was chosen for this item: a refused request, its tracks not loaded yet, or no playable track of that type |

Sent `audio_label = "LostFilm"` and got `audio_chosen_by = languages`? The episode had no LostFilm and
the language step chose.

Both describe the item in `index`. Each item keeps its own value for the launch: coming back to an
item reports what was chosen for it before.

### 7.4 `history`

One Bundle per visit of an item, in order, the visit in progress included:

| Key | Type | Meaning |
|---|---|---|
| `index` | `int` | The item. |
| `started_at` | `long` | Unix time, seconds: when the visit opened. |
| `ended_at` | `long` | Unix time, seconds: when `position_sec` was last moved — the moment the item was left, paused or stopped; for the visit playing now, about now. |
| `position_sec` | `int` | Where that visit ended; for the current one, where it is now. |
| `duration_sec` | `int` | The item's duration; **`-1` = not known** (unlike the top-level `duration_sec`, where it is `0`). |

Each entry carries `position_ms` and `duration_ms` as well.

A visit opens on the first playback and on every move to another item (auto-next, a jump, a repeat); a
rebuild of the same item (a quality or voice switch) continues the open visit. The journal survives a
recreate and a process kill. It holds the last 500 visits; older ones are dropped. `positions_sec` is
not affected by the cap.

---

## 8. The result callback

`setResult` is lost on Home, when your process died meanwhile, when you did not launch for a result, and
on a second launch into the running player. A `result_callback` reaches you in all of these.

**When it is sent** — always a full snapshot of the session so far (section 7.2), never a delta, so a
repeat is harmless:

- the viewer leaves: Home, Back, the end of the playlist, a picture-in-picture window closed;
- every stop of the player but a recreate of its screen (screen off, a TV switched off, the task swiped
  away);
- a new launch replacing the session — to the **old** session's callback;
- every `report_interval_sec` while something plays;
- possibly when the player opens a screen of its own (settings, a file picker).

A snapshot identical to the one sent before is not sent again (Home used to deliver one report twice,
and the next launch a third time). Still, never treat a report as the last one: the latest received is
the state of the session.

**How to build it**

```kotlin
val callback = PendingIntent.getBroadcast(
    context, requestCode,                               // one per session if you run several
    Intent(context, PlayerReportReceiver::class.java)   // explicit; your receiver, in your manifest
        .putExtra("myapp_session_id", sessionId),       // your own extras come back too
    PendingIntent.FLAG_UPDATE_CURRENT or
        (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
)
playlist.putParcelable("result_callback", callback)
```

- **Mutable**, or the system silently drops every extra the player adds. Below Android 12 a
  `PendingIntent` is mutable by default, and so it is for an app targeting SDK < 31; targeting 31+, add
  `FLAG_MUTABLE`.
- **A broadcast to an explicit receiver** declared in the manifest, so it is delivered while your app is
  in the background or was killed. It can be `android:exported="false"`: a `PendingIntent` is sent with
  your app's identity. The intent must be explicit — an app targeting Android 14 (API 34) or later
  cannot create a mutable `PendingIntent` from an implicit intent; the system throws. An activity
  `PendingIntent` would pull your app over the player.
- The player only fills in extras (`Intent.fillIn`): your action, component, data and extras stay as you
  set them. On a name clash **your** extra wins, so prefix yours (`myapp_…`).
- Read the result with `intent.extras` in `onReceive`, exactly as section 7.2.

---

## 9. Passing a result back

Every track key of the result is an input key. To resume a series in the same dub and subtitles, copy
them onto the next launch — on the playlist (for the whole series) or on the item:

| From the result | Put on the next launch |
|---|---|
| `audio_language`, `audio_label`, `audio_language_ordinal`, `audio_language_count` | the same keys |
| `subtitle_language`, `subtitle_label`, `subtitle_language_ordinal`, `subtitle_language_count` | the same keys |
| `subtitle_index = -1` | the same key — subtitles stay off |
| `audio_index`, `subtitle_index` ≥ 0 | the same keys, for the **same file** only. Another episode may number its tracks differently; the label beside the index guards it (section 5.2), but the label keys above are what carries a dub across a series. |
| `voice_label` | `selected = true` on the voice with that label |
| `positions_sec[i]` (or `positions_ms[i]`) | `items[i].position_sec` (or `position_ms`; skip `-1` and finished ones) |

When `*_language` is `null`, leave out the ordinal and the count as well: without a language they are
dropped with a warning (section 5.4).

The label decides first; the ordinal (guarded by the count) and the language back it up when the next
file names the dub differently.

---

## 10. Limits and platform notes

- **Size.** Keep the whole intent well under 500 KB — the Binder limit is 1 MB per process, shared.
  Images by URL only. 100 episodes with subtitles and qualities is tens of KB.
- **`content://` uris.** `FLAG_GRANT_READ_URI_PERMISSION` covers only `data` and `ClipData`, not
  extras: put every `content://` item and subtitle uri into the intent's `ClipData` as well.
- **Headers** are matched by the request's uri: a request that is not an item's own (an HLS segment, a
  subtitle) gets the headers of the item playing now. Harmless while all items share one header set.
- **Android 6+.** Everything here — nested Bundles, `Parcelable[]`, broadcast `PendingIntent`s,
  `fillIn` — exists since API 1. On the player side arrays arrive as `Parcelable[]`, never `Bundle[]`.
- **Not supported (yet):** a stream mime type (the player detects the container), chapters, an external
  audio file per item, numeric error codes.

---

## 11. Complete example (Kotlin)

`callback` (section 8), `log`, `savePosition`, `saveTrackKeys` and `REQUEST_PLAY` are your own.

```kotlin
fun episode(uri: String, season: Int, episode: Int, name: String, positionSec: Int?) = Bundle().apply {
    putString("uri", uri)
    putString("imdb_id", "tt0944947")
    putInt("season", season)
    putInt("episode", episode)
    putString("episode_title", name)
    positionSec?.let { putInt("position_sec", it) }
    putParcelableArray("qualities", arrayOf(
        Bundle().apply { putString("label", "1080p"); putString("uri", "$uri?q=1080"); putBoolean("selected", true) },
        Bundle().apply { putString("label", "720p"); putString("uri", "$uri?q=720") },
    ))
    putParcelableArray("subtitles", arrayOf(
        Bundle().apply {
            putString("uri", "https://host/s${season}e$episode.uk.srt")
            putString("language", "uk")
            putString("label", "Full")
        },
    ))
}

val playlist = Bundle().apply {
    putString("title", "Game of Thrones")
    putString("logo", "https://host/logo.png")
    putStringArray("headers", arrayOf("User-Agent", "MyApp/1.0"))
    putInt("start_index", 2)
    // The series in Ukrainian, LostFilm if there is one, else Russian; Ukrainian subtitles.
    putStringArray("audio_languages", arrayOf("uk", "ru"))
    putString("audio_label", "LostFilm")
    putStringArray("subtitle_languages", arrayOf("uk"))
    putParcelableArray("items", arrayOf(
        episode("https://host/s1e1.mkv", 1, 1, "Winter Is Coming", null),
        episode("https://host/s1e2.mkv", 1, 2, "The Kingsroad", null),
        episode("https://host/s1e3.mkv", 1, 3, "Lord Snow", 754),
    ))
    putInt("report_interval_sec", 120)
    putParcelable("result_callback", callback)              // section 8
}

val intent = Intent(Intent.ACTION_VIEW).apply {
    component = ComponentName("com.justplus.player", "com.brouken.player.PlayerActivity")
    putExtra("playlist", playlist)
    // content:// items or subtitles: also put each uri in ClipData and grant read access (section 10):
    // clipData = ClipData.newRawUri("", contentUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}
try {
    startActivityForResult(intent, REQUEST_PLAY)
} catch (e: ActivityNotFoundException) {
    // the player is not installed
}

// onActivityResult or the callback's onReceive:
fun read(extras: Bundle) {
    if (extras.getString("end_by") == "error") log("player: ${extras.getString("error_message")}")
    if (extras.getInt("index", -1) < 0) return              // refused: nothing played (section 6.3)
    val positions = extras.getIntArray("positions_sec") ?: return
    positions.forEachIndexed { i, sec ->
        // -1 = never opened. Finished = sec equals the item's duration: keep durations yourself
        // (duration_sec while index == i); history may have dropped the item's visits (section 7.4).
        if (sec >= 0) savePosition(i, sec)
    }
    saveTrackKeys(extras)                                   // section 9
    extras.getStringArray("warnings")?.forEach { log("player dropped: $it") }
}
```
