# Plan: accurate skip-segment selection + optional video-based boundary refinement

## Context

Online skip-segment lookup (`com.brouken.player.skip`) misfires in two distinct ways (diagnosis in
`SKIP/SKIP_VOTING_PLAN.md`):

1. **Shifted skip** — 4 of 6 sources (IntroDB.app, Aniskip, TheIntroDB, IntroHater) return absolute
   broadcast seconds, and `NetworkSegmentsSource` applies them without remap. Worse, the current
   `bestScored` (trust×signal, `ACCEPT=0.60`) lets them overtake duration-aware SkipDB, inverting its
   own "file-adaptivity first" principle.
2. **Extra/phantom skip** — winner-take-all: one bad source supplies the whole list; plus mapping bugs
   (`isMovie = season<1||episode<1`, split-cour `cand[0]`, open-ended `99999`, cache without
   `durationSec`, never-expiring negative cache).

**Goal (P0, mandatory):** most accurate segment selection for movies and series — replace
winner-take-all with **cross-source voting** that is coordinate-system aware.
**Goal (P1, desirable):** optional refinement of boundaries from the video itself (black-frame snap),
default OFF and auto-disabled on weak devices.

### Fixed decisions
- Scope: **P0 + P1** (chapters deferred, see "Out of scope").
- Single unconfirmed segment: **behaves as before** (auto-skip per `skipMode`/`skipModeCredits`).
  Coverage prioritized; voting still fixes the shift (coordinate-base) and dedups agreeing segments.
- Probe budget: **whole profile** (max coverage) → to avoid accumulating latency, sources are probed
  **in parallel**.

### Logistics (artifacts in the project directory)
As the first implementation step (before code edits):
1. **New branch** off `master`: `feature/skip-segment-voting`.
2. **Save this whole plan** in the repo: `SKIP/SKIP_ACCURACY_PLAN.md` (this file).
3. **Progress journal**: `SKIP/SKIP_ACCURACY_PROGRESS.md` — kept up to date during execution (modeled
   on `SKIP/FIND_INTRO_PROGRESS.MD`): checklist of steps A1–A4/B1–B4, statuses, decisions/notes,
   manual-test results. Update after each significant step.

---

## Part A — P0: voting (accuracy)

### A1. Model: `skip/SkipSegment.java`
Voting is "per segment type", but the model only knows `Type{SKIP,AD}` (intro/recap/credits category
from the API is lost). Add:
- `enum Category { INTRO, RECAP, CREDITS, PREVIEW, UNKNOWN }` + `public final Category category;`
- `enum CoordBase { DURATION_AWARE, ABSOLUTE, CHAPTER }` + `public final CoordBase coordBase;` +
  `public final int timeTrust;` (time-source priority: chapter > duration-aware > absolute).
- `public boolean confirmed;` (runtime flag: ≥2 agreeing). Keep the old constructor as an overload
  (`Category.UNKNOWN`, `ABSOLUTE`) so `IntentSegmentsSource` need not be rewritten.

### A2. Core: `skip/SegmentFinder.java` (main work)
- **Parallel profile probe.** Replace the sequential ladder with a fan-out modeled on
  `SubtitleFetcher.java` (`client.newCall().enqueue()` + `CountDownLatch`). Each source →
  `Scored{segments, signal, coordBase, timeTrust}`. Probe **all** profile sources (remove `ACCEPT`
  early exit and `NONEMPTY_BUDGET`); overall deadline ≈ one timeout (5–6s) + cancel via
  `Thread.interrupt()`.
- **Two order profiles** (order = time priority on equal agreement):
  - non-anime: `SkipDB(DA) → SkipMe(DA) → [ABS: TheIntroDB, IntroDB.app, IntroHater]`
  - anime: `Aniskip(primary, ABS) → SkipDB(DA) → SkipMe(DA) → [ABS: ...]`
- **Voting** (`voteSegments(...)` instead of `bestScored`):
  1. Group same-`Category` segments from all non-empty sources.
  2. **Agreement = ≥2 sources** put a segment in one zone (overlap OR starts within
     `AGREE_TOLERANCE_SEC`, default 30s) → `confirmed=true`.
  3. **Timing** of a confirmed segment — from the highest `timeTrust` among agreeing sources (for
     anime, prefer Aniskip's time). **Do not average** across different coordinate systems.
  4. **Single segment** (only 1 source, type not seen elsewhere) → included as before
     (`confirmed=false`, stays in the list; UI handles per mode — see A3).
- Remove the inversion: adaptivity is expressed via `timeTrust`/order, not a fixed signal.
- **Cache**: key `imdb|tmdb|season|episode|durationBucket` (bucket = rounded `durationSec`, so a
  replay of a different rip does not reuse stale timings). Negative result — TTL (e.g. 10 min), not
  for the whole process.
- **Fix `isMovie`**: season present, episode 0/absent → do not blindly treat as movie (use the series
  profile when a season exists).
- **Remove all debug logging** from the working tree (`TAG="SkipSegments"`, `Log.d`, `fmt`, TODO, and
  `Candidate.name` added only for logs; revert the `execute()` reordering if it was only for logs) —
  CLAUDE.md rule.

### A3. Consumption: `skip/NetworkSegmentsSource.java` / `skip/SkipManager.java` / UI
- `NetworkSegmentsSource` returns the already-grouped/tagged voting output (still fresh copies each
  rebuild so `skipped` does not leak).
- **Open-ended `99999`**: allow only for `Category.CREDITS`/end-of-file; otherwise drop or synthesize
  a reasonable end. Cross-check `classifyCredits`/`reachesEnd` (`CREDITS_END_FRACTION=0.75`, tol 1.5s).
- **UI policy** in `PlayerActivity.skipTick()` (~1843): behavior by `confirmed` — per the "as before"
  decision, a single segment still auto-skips per mode. Keep `confirmed` in the model (headroom for a
  future tightening) but do NOT weaken current auto-skip. `Type.AD` — as now (silent auto-skip).

### A4. Constants
All thresholds in one place (next to current `ACCEPT`/`NONEMPTY_BUDGET`): `AGREE_TOLERANCE_SEC=30`,
`MIN_VOTES=2`, probe deadline, `timeTrust` priorities by `CoordBase`.

---

## Part B — P1 (optional): black-frame snap boundary refinement

> **REMOVED (user feedback).** The content-based refinement below (black-frame + scene-cut, via
> `SegmentRefiner`) was implemented and tested on-device but did not work well enough, so it was
> dropped in full. Only the deterministic **manual skip offset** (`skipOffset`, see B4) survives from
> Part B. The rest of this section is kept as a historical record of what was tried. P0 is unaffected.

**Isolated: 1 new class + 1 pref + ~5 small call sites. Default OFF. Deletable without touching P0.**
Ranking: black-frame (recommended) > silence (phase 2) > scene-change (reject). No in-player frame
access — use a **separate `MediaMetadataRetriever`** over the same URI in the background, never
touching `player`/surface/renderers.

### B1. New file: `skip/SegmentRefiner.java`
- `static Thread refine(Context, Uri, Map<String,String> headers, List<SkipSegment>, double
  durationSec, Callback)` — interruptible **daemon** thread (contract like `SegmentFinder.find`),
  callback on the worker with a **new** list; worker's first line `Process.setThreadPriority(LOWEST)`.
- **Eligibility** (on the worker, first): scheme `file`/`content`, or http(s) with
  `Utils.isProgressiveContainerUri(uri)`; otherwise no-op (no HLS/DASH/DRM). Refine **≤2 boundaries**:
  intro/recap end (`SKIP && !credits`) and credits start.
- **Algorithm**: window **±8s** around the broadcast value, clamped to `[0,duration]` and to neighbor
  boundaries (only "nudge", never "invent"/"delete"). Coarse pass 1s step `OPTION_CLOSEST_SYNC` +
  `getScaledFrameAtTime(...,96,54)` (API27+, thumbnail-res); blackness = mean luma < ~0.06 AND low
  variance (catches fade, rejects dark scenes). If a "dark valley" exists — fine pass 0.25s step
  `OPTION_CLOSEST`, snap intro-end to the last black frame, credits-start to the first. No valley →
  original unchanged.
- **Budgets (hard)**: wall-clock ≈10s (`System.nanoTime()` check between frames → graceful abort),
  ≤24 `OPTION_CLOSEST` decodes, `Thread.interrupted()` between frames, `retriever.release()` in
  `finally`, any throwable → "no change".
- Classifier (`isBlack`, valley/edge search) — package-private static without Android types (pure like
  `SegmentAdjuster`), suitable for a JVM test.

### B2. `skip/SkipManager.java`
`public void applyRefined(List<SkipSegment> refined, double durationSec)` — replaces the list, re-runs
`classifyCredits(durationSec)`, carries over `skipped` by position. `skipTick` unchanged — the next
tick (250ms) reads new boundaries; `skipSeekTo` (~1891, `SeekParameters.EXACT`) already lands exactly.

### B3. `PlayerActivity.java` (small insertions)
- Field `segmentRefinerThread` + `cancelSegmentRefiner()` (mirror `cancelSegmentFinder` ~1773); cancel
  in `resetApiState` (~1676) and `setupSkipSource` (~1685).
- `maybeRefineSegments()` at the end of `rebuildSkip()` (after ~1724) — only when
  `skipManager.hasSegments() && mPrefs.skipEnabled && mPrefs.skipRefineContent &&
  Utils.isContentRefinementCapable(this)` and a per-media gate. Captures `targetIndex`,
  `currentPlayingUri()` (~2807) + headers (like ~3440), starts with `postDelayed` ~3s (do not disturb
  start-up buffering). Both paths (intent and network) funnel through the single `rebuildSkip()`, so
  one site suffices.
- `onSegmentsRefined(targetIndex, refined)` via `runOnUiThread`, guard like `onSegmentsFetched`
  (~1764): discard if `player==null`, index changed, or no segments → `applyRefined` +
  `updateSkipHighlights()`.

### B1b. Scene-cut (added to `SegmentRefiner`)
A boundary is often a hard cut with no fade, so `snapBoundary` now: tries the black-frame snap first
only when `useBlack`; otherwise / on its miss, falls back to `snapToSceneCut` when `useSceneCut` —
sample exact frames at 0.5s over a narrow ±3s window, reduce each to a 4×4 luma-grid signature, and
snap to the **significant cut nearest the estimate** (largest consecutive-signature distance above
`SCENE_CUT_MIN`, tie-broken by proximity). Decode budget raised to 48 (still under the 10s wall-clock).

### B4. Prefs + gating (disable strategy)
Two independent refinement techniques + a manual offset, all in the **Skip** settings section
(`pref_skip_header`), each `app:dependency="skipEnabled"`:
- **`skipRefineScene`** (`SwitchPreferenceCompat`, **default ON**) — scene-cut snap; the primary fix
  for small (~1–3s) offsets at hard cuts, hence on by default.
- **`skipRefineBlack`** (`SwitchPreferenceCompat`, **default OFF**) — additionally prefer a nearby
  fade/black frame when present (tried before the scene-cut fallback).
- **`skipOffset`** (`ListPreference`, values `-5..+5`, **default 0**) — deterministic global shift of
  every segment (no analysis), for when neither snap lands right. Applied in `SkipManager.applyOffset`
  (shift + clamp to media bounds); set via `SkipManager.setOffsetSec` from `rebuildSkip` before `rebuild`.
- `maybeRefineSegments` runs when **either** refine toggle is on and passes `useBlack`/`useSceneCut`.
- **No hard device lock** (overridable everywhere). Safety nets instead of a device-tier gate:
  - `SegmentRefiner` no-ops on `Build.VERSION.SDK_INT < 27` (its `getScaledFrameAtTime` needs API 27).
  - Per-media gate in `maybeRefineSegments`: refuse when `height>2160`, or `height>1080 && isTvBox`.
- `Utils.isContentRefinementCapable(Context)` (SDK≥27 && !low-RAM && ≥4 cores) is **kept but unused** —
  a ready hook for a future "disable/smart-default on old hardware" behavior.

---

## Work order
0. Create branch `feature/skip-segment-voting`; save plan to `SKIP/SKIP_ACCURACY_PLAN.md`; start
   `SKIP/SKIP_ACCURACY_PROGRESS.md` with a checklist.
1. A1 model → A2 voting core → A3 consumption → A4 constants (P0).
2. B1 refiner → B2 applyRefined → B3 hooks → B4 pref/gating (P1).
3. Remove debug logging; build; manual verification per checklist.
4. Update `SKIP/SKIP_ACCURACY_PROGRESS.md` along the way; finally `git diff` for cleanliness (no debug).

## Files to modify
- `app/.../skip/SkipSegment.java` — category, coordBase/timeTrust, confirmed (A1).
- `app/.../skip/SegmentFinder.java` — parallel probe, profiles, voting, cache, isMovie, remove logs
  (A2). **Core work.**
- `app/.../skip/NetworkSegmentsSource.java`, `skip/SkipManager.java` — consumption, open-ended cap,
  `applyRefined` (A3, B2).
- `app/.../PlayerActivity.java` — remove debug logs; refiner hooks (A3, B3).
- `app/.../skip/SegmentRefiner.java` — **new** (B1).
- `app/.../Prefs.java`, `res/xml/root_preferences.xml`, `res/values/strings.xml`,
  `app/.../SettingsActivity.java`, `app/.../Utils.java` — pref + gating (B4).

## Reuse
- Fan-out probe: `SubtitleFetcher.java` (enqueue + `CountDownLatch`).
- Threading/cancel: `SegmentFinder.find`/`cancelSegmentFinder`, `nextUriThread`, `frameRateSwitchThread`.
- OkHttp/JSON/endpoints: `SegmentFinder` helpers + `SegmentEndpoints.java`.
- Gating: `Utils.isTvBox`, `player.getVideoFormat()` (~2204), `setEnabled` pattern in `SettingsActivity` (~93).
- URI/eligibility: `currentPlayingUri()` (~2807), `Utils.isProgressiveContainerUri`.

## Verification (no tests in project → build + manual)
1. `./gradlew assembleLatestUniversalDebug` (JDK 17); artifact — `just-plus.player.debug.apk`.
2. **Non-anime** (Breaking Bad `tt0903747` S1E1): duration-aware SkipDB gives timing; absolute
   TheIntroDB does not overtake nor apply shifted.
3. **Anime** (Attack on Titan `tt2560140` S1E1): Aniskip primary; SkipMe does not override timing.
4. **Replay shift**: same title, different length does not take the stale cache (key with duration bucket).
5. **Voting**: ≥2 agreeing → `confirmed`, exact timing; single → shown (per mode).
6. **Latency**: parallel profile probe fits within ~one timeout (temporary request-count log, then remove).
7. **P1** (Pulp Fiction `tt0110912` / local MKV with fade intro): snap ≤0.25s; file with no black
   boundary → explicit no-op; HLS URL → "ineligible", 0 retriever calls; episode change during refine
   → interrupt without callback; 4K long-GOP → graceful abort in ~10s.
8. **P1 gating**: low-RAM/API26 emulator — pref greyed out; TV + >1080p — per-media refusal.
9. **Non-regression**: with pref OFF (default) player/skip behavior byte-identical; `git grep
   skipRefineContent` outside the new class touches only the guard, Prefs, XML, SettingsActivity.
10. `git diff` — no debug logging before commit (CLAUDE.md).

## Out of scope (deferred)
- **Chapters as a time source** — strongest lever against shift, but deliberately deferred in
  `SKIP_VOTING_PLAN.md` ("network only for now"). Model is ready (`CoordBase.CHAPTER`, top
  `timeTrust`) — port `ChapterScanner` as a separate `SkipSource` next iteration. Limitation: current
  parsers (`ContainerMetadataReader`/`Mp4`/`Matroska`) read only the start of the file — MP4 `moov`/MKV
  Chapters at EOF are not reached; needs extension.
- **Silence detection** — phase 2 inside `SegmentRefiner` (fallback for audio-led content), if field
  data shows black-frame misses.
